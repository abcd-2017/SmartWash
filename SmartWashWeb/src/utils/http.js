// src/utils/http.js
import axios from 'axios';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import logger from '@/utils/logger';

// baseURL 从环境变量读取：开发环境为 /api（走 vite 代理转发），
// 生产环境由 .env.production 注入完整地址；生产建议改用 Nginx 反代 + HTTPS 域名
const baseURL = __SMART_WASH_BASE_URL__ || import.meta.env.VITE_BASE_URL || '/api';

// 创建 axios 实例
const http = axios.create({
  // 编译时由 vite.config.js define 注入 __SMART_WASH_BASE_URL__（与 Android 端对齐）；
  // 兜底：VITE_BASE_URL（.env 文件）→ /api（开发代理）
  baseURL,
  // 请求超时 30 秒：与 Android（OkHttp read 30s）、鸿蒙端（readTimeout 30s）三端统一基准（评审 #20），
  // 覆盖导出、地图选点等慢接口，避免大分页/弱网下被 5s 误杀
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 公开信封请求实例：匿名公开页专用（当前唯一消费方为设备投递公开页 /d/:code，
// 接口段 /web/delivery/**，码即凭证、本就无 token）。与主实例的区别：
// 1. 不注入 Bearer、不读登录态，也不会触发全局 401 登出逻辑；
// 2. 不解包信封、不弹全局 ElMessage——业务码 403/404/409/413 是投递语义
//    （密码错误/会话失效/被占用/负载超限），由调用方拿到完整 { code, message, data } 自行消费
export const envelopeHttp = axios.create({
  baseURL,
  timeout: 30000,
});

// 从登录态 store 获取 token；pinia 未激活的极端场景（如单测环境）回退读持久化层
function getToken() {
  try {
    return useAuthStore().token;
  } catch {
    return localStorage.getItem('token');
  }
}

// 续签凭证读取方式与 token 一致：store 优先，pinia 未激活时回退持久化层
function getRefreshToken() {
  try {
    return useAuthStore().refreshToken;
  } catch {
    return localStorage.getItem('refreshToken');
  }
}

// 401 统一处理：清登录态 → 跳转登录页（替代旧的 window.location.reload()）
let redirectingToLogin = false; // 1.5 秒去重窗口，防止并发 401 重复提示/重复跳转
function handleUnauthorized() {
  // 无论当前处于哪个页面，先清空登录态（内存 + 持久化一并清理）
  try {
    useAuthStore().clearLogin();
  } catch {
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('role');
  }

  // 登录页自身的 401（如账号密码错误）由调用方展示后端 message，这里不再重复提示/跳转
  if (window.location.pathname === '/login') {
    return;
  }
  if (redirectingToLogin) {
    return;
  }
  redirectingToLogin = true;
  setTimeout(() => {
    redirectingToLogin = false;
  }, 1500);

  ElMessage.error('登录已过期，请重新登录');
  // 动态引入 router 避免 http.js 与 router 的循环依赖，SPA 内跳转到登录页；
  // 带上当前地址，登录成功后原路返回，避免深层操作被过期 token 打断后丢失现场
  import('@/router').then(({ default: router }) => {
    const current = router.currentRoute.value.fullPath;
    router.push(`/login?redirect=${encodeURIComponent(current)}`);
  });
}

// 判断请求是否为认证端点（/auth/**：登录、刷新自身等）：
// 这些请求的 401 是业务语义（如账号密码错误），直接按登录过期处理，不得触发续签
function isAuthPath(url) {
  return typeof url === 'string' && url.startsWith('/auth/');
}

// 单飞续签：模块级 in-flight Promise 去重，并发 401 只发一次 /auth/refresh；
// 刷新请求带 __isRefresh 标记，其自身失败在响应拦截器内直接 reject，
// 不递归触发 handleUnauthorized（登出由等待方统一执行）
let refreshPending = null;
function refreshTokens() {
  if (refreshPending) {
    return refreshPending;
  }
  refreshPending = http
    .post('/auth/refresh', { refreshToken: getRefreshToken() }, { __isRefresh: true })
    .then((data) => {
      // 响应拦截器已解包统一信封，data 即 { token, refreshToken }（无 role）
      if (!data?.token) {
        throw new Error('续签响应缺少凭证');
      }
      try {
        useAuthStore().setTokens(data.token, data.refreshToken);
      } catch {
        // pinia 未激活的极端场景（单测）：直接写持久化层，保证重试请求能取到新 token
        localStorage.setItem('token', data.token);
        if (data.refreshToken) {
          localStorage.setItem('refreshToken', data.refreshToken);
        }
      }
      return data;
    })
    .finally(() => {
      // 无论成败都释放单飞位，下一次 401 可重新发起续签
      refreshPending = null;
    });
  return refreshPending;
}

// 续签成功后用新 token 重发原请求一次；带 __isRetry 标记，
// 重试若再遇 401 由拦截器直接登出，不再进入续签流程（防循环）
function retryRequest(config, token) {
  config.headers.Authorization = `Bearer ${token}`;
  config.__isRetry = true;
  return http(config);
}

// 401 续签流程（业务码 401 与 HTTP 401 两通道共用）：
// - 认证端点（登录/刷新自身）/ 已是重试 / 无 refreshToken → 直接 handleUnauthorized（原语义不变）；
// - 否则等单飞续签结果：成功 → 更新 store 双 token 并重发原请求一次，
//   重试结果作为最终结果返回调用方（业务无感知）；失败 → handleUnauthorized（清态 + 跳登录）。
// originalError 用于在「不走续签」与「续签失败」分支保持与旧实现一致的调用方可见错误
function processUnauthorized(config, originalError) {
  if (isAuthPath(config?.url) || config?.__isRetry) {
    handleUnauthorized();
    return Promise.reject(originalError);
  }
  if (!getRefreshToken()) {
    handleUnauthorized();
    return Promise.reject(originalError);
  }
  return refreshTokens().then(
    (data) => retryRequest(config, data.token),
    () => {
      handleUnauthorized();
      return Promise.reject(originalError);
    }
  );
}

// 请求拦截器
http.interceptors.request.use(
  (config) => {
    // 统一附加认证头，token 从登录态 store 读取
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
http.interceptors.response.use(
  (response) => {
    const res = response.data;

    // 业务码 401 与 HTTP 401 走同一份续签/登出逻辑
    if (res.code === 401) {
      // 刷新请求自身的业务 401（refreshToken 失效）：仅向单飞等待方传递失败信号，
      // 不在此处登出，由 processUnauthorized 的失败分支统一处理
      if (response.config?.__isRefresh) {
        return Promise.reject(new Error(res.message || '登录已过期'));
      }
      return processUnauthorized(response.config, new Error(res.message || '登录已过期'));
    }
    if (res.code !== 200) {
      // 业务拒绝留痕：只记 method/url/业务码/message 摘要，不打完整请求/响应体（敏感红线）
      logger.warn(
        `业务码非200 ${response.config?.method?.toUpperCase()} ${response.config?.url}`,
        `code=${res.code}`,
        res.message
      );
      return Promise.reject(new Error(res.message || 'Error'));
    }
    // 成功时解包统一响应信封，直接返回业务数据 res.data
    return res.data;
  },
  (error) => {
    // HTTP 层错误留痕：只记 method/url/status/message 摘要，不打完整请求/响应体（敏感红线）
    logger.warn(
      `HTTP错误 ${error.config?.method?.toUpperCase() || 'UNKNOWN'} ${error.config?.url || ''}`,
      `status=${error.response?.status ?? '-'}`,
      error.response?.data?.message || error.message
    );
    if (error.response?.status === 401) {
      // HTTP 401：认证失败（token 缺失/过期等），进入统一的续签/登出流程；
      // 刷新请求自身的 HTTP 401 同样只传失败信号，不递归登出
      if (error.config?.__isRefresh) {
        return Promise.reject(error);
      }
      return processUnauthorized(error.config, error);
    }
    // 非 401 的 HTTP 错误统一提示（优先展示后端返回的 message）；
    // 刷新请求的此类失败由等待方按登录过期处理，不叠加网络错误提示
    // grouping 合并同文案的并发失败提示（页面初始化多请求齐挂时只弹一条）
    if (!error.config?.__isRefresh) {
      ElMessage.error({
        message: error.response?.data?.message || error.message || '网络异常，请稍后重试',
        grouping: true,
      });
    }
    return Promise.reject(error);
  }
);

export default http;

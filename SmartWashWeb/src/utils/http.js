// src/utils/http.js
import axios from 'axios';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/stores/auth';

// 创建 axios 实例
// baseURL 从环境变量读取：开发环境为 /api（走 vite 代理转发），
// 生产环境由 .env.production 注入完整地址；生产建议改用 Nginx 反代 + HTTPS 域名
const http = axios.create({
    baseURL: import.meta.env.VITE_BASE_URL || '/api',
    timeout: 5000, // 请求超时（毫秒）
    headers: {
        'Content-Type': 'application/json',
    },
});

// 从登录态 store 获取 token；pinia 未激活的极端场景（如单测环境）回退读持久化层
function getToken() {
    try {
        return useAuthStore().token;
    } catch {
        return localStorage.getItem('token');
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
    // 动态引入 router 避免 http.js 与 router 的循环依赖，SPA 内跳转到登录页
    import('@/router').then(({ default: router }) => {
        router.push('/login');
    });
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

        // 业务码 401 与 HTTP 401 走同一份处理逻辑
        if (res.code === 401) {
            handleUnauthorized();
            return Promise.reject(new Error(res.message || '登录已过期'));
        }
        if (res.code !== 200) {
            return Promise.reject(new Error(res.message || 'Error'));
        }
        // 成功时解包统一响应信封，直接返回业务数据 res.data
        return res.data;
    },
    (error) => {
        if (error.response?.status === 401) {
            // HTTP 401：认证失败（token 缺失/过期等），统一清登录态并跳转登录页
            handleUnauthorized();
        } else {
            // 非 401 的 HTTP 错误统一提示（优先展示后端返回的 message）
            ElMessage.error(
                error.response?.data?.message || error.message || '网络异常，请稍后重试'
            );
        }
        return Promise.reject(error);
    }
);

export default http;

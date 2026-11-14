import request from '@/utils/http';

// config 透传 axios 请求配置（如 { silent: true } 跳过 http.js 全局网络错误 toast，
// 由登录页 catch 分级自行提示，避免双弹）
export function login(data, config = {}) {
  return request({
    url: '/auth/adminUsers/login',
    method: 'post',
    data,
    ...config,
  });
}

// src/utils/http.js
import axios from 'axios';

// 创建 axios 实例
const http = axios.create({
    baseURL: 'http://localhost:8080', // 设置基础 URL，替换成你的后台 API 地址
    timeout: 5000, // 请求超时
    headers: {
        'Content-Type': 'application/json',
    },
});

// 请求拦截器
http.interceptors.request.use(
    (config) => {
        // 可以在这里添加 token 等认证信息
        const token = localStorage.getItem('token'); // 假设你将 token 存储在 localStorage 中
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
        const res = response.data

        if (res.code !== 200) {
            // ElMessage.error(res.message || 'Error')
            return Promise.reject(new Error(res.message || 'Error'))
        } else if (res.code === 401) {
            ElMessage.error('登录已过期，请重新登录')
            localStorage.removeItem("token")
            window.location.reload()
        }
        return res
    },
    (error) => {
        // 处理401未授权错误
        if (error.response?.status === 401) {
            ElMessage.error('登录已过期，请重新登录')
            localStorage.removeItem("token")
            window.location.reload()
        }

        // ElMessage.error(error.message)
        return Promise.reject(error)
    }
);

export default http;
// src/utils/http.js
import axios from 'axios';
import { ElMessage } from 'element-plus';

// 创建 axios 实例
const http = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
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

        if (res.code === 401) {
            ElMessage.error('登录已过期，请重新登录')
            localStorage.removeItem("token")
            window.location.reload()
            return Promise.reject(new Error('登录已过期'))
        } else if (res.code !== 200) {
            return Promise.reject(new Error(res.message || 'Error'))
        }
        return res.data
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
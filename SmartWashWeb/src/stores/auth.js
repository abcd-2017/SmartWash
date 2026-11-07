// src/stores/auth.js
// 登录态全局状态（Pinia）：双 token（access + refresh）/ 角色的唯一读写入口。
// 持久化沿用 localStorage 的 "token" / "refreshToken" / "role" 键，与既有页面/拦截器/单测兼容；
// 新代码一律通过本 store 读写登录态，禁止再散落裸 localStorage 操作。
import { defineStore } from 'pinia';

const TOKEN_KEY = 'token';
const REFRESH_TOKEN_KEY = 'refreshToken';
const ROLE_KEY = 'role';

// 持久化辅助：值存在则写入，为空则移除，避免残留旧凭证
function persist(key, value) {
  if (value) {
    localStorage.setItem(key, value);
  } else {
    localStorage.removeItem(key);
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    // 初始化时从持久化层恢复，保证刷新页面后登录态不丢
    token: localStorage.getItem(TOKEN_KEY) || '',
    refreshToken: localStorage.getItem(REFRESH_TOKEN_KEY) || '',
    role: localStorage.getItem(ROLE_KEY) || '',
  }),
  actions: {
    // 登录成功后写入登录态：双 token + 后端返回的管理员实际角色名（如 "admin"）
    login(token, role, refreshToken) {
      this.token = token || '';
      this.refreshToken = refreshToken || '';
      this.role = role || '';

      persist(TOKEN_KEY, this.token);
      persist(REFRESH_TOKEN_KEY, this.refreshToken);
      persist(ROLE_KEY, this.role);
    },
    // 401 自动续签成功后更新双 token（刷新接口不返回 role，角色保持不变；
    // 响应缺 refreshToken 时保留旧值，避免误清仍在有效期内的续签凭证）
    setTokens(token, refreshToken) {
      if (token) {
        this.token = token;
      }
      if (refreshToken) {
        this.refreshToken = refreshToken;
      }
      persist(TOKEN_KEY, this.token);
      persist(REFRESH_TOKEN_KEY, this.refreshToken);
    },
    // 清空登录态（主动登出 / 401 登录过期），内存与持久化一并清理
    clearLogin() {
      this.token = '';
      this.refreshToken = '';
      this.role = '';
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(REFRESH_TOKEN_KEY);
      localStorage.removeItem(ROLE_KEY);
    },
  },
});

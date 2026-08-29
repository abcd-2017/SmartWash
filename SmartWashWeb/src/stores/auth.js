// src/stores/auth.js
// 登录态全局状态（Pinia）：token / 角色的唯一读写入口。
// 持久化沿用 localStorage 的 "token" / "role" 键，与既有页面/拦截器/单测兼容；
// 新代码一律通过本 store 读写登录态，禁止再散落裸 localStorage 操作。
import { defineStore } from 'pinia'

const TOKEN_KEY = 'token'
const ROLE_KEY = 'role'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    // 初始化时从持久化层恢复，保证刷新页面后登录态不丢
    token: localStorage.getItem(TOKEN_KEY) || '',
    role: localStorage.getItem(ROLE_KEY) || '',
  }),
  actions: {
    // 登录成功后写入登录态：token + 后端返回的管理员实际角色名（如 "admin"）
    login(token, role) {
      this.token = token || ''
      this.role = role || ''

      if (this.token) {
        localStorage.setItem(TOKEN_KEY, this.token)
      } else {
        localStorage.removeItem(TOKEN_KEY)
      }
      if (this.role) {
        localStorage.setItem(ROLE_KEY, this.role)
      } else {
        localStorage.removeItem(ROLE_KEY)
      }
    },
    // 清空登录态（主动登出 / 401 登录过期），内存与持久化一并清理
    clearLogin() {
      this.token = ''
      this.role = ''
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(ROLE_KEY)
    },
  },
})

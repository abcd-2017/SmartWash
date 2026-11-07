/**
 * 修改密码请求体（对齐后端 PUT /web/auth/user/password 契约：6-16 位）。
 */
export class ChangePasswordForm {
  oldPassword: string | null = null
  newPassword: string | null = null
}

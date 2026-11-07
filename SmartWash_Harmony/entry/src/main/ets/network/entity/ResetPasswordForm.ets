/**
 * 忘记密码-重置请求体（对齐后端 ResetPasswordFrom：code 6 位、newPassword 6-16 位）。
 */
export class ResetPasswordForm {
  phoneNumber: string | null = null
  code: string | null = null
  newPassword: string | null = null
}

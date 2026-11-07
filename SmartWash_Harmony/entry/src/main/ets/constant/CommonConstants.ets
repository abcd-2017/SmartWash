export class CommonConstants {
  public static readonly USER_TOKEN = "token"
  // 刷新令牌（对齐后端 LoginVo.refreshToken）：登录/注册成功随双 token 一起落盘，供后续静默续期消费
  public static readonly USER_REFRESH_TOKEN = "refreshToken"
  public static readonly BORDER_RADIUS = 12
}
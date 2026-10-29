/**
 * 支付方式类型（对齐安卓 feature/payment PaymentType：String "1"/"2"/"3" 契约，
 * 差异分析 01 §4.3 number→string，防后端契约歧义）
 */
export enum PaymentType {
  PURSE = "1",
  ALIPAY = "2",
  WECHAT = "3",
}

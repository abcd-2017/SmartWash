/**
 * 充值渠道类型码（评审 #30：魔法字符串 "1"/"2" 收敛为具名常量）
 * 与后端 recharge_records.recharge_type 字段约定：1=微信支付 2=支付宝
 */
export class RechargeType {
  static readonly WECHAT: string = "1"
  static readonly ALIPAY: string = "2"
}

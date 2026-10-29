/**
 * 优惠券状态（对齐安卓 feature/coupon CouponStatus：String "0"/"1"/"2" 契约，
 * 差异分析 01 §4.3 number→string，防后端契约歧义）
 */
export enum CouponStatus {
  //生效中
  ACTIVE = "0",
  //已失效
  EXPIRED = "1",
  //已领取
  RECEIVE = "2"
}

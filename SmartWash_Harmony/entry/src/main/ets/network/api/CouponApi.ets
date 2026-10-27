import { BaseResponse } from '../BaseResponse';
import { httpUtil } from '../HttpUtil';
import { AllCouponsVo } from '../vo/AllCouponsVo';
import { UserCouponVo } from '../vo/UserCouponVo';

//获取所有优惠券数据（可领取/已领取/历史三类，对齐安卓聚合接口，切 tab 零请求）
async function getAllCoupons(): Promise<BaseResponse<AllCouponsVo>> {
  return httpUtil.get<AllCouponsVo>(`/web/auth/userCoupon/allCoupons`);
}

//领取优惠券
async function receiveCoupon(couponId: number): Promise<BaseResponse<boolean>> {
  return httpUtil.post<boolean>(`/web/auth/userCoupon/receiveCoupon/${couponId}`);
}

//判断当前订单可以使用的优惠券
async function getCanUseCoupon(orderId: number): Promise<BaseResponse<Array<UserCouponVo>>> {
  return httpUtil.get<Array<UserCouponVo>>(`/web/auth/userCoupon/available/${orderId}`);
}


export { getAllCoupons, receiveCoupon, getCanUseCoupon }

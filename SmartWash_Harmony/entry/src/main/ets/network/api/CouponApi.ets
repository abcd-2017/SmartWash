import { BaseResponse } from '../BaseResponse';
import { httpUtil } from '../HttpUtil';
import { CouponVo } from '../vo/CouponVo';
import { UserCouponVo } from '../vo/UserCouponVo';

//获取所有优惠券
async function getAllCoupon(): Promise<BaseResponse<Array<CouponVo>>> {
  return httpUtil.get<Array<CouponVo>>(`/web/auth/coupon/allCoupon`);
}

//领取优惠券
async function receiveCoupon(couponId: number): Promise<BaseResponse<boolean>> {
  return httpUtil.post<boolean>(`/web/auth/userCoupon/receiveCoupon/${couponId}`);
}

//获取用户优惠券
async function getUserCoupon(params: Record<string, Object>): Promise<BaseResponse<Array<UserCouponVo>>> {
  return httpUtil.get<Array<UserCouponVo>>(`/web/auth/userCoupon/getUserCoupon`, params);
}

//判断当前订单可以使用的优惠券
async function getCanUseCoupon(orderId: number): Promise<BaseResponse<Array<UserCouponVo>>> {
  return httpUtil.post<Array<UserCouponVo>>(`/web/auth/userCoupon/getCanUseCoupon/${orderId}`);
}


export { getAllCoupon, receiveCoupon, getUserCoupon, getCanUseCoupon }
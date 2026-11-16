import { BaseResponse } from '../BaseResponse';
import { httpUtil } from '../HttpUtil';
import { AllCouponsVo } from '../vo/AllCouponsVo';
import { UserCouponPageData, userCouponPageFromJSON } from '../vo/UserCouponVo';
import { UserCouponVo } from '../vo/UserCouponVo';

/** 用户优惠券单页条数（后端默认 10） */
export const COUPON_PAGE_SIZE: number = 10
/** 用户优惠券状态：0-可用（已领取未使用未过期） */
export const COUPON_STATUS_USABLE: string = '0'
/** 用户优惠券状态：1-已使用或过期（历史） */
export const COUPON_STATUS_USED: string = '1'

//获取所有优惠券数据（可领取/已领取/历史三类，对齐安卓聚合接口，切 tab 零请求）
async function getAllCoupons(): Promise<BaseResponse<AllCouponsVo>> {
  return httpUtil.get<AllCouponsVo>(`/web/auth/userCoupon/allCoupons`);
}

/**
 * 用户优惠券分页（对齐后端 GET /web/auth/userCoupon/getUserCoupon）。
 * @param status 0-可用，1-已使用或过期
 * @param page 页码（从 1 起）
 * @param size 每页条数
 */
async function getUserCoupon(status: string, page: number,
  size: number = COUPON_PAGE_SIZE): Promise<BaseResponse<UserCouponPageData>> {
  // ArkTS 禁止裸对象字面量作参数（arkts-no-untyped-obj-literals），查询参数用 Record 并按索引赋值
  const params: Record<string, Object> = {}
  params['status'] = status
  params['page'] = page
  params['size'] = size
  const raw = await httpUtil.get<UserCouponPageData>(`/web/auth/userCoupon/getUserCoupon`, params)
  const decoded: BaseResponse<UserCouponPageData> = {
    data: raw.data === null || raw.data === undefined ? userCouponPageFromJSON(null) : userCouponPageFromJSON(raw.data),
    code: raw.code,
    message: raw.message
  }
  return decoded
}

async function receiveCoupon(couponId: number): Promise<BaseResponse<boolean>> {
  return httpUtil.post<boolean>(`/web/auth/userCoupon/receiveCoupon/${couponId}`);
}

async function getCanUseCoupon(orderId: number): Promise<BaseResponse<Array<UserCouponVo>>> {
  return httpUtil.get<Array<UserCouponVo>>(`/web/auth/userCoupon/available/${orderId}`);
}


export { getAllCoupons, getUserCoupon, receiveCoupon, getCanUseCoupon }

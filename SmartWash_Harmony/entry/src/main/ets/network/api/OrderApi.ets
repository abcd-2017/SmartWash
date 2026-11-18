import { BaseResponse } from '../BaseResponse';
import { OrderItemCountForm } from '../entity/OrderItemCountForm';
import { OrderListForm } from '../entity/OrderListForm';
import { OrderNextStatus } from '../entity/OrderNextStatus';
import { ReservationLaundry } from '../entity/ReservationLaundry';
import { ReviewSubmit } from '../entity/ReviewSubmit';
import { httpUtil } from '../HttpUtil';
import { OrderInfo } from '../vo/OrderInfo';
import { OrderItemCountVo, orderItemCountVoFromJSON } from '../vo/OrderItemCountVo';
import { OrderVo, orderVoFromJSON } from '../vo/OrderVo';
import { ReviewVo, reviewVoListFromJSON } from '../vo/ReviewVo';
import { mapArray } from '../vo/JsonParse';

async function reservationLaundry(data: ReservationLaundry): Promise<BaseResponse<number>> {
  return httpUtil.post<number>(`/web/auth/orders/reservation`, data);
}

async function getOrderInfo(orderId: number): Promise<BaseResponse<OrderInfo>> {
  return httpUtil.get<OrderInfo>(`/web/auth/orders/${orderId}`);
}

async function getOrderList(data: OrderListForm): Promise<BaseResponse<Array<OrderInfo>>> {
  return httpUtil.get<Array<OrderInfo>>(`/web/auth/orders`, data);
}

async function getOrderItemCount(data: OrderItemCountForm): Promise<BaseResponse<OrderItemCountVo>> {
  const raw: BaseResponse<Object> = await httpUtil.get<Object>(`/web/auth/orders/itemCount`, data);
  const mapped: BaseResponse<OrderItemCountVo> = {
    code: raw.code,
    message: raw.message,
    data: orderItemCountVoFromJSON(raw.data)
  }
  return mapped;
}

async function shippingOrder(data: OrderNextStatus): Promise<BaseResponse<boolean>> {
  return httpUtil.post<boolean>(`/web/auth/orders/shipping`, data);
}

async function pickupOrder(data: OrderNextStatus): Promise<BaseResponse<boolean>> {
  return httpUtil.post<boolean>(`/web/auth/orders/pickup`, data);
}

async function getWashingOrder(): Promise<BaseResponse<Array<OrderVo>>> {
  const raw: BaseResponse<Object> = await httpUtil.get<Object>(`/web/auth/orders/getWashingOrder`);
  const mapped: BaseResponse<Array<OrderVo>> = {
    code: raw.code,
    message: raw.message,
    data: mapArray(raw.data, (item: Object): OrderVo => orderVoFromJSON(item))
  }
  return mapped;
}

async function cancelOrder(orderId: number): Promise<BaseResponse<boolean>> {
  return httpUtil.delete<boolean>(`/web/auth/orders/${orderId}`);
}

//计算订单优惠券（ArkTS 禁止裸对象字面量，查询参数用 Record 并按索引赋值）
async function calculationOrder(orderId: number, userCouponId: number): Promise<BaseResponse<OrderInfo>> {
  const params: Record<string, Object> = { 'userCouponId': userCouponId }
  return httpUtil.get<OrderInfo>(`/web/auth/orders/${orderId}/calculation`, params);
}

async function generateQrCode(pickCode: string): Promise<BaseResponse<string>> {
  return httpUtil.get<string>(`/web/auth/orders/generateQrCode/${pickCode}`);
}

/**
 * 提交订单评价（对齐后端 POST /web/auth/reviews/add，AddReviewFrom：orderId/rating/content）。
 * data=true 表示受理成功（重复评价由后端拒绝）。
 */
async function addReview(data: ReviewSubmit): Promise<BaseResponse<boolean>> {
  return httpUtil.post<boolean>('/web/auth/reviews/add', data);
}

/**
 * 订单评价列表（后端按创建时间倒序分页，Page 包装 records）；消费侧取首条作本人评价回显。
 */
async function getOrderReviews(orderId: number): Promise<BaseResponse<Array<ReviewVo>>> {
  const raw: BaseResponse<Object> = await httpUtil.get<Object>(`/web/auth/reviews/${orderId}`);
  // Page 结构：data.records 才是数组（裸数组解析会恒空，曾导致已评价入口不显示）
  let records: Object | null = null
  if (raw.data !== null) {
    const pageObj = raw.data as Record<string, Object>
    records = pageObj['records'] ?? null
  }
  const mapped: Array<ReviewVo> = reviewVoListFromJSON(records)
  return { code: raw.code, message: raw.message, data: mapped };
}

export {
  reservationLaundry,
  calculationOrder,
  cancelOrder,
  getWashingOrder,
  pickupOrder,
  shippingOrder,
  getOrderList,
  getOrderItemCount,
  getOrderInfo,
  generateQrCode,
  addReview,
  getOrderReviews
}
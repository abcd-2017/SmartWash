import { BaseResponse } from '../BaseResponse';
import { OrderItemCountForm } from '../entity/OrderItemCountForm';
import { OrderListForm } from '../entity/OrderListForm';
import { OrderNextStatus } from '../entity/OrderNextStatus';
import { ReservationLaundry } from '../entity/ReservationLaundry';
import { httpUtil } from '../HttpUtil';
import { OrderInfo } from '../vo/OrderInfo';
import { OrderItemCountVo, orderItemCountVoFromJSON } from '../vo/OrderItemCountVo';
import { OrderVo, orderVoFromJSON } from '../vo/OrderVo';
import { mapArray } from '../vo/JsonParse';

//预约订单
async function reservationLaundry(data: ReservationLaundry): Promise<BaseResponse<number>> {
  return httpUtil.post<number>(`/web/auth/orders/reservation`, data);
}

//获取订单详情
async function getOrderInfo(orderId: number): Promise<BaseResponse<OrderInfo>> {
  return httpUtil.get<OrderInfo>(`/web/auth/orders/${orderId}`);
}

//获取订单列表
async function getOrderList(data: OrderListForm): Promise<BaseResponse<Array<OrderInfo>>> {
  return httpUtil.get<Array<OrderInfo>>(`/web/auth/orders`, data);
}

//获取订单数量
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

//取消订单
async function cancelOrder(orderId: number): Promise<BaseResponse<boolean>> {
  return httpUtil.delete<boolean>(`/web/auth/orders/${orderId}`);
}

//计算订单优惠券（ArkTS 禁止裸对象字面量，查询参数用 Record 并按索引赋值）
async function calculationOrder(orderId: number, userCouponId: number): Promise<BaseResponse<OrderInfo>> {
  const params: Record<string, Object> = { 'userCouponId': userCouponId }
  return httpUtil.get<OrderInfo>(`/web/auth/orders/${orderId}/calculation`, params);
}

//生成二维码
async function generateQrCode(pickCode: string): Promise<BaseResponse<string>> {
  return httpUtil.get<string>(`/web/auth/orders/generateQrCode/${pickCode}`);
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
  generateQrCode
}
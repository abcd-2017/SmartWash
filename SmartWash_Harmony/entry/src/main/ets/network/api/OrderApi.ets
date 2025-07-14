import { BaseResponse } from '../BaseResponse';
import { OrderItemCountFrom } from '../entity/OrderItemCountFrom';
import { OrderListFrom } from '../entity/OrderListFrom';
import { OrderNextStatus } from '../entity/OrderNextStatus';
import { ReservationLaundry } from '../entity/ReservationLaundry';
import { httpUtil } from '../HttpUtil';
import { OrderInfo } from '../vo/OrderInfo';
import { OrderItemCountVo } from '../vo/OrderItemCountVo';
import { OrderVo } from '../vo/OrderVo';

//预约订单
async function reservationLaundry(data: ReservationLaundry): Promise<BaseResponse<number>> {
  return httpUtil.post<number>(`/web/auth/orders/reservation`, data);
}

//获取订单详情
async function getOrderInfo(orderId: number): Promise<BaseResponse<OrderInfo>> {
  return httpUtil.post<OrderInfo>(`/web/auth/orders/getOrderInfo/${orderId}`);
}

//获取订单列表
async function getOrderList(data: OrderListFrom): Promise<BaseResponse<Array<OrderInfo>>> {
  return httpUtil.post<Array<OrderInfo>>(`/web/auth/orders/getOrderList`, data);
}

//获取订单数量
async function getOrderItemCount(data: OrderItemCountFrom): Promise<BaseResponse<OrderItemCountVo>> {
  return httpUtil.post<OrderItemCountVo>(`/web/auth/orders/getOrderItemCount`, data);
}

async function shippingOrder(data: OrderNextStatus): Promise<BaseResponse<boolean>> {
  return httpUtil.post<boolean>(`/web/auth/orders/shipping`, data);
}

async function pickupOrder(data: OrderNextStatus): Promise<BaseResponse<boolean>> {
  return httpUtil.post<boolean>(`/web/auth/orders/pickup`, data);
}

async function getWashingOrder(): Promise<BaseResponse<Array<OrderVo>>> {
  return httpUtil.get<Array<OrderVo>>(`/web/auth/orders/getWashingOrder`);
}

//取消订单
async function cancelOrder(orderId: number): Promise<BaseResponse<boolean>> {
  return httpUtil.post<boolean>(`/web/auth/orders/cancelOrder/${orderId}`);
}

//计算订单优惠券
async function calculationOrder(orderId: number, userCouponId: number): Promise<BaseResponse<OrderInfo>> {
  return httpUtil.post<OrderInfo>(`/web/auth/orders/calculationOrder/${orderId}/${userCouponId}`);
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
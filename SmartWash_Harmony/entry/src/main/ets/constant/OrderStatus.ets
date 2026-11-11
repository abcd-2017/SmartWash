/**
 * 订单状态枚举（对齐安卓 OrderStatus 10 态，值集 "-2"~"7"）。
 * 差异分析 02 §10：删除原 Enum+Map 双重定义冗余，描述文案统一由
 * getOrderStatusDescription 提供（对应安卓 descriptionRes 单一事实来源）。
 */
import { resString } from '../utils/ResourceString'
export enum OrderStatusEnum {
  CANCELED = "-2",
  REFUNDED = "-1",
  PENDING_PAYMENT = "0",
  PENDING_SHIPMENT = "1",
  RECEIVED = "2",
  WASHING = "3",
  DRIED = "4",
  IN_DELIVERY = "5",
  READY_FOR_PICKUP = "6",
  COMPLETED = "7"
}

/** 状态 → 展示文案（未知状态兜底返回原值） */
export function getOrderStatusDescription(status: string | null | undefined): string {
  switch (status) {
    case OrderStatusEnum.CANCELED:
      return resString('cancelled')
    case OrderStatusEnum.REFUNDED:
      return resString('order_status_refunded')
    case OrderStatusEnum.PENDING_PAYMENT:
      return resString('pending_payment')
    case OrderStatusEnum.PENDING_SHIPMENT:
      return resString('pending_shipment')
    case OrderStatusEnum.RECEIVED:
      return resString('order_status_received')
    case OrderStatusEnum.WASHING:
      return resString('step_washing')
    case OrderStatusEnum.DRIED:
      return resString('order_status_dried')
    case OrderStatusEnum.IN_DELIVERY:
      return resString('order_status_in_delivery')
    case OrderStatusEnum.READY_FOR_PICKUP:
      return resString('step_pickup')
    case OrderStatusEnum.COMPLETED:
      return resString('completed')
    default:
      return status ?? ""
  }
}

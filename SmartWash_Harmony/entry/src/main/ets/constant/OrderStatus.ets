/**
 * 订单状态枚举（对齐安卓 OrderStatus 10 态，值集 "-2"~"7"）。
 * 差异分析 02 §10：删除原 Enum+Map 双重定义冗余，描述文案统一由
 * getOrderStatusDescription 提供（对应安卓 descriptionRes 单一事实来源）。
 */
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
      return "已取消"
    case OrderStatusEnum.REFUNDED:
      return "已退款"
    case OrderStatusEnum.PENDING_PAYMENT:
      return "待支付"
    case OrderStatusEnum.PENDING_SHIPMENT:
      return "待寄件"
    case OrderStatusEnum.RECEIVED:
      return "已收取"
    case OrderStatusEnum.WASHING:
      return "清洗中"
    case OrderStatusEnum.DRIED:
      return "已烘干"
    case OrderStatusEnum.IN_DELIVERY:
      return "配送中"
    case OrderStatusEnum.READY_FOR_PICKUP:
      return "待取件"
    case OrderStatusEnum.COMPLETED:
      return "已完成"
    default:
      return status ?? ""
  }
}

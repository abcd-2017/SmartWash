/**
 * 订单列表页签状态（对齐安卓 ShowOrderStatus 5 态：全部/待支付/待寄件/清洗中/待取件）。
 * 差异分析 02 §10：删除原 Enum+Map 双重定义冗余，页签文案统一由
 * getShowOrderStatusDescription 提供（对应安卓 descriptionRes 单一事实来源）。
 */
export enum ShowOrderStatusEnum {
  ALL_ORDER = "001",
  PENDING_PAYMENT = "0",
  PENDING_SHIPMENT = "1",
  WASHING = "3",
  READY_FOR_PICKUP = "6",
}

/** 页签状态 → 展示文案（未知状态兜底返回原值） */
export function getShowOrderStatusDescription(status: string | null | undefined): string {
  switch (status) {
    case ShowOrderStatusEnum.ALL_ORDER:
      return "全部订单"
    case ShowOrderStatusEnum.PENDING_PAYMENT:
      return "待支付"
    case ShowOrderStatusEnum.PENDING_SHIPMENT:
      return "待寄件"
    case ShowOrderStatusEnum.WASHING:
      return "清洗中"
    case ShowOrderStatusEnum.READY_FOR_PICKUP:
      return "待取件"
    default:
      return status ?? ""
  }
}

/** 订单列表页签顺序（下标即 OrderList 路由入参语义，两端约定一致） */
export function OrderStatusList(): Array<string> {
  return ['001', '0', '1', '3', '6']
}

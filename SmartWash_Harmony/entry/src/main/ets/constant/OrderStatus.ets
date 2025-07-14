export type OrderStatus =
  | "-2" // 已取消
    | "-1" // 已退款
    | "0" // 待支付
    | "1" // 待寄件
    | "2" // 已收取
    | "3" // 清洗中
    | "4" // 已烘干
    | "5" // 配送中
    | "6" // 待取件
    | "7"; // 已完成

export const OrderStatusMap: Record<OrderStatus, string> = {
  "-2": "已取消",
  "-1": "已退款",
  "0": "待支付",
  "1": "待寄件",
  "2": "已收取",
  "3": "清洗中",
  "4": "已烘干",
  "5": "配送中",
  "6": "待取件",
  "7": "已完成",
};

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

// 工具函数
export function getOrderStatusDescription(status: string): string | undefined {
  return OrderStatusMap[status as OrderStatus];
}

export type ShowOrderStatus =
  | "001" // 全部订单
    | "0" // 待支付
    | "1" // 待寄件
    | "3" // 清洗中
    | "6"; // 待取件

export const ShowOrderStatusMap: Record<ShowOrderStatus, string> = {
  "001": "全部订单",
  "0": "待支付",
  "1": "待寄件",
  "3": "清洗中",
  "6": "待取件",
};

export enum ShowOrderStatusEnum {
  ALL_ORDER = "001",
  PENDING_PAYMENT = "0",
  PENDING_SHIPMENT = "1",
  WASHING = "3",
  READY_FOR_PICKUP = "6",
}

// 工具函数
export function getShowOrderStatusDescription(status: string): string | undefined {
  return ShowOrderStatusMap[status as ShowOrderStatus];
}

export function OrderStatusList(): Array<string> {
  return ['001', '0', '1', '3', '6']
}
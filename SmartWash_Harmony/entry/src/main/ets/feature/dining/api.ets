// 吃啥（dining）域契约 — 路由参数类（对齐 toolbox/api.ets 的 ToolboxShortCodeDetailParam 模式）。
// 网络接口在 network/api/DiningApi.ets、VO 在 network/vo/DiningVo.ets、纯函数在 DiningLogic.ets、
// 文案在 DiningText.ets；本文件只承载跨文件（含深链注册表）引用的路由参数结构。

/** 创建订单页路由参数（fromOrderId > 0 = 复用历史菜单模式） */
export class DiningOrderCreateParam {
  /** 复用源单 ID；0 = 常规模式 */
  fromOrderId: number = 0
  /** 源单标题（复用提示回显） */
  sourceTitle: string = ''
}

/** 订单分享页路由参数（joinToken 来自创建响应；从详情进入时空串由详情响应补发） */
export class DiningOrderShareParam {
  orderId: number = 0
  joinToken: string = ''
}

/** 加入入口页路由参数（深链 smartwash://dining/order/{orderId}?t={token} 解析产物） */
export class DiningOrderJoinParam {
  orderId: number = 0
  token: string = ''
}

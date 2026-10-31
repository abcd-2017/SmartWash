/**
 * 路由名常量 — 单一事实来源（差异分析 01 §5.3，对齐安卓各域 *Route 密封类）。
 * 取值必须与 entry/src/main/resources/base/profile/router_map.json 注册的 name 一一对应：
 * 新增/改名路由时两处同步修改，页面禁止再手写路由名字符串。
 */
export class RouteNames {
  static readonly LOGIN: string = "Login"
  static readonly REGISTER: string = "Register"
  static readonly HOME: string = "Home"
  static readonly ADD_SCHOOL: string = "AddSchool"
  static readonly SETTING: string = "Setting"
  static readonly RECHARGE: string = "Recharge"
  static readonly COUPON: string = "Coupon"
  static readonly ORDER_LIST: string = "OrderList"
  static readonly PAYMENT: string = "Payment"
  static readonly ORDER_DETAIL: string = "OrderDetail"
  static readonly PICKUP_DELIVERY: string = "PickupDelivery"
  static readonly BING_CAMPUS_CARD_ALERT: string = "BingCampusCardAlert"
  static readonly LAUNDRY: string = "Laundry"
  static readonly PICK_UP: string = "PickUp"
  static readonly PAYMENT_SUCCESS: string = "PaymentSuccess"
  static readonly RECHARGE_RECORD: string = "RechargeRecord"
  static readonly WORKBENCH: string = "Workbench"
  // 观象台域路由名（#29 落地）：先登记常量，待目标页注册 router_map 后接通跳转
  static readonly DIV_HOME: string = "DivHome"
  static readonly DIV_HISTORY: string = "DivHistory"
  // 观象台三页（本批次 DivHome / DivAsk / DivCast 已注册 router_map）
  static readonly DIV_ASK: string = "DivAsk"
  static readonly DIV_CAST: string = "DivCast"
  // 卦盘路由常量先行登记（页面随卦盘批次注册；首页今日一签 / 问事 / 摇卦成卦均跳此路由）
  static readonly DIV_CHART: string = "DivChart"
  // 解读 / 追问页路由常量（本批次 DivChart / DivReading / DivFollowUp 三页注册 router_map）
  static readonly DIV_READING: string = "DivReading"
  static readonly DIV_FOLLOW_UP: string = "DivFollowUp"
  // 工具箱短链域路由名（#28 落地）：列表 / 新建 / 详情
  static readonly TOOLBOX_SHORT_CODE_LIST: string = "ToolboxShortCodeList"
  static readonly TOOLBOX_SHORT_CODE_CREATE: string = "ToolboxShortCodeCreate"
  static readonly TOOLBOX_SHORT_CODE_DETAIL: string = "ToolboxShortCodeDetail"
  // 内置浏览器页（深链 web 分支与运营位 H5 承载，对齐安卓 ShellRoute.WEB_VIEW）
  static readonly WEB_PAGE: string = "WebPage"
}

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
  // 权限管理页（对齐安卓 UserRoute.PermissionManagement；设置页「消息通知」「权限管理」行跳入）
  static readonly PERMISSION_MANAGEMENT: string = "PermissionManagement"
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
  // 钱包流水页（充值+消费合并明细，入口：我的页账户卡「钱包流水」行）
  static readonly WALLET_TRANSACTIONS: string = "WalletTransactions"
  // 修改密码页（登录态接口，成功后登出回登录页；入口：设置页「其他」组）
  static readonly CHANGE_PASSWORD: string = "ChangePassword"
  // 忘记密码-重置密码页（公开接口，凭手机号+验证码重置；入口：登录页密码行下「忘记密码？」）
  static readonly RESET_PASSWORD: string = "ResetPassword"
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
  // 工具箱设备投递域路由名（对齐安卓 ToolboxRoute.Delivery / DeliverySession）
  static readonly TOOLBOX_DELIVERY: string = "ToolboxDelivery"
  static readonly TOOLBOX_DELIVERY_SESSION: string = "ToolboxDeliverySession"
  // AI 助手页（对齐安卓壳层 PageConstant.AiWork；深链 smartwash://ai-work 与工作台卡片入口）
  static readonly AI_WORK: string = "AiWork"
  // 内置浏览器页（深链 web 分支与运营位 H5 承载，对齐安卓 ShellRoute.WEB_VIEW）
  static readonly WEB_PAGE: string = "WebPage"
  // 游戏中心页（对齐安卓 GameRoute.Center；入口为深链 smartwash://game-center 与后端 workbench_entry 卡片）
  static readonly GAME_CENTER: string = "GameCenter"
  // 情绪气象站域路由名（对齐安卓 MoodRoute.Today/Calendar/Trend）
  static readonly MOOD_TODAY: string = "MoodToday"
  static readonly MOOD_CALENDAR: string = "MoodCalendar"
  static readonly MOOD_TREND: string = "MoodTrend"
  // 记忆复习域路由名（对齐安卓 ReviewRoute.Queue/Study/Materials）
  static readonly REVIEW_QUEUE: string = "ReviewQueue"
  static readonly REVIEW_STUDY: string = "ReviewStudy"
  static readonly REVIEW_MATERIAL_LIST: string = "ReviewMaterials"
  // 稍后读域路由名（对齐安卓 ReadLaterRoute.Page；入口为深链 smartwash://read-later 与后端 workbench_entry 卡片）
  static readonly READ_LATER: string = "ReadLater"
  // 订阅逃生舱域路由名（对齐安卓 SubscriptionRoute.List/Edit/Detail/Reminders）
  static readonly SUBSCRIPTION_LIST: string = "SubscriptionList"
  static readonly SUBSCRIPTION_EDIT: string = "SubscriptionEdit"
  static readonly SUBSCRIPTION_DETAIL: string = "SubscriptionDetail"
  static readonly SUBSCRIPTION_REMINDERS: string = "SubscriptionReminders"
  // 解压实验舱域路由名（对齐安卓 LabRoute：Home + 10 个实验页）
  static readonly LAB_HOME: string = "LabHome"
  static readonly LAB_BREATH: string = "LabBreath"
  static readonly LAB_BUBBLE: string = "LabBubble"
  static readonly LAB_FISH: string = "LabFish"
  static readonly LAB_DRUM: string = "LabDrum"
  static readonly LAB_SAND: string = "LabSand"
  static readonly LAB_PET: string = "LabPet"
  static readonly LAB_PAINTING: string = "LabPainting"
  static readonly LAB_FLUID: string = "LabFluid"
  static readonly LAB_SOUNDSCAPE: string = "LabSoundscape"
  static readonly LAB_STARMAP: string = "LabStarMap"
  // 建设中占位页（深链兜底：安卓新增域 mood/review/subscription/delivery/ai-work
  // 在鸿蒙端未实现时，DeepLinkRegistry 把这些 host 注册到本页，避免卡片点击成死路）
  static readonly COMING_SOON: string = "ComingSoon"
}

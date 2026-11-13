# AGENTS.md — SmartWash 鸿蒙端指南

> 智慧校园洗衣服务 App（HarmonyOS NEXT）：与 Android 端功能对齐——注册登录、洗衣套餐选购下单、订单状态追踪、优惠券、钱包充值、储物柜取寄件；并承载平台实验模块（观象台、游戏中心、实验舱、消息中心、记忆复习、订阅逃生舱、工具箱/短链/设备投递、工作台）。接口与 Android 端逐接口对齐。
>
> 相关文档：编码 agent 硬规则见 [CLAUDE.md](CLAUDE.md)；子代理定义见 [.claude/agents/](.claude/agents/)；四端联动约定见仓库根目录 CLAUDE.md。

---

## 一、技术栈

| 维度 | 技术选型 |
|------|----------|
| 语言 | ArkTS（严格模式） |
| UI | ArkUI 声明式框架 |
| 模型 | Stage 模型（UIAbility），API 5.0.5(17) |
| HTTP | @ohos/axios 2.2.6（声明在根 oh-package.json5） |
| 存储 | @kit.ArkData preferences（经 StorageUtil 封装） |
| 路由 | Navigation + router_map.json 系统路由表 + 全局 NavPathStack |
| 测试 | @ohos/hypium + @ohos/hamock（仅模板用例） |
| 包管理 | ohpm（Registry ohpm.openharmony.cn） |
| Lint | code-linter（@performance/@typescript-eslint/@security 规则集） |

---

## 二、目录结构

```
entry/src/main/ets/
├── entryability/EntryAbility.ets   # 主 UIAbility，加载 pages/Index
├── entryformability/               # EntryFormAbility（桌面卡片提供方）
├── pages/                          # 26 个页面（Index.ets 为唯一 @Entry 入口页）
│   ├── Index.ets                   # 启动分流：token + 学校信息 → Login / AddSchool / Home
│   ├── Login / Register / Home / AddSchool / ChangePassword / ResetPassword
│   ├── Laundry / Payment / PaymentSuccess / OrderList / OrderDetail
│   ├── PickUp / PickupDelivery / Recharge / RechargeRecord / WalletTransactions
│   ├── Coupon / Points / Setting / PermissionManagement / BingCampusCardAlert
│   ├── WebPage / AiWorkPage / ComingSoon
├── feature/                        # 8 个业务域（目录即模块，页面禁绕 api.ets 契约）
│   ├── divination/                 # 观象台（四术排盘 + 卦例 RDB + SSE 解读 + BYOK）
│   ├── game/                       # 游戏中心
│   ├── lab/                        # 解压实验舱（10 个纯本地玩具）
│   ├── notice/                     # 消息中心
│   ├── review/                     # 记忆复习（FSRS）
│   ├── subscription/               # 订阅逃生舱
│   ├── toolbox/                    # 工具箱（短链 + 设备投递）
│   └── workbench/                  # 工作台（底栏第 4 tab）
├── view/                           # 可复用组件
│   ├── common/AppComponents.ets    # AppButton（自带 loading 防重）等
│   ├── IndexPage / Service / UserInfo / BottomNavBar
│   └── OrderCard / OrderItemRow / LoadingFooter / DrumIllustration 等
├── widget/                         # 桌面卡片 OrderStatusCard（2x2 / 2x4）
├── network/
│   ├── Axios.ets                   # Axios 实例 + 请求/响应拦截器（401 单飞续签）
│   ├── HttpUtil.ets                # GET/POST/PUT/DELETE/upload 封装（单例）
│   ├── BaseResponse.ets            # { data, code?, message? } 信封
│   ├── PublicPaths.ets             # 公开路径白名单（token 注入判定）
│   ├── api/                        # 12 个 API 模块（与 Android 逐接口一致）
│   ├── entity/                     # 请求体 DTO
│   └── vo/                         # 响应 VO
├── constant/                       # 枚举、状态常量、DesignSystem 色值、RouteNames（60 个）
└── utils/                          # StorageUtil、PathStackUtil、Logger、TimeUtil、
    │                               # ReminderService/ReminderLogic、RefreshSignal 等
    └── deeplink/                   # 深链解析/注册表/分发（Parser / Registry / Handler）
```

---

## 三、页面与路由

- **系统路由表**：`entry/src/main/resources/base/profile/router_map.json` 注册 60 条命名路由（与 `RouteNames` 一一对应）；`main_pages` 仅保留 Index 入口；页面经 `@Builder` 函数导出给路由系统；NavDestination 页面**禁止标 `@Entry`**。
- **导航**：一律经 `utils/PathStackUtil.ets` 全局 `pathStack`（`pushPathByName` / `replacePathByName` / `pop`），禁止 `@ohos.router`。需要替换当前页（如跳登录）用 `replacePathByName`，并发 401 场景禁止连续 push。
- **传参**：页面参数用 `getParamByName` 获取——禁止 `[0] as X` 裸强转（参数缺失即崩溃），禁止字符串/number 类型错位（Laundry→Payment 曾 orderId 传成字符串）。

---

## 四、核心模式

- **鉴权流程**：请求拦截器检测 URL 以 `/web` 开头 → 从 preferences 读 token 注入 `Authorization: Bearer <token>`；响应拦截器统一处理 401 与网络错误。**401 已统一为「清 token → 清内存登录态 → replace 到登录页」+ 1.5s 防抖**（`Axios.ets` `handleUnauthorized`）。
- **HTTP 链路**：`页面 → HttpUtil.get/post<T>() → Axios 实例（拦截器）→ errorHandle → BaseResponse<T>`。
- **全局状态**：`@StorageLink('userLoginFlag')` 共享登录态；AppStorage flag + @Watch 是粗糙的事件模拟，跨组件通知优先 `@Monitor` 或 emitter。
- **按钮**：统一用 `AppButton`（自带 loading 防重），提交类操作必须接 loading。
- **色值**：走 `constant/DesignSystem.ets`，不硬编码。

---

## 五、深链（smartwash:// scheme）

完整契约以代码为准（`utils/deeplink/` 三件套 + `DeepLinkRegistry.HOST_ROUTES`，与 Android 端同构）。要点：

- **架构**：解析与执行下沉 `utils/deeplink/`（`DeepLinkParser.ets` 纯函数解析、`DeepLinkRegistry.ets` host → 路由名注册表、`DeepLinkHandler.ets` 三态分发），Want 接入在 `entryability/EntryAbility.ets`，WebView 承载页为 `pages/WebPage.ets`（路由名 `RouteNames.WEB_PAGE`）。
- **声明**：`module.json5` EntryAbility skills 独立对象声明 `scheme: "smartwash"`（`ohos.want.action.viewData` + `entity.system.browsable`），host 省略即通配。
- **消费时序**：`onCreate` / `onNewWant` 把 `want.uri` 暂存 `AppStorage('pendingDeepLinkUri')`；`pages/Home.ets` 挂载后（aboutToAppear）与热更新时（@StorageLink + @Watch）消费。未登录时暂存不丢，登录后补消费；消费点不做登录前置判断，由 401 统一兜底（对齐 Android）。
- **新页面接入**：router_map 注册路由 + `RouteNames` 加常量 + `DeepLinkRegistry.lookup` 加映射（带参 host 仿 `order` / `t` 自行解析校验），三处同步。
- **host 表**（21 个，含内建 `web`）：login/register/setting/recharge/recharge-record/coupon/order(query tab)/pickup/laundry/divination/divination-history/toolbox-short-code/game-center/delivery/lab/review/subscription/ai-work/points/messages/order-detail/{orderId}/t/{code}/web(内建)；`user-info`/`service`（tab 页非独立路由）不注册。
- **公开白名单** `PUBLIC_DEEP_LINK_HOSTS = ['web','login','register','lab']`（未登录可放行，其余 host 未登录由壳层拦截）。

---

## 六、ArkTS 硬规则

1. **`@ComponentV2` 没有 `onDidUpdate`**（只有 aboutToAppear/onReuse/aboutToRecycle/aboutToDisappear）；状态变化启停逻辑用 `@Monitor('属性名')`。历史事故：IndexPage 轮询启停静默失效。
2. **定时器必须清理**：`setInterval`/`setTimeout` 在 `aboutToDisappear` 中 clear（验证码倒计时曾泄漏）。
3. **严格模式**：`!==`/`===`（禁宽松比较）；VO 用 interface + 转换函数（不靠 class 直接接 JSON）；padding/margin 传 number；金额用 parseFloat 并校验 > 0（禁 parseInt 截断小数）。
4. **日志脱敏**：release 禁止打印完整响应体（手机号/余额），仅 DEBUG 开 body 日志。
5. **BASE_URL 禁止新增硬编码**（当前兜底为无 IP 占位(未注入时请求失败,待环境化，与 Android 分环境机制对齐）。

---

## 六之二、桌面卡片与取件提醒

- **OrderStatusCard**（`widget/` + `entryformability/`）：2x2 / 2x4 双版式，展示进行中订单（在途五态 2/3/4/5/6，优先级 6>5>4>3>2，同状态取 orderId 最大）；待取件显示取件码，其余显示更新时间；点击跳 `smartwash://order-detail/{orderId}`，无订单/未登录走引导态跳 `smartwash://order?tab=0`。`form_config.json` 声明 `updateDuration 1`（每 30 分钟刷新）。
- **取件提醒**：不是卡片，由 `utils/ReminderService` + `ReminderLogic` 经 `reminderAgentManager`（`REMINDER_TYPE_TIMER`）注册系统级定时提醒；权限 `ohos.permission.PUBLISH_AGENT_REMINDER`，登记表 orderId→reminderId 存 preferences。
- **卡片进程独立**：401 时卡片进程无 UI，清 token 后下次进 App 落登录页。

---

## 七、编码规范

- 所有对话、注释、提交信息使用中文；提交格式 `<type>(Harmony): <描述>`，一个 commit 一个完整功能点。
- 新组件优先 `@ComponentV2` + `@Param`/`@Local`/`@Event`。
- 存储经 `utils/StorageUtil.ets` 封装；toast 统一封装兜底（message 为 undefined 不弹 "undefined"）。

### 日志规范（逐页面扫描遵循）

- **统一走 `utils/Logger.ets` 的 `logger`**：四方法 `debug/info/warn/error(tag, msg, errorSummary?)`（形状对齐 Android 端 `AppLogger`）；**禁止直接调用 hilog**。
- **TAG**：调用方只传页面/模块名（小写，如 `index`、`axios`、`deeplink`、`EntryAbility`），Logger 统一拼接 `SmartWash/` 全局前缀输出（对齐 Android 端 `SmartWash/` 前缀）。
- **级别约定**：外部调用失败/异常 = error；业务拒绝、幂等命中、降级、空态预警 = warn；低频关键动作成功（登录、支付完成、订单流转等）= info；高频路径（渲染、轮询、每输入回调、每请求成功）禁止 info/warn，最多 debug。
- **构建门控**：debug 级在 release 构建直接短路（`BuildProfile.BUILD_MODE_NAME` 判定）；info/warn/error 全模式保留。
- **敏感红线**：手机号必须脱敏（如 `138****5678`）；密码、token、验证码、完整请求/响应体一律不打日志；错误只记 `error.message` 摘要 + name，禁止序列化完整 error 对象。
- **全局兜底**：未捕获异常由 `EntryAbility.onCreate` 注册的 `errorManager.on('error')` 观测统一落 error 日志，页面代码无需重复兜底记录。

---

## 八、构建与运行

```bash
ohpm install           # 安装依赖
hvigorw assembleHap    # 命令行构建
hvigorw test           # 本地单元测试
code-linter --fix      # Lint
```

- 推荐 DevEco Studio 直接 Run 'entry'；签名配置当前为空（本地注入），CI 出包需环境变量注入 signingConfigs。
- 依赖错位待修：@ohos/axios 声明在根 oh-package.json5，entry 模块 dependencies 为空。

---

## 九、接口变更联动

`network/api/` 与 Android `network/api/` 逐接口对齐。改任何接口，必须同步检查 Android 端与后端 `controller/web/`，并参照根目录 CLAUDE.md 四端联动检查表。

---

## 十、已知问题

完整评审清单（含行号）见 [../../docs/code-review-2026-08-28.md](../../docs/code-review-2026-08-28.md) 第四章。当前状态：

- baseURL 由构建参数注入,仓库不携带真实服务器地址(未注入时请求失败)——待环境化,与 Android 分环境机制对齐。
- 401 已统一为「清 token → 清内存登录态 → replace 登录页」+ 1.5s 防抖，并接入双 token 单飞续签（`/auth/refresh`）。
- `@ComponentV2` 轮询已改用 `@Monitor('isActive')`。
- Laundry→Payment 传参统一 `number` 类型；支付按钮已加 `paying` 防重、Radio 支持取消选券。
- 金额校验已改用 `parseFloat` 并校验 >0 与上限。
- 测试：`entry/src/test/` 下有 10 个用例（四术锚点、HttpUtil、PublicPaths、RequestRules、LocalUnit、List 等）。

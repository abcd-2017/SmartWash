# AGENTS.md — app（壳模块）

## 1. 模块身份

- **Gradle 坐标**：`:app`
- **包名**：`com.smartwash`
- **职责**：单 Activity 壳——聚合各域 NavGraph 路由注册、留守跨域页面（主页壳/订单详情/取件/内置浏览器）、更新流程（事件收集→弹窗）、深链分发（smartwash:// scheme：解析契约在 common:utils，host 由各域贡献者 @IntoSet 注册，壳层自持注册表与处理器）、登录态/401 事件分发、Hilt 全局注入（BASE_URL / HiltWorkerFactory）、InitEngine 启动调度、工作台入口下发（壳层 tab 页自持网络链路）。

## 2. 包结构

```
com.smartwash
├── App.kt                           # @HiltAndroidApp Application，InitEngine 启动 + WorkManager 按需初始化
├── deeplink/                        # 深链壳层聚合（解析契约在 common:utils/deeplink）
│   ├── DeepLinkRegistry.kt          # 注入 Set<DeepLinkContributor> 合并 host 总表（重复 host 告警忽略后到者）
│   ├── DeepLinkHandler.kt           # resolve（挂起，未命中查后端字典兜底）+ navigate（三态分发）
│   └── DeepLinkRepository.kt        # 路由字典拉取 + 内存缓存（失败静默返回空列表）
├── di/
│   ├── BaseUrlModule.kt             # BASE_URL 注入（BuildConfig 兜底演示地址，生产 -PbaseUrl 切换）
│   ├── DeepLinkModule.kt            # DeepLinkApi 供给 + Repository 绑定 + 壳层自有贡献者（pickup/ai-work @IntoSet）
│   └── WorkbenchModule.kt           # 工作台入口 Api/Repository 供给（壳层自有页面）
├── network/
│   └── api/
│       ├── WorkbenchEntryApi.kt     # GET /web/auth/workbench/entries + WorkbenchEntryVo（壳层自有）
│       └── DeepLinkApi.kt           # GET /web/deeplink/routes + DeeplinkRouteVo（公开接口）
├── repository/
│   ├── WorkbenchEntryRepository.kt       # 工作台入口数据契约
│   └── WorkbenchEntryRepositoryImpl.kt   # 实现（失败抛 NetworkException 交 VM 兜底）
└── ui/
    ├── activity/
    │   └── MainActivity.kt          # 单 Activity，NavHost 聚合所有 xxxGraph + 深链冷/热启动收集
    ├── navigation/
    │   └── ShellGraph.kt            # shellGraph 路由聚合（含 WebView 页）+ 壳层全局转场动画
    └── page/
        ├── PageConstant.kt          # 壳层路由常量（Pickup/AiWork/WebView）+ HomePageConstant（底部 tab）
        ├── detail/                  # 订单详情（跨域留守）
        │   ├── OrderDetailPage.kt
        │   └── OrderDetailViewModel.kt
        ├── home/                    # 主页框架（底部导航 + 嵌套 NavHost）
        │   ├── HomePage.kt
        │   └── HomeViewModel.kt
        ├── index/                   # 首页 Tab
        │   ├── IndexPage.kt
        │   └── IndexViewModel.kt
        ├── pickup/                  # 取件留守页
        │   ├── PickupPage.kt
        │   ├── PickupViewModel.kt
        │   ├── PickupDeliveryPage.kt
        │   └── PickupDeliveryViewModel.kt
        ├── webview/                 # 内置浏览器页（深链 web 分支承载；AndroidView 包原生 WebView）
        │   └── WebViewPage.kt
        ├── workbench/               # 工作台 tab 根页（入口下发 + 深链点击分发）
        │   ├── WorkbenchPage.kt
        │   ├── WorkbenchTool.kt
        │   └── WorkbenchViewModel.kt
        └── update/                  # 更新弹窗（壳层 UI）
            ├── UpdateDialog.kt
            └── UpdateFlow.kt
```

## 3. 公开 API

壳模块为应用入口，不对外暴露 API；供 IDE/Agent 理解壳层边界：

| 责任 | 说明 |
|------|------|
| NavHost 聚合 | `MainActivity` 经 `shellGraph` + `userGraph` + `orderGraph` + `paymentGraph` + `laundryGraph` + `couponGraph` + `divinationGraph` + `toolboxGraph` 完成路由注册 |
| InitEngine 调度 | `App.onCreate` 经 `applicationScope.launch { InitEngine(initTaskRegistry.getTasks()).executeAll() }` 启动各模块 InitTask |
| 登录态事件分发 | `MainActivity` 收集 `UserApi.loginEvents`（LoginEvent.LoggedOut/LoggedIn）→ 统一跳登录页，401 去重 + `launchSingleTop` |
| 深链分发 | `MainActivity`（Manifest scheme `smartwash`，singleTask）冷启动 onCreate / 热启动 onNewIntent → `pendingDeepLinkUri` → NavHost 就绪后 `DeepLinkHandler.resolve + navigate`；未登录直接放行（401 兜底拉回登录）。host 由各域贡献者注册（见下方「深链章节」），scheme 清单与 adb 示例见 docs/deeplink-对接文档.md |
| 更新流程 | `UpdateFlow` 收集 `UpdateEventBus` 事件 → 驱动 `UpdateViewModel` → `UpdateDialog` 弹窗 |
| WorkManager 工厂 | `App` 实现 `Configuration.Provider`，注入 `HiltWorkerFactory` 供 `@HiltWorker` 实例化 |

## 4. 依赖关系

- **壳层留守页面**（本模块自有）：主页壳 HomePage、首页 Tab IndexPage、订单详情 OrderDetailPage、取件 Pickup/PickupDelivery、内置浏览器 WebViewPage、工作台 tab 根页、更新弹窗 UpdateDialog/UpdateFlow
- **聚合的域模块**（全部 implementation）：
  - `:feature:user:api` + `:feature:user:impl`（用户域）
  - `:feature:order:api` + `:feature:order:impl`（订单域）
  - `:feature:payment:api` + `:feature:payment:impl`（支付域）
  - `:feature:laundry:api` + `:feature:laundry:impl`（洗衣域）
  - `:feature:coupon:api` + `:feature:coupon:impl`（优惠券域）
  - `:feature:divination`（观象台）
  - `:feature:toolbox`（工具箱短链）
  - `:feature:update`（热更新）
- **公共基建**：`:common:init`、`:common:utils`、`:common:utils`、`:common:network`、`:common:database`、`:common:ui`
- **无 Room 注解类**：app 不编译 Room 实体，ksp 处理器随 DivinationDatabase 迁入 `:feature:divination`；本模块仅声明 `libs.androidx.work.runtime.ktx` + `libs.androidx.hilt.work`（feature 的 implementation 不传递）

## 5. 内部约定

1. **单 Activity + NavHost**：全局一个 `MainActivity`，`startDestination = UserRoute.Login`；转场动画经 `shellEnterTransition/shellExitTransition` 统一，`reduceMotion` 时降级短 tween。
2. **路由常量单一事实来源**：各域路由归位各域 `Route` 常量（UserRoute/OrderRoute/PaymentRoute/LaundryRoute/CouponRoute/DivRoute/ShellRoute）；`PageConstant` 仅保留壳层留守页面（Pickup/AiWork/WebView，其中 WebView 委托 `ShellRoute.WEB_VIEW`）；`HomePageConstant` 委托各域 Route 值。
3. **域模块经 NavGraphBuilder.xxxGraph 自注册**：壳层不内联各页面 composable，一行 `xxxGraph(navController)` 完成聚合。
4. **BASE_URL 注入**：`BaseUrlModule` 读 `BuildConfig.BASE_URL`（debug/release 同值），生产经 Gradle 属性 `-PbaseUrl=https://your-domain.com/`（保留末尾斜杠）切换，并同步删除 `network_security_config.xml` 中对应明文放行。
5. **会话事件去重**：`UserApi.loginEvents` 已由拦截器幂等清 token + 事件源去重 + `navigate(launchSingleTop)`，壳层不重复清 token。
6. **更新弹窗事件驱动**：`UpdateFlow` 收集 `UpdateEventBus`（replay=1），不反向调用 `:feature:update` 检查时机；4 态弹窗分流（强制/可选/已关闭/最新）在壳层自理。
7. **深链消费时机**：`resolve` 是挂起函数（未命中本地注册表会查后端字典），只在 `LaunchedEffect(pendingDeepLinkUri)` 内调用（NavHost 组合完成后），catch 分支必须先 rethrow `CancellationException`；onCreate/onNewIntent 只写 `pendingDeepLinkUri` state，不做解析。
8. **工作台数据流**：WorkbenchViewModel → WorkbenchEntryRepository（壳层自有，app/di 供给）→ `GET /web/auth/workbench/entries`；失败/空列表回落本地兜底（含观象台品牌位之外的 7 项）；点击 target 经 `DeepLinkHandler` 分发，观象台玄墨特色卡为端上固定品牌位不参与下发。

## 5.1 深链章节（smartwash:// scheme）

**架构**：解析契约 `DeepLinkParser` / `DeepLinkAction` / `DeepLinkContributor` 在 `common:utils/deeplink`（纯 Kotlin，零路由知识、零 feature 依赖）；**各域 impl 模块在自己的 di/DeepLinkModule 以 `@Provides @IntoSet` 贡献本域 host 的路由工厂**（对齐 common:init 的 InitTask 模式）；壳层 `deeplink/DeepLinkRegistry` 注入 `Set<@JvmSuppressWildcards DeepLinkContributor>` 合并总表（重复 host 告警并忽略后到者），`DeepLinkHandler` 负责解析、后端字典兜底（`GET /web/deeplink/routes`，公开接口）与导航分发。解析分支：http(s) 直链与 `smartwash://web`（query url 必填 title 可选，均 URL decode）→ 壳层 WebView 页（`ShellRoute.WEB_VIEW`）；其余 host → 贡献者注册表。

**host 对照表**（host 由贡献者就地定义，路由串直引各域 *Route 常量——单一事实来源）：

| host | 目标路由 | 贡献者模块 | 参数 |
|------|---------|-----------|------|
| login / register / setting | UserRoute.Login/Register/Setting | :feature:user:impl di/UserDeepLinkModule | — |
| order | `Order/{tab}` | :feature:order:impl di/OrderDeepLinkModule | query `tab`（初始 tab，缺省 0，夹取 0..ShowOrderStatus.size-1） |
| recharge / recharge-record | PaymentRoute.Recharge/RechargeRecord | :feature:payment:impl di/PaymentDeepLinkModule | — |
| coupon | CouponRoute.Coupon | :feature:coupon:impl di/CouponDeepLinkModule | — |
| laundry | LaundryRoute.Laundry | :feature:laundry:impl di/LaundryDeepLinkModule | — |
| divination / divination-history | DivRoute.Home/History | :feature:divination di/DivinationDeepLinkModule | — |
| toolbox-short-code / t | ToolboxRoute.List/Detail | :feature:toolbox di/ToolboxDeepLinkModule | t：path 段 code（4-16 位字母数字，本域自校验） |
| pickup / ai-work | PageConstant.Pickup/AiWork | :app di/ShellDeepLinkModule（壳层自有） | — |
| web | Parser 内建分支 → 壳层 WebView 页 | — | query `url` 必填（须 http/https）、`title` 可选 |

**t/{code} 取舍**：映射 ToolboxRoute.Detail 仅 code 必填，详情页其余字段走注册缺省值（id=-1 等）——深链场景详情展示字段不完整，完整数据路径仍走工作台/列表页入口。`smartwash://t/{code}` 是 App 内深链，勿与 Web 跳转链接 `{toolbox.public-base-url}/web/t/{code}`（http(s) 直链只会进 WebView）混淆。

**user-info / service 不注册**：UserInfo（UserRoute.UserInfo）与 Service（LaundryRoute.Service）是 HomePage 内 HorizontalPager 的 tab 页，无独立路由栈；跳转等价「navigate(Home) 并切 pager」，需改 `ShellRoute.HOME` 注册形态并让 HomePage 从 savedStateHandle 观察初始 tab——会波及 user-impl / payment-impl 两个既有跨模块消费方与 Pager 初始页生命周期，不满足低成本标准，故不注册；深链解析为 Unknown 提示「暂不支持该链接」。

**如何为新页面注册 deeplink**：在该页所属域的 impl 模块 `di/DeepLinkModule.kt` 的贡献者 map 中加一行工厂（host 就地定义、路由串引本域 *Route 常量），无需改动壳层与 common:utils；host 全域唯一，重复注册会被 Registry 告警并忽略后到者。

## 6. 已知坑

1. **WorkManager 按需初始化**：`App` 实现 `Configuration.Provider` 注入 `HiltWorkerFactory`，且 Manifest 须移除默认 `WorkManagerInitializer`；否则 `:feature:update` 的 `ApkDownloadWorker` 实例化失败。
2. **BASE_URL 末尾斜杠**：Retrofit 要求 baseUrl 以 `/` 结尾，Gradle 属性传入时务必保留；缺失会导致接口 404。
3. **401 跳登录堆叠**：并发 401 经拦截器去重 + `launchSingleTop` 防堆叠；若未来新增事件源须同步加去重逻辑。
4. **留守页面归属**：订单详情/寄件取件语义归订单域但页面留 app 壳（T6.1 调研结论），路由常量走 `OrderRoute`；后续若订单域独立编译壳层页面须随迁。
5. **HiltWorkerFactory 与 ksp**：`libs.androidx.hilt.compiler` 在 `:feature:update` 走 ksp，app 只需声明 `libs.androidx.hilt.work`；若 app 新增 `@HiltWorker` 须自持 androidx hilt-compiler。
6. **签名配置兜底**：release 签名优先读 Gradle 属性，其次环境变量，均无则回退 `none`（构建即失败，强制必须配置）；CI 须注入 `SMART_WASH_STORE_*`。
7. **深链路由串漂移**：贡献者路由串直引各域 *Route 常量（编译期安全），但各域 NavHost **注册形态**（如 `Order/{itemId}` 的 path 段拼法、query 参数名）若变化，深链会失配——`DeepLinkHandler.navigate` 已兜住 IllegalArgumentException 降级「暂不支持」提示，不会崩导航栈，但功能不可达；改注册形态须同步对应贡献者。

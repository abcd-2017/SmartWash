# AGENTS.md — app（壳模块）

## 1. 模块身份

- **Gradle 坐标**：`:app`
- **包名**：`com.smartwash`
- **职责**：单 Activity 壳——聚合各域 NavGraph 路由注册、留守跨域页面（主页壳/订单详情/取件）、更新流程（事件收集→弹窗）、登录态/401 事件分发、Hilt 全局注入（BASE_URL / HiltWorkerFactory）、InitEngine 启动调度。

## 2. 包结构

```
com.smartwash
├── App.kt                           # @HiltAndroidApp Application，InitEngine 启动 + WorkManager 按需初始化
├── di/
│   └── BaseUrlModule.kt             # BASE_URL 注入（BuildConfig 兜底演示地址，生产 -PbaseUrl 切换）
└── ui/
    ├── activity/
    │   └── MainActivity.kt          # 单 Activity，NavHost 聚合所有 xxxGraph
    ├── navigation/
    │   └── ShellGraph.kt            # shellGraph 路由聚合 + 壳层全局转场动画
    └── page/
        ├── PageConstant.kt          # 壳层路由常量（仅剩 Pickup）+ HomePageConstant（底部 tab）
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
        └── update/                  # 更新弹窗（壳层 UI）
            ├── UpdateDialog.kt
            └── UpdateFlow.kt
```

## 3. 公开 API

壳模块为应用入口，不对外暴露 API；供 IDE/Agent 理解壳层边界：

| 责任 | 说明 |
|------|------|
| NavHost 聚合 | `MainActivity` 经 `shellGraph` + `userGraph` + `orderGraph` + `paymentGraph` + `laundryGraph` + `couponGraph` + `divinationGraph` 完成路由注册 |
| InitEngine 调度 | `App.onCreate` 经 `applicationScope.launch { InitEngine(initTaskRegistry.getTasks()).executeAll() }` 启动各模块 InitTask |
| 登录态事件分发 | `MainActivity` 收集 `UserApi.loginEvents`（LoginEvent.LoggedOut/LoggedIn）→ 统一跳登录页，401 去重 + `launchSingleTop` |
| 更新流程 | `UpdateFlow` 收集 `UpdateEventBus` 事件 → 驱动 `UpdateViewModel` → `UpdateDialog` 弹窗 |
| WorkManager 工厂 | `App` 实现 `Configuration.Provider`，注入 `HiltWorkerFactory` 供 `@HiltWorker` 实例化 |

## 4. 依赖关系

- **壳层留守页面**（本模块自有）：主页壳 HomePage、首页 Tab IndexPage、订单详情 OrderDetailPage、取件 Pickup/PickupDelivery、更新弹窗 UpdateDialog/UpdateFlow
- **聚合的域模块**（全部 implementation）：
  - `:feature:user:api` + `:feature:user:impl`（用户域）
  - `:feature:order:api` + `:feature:order:impl`（订单域）
  - `:feature:payment:api` + `:feature:payment:impl`（支付域）
  - `:feature:laundry:api` + `:feature:laundry:impl`（洗衣域）
  - `:feature:coupon:api` + `:feature:coupon:impl`（优惠券域）
  - `:feature:divination`（观象台）
  - `:feature:update`（热更新）
- **公共基建**：`:core:init`、`:common:model`、`:common:utils`、`:common:network`、`:common:database`、`:common:ui`
- **无 Room 注解类**：app 不编译 Room 实体，ksp 处理器随 DivinationDatabase 迁入 `:feature:divination`；本模块仅声明 `libs.androidx.work.runtime.ktx` + `libs.androidx.hilt.work`（feature 的 implementation 不传递）

## 5. 内部约定

1. **单 Activity + NavHost**：全局一个 `MainActivity`，`startDestination = UserRoute.Login`；转场动画经 `shellEnterTransition/shellExitTransition` 统一，`reduceMotion` 时降级短 tween。
2. **路由常量单一事实来源**：各域路由归位各域 `Route` 常量（UserRoute/OrderRoute/PaymentRoute/LaundryRoute/CouponRoute/DivRoute/ShellRoute）；`PageConstant` 仅保留壳层留守页面（Pickup）；`HomePageConstant` 委托各域 Route 值。
3. **域模块经 NavGraphBuilder.xxxGraph 自注册**：壳层不内联各页面 composable，一行 `xxxGraph(navController)` 完成聚合。
4. **BASE_URL 注入**：`BaseUrlModule` 读 `BuildConfig.BASE_URL`（debug/release 同值），生产经 Gradle 属性 `-PbaseUrl=https://your-domain.com/`（保留末尾斜杠）切换，并同步删除 `network_security_config.xml` 中对应明文放行。
5. **会话事件去重**：`UserApi.loginEvents` 已由拦截器幂等清 token + 事件源去重 + `navigate(launchSingleTop)`，壳层不重复清 token。
6. **更新弹窗事件驱动**：`UpdateFlow` 收集 `UpdateEventBus`（replay=1），不反向调用 `:feature:update` 检查时机；4 态弹窗分流（强制/可选/已关闭/最新）在壳层自理。

## 6. 已知坑

1. **WorkManager 按需初始化**：`App` 实现 `Configuration.Provider` 注入 `HiltWorkerFactory`，且 Manifest 须移除默认 `WorkManagerInitializer`；否则 `:feature:update` 的 `ApkDownloadWorker` 实例化失败。
2. **BASE_URL 末尾斜杠**：Retrofit 要求 baseUrl 以 `/` 结尾，Gradle 属性传入时务必保留；缺失会导致接口 404。
3. **401 跳登录堆叠**：并发 401 经拦截器去重 + `launchSingleTop` 防堆叠；若未来新增事件源须同步加去重逻辑。
4. **留守页面归属**：订单详情/寄件取件语义归订单域但页面留 app 壳（T6.1 调研结论），路由常量走 `OrderRoute`；后续若订单域独立编译壳层页面须随迁。
5. **HiltWorkerFactory 与 ksp**：`libs.androidx.hilt.compiler` 在 `:feature:update` 走 ksp，app 只需声明 `libs.androidx.hilt.work`；若 app 新增 `@HiltWorker` 须自持 androidx hilt-compiler。
6. **签名配置兜底**：release 签名优先读 Gradle 属性，其次环境变量，均无则回退 `none`（构建即失败，强制必须配置）；CI 须注入 `SMART_WASH_STORE_*`。

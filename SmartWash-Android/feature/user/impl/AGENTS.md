# :feature:user:impl

## 模块身份

- **Gradle 坐标**: `:feature:user:impl`
- **职责**: 用户域实现——会话管理（SessionManager/SessionEventBus）、用户网络链路（UserAccountApi/UserRepository）、登录/注册/用户中心/资料编辑/设置/权限/钱包流水页面；对外仅经 `:feature:user:api` 的 `UserApi` 契约暴露。
- **变体源集**（与 app 模块同维度 `app`，full/lite 两 flavor）:
  - `src/main`：**两变体共用**——会话、网络链路、权限管理/修改密码/重置密码页；
  - `src/full`：**full 专属**——登录/注册/设置/我的页/资料编辑/解读模型/钱包流水页及其网络链路（DivSettingApi/TransactionApi）与图贡献者（UserFlavorGraphModule）。这些页面依赖 laundry/order/payment/coupon/points/notice 等 full 专属契约，故对应依赖一律 `fullImplementation`（放进 `implementation` 会让 lite 编译缺符号）。

## 包结构

```
com.smartwash.feature.user.impl
├── UserApiImpl.kt                # @Binds UserApi 实现（门面：isLogin/getUserInfo/loginEvents 桥接）
├── UserImplConstant.kt           # 模块内常量（TOKEN/USER_ROLE DataStore 键、SEND_CAPTCHA 倒计时）
├── di/
│   ├── UserImplModule.kt         # Hilt 模块（UserApi/TokenProvider/SessionEventNotifier 绑定 + UserAccountApi 供给 + SessionInitTask 注册）
│   └── DeepLinkModule.kt         # 深链贡献者（@IntoSet：login/register/setting → UserRoute，模式同 InitTask 注册）
├── init/
│   ├── SessionInitTask.kt        # 阻塞高优先级 InitTask（priority=10），预热 token/role
│   └── HapticSettingsInitTask.kt # 触感反馈开关预热（非阻塞，priority=200）
├── network/
│   ├── api/
│   │   └── UserAccountApi.kt     # Retrofit 接口（验证码/注册/登录/资料/校园卡/头像）
│   ├── entity/user/
│   │   ├── LoginUser.kt          # 登录请求体
│   │   ├── RefreshTokenRequest.kt # 刷新令牌请求体（/auth/refresh）
│   │   ├── RegisterUser.kt       # 注册请求体
│   │   └── UpdateUserInfo.kt     # 更新用户信息请求体
│   └── vo/user/
│       ├── LoginVo.kt            # 登录响应 VO（token/role）
│       ├── TokenPairVo.kt        # 刷新令牌响应 VO（token/refreshToken，一次性轮换）
│       └── UserInfoVo.kt         # 用户信息响应 VO
├── repository/
│   └── UserRepository.kt         # 用户数据仓库（验证码/注册/登录/资料/校园卡/头像）
├── session/
│   ├── SessionManager.kt         # 会话管理（TokenProvider 实现）：内存缓存 + DataStore 持久化
│   ├── TokenRefresherImpl.kt     # 双 token 续签（TokenRefresher 实现）：单飞 + 裸客户端调 /auth/refresh
│   └── SessionEventBus.kt        # 会话事件总线（SessionEventNotifier 实现）：四类事件 + 去重窗口
└── ui/
    ├── UserGraph.kt              # NavGraphBuilder.userGraph 路由注册（仅两变体共用页面）
    ├── LocalCheckUpdateContent.kt # 设置页「检查更新」插槽（壳层注入，feature:update 归壳层聚合）
    ├── changepassword/           # 修改密码页（两变体共用）
    ├── permission/               # 权限管理页（两变体共用，含触感反馈开关）
    ├── resetpassword/            # 重置密码页（两变体共用）
    └── [src/full] 以下为 full 专属页面与网络链路（包名同上，源集在 src/full）
        ├── di/
        │   ├── UserFlavorGraphModule.kt # full 图贡献者（Login/Register/Setting/UpdateUserInfo/ApiConfig/WalletTransactions 注册 + 认证转场）
        │   └── UserFlavorModule.kt      # full 网络供给（DivSettingApi/TransactionApi 的 @Provides）
        ├── network/
        │   ├── api/{DivSettingApi,TransactionApi}.kt
        │   ├── entity/div/SaveUserApiConfig.kt
        │   └── vo/div/{PlatformModelVo,UserApiConfigVo}.kt, vo/user/TransactionVo.kt
        ├── paging/TransactionPagingSource.kt
        ├── repository/DivSettingRepository.kt
        └── ui/
            ├── login/{LoginPage,LoginViewModel}.kt
            ├── register/{RegisterPage,RegisterViewModel}.kt
            ├── setting/{SettingPage,SettingViewModel,ApiConfigPage}.kt
            ├── update_userinfo/{UpdateUserInfoPage,UpdateUserInfoViewModel}.kt
            ├── userinfo/{UserInfoPage,UserInfoViewModel,UnreadBadgeFormatter}.kt
            └── wallet/{WalletTransactionsPage,WalletTransactionsViewModel}.kt
```

## 公开 API

本模块**对外无直接暴露 API**——跨模块消费方一律经 `:feature:user:api` 的 `UserApi` 访问。

**模块内暴露**:
- `UserApiImpl`：`@Singleton`，Hilt `@Binds UserApi → UserApiImpl`
- `SessionManager`：`@Singleton`，实现 `TokenProvider`（`currentToken()` / `clearToken()`），`@Binds TokenProvider → SessionManager`
- `TokenRefresherImpl`：`@Singleton`，实现 `TokenRefresher`（`refreshBlocking()` 单飞续签，裸 OkHttp 调 `/auth/refresh`，成功经 `SessionManager.updateTokens` 覆盖双 token），`@Binds TokenRefresher → TokenRefresherImpl`
- `SessionEventBus`：`@Singleton`，实现 `SessionEventNotifier`（`notifyNeedLogin()` / `notifyUnauthorized()`），`@Binds SessionEventNotifier → SessionEventBus`
- `SessionInitTask`：`@IntoSet InitTask`，`taskId="sessionWarmUp"`，`priority=10`，`blocking=true`
- `NavGraphBuilder.userGraph(navController, checkUpdateContent?)`：路由聚合扩展函数

## 依赖关系

### 本模块依赖

**两变体共用（`implementation`，src/main）**:
- `:feature:user:api`（UserApi / UserRoute / LoginEvent / UserInfo）
- `:common:utils`（ApiResult / RequestState / SharePreferenceUtils / 触感反馈）
- `:common:network`（Retrofit / @RequireAuthorization / NetworkException / TokenProvider / SessionEventNotifier）
- `:common:ui`（清氧设计系统）
- `:common:init`（InitTask 基类）

**full 专属（`fullImplementation`，src/full）**:
- `:feature:laundry:api`（SchoolSearchSource/SchoolOption — 资料编辑页搜学校）
- `:feature:order:api`（OrderRoute — 我的页「我的订单」入口跨域跳转）
- `:feature:payment:api`（PaymentRoute — 我的页/钱包流水页「去充值」跨域跳转）
- `:feature:coupon:api`（CouponRoute — 我的页「优惠券」入口）
- `:feature:points:api`（PointsRoute — 我的页「签到有礼」入口）
- `:feature:notice:api`（NoticeRoute / NoticeApi — 我的页「消息中心」入口 + 未读角标消费未读数）
- Paging 3（钱包流水分页）/ Coil（头像加载）/ compose-animation（认证转场）

> 依赖方向恒为 impl → 他人 api（铁律 2），无任何 impl → impl；`fullImplementation` 只是把跨域契约挡在 lite classpath 之外，不改变方向。

### 谁依赖本模块
- **无**——impl 模块禁止被其他 feature-impl 依赖（依赖铁律）。仅 app 壳经 `implementation project(':feature:user:impl')` 聚合 `userGraph`（app 的 full/lite flavor 自动匹配本模块同名 flavor）。

## 内部约定

1. **TokenProvider 契约**: `SessionManager.currentToken()` 必须无 IO、无挂起（拦截器同步调用），内存未预热返回空串
2. **SessionEventBus 去重**: 网络层事件（NeedLogin/Unauthorized）走 1500ms 去重窗口，用户主动操作（LoggedIn/LoggedOut）直接广播不去重
3. **桥接协程生命周期**: `UserApiImpl` 用 `SupervisorJob() + Dispatchers.Default` 常驻桥接 SessionEventBus → loginEvents，异常互不拖垮
4. **DataStore 键名冻结**: `UserImplConstant.TOKEN = "user_token"` / `USER_ROLE = "user_role"` 沿用 app 时期取值，禁止修改
5. **school 映射容错**: `UserInfoVo.toUserInfo()` 中 `schoolVo` 字段逐项 `?: -1L` / `?: ""` / `?: 0` 容错，避免未绑定学校用户触发 NPE
6. **幂等清 token**: `SessionManager.clearToken()` 幂等——已为空时不重复写盘，防并发多请求同时 401 重复落盘
7. **设置页插槽**: `UserGraph` 的 `checkUpdateContent` 参数由 app 壳注入（feature:update 归壳层聚合），user-impl 不依赖 feature:update
8. **续签单飞**: `TokenRefresherImpl.refresh()` 并发 401 只发一次刷新（`inFlight` CompletableDeferred 共享结果，Mutex 只护读取-置入竞速）——refreshToken 一次性轮换，重复刷新即重放失败；续签必须走自建裸 OkHttp 客户端（不挂业务拦截器防递归、不与主客户端共享 Dispatcher 防饿死死锁）；`refreshBlocking()` 的 runBlocking 仅允许发生在 OkHttp 拦截器线程（主通道全为 Retrofit suspend，无主线程路径）
9. **未读角标独立链路**: `UserInfoViewModel.loadUnreadCount()` 经 `:feature:notice:api` 的 `NoticeApi` 取未读数，失败仅留痕（角标隐藏），不影响用户信息主流程；页面 active / ON_RESUME 触发（从消息中心返回时刷新）

## 已知坑

- **DataStore 键名 = 登录态**: 改 `TOKEN`/`USER_ROLE` 取值等于丢登录态，需数据迁移
- **school 空对象 NPE**: 后端对未绑定学校用户返回空 `schoolVo`（Gson 经 Unsafe 绕过非空检查置 null），映射必须逐项容错
- **预热竞态**: `SessionInitTask`（blocking=true, priority=10）保证预热先于 UpdateInitTask（900）完成；早期请求若早于预热完成，`currentToken()` 返回空串 → 请求未带鉴权 → 服务端 401 → 走重新登录流程（行为可恢复）
- **头像上传 okhttp 依赖**: 直接消费 `okhttp3.MultipartBody.Part`，按惯例显式声明 `implementation libs.okhttp`（不依赖 common:network 的 api 传递）
- **SessionEvent 四类**: NeedLogin/Unauthorized（网络层触发，走去重） + LoggedIn/LoggedOut（用户操作触发，不走去重）——新增事件类型须明确归类
- **flavor 源集与依赖配置必须成对**: 在 `src/full` 新增引用 full 专属契约的代码时，对应依赖必须加 `fullImplementation`；加进 `implementation` 会让 lite 变体编译缺符号。新增 full 专属页面时，路由注册放 `src/full` 的 `UserFlavorGraphModule`（与页面实现同源），不要放 `src/main` 的 `UserGraph`——Login/Register/Setting 三条路由在 lite 由 `app/src/lite` 的 `liteGraph` 注册，同路由重复注册在 Navigation 2.8 是「后注册覆盖」。
- **本模块 flavor 维度名固定 `app`**: 与 app 模块一致，改名会导致 app 的 `project(':feature:user:impl')` 无法匹配变体。

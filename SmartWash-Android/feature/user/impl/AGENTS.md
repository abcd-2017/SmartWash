# :feature:user:impl

## 模块身份

- **Gradle 坐标**: `:feature:user:impl`
- **职责**: 用户域实现——会话管理（SessionManager/SessionEventBus）、用户网络链路（UserAccountApi/UserRepository）、5 组页面（登录/注册/用户中心/资料编辑/设置）；对外仅经 `:feature:user:api` 的 `UserApi` 契约暴露。

## 包结构

```
com.smartwash.feature.user.impl
├── UserApiImpl.kt                # @Binds UserApi 实现（门面：isLogin/getUserInfo/loginEvents 桥接）
├── UserImplConstant.kt           # 模块内常量（TOKEN/USER_ROLE DataStore 键、SEND_CAPTCHA 倒计时）
├── di/
│   └── UserImplModule.kt         # Hilt 模块（UserApi/TokenProvider/SessionEventNotifier 绑定 + UserAccountApi 供给 + SessionInitTask 注册）
├── init/
│   └── SessionInitTask.kt        # 阻塞高优先级 InitTask（priority=10），预热 token/role
├── network/
│   ├── api/
│   │   └── UserAccountApi.kt     # Retrofit 接口（验证码/注册/登录/资料/校园卡/头像）
│   ├── entity/user/
│   │   ├── LoginUser.kt          # 登录请求体
│   │   ├── RegisterUser.kt       # 注册请求体
│   │   └── UpdateUserInfo.kt     # 更新用户信息请求体
│   └── vo/user/
│       ├── LoginVo.kt            # 登录响应 VO（token/role）
│       └── UserInfoVo.kt         # 用户信息响应 VO
├── repository/
│   └── UserRepository.kt         # 用户数据仓库（验证码/注册/登录/资料/校园卡/头像）
├── session/
│   ├── SessionManager.kt         # 会话管理（TokenProvider 实现）：内存缓存 + DataStore 持久化
│   └── SessionEventBus.kt        # 会话事件总线（SessionEventNotifier 实现）：四类事件 + 去重窗口
└── ui/
    ├── UserGraph.kt              # NavGraphBuilder.userGraph 路由注册（4 页面聚合）
    ├── login/
    │   ├── LoginPage.kt          # 登录页 Composable
    │   └── LoginViewModel.kt     # 登录页 ViewModel
    ├── register/
    │   ├── RegisterPage.kt       # 注册页 Composable
    │   └── RegisterViewModel.kt  # 注册页 ViewModel
    ├── setting/
    │   ├── SettingPage.kt        # 设置页 Composable（登出入口）
    │   └── SettingViewModel.kt   # 设置页 ViewModel
    ├── update_userinfo/
    │   ├── UpdateUserInfoPage.kt  # 资料编辑页 Composable
    │   └── UpdateUserInfoViewModel.kt
    └── userinfo/
        ├── UserInfoPage.kt       # 用户中心主页 Composable
        └── UserInfoViewModel.kt  # 用户中心主页 ViewModel
```

## 公开 API

本模块**对外无直接暴露 API**——跨模块消费方一律经 `:feature:user:api` 的 `UserApi` 访问。

**模块内暴露**:
- `UserApiImpl`：`@Singleton`，Hilt `@Binds UserApi → UserApiImpl`
- `SessionManager`：`@Singleton`，实现 `TokenProvider`（`currentToken()` / `clearToken()`），`@Binds TokenProvider → SessionManager`
- `SessionEventBus`：`@Singleton`，实现 `SessionEventNotifier`（`notifyNeedLogin()` / `notifyUnauthorized()`），`@Binds SessionEventNotifier → SessionEventBus`
- `SessionInitTask`：`@IntoSet InitTask`，`taskId="sessionWarmUp"`，`priority=10`，`blocking=true`
- `NavGraphBuilder.userGraph(navController, checkUpdateContent?)`：路由聚合扩展函数

## 依赖关系

### 本模块依赖
- `:feature:user:api`（UserApi / UserRoute / LoginEvent / UserInfo）
- `:feature:payment:api`（PaymentRoute — 用户中心「去充值」跨域跳转）
- `:feature:order:api`（OrderApi/OrderRoute/ShowOrderStatus — 用户中心订单计数与跳订单页）
- `:feature:laundry:api`（SchoolSearchSource/SchoolOption — 资料编辑页搜学校）
- `:feature:coupon:api`（CouponRoute — 用户中心「优惠券」入口）
- `:common:utils`（ApiResult / RequestState）
- `:common:network`（Retrofit / @RequireAuthorization / NetworkException / TokenProvider / SessionEventNotifier）
- `:common:ui`（清氧设计系统）
- `:common:utils`（SharePreferenceUtils / 触感反馈）
- `:common:init`（InitTask 基类）

### 谁依赖本模块
- **无**——impl 模块禁止被其他 feature-impl 依赖（依赖铁律）。仅 app 壳聚合 `userGraph`。

## 内部约定

1. **TokenProvider 契约**: `SessionManager.currentToken()` 必须无 IO、无挂起（拦截器同步调用），内存未预热返回空串
2. **SessionEventBus 去重**: 网络层事件（NeedLogin/Unauthorized）走 1500ms 去重窗口，用户主动操作（LoggedIn/LoggedOut）直接广播不去重
3. **桥接协程生命周期**: `UserApiImpl` 用 `SupervisorJob() + Dispatchers.Default` 常驻桥接 SessionEventBus → loginEvents，异常互不拖垮
4. **DataStore 键名冻结**: `UserImplConstant.TOKEN = "user_token"` / `USER_ROLE = "user_role"` 沿用 app 时期取值，禁止修改
5. **school 映射容错**: `UserInfoVo.toUserInfo()` 中 `schoolVo` 字段逐项 `?: -1L` / `?: ""` / `?: 0` 容错，避免未绑定学校用户触发 NPE
6. **幂等清 token**: `SessionManager.clearToken()` 幂等——已为空时不重复写盘，防并发多请求同时 401 重复落盘
7. **设置页插槽**: `UserGraph` 的 `checkUpdateContent` 参数由 app 壳注入（feature:update 归壳层聚合），user-impl 不依赖 feature:update

## 已知坑

- **DataStore 键名 = 登录态**: 改 `TOKEN`/`USER_ROLE` 取值等于丢登录态，需数据迁移
- **school 空对象 NPE**: 后端对未绑定学校用户返回空 `schoolVo`（Gson 经 Unsafe 绕过非空检查置 null），映射必须逐项容错
- **预热竞态**: `SessionInitTask`（blocking=true, priority=10）保证预热先于 UpdateInitTask（900）完成；早期请求若早于预热完成，`currentToken()` 返回空串 → 请求未带鉴权 → 服务端 401 → 走重新登录流程（行为可恢复）
- **头像上传 okhttp 依赖**: 直接消费 `okhttp3.MultipartBody.Part`，按惯例显式声明 `implementation libs.okhttp`（不依赖 common:network 的 api 传递）
- **SessionEvent 四类**: NeedLogin/Unauthorized（网络层触发，走去重） + LoggedIn/LoggedOut（用户操作触发，不走去重）——新增事件类型须明确归类

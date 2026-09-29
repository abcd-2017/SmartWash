# CLAUDE.md

本文件为编码 agent 在 SmartWash Android 端工作时提供指导。仓库总纲见根目录 [CLAUDE.md](../CLAUDE.md)。

**遵守共享规则**：STOP规则、派发红线、协作流程、Git工作流、冲突协议等见 [docs/agents/shared-rules.md](../../docs/agents/shared-rules.md)。提交规范见根目录 CLAUDE.md。

**必须使用中文回答。**

## 基本规则

- **新增页面必须注册路由** — 各域页面在本域 `*Graph.kt`（如 `OrderGraph.kt`）中注册 composable，壳层 `MainActivity` 经 `NavGraphBuilder.xxxGraph` 聚合；壳层留守页面（取件页等）注册于 `shellGraph`。路由常量以各域 `*Route` 密封类为单一事实来源（`OrderRoute`/`PaymentRoute`/`UserRoute`/`LaundryRoute`/`CouponRoute`/`DivRoute`）。
- **API 接口遵循既有模式** — 需要认证的接口加 `@RequireAuthorization` 注解；返回值统一 `ApiResult<T>` 包装。
- **异步状态统一使用 `RequestState`** — ViewModel 中所有网络请求状态用 `RequestState`（Idle/Loading/Success/Error）管理，页面通过 `StateFlow` 收集。
- **遵循 MVVM 模式** — 每个页面一个 `*Page.kt` + 一个 `*ViewModel.kt`，ViewModel 通过 Repository（或跨域经 api 接口）访问数据，Page 只负责 UI 渲染。
- **禁止字符串硬编码** — 用户可见文本一律定义在模块内 `res/values/strings.xml`，代码经 `stringResource(R.string.xxx)` 引用；ViewModel 内经 `application.getString(...)`；非用户可见常量（存储 key、TAG）定义在对应常量类中。

## 构建与运行

```bash
./gradlew assembleDebug    # Debug APK
./gradlew assembleRelease  # Release APK（ProGuard 混淆 + 资源收缩）
./gradlew test             # JVM 单元测试
./gradlew lint             # 代码检查
./gradlew feature:divination:test  # 观象台算法锚点单测（亦含于全量 test）
```

- **环境配置**：BASE_URL 由 `app/build.gradle` 通过 Gradle 属性 `baseUrl` 注入（兜底为演示服务器 `http://8.148.70.81:9000/`），生产通过 `-PbaseUrl=https://your-domain.com/` 注入。代码读 `BuildConfig.BASE_URL`，禁止硬编码 URL。另有 `DIVINATION_BASE_URL`（观象台 LLM 网关，当前与 BASE_URL 一致）。
- Manifest 中 `usesCleartextTraffic=true` 为 demo 项目全局放行明文 HTTP，并声明 `REQUEST_INSTALL_PACKAGES`（APK 热更新）与 FileProvider（APK 安装）。**发版前须按生产地址改为 HTTPS 并移除该开关**。
- Maven 仓库用阿里云镜像（当前注释，启用需同时取消 `settings.gradle` 与 `build-logic` 两处注释）；海外构建需改回 `google()` / `mavenCentral()`。

## 项目架构

智慧校园洗衣服务 App，**多模块 Gradle 项目**，MVVM + Jetpack Compose + Hilt。已完成模块化重构（阶段 0–8，提交 3276513 起），从单模块拆为 16 个 Gradle 模块。

### 模块地图

```
:app                             壳：Application + MainActivity(NavHost 聚合) + 更新弹窗 UI + 取件留守页
:core:init                      InitTask 契约 + InitEngine（零业务依赖：仅 Android SDK + Hilt + coroutines）
:common:model                   跨模块共享：ApiResult 信封 / PageData / HttpStatusCode / RequestState
:common:utils                   DataStore 封装(SharePreferenceUtils) / RequestState / 动效触感 / 二维码 / pagingFlow
:common:network                 OkHttp/Retrofit 供给 / 鉴权+错误转译拦截器 / @RequireAuthorization / NetworkException
:common:database                Room 主缓存库(AppDatabase v3)：洗衣项目/学校/优惠券三表（共库，T8.2 决策不拆）
:common:ui                      清氧设计系统(theme) + 共享组件(components) + ShellRoute 壳层路由契约
:feature:update                 热更新全链路 + UpdateInitTask（InitTask 首业务案例）
:feature:divination             观象台占卜子系统（四套算法内核 + 卦历库 + 解读网关 + 7 页面 + 独立库）
:feature:user:api               UserApi 契约 + User 模型 + UserRoute（登录态/用户信息/登录事件）
:feature:user:impl              SessionManager + UserRepository + 登录/注册/用户中心/资料编辑/设置页
:feature:order:api              OrderApi 契约 + 订单模型 + OrderRoute（含寄件取件路由常量）
:feature:order:impl             OrderRepository + 订单页
:feature:payment:api            PaymentRoute 路由常量（零跨模块数据消费）
:feature:payment:impl           支付 + 充值网络链路与页面
:feature:laundry:api            LaundryRoute + 学校搜索最小契约（SchoolSearchSource）
:feature:laundry:impl           Laundry/School Repository + 洗衣预约/服务页（内存→Room→网络 缓存降级）
:feature:coupon:api             CouponApi 契约 + 优惠券模型 + CouponRoute
:feature:coupon:impl            CouponRepository + 优惠券页
build-logic                     convention plugin（smartwash.android.library / smartwash.compose / smartwash.hilt）
```

### 六条依赖铁律（强制，`scripts/check-deps.sh` 静态校验前四条）

1. **方向单向**：`app → feature:impl → feature:api → common → core:init`，禁止反向。
2. **feature 间仅 impl → 他人 api**：禁止 `impl→impl`、禁止 `api→api`、禁止 `api→任何 feature`。
3. **core:init 零项目依赖**：不依赖 common 任何模块、不依赖 Compose。
4. **common 不依赖 feature**。
5. **业务模型跟各自 api 模块走**；`common:model` 只放 `ApiResult`/`PageData`/`HttpStatusCode`/`RequestState` 等真共享物。
6. **构建配置统一 convention plugin**（build-logic），模块 `build.gradle` 只声明差异依赖。

### 核心机制

**InitTask（core:init）** — 启动任务抽象（taskId/priority/dependencies/blocking/timeoutMs/suspend execute），`InitEngine` 拓扑排序 + 阻塞串行/非阻塞 launch 即返回 + 超时保护 + 进度 StateFlow。收集用 Hilt `@IntoSet` 多绑定：

```kotlin
// 在各 impl 模块的 Hilt Module 里提供
@Module @InstallIn(SingletonComponent::class)
abstract class XxxInitModule {
    @Binds @IntoSet
    abstract fun bindXxxInitTask(task: XxxInitTask): InitTask
}
```

`App.onCreate` 注入 `InitTaskRegistry`，经 `applicationScope.launch { InitEngine(registry.getTasks()).executeAll() }` 调度。已接入：`SessionInitTask`（user:impl，阻塞高优先级，预热 token）、`UpdateInitTask`（feature:update，非阻塞，静默检查）。

**api/impl 服务化** — api 模块（纯 Kotlin/轻 Android，不引 Hilt 运行时）放接口 + 模型 + 路由常量；impl 模块 `@Binds Api → ApiImpl`；消费方 `@Inject` 接口。api 模块**可带 res** 先例：`feature:order:api`（OrderStatus/ShowOrderStatus 枚举的 descriptionRes）、`feature:payment:api`（无 res）、`feature:coupon:api`（枚举带 res）。判断标准：res 仅服务于本 api 模块的枚举/模型即可带。

**鉴权流程** — API 方法加 `@RequireAuthorization`，`RequestInterceptor` 经 `retrofit2.Invocation` tag 检测后从 `TokenProvider`（user:impl 的 SessionManager `@Binds`）取 token 注入 `Bearer <token>`。`ResponseInterceptor` 遇 401 清 token 并经 `SessionEventNotifier`（user:impl 的 SessionEventBus `@Binds`）通知 UI 跳登录。

**路由** — 单 Activity + NavHost。各域暴露 `NavGraphBuilder.xxxGraph` 扩展函数，壳层 `MainActivity` 聚合；路由常量单一事实来源在各域 `*Route`。壳层路由契约 `ShellRoute`（common:ui）。

**状态管理** — ViewModel 用 `MutableStateFlow` → `asStateFlow()`；`RequestState` 密封类统一异步 UI 状态。

**依赖注入** — Hilt，ViewModel 用 `@HiltViewModel` + `@Inject constructor`。注解处理统一 **KSP**。

**分页** — Paging 3，`pagingFlow`（debounce + flatMapLatest + cachedIn）统一封装在 `:common:utils`。

### Compose 硬规则

- **组合期禁止副作用**：Toast、导航、状态回写一律放 `LaunchedEffect`/`SideEffect`，禁止写在 `when(state)` 渲染分支里。
- **禁止主线程阻塞 IO**：`runBlocking` 读写 DataStore 已知会阻塞 UI 线程，一律用 suspend/flow。
- **LazyColumn 必须给 `key`**；列表参数注意稳定性，昂贵计算用 `remember`。
- **catch 协程异常先 rethrow `CancellationException`**，否则取消会被当网络错误。

## 相关 Skills 与子代理

**自动生效 skill**：`android-kotlin`、`android-jetpack-compose`（按 `.kt` 路径触发）；按需调用 `android-clean-architecture`、`mobile-android-design`。

`.claude/agents/smartwash-android.md` 提供统一的 Android 全栈代理，覆盖开发、审查、调试、测试、UI/动效专项。

## 已知坑（改动前先看）

完整清单见 [docs/code-review-2026-08-28.md](../docs/code-review-2026-08-28.md) 第二章，重点关注：

- `utils/PressFeedbackModifier.kt` 的 `pressScale/pressAlpha` 自建 InteractionSource 未接入 clickable，全项目 31 处按压反馈实际无效——修复前不要模仿该写法。
- Room 无 migration 配置；缓存写入是 deleteAll + insertAll 无事务，改动 database/ 时需补 `@Transaction`。
- ⚠️ `App.globalRequestBefore/AfterCallback` 已不存在（原静态 lateinit 回调在模块化中移除，鉴权失败改走 `SessionEventNotifier` 契约）；新增早期请求路径时勿引用该回调。
- 测试除模板类 `ExampleUnitTest` 外，还有观象台四套算法内核的锚点单测（`feature:divination/src/test/.../liuren|liuyao|meihua|qimen/*AnchorTest.kt`）；给拦截器、参数校验等纯逻辑补单测时放对应模块的 `src/test/`。
- ⚠️ **RechargePage 组合期副作用**（独立任务，本任务不修）：`RechargePage.kt` 的金额选择/支付状态分支中存在写在 `when(state)` 渲染分支里的副作用，违反 Compose 硬规则，待独立任务修复。

## ⛔ Android 特化红线操作表（绝对禁止）

| 红线 | 说明 |
|------|------|
| 跳过设计系统 | 新页面必须遵循清氧设计系统（配色/圆角/排版/阴影规范） |
| 组合期副作用 | Toast、导航、状态回写一律放 `LaunchedEffect`/`SideEffect`，禁止写在 `when(state)` 渲染分支里 |
| 主线程阻塞 IO | 禁止 `runBlocking` 读写 DataStore，一律用 suspend/flow |
| 字符串硬编码 | 用户可见文本一律定义在 `strings.xml`，经 `stringResource()` 引用 |
| 跳过各端联动检查 | 改接口必须同步检查鸿蒙端对应接口与后端 `controller/web/` |

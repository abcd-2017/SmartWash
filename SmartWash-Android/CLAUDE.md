# CLAUDE.md

本文件为编码 agent 在 SmartWash Android 端工作时提供指导。仓库总纲见根目录 [CLAUDE.md](../CLAUDE.md)。

**遵守共享规则**：STOP规则、派发红线、协作流程、Git工作流、冲突协议等见 [.claude/docs/shared-rules.md](../.claude/docs/shared-rules.md)。提交规范见根目录 CLAUDE.md。

**必须使用中文回答。**

## 基本规则

- **新增页面必须注册路由** — 各域页面在本域 `*Graph.kt`（如 `OrderGraph.kt`）中注册 composable，壳层 `MainActivity` 经 `NavGraphBuilder.xxxGraph` 聚合；壳层留守页面（取件页等）注册于 `shellGraph`。路由常量以各域 `*Route` 密封类为单一事实来源（`OrderRoute`/`PaymentRoute`/`UserRoute`/`LaundryRoute`/`CouponRoute`/`DivRoute`）。
- **API 接口遵循既有模式** — 需要认证的接口加 `@RequireAuthorization` 注解；返回值统一 `ApiResult<T>` 包装。
- **异步状态统一使用 `RequestState`** — ViewModel 中所有网络请求状态用 `RequestState`（Idle/Loading/Success/Error）管理，页面通过 `StateFlow` 收集。
- **遵循 MVVM 模式** — 每个页面一个 `*Page.kt` + 一个 `*ViewModel.kt`，ViewModel 通过 Repository（或跨域经 api 接口）访问数据，Page 只负责 UI 渲染。
- **禁止字符串硬编码** — 用户可见文本一律定义在模块内 `res/values/strings.xml`，代码经 `stringResource(R.string.xxx)` 引用；ViewModel 内经 `application.getString(...)`；非用户可见常量（存储 key、TAG）定义在对应常量类中。

## UI 设计规范（强制）

详见 [.claude/ui-design-spec.md](.claude/ui-design-spec.md)。**改任何 UI 前必读**，与原型图冲突时以该文档为准。

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
- Maven 仓库用阿里云镜像（当前注释，启用需同时取消 `settings.gradle` 与 `config/` 两处注释）；海外构建需改回 `google()` / `mavenCentral()`。

## 项目架构

智慧校园洗衣服务 App，**多模块 Gradle 项目**，MVVM + Jetpack Compose + Hilt。已完成模块化重构（阶段 0–8，提交 3276513 起），从单模块拆为 18 个 Gradle 模块。

### 模块地图

```
:app                             壳：Application + MainActivity(NavHost 聚合) + 首页(HomePage/IndexPage) + 订单详情页(OrderDetailPage) + 取件页(PickupPage/PickupDeliveryPage) + 更新弹窗(UpdateDialog/UpdateFlow) + AI 工作台页(AiWorkPage)
:common:database                Room 主缓存库(AppDatabase v3)：洗衣项目/学校/优惠券三表（共库，T8.2 决策不拆）
:common:network                 OkHttp/Retrofit 供给 / 鉴权+错误转译拦截器 / @RequireAuthorization / NetworkException
:common:utils                    跨模块共享：ApiResult 信封 / PageData / HttpStatusCode / RequestState / DataStore 封装(SharePreferenceUtils) / 动效触感 / 二维码 / pagingFlow
:common:ui                      清氧设计系统(theme) + 共享组件(components) + ShellRoute 壳层路由契约
:common:init                      InitTask 契约 + InitEngine + InitTaskRegistry（零业务依赖：仅 Android SDK + Hilt + coroutines）
:feature:coupon:api             CouponApi 契约 + 优惠券模型 + CouponRoute
:feature:coupon:impl            CouponRepository + CouponStatus + CouponApiImpl + EntityMappers + 三 Tab 页(AvailableCouponsTab/HistoricalCouponsTab/ClaimedCouponsTab)
:feature:divination             观象台占卜子系统（四套算法内核 + 卦历库 + 解读网关 + 7 页面(ask/cast/chart/followup/history/home/reading) + 独立库）
:feature:laundry:api            LaundryRoute + 学校搜索最小契约（SchoolSearchSource）
:feature:laundry:impl           Laundry/School Repository + EntityMappers + SchoolSearchSourceImpl + SchoolApi/LaundryItemsApi + 洗衣预约/服务页（内存→Room→网络 缓存降级）
:feature:order:api              OrderApi 契约 + 订单模型 + OrderRoute（含寄件取件路由常量）
:feature:order:impl             OrderRepository + OrderGroup + OrderPagingSource + OrderServiceApi + 订单 VO 实体 + 订单页
:feature:payment:api            PaymentRoute 路由常量（零跨模块数据消费）
:feature:payment:impl            PaymentRepository + RechargeRepository + RechargeRecordPagingSource + PaymentType + PaySuccessPage + 支付/充值网络链路与页面
:feature:update                 热更新全链路 + UpdateInitTask + ApkDownloadWorker + ApkInstaller + UpdateEventBus
:feature:user:api               UserApi 契约 + User 模型 + UserRoute（登录态/用户信息/登录事件）
:feature:user:impl              SessionManager + SessionEventBus + UserAccountApi + UserRepository + 登录/注册/用户中心/资料编辑/设置页
config/                          convention 脚本目录（android-library.gradle / compose.gradle / hilt.gradle，传统 apply from 方式）
```

### 六条依赖铁律（强制，`scripts/check-deps.sh` 静态校验前四条）

1. **方向单向**：`app → feature:impl → feature:api → common → common:init`，禁止反向。
2. **feature 间仅 impl → 他人 api**：禁止 `impl→impl`、禁止 `api→api`、禁止 `api→任何 feature`。
3. **common:init 零项目依赖**：不依赖 common 任何模块、不依赖 Compose。
4. **common 不依赖 feature**。
5. **业务模型跟各自 api 模块走**；`common:utils` 只放 `ApiResult`/`PageData`/`HttpStatusCode`/`RequestState` 等真共享物。
6. **构建配置统一 convention 脚本**（config/），模块 `build.gradle` 只声明差异依赖。

### 核心机制

**InitTask（common:init）** — 启动任务抽象（taskId/priority/dependencies/blocking/timeoutMs/suspend execute），`InitEngine` 拓扑排序 + 阻塞串行/非阻塞 launch 即返回 + 超时保护 + 进度 StateFlow。收集用 Hilt `@IntoSet` 多绑定：

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

详见 [android-known-pitfalls.md](.claude/android-known-pitfalls.md)。

## ⛔ Android 特化红线操作表（绝对禁止）

| 红线 | 说明 |
|------|------|
| 跳过设计系统 | 新页面与改 UI 必须遵循 [.claude/ui-design-spec.md](.claude/ui-design-spec.md) 的令牌、间距、圆角、动效全部规则 |
| 自创令牌值 | 配色 / 间距 / 圆角一律取自规范 §2 与 `common/ui/theme/`，禁止在页面内写死数值 |
| 引入外部图标库 | 已调研否决（规范 §4.1）。自绘图标仅限规范 §4.5 的 12 个 |
| 重绘 Material 图标 | 存量 `Icons.*` 一律保持内置，不替换、不重绘、不做与规范无关的改动 |
| 位图图标 / AI 生图直入 | 图标一律矢量；AI 生图只能出草图，最终人工重绘为 VectorDrawable |
| 无降级的动效 | 新增动效必须提供 reduced motion 分支（`LocalReduceMotion` / `motionSpec()`），否则不予合入 |
| 伪造状态动画 | 支付成功 / 订单完成 / 算法结果的动画，必须由真实状态触发 |
| 引入动画库 | 禁止 Lottie / Rive / Shimmer / Vico；规范覆盖范围内零依赖可完成 |
| 组合期副作用 | Toast、导航、状态回写一律放 `LaunchedEffect`/`SideEffect`，禁止写在 `when(state)` 渲染分支里 |
| 主线程阻塞 IO | 禁止 `runBlocking` 读写 DataStore，一律用 suspend/flow |
| 字符串硬编码 | 用户可见文本一律定义在 `strings.xml`，经 `stringResource()` 引用 |
| 跳过各端联动检查 | 改接口必须同步检查鸿蒙端对应接口与后端 `controller/web/` |

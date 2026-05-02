# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指导。

## 基本规则

- **必须使用中文回答** — 所有对话、代码注释、提交信息均使用中文。
- **每次修改完必须提交 Git Commit** — 完成一个功能或修复后，立即使用中文提交信息进行 `git commit`，保持提交粒度清晰。
- **新增页面必须注册路由** — 在 `PageConstant` 中添加路由常量，在 `MainActivity` 的 `NavHost` 中注册 composable。
- **API 接口遵循既有模式** — 需要认证的接口加 `@RequireAuthorization` 注解；返回值统一使用 `ResponseData<T>` 包装。
- **异步状态统一使用 `RequestState`** — ViewModel 中所有网络请求状态用 `RequestState`（Idle/Loading/Success/Error）管理，页面通过 `StateFlow` 收集。
- **遵循 MVVM 模式** — 每个页面一个 `*Page.kt` + 一个 `*ViewModel.kt`，业务逻辑在 ViewModel 中，Page 只负责 UI 渲染。

## 构建与运行

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK（开启 ProGuard 混淆 + 资源压缩）
./gradlew assembleRelease

# 运行单元测试（JVM，不需要设备）
./gradlew test

# 运行 Android 插桩测试（需要设备或模拟器）
./gradlew connectedAndroidTest

# 运行单个测试类
./gradlew test --tests "com.smartwash.ExampleTest"

# 代码检查
./gradlew lint
```

## 项目架构

智慧校园洗衣服务 App，单模块 Gradle 项目（仅 `app/` 模块），使用 **MVVM + Jetpack Compose** + **Hilt 依赖注入**。

### 分层结构

```
ui/page/<功能>/     → Compose Page + ViewModel（页面级，一页一个 VM）
network/api/        → Retrofit 接口（通过 Hilt 注入到 ViewModel）
network/entity/     → 请求体对象 + ResponseData<T> 包装类 {code, message, data}
network/vo/         → 服务端返回的值对象
network/interceptor/ → OkHttp 拦截器（鉴权 & 错误处理）
utils/              → 本地存储（DataStore）、RequestState 状态类、枚举常量
paging/             → PagingSource 分页实现
database/           → Room 数据库（已搭建，暂未启用）
```

### 核心设计模式

**单 Activity** — `MainActivity` 通过 `NavHost` 承载所有页面。路由常量定义在 `PageConstant` 密封类中。页面切换使用 Compose Navigation 的滑动 + 淡入淡出动画。

**鉴权流程** — 需要登录的 API 加 `@RequireAuthorization` 注解。`RequestInterceptor` 检测该注解后自动从 DataStore 中取出 token 并注入请求头 `Bearer <token>`。`ResponseInterceptor` 解析每个响应的 body；遇到 401 时清除本地 token 并触发 `App.globalRequestAfterCallback`，跳转回登录页。

**状态管理** — 每个 ViewModel 通过 `MutableStateFlow` → `asStateFlow()` 暴露状态。页面以 Compose State 方式收集。`RequestState` 密封类（Idle / Loading / Success / Error）是统一的异步 UI 状态模式。

**依赖注入** — `RetrofitClient` 是 Hilt `@Module`，提供 Retrofit 单例及所有 API 接口实例。ViewModel 使用 `@HiltViewModel` 配合 `@Inject constructor` 获取依赖。

**分页** — `OrderPagingSource` 和 `UserCouponPagingSource` 实现 `PagingSource<Int, T>`，采用页码分页。

### API 返回格式

所有接口返回数据统一使用 `ResponseData<T>` 包装：

- `code: Int` — 业务状态码（参见 `HttpStatusCode` 枚举）
- `message: String` — 错误或成功提示信息
- `data: T?` — 可空数据载荷

### 依赖说明

- Maven 仓库使用阿里云镜像（国内下载更快）。如果在海外构建，需要改回 `google()` / `mavenCentral()`。
- Hilt 使用 KAPT 注解处理，Room 使用 KSP。
- Manifest 中开启了 `usesCleartextTraffic=true`，因为后端是 HTTP 协议而非 HTTPS。
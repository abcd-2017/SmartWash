# AGENTS.md — feature:update（应用热更新链路）

## 1. 模块身份

- **Gradle 坐标**：`:feature:update`
- **包名**：`com.smartwash.feature.update`
- **职责**：应用热更新全链路——启动静默版本检查（InitTask）/ 预签名下载地址获取 / WorkManager 后台 APK 下载 / FileProvider 安装；弹窗 UI 留壳层 app，本模块经 Hilt 聚合对外提供 `UpdateViewModel` / `UpdateState` / `AppVersionVo` / `ApkInstaller`。

## 2. 包结构

```
com.smartwash.feature.update
├── di/
│   ├── UpdateModule.kt              # Hilt 供给：AppUpdateApi + WorkManager
│   └── UpdateInitModule.kt          # Hilt 多绑定：UpdateInitTask 经 @IntoSet 注册进 InitTaskRegistry
├── event/
│   └── UpdateEventBus.kt            # 更新事件总线（replay=1 SharedFlow，feature → 壳层广播）
├── init/
│   └── UpdateInitTask.kt            # 非阻塞 InitTask（priority=900，启动静默检查）
├── model/
│   └── AppVersionVo.kt              # 版本信息 VO（versionCode / versionName / forceUpdate 等）
├── network/
│   └── AppUpdateApi.kt              # Retrofit 接口：getLatestVersion + getPresignedDownloadUrl
├── repository/
│   └── AppUpdateRepository.kt       # 更新仓库（版本比对 + 预签名地址获取）
├── service/
│   ├── ApkDownloadWorker.kt         # @HiltWorker 后台下载（OkHttp，每 5% 进度回调）
│   └── ApkInstaller.kt              # APK 安装器（FileProvider URI + 安装 Intent）
└── ui/
    └── UpdateViewModel.kt           # 更新 UI 状态机（UpdateState 密封类）
```

## 3. 公开 API

| 暴露点 | 消费方 | 说明 |
|--------|--------|------|
| `UpdateViewModel` | app 壳 UpdateFlow / CheckUpdateSettingRow | 状态机驱动 UI（Idle/Checking/UpdateAvailable/Downloading/Downloaded/Installing/LatestVersion/Error） |
| `UpdateState` | app 壳 | 密封类，覆盖检查→下载→安装全链路异步状态 |
| `UpdateEventBus` | app 壳 MainActivity | 事件总线（replay=1），发现新版本广播 |
| `AppVersionVo` | app 壳 | 版本信息 VO |
| `ApkInstaller` | app 壳 / 设置页 | APK 安装器（object，静态调用） |
| `UpdateInitTask` | App.onCreate 经 InitEngine | 启动静默检查（@IntoSet 自动注册） |

## 4. 依赖关系

- **本模块依赖**：`:common:utils`（ApiResult）、`:common:network`（Retrofit + NetworkException）、`:common:init`（InitTask 基类）、`libs.androidx.work.runtime.ktx` + `libs.androidx.hilt.work`（WorkManager + @HiltWorker）、`libs.okhttp`（ApkDownloadWorker 直接消费）
- **谁依赖本模块**：`:app`（壳层聚合 UpdateFlow + 消费 UpdateViewModel/UpdateEventBus）
- **HiltWorker 处理器自持**：`libs.androidx.hilt.compiler` 走 ksp（smartwash.hilt convention plugin 只带 dagger 的，不传递 androidx 的）
- **不引 smartwash.compose / common:ui**：调研结论——链路内无 Compose/公共 UI 组件消费

## 5. 内部约定

1. **InitTask 接入模式**：`UpdateInitTask` 实现 `InitTask()`，经 `UpdateInitModule` 的 `@Provides @IntoSet` 多绑定汇入集合；`App.onCreate` 由 `InitEngine` 统一调度，壳层不感知检查时机。
2. **非阻塞低优先级**：`blocking = false`，`priority = 900`（晚于其他就绪任务出队，不拖慢启动）；网络检查直接挂起（默认 10s 超时）。
3. **事件驱动解耦**：检查出新版本经 `UpdateEventBus.notifyUpdateAvailable` 广播；壳层 `MainActivity` 收集事件转弹窗状态，不反向调用本模块。
4. **replay = 1 防丢失**：启动检查在 `App.onCreate` 即发起（InitEngine 非阻塞任务），可能早于 `MainActivity` 订阅；无重放则事件被丢弃、本次启动不弹更新；重复打扰由 `UpdateViewModel` 的「已关闭版本」防重挡下。
5. **WorkManager @HiltWorker**：`ApkDownloadWorker` 经 `@AssistedInject` 实例化（两参构造），需 `App` 注入 `HiltWorkerFactory` + Manifest 移除默认 `WorkManagerInitializer`，否则反射工厂找不到构造。
6. **版本比对走 PackageManager**：`AppUpdateRepository.getCurrentVersionCode()` 经 `context.packageManager.getPackageInfo` 读取（library 模块不可见宿主 BuildConfig），读取失败兜底 `Int.MAX_VALUE`（系统异常不打扰用户弹更新）。
7. **预签名下载地址**：APK 下载走 MinIO 预签名临时链接（`getPresignedDownloadUrl`），不暴露存储桶结构；下载后校验 SHA256（可选）。

## 6. 已知坑

1. **WorkManager 初始化时机**：`App` 必须实现 `Configuration.Provider` 注入 `HiltWorkerFactory`，且 Manifest 须移除默认 `WorkManagerInitializer`；否则 `ApkDownloadWorker` 两参构造反射失败，下载链路不可用。
2. **ksp 处理器分离**：`androidx.hilt.compiler` 必须本模块自持（smartwash.hilt convention 只带 dagger 的），删除后 `@HiltWorker` 注解处理失败，Worker 无法实例化。
3. **UpdateEventBus replay 语义**：replay=1 保证晚订阅者不丢事件，但壳层在 `UpdateViewModel` 内须维护「已关闭版本」集合防重弹；重复事件不处理会反复弹窗。
4. **OkHttp 直传**：`ApkDownloadWorker` 直接消费 OkHttp（非 Retrofit），`libs.okhttp` 须显式声明；下载大文件注意 `kotlinx.coroutines.yield()` 让出线程。
5. **FileProvider 配置**：`ApkInstaller` 走 `${packageName}.fileprovider`，壳层 Manifest 须声明对应 `<provider>` + `file_paths.xml` 包含 `cache-path`；缺失则安装 Intent 抛 FileUriExposedException。
6. **forceUpdate 分流**：`UpdateAvailable.version.forceUpdate = true` 为强制更新，壳层沿用 4 态弹窗分流；本期逻辑在壳层 `UpdateFlow`，本模块不处理 UI 分支。
7. **前台服务类型双声明缺一即崩（targetSdk 34+）**：`setForeground` 的 `ForegroundInfo` 必须携带 `FOREGROUND_SERVICE_TYPE_DATA_SYNC`（所有调用点统一走 `buildForegroundInfo`），且本模块 Manifest 须覆盖声明 `SystemForegroundService` 的 `foregroundServiceType="dataSync"`——WorkManager 自带清单未声明任何类型，两处缺一即抛 `InvalidForegroundServiceTypeException`；且 `SystemForegroundService` 以 `START_REDELIVER_INTENT` 运行，残留的下载任务会让 App 每次冷启动即崩（崩溃循环），修复时勿漏改任何一处 `setForeground` 调用点。

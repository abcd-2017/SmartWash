# AGENTS.md — :feature:update:impl（热更新 full 变体实现）

## 1. 模块身份

- **Gradle 坐标**：`:feature:update:impl`
- **包名 / namespace**：`com.smartwash.feature.update`（与模块路径解耦）
- **职责**：热更新 **full 变体专属**的启动静默检查 + 更新 UI 状态机。下载/安装/版本比较内核已抽至 `:feature:update:core`（两变体共用）；弹窗 UI 留壳层 app 的 `app/src/full`。
- **lite 变体不依赖本模块**：`UpdateInitTask` 的 `@IntoSet` 注册随模块缺失而消失，即 lite 不注册启动静默检查。

## 2. 包结构

```
com.smartwash.feature.update/
├── di/UpdateInitModule.kt     # Hilt 多绑定：UpdateInitTask @IntoSet 注册进 InitTaskRegistry
├── event/UpdateEventBus.kt    # 更新事件总线（replay=1 SharedFlow，feature → 壳层广播）
├── init/UpdateInitTask.kt     # 非阻塞 InitTask（priority=900，启动静默检查）
└── ui/UpdateViewModel.kt      # 更新 UI 状态机（UpdateState 密封类）
```

> `AppUpdateRepository` / `ApkDownloadWorker` / `ApkInstaller` / `UpdateSettingsStore` / `UpdateCoreModule`
> 已迁至 `:feature:update:core`；`R.string.download_url_empty` 等文案改用 `com.smartwash.feature.update.core.R`。

## 3. 公开 API

| 暴露点 | 消费方 | 说明 |
|--------|--------|------|
| `UpdateViewModel` | app 壳 `UpdateFlow` / `UpdateSettingRows` | 状态机（Idle/Checking/UpdateAvailable/Preparing/Downloading/Downloaded/Installing/LatestVersion/Error） |
| `UpdateState` / `ErrorSource` | app 壳 | 密封类与错误来源 |
| `UpdateEventBus` | app 壳 full 变体 | 事件总线（replay=1） |
| `UpdateInitTask` | App.onCreate 经 InitEngine | 启动静默检查（@IntoSet 自动注册） |

## 4. 依赖关系

- **依赖**：`:feature:update:api`、`:feature:update:core`、`:common:utils`、`:common:network`、`:common:init`、lifecycle-viewmodel、coroutines
- **谁依赖**：`:app` 的 **full 变体**（`fullImplementation`）
- **不引** smartwash.compose / common:ui（链路内无 Compose/公共 UI 组件消费）

## 5. 内部约定

1. **InitTask 接入模式**：`UpdateInitTask` 实现 `InitTask()`，经 `UpdateInitModule` 的 `@Provides @IntoSet` 汇入集合，`App.onCreate` 由 `InitEngine` 调度。
2. **非阻塞低优先级**：`blocking=false`，`priority=900`。
3. **事件驱动解耦**：检查出新版本经 `UpdateEventBus` 广播，壳层 `MainActivity`（full）收集转弹窗。
4. **replay=1 防丢失**：启动检查早于 MainActivity 订阅，重放由 `UpdateViewModel` 的「已关闭版本」防重挡下。
5. **设置载入幂等**：`UpdateSettingsStore.load()` 由启动任务与 ViewModel 各自调用，幂等。

## 6. 已知坑

1. **lite 变体无更新域**：`app/src/lite` 的 `UpdateFlowHost` 为空实现，设置页更新插槽返回 null；lite 手动检查更新为待办（契约在 `:feature:update:api`，内核在 `:feature:update:core`）。
2. **WorkManager 初始化时机**：`App` 必须实现 `Configuration.Provider` + Manifest 移除默认 `WorkManagerInitializer`（配置在 app 壳，Worker 在 core）。
3. **UpdateEventBus replay 语义**：重复事件由 `UpdateViewModel.dismissedVersionCode` 防重。
4. **forceUpdate 分流**：强制更新必须让用户看到说明，逻辑在 `UpdateInitTask` + 壳层 `UpdateFlow`。

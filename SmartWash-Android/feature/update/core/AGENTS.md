# AGENTS.md — :feature:update:core（热更新内核）

## 1. 模块身份

- **Gradle 坐标**：`:feature:update:core`
- **namespace**：`com.smartwash.feature.update.core`（R 独立；类包名保持 `com.smartwash.feature.update.*`）
- **职责**：热更新**下载 / 安装 / 版本比较**内核，**不含 InitTask**。两变体共用：full 经 `:feature:update:impl` 的 `UpdateInitTask` 自动检查；lite 不依赖 impl，可按需手动检查并复用本模块链路。

## 2. 包结构

```
com.smartwash.feature.update/
├── di/UpdateCoreModule.kt        # Hilt：AppUpdateApi + WorkManager
├── repository/AppUpdateRepository.kt  # 版本比对（PackageManager）+ 预签名地址
├── service/ApkDownloadWorker.kt  # @HiltWorker 后台下载 + 通知栏进度
├── service/ApkInstaller.kt       # FileProvider URI + 安装 Intent
└── settings/UpdateSettingsStore.kt    # 自动更新开关 / 不再提醒版本（DataStore）
```

## 3. 依赖关系

- **依赖**：`:feature:update:api`、`:common:utils`、`:common:network`、WorkManager、hilt-work、okhttp、core-ktx
- **被依赖**：`:feature:update:impl`（full）、`:app`（两变体均声明 implementation）
- **不依赖** `:common:init`（无 InitTask）、`:feature:update:impl`（禁止反向）

## 4. 内部约定

1. **InitTask 不在此**：`UpdateInitTask` 留在 `:feature:update:impl`；lite 不依赖 impl，故 lite 天然不注册启动静默检查。
2. **包名与模块路径解耦**：类仍为 `com.smartwash.feature.update.*`，R 为 `com.smartwash.feature.update.core.R`。
3. **FileProvider / 安装权限 / 前台服务类型** 在本模块 Manifest + `res/xml/file_paths.xml`；`authorities` 用 `${applicationId}`，随变体 applicationId 变化。
4. **WorkManager 按需初始化**：`App` 实现 `Configuration.Provider` 注入 `HiltWorkerFactory`，Manifest 移除默认 `WorkManagerInitializer`。
5. **ksp 处理器自持**：`libs.androidx.hilt.compiler` 走 ksp（`@HiltWorker` 需要）。
6. **通知文案在本模块 strings.xml**；其中品牌词「SmartWash」为当前文案，lite 变体复用时会显示该词（变体品牌化待办）。

## 5. 已知坑

1. `ApkDownloadWorker.apkDir` 必须 `mkdirs`，否则首次下载 `FileNotFoundException`。
2. 完成/失败通知用独立 ID（`NOTIFICATION_ID_COMPLETE/FAILED`），否则被前台通知回收连带清除。
3. `SystemForegroundService` 必须声明 `foregroundServiceType="dataSync"`，与 `ForegroundInfo` 类型一致。
4. 安装序列内禁止删除 APK（系统安装器异步读取）。

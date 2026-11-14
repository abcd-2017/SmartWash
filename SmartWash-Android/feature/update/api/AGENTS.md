# AGENTS.md — :feature:update:api

## 模块身份

- **Gradle 坐标**: `:feature:update:api`
- **包名**: `com.smartwash.feature.update.api`
- **职责**: 热更新对外契约——`AppVersionVo` + `AppUpdateApi`。

## 包结构

```
com.smartwash.feature.update.api/
├── model/AppVersionVo.kt      # 版本信息 VO
└── network/AppUpdateApi.kt    # 版本检查接口
```

## 依赖关系

- **此模块依赖**: `:common:utils`（ApiResult）+ `:common:network`（retrofit）
- **谁依赖此模块**: `:feature:update:impl`（仓库/Worker/ViewModel）、`:app`（`UpdateFlow`/`UpdateDialog` 渲染 `AppVersionVo`）

## 内部约定

- 零 Hilt 运行时、零 Android 组件；仅契约与模型。
- `UpdateEventBus`（javax.inject 单例）、Worker、Installer、FileProvider、InitTask 留 impl。

## 已知坑

- 本模块无页面路由（更新链路非 NavHost 承载）。

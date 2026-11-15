# AGENTS.md — :feature:toolbox:api

## 模块身份

- **Gradle 坐标**: `:feature:toolbox:api`
- **包名**: `com.smartwash.feature.toolbox.api`
- **职责**: 工具箱对外契约——`ToolboxRoute` + `ToolboxConstant` + 短码 / 资产标签 / 设备投递三套 `Api` 与 VO。

## 包结构

```
com.smartwash.feature.toolbox.api/
├── ToolboxRoute.kt           # 短链/资产标签/投递路由常量
├── ToolboxConstant.kt        # 域常量（短码规则 key 等）
└── network/
    ├── ToolboxApi.kt         # 短码 Api + VO
    ├── AssetTagApi.kt        # 资产标签 Api + VO
    ├── DeliveryApi.kt        # 设备投递 Api + VO
    └── DeliveryBusinessCode.kt
```

## 依赖关系

- **此模块依赖**: `:common:utils`（SmartWashRoute/ApiResult/PageData）+ `:common:network`（@RequireAuthorization；retrofit 经其 api 传递）
- **谁依赖此模块**: `:feature:toolbox:impl`（深链贡献 / NavGraph / 仓库 / 分页）

## 内部约定

- 零 Hilt 运行时、零 Compose；仅契约与模型。
- 业务规则（`ShortCodeRules` / `AssetTagRules` / `DeliveryRules`）、PagingSource、Repository 留 impl。

## 已知坑

- 投递业务码 `DeliveryBusinessCode` 随 `network/` 走 api（impl 与外部契约共用）。

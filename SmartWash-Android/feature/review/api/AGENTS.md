# AGENTS.md — :feature:review:api

## 模块身份

- **Gradle 坐标**: `:feature:review:api`
- **包名**: `com.smartwash.feature.review.api`
- **职责**: 记忆复习对外契约——`ReviewRoute` 路由常量 + `ReviewConstant` + `ReviewApi` / VO。

## 包结构

```
com.smartwash.feature.review.api/
├── ReviewRoute.kt          # 路由常量（今日队列/学习/资料）
├── ReviewConstant.kt       # 域常量
└── network/ReviewApi.kt    # ReviewApi + 请求/返回 VO
```

## 依赖关系

- **此模块依赖**: `:common:utils`（SmartWashRoute/ApiResult/PageData）+ `:common:network`（@RequireAuthorization；retrofit 经其 api 传递）
- **谁依赖此模块**: `:feature:review:impl`（深链贡献 / NavGraph / 仓库）

## 内部约定

- 零 Hilt 运行时、零 Compose；仅契约与模型。
- `ReviewModels`（含 `@StringRes`/R 的 UI 契约）留在 `:feature:review:impl`。

## 已知坑

- `ReviewConstant` 为纯常量可放 api；`ReviewRating` 枚举随 `ReviewModels` 留在 impl。

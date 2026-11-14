# AGENTS.md — :feature:subscription:api

## 模块身份

- **Gradle 坐标**: `:feature:subscription:api`
- **包名**: `com.smartwash.feature.subscription.api`
- **职责**: 订阅逃生舱对外契约——`SubscriptionRoute` + `SubscriptionConstant` + `SubscriptionEnums` + `SubscriptionApi` / VO。

## 包结构

```
com.smartwash.feature.subscription.api/
├── SubscriptionRoute.kt          # 路由常量（列表/编辑/详情/提醒）
├── SubscriptionConstant.kt       # 域常量
├── SubscriptionEnums.kt          # SubscriptionCycleType / SubscriptionStatus 等枚举
└── network/SubscriptionApi.kt    # SubscriptionApi + 请求/返回 VO
```

## 依赖关系

- **此模块依赖**: `:common:utils`（SmartWashRoute/ApiResult/PageData）+ `:common:network`（@RequireAuthorization；retrofit 经其 api 传递）
- **谁依赖此模块**: `:feature:subscription:impl`（深链贡献 / NavGraph / 仓库 / 分页）

## 内部约定

- 零 Hilt 运行时、零 Compose；仅契约与模型。
- 枚举/常量是纯 Kotlin，可放 api；UI 组件与业务逻辑留 impl。

## 已知坑

- `SubscriptionRules` / `PagingSource` / Repository 留 impl。

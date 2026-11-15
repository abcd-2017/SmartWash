# AGENTS.md — :feature:divination:api

## 模块身份

- **Gradle 坐标**: `:feature:divination:api`
- **包名**: `com.smartwash.feature.divination.api`
- **职责**: 观象台对外契约——`DivRoute` 路由常量 + `DivinationApi` / 请求返回 VO。

## 包结构

```
com.smartwash.feature.divination.api/
├── DivRoute.kt               # Home/Ask/Cast/Chart/Reading/FollowUp/History
└── network/DivinationApi.kt  # DivinationApi + DivRecordVo/DivModelVo 等 VO
```

## 依赖关系

- **此模块依赖**: `:common:utils`（SmartWashRoute/ApiResult/PageData）+ `:common:network`（@RequireAuthorization；retrofit 经其 api 传递）
- **谁依赖此模块**: `:feature:divination:impl`（深链 / 仓库 / 解读网关）、`:app`（WorkbenchPage 跳转 `DivRoute.Home`）

## 内部约定

- 零 Hilt 运行时、零 Compose、零算法内核；仅契约与模型。
- 四套算法内核（liuren/liuyao/meihua/qimen）、卦历库、Room、解读网关留 impl。

## 已知坑

- `DivRoute.Chart/Reading/FollowUp` 的 `recordId` 为 path 参数，其余 query。

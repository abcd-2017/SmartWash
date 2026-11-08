# AGENTS.md — :feature:points:api

## 模块身份

- **Gradle 坐标**: `:feature:points:api`
- **包名**: `com.smartwash.feature.points.api`
- **职责**: 积分域对外契约（扩展提案功能 17）——`PointsRoute` 路由常量 + Gson 载荷模型（与后端 `SmartWash/src/main/java/com/smartwash/points/vo/` 一一对应）+ `PointsServiceApi` 网络接口。

## 包结构

```
src/main/java/com/smartwash/feature/points/api/
├── PointsRoute.kt               # 路由常量（PointsHome；单页承载签到+余额+月榜+流水，取舍见 KDoc）
├── PointsServiceApi.kt          # Retrofit 接口（getOverview/signIn/getLeaderboard，全 @RequireAuthorization）
└── model/
    ├── PointsOverviewVo.kt      # 概览 + 流水条目（balance/totalEarned/signedToday/streak/nextBonusIn/recentLedger）
    ├── SignInResultVo.kt        # 签到结果（幂等命中 alreadySigned=true 不重复加分）
    └── PointsLeaderboardVo.kt   # 月榜（entries/my，手机号后端已脱敏）
```

## 公开 API

| 符号 | 类型 | 说明 |
|------|------|------|
| `PointsRoute` | `sealed interface` | 积分域路由常量 |
| `PointsRoute.Home` | `data object` | 签到有礼页（单页） |
| `PointsServiceApi` | `interface` | 积分网络接口（Retrofit） |
| `POINTS_LEADERBOARD_SCOPE_MONTH` | `const` | 榜单口径常量（month） |
| `PointsOverviewVo` / `PointLedgerItemVo` / `SignInResultVo` / `PointsLeaderboardVo` / `LeaderboardEntryVo` / `MyRankVo` | `data class` | Gson 载荷（与后端 vo 一一对应） |

## 依赖关系

- **此模块依赖**: `:common:utils`（SmartWashRoute/ApiResult）+ `:common:network`（@RequireAuthorization；retrofit 经其 api 传递）+ kotlinx-coroutines-core + androidx.annotation
- **谁依赖此模块**: `:feature:points:impl`（域内实现）、`:feature:user:impl`（我的页「签到有礼」入口跳转）、`:app`（NavHost 注册）

## 内部约定

- **Gson 容错**: 全字段可空 + 默认 null，消费方一律经 `safeXxx` / `isXxx` 计算属性取兜底值；日期时间字段保留后端 ISO 字符串原样（Gson 不支持 java.time，解析在消费方容错处理）。
- **Retrofit 接口放 api 的差异**: coupon 先例是 Retrofit 接口在 impl；points 无跨模块消费方（无 CouponApi 式服务契约需求），VO 即领域载荷，故接口随模型放本模块，impl 免一层映射。依赖方向 api → common 合法（铁律 1）。
- **契约源**: 后端分支 `feat/point-signin` 的 `PointsWebController.java` + `vo/` + `application.yaml` points 段（签到基础分 5 / 连签周期 7 天 / 奖励 10 分 / 月榜 TOP 50 / 最近流水 20 条）。

## 已知坑

- **深链 host**: `points`（impl 模块 di 贡献），受保护 host（消费 /web/auth 接口，未登录由壳层拦截），不进 `PUBLIC_DEEP_LINK_HOSTS` 白名单。
- **幂等语义**: `signIn` 重复请求 code=200 且 `alreadySigned=true`，端上不作错误处理。

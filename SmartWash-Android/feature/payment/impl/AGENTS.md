# AGENTS.md — :feature:payment:impl

## 模块身份

- **Gradle 坐标**: `:feature:payment:impl`
- **包名**: `com.smartwash.feature.payment.impl`
- **职责**: 支付域实现——支付 + 充值（同属资金链路）的网络链路与页面，对外仅经 `:feature:payment:api` 的 `PaymentRoute` 暴露。

## 包结构

```
src/main/java/com/smartwash/feature/payment/impl/
├── PaymentImplConstant.kt               # 模块内常量（APP_NAME）
├── di/
│   └── PaymentApiModule.kt              # Hilt 模块（提供 PaymentApi / RechargeApi）
├── network/
│   ├── api/
│   │   ├── PaymentApi.kt                # Retrofit 支付接口（POST /web/auth/payments/payment）
│   │   └── RechargeApi.kt               # Retrofit 充值接口（POST userRecharge + GET list）
│   ├── entity/
│   │   ├── OrderPayment.kt              # 支付请求体
│   │   └── recharge/UserRecharge.kt     # 充值请求体
│   └── vo/recharge/RechargeRecordVo.kt  # 充值记录 VO
├── paging/
│   └── RechargeRecordPagingSource.kt    # 充值记录分页
├── repository/
│   ├── PaymentRepository.kt             # 支付仓库
│   └── RechargeRepository.kt            # 充值仓库
└── ui/
    ├── PaymentGraph.kt                  # 路由聚合（paymentGraph）
    ├── payment/
    │   ├── PaymentPage.kt               # 支付页
    │   ├── PaymentViewModel.kt          # 注入 OrderApi + CouponApi
    │   ├── PaySuccessPage.kt            # 支付成功页
    │   ├── PaySuccessViewModel.kt       # 注入 OrderApi
    │   └── PaymentType.kt               # 支付方式枚举
    └── recharge/
        ├── RechargePage.kt              # 充值页
        ├── RechargeViewModel.kt
        ├── RechargeRecordPage.kt        # 充值记录页
        └── RechargeRecordViewModel.kt
```

## 公开 API

| 符号 | 说明 |
|------|------|
| `NavGraphBuilder.paymentGraph(navController, reduceMotion)` | 路由聚合扩展，壳层 `MainActivity` 一行调用 |

> 域内 Repository 不对外暴露——支付域零跨模块数据消费，外部经路由常量导航即可。

## 依赖关系

- **此模块依赖**:
  - `:feature:payment:api` — 本域路由常量
  - `:feature:order:api` — `OrderApi`（订单详情/优惠券试算）
  - `:feature:coupon:api` — `CouponApi` + `UsableCoupon`（支付页选券）+ `CouponRoute`
  - `:common:model` / `:common:network` / `:common:ui` / `:common:utils`
- **谁依赖此模块**: 无（支付域实现无外部消费方，仅经 api 暴露路由）

## 内部约定

- **跨域数据经接口**: `PaymentViewModel` 注入 `OrderApi` / `CouponApi`，禁止触碰 `order-impl` / `coupon:impl` 实现。
- **鉴权**: 写操作接口加 `@RequireAuthorization`，由 `RequestInterceptor` 注入 Bearer token。
- **路由转场**: 支付/支付成功页保持底部滑入专属转场（`slideEnter/slideExit`），`reduceMotion` 时降级短 tween。
- **分页**: 充值记录用 Paging 3（`RechargeRecordPagingSource` + `pagingFlow`）。

## 已知坑

- **RechargePage 组合期副作用**: `RechargePage.kt` 的金额选择/支付状态分支存在写在 `when(state)` 渲染分支里的副作用，违反 Compose 硬规则，待独立任务修复——新增逻辑时不要模仿该写法。
- **资金链路并发**: 支付/充值涉及资金，改动前必看 `docs/code-review-2026-08-28.md` 第一章 P0 项（后端资金链路已知并发竞态与幂等缺失）。

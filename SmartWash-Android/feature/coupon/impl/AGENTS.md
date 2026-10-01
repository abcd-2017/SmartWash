# AGENTS.md — :feature:coupon:impl

## 模块身份

- **Gradle 坐标**: `:feature:coupon:impl`
- **包名**: `com.smartwash.feature.coupon`（注意：实际包路径短于 Gradle 路径，与模块路径解耦）
- **职责**: 优惠券域实现——优惠券网络链路与页面（三 tab：可领取/已领取/历史），对外经 `:feature:coupon:api` 的 `CouponRoute` + `CouponApi` 暴露（di `@Binds`）。

## 包结构

```
src/main/java/com/smartwash/feature/coupon/
├── CouponImplConstant.kt                # 模块内常量（APP_NAME）
├── CouponStatus.kt                      # 优惠券状态枚举（EXPIRED/ACTIVE/RECEIVE）
├── di/
│   └── CouponModule.kt                  # Hilt 模块（@Binds CouponApi + 提供 CouponServiceApi）
├── network/
│   ├── api/
│   │   └── CouponServiceApi.kt          # Retrofit 接口（getAllCoupon/receiveCoupon/getCanUseCoupon/getAllCoupons）
│   └── vo/coupon/
│       ├── AllCouponsVo.kt              # 聚合响应 VO（available/claimed/historical）
│       ├── CouponVo.kt                  # 优惠券 VO
│       └── UserCouponVo.kt              # 用户优惠券 VO
├── repository/
│   ├── CouponApiImpl.kt                 # CouponApi @Binds 实现
│   ├── CouponRepository.kt              # 优惠券仓库（Room 缓存 + cache-first 降级）
│   └── EntityMappers.kt                 # VO ↔ Entity 映射
└── ui/
    ├── CouponGraph.kt                   # 路由聚合（couponGraph）
    └── coupon/
        ├── CouponPage.kt                # 优惠券页（三 tab 容器）
        ├── CouponViewModel.kt
        └── tab/
            ├── AvailableCouponsTab.kt   # 可领取 tab
            ├── ClaimedCouponsTab.kt     # 已领取 tab
            └── HistoricalCouponsTab.kt  # 历史 tab
```

## 公开 API

| 符号 | 说明 |
|------|------|
| `NavGraphBuilder.couponGraph(navController)` | 路由聚合扩展，壳层 `MainActivity` 一行调用 |
| `CouponApiImpl` | `@Binds` 实现，供外部（payment-impl）经接口注入 |

## 依赖关系

- **此模块依赖**:
  - `:feature:coupon:api` — 本域路由常量 + 服务契约
  - `:common:model` / `:common:network` / `:common:ui` / `:common:database`
- **谁依赖此模块**: `:feature:payment:impl` — 支付页注入 `CouponApi` 取订单可用优惠券

## 内部约定

- **@Binds 实现**: `CouponApiImpl` 实现 `CouponApi`，类型面收窄为 `UsableCoupon`（不泄漏优惠券域 VO）。
- **Cache-first 降级**: `getAllCoupon`/`getAllCoupons` 网络失败时可用券降级为 Room 缓存（已领/历史无本地缓存表置空）；无缓存抛异常。
- **事务保护**: `deleteAll + insertAll` 包进 `appDatabase.withTransaction`。
- **取消传播**: `catch` 先 `rethrow CancellationException`。
- **聚合接口**: `getAllCoupons` 一次拉取三类数据，无分页需求；`getAllCoupon` 仅维护可用券缓存表。
- **鉴权**: 所有接口加 `@RequireAuthorization`。
- **CouponStatus 留 impl**: 仅本域可领取 tab 消费，无跨模块消费方，不进 coupon-api（对比 OrderStatus 迁 api 是因 order-impl 与 app 壳双侧消费）。

## 已知坑

- **包名与模块路径解耦**: 实际包名 `com.smartwash.feature.coupon`（非 `com.smartwash.feature.coupon.impl`），与 laundry:impl 同模式——引用时注意路径。
- **Room 缓存表共库**: `CouponVo` 表在 `:common:database`（三表共库），拆表归 T8.2 统一评估，本模块不扩面。
- **pagingFlow 双份副本**: app 原件/payment-impl 副本维持现状待 T8.2 统一收敛，不要在本模块引入 paging。
- **死代码清理**: app 壳 `UserCouponPagingSource` 为零引用死代码已删（不迁入）。

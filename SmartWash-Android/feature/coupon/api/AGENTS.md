# AGENTS.md — :feature:coupon:api

## 模块身份

- **Gradle 坐标**: `:feature:coupon:api`
- **包名**: `com.smartwash.feature.coupon.api`
- **职责**: 优惠券域对外契约——`CouponRoute` 路由常量 + `CouponApi` 服务契约（订单可用优惠券）+ `UsableCoupon` 模型（优惠券域存在真实跨模块数据消费方：payment-impl 支付页选券）。

## 包结构

```
src/main/java/com/smartwash/feature/coupon/api/
├── CouponRoute.kt               # 路由常量密封类（Coupon）
├── CouponApi.kt                 # 优惠券服务契约接口
└── model/
    └── UsableCoupon.kt          # 可用优惠券模型（userCouponId / discount / threshold / expiredAt）
```

## 公开 API

| 符号 | 类型 | 说明 |
|------|------|------|
| `CouponRoute` | `sealed class` | 优惠券域路由常量 |
| `CouponRoute.Coupon` | `data object` | 优惠券页（三 tab） |
| `CouponApi` | `interface` | 优惠券服务契约（`suspend getCanUseCoupon(orderId): List<UsableCoupon>`） |
| `UsableCoupon` | `data class` | 支付页选券消费的四个字段 |

## 依赖关系

- **此模块依赖**: 无项目依赖（纯 Kotlin，不引 Hilt 运行时）
- **谁依赖此模块**:
  - `:feature:coupon:impl` — 域内页面 + `CouponApiImpl`（`@Binds` 实现）
  - `:feature:payment:impl` — 支付页注入 `CouponApi` 取订单可用优惠券
  - `:app` — NavHost 注册（`PageConstant` 委托 `CouponRoute` 值）

## 内部约定

- **契约归数据持有方**: `CouponApi` 由优惠券域 api 持有，命名对齐 `user-api` 的 `UserApi` / `order-api` 的 `OrderApi`。
- **模型不带 `@Keep`**: `UsableCoupon` 是领域载荷，不直接参与 Gson 反序列化（网络层 `UserCouponVo` 在优惠券域内映射到本类）。
- **类型面收窄**: `UsableCoupon` 只保留支付页选券消费的四个字段，不泄漏优惠券域 VO（`UserCouponVo`/`CouponVo`）。
- **data 为 null 语义**: `getCanUseCoupon` 的 `data` 为 null 时抛 `NetworkException`，由消费方并入错误状态。

## 已知坑

- **契约升级来源**: `CouponApi` 自 payment-impl 的 `CouponSource` 过渡 seam 迁入并改名，原 app 壳 `PaymentImplSeamModule` 实现已删——不要回退到旧位置查找实现。
- **跨域消费方唯一**: 当前仅 payment-impl 支付页消费 `CouponApi`，不要假设其他消费方存在。
- **路由值委托**: `PageConstant` 已改为委托 `CouponRoute`，feature 不得反向依赖 app 壳 `PageConstant`。

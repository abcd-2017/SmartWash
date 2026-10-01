# AGENTS.md — :feature:payment:api

## 模块身份

- **Gradle 坐标**: `:feature:payment:api`
- **包名**: `com.smartwash.feature.payment.api`
- **职责**: 支付域对外路由契约——仅暴露 `PaymentRoute` 路由常量，零接口、零模型（支付域无跨模块数据消费，跨域诉求只有导航）。

## 包结构

```
src/main/java/com/smartwash/feature/payment/api/
└── PaymentRoute.kt          # 路由常量密封类（Payment / PaySuccess / Recharge / RechargeRecord）
```

## 公开 API

| 符号 | 类型 | 说明 |
|------|------|------|
| `PaymentRoute` | `sealed class` | 支付域路由常量，`text` 值对齐 app 壳 `PageConstant` |
| `PaymentRoute.Payment` | `data object` | 支付页（跳转拼 `/{orderId}`） |
| `PaymentRoute.PaySuccess` | `data object` | 支付成功页（跳转拼 `/{orderId}`） |
| `PaymentRoute.Recharge` | `data object` | 充值页 |
| `PaymentRoute.RechargeRecord` | `data object` | 充值记录页 |

## 依赖关系

- **此模块依赖**: 无项目依赖（纯 Kotlin，不引 Hilt 运行时）
- **谁依赖此模块**:
  - `:feature:payment:impl` — 域内页面互跳
  - `:feature:order:impl` — 订单页「去支付」跳 `PaymentRoute.Payment`
  - `:feature:user:impl` — 用户中心「去充值」跳 `PaymentRoute.Recharge`
  - `:app` — NavHost 注册（`PageConstant` 委托本类值）

## 内部约定

- **最小暴露原则**: 仅路由常量，不新增接口/模型——支付域当前无跨模块数据消费方，后续出现真实消费方时再增补（YAGNI）。
- **路由值单一事实来源**: `PageConstant` 已改为委托 `PaymentRoute`，feature 不得反向依赖 app 壳的 `PageConstant`。
- **命名模式**: 仿 `OrderRoute` / `UserRoute` / `DivRoute`，`sealed class + data object`。
- **构建配置**: 仅 `smartwash.android.library`，不引 Compose/Hilt。

## 已知坑

- **零数据消费设计**: 不要凭直觉往本模块加接口——T6.1/T6.2 两度调研确认 `PaymentRepository`/`RechargeRepository` 仅支付/充值页面自用，跨模块只有导航诉求。若后续出现消费方，先评估是否真需跨域，再按 `laundry-api` 的 `SchoolSearchSource` 模式增补契约。

# :feature:order:api

## 模块身份

- **Gradle 坐标**: `:feature:order:api`
- **职责**: 订单域对外服务契约——订单操作接口、订单模型、路由常量、状态枚举的纯 Kotlin 接口与领域模型，零 Hilt 运行时 / 零 Compose / 零 feature 依赖。

## 包结构

```
com.smartwash.feature.order.api
├── OrderApi.kt                  # 服务契约接口（预约/详情/优惠券试算/进行中订单/计数/寄取件/分页工厂）
├── OrderRoute.kt                # 路由常量密封类（Order/OrderDetail/PickupDelivery）+ PICKUP_TYPE_* 参数常量
├── PickupDeliveryType.kt        # 取件/寄件类型枚举（委托 OrderRoute.PICKUP_TYPE_* 取值）
└── model/
    ├── OrderBrief.kt            # 进行中订单简要模型（首页卡片）
    ├── OrderInfo.kt             # 订单详情/列表条目模型（含 UserSnapshot/School/Locker/LaundryPackage 四个嵌套快照）
    ├── OrderItemCount.kt        # 各状态订单计数（pendingPayment/processing/pendingPickup/shipped）
    └── OrderStatus.kt           # OrderStatus（10 种后端状态码）+ ShowOrderStatus（5 种 tab 显示状态）枚举
```

## 公开 API

| 符号 | 类型 | 说明 |
|------|------|------|
| `OrderApi` | `interface` | 订单域服务契约。`reservationLaundry(itemsId, totalPrice): Long`（返回 -1 表失败）、`getOrderInfo(orderId): OrderInfo`、`calculationOrder(orderId, userCouponId): OrderInfo`、`getWashingOrder(): List<OrderBrief>`、`getOrderItemCount(...): OrderItemCount`、`shippingOrder(orderId, pickupCode): Boolean`、`pickupOrder(orderId, pickupCode): Boolean`、`orderPagingSource(status): PagingSource<Int, OrderInfo>` |
| `OrderRoute` | `sealed class` | 订单域路由常量。`Order`（跳转拼 `/{itemId}` 初始 tab）、`OrderDetail`（页面留 app 壳，拼 `/{orderId}`）、`PickupDelivery`（页面留 app 壳，拼 `/{orderId}/{pickupType}`）；companion `PICKUP_TYPE_PICKUP = 0` / `PICKUP_TYPE_DELIVERY = 1` |
| `PickupDeliveryType` | `enum` | 取件/寄件类型。`PICKUP(0)` / `DELIVERY(1)`，取值委托 `OrderRoute.PICKUP_TYPE_*` |
| `OrderStatus` | `enum` | 订单状态全集（10 种后端状态码）。`CANCELED(-2)` / `REFUNDED(-1)` / `PENDING_PAYMENT(0)` / `PENDING_SHIPMENT(1)` / `RECEIVED(2)` / `WASHING(3)` / `DRIED(4)` / `IN_DELIVERY(5)` / `READY_FOR_PICKUP(6)` / `COMPLETED(7)`，带 `descriptionRes` |
| `ShowOrderStatus` | `enum` | 订单页 tab 展示状态子集（含虚拟"全部"tab）。`ALL_ORDER("001")` / `PENDING_PAYMENT("0")` / `PENDING_SHIPMENT("1")` / `WASHING("3")` / `READY_FOR_PICKUP("6")` |
| `OrderInfo` | `data class` | 订单详情/列表条目。`orderId`/`userVo`/`schoolsVo`/`lockersVo`/`orderNo`/`laundryPackageVo`/`totalPrice`/`payPrice`/`status`/`pickupCode`/`createdAt`/`updatedAt`，含四个嵌套快照类型 |
| `OrderBrief` | `data class` | 进行中订单简要（首页卡片）。字段与原 `OrderVo` 一致，含默认值 |
| `OrderItemCount` | `data class` | 各状态订单计数。`pendingPaymentCount`/`processingCount`/`pendingPickupCount`/`shippedCount` |

**契约要点**:
- `orderPagingSource(status)` 是**工厂函数**（非 suspend），每次调用返回全新 `PagingSource`，状态变化时调用方须重新创建
- `OrderInfo` 的嵌套快照（`UserSnapshot`/`School`/`Locker`/`LaundryPackage`）刻意不依赖各域 impl 模型，保持 api 模块零 feature 依赖
- `OrderStatus`/`ShowOrderStatus` 带 `descriptionRes`，文案资源随枚举迁入本模块 `res`（api 模块可带 res 先例）
- `OrderRoute` 是单一事实来源——订单详情/寄件取件页面虽留 app 壳，路由常量语义归订单域

## 依赖关系

### 本模块依赖
- `kotlinx.coroutines.core`（suspend 契约）
- `androidx.paging.common`（PagingSource 纯接口库，无 Android 框架依赖）
- `androidx.annotation`（@StringRes）

### 谁依赖本模块
- `:feature:order:impl`（OrderApiImpl 实现 OrderApi）
- `:feature:payment:impl`（T6.2 后支付/支付成功页消费订单详情与优惠券试算）
- `:feature:user:impl`（T6.3 后用户中心订单计数消费 `getOrderItemCount`）
- `:app` 壳（留守页面 detail/index/pickup 消费 OrderApi；shellGraph 注册 OrderDetail/PickupDelivery）

## 内部约定

1. **零 feature 依赖**: 不引任何 feature-impl / app 壳 / Hilt / Compose
2. **模型内嵌快照**: `OrderInfo` 的四个嵌套类型（`UserSnapshot`/`School`/`Locker`/`LaundryPackage`）字段面对应各域 VO 的标量字段，不依赖各域 impl 模型
3. **路由单一来源**: 订单域路由文本以 `OrderRoute.text` 为准，壳层 `PageConstant.OrderDetail` 等委托取值；`PickupDeliveryType` 委托 `OrderRoute.PICKUP_TYPE_*`
4. **状态枚举契约**: `OrderStatus` 状态码与后端字符串完全对齐，`ShowOrderStatus` 是 UI 显示子集；新增状态须同步后端
5. **PagingSource 工厂**: `orderPagingSource` 返回 `PagingSource<Int, OrderInfo>`（Int 页码），状态变化时调用方须重新创建数据源

## 已知坑

- **OrderInfo 嵌套快照**: `UserSnapshot` 去掉订单上下文用不到的 `schoolVo` 嵌套——字段面对应 `UserInfoVo` 标量字段，改字段须同步 impl 层映射
- **PagingSource 工厂非单例**: 每次调用 `orderPagingSource(status)` 返回全新实例，状态变化时调用方（取件页）须重新创建，否则分页数据不刷新
- **ShowOrderStatus 含虚拟 tab**: `ALL_ORDER("001")` 是虚拟值（非后端状态码），`getOrderItemCount` 的状态参数取 `ShowOrderStatus.status`（"001" 等），勿与 `OrderStatus.status` 混用
- **api 模块带 res**: `OrderStatus`/`ShowOrderStatus` 的 `descriptionRes` 引用 `com.smartwash.feature.order.api.R.string.order_status_*`，文案资源随枚举迁入本模块
- **路由页面归属分离**: `OrderDetail`/`PickupDelivery` 页面留 app 壳（注册归 shellGraph），但路由常量语义归 `OrderRoute`——跳转与注册须同源取值

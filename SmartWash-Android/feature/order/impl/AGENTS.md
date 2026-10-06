# :feature:order:impl

## 模块身份

- **Gradle 坐标**: `:feature:order:impl`
- **职责**: 订单域实现——订单网络链路（OrderServiceApi/OrderRepository）、订单页（OrderPage/OrderViewModel）、OrderPagingSource；对外仅经 `:feature:order:api` 的 `OrderApi` 契约暴露。

## 包结构

```
com.smartwash.feature.order.impl
├── OrderApiImpl.kt              # @Binds OrderApi 实现（纯委托 OrderRepository，零业务逻辑）
├── OrderImplConstant.kt         # 模块内常量（APP_NAME / PAGE_SIZE = 10）
├── di/
│   └── OrderImplModule.kt       # Hilt 模块（OrderApi 绑定 + OrderServiceApi 供给）
├── network/
│   ├── api/
│   │   └── OrderServiceApi.kt   # Retrofit 接口（预约/详情/列表/分组/计数/寄取件/取消/优惠券试算）
│   ├── entity/order/
│   │   ├── OrderNextStatus.kt   # 寄件/取件请求体（orderId + pickupCode）
│   │   └── ReservationLaundry.kt # 预约请求体（itemsId + totalPrice）
│   └── vo/order/
│       ├── OrderVo.kt           # 进行中订单响应 VO（toModel → OrderBrief）
│       ├── OrderInfoVo.kt       # 订单详情响应 VO（toModel → OrderInfo）
│       ├── OrderGroupVo.kt      # 订单分组响应 VO（toModel → OrderGroup）
│       └── OrderItemCountVo.kt  # 订单计数响应 VO（toModel → OrderItemCount）
├── paging/
│   └── OrderPagingSource.kt     # 订单分页 PagingSource（直调 ServiceApi，null 按空页静默结束）
├── repository/
│   ├── OrderRepository.kt       # 订单数据仓库（VO → 领域模型映射集中层）
│   └── OrderGroup.kt            # 订单分组领域模型（items/hasMore/total，仅 order-impl 内部使用）
└── ui/
    ├── OrderGraph.kt            # NavGraphBuilder.orderGraph 路由注册（订单页 composable）
    └── order/
        ├── OrderPage.kt         # 订单页 Composable（5 tab HorizontalPager）
        └── OrderViewModel.kt    # 订单页 ViewModel
```

## 公开 API

本模块**对外无直接暴露 API**——跨模块消费方一律经 `:feature:order:api` 的 `OrderApi` 访问。

**模块内暴露**:
- `OrderApiImpl`：`@Singleton`，Hilt `@Binds OrderApi → OrderApiImpl`，纯委托 `OrderRepository`
- `OrderPagingSource`：`PagingSource<Int, OrderInfo>`，经 `OrderApi.orderPagingSource(status)` 工厂对外提供
- `NavGraphBuilder.orderGraph(navController)`：路由聚合扩展函数

## 依赖关系

### 本模块依赖
- `:feature:order:api`（OrderApi / OrderRoute / OrderInfo / OrderStatus 等）
- `:feature:payment:api`（PaymentRoute — 订单页「去支付」跨域跳转）
- `:common:utils`（ApiResult / RequestState）
- `:common:network`（Retrofit / @RequireAuthorization / NetworkException）
- `:common:ui`（清氧设计系统）
- `:common:utils`（触感反馈）

**注意**: 订单域现状**无 UserApi / PaymentRepository 注入点**（T6.1 调研结论），不依赖 user-impl / payment-impl。

### 谁依赖本模块
- **无**——impl 模块禁止被其他 feature-impl 依赖（依赖铁律）。仅 app 壳聚合 `orderGraph`。

## 内部约定

1. **门面零逻辑**: `OrderApiImpl` 纯委托 `OrderRepository`，业务语义与错误处理全部在 Repository 层
2. **VO → 模型映射集中**: `network/vo/order/*Vo.kt` 的 `toModel()` 扩展函数是 VO → 领域模型的唯一映射点
3. **Repository null 抛错**: `OrderRepository` 方法 `data` 为 null 时抛 `NetworkException`，让 UI 区分"无数据"与"请求失败"
4. **PagingSource 静默结束**: `OrderPagingSource` 的 `data` 为 null 按空页处理（不抛错），与 Repository 的"null 抛 NetworkException"语义不同——故直调 `OrderServiceApi` 而非走 Repository
5. **PAGE_SIZE 对齐服务端**: `OrderImplConstant.PAGE_SIZE = 10` 与 Paging 3 默认一致，返回条数不足一页说明已是最后一页
6. **OrderGroup 内部模型**: `OrderGroup`（items/hasMore/total）仅 order-impl 内部使用（订单页 tab 分组加载），不进 order-api
7. **资金链路零改动**: 预约/支付/优惠券试算等资金操作行为对齐原实现（资金链路并发竞态与幂等缺失见 `.claude/docs/code-review-2026-08-28.md` P0 项，改动前必读）

## 已知坑

- **PagingSource 直调 ServiceApi**: `OrderPagingSource` 直接调用 `OrderServiceApi.getOrderList` 而非走 Repository，目的是保持"null 按空页静默结束"语义——不要改为走 Repository（否则语义变为抛 NetworkException）
- **OrderGroup 不进 api**: `OrderGroup` 是 impl 内部模型（订单页分组加载），无跨模块消费者，禁止迁入 order-api
- **资金链路竞态**: 预约下单 / 优惠券试算 / 支付存在已知并发竞态与幂等缺失（见 `.claude/docs/code-review-2026-08-28.md` 第一章 P0），改动前必读
- **@Keep 注解**: 网络 VO/请求体需 `@Keep`（ProGuard 混淆），按惯例显式声明 `implementation libs.androidx.annotation`
- **T6.2 后支付依赖**: 当前订单域不依赖 payment-impl（仅经导航跳转），T6.2 payment-impl 迁移后支付页消费 `OrderApi.getOrderInfo` / `calculationOrder`——仍为 api 层消费，本模块不变
- **OrderServiceApi 命名**: 故意与 order-api 的 `OrderApi` 区分（避免同名冲突），模式同 user-impl 的 `UserAccountApi`

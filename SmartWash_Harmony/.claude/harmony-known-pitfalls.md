# 鸿蒙端已知坑

完整清单见 [.claude/docs/code-review-2026-08-28.md](../../.claude/docs/code-review-2026-08-28.md) 第四章，重点关注：

- `network/Axios.ets:22` 硬编码演示服务器明文 HTTP 地址（与 Android 分环境不一致）——改网络层时一并环境化（参照 Android 端 Gradle 属性注入或 DevEco 多 target/profile）。
- `view/IndexPage.ets` 轮询已改用 `@Monitor('isActive')` 启停（已修复）。
- `pages/Laundry.ets → Payment` 传参已统一 `number` 类型（已修复）。
- `pages/Payment.ets` 支付按钮已加 `paying` loading 防重、Radio 已支持取消选券（已修复）。
- `pages/Recharge.ets` 金额校验已改用 `parseFloat` 并校验 >0 与上限（已修复）。
- 待清理代码：`view/CouponCard.ets`、`UserCouponCard.ets`、`OrderStatusCard.ets` 无引用，待删除，不要复用（`OrderCard.ets`、`OrderItemRow.ets`、`LoadingFooter.ets` 为在用组件）。
- `StorageUtil` 依赖非空断言且初始化未 await，存在时序隐患；token 明文存储待迁移 HUKS/Asset Store。

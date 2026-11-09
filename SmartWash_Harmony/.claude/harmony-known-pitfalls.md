# 鸿蒙端已知坑

完整清单见 [docs/code-review-2026-08-28.md](../../docs/code-review-2026-08-28.md) 第四章，重点关注：

- `network/Axios.ets:22` 硬编码演示服务器明文 HTTP 地址（与 Android 分环境不一致）——改网络层时一并环境化（参照 Android 端 Gradle 属性注入或 DevEco 多 target/profile）。
- `view/IndexPage.ets` 轮询已改用 `@Monitor('isActive')` 启停（已修复）。
- `pages/Laundry.ets → Payment` 传参已统一 `number` 类型（已修复）。
- `pages/Payment.ets` 支付按钮已加 `paying` loading 防重、Radio 已支持取消选券（已修复）。
- `pages/Recharge.ets` 金额校验已改用 `parseFloat` 并校验 >0 与上限（已修复）。
- 死代码 `CouponCard.ets` / `UserCouponCard.ets` / `OrderStatusCard.ets` 已删除，不要复用。
- `StorageUtil` 依赖非空断言且初始化未 await，存在时序隐患；token 明文存储待迁移 HUKS/Asset Store。
- 取件提醒依赖 `PUBLISH_AGENT_REMINDER` 权限；未授权时提醒静默失效，调试先确认权限与登记表。
- 桌面卡片进程独立，401 时清 token 后需下次进 App 才落登录页。

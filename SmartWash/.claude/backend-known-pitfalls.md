# 后端已知坑

- `LoginController` 验证码：已加 IP 限流（60s/10）与 purpose 隔离单测，但新增验证码场景时仍要先隔离 purpose，不要复用同一 Redis key。
- `DashboardMapper.xml` 引用了不存在的 `is_delete` 列，Dashboard 接口当前报错（见评审报告）。
- `OrderTimeoutManager` 是单机动态 TaskScheduler（非 `@Scheduled`），多实例部署会重复/遗漏；不要在事务内注册新的内存任务。
- `application.yaml` 的 `request-log` 缩进在 `toolbox:` 下（实际生效 `toolbox.request-log.*`），而 `RequestLogProperties` 绑定 `smartwash.request-log`——配置值未生效，需统一。
- `SecurityConfig` 中 `/web/orders/status`、`/web/lockers/status`、`/web/lockers/all` 不在 permitAll 列表，需任意已认证用户（非匿名）；新增公开接口时显式加入白名单，不要假设 `/web/**` 全公开。
- `audit.hmac-secret` 生产 profile 无默认值（围栏），management 端写接口必须配置否则审计链无法校验。
- 测试共 36 个类 + H2 测试 profile（`application-test.yaml`/`schema.sql`，`smartwash.rate-limit.enabled=false`）；新增核心逻辑请配套单测，避免依赖真实中间件。

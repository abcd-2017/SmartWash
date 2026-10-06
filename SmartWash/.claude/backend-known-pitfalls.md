# 后端已知坑

- `LoginController` 验证码：注册与重置密码共用同一 Redis key，校验无尝试次数限制——新增验证码场景时先隔离 purpose。
- `DashboardMapper.xml` 引用了不存在的 `is_delete` 列，Dashboard 接口当前报错（见评审报告）。
- `OrderTimeoutManager` 是单机内存调度，多实例部署会重复/遗漏；不要在事务内注册新的内存任务。
- 测试仅 3 个类且无 test profile，新增核心逻辑请配套单测并避免依赖真实中间件。

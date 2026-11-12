# CLAUDE.md

本文件为编码 agent 在 SmartWash 后端工作时提供指导。仓库总纲见根目录 [CLAUDE.md](../CLAUDE.md)。

**必须使用中文回答。**

**遵守共享规则**：STOP规则、派发红线、协作流程、Git工作流、冲突协议等见 [.claude/docs/shared-rules.md](../.claude/docs/shared-rules.md)。提交规范见根目录 CLAUDE.md。

---

## 项目概述

**SmartWash** 后端 — 基于 Spring Boot 3.4.0 的校园洗衣寄存柜管理平台 REST API。Java 17，未包含 Maven Wrapper — 使用系统安装的 `mvn`。

## 构建与运行

```bash
mvn spring-boot:run            # 启动（需要 MySQL 和 Redis）
mvn test                       # 运行测试
mvn clean package -DskipTests  # 打包
```

- **MySQL 8.x** `127.0.0.1:3306`，库 `smart_wash`；**Redis** `127.0.0.1:6379`，database `3`。配置在 `src/main/resources/application.yaml`。
- 提供 `application-dev.yaml`（开发，桩实现开启）与 `application-prod.yaml`（生产，敏感项无默认值围栏）两套 profile。`mvn test` 的 `SmartWashApplicationTests` 会连真实 MySQL/Redis（无 test profile）。
- 数据库结构统一由根目录 `smart_wash.sql` 管理，结构变更请直接修改该文件。

## 架构

标准分层：`controller → service (接口) → service/impl → mapper (接口) → mapper XML`

### URL 路由与权限

| URL 前缀 | 需要认证 | 角色 |
|---|---|---|
| `/auth/**` | 否 | 公开接口（登录、注册、验证码、刷新令牌） |
| `/admin/**` | 是 | `ROLE_ADMIN`（Web 管理后台） |
| `/web/auth/div/**` | 是 | `ROLE_USER`（**先于** `/web/auth/**` 匹配） |
| `/web/auth/**` | 是 | `ROLE_USER`（Android / 鸿蒙） |
| `/web/**` | 否 | 公开 Web 接口 |

**公开只读路径**（SecurityConfig 显式 permitAll）：`/web/laundryItems/all`、`/web/schools/allName`、`/web/payments/payType`、`/web/payments/payStatus`、`/web/app/version`、`/web/app/download`、`/web/t/**`（短链跳转）、`/web/deeplink/routes`、`/web/config`、`/web/delivery/**`（设备投递对端公开页）、`/swagger-ui/**`、`/v3/api-docs/**`。其余 `/web/**`（如 `/web/orders/status`、`/web/lockers/status`、`/web/lockers/all`）需**任意已认证**用户。

两种用户类型，登录流程不同：管理员（`/auth/adminUsers/login`）通过用户名认证，普通用户（`/auth/user/login`）通过手机号认证。登录成功返回**双 token**：access（TTL 2 小时）+ refresh（TTL 7 天），`sub` 带前缀 `admin-{用户名}` 或 `user-{手机号}`；refresh 仅用于 `/auth/refresh` 一次性轮换，`jti` 是黑名单键。

认证链：`JwtAuthenticationFilter`（提取 Bearer token，经 `CustomUserDetailsService` 加载 `LoginUser`，设置 `SecurityContext`）→ `SecurityConfig`（基于角色的访问控制）。

### 包结构

| 包 | 用途 |
|---|---|
| `common/` | 枚举（`OrderStatus`、`LockerStatusEnum` 等）、常量、统一响应 `Result<T>`；子包 `audit/`（`@Audited` + HMAC 链）、`ratelimit/`（`@RateLimit` + Redis Lua）、`log/`（TraceId / AccessLog / 脱敏） |
| `config/` | Security、CORS、Redis、缓存序列化、MyBatis-Plus、MinIO、OpenAPI、全局异常、JWT 入口/登出、日志过滤器注册、MDC 任务装饰器 |
| `controller/` | `LoginController` 在顶层；`background/` 管理端 API（含 Dashboard、导出），`web/` 用户端 API |
| `entity/` | 核心业务实体（12 张表，MyBatis-Plus 映射） |
| `exception/` | 自定义异常（`CustomExceptions`、`GameServiceException`、`UserAuthenticationException`） |
| `filter/` | `JwtAuthenticationFilter` — 每次请求执行 JWT 校验 + Redis 黑名单 |
| `from/` | 请求 DTO。命名 `{操作}{实体}From`，分页继承 `BaseSearchFrom` |
| `mapper/` | MyBatis-Plus `BaseMapper`。自定义 SQL 在 `src/main/resources/mapper/*.xml` |
| `service/` | 接口继承 `IService<T>`；实现在 `service/impl/` 继承 `ServiceImpl<M, T>` |
| `task/` | `OrderTimeoutManager`（动态 TaskScheduler，30 分钟未支付自动取消，非 `@Scheduled`） |
| `divination/` | 「观象台」占卜子系统（controller / core 四算法 / llm SSE 网关 / prompt / rag / entity / mapper / service / vo / from / task 用量聚合） |
| `game/` | 游戏中心：游戏库 / 关卡 / 存档 / 成绩 / 排行榜，MinIO 对象存储与上传包校验 |
| `notice/` | 站内信公告与已读记录 |
| `points/` | 积分账户、签到、流水、月榜 |
| `review/` | 记忆复习（FSRS-6 纯函数调度器 + 资料/卡片/队列/统计） |
| `subscription/` | 订阅、续费推进、用量、提醒 |
| `toolbox/` | 短链、设备投递（`delivery/` 子包含公开对端接口）、访问统计 |
| `workbench/` | 工作台入口与 deeplink 路由字典 |
| `utils/` | `JwtUtil`（双 token）、`JsonUtils`、`LoginUser`、`PickupCodeUtils`、`SecurityUtil`、`UserContextHolder` |
| `vo/` | 视图对象，命名 `{实体}Vo` |

### 关键模式

- **`Result<T>`** 统一 API 响应：成功 `Result.ok(data)`，失败 `Result.failMsg(msg)`。全局异常由 `ExceptionControllerAdvice` 捕获。**禁止直接返回实体或字符串**。
- **MyBatis-Plus**：`@TableName`/`@TableId`，优先用内置方法（`save`、`removeById`、`getById`、`updateById`、`list`、`count`、`page`），复杂查询才写 XML。`MybatisConfig` 已启用分页插件。
- **注意：本项目没有逻辑删除**——无 `@TableLogic`，表无 `is_delete` 列，删除均为物理删除。涉及资金类记录（payments/recharge_records）禁止新增删除入口。
- **Form 对象**用 `@Valid` + Jakarta Bean Validation 校验。
- **ThreadLocal 用户上下文**：`UserContextHolder.setUser(loginUser)` 在 JWT 过滤器设置，Service 层经 `SecurityUtil.getCurrentUser()` 获取。
- **Lombok** 全项目使用（`@Data`、`@Slf4j`、`@AllArgsConstructor`）；**Hutool** 提供 `Snowflake`、`IdUtil` 等；**FastJSON 2** 是 JSON 库。
- **横切能力**：`@RateLimit`（Redis Lua 分布式限流，登录/注册/验证码/短链跳转/占卜解读按 IP 或用户限流）、`@Audited`（管理端写操作 HMAC 链审计，`/admin/audit-logs/verify` 校验完整性）、TraceId/AccessLog（`X-Request-Id` + MDC + 敏感字段脱敏）。
- **缓存**：Redis `CacheManager`（schools/laundryItems/workbenchEntries/deeplinkRoutes/gameList 30min、coupon 10min、dashboard 2min），缓存序列化带 autoType 白名单。

## 硬性约束

- **资金/状态操作必须防并发**：任何"先查再改"的订单状态流转、优惠券核销/领取、余额扣减，必须用条件 UPDATE 判断影响行数或 `SELECT FOR UPDATE`，禁止 `getById` 后直接 `updateById`（已知竞态清单见评审报告第一章 P0）。
- **支付/充值金额以后端计算为准**，不信任前端传入价格。
- **新增接口遵循 URL 路由规范**（见上表），响应统一 `Result<T>`。
- **不硬编码敏感信息**：密钥/密码走环境变量，`application.yaml` 中不落默认生产密钥。
- **代码注释和日志使用中文**，日志用 Lombok `@Slf4j`，请求级高频日志用 debug 级别。
- **限流/审计注解**：新增公开高频接口按需加 `@RateLimit`；管理端写接口必须加 `@Audited`，不得绕过审计直接改库。

## 定时任务

| 任务 | 调度 | 用途 |
|---|---|---|
| `OrderTimeoutManager` | 动态 TaskScheduler（每单 30 分钟，启动重载） | 未支付订单 CAS 取消 + 释放柜子 |
| `SubscriptionRenewJob` | 每日 07:30 | 推进过期自动续费订阅（CAS + 幂等提醒） |
| `ToolboxShortCodeCleaner` | 每日 03:30 | 清理过期 30 天的短码 + 孤儿访问明细 |
| `ToolboxDeliveryCleaner` | 每 5 分钟 | 清理过期 10 分钟的投递会话（MinIO 对象 → 条目 → 会话） |
| `DivUsageAggregator` | 每日 00:05 | 聚合昨日占卜用量到 `div_usage_daily`（删后插幂等） |

## 测试

`src/test` 共 36 个测试类 + `application-test.yaml` / `schema.sql`（H2 内存库，`smartwash.rate-limit.enabled=false`）。覆盖登录验证码、JWT、资金幂等、订单 CAS、游戏、工具箱、工作台等；新增核心逻辑请配套单测。

## 已知坑（改动前先看）

详见 [.claude/backend-known-pitfalls.md](.claude/backend-known-pitfalls.md)。

## 子代理

`.claude/agents/smartwash-backend.md` 为合并后的后端全栈代理，覆盖开发、审查、调试、测试、架构决策与文档同步。跨端通用方法论（systematic-debugging、test-driven-development 等）可直接调用库内 skill。

## ⛔ 红线操作表（后端特化）

| 红线 | 说明 |
|------|------|
| 跳过各端联动检查 | 改接口必须按根目录 CLAUDE.md 四端联动表同步（Android / 鸿蒙 / Web） |
| 资金操作不走条件更新 | 订单状态流转、优惠券核销、余额扣减必须条件 UPDATE 判影响行数或 `SELECT FOR UPDATE` |
| 新增删除资金记录入口 | payments/recharge_records 禁止新增删除接口 |
| 跳过根因分析 | 没有根因调查不允许修复 |

通用红线（直接 push 到 main、修改 CLAUDE.md 需团队共识、声称完成 without 验证）见 [.claude/docs/shared-rules.md](../.claude/docs/shared-rules.md)。

完整项目规则见本子目录 `CLAUDE.md`，评审报告见 `docs/code-review-2026-08-28.md`。

# AGENTS.md — SmartWash 后端项目指南

> 校园智能洗衣平台后端：为 Android / 鸿蒙用户端和 Vue3 管理后台提供 REST API。覆盖用户、订单、储物柜、优惠券、充值支付、管理端权限等模块。
>
> 相关文档：编码 agent 硬规则见 [CLAUDE.md](CLAUDE.md)；子代理定义见 [.claude/agents/](.claude/agents/)；四端联动约定见仓库根目录 CLAUDE.md。

---

## 一、技术栈

| 维度 | 技术选型 |
|------|----------|
| 语言/运行时 | Java 17 |
| 框架 | Spring Boot 3.4.0 + Spring Security |
| ORM | MyBatis-Plus 3.5（分页插件已启用） |
| 数据库 | MySQL 8.x（`127.0.0.1:3306`，库 `smart_wash`） |
| 缓存 | Redis（`127.0.0.1:6379`，database 3；CacheManager 分域 TTL） |
| 认证 | JWT 双 token（jjwt：access 2h + refresh 7d，Redis 登出黑名单 + refresh jti 轮换） |
| JSON | FastJSON 2 |
| 工具库 | Lombok、Hutool（Snowflake/IdUtil）、MinIO（对象存储） |
| 横切 | `@RateLimit` 分布式限流、`@Audited` HMAC 审计链、TraceId/AccessLog 请求链路 |
| 数据库结构 | 统一由根目录 `smart_wash.sql` 管理 |
| 构建 | Maven（无 Wrapper，用系统 `mvn`） |

---

## 二、包结构

```
src/main/java/com/smartwash/
├── common/          # 枚举、常量、Result<T>；audit/（@Audited+HMAC 链）、ratelimit/（@RateLimit+Redis Lua）、log/（TraceId/AccessLog/脱敏）
├── config/          # Security、CORS、Redis、缓存序列化、MyBatis-Plus、MinIO、OpenAPI、全局异常、JwtLogoutHandler
├── controller/
│   ├── LoginController.java      # 登录/注册/验证码/刷新（顶层）
│   ├── background/               # 管理端 API（/admin/**）：用户、学校、储物柜、套餐、
│   │                             #   订单、支付、充值、优惠券、角色、管理员、Dashboard、导出
│   └── web/                      # 用户端 API（/web/**）：WebUsersController、WebOrdersController 等
├── entity/          # 核心业务实体（12 张表）
├── exception/       # CustomExceptions、GameServiceException、UserAuthenticationException
├── filter/          # JwtAuthenticationFilter（每请求 JWT 校验 + Redis 黑名单）
├── from/            # 请求 DTO：Add*/Update*/Search*From，分页继承 BaseSearchFrom
├── mapper/          # BaseMapper 接口（自定义 SQL 在 resources/mapper/*.xml）
├── service/         # 接口继承 IService<T>；impl/ 继承 ServiceImpl<M, T>
├── task/            # OrderTimeoutManager（动态 TaskScheduler，订单超时取消）
├── utils/           # JwtUtil（双 token）、JsonUtils、LoginUser、PickupCodeUtils、SecurityUtil、UserContextHolder
├── vo/              # 视图对象 {实体}Vo
├── divination/      # 观象台（四算法 core / llm SSE / prompt / rag / 审计 / 用量聚合）
├── game/            # 游戏库 / 关卡 / 存档 / 成绩 / 排行榜 + MinIO 对象存储
├── mood/            # 情绪记录 / 月历 / 统计
├── notice/          # 站内信与已读记录
├── points/          # 积分账户 / 签到 / 流水 / 月榜
├── review/          # 记忆复习（FSRS-6 调度器 + 资料/卡片/队列）
├── subscription/    # 订阅 / 续费推进 / 用量 / 提醒
├── toolbox/         # 短链 + 设备投递（delivery/ 子包含公开对端接口）
└── workbench/       # 工作台入口 + deeplink 路由字典
```

> **注意**：本项目没有逻辑删除——无 `@TableLogic`、表无 `is_delete` 列，删除均为物理删除。

---

## 三、URL 路由与权限

| URL 前缀 | 认证 | 角色 | 消费端 |
|---|---|---|---|
| `/auth/**` | 否 | 公开（登录、注册、验证码、刷新） | 三端 |
| `/admin/**` | 是 | ROLE_ADMIN | Web 管理后台 |
| `/web/auth/div/**` | 是 | ROLE_USER（先于 `/web/auth/**`） | Android / 鸿蒙 |
| `/web/auth/**` | 是 | ROLE_USER | Android / 鸿蒙 |
| `/web/**` | 否 | 公开 Web 接口 | Android / 鸿蒙 |

**公开只读路径**：`/web/laundryItems/all`、`/web/schools/allName`、`/web/payments/payType`、`/web/payments/payStatus`、`/web/app/version`、`/web/app/download`、`/web/t/**`、`/web/deeplink/routes`、`/web/config`、`/web/delivery/**`、`/swagger-ui/**`、`/v3/api-docs/**`；其余 `/web/**`（如 `/web/orders/status`、`/web/lockers/status`、`/web/lockers/all`）需任意已认证用户。

双登录体系：管理员走 `/auth/adminUsers/login`（用户名），普通用户走 `/auth/user/login`（手机号）。登录返回**双 token**（access 2h + refresh 7d），`sub` 带前缀 `admin-{用户名}` 或 `user-{手机号}`；refresh 仅用于 `/auth/refresh` 一次性轮换。

认证链：`JwtAuthenticationFilter`（提取 Bearer token → 校验 + Redis 黑名单 → `CustomUserDetailsService` 加载 `LoginUser` → 设置 SecurityContext）→ `SecurityConfig` 角色鉴权。登出走 Redis 黑名单（jti + 剩余 TTL）。

---

## 四、核心模式

- **统一响应** `Result<T>`：成功 `Result.ok(data)`，失败 `Result.failMsg(msg)`；全局异常由 `ExceptionControllerAdvice` 捕获。
- **参数校验**：Form 对象 + `@Valid` + Jakarta Bean Validation。
- **MyBatis-Plus**：优先内置方法（save/removeById/getById/updateById/list/count/page），复杂查询写 XML（一律 `#{}` 预编译）。
- **用户上下文**：`UserContextHolder`（ThreadLocal）在过滤器设置，Service 层经 `SecurityUtil.getCurrentUser()` 获取。
- **库存/余额并发**：扣款用条件更新 `balance >= amount`（`UsersMapper.xml`）、储物柜分配 `FOR UPDATE`（`LockersMapper.xml`）、订单行加锁防重复支付（`OrdersMapper`）。
- **横切能力**：`@RateLimit`（Redis Lua，登录/注册/验证码/短链跳转/占卜解读按 IP 或用户限流）、`@Audited`（管理端写操作 HMAC 链审计）、`TraceIdFilter`/`AccessLogFilter`（`X-Request-Id` + MDC + 敏感字段脱敏）。
- **缓存**：Redis `CacheManager`（schools/laundryItems/workbenchEntries/deeplinkRoutes/gameList 30min、coupon 10min、dashboard 2min），缓存序列化带 autoType 白名单。
- **技术底座**：Lombok（@Data/@Slf4j）、Hutool、FastJSON 2（缓存序列化带 autoType 白名单）。

---

## 五、编码规范（硬约束）

1. 所有 API 返回 `Result<T>`，禁止直接返回实体/字符串。
2. 新接口遵循上表路由规范；参数进 `from/`、返回进 `vo/`。
3. **资金与状态流转必须防并发**：条件 UPDATE 判影响行数或 `SELECT FOR UPDATE`，禁止 check-then-act；金额一律后端计算。
4. 数据库结构变更统一修改根目录 `smart_wash.sql`；禁止给 payments/recharge_records 加删除入口。
5. 代码注释、日志用中文；请求级高频日志用 debug。
6. 敏感信息走环境变量，不落 `application.yaml` 默认值。

---

## 六、构建与运行

```bash
mvn spring-boot:run            # 启动（需 MySQL + Redis）
mvn test                       # 测试（注意：当前会连真实 MySQL/Redis）
mvn clean package -DskipTests  # 打包
```

- 提供 `application-dev.yaml`（开发，桩实现开启）与 `application-prod.yaml`（生产，敏感项无默认值围栏，桩实现关闭）两套 profile；短信/支付为桩实现（StubSmsServiceImpl、StubPaymentGatewayServiceImpl）。
- Swagger 与 CORS 含内网通配放行（`http://192.168.*`），按 profile 收紧前不要用于公网。

---

## 七、接口变更联动

改任何接口，必须同步检查（详见根目录 CLAUDE.md 四端联动检查表）：
- 用户端接口 → Android `network/api/` + 鸿蒙 `entry/src/main/ets/network/api/`
- 管理端接口 → Web `src/api/`
- 响应结构/错误码 → 三端各自的拦截器/信封解析

---

## 八、定时任务

| 任务 | 调度 | 用途 |
|---|---|---|
| `OrderTimeoutManager` | 动态 TaskScheduler（每单 30 分钟，启动重载） | 未支付订单 CAS 取消 + 释放柜子 |
| `SubscriptionRenewJob` | 每日 07:30 | 推进过期自动续费订阅（CAS + 幂等提醒） |
| `ToolboxShortCodeCleaner` | 每日 03:30 | 清理过期 30 天的短码 + 孤儿访问明细 |
| `ToolboxDeliveryCleaner` | 每 5 分钟 | 清理过期 10 分钟的投递会话 |
| `DivUsageAggregator` | 每日 00:05 | 聚合昨日占卜用量到 `div_usage_daily` |

## 九、已知问题

完整评审清单（含行号与修复方向）见 `docs/code-review-2026-08-28.md` 第一章。P0 级：

- 订单取消/超时与支付的竞态（`task/OrderTimeoutManager.java`、`OrdersServiceImpl.java`）
- 优惠券并发核销/重复领取（`PaymentsServiceImpl.java`、`UserCouponServiceImpl.java`）
- 充值/支付绕过网关无幂等（`RechargeRecordsServiceImpl.java`）
- JWT 密钥默认值入库（`application.yaml`）、验证码可穷举且未接真实短信（`LoginController.java`）
- Dashboard SQL 引用不存在的 `is_delete` 列（`DashboardMapper.xml`）
- 管理员种子密码为 MD5（smart_wash.sql）

新增注意项：

- **request-log 配置错位**：`application.yaml` 中 `request-log` 缩进在 `toolbox:` 下（实际生效 `toolbox.request-log.*`），而 `RequestLogProperties` 绑定前缀是 `smartwash.request-log`——配置值未生效，需统一。
- **测试**：`src/test` 共 36 个测试类 + H2 测试 profile（`application-test.yaml`/`schema.sql`）；新增核心逻辑配套单测。

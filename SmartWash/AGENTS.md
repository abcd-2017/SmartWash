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
| 缓存 | Redis（`127.0.0.1:6379`，database 3） |
| 认证 | JWT（jjwt，7 天有效期，Redis 登出黑名单） |
| JSON | FastJSON 2 |
| 工具库 | Lombok、Hutool（Snowflake/IdUtil）、MinIO（对象存储） |
| 数据库结构 | 统一由根目录 `smart_wash.sql` 管理 |
| 构建 | Maven（无 Wrapper，用系统 `mvn`） |

---

## 二、包结构

```
src/main/java/com/smartwash/
├── common/          # 枚举（OrderStatus、LockerStatusEnum）、常量、统一响应 Result<T>
├── config/          # Security、CORS、MyBatis-Plus、Redis、全局异常、JwtLogoutHandler
├── controller/
│   ├── LoginController.java      # 登录/注册/验证码（顶层）
│   ├── background/               # 管理端 API（/admin/**）：用户、学校、储物柜、套餐、
│   │                             #   订单、支付、充值、优惠券、角色、管理员、Dashboard、导出
│   └── web/                      # 用户端 API（/web/**）：WebUsersController、WebOrdersController 等
├── entity/          # 数据库实体（MyBatis-Plus @TableName/@TableId）
├── exception/       # CustomExceptions、UserAuthenticationException
├── filter/          # JwtAuthenticationFilter（每请求 JWT 校验）
├── from/            # 请求 DTO：Add*/Update*/Search*From，分页继承 BaseSearchFrom
├── mapper/          # BaseMapper 接口（自定义 SQL 在 resources/mapper/*.xml）
├── service/         # 接口继承 IService<T>；impl/ 继承 ServiceImpl<M, T>
├── task/            # OrderTimeoutManager（订单超时取消，单机内存调度）
├── utils/           # JwtUtil、LoginUser、UserContextHolder(ThreadLocal)、SecurityUtil、QrCodeUtil
└── vo/              # 视图对象 {实体}Vo
```

> **注意**：本项目没有逻辑删除——无 `@TableLogic`、表无 `is_delete` 列，删除均为物理删除。

---

## 三、URL 路由与权限

| URL 前缀 | 认证 | 角色 | 消费端 |
|---|---|---|---|
| `/auth/**` | 否 | 公开（登录、注册、验证码） | 三端 |
| `/admin/**` | 是 | ROLE_ADMIN | Web 管理后台 |
| `/web/auth/**` | 是 | ROLE_USER | Android / 鸿蒙 |
| `/web/**` | 否 | 公开 Web 接口 | Android / 鸿蒙 |

双登录体系：管理员走 `/auth/adminUsers/login`（用户名），普通用户走 `/auth/user/login`（手机号）。JWT `sub` 带前缀 `admin-{用户名}` 或 `user-{手机号}`。

认证链：`JwtAuthenticationFilter`（提取 Bearer token → `CustomUserDetailsService` 加载 `LoginUser` → 设置 SecurityContext）→ `SecurityConfig` 角色鉴权。登出走 Redis 黑名单（jti + 剩余 TTL）。

---

## 四、核心模式

- **统一响应** `Result<T>`：成功 `Result.ok(data)`，失败 `Result.failMsg(msg)`；全局异常由 `ExceptionControllerAdvice` 捕获。
- **参数校验**：Form 对象 + `@Valid` + Jakarta Bean Validation。
- **MyBatis-Plus**：优先内置方法（save/removeById/getById/updateById/list/count/page），复杂查询写 XML（一律 `#{}` 预编译）。
- **用户上下文**：`UserContextHolder`（ThreadLocal）在过滤器设置，Service 层经 `SecurityUtil.getCurrentUser()` 获取。
- **库存/余额并发**：扣款用条件更新 `balance >= amount`（`UsersMapper.xml`）、储物柜分配 `FOR UPDATE`（`LockersMapper.xml`）、订单行加锁防重复支付（`OrdersMapper`）。
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

## 八、已知问题

完整评审清单（含行号与修复方向）见 `/Users/admin/code/Android/SmartWash/docs/code-review-2026-08-28.md` 第一章。P0 级：

- 订单取消/超时与支付的竞态（`task/OrderTimeoutManager.java`、`OrdersServiceImpl.java`）
- 优惠券并发核销/重复领取（`PaymentsServiceImpl.java`、`UserCouponServiceImpl.java`）
- 充值/支付绕过网关无幂等（`RechargeRecordsServiceImpl.java`）
- JWT 密钥默认值入库（`application.yaml`）、验证码可穷举且未接真实短信（`LoginController.java`）
- Dashboard SQL 引用不存在的 `is_delete` 列（`DashboardMapper.xml`）
- 管理员种子密码为 MD5（smart_wash.sql）

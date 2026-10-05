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
| `/auth/**` | 否 | 公开接口（登录、注册、验证码） |
| `/admin/**` | 是 | `ROLE_ADMIN` |
| `/web/auth/**` | 是 | `ROLE_USER` |
| `/web/**` | 否 | 公开 Web 接口 |

两种用户类型，登录流程不同：管理员（`/auth/adminUsers/login`）通过用户名认证，普通用户（`/auth/user/login`）通过手机号认证。登录成功返回 JWT（7 天有效期），`sub` 带前缀 `admin-{用户名}` 或 `user-{手机号}`。

认证链：`JwtAuthenticationFilter`（提取 Bearer token，经 `CustomUserDetailsService` 加载 `LoginUser`，设置 `SecurityContext`）→ `SecurityConfig`（基于角色的访问控制）。

### 包结构

| 包 | 用途 |
|---|---|
| `common/` | 枚举（`OrderStatus`、`LockerStatusEnum` 等）、常量（`DefaultConstant`）、统一响应 `Result<T>` |
| `config/` | Security、CORS、MyBatis-Plus、Redis、全局异常处理 |
| `controller/` | `LoginController` 在顶层；`background/` 管理端 API，`web/` 用户端 API |
| `entity/` | 数据库实体（MyBatis-Plus 映射） |
| `exception/` | 自定义异常（`CustomExceptions`、`UserAuthenticationException`） |
| `filter/` | `JwtAuthenticationFilter` — 每次请求执行 JWT 校验 |
| `from/` | 请求 DTO。命名 `{操作}{实体}From`（`Add*From`、`Update*From`、`Search*From`），分页继承 `BaseSearchFrom` |
| `mapper/` | MyBatis-Plus `BaseMapper`。自定义 SQL 在 `src/main/resources/mapper/*.xml` |
| `service/` | 接口继承 `IService<T>`；实现在 `service/impl/` 继承 `ServiceImpl<M, T>` |
| `task/` | 定时任务（`OrderTimeoutManager` 订单超时取消） |
| `divination/` | 「观象台」占卜子系统（controller / core 四算法 / llm / prompt / entity / mapper / service / vo / from / task） |
| `utils/` | `JwtUtil`、`LoginUser`、`UserContextHolder`（ThreadLocal）、`SecurityUtil`、`QrCodeUtil` |
| `vo/` | 视图对象，命名 `{实体}Vo` |

### 关键模式

- **`Result<T>`** 统一 API 响应：成功 `Result.ok(data)`，失败 `Result.failMsg(msg)`。全局异常由 `ExceptionControllerAdvice` 捕获。**禁止直接返回实体或字符串**。
- **MyBatis-Plus**：`@TableName`/`@TableId`，优先用内置方法（`save`、`removeById`、`getById`、`updateById`、`list`、`count`、`page`），复杂查询才写 XML。`MybatisConfig` 已启用分页插件。
- **注意：本项目没有逻辑删除**——无 `@TableLogic`，表无 `is_delete` 列，删除均为物理删除。涉及资金类记录（payments/recharge_records）禁止新增删除入口。
- **Form 对象**用 `@Valid` + Jakarta Bean Validation 校验。
- **ThreadLocal 用户上下文**：`UserContextHolder.setUser(loginUser)` 在 JWT 过滤器设置，Service 层经 `SecurityUtil.getCurrentUser()` 获取。
- **Lombok** 全项目使用（`@Data`、`@Slf4j`、`@AllArgsConstructor`）；**Hutool** 提供 `Snowflake`、`IdUtil` 等；**FastJSON 2** 是 JSON 库。

## 硬性约束

- **资金/状态操作必须防并发**：任何"先查再改"的订单状态流转、优惠券核销/领取、余额扣减，必须用条件 UPDATE 判断影响行数或 `SELECT FOR UPDATE`，禁止 `getById` 后直接 `updateById`（已知竞态清单见评审报告第一章 P0）。
- **支付/充值金额以后端计算为准**，不信任前端传入价格。
- **新增接口遵循 URL 路由规范**（见上表），响应统一 `Result<T>`。
- **不硬编码敏感信息**：密钥/密码走环境变量，`application.yaml` 中不落默认生产密钥。
- **代码注释和日志使用中文**，日志用 Lombok `@Slf4j`，请求级高频日志用 debug 级别。

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

完整项目规则见本子目录 `CLAUDE.md`，评审报告见 `.claude/docs/code-review-2026-08-28.md`。

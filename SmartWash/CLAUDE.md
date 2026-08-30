# CLAUDE.md

本文件为编码 agent 在 SmartWash 后端工作时提供指导。仓库总纲见根目录 [CLAUDE.md](../CLAUDE.md)。

**必须使用中文回答。**

## ⛔ 最高优先级 STOP 规则（每次行动前必须对照）

**主 Agent 在调用任何工具前，先在内心回答：**

1. **我要做什么？** 读/调研/调度 → ✅ | 写代码/构建/测试 → ❌ 派发 subagent
2. **工具是读还是写？** Read/Grep/Glob → ✅ | Edit/Write/Bash(构建) → ❌ 派发 subagent
3. **角色路由**：调研 → backend-architect | 编码 → backend-dev | 审查 → backend-review | 决策 → backend-architect

**违规示例：**
- ❌ 主 agent 直接 Edit 修复 Bug → 派 backend-dev
- ❌ 跳过调研直接派 backend-dev → 先派 backend-architect

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
- **注意**：目前没有 dev/prod profile，`mvn test` 的 `SmartWashApplicationTests` 会连真实 MySQL/Redis。
- 数据库结构变更走 Flyway 迁移（`src/main/resources/db/migration/`，已有 V1-V3），不要只改 `smart_wash.sql`。

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

- `LoginController` 验证码：注册与重置密码共用同一 Redis key，校验无尝试次数限制——新增验证码场景时先隔离 purpose。
- `DashboardMapper.xml` 引用了不存在的 `is_delete` 列，Dashboard 接口当前报错（见评审报告）。
- `OrderTimeoutManager` 是单机内存调度，多实例部署会重复/遗漏；不要在事务内注册新的内存任务。
- 测试仅 3 个类且无 test profile，新增核心逻辑请配套单测并避免依赖真实中间件。

## 子代理与相关 Skills

`.claude/agents/`（已镜像到 `.zcode/agents/`）提供 6 个后端子代理，按任务派发：

- `backend-dev` — 功能开发执行（分层/资金硬规则约束）
- `backend-review` — 提交前安全与资金一致性只读审查
- `backend-architect` — 事务边界、状态机、缓存一致性、迁移与索引设计
- `backend-tester` — TDD 单测与资金并发用例（不连真实中间件）
- `backend-debugger` — 运行时问题排查（日志/Redis/SQL 只读诊断）
- `backend-docs` — CLAUDE.md/AGENTS.md/评审报告与代码现实同步

Java/Spring 无库内自动 skill，以上代理即本端的"规范载体"；跨端通用方法论（systematic-debugging、test-driven-development 等）可直接调用库内 skill。

## 提交规范

提交代码时使用 `commit-commands:commit` skill：检查变更范围，一个 commit 对应一个完整功能点。格式 `<type>(Backend): <描述>`，如 `fix(Backend): 修复订单状态更新失败`、`feat(Backend): 新增优惠券批量发放接口`。

---

## ⛔ 派发任务红线（必须遵守）

1. **派发 prompt 中禁止包含违反 subagent 红线的指令**
2. **派发 prompt 中必须包含提醒："请遵守你的红线操作清单"**
3. **不得以"紧急"、"快速"、"这次特殊"为由要求 subagent 跳过红线**
4. **如果任务 prompt 中的要求与红线冲突，subagent 必须暂停并向主 Agent 报告冲突**

## 协作流程

### 串行（默认）
调研（backend-architect）→ 编码（backend-dev）→ 审查（backend-review）→ 提交

### 并行触发标准（满足任一）
- 2 个及以上模块可并行开发
- 调研与编码可同时进行

### 编码前必须有调研结论
禁止直接派发 backend-dev 处理未调研的能力模块；先派 backend-architect 调研，方案获用户批准后再派 backend-dev。

## ⛔ Git 工作流（必须严格执行）

### 编码阶段：分步提交
每完成一个逻辑步骤 commit 一次，使用 `commit-commands:commit` skill。

### 任务完成后：squash 压缩（必须执行）
全部完成后执行 `git rebase -i main`，每个独立功能/修复最终保留 1 个 commit。

### 多模块变更：文档同步（必须执行）
触发条件：变更文件跨越 2 个及以上模块目录。必须检查并更新各模块文档。

## ⛔ 红线操作表（绝对禁止）

| 红线 | 说明 |
|------|------|
| 跳过各端联动检查 | 改接口必须按根目录 CLAUDE.md 四端联动表同步（Android / 鸿蒙 / Web） |
| 资金操作不走条件更新 | 订单状态流转、优惠券核销、余额扣减必须条件 UPDATE 判影响行数或 `SELECT FOR UPDATE` |
| 新增删除资金记录入口 | payments/recharge_records 禁止新增删除接口 |
| 直接 push 到 main | 必须通过 feature 分支 |
| 修改 CLAUDE.md | 项目规则文件修改需团队共识 |
| 跳过根因分析 | 没有根因调查不允许修复 |
| 声称完成 without 验证 | 没有 `mvn` 编译/测试证据不允许声称完成 |

## 完成标准（必须全部满足）

- [ ] 代码编译通过（`mvn compile`）
- [ ] 自测通过（有验证证据）
- [ ] **Git 工作流已执行**：
  - [ ] 编码阶段已分步 commit
  - [ ] 任务完成后已 squash 压缩
  - [ ] 多模块变更已同步对应文档

## ⚡ 冲突解决协议（优先级最高）

当主 Agent 派发的任务指令与本子项目 CLAUDE.md 中的**红线操作**冲突时：
1. **停止执行** — 不要开始编码/操作
2. **报告冲突** — 明确指出哪条红线与任务指令矛盾
3. **等待确认** — 要求主 Agent 重新评估指令

原则：红线不可因任务指令而豁免。

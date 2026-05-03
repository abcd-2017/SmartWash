# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指导。

**必须使用中文回答。**

## 项目概述

**SmartWash** 后端 — 基于 Spring Boot 3.4.0 的校园洗衣寄存柜管理平台 REST API。仓库根目录 (`Android/SmartWash/`) 是多项目单体仓库：

| 目录 | 说明 |
|---|---|
| `SmartWash/` | **本项目** — Spring Boot 后端 |
| `SmartWash-Android/` | Android 原生客户端 |
| `SmartWashWeb/` | Vue 3 + Vite Web 管理端/用户端 |
| `SmartWash_Harmony/` | 鸿蒙端 |
| `smart_wash.sql` | MySQL 建表语句 + 种子数据 |

## 构建与运行

```bash
# 启动应用（需要 MySQL 和 Redis）
mvn spring-boot:run

# 运行测试
mvn test

# 打包
mvn clean package -DskipTests
```

Java 17，未包含 Maven Wrapper — 使用系统安装的 `mvn`。

## 基础设施依赖

- **MySQL 8.x** 地址 `127.0.0.1:3306`，数据库 `smart_wash`
- **Redis** 地址 `127.0.0.1:6379`，database `3`
- 配置位于 `src/main/resources/application.yaml`

## 架构

标准 Spring Boot 分层架构：

```
controller → service (接口) → service/impl → mapper (接口) → mapper XML
```

### URL 路由与权限

| URL 前缀 | 需要认证 | 角色 |
|---|---|---|
| `/auth/**` | 否 | 公开接口（登录、注册、验证码） |
| `/admin/**` | 是 | `ROLE_ADMIN` |
| `/web/auth/**` | 是 | `ROLE_USER` |
| `/web/**` | 否 | 公开 Web 接口 |

两种用户类型，登录流程不同：管理员（`/auth/adminUsers/login`）通过用户名认证，普通用户（`/auth/user/login`）通过手机号认证。登录成功返回 JWT（7天有效期）。JWT 的 `sub` 字段带前缀：`admin-{用户名}` 或 `user-{手机号}`。

认证链：`JwtAuthenticationFilter`（提取 Bearer token，通过 `CustomUserDetailsService` 加载 `LoginUser`，设置 `SecurityContext`）→ `SecurityConfig`（基于角色的访问控制）。

### 包结构

| 包 | 用途 |
|---|---|
| `common/` | 枚举类（`OrderStatus`、`LockerStatusEnum` 等）、常量（`DefaultConstant`）、统一响应封装 `Result<T>` |
| `config/` | Spring 配置类：Security、CORS、MyBatis-Plus、Redis、全局异常处理 |
| `controller/` | REST 控制器。`LoginController` 在顶层；`background/` 存放管理端 API，`web/` 存放用户端 API |
| `entity/` | 数据库实体（MyBatis-Plus 映射） |
| `exception/` | 自定义异常（`CustomExceptions`、`UserAuthenticationException`） |
| `filter/` | `JwtAuthenticationFilter` — 每次请求执行 JWT 校验 |
| `from/` | 请求 DTO / 表单对象。每个实体通常对应 `Add*From`、`Update*From`、`Search*From` |
| `mapper/` | MyBatis-Plus `BaseMapper` 接口。自定义 SQL 在 `src/main/resources/mapper/*.xml` |
| `service/` | 接口继承 `IService<T>`。实现在 `service/impl/` 中，继承 `ServiceImpl<M, T>` |
| `utils/` | `JwtUtil`、`LoginUser`、`UserContextHolder`（ThreadLocal）、`SecurityUtil`、`QrCodeUtil` |
| `vo/` | 返回给控制器的视图对象 |

### 关键模式

- **`Result<T>`** 是统一 API 响应封装：成功用 `Result.ok(data)`，失败用 `Result.failMsg(msg)`。全局异常由 `ExceptionControllerAdvice` 捕获并返回 `Result`。
- **MyBatis-Plus**：实体使用 `@TableName`、`@TableId`，逻辑删除字段 `isDelete`。`MybatisConfig` 启用了分页插件。复杂查询使用 XML mapper 配合 MyBatis 动态 SQL（`<if test="...">`）。
- **MyBatis-Plus `ServiceImpl<M, T>`** 提供内置 CRUD（`save`、`removeById`、`getById`、`updateById`、`list`、`count`、`page`）。自定义方法在 mapper 接口和 XML 中添加。
- **Form 对象**（`from/` 包）使用 `@Valid` + Jakarta Bean Validation 进行请求校验。`BaseSearchFrom` 基类提供 `page`/`size` 分页字段。
- **ThreadLocal 用户上下文**：`UserContextHolder.setUser(loginUser)` 在 JWT 过滤器中设置，Service 层通过 `SecurityUtil.getCurrentUser()` 获取。
- **Lombok** 全项目使用 — `@Data`、`@Slf4j`、`@AllArgsConstructor`。注解处理器已在 `pom.xml` 中配置。
- **Hutool** (`cn.hutool`) 提供工具类，如 `Snowflake`（分布式ID）、`IdUtil`、`RandomUtil`。
- **FastJSON 2** 是 JSON 库（阿里巴巴），非 Jackson。

## 约束

- **提交代码时使用 `commit-commands:commit` skill**：每次提交前通过该 skill 检查变更范围、生成规范的 commit message，确保一个 commit 对应一个完整的功能点而非单个文件。commit message 使用中文，格式：`<type>(Backend): <描述>`（如 `fix(Backend): 修复订单状态更新失败`、`feat(Backend): 新增优惠券批量发放接口`）。
- **所有 API 返回统一使用 `Result<T>`**，禁止直接返回实体或字符串。成功用 `Result.ok(data)`，失败用 `Result.failMsg(msg)`。
- **新增接口遵循现有 URL 路由规范**：管理端 `/admin/**`（ROLE_ADMIN），用户端需要认证 `/web/auth/**`（ROLE_USER），公开接口 `/web/**` 或 `/auth/**`。
- **请求参数放在 `from/` 包**，命名遵循 `{操作}{实体}From`（如 `Add*From`、`Update*From`、`Search*From`）。分页查询继承 `BaseSearchFrom`。
- **返回数据放在 `vo/` 包**，命名遵循 `{实体}Vo`。
- **数据库操作优先使用 MyBatis-Plus 内置方法**（`save`、`removeById`、`getById`、`updateById`、`list`、`count`、`page`），复杂查询才写自定义 SQL。
- **代码注释和日志使用中文**，日志使用 Lombok `@Slf4j`。
- **不要将敏感信息硬编码**，数据库密码等配置项放在 `application.yaml` 中（当前已配置，不要提交到公开仓库）。

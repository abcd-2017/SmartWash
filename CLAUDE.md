# CLAUDE.md

本文件为编码 agent 在 SmartWash 多项目仓库根目录工作时的总纲。进入某个子项目工作前，**必须先读该子项目自己的 CLAUDE.md**（细节以子项目文档为准）。

**必须使用中文回答。**

## 仓库结构

校园智能洗衣平台，四个子项目共用同一个后端 API 与 MySQL 数据库：

| 目录 | 说明 | 技术栈 | 端别文档 |
|------|------|--------|---------|
| `SmartWash/` | Spring Boot 3.4 后端（MyBatis-Plus + MySQL + Redis + JWT） | Java 17 / Maven | [SmartWash/CLAUDE.md](SmartWash/CLAUDE.md) |
| `SmartWash-Android/` | Android 用户端 | Kotlin / Jetpack Compose / Hilt | [SmartWash-Android/CLAUDE.md](SmartWash-Android/CLAUDE.md) |
| `SmartWash_Harmony/` | 鸿蒙 NEXT 用户端 | ArkTS / ArkUI / Stage 模型 | [SmartWash_Harmony/CLAUDE.md](SmartWash_Harmony/CLAUDE.md) |
| `SmartWashWeb/` | Web 管理后台 | Vue 3 / Vite / Element Plus / Pinia | [SmartWashWeb/CLAUDE.md](SmartWashWeb/CLAUDE.md) |
| `smart_wash.sql` | MySQL 建表 + 种子数据（结构变更以本文件为准） | — | — |

## 全局规则

- **提交代码时使用 `commit-commands:commit` skill**：提交前检查变更范围，确保一个 commit 对应一个完整功能点。commit message 中文，格式 `<type>(<scope>): <描述>`，scope 取 `Backend` / `Android` / `Harmony` / `Web` / `SQL`，跨端改动拆分提交或在描述中列明。
- **改动任何接口必须四端联动检查**。同一个接口最多被 4 处消费，改路径/参数/返回结构时按下表核对：

| 改动内容 | 需要同步的位置 |
|---------|--------------|
| 新增/修改用户端接口 | 后端 `controller/web/` + Android `network/api/` + 鸿蒙 `network/api/` |
| 新增/修改管理端接口 | 后端 `controller/background/` + Web `src/api/` |
| 修改响应结构/错误码 | 三端各自的响应包装解析：Android `ResponseInterceptor`、鸿蒙 `Axios.ets`、Web `utils/http.js` |
| 修改枚举/状态码 | 三端各自的枚举映射：Android `utils/OrderStatus.kt`、鸿蒙 `constant/`、Web 页面内映射函数 |

## 统一 API 契约

- **响应信封**：`{ code, message, data }`，业务成功 `code = 200`。HTTP 状态码只反映传输层，业务语义看 `code`。
- **URL 前缀与权限**（后端 SecurityConfig 决定，三端不要绕过）：
  - `/auth/**` 公开（登录、注册、验证码）
  - `/admin/**` 管理端，需 ROLE_ADMIN（Web 后台消费）
  - `/web/auth/**` 用户端，需 ROLE_USER（Android / 鸿蒙消费）
  - `/web/**` 公开 Web 接口
- **认证**：JWT Bearer token，`sub` 带前缀 `admin-{用户名}` 或 `user-{手机号}`。
- **401 语义三端必须一致**：清空本地 token → 清内存登录态 → replace（非 push）到登录页，并发 401 去重只跳一次。

## 环境红线

- **生产禁明文 HTTP**：鸿蒙端 BASE_URL 当前硬编码演示服务器地址（明文 HTTP），Web 端生产地址已改为构建时环境变量注入（不再写死 IP）。改动网络层时必须按环境注入（dev 内网 / prod HTTPS），禁止新增硬编码地址。Android 端 `usesCleartextTraffic=true` 为 demo 全局放行，发版前须按生产地址改为 HTTPS 并移除该开关。
- **密钥不入库**：JWT_SECRET、DB 密码、高德 key/securityJsCode、支付密钥一律走环境变量或本地未跟踪配置文件；发现入库立即轮换。
- 后端地址等环境差异见各子项目 CLAUDE.md 的「构建与运行」。

## 相关 Skills 与子代理编制

**库内 Skills**（`~/.agents/skills/`，共 50+ 个）：Android 端 `android-kotlin`、`android-jetpack-compose` 按 `.kt` 路径自动生效；鸿蒙按需调用 `arkts-development`、`arkts-syntax-assistant`、`harmonyos-app`；Web 视觉用 `frontend-design`/`design`；跨端通用方法论 `systematic-debugging`、`test-driven-development`、`verification-before-completion` 等。

**各工程子代理**（位于各子项目 `.claude/agents/`，每端 6 个，共 24 个）：

| 工程 | 子代理 |
|------|--------|
| 后端 | backend-dev、backend-review、backend-architect、backend-tester、backend-debugger、backend-docs |
| Android | android-dev、android-review、android-compose-ui、android-anim、android-architect、android-tester |
| 鸿蒙 | harmony-dev、harmony-review、harmony-ui、arkts-syntax、harmony-debugger、harmony-docs |
| Web | web-dev、web-review、web-ui、web-perf、web-tester、web-debugger |

按任务性质派发：写功能用 `*-dev`，动效用 `*-anim`/`*-ui`，排查用 `*-debugger`，测试用 `*-tester`，结构决策用 `*-architect`，文档同步用 `*-docs`；`*-review` 在提交前做只读审查。

## 已知风险

四端深度评审（2026-08-28）发现的问题清单与优先级路线图见 **[docs/code-review-2026-08-28.md](docs/code-review-2026-08-28.md)**。做任何涉及订单、支付、充值、优惠券的改动前，先读该报告第一章的 P0 项——后端资金链路存在已知的并发竞态与幂等缺失。

# AGENTS.md

本文件为编码 agent 在 SmartWash 多项目仓库根目录工作时的总纲（与根目录 CLAUDE.md 内容对应）。进入某个子项目工作前，**必须先读该子项目自己的规则文档**（细节以子项目文档为准）。

**必须使用中文回答。**

## 仓库结构

校园智能洗衣平台，四个子项目共用同一个后端 API 与 MySQL 数据库：

| 目录 | 说明 | 技术栈 | 端别文档 |
|------|------|--------|---------|
| `SmartWash/` | Spring Boot 3.4 后端（MyBatis-Plus + MySQL + Redis + JWT） | Java 17 / Maven | [硬规则](SmartWash/CLAUDE.md) · [工程指南](SmartWash/AGENTS.md) |
| `SmartWash-Android/` | Android 用户端 | Kotlin / Jetpack Compose / Hilt（多模块：app壳 + core:init + common五模块 + 7域feature模块） | [硬规则](SmartWash-Android/CLAUDE.md) · [工程指南](SmartWash-Android/AGENTS.md) |
| `SmartWash_Harmony/` | 鸿蒙 NEXT 用户端 | ArkTS / ArkUI / Stage 模型 | [硬规则](SmartWash_Harmony/CLAUDE.md) · [工程指南](SmartWash_Harmony/AGENTS.md) |
| `SmartWashWeb/` | Web 管理后台 | Vue 3 / Vite / Element Plus / Pinia | [硬规则](SmartWashWeb/CLAUDE.md) · [工程指南](SmartWashWeb/AGENTS.md) |
| `smart_wash.sql` | MySQL 建表 + 种子数据（结构变更以本文件为准） | — | — |

## 全局规则

- **提交代码**：提交前检查变更范围，确保一个 commit 对应一个完整功能点。commit message 遵循 [commit-conventions.md](.claude/docs/commit-conventions.md)（Conventional Commits 中文精简版）。**描述必须写解决了什么问题，不要写怎么解决问题**（例：`fix(Android): 修复点击设置页闪退`，而非 `fix(Android): MainActivity 改用 hiltViewModel`）。跨端改动拆分提交或在描述中列明。
- **改动任何接口必须四端联动检查**。同一个接口最多被 4 处消费，改路径/参数/返回结构时按下表核对：

| 改动内容 | 需要同步的位置 |
|---------|--------------|
| 新增/修改用户端接口 | 后端 `controller/web/` + Android 各域 `feature:<域>:api/` + 鸿蒙 `network/api/` |
| 新增/修改管理端接口 | 后端 `controller/background/` + Web `src/api/` |
| 修改响应结构/错误码 | 三端各自的响应包装解析：Android `:common:network` 模块 `ResponseInterceptor`、鸿蒙 `Axios.ets`、Web `utils/http.js` |
| 修改枚举/状态码 | 三端各自的枚举映射：Android 各域 `feature:<域>:api/`（如 `OrderStatus` 在 `:feature:order:api`）、鸿蒙 `constant/`、Web 页面内映射函数 |

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
- 后端地址等环境差异见各子项目规则文档的「构建与运行」。

## 相关 Skills 与子代理编制

**库内 Skills**（`~/.agents/skills/`，共 40 个，ZCode 用户级发现路径兼容）：Android 端 `android-kotlin`、`android-jetpack-compose` 按 `.kt` 路径自动生效；鸿蒙按需调用 `arkts-development`、`arkts-syntax-assistant`、`harmonyos-app`；Web 视觉用 `frontend-design`/`design`；跨端通用方法论 `systematic-debugging`、`test-driven-development`、`verification-before-completion` 等可直接调用。

**工作流方法论**（mattpocock-skills 系列，用户级 skill，与编码工具无关）：工作流统一采用 `implement`（内含 `tdd`）→ `code-review` 双轴审查，调试用 `diagnosing-bugs`，架构用 `improve-codebase-architecture` + `codebase-design`。

**各工程子代理**（位于根目录 `.zcode/agents/`，每端 1 个全栈代理，共 4 个）：

| 工程 | 代理文件 | 覆盖职责 |
|------|---------|---------|
| 后端 | `.zcode/agents/smartwash-backend.md` | 开发、审查、调试、测试、架构 |
| Android | `.zcode/agents/smartwash-android.md` | 开发、审查、调试、测试、UI/动效 |
| 鸿蒙 | `.zcode/agents/smartwash-harmony.md` | 开发、审查、调试、测试、ArkTS 语法 |
| Web | `.zcode/agents/smartwash-web.md` | 开发、审查、调试、测试、性能 |

## 已知风险

四端深度评审（2026-08-28）发现的问题清单与优先级路线图见 **[.claude/docs/code-review-2026-08-28.md](.claude/docs/code-review-2026-08-28.md)**。做任何涉及订单、支付、充值、优惠券的改动前，先读该报告第一章的 P0 项——后端资金链路存在已知的并发竞态与幂等缺失。

## Agent 工作流配套

### Issue tracker

Issues 存放在本仓库 GitHub Issues，使用 `gh` CLI 操作。详见 `.claude/docs/issue-tracker.md`。

### Triage labels

使用五个标准分诊角色标签：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`。详见 `.claude/docs/triage-labels.md`。

### Domain docs

Single-context 布局：根目录一个 `CONTEXT.md` + `docs/adr/`。详见 `.claude/docs/domain.md`。

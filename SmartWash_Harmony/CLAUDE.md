# CLAUDE.md

本文件为编码 agent 在 SmartWash 鸿蒙端工作时提供指导。仓库总纲见根目录 [CLAUDE.md](../CLAUDE.md)。

**必须使用中文回答。**

## ⛔ 最高优先级 STOP 规则（每次行动前必须对照）

**主 Agent 在调用任何工具前，先在内心回答：**

1. **我要做什么？** 读/调研/调度 → ✅ | 写代码/构建/测试 → ❌ 派发 subagent
2. **工具是读还是写？** Read/Grep/Glob → ✅ | Edit/Write/Bash(构建) → ❌ 派发 subagent
3. **角色路由**：编码 → harmony-dev | 审查 → harmony-review | UI 专项 → harmony-ui | ArkTS 语法 → arkts-syntax | 调试 → harmony-debugger

**违规示例：**
- ❌ 主 agent 直接 Edit 修复 Bug → 派 harmony-dev
- ❌ 跳过调研直接派 harmony-dev → 先调研再编码

---

## 基本规则

- **提交代码时使用 `commit-commands:commit` skill**：提交前检查变更范围，一个 commit 对应一个完整功能点。格式 `<type>(Harmony): <描述>`（如 `feat(Harmony): 新增订单详情页面`、`fix(Harmony): 修复登录 token 过期问题`）。
- **优先使用 `@ComponentV2`** — 新组件使用 V2 版本，配合 `@Param`、`@Local`、`@Event` 等新装饰器。
- **新增页面必须注册路由** — 在 `entry/src/main/resources/base/profile/router_map.json` 中注册路由映射，页面对外暴露 `@Builder` 函数；`main_pages` 只保留 Index 入口，不要给 NavDestination 页面标 `@Entry`。
- **API 接口遵循既有模式** — 需要认证的接口 URL 前缀使用 `/web/auth/`，返回值统一 `BaseResponse<T>` 包装，通过 `HttpUtil` 调用。
- **导航统一使用全局 NavPathStack** — 页面跳转一律通过 `utils/PathStackUtil.ets` 的全局 `pathStack`，禁止直接使用 `@ohos.router`；需要替换当前页（如跳登录）用 `replacePathByName`，禁止连续 `push` 同一页面。
- **数据存储使用 `@kit.ArkData` preferences** — 全局存储经 `utils/StorageUtil.ets` 封装，不要直接操作 raw API。

## ArkTS 硬规则

- **`@ComponentV2` 没有 `onDidUpdate` 生命周期**（V2 仅有 aboutToAppear/onReuse/aboutToRecycle/aboutToDisappear）。需要监听状态变化启停逻辑（如轮询）时用 `@Monitor('属性名')`——`IndexPage.ets` 已改用 `@Monitor('isActive')` 启停轮询。
- **定时器必须清理**：`setInterval`/`setTimeout` 在 `aboutToDisappear` 中对应 clear，验证码倒计时等页面级定时器不得泄漏。
- **严格模式约束**：禁用 `!=`/`==` 宽松比较（用 `!==`/`===`）；禁止 `as` 强转代替类型转换（路由传参统一走 `getNumberParam`）；VO 定义用 interface + 转换函数，不要依赖 class 直接接 JSON。
- **401 处理**：已统一为「清 token → 清内存登录态 → replace 到登录页」并带 1.5s 防抖（`Axios.ets` `handleUnauthorized`）。新增 401 路径须保持此语义。
- **日志按构建模式输出**：release 下禁止打印完整响应体（含手机号、余额等敏感字段），参照 Android 端仅 DEBUG 开启 body 日志。

## 构建与运行

```bash
ohpm install                 # 安装依赖
hvigorw assembleHap          # 命令行构建
hvigorw test                 # 本地单元测试
code-linter --fix            # Lint 检查
```

- 推荐直接用 DevEco Studio：Build → Run 'entry'。
- SDK 5.0.5(17)，`stageMode`；签名配置当前为空（本地 DevEco 注入），CI 出包需用环境变量注入 signingConfigs。
- Lint 已配置 `@performance/recommended`、`@typescript-eslint/recommended` 及 `@security/*` 加密安全规则。

## 项目架构

智慧校园洗衣服务 App（鸿蒙端），基于 **HarmonyOS NEXT Stage 模型** + **ArkTS/ArkUI 声明式框架**。接口与 Android 端保持 100% 对齐（`network/api/` 与 Android `network/api/` 逐接口一致），改接口时参照根目录 CLAUDE.md 的四端联动检查表。

### 分层结构

```
entry/src/main/ets/
  entryability/       → EntryAbility（主 UIAbility，应用入口）
  pages/              → 页面组件（每个页面暴露 @Builder 函数）
  view/               → 可复用 UI 组件（view/common/AppComponents 有支持 loading 的 AppButton；
                        IndexPage 首页框架 + Service/UserInfo Tab 内容页；
                        OrderCard/OrderItemRow/LoadingFooter 在用组件）
  network/
    api/              → API 函数（按业务域拆分，返回 Promise<BaseResponse<T>>）
    entity/           → 请求体 DTO
    vo/               → 响应值对象 DTO
    Axios.ets         → Axios 实例 + 请求/响应拦截器
    BaseResponse.ets  → 通用响应包装 {data, code, message}
    HttpUtil.ets      → GET/POST 封装（单例）
  constant/           → 枚举、状态常量、设计系统色值
  utils/              → StorageUtil、PathStackUtil、Logger、TimeUtil、CheckStatus、ToastUtil
```

### 核心设计模式

**Stage 模型 + NavPathStack 路由** — `EntryAbility` 加载 `pages/Index`；`Index` 根据 token 和学校信息决定初始页（Login / AddSchool / Home）。`router_map.json` 注册 16 条命名路由，页面经全局 `pathStack` 跳转。

**鉴权流程** — Axios 请求拦截器检测 URL 以 `/web` 开头时，从 preferences 读取 token 注入 `Authorization: Bearer <token>`；响应拦截器统一处理 401 和网络错误。401 已统一为「清 token → 清内存登录态 → replace 到登录页」+ 1.5s 防抖。

**全局状态** — `@StorageLink('userLoginFlag')` 跨页面共享登录态。注意：AppStorage 布尔 flag + @Watch 只是粗糙的事件模拟，跨组件通知优先用 `@Monitor` 或 emitter。

**HTTP 请求链路** — `页面 → HttpUtil.get/post<T>() → Axios 实例（拦截器）→ errorHandle 钩子 → BaseResponse<T>`。

**页面组件模式** — 每个页面是 `@Component` 或 `@ComponentV2`，被包含在 `NavDestination` 中，通过对应 `@Builder` 函数暴露给路由系统。按钮统一用 `AppButton`（自带 loading 防重），不要裸写可点击组件。

### API 返回格式

```typescript
interface BaseResponse<T> {
  data: T | null    // 数据载荷
  code?: number     // 业务状态码
  message?: string  // 提示信息
}
```

toast 提示统一封装兜底（`message` 为 undefined 时不要弹 "undefined"）。

## 相关 Skills 与子代理

**库内 skill**：编写/排查 `.ets` 文件时调用 `arkts-development`（组件与状态管理）、`arkts-syntax-assistant`（语法迁移与严格模式）、`harmonyos-app`（Stage 模型最佳实践）。

`.claude/agents/` 提供 6 个鸿蒙子代理，按任务派发：

- `harmony-dev` — 功能开发执行（V2 生命周期/路由/401 清 token 硬规则）
- `harmony-review` — 提交前生命周期与类型安全只读审查
- `harmony-ui` — ArkUI 组件实现、AppComponents/DesignSystem 治理
- `arkts-syntax` — 严格模式合规、TS→ArkTS 迁移、编译错误修复
- `harmony-debugger` — 轮询/定时器/路由堆栈/preferences 时序排查
- `harmony-docs` — 文档与代码一致性、页面路由清单、评审报告状态同步

## 已知坑（改动前先看）

完整清单见 [docs/code-review-2026-08-28.md](../docs/code-review-2026-08-28.md) 第四章，重点关注：

- `network/Axios.ets:22` 硬编码演示服务器明文 HTTP 地址（与 Android 分环境不一致）——改网络层时一并环境化（参照 Android 端 Gradle 属性注入或 DevEco 多 target/profile）。
- `view/IndexPage.ets` 轮询已改用 `@Monitor('isActive')` 启停（已修复）。
- `pages/Laundry.ets → Payment` 传参已统一 `number` 类型（已修复）。
- `pages/Payment.ets` 支付按钮已加 `paying` loading 防重、Radio 已支持取消选券（已修复）。
- `pages/Recharge.ets` 金额校验已改用 `parseFloat` 并校验 >0 与上限（已修复）。
- 待清理代码：`view/CouponCard.ets`、`UserCouponCard.ets`、`OrderStatusCard.ets` 无引用，待删除，不要复用（`OrderCard.ets`、`OrderItemRow.ets`、`LoadingFooter.ets` 为在用组件）。
- `StorageUtil` 依赖非空断言且初始化未 await，存在时序隐患；token 明文存储待迁移 HUKS/Asset Store。

## 依赖说明

- HTTP 客户端：`@ohos/axios` v2.2.6（注意：声明在根 `oh-package.json5`，entry 模块 dependencies 为空，属待修的依赖错位）
- 测试框架：`@ohos/hypium` + `@ohos/hamock`（当前仅模板用例）
- 包管理器 ohpm，Registry `https://ohpm.openharmony.cn/ohpm/`
- 后端 API 地址当前硬编码于 `Axios.ets`（明文 HTTP，待环境化）

## 提交规范

见顶部基本规则。涉及接口变更时参照根目录 CLAUDE.md 的四端联动检查表。

---

## ⛔ 派发任务红线（必须遵守）

1. **派发 prompt 中禁止包含违反 subagent 红线的指令**
2. **派发 prompt 中必须包含提醒："请遵守你的红线操作清单"**
3. **不得以"紧急"、"快速"、"这次特殊"为由要求 subagent 跳过红线**
4. **如果任务 prompt 中的要求与红线冲突，subagent 必须暂停并向主 Agent 报告冲突**
5. **涉及鸿蒙 API 的派发 prompt 必须点名离线优先**：先查 `arkts-development` / `arkts-syntax-assistant` skill，未命中才允许在线 fallback

## 协作流程

### 串行（默认）
调研 → 编码（harmony-dev）→ 审查（harmony-review）→ 提交

### 并行触发标准（满足任一）
- 2 个及以上模块可并行开发
- 调研与编码可同时进行

### 编码前必须有调研结论
禁止直接派发 harmony-dev 处理未调研的能力模块；先调研，方案获用户批准后再派 harmony-dev。

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
| 鸿蒙 API 未经核实落码 | 写 `.ets` 前必须查 `arkts-development`/`arkts-syntax-assistant` skill，禁止凭记忆编造 ArkTS/`@ohos.*` API |
| @ComponentV2 误用 onDidUpdate | V2 无此生命周期，状态变化启停逻辑用 `@Monitor('属性名')` |
| 定时器不清理 | `setInterval`/`setTimeout` 必须在 `aboutToDisappear` 中 clear |
| 401 不清 token | 跳转登录前必须清空本地 token 并复位登录态 |
| 路由裸强转参数 | 禁止 `[0] as X`，必须用 `getParamByName` |
| 直接 push 到 main | 必须通过 feature 分支 |
| 修改 CLAUDE.md | 项目规则文件修改需团队共识 |
| 声称完成 without 验证 | 没有 `hvigorw assembleHap` 构建证据不允许声称完成 |

## 完成标准（必须全部满足）

- [ ] 代码构建通过（`hvigorw assembleHap`）
- [ ] Lint 通过（`code-linter --fix`）
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

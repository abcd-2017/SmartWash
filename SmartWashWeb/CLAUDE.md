# CLAUDE.md

本文件为编码 agent 在 SmartWash Web 管理后台工作时提供指导。仓库总纲见根目录 [CLAUDE.md](../CLAUDE.md)。

**必须使用中文回答。**

## ⛔ 最高优先级 STOP 规则（每次行动前必须对照）

**主 Agent 在调用任何工具前，先在内心回答：**

1. **我要做什么？** 读/调研/调度 → ✅ | 写代码/构建/测试 → ❌ 派发 subagent
2. **工具是读还是写？** Read/Grep/Glob → ✅ | Edit/Write/Bash(构建) → ❌ 派发 subagent
3. **角色路由**：编码 → web-dev | 审查 → web-review | UI 专项 → web-ui | 性能 → web-perf | 调试 → web-debugger

**违规示例：**
- ❌ 主 agent 直接 Edit 修复 Bug → 派 web-dev
- ❌ 跳过调研直接派 web-dev → 先调研再编码

---

## 核心规则

- **提交代码时使用 `commit-commands:commit` skill**：提交前检查变更范围，一个 commit 对应一个完整功能点。格式 `<type>(Web): <描述>`（如 `feat(Web): 新增学校管理页面`、`fix(Web): 修复登录 token 过期问题`）。
- **遵循现有代码模式**：新增页面和 API 模块必须遵循项目已有模式，不要自行发明新风格。
- **使用 `<script setup>` 语法**：Vue 组件统一 Composition API + `<script setup>`。
- **UI 组件使用 Element Plus**：表单、表格、弹窗、按钮等统一 Element Plus。
- **权限不可只做前端校验**：当前路由守卫仅检查 localStorage 中的 `role`（登录后硬编码写入 `"admin"`，可被篡改）。新增管理功能时，权限判断必须以后端接口鉴权为准，前端仅做展示层控制。

## 项目概述

SmartWash 智能洗衣管理系统的 Vue 3 后台管理端，功能：学校管理、学生管理、洗护套餐、寄存柜、订单、支付记录、充值记录、优惠券、角色管理、管理员用户管理。

## 常用命令

```sh
npm install        # 安装依赖
npm run dev        # 启动开发服务器（端口 5000，绑定 0.0.0.0）
npm run build      # 生产构建，输出到 dist/
npm run preview    # 预览生产构建
```

- **测试**：vitest + happy-dom + @vue/test-utils 已配置，`package.json` 已含 `test`（`vitest run`）与 `lint`/`lint:fix` script；现有 `src/__tests__/http.test.js` 的 baseURL 与响应解包断言已和实现脱节（跑必挂）——新增测试前先修这两处。
- **环境变量**：生产 API 地址已改为构建时环境变量 `SMART_WASH_BASE_URL` 注入（不再写死 IP）；地图组件依赖 `VITE_AMAP_KEY` 与 `VITE_AMAP_SECURITY_CODE`，**两者已在 `.env.development`/`.env.production` 声明**（`VITE_AMAP_KEY` 当前为空需配置，`VITE_AMAP_SECURITY_CODE` 已迁移自 `index.html` 内联，旧值已泄露待轮换）。

## 技术架构

**技术栈**：Vue 3（Composition API，`<script setup>`）、Vite 6、Element Plus（按需引入 via unplugin-auto-import/vue-components）、Pinia、Vue Router、Axios、Day.js。`@` 别名映射到 `src/`。

### 分层结构

1. **入口** — `src/main.js`：注册 Element Plus、Pinia、Router，`$dayjs` 全局属性。
2. **HTTP 层** — `src/utils/http.js`：Axios 实例。请求拦截器从 `useAuthStore` 取 token 加 `Bearer` 头；响应拦截器解包 `response.data`，`res.code !== 200` 视为错误。新代码保持「清 token + 跳 `/login`」语义，不要使用 `window.location.reload()`。
3. **API 层** — `src/api/*.js`：按领域拆分（共 13 个模块：order/user/school/laundry/locker/payment/recharge/coupon/role/adminUser/auth/dashboard/userCoupon/divination），统一 `request({url, method, params})` 模式，`code === 200` 时返回 `res.data`，失败抛 `Error(res.message)`。
4. **状态管理** — `src/stores/auth.js`：Pinia `useAuthStore` 统一管理 token/角色（初始化自动从 localStorage 恢复），路由守卫与 HTTP 拦截器统一从 store 读取登录态。不要新增裸 localStorage 读写。
5. **路由** — `src/router/index.js`：管理页面均为 `Layout` 子路由（路径 `/`），`/login` 免认证；**所有页面已使用 `() => import(...)` 懒加载**（含 404 兜底页 `NotFound.vue`，通配路由 `/:pathMatch(.*)*`）。`beforeEach` 守卫从 `useAuthStore` 检查 token/角色。
6. **布局** — `src/components/Layout/Layout.vue`：`Sidebar`（菜单，按 `meta.showInMenu` 过滤）+ `Navbar`（meta 面包屑 + 用户下拉）+ `<router-view />`。菜单/面包屑/图标均由路由 `meta` 驱动，新增页面记得配齐 meta（title、showInMenu、icon）。
7. **页面** — `src/views/`：登录页 + `src/views/system/` 下 11 个管理模块 CRUD 页面 + `src/views/divination/` 下 7 个观象台管理页面（Prompt/语料/审计/用量/拦截/模型/设置）+ `NotFound.vue`（404 兜底）。

### API 响应约定

后端统一返回 `{ code: 200, message: "...", data: ... }`。HTTP 拦截器对 `code !== 200` reject；API 函数进一步解包，直接返回 `res.data`（分页接口即 `{ records, total }`）。

### CRUD 页面模式

每个管理页面遵循相同结构：

- 响应式 `listQuery`（搜索 + 分页参数）
- `el-table` + `v-loading`，`el-pagination` 分页
- `el-dialog` 弹窗承载新增/编辑，内含 `el-form` 校验（手机号正则、密码长度等校验写法参考 `UserList.vue`）
- 标准方法：`fetchData`、`handleSearch`、`resetSearch`、`handleCreate`、`handleEdit`、`handleDelete`、`submitForm`
- `formatTime` 用 dayjs 格式化；枚举状态经映射函数渲染文本/标签（参考 `OrderList.vue`）

**重要**：这套结构在 11 个列表页中是复制粘贴的（含 `formatTime`、时间范围 computed 均逐字重复）。新增列表页时，若发现逻辑在多页重复，优先抽取 composable（如 `useTableList`、`useTimeRange`）或放入 `src/utils/`，禁止继续复制第 12 份。枚举/字典值新增时建 `src/constants/` 统一维护，与后端枚举对齐。

## 相关 Skills 与子代理

**库内 skill**：新页面视觉设计或整体风格调整时调用 `frontend-design` 或 `design`；无 Vue 自动生效 skill。

`.claude/agents/` 提供 6 个 Web 子代理，按任务派发：

- `web-dev` — 功能开发执行（CRUD 模式/composable 复用/安全红线约束）
- `web-review` — 提交前权限与安全只读审查
- `web-ui` — Element Plus 视觉一致性治理、组件抽象、状态设计
- `web-perf` — 路由懒加载、按需引入、Vite 分包（量化驱动）
- `web-tester` — vitest 基建修复与拦截器/工具用例（TDD）
- `web-debugger` — 登录态/401/静默失败/Element Plus 行为坑排查

## 已知坑（改动前先看）

完整清单见 [docs/code-review-2026-08-28.md](../docs/code-review-2026-08-28.md) 第三章，重点关注：

- 高德 securityJsCode 已从 `index.html` 内联迁移至 `.env.*` 的 `VITE_AMAP_SECURITY_CODE`（旧值 `cc4d5ecf...` 已随 git 入库视为泄露，待轮换）；`VITE_AMAP_KEY` 已声明但当前为空——地图功能暂不可用，需到高德控制台创建/轮换后填入。
- 下拉选项多处用 `size:1000` 拉全量（`UserList.vue` 等），数据量大即卡——新增下拉优先做专用接口或全局缓存。
- 已配置 ESLint（`eslint.config` 扁平配置 + `eslint-plugin-vue`）与 Prettier，`package.json` 含 `lint`/`lint:fix` script；`RechargeList.vue` 有整块注释死代码待清理。
- 404 页面已存在（`NotFound.vue` + 通配路由 `/:pathMatch(.*)*`）。

## 提交规范

见顶部核心规则。涉及接口变更时参照根目录 CLAUDE.md 的四端联动检查表。

---

## ⛔ 派发任务红线（必须遵守）

1. **派发 prompt 中禁止包含违反 subagent 红线的指令**
2. **派发 prompt 中必须包含提醒："请遵守你的红线操作清单"**
3. **不得以"紧急"、"快速"、"这次特殊"为由要求 subagent 跳过红线**
4. **如果任务 prompt 中的要求与红线冲突，subagent 必须暂停并向主 Agent 报告冲突**

## 协作流程

### 串行（默认）
调研 → 编码（web-dev）→ 审查（web-review）→ 提交

### 并行触发标准（满足任一）
- 2 个及以上模块可并行开发
- 调研与编码可同时进行

### 编码前必须有调研结论
禁止直接派发 web-dev 处理未调研的能力模块；先调研，方案获用户批准后再派 web-dev。

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
| 权限只做前端校验 | 必须以后端接口鉴权为准，前端仅做展示层控制 |
| 复制粘贴 CRUD 页 | 发现逻辑在多页重复，优先抽取 composable（`useTableList`/`useTimeRange`）复用 |
| 硬编码密钥/URL | 高德 key / API 地址一律走 `import.meta.env`，禁止新增硬编码 |
| 使用 v-html | 禁止使用，防止 XSS |
| 新增裸 localStorage 读写 | 全局状态统一放 `src/stores/`（Pinia） |
| 直接 push 到 main | 必须通过 feature 分支 |
| 修改 CLAUDE.md | 项目规则文件修改需团队共识 |
| 声称完成 without 验证 | 没有 `npm run build` 证据不允许声称完成 |

## 完成标准（必须全部满足）

- [ ] 代码构建通过（`npm run build`）
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

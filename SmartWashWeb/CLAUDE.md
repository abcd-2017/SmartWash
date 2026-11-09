# CLAUDE.md

本文件为编码 agent 在 SmartWash Web 管理后台工作时提供指导。仓库总纲见根目录 [CLAUDE.md](../CLAUDE.md)。

**遵守共享规则**：STOP规则、派发红线、协作流程、Git工作流、冲突协议等见 [.claude/docs/shared-rules.md](../.claude/docs/shared-rules.md)。提交规范见根目录 CLAUDE.md。

**必须使用中文回答。**

## 核心规则

- **遵循现有代码模式**：新增页面和 API 模块必须遵循项目已有模式，不要自行发明新风格。
- **使用 `<script setup>` 语法**：Vue 组件统一 Composition API + `<script setup>`。
- **UI 组件使用 Element Plus**：表单、表格、弹窗、按钮等统一 Element Plus。
- **权限不可只做前端校验**：当前路由守卫仅检查 localStorage 中的 `role`（登录后硬编码写入 `"admin"`，可被篡改）。新增管理功能时，权限判断必须以后端接口鉴权为准，前端仅做展示层控制。

## 项目概述

SmartWash 智能洗衣管理系统的 Vue 3 后台管理端。管理主航道之外，还承载平台实验性模块的运营台：

- **洗衣主航道**：学校、学生、洗护套餐、寄存柜、订单、支付记录、充值记录、优惠券、用户优惠券、角色、管理员账号、验证码查询。
- **平台模块**：站内信（消息中心）、订阅管理、心情报表、操作审计、游戏库、工作台入口、跳转路由字典、短链管理。
- **观象台**：Prompt / 语料 / 审计复审 / 用量看板 / 拦截日志 / 模型目录 / 平台设置。
- **公开页**：设备投递对端页（`/d/:code?`，免登录，交付箱业务的对端承载）。

## 常用命令

```sh
npm install        # 安装依赖
npm run dev        # 启动开发服务器（端口 5000，绑定 0.0.0.0）
npm run build      # 生产构建，输出到 dist/
npm run preview    # 预览生产构建
```

- **测试**：vitest + happy-dom + @vue/test-utils 已配置，`package.json` 含 `test`（`vitest run`）与 `lint`/`lint:fix`。当前生效 3 个用例（`http.test.js`、`LockerList.test.js`、`toolbox.test.js`）；另有 2 个审计相关用例以 `.bak-feat-audit-log` 后缀禁用（不匹配 vitest 默认 include），需要时改后缀启用。
- **环境变量**：生产 API 地址已改为构建时环境变量 `SMART_WASH_BASE_URL` 注入（不再写死 IP）；地图组件依赖 `VITE_AMAP_KEY` 与 `VITE_AMAP_SECURITY_CODE`，**两者已在 `.env.development`/`.env.production` 声明**（`VITE_AMAP_KEY` 当前为空需配置，`VITE_AMAP_SECURITY_CODE` 已迁移自 `index.html` 内联，旧值已泄露待轮换）。

## 技术架构

**技术栈**：Vue 3（Composition API，`<script setup>`）、Vite 6、Element Plus（按需引入 via unplugin-auto-import/vue-components）、Pinia、Vue Router、Axios、Day.js。`@` 别名映射到 `src/`。

### 分层结构

1. **入口** — `src/main.js`：注册 Element Plus、Pinia、Router，`$dayjs` 全局属性。
2. **HTTP 层** — `src/utils/http.js`：默认导出 `http`（管理端实例）+ 命名导出 `envelopeHttp`（公开信封实例，供设备投递页）。请求拦截器从 `useAuthStore` 取 token 加 `Bearer` 头；响应拦截器解包 `response.data`，`code !== 200` 视为错误。**401 已统一为单飞续签**：`/auth/refresh` 换新双 token → 落盘 store → 重试原请求一次，仍 401 则清态跳 `/login?redirect=`；1.5s 去重窗防并发堆叠。新代码不要使用 `window.location.reload()`，也不要另写一套 401 处理。
3. **API 层** — `src/api/*.js`：按领域拆分，共 24 个模块：order / user / school / laundry / locker / payment / recharge / coupon / role / adminUser / auth / dashboard / userCoupon / divination / game / toolbox / workbench / deeplink / notice / subscription / mood / audit / captcha / delivery。统一 `request({url, method, params})` 模式，`code === 200` 时返回 `res.data`，失败抛 `Error(res.message)`。例外：`delivery.js` 用 `envelopeHttp`（公开端返回完整信封，业务码 200/403/404/409/413 由页面分支）。
4. **状态管理** — `src/stores/auth.js`：Pinia `useAuthStore` 统一管理 token / refreshToken / role（初始化从 localStorage 恢复；`setTokens` 供 401 续签后更新），路由守卫与 HTTP 拦截器统一从 store 读取登录态。不要新增裸 localStorage 读写。
5. **路由** — `src/router/index.js`：管理页面均为 `Layout` 子路由（路径 `/`），`/login` 与 `/d/:code?`（设备投递对端页）免认证；**所有页面已使用 `() => import(...)` 懒加载**（含 404 兜底页 `NotFound.vue`，通配路由 `/:pathMatch(.*)*`）。`beforeEach` 守卫从 `useAuthStore` 检查 token/角色；侧边栏按 `MENU_GROUP_CONFIG` 分组渲染。
6. **布局与共享层** — `src/components/Layout/`（Layout + Sidebar + Navbar，菜单/面包屑/图标由路由 `meta` 驱动）、`AmapPicker.vue` / `RegionCascader.vue`（高德选点与省市区）、`TrendChart.vue`（ECharts 趋势图）；通用逻辑抽在 `src/composables/`（`useTableList` 列表分页、`useTimeRange`、`useConfirm`、`useAnimatedNumber`）。新增页面优先复用，禁止再复制第 N 份 CRUD。
7. **页面** — `src/views/`：登录页 + `system/` 16 个管理页（含审计、站内信、订阅、心情报表、验证码查询）+ `divination/` 7 个观象台页 + `game/` 3 个游戏库页 + `toolbox/` 1 个短链页 + `workbench/` 2 个配置页 + `delivery/` 2 个公开投递页 + `NotFound.vue`。

### API 响应约定

后端统一返回 `{ code: 200, message: "...", data: ... }`。HTTP 拦截器对 `code !== 200` reject；API 函数进一步解包，直接返回 `res.data`（分页接口即 `{ records, total }`）。

### CRUD 页面模式

每个管理页面遵循相同结构：

- 响应式 `listQuery`（搜索 + 分页参数）
- `el-table` + `v-loading`，`el-pagination` 分页
- `el-dialog` 弹窗承载新增/编辑，内含 `el-form` 校验（手机号正则、密码长度等校验写法参考 `UserList.vue`）
- 标准方法：`fetchData`、`handleSearch`、`resetSearch`、`handleCreate`、`handleEdit`、`handleDelete`、`submitForm`
- `formatTime` 用 dayjs 格式化；枚举状态经映射函数渲染文本/标签（参考 `OrderList.vue`）

**重要**：`src/composables/` 已提供 `useTableList`（列表/分页/搜索/竞态守卫）与 `useTimeRange`，新列表页必须复用；`formatTime`/`formatAmount`/`formatFileSize` 统一在 `src/utils/format.js`。枚举/字典值统一在 `src/constants/dict.js`（68 个导出，含订单/支付/充值/优惠券/短码/观象台/工作台/游戏/深链/情绪/订阅/站内信分组），与后端枚举对齐，禁止页面内联映射。

## 相关 Skills 与子代理

**库内 skill**：新页面视觉设计或整体风格调整时调用 `frontend-design` 或 `design`；无 Vue 自动生效 skill。

`.claude/agents/` 提供统一的 Web 全栈代理：

- `smartwash-web` — 开发 / 审查 / 调试 / TDD 一体化代理（Vue 3 + Vite + Element Plus + Pinia）

## 已知坑（改动前先看）

详见 [.claude/web-known-pitfalls.md](.claude/web-known-pitfalls.md)。要点：

- 高德 `VITE_AMAP_SECURITY_CODE` 旧值已泄露待轮换，`VITE_AMAP_KEY` 当前为空（地图选点/行政区划不可用）。
- 设备投递公开页（`/d/:code?`）走 `envelopeHttp`，业务码 403/404/409/413 由页面分支处理，不要按管理端 `code !== 200` 的惯例直接 reject。
- 401 已单飞续签（双 token），不要再写 `window.location.reload()` 或第二套清态逻辑。

---

## ⛔ 红线操作表（Web 特化，绝对禁止）

| 红线 | 说明 |
|------|------|
| 权限只做前端校验 | 必须以后端接口鉴权为准，前端仅做展示层控制 |
| 复制粘贴 CRUD 页 | 发现逻辑在多页重复，优先抽取 composable（`useTableList`/`useTimeRange`）复用 |
| 硬编码密钥/URL | 高德 key / API 地址一律走 `import.meta.env`，禁止新增硬编码 |
| 使用 v-html | 禁止使用，防止 XSS |
| 新增裸 localStorage 读写 | 全局状态统一放 `src/stores/`（Pinia） |

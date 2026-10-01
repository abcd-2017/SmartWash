# CLAUDE.md

本文件为编码 agent 在 SmartWash Web 管理后台工作时提供指导。仓库总纲见根目录 [CLAUDE.md](../CLAUDE.md)。

**遵守共享规则**：STOP规则、派发红线、协作流程、Git工作流、冲突协议等见 [docs/agents/shared-rules.md](../../docs/agents/shared-rules.md)。提交规范见根目录 CLAUDE.md。

**必须使用中文回答。**

## 核心规则

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

`.claude/agents/` 提供统一的 Web 全栈代理：

- `smartwash-web` — 开发 / 审查 / 调试 / TDD 一体化代理（Vue 3 + Vite + Element Plus + Pinia）

## 已知坑（改动前先看）

详见 [web-known-pitfalls.md](web-known-pitfalls.md)。

---

## ⛔ 红线操作表（Web 特化，绝对禁止）

| 红线 | 说明 |
|------|------|
| 权限只做前端校验 | 必须以后端接口鉴权为准，前端仅做展示层控制 |
| 复制粘贴 CRUD 页 | 发现逻辑在多页重复，优先抽取 composable（`useTableList`/`useTimeRange`）复用 |
| 硬编码密钥/URL | 高德 key / API 地址一律走 `import.meta.env`，禁止新增硬编码 |
| 使用 v-html | 禁止使用，防止 XSS |
| 新增裸 localStorage 读写 | 全局状态统一放 `src/stores/`（Pinia） |

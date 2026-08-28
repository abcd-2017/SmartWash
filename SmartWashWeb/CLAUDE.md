# CLAUDE.md

本文件为编码 agent 在 SmartWash Web 管理后台工作时提供指导。仓库总纲见根目录 [CLAUDE.md](../CLAUDE.md)。

**必须使用中文回答。**

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

- **测试**：vitest + happy-dom + @vue/test-utils 已在 `vite.config.js` 配置好，但 `package.json` 尚无 `test` script，且现有 `src/__tests__/http.test.js` 的 baseURL 与响应解包断言已和实现脱节（跑必挂）——新增测试前先修这两处。
- **环境变量**：`.env.production` 定义 API 地址（当前为明文 HTTP 固定 IP，待改 HTTPS 域名）；地图相关组件依赖 `VITE_AMAP_KEY`，**该变量目前未在任何 env 文件定义**，新增地图功能前先补配置。

## 技术架构

**技术栈**：Vue 3（Composition API，`<script setup>`）、Vite 6、Element Plus、Pinia、Vue Router、Axios、Day.js。`@` 别名映射到 `src/`。

### 分层结构

1. **入口** — `src/main.js`：注册 Element Plus、Pinia、Router，`$dayjs` 全局属性。
2. **HTTP 层** — `src/utils/http.js`：Axios 实例。请求拦截器从 localStorage 取 token 加 `Bearer` 头；响应拦截器解包 `response.data`，`res.code !== 200` 视为错误，401 清 token 跳登录（当前实现是 `window.location.reload()`，属待修项——新代码不要模仿）。
3. **API 层** — `src/api/*.js`：按领域拆分（order/user/school/… 共 12 个模块），统一 `request({url, method, params})` 模式，`code === 200` 时返回 `res.data`，失败抛 `Error(res.message)`。
4. **状态管理** — `src/stores/` **当前为空目录**：Pinia 已在 main.js 注册但没有任何 store，登录态散落在 localStorage 各处读写。新建全局状态（如 `useAuthStore` 管理 token/角色）时放这里，不要新增裸 localStorage 读写。
5. **路由** — `src/router/index.js`：管理页面均为 `Layout` 子路由（路径 `/`），`/login` 是唯一 `requiresAuth: false` 路由；`beforeEach` 守卫检查 token。**当前路由全部静态 import，新页面应使用 `() => import(...)` 懒加载**。
6. **布局** — `src/components/Layout/Layout.vue`：`Sidebar`（菜单，按 `meta.showInMenu` 过滤）+ `Navbar`（meta 面包屑 + 用户下拉）+ `<router-view />`。菜单/面包屑/图标均由路由 `meta` 驱动，新增页面记得配齐 meta（title、showInMenu、icon）。
7. **页面** — `src/views/`：登录页 + `src/views/system/` 下各管理模块 CRUD 页面。

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

## 相关 Skills

- 涉及新页面视觉设计或整体风格调整时，调用 `frontend-design` 或 `design` skill。
- 无 Vue 专用自动生效 skill，遵循本文档模式约定即可。

## 已知坑（改动前先看）

完整清单见 [docs/code-review-2026-08-28.md](../docs/code-review-2026-08-28.md) 第三章，重点关注：

- `index.html:10` 内联硬编码高德 securityJsCode（已泄露，待轮换）；地图 key 走 env 但未配置——两者统一收敛到 env + 构建注入。
- 下拉选项多处用 `size:1000` 拉全量（`UserList.vue` 等），数据量大即卡——新增下拉优先做专用接口或全局缓存。
- 无 ESLint/Prettier 配置；`RechargeList.vue` 有整块注释死代码待清理。
- 无 404 页面（通配路由直接 redirect 首页）。

## 提交规范

见顶部核心规则。涉及接口变更时参照根目录 CLAUDE.md 的四端联动检查表。

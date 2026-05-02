# CLAUDE.md

本文件为 Claude Code（[claude.ai/code](https://claude.ai/code)）在此仓库中工作时提供指导。

## 核心规则

- **必须使用中文回答**：所有对话回复、代码注释、commit 消息均使用中文。
- **每次修改后必须提交 commit**：任何代码修改完成后，立即创建 git commit。提交信息格式：`<type>(Web): <描述>`（如 `feat(Web): 新增学校管理页面`、`fix(Web): 修复登录 token 过期问题`），使用中文描述变更内容。
- **遵循现有代码模式**：新增页面和 API 模块必须遵循项目中已有的模式和结构，不要自行发明新风格。
- **使用 `<script setup>` 语法**：Vue 组件统一使用 Composition API + `<script setup>`。
- **UI 组件使用 Element Plus**：所有表单、表格、弹窗、按钮等 UI 元素统一使用 Element Plus 组件。

## 项目概述

SmartWash Web 是 SmartWash 智能洗衣管理系统的 Vue 3 后台管理端，功能包括：学校管理、学生管理、洗护套餐、寄存柜、订单、支付记录、充值记录、优惠券、角色管理和管理员用户管理。

## 常用命令

```sh
npm install        # 安装依赖
npm run dev        # 启动开发服务器（端口 5000，绑定 0.0.0.0）
npm run build      # 生产构建，输出到 dist/
npm run preview    # 预览生产构建
```

## 技术架构

**技术栈**: Vue 3（Composition API，`<script setup>`）、Vite 6、Element Plus、Pinia、Vue Router、Axios、Day.js。

**`@` 别名**映射到 `src/`（在 `vite.config.js` 和 `jsconfig.json` 中配置）。

### 分层结构

1. **入口** — `src/main.js`：创建 Vue 应用，注册 Element Plus、Pinia、Router，并将 `$dayjs` 暴露为全局属性。
2. **HTTP 层** — `src/utils/http.js`：Axios 实例，baseURL 为 `http://8.148.70.81:9000`。请求拦截器从 `localStorage` 获取 token 并添加 `Bearer` 头。响应拦截器解包 `response.data`，当 `res.code !== 200` 时视为错误，401 时清除 token 并刷新页面。
3. **API 层** — `src/api/*.js`：每个模块导入 `http` 并导出调用后端接口的函数。大部分函数在 `res.code === 200` 时返回 `res.data`，失败时抛出 `Error(res.message)`。
4. **状态管理** — `src/stores/`：Pinia store（目前仅有演示用的 `counter` store）。
5. **路由** — `src/router/index.js`：所有管理页面均为 `Layout` 组件的子路由（路径 `/`）。`/login` 是唯一 `requiresAuth: false` 的路由。`beforeEach` 守卫检查 `localStorage.getItem('token')`，未登录重定向到 `/login`。
6. **布局** — `src/components/Layout/Layout.vue`：Flex 布局，左侧 `Sidebar`（200px 深色菜单）+ 顶部 `Navbar`（面包屑和用户下拉菜单）+ `<router-view />`（主内容区）。
   - `Sidebar.vue`：动态构建菜单项，筛选 `meta.showInMenu === true` 的路由。使用 Element Plus `el-menu` + `router` 属性实现导航。
   - `Navbar.vue`：路由 meta 面包屑 + 用户头像下拉菜单（退出登录，调用 `@/api/adminUser` 的 `getCurrentAdminUser`）。
7. **页面** — `src/views/`：登录页 + `src/views/system/` 下的各管理模块 CRUD 页面。

### API 响应约定

所有后端响应格式为：`{ code: 200, message: "...", data: ... }`。HTTP 拦截器对 `code !== 200` 进行 reject。API 函数进一步解包，直接返回 `res.data`。

### CRUD 页面模式

每个管理页面（`SchoolList`、`UserList` 等）遵循相同结构：
- 响应式 `listQuery` 用于搜索和分页参数
- `el-table` 配合 `v-loading`，`el-pagination` 分页
- `el-dialog` 弹窗用于新增/编辑，内含 `el-form` 表单验证
- 标准方法：`fetchData`、`handleSearch`、`resetSearch`、`handleCreate`、`handleEdit`、`handleDelete`、`submitForm`
- `formatTime` 辅助方法使用 `dayjs` 格式化时间
- 从对应 `@/api/` 模块导入 API 函数

### 认证流程

登录页提交凭据 → 后端返回 token 字符串 → 存入 `localStorage` → 路由守卫检查 token 是否存在 → Axios 拦截器添加 Bearer 头。

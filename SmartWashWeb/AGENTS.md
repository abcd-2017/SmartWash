# AGENTS.md — SmartWashWeb 管理后台指南

> SmartWash 智能洗衣系统的 Vue 3 管理后台：学校管理、学生管理、洗护套餐、寄存柜、订单、支付记录、充值记录、优惠券、角色与管理员用户管理。
>
> 相关文档：编码 agent 硬规则见 [CLAUDE.md](CLAUDE.md)；子代理定义见 [.claude/agents/](.claude/agents/)；四端联动约定见仓库根目录 CLAUDE.md。

---

## 一、技术栈

| 维度 | 技术选型 |
|------|----------|
| 框架 | Vue 3.5（Composition API，`<script setup>`） |
| 构建 | Vite 6 + `@vitejs/plugin-vue` |
| UI | Element Plus 2.9（按需引入 via unplugin-auto-import/vue-components） |
| 状态 | Pinia 3（已注册，stores/auth.js 管理登录态） |
| 路由 | Vue Router 4（Layout 子路由模式） |
| HTTP | Axios 1.8 |
| 时间 | Day.js（`$dayjs` 全局属性） |
| 地图 | @amap/amap-jsapi-loader（高德） |
| 测试 | Vitest 4 + happy-dom + @vue/test-utils（已配置，含 test/lint script） |
| 语言 | JavaScript（jsconfig.json 仅配 `@` → `src/` 别名） |

---

## 二、目录结构

```
src/
├── main.js                 # 入口：注册 Element Plus、Pinia、Router、$dayjs
├── App.vue
├── api/                    # API 层：13 个领域模块，统一 request({url,method,params})
│   ├── adminUser.js  auth.js  coupon.js  dashboard.js  divination.js  laundry.js  locker.js
│   ├── order.js  payment.js  recharge.js  role.js  school.js  user.js  userCoupon.js
├── utils/
│   └── http.js             # Axios 实例：Bearer 注入、响应解包、401 处理
├── stores/                 # Pinia（当前空目录，待建 useAuthStore）
├── router/index.js         # 全部管理页为 Layout 子路由；/login 免认证
├── components/Layout/      # Layout.vue + Sidebar.vue + Navbar.vue
├── views/
│   ├── LoginPage.vue       # 登录
│   ├── Home.vue            # Dashboard 首页
│   ├── NotFound.vue        # 404 兜底页
│   ├── system/             # 11 个管理模块 CRUD 页面（UserList、OrderList、SchoolList、
│   │                       #   LockerList、RechargeList、PaymentList、AdminUserList 等）
│   └── divination/         # 7 个观象台管理页面（DivPromptList/DivRagList/DivAuditList/
│                           #   DivUsage/DivBlockedList/DivModelList/DivSettings）
└── __tests__/http.test.js  # 唯一测试（断言已过时，跑必挂）
```

---

## 三、HTTP 层与 API 约定

- **请求**：拦截器从 Pinia `useAuthStore` 取 token 加 `Bearer` 头；生产 baseURL 由构建时环境变量 `SMART_WASH_BASE_URL` 注入（不再写死 IP），本地开发走 Vite 代理 `/api` → `127.0.0.1:8080`。
- **响应信封**：后端统一 `{ code: 200, message, data }`；拦截器对 `code !== 200` reject，`code/data` 成功时解包返回 `res.data`（分页接口即 `{ records, total }`）。
- **API 函数**：`code === 200` 返回 `res.data`，失败 `throw Error(res.message)`；页面用 try/catch 配合 ElMessage 提示。
- **401 处理**（待修项）：当前实现是 `window.location.reload()` 且业务码 401 与 HTTP 401 两处重复——新代码不要模仿，统一应为清 token + 跳 `/login` 并去重。

---

## 四、路由与权限

- 所有管理页面挂 `Layout`（`/`）下；`/login` 免认证；通配路由 `/:pathMatch(.*)*` 渲染 404 页。
- 菜单、面包屑、图标由路由 `meta` 驱动（`title`/`showInMenu`/icon），新增页面必须配齐 meta；图标经 `markRaw` 包裹。
- **权限现状（待修项）**：守卫从 `useAuthStore` 检查 token/角色，角色由后端登录接口返回（中文展示名），具体接口权限由后端 `/admin/**` 的 ROLE_ADMIN 强校验兜底。权限必须以后端接口鉴权为准，前端只做展示控制。
- 路由已全部懒加载（`component: () => import(...)`）；新增页面保持此模式。

---

## 五、CRUD 页面模式

每个管理页面遵循统一结构（参考 `src/views/system/UserList.vue`）：

1. 响应式 `listQuery`（搜索 + 分页参数）
2. `el-table` + `v-loading`，`el-pagination` 分页
3. `el-dialog` 弹窗承载新增/编辑，内含 `el-form` 校验（手机号正则、密码长度等写法见 UserList）
4. 标准方法：`fetchData`、`handleSearch`、`resetSearch`、`handleCreate`、`handleEdit`、`handleDelete`、`submitForm`
5. `formatTime` 用 dayjs；枚举状态经映射函数渲染文本/标签

**禁止复制粘贴**：这套结构在 11 个页面逐字重复（含 formatTime、时间范围 computed）。新增页面前先抽/复用 composable（`useTableList`、`useTimeRange`），枚举映射进 `src/constants/`，下拉数据不要 `size:1000` 拉全量。

---

## 六、编码规范

- 所有对话、注释、提交信息使用中文；提交格式 `<type>(Web): <描述>`，一个 commit 一个完整功能点。
- 组件一律 `<script setup>`；UI 一律 Element Plus；禁用 `v-html`。
- 全局状态放 `src/stores/`（Pinia），不要新增裸 localStorage 读写（现有 7 处待收敛）。
- 密钥只走 `import.meta.env`（注意：`VITE_AMAP_KEY` 当前未在任何 env 文件定义，地图组件会失效）；禁止新增硬编码密钥/明文 HTTP 地址。

---

## 七、构建与运行

```sh
npm install     # 安装依赖
npm run dev     # 开发服务器（端口 5000，绑定 0.0.0.0）
npm run build   # 生产构建 → dist/
npm run preview # 预览生产构建
```

- 测试：`npm test`（`vitest run`）可运行，运行前先修复 `src/__tests__/http.test.js` 的过时断言（baseURL、响应解包）。
- 已配置 ESLint（扁平配置 + `eslint-plugin-vue`）与 Prettier，`npm run lint`/`npm run lint:fix` 可用。

---

## 八、接口变更联动

改任何接口，必须同步检查后端 `controller/background/` 与根目录 CLAUDE.md 四端联动检查表。

---

## 九、已知问题

完整评审清单（含行号）见 `/Users/admin/code/Android/SmartWash/docs/code-review-2026-08-28.md` 第三章。P0 级：

- 高德 securityJsCode 已迁移至 `.env.*`（旧值已泄露待轮换）；`VITE_AMAP_KEY` 已声明但未配置（地图功能暂不可用）
- 登录态统一由 `useAuthStore` 管理（`stores/auth.js`），角色由后端登录接口返回；权限以后端 `/admin/**` ROLE_ADMIN 强校验兜底（`router/index.js`）
- 生产 API 地址已改为构建时 `SMART_WASH_BASE_URL` 环境变量注入（不再写死 IP）
- 测试断言与实现脱节（`http.test.js`），已含 test/lint script

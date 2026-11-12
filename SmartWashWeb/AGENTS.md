# AGENTS.md — SmartWashWeb 管理后台指南

> SmartWash 智能洗衣系统的 Vue 3 管理后台。洗衣主航道：学校、学生、洗护套餐、寄存柜、订单、支付/充值记录、优惠券、角色与管理员账号。平台模块运营台：站内信、订阅、操作审计、游戏库、工作台入口、跳转路由、短链管理；另有观象台管理（Prompt/语料/审计/用量/拦截/模型/设置）与设备投递公开页。
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
├── api/                    # API 层：24 个领域模块，统一 request({url,method,params})
│   ├── adminUser.js  auth.js  captcha.js  coupon.js  dashboard.js  divination.js
│   │   laundry.js  locker.js  order.js  payment.js  recharge.js  role.js  school.js
│   │   user.js  userCoupon.js  game.js  toolbox.js  workbench.js  deeplink.js
│   │   notice.js  subscription.js  audit.js  delivery.js
├── utils/
│   ├── http.js             # http（管理端）+ envelopeHttp（公开信封）：Bearer 注入、响应解包、401 单飞续签
│   ├── format.js           # formatTime/formatAmount/formatThousands/formatFileSize
│   ├── download.js         # saveBlobAs
│   ├── logger.js           # 分级日志（生产门控 + 手机号打码）
│   └── optionCache.js      # 下拉全量缓存（school/laundry/role）
├── stores/auth.js          # Pinia useAuthStore（token/refreshToken/role）
├── router/index.js         # 管理页为 Layout 子路由；/login 与 /d/:code? 免认证
├── constants/dict.js       # 66 个枚举/字典导出（订单/支付/充值/券/短码/观象台/工作台/游戏/深链/订阅/站内信）
├── components/             # Layout/（Layout+Sidebar+Navbar）、AmapPicker、RegionCascader、TrendChart
├── composables/            # useTableList、useTimeRange、useConfirm、useAnimatedNumber
├── views/
│   ├── LoginPage.vue  Home.vue  NotFound.vue
│   ├── system/             # 15 个管理页（UserList、OrderList、SchoolList、LockerList、
│   │                       #   RechargeList、PaymentList、AdminUserList、AuditLogList、
│   │                       #   NoticeList、SubscriptionList、CaptchaQuery 等）
│   ├── divination/         # 7 个观象台管理页
│   ├── game/               # 游戏库（GameList + BatchUploadDialog + gameDirectory.js）
│   ├── toolbox/            # 短链管理
│   ├── workbench/          # 工作台入口 + 跳转路由字典
│   └── delivery/           # 设备投递公开对端页（DeliveryPickup + DeliverySession）
└── __tests__/              # 3 个生效用例 + 2 个 .bak 禁用用例
```

---

## 三、HTTP 层与 API 约定

- **请求**：拦截器从 Pinia `useAuthStore` 取 token 加 `Bearer` 头；生产 baseURL 由构建时环境变量 `SMART_WASH_BASE_URL` 注入（不再写死 IP），本地开发走 Vite 代理 `/api` → `127.0.0.1:8080`。
- **响应信封**：后端统一 `{ code: 200, message, data }`；`http` 拦截器对 `code !== 200` reject，`code === 200` 时解包返回 `res.data`（分页接口即 `{ records, total }`）。`envelopeHttp` 保留完整信封（公开投递端用）。
- **API 函数**：`code === 200` 返回 `res.data`，失败 `throw Error(res.message)`；页面用 try/catch 配合 ElMessage 提示。
- **401 处理**：已统一为单飞续签（`/auth/refresh` 换双 token → 落盘 → 重试一次），失败清态跳 `/login?redirect=`，1.5s 去重窗。不要再写 `window.location.reload()` 或第二套清态逻辑。

---

## 四、路由与权限

- 所有管理页面挂 `Layout`（`/`）下；`/login` 与 `/d/:code?`（设备投递公开页）免认证；通配路由 `/:pathMatch(.*)*` 渲染 404 页。共 30 条路由。
- 菜单、面包屑、图标由路由 `meta` 驱动（`title`/`showInMenu`/icon），新增页面必须配齐 meta；侧边栏按 `MENU_GROUP_CONFIG` 分组（业务管理/基础数据/用户与权限/营销/观象台/工具箱/工作台/订阅/系统与安全/消息中心）。
- **权限现状**：守卫从 `useAuthStore` 检查 token/角色，角色由后端登录接口返回（中文展示名），具体接口权限由后端 `/admin/**` 的 ROLE_ADMIN 强校验兜底。权限必须以后端接口鉴权为准，前端只做展示控制。
- 路由已全部懒加载（`component: () => import(...)`）；新增页面保持此模式。

---

## 五、CRUD 页面模式

每个管理页面遵循统一结构（参考 `src/views/system/UserList.vue`）：

1. 响应式 `listQuery`（搜索 + 分页参数）
2. `el-table` + `v-loading`，`el-pagination` 分页
3. `el-dialog` 弹窗承载新增/编辑，内含 `el-form` 校验（手机号正则、密码长度等写法见 UserList）
4. 标准方法：`fetchData`、`handleSearch`、`resetSearch`、`handleCreate`、`handleEdit`、`handleDelete`、`submitForm`
5. `formatTime` 用 dayjs；枚举状态经映射函数渲染文本/标签

**禁止复制粘贴**：`useTableList`（列表/分页/搜索/竞态守卫）与 `useTimeRange` 已抽到 `src/composables/`，新列表页必须复用；枚举映射统一进 `src/constants/dict.js`。下拉全量缓存走 `optionCache.js`，不要在页面里 `size:1000` 拉全量。

---

## 六、编码规范

- 所有对话、注释、提交信息使用中文；提交格式 `<type>(Web): <描述>`，一个 commit 一个完整功能点。
- 组件一律 `<script setup>`；UI 一律 Element Plus；禁用 `v-html`。
- 全局状态放 `src/stores/`（Pinia），不要新增裸 localStorage 读写（现有 7 处待收敛）。
- 密钥只走 `import.meta.env`（`VITE_AMAP_KEY` 当前为空、`VITE_AMAP_SECURITY_CODE` 为已泄露旧值，地图功能待配置/轮换）；禁止新增硬编码密钥/明文 HTTP 地址。

---

## 七、构建与运行

```sh
npm install     # 安装依赖
npm run dev     # 开发服务器（端口 5000，绑定 0.0.0.0）
npm run build   # 生产构建 → dist/
npm run preview # 预览生产构建
```

- 测试：`npm test`（`vitest run`）可运行，当前生效 3 个用例（http/LockerList/toolbox）；2 个审计用例以 `.bak-feat-audit-log` 后缀禁用。
- 已配置 ESLint（扁平配置 + `eslint-plugin-vue`）与 Prettier，`npm run lint`/`npm run lint:fix` 可用。

---

## 八、接口变更联动

改任何接口，必须同步检查后端 `controller/background/` 与根目录 CLAUDE.md 四端联动检查表。

---

## 九、已知问题

完整评审清单（含行号）见 `docs/code-review-2026-08-28.md` 第三章。当前状态：

- 高德 `VITE_AMAP_SECURITY_CODE` 旧值已泄露待轮换；`VITE_AMAP_KEY` 为空，地图选点/行政区划暂不可用。
- 登录态统一由 `useAuthStore`（`stores/auth.js`）管理，双 token + 单飞续签已接入；权限以后端 `/admin/**` ROLE_ADMIN 强校验兜底。
- 生产 API 地址走构建时 `SMART_WASH_BASE_URL`；本地走 Vite 代理 `/api`。
- 401 已从 `window.location.reload()` 收敛为单飞续签 + 清态跳登录（原待修项已解决）。
- 测试：3 个生效用例可跑；审计用例禁用中。

# SmartWashWeb 管理后台

SmartWash 校园智能洗衣平台的 Vue 3 管理后台，与后端 `SmartWash/`（Spring Boot）配套，消费 `/admin/**` 管理端接口。

## 功能范围

- **洗衣主航道**：学校、学生、洗护套餐、寄存柜、订单、支付记录、充值记录、优惠券、用户优惠券、角色、管理员账号、验证码查询。
- **平台模块运营台**：站内信、订阅管理、心情报表、操作审计、游戏库、工作台入口、跳转路由字典、短链管理。
- **观象台**：Prompt / 语料 / 审计复审 / 用量看板 / 拦截日志 / 模型目录 / 平台设置。
- **公开页**：设备投递对端页（`/d/:code?`，免登录）。

## 技术栈

Vue 3.5（`<script setup>`）· Vite 6 · Element Plus 2.9 · Pinia 3 · Vue Router 4 · Axios 1.8 · Day.js · ECharts 6。

## 快速开始

```bash
npm install
npm run dev        # 开发服务器，端口 5000，代理 /api → 127.0.0.1:8080
npm run build      # 生产构建 → dist/
npm run preview    # 预览生产构建
npm test           # vitest run
npm run lint       # ESLint
npm run lint:fix
```

## 环境变量

| 变量 | 说明 |
|------|------|
| `SMART_WASH_BASE_URL` | 生产 API 地址，构建时经 Vite `define` 注入（未设置为空串，需 Nginx 反代或显式传入） |
| `VITE_BASE_URL` | 开发基址，默认 `/api`（走 Vite 代理） |
| `VITE_AMAP_KEY` | 高德地图 key（当前为空，需配置后地图选点/行政区划才可用） |
| `VITE_AMAP_SECURITY_CODE` | 高德安全密钥（旧值已泄露，待轮换） |

## 目录结构

```
src/
├── api/            # 24 个领域 API 模块，统一 request({url, method, params})
├── assets/         # 全局样式与 logo
├── components/     # Layout（Sidebar/Navbar）、AmapPicker、RegionCascader、TrendChart
├── composables/    # useTableList / useTimeRange / useConfirm / useAnimatedNumber
├── constants/      # dict.js 枚举字典（68 个导出）
├── router/         # 路由表（31 条，管理页挂 Layout，/login 与 /d/:code? 免认证）
├── stores/         # Pinia useAuthStore（token/refreshToken/role）
├── utils/          # http.js（双实例 + 单飞续签）、format、download、logger、optionCache
├── views/          # system / divination / game / toolbox / workbench / delivery + 登录/首页/404
└── __tests__/      # vitest 用例
```

## 约定

- 所有对话、注释、提交信息使用中文；提交格式 `<type>(Web): <描述>`。
- 组件一律 `<script setup>`，UI 一律 Element Plus，禁用 `v-html`。
- 接口权限以后端 `/admin/**` 的 ROLE_ADMIN 强校验为准，前端只做展示层控制。
- 401 统一走 `utils/http.js` 的单飞续签，不要在页面里另写清态逻辑。

各端联动与编码硬规则见 [CLAUDE.md](CLAUDE.md) 与 [AGENTS.md](AGENTS.md)。

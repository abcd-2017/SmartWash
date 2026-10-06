# Web 端已知坑

完整清单见 [.claude/docs/code-review-2026-08-28.md](../../.claude/docs/code-review-2026-08-28.md) 第三章，重点关注：

- 高德 securityJsCode 已从 `index.html` 内联迁移至 `.env.*` 的 `VITE_AMAP_SECURITY_CODE`（旧值 `cc4d5ecf...` 已随 git 入库视为泄露，待轮换）；`VITE_AMAP_KEY` 已声明但当前为空——地图功能暂不可用，需到高德控制台创建/轮换后填入。
- 下拉选项多处用 `size:1000` 拉全量（`UserList.vue` 等），数据量大即卡——新增下拉优先做专用接口或全局缓存。
- 已配置 ESLint（`eslint.config` 扁平配置 + `eslint-plugin-vue`）与 Prettier，`package.json` 含 `lint`/`lint:fix` script；`RechargeList.vue` 有整块注释死代码待清理。
- 404 页面已存在（`NotFound.vue` + 通配路由 `/:pathMatch(.*)*`）。

# Web 端已知坑

完整清单见 [docs/code-review-2026-08-28.md](../../docs/code-review-2026-08-28.md) 第三章，重点关注：

- 高德 securityJsCode 在 `.env.*` 的 `VITE_AMAP_SECURITY_CODE`（旧值 `cc4d5ecf...` 已随 git 入库视为泄露，待轮换）；`VITE_AMAP_KEY` 当前为空——地图选点/行政区划暂不可用，需到高德控制台创建/轮换后填入。
- 下拉全量数据统一走 `src/utils/optionCache.js`（school/laundry/role，模块级 Promise 缓存）；新增下拉优先复用，不要在页面里 `size:1000` 拉全量。
- 401 已收敛为 `utils/http.js` 的单飞续签（双 token → 落盘 → 重试一次 → 失败清态跳 `/login?redirect=`），不要再写 `window.location.reload()` 或第二套清态逻辑。
- 设备投递公开页（`/d/:code?`）走 `envelopeHttp`，业务码 200/403/404/409/413 由页面分支处理，不要按管理端 `code !== 200` 的惯例直接 reject。
- 已配置 ESLint（`eslint.config` 扁平配置 + `eslint-plugin-vue`）与 Prettier，`package.json` 含 `lint`/`lint:fix` script；`RechargeList.vue` 有整块注释死代码待清理。
- 404 页面已存在（`NotFound.vue` + 通配路由 `/:pathMatch(.*)*`）。
- 测试：3 个生效用例（http/LockerList/toolbox）；2 个审计用例以 `.bak-feat-audit-log` 后缀禁用，不要误以为测试覆盖完整。

# 点石成金（gameKey：`stock-tap`，category 建议：`other`）

- 玩法：60 个交易日的股价折线模拟仓，本金 1000 全仓进出，低买高卖吃波段；波动随交易日六阶段递增，后期偶发跳空，收盘总资产即本局成绩。
- 操作：触屏点「买入（全仓）／卖出（清仓）／暂停／开市」，图上有 ▲买 ▼卖 标记；键盘 B 买入、S 卖出、空格暂停/继续、R 重新开市。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）。

## 分数与上报口径

- 分数驱动款，`desc`（越大越好）：每局收盘 `GB.reportScore("stock-tap",{metricValue:历史最佳资产, metricExtra:本局资产})`，`metricValue` 上报**历史最佳总资产**（本金 1000，全仓进出无手续费）。
- 存档键 `sw.stocktap.v1`（best / runs），localStorage + 账号双写，账号更优才覆盖。
- 计时说明：行情推进为 rAF dt 累积制（0.6s 一跳），切后台 visibilitychange 停帧自动冻结，恢复续跑且 dt 钳制 0.05，无 Date.now 到期基点，无平移需求；无延迟回调，无代际竞态。

## 验证（锚点用例）

核心 `ST`（`/*XC-CORE-BEGIN*/` 段）可被 node 抽取独立复现，`window.__validateLevels()` 共 7 条：

- 锚点·低买高卖：序列 [100,120]，d0 买 d1 卖 → final = 1200（精确）。
- 锚点·满仓腰斩：序列 [100,50]，d0 买持有 → final = 500（精确）。
- 锚点·空仓保值：无操作 → final = 1000 且 peak = 1000。
- 锚点·持股复利：[100,110,121] d0 买 d2 卖 → final = 1210（精确）。
- genSeries：长度 60、价格下限 ≥8、同种子逐字节一致（确定性回放）。

实测：7/7 通过（`node` 抽取 XC-CORE 复现）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 `--g-*` token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作；按钮热区 ≥44px
- [x] 切后台停帧/续跑（dt 钳制）；深浅色双主题 canvas 重绘
- [x] 存档键 `sw.stocktap.v1`；无桥自动降级 localStorage
- [x] audit-gates 通过（0 违规）；theme-gates 全绿

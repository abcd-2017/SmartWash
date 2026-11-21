# 称重排序（gameKey：`weight-order`，category 建议：`puzzle`）

- 玩法：N 枚隐藏重量的砝码（互异），用天平做有限次两两比较并留下记录，把砝码从轻到重排进排序槽提交；比较预算恰好等于二分插入排序的最坏比较数，用错策略就不够用，提交错 2 次失败。
- 操作：触屏点两枚砝码上天平比较（比较模式），点「排序模式」后从轻到重点砝码入槽、点已填槽可撤销，提交排序；键盘数字键选砝码、空格切换模式、回车提交；切后台自动暂停。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；40 关内嵌（3 枚→6 枚砝码递进，预算 3/5/8/11 次），过关上报到达关卡（desc，metricExtra 为累计星数）；存档 `{lv, done:{关卡:星}, best}` 本地 + 账号双写；无 WebAudio。

## 关卡验证

- 关卡结构 `{name, no, seed, N, weights[], budget}`，确定性种子生成（砝码值为互异整数，隐藏不下发 UI）。
- 验证方式：内嵌**独立验证器** `GEN.validate`：重量两两互异；`budget` 必须恰等于二分插入最坏比较数 Σ⌈log₂(m+1)⌉（N=3/4/5/6 → 3/5/8/11，可解性上界证明）且不低于信息论下界 ⌈log₂(N!)⌉（3/5/7/10，预算合理性证明）；`GEN.validateLevelsAll` 逐关复核，页面暴露 `window.__validateLevels` 可复跑。
- 验证结果：40/40 关通过（无 fails）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，天平 canvas 自绘
- [x] 颜色全部走 `--g-*` / `--g-st-*` token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅双主题 canvas 重绘
- [x] 存档键 `sw.weightorder.v1`（+ `.levels` / `.levels.ver`）；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；40 关独立验证 40/40 通过；theme-gates / audit-gates 全绿

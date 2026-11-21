# 移靶射箭（gameKey：`archery-move`，category 建议：`arcade`）

- 玩法：5 支箭限时不定、总分判定：靶在竖直方向正弦摆动（摆幅 90→150、靶速 1.1→2.0 递进），每箭风向随机（顺风飞得快落点偏高 / 逆风飞得慢掉得更多，30→160 加速度档）；按住蓄力决定弹道高度，闭式弹道解算落点；环分 10/8/6/4，总分 ≥ 目标过关。
- 操作：触屏按住画布蓄力、松手放箭；键盘空格按下蓄力、松开放箭；「重射 / 选关」工具按钮；切后台自动暂停。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；40 关内嵌（gen 内用独立求解器定标目标分），过关上报到达关卡（desc，metricExtra 为累计星数）；存档 `{lv, done:{关卡:星}, best}` 本地 + 账号双写；与 pitch-pot（固定壶阵）、slingshot（皮筋弹射）不同：本款是摆动靶 + 风场弹道修正；WebAudio 未接入。

## 关卡验证

- 关卡结构 `{name, no, seed, range, om, ph, winds[5], need}`，确定性种子生成。
- 验证方式：内嵌**独立求解器** `GEN.arrowMax`（蓄力 0.4→1.0 步进 0.04 × 出手时刻覆盖 2.6 个摆动周期步进 30ms，弹道闭式解 + 靶面相遇点逐解复核）：要求每支箭在其风场下均存在 ≥8 环解，且目标分 `need ≤ Σ可达环分×70%`；`GEN.validateLevelsAll` 逐关复核，页面暴露 `window.__validateLevels` 可复跑。
- 验证结果：40/40 关通过（无 fails）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，靶场 canvas 自绘
- [x] 颜色全部走 `--g-*` / `--g-st-*` token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅双主题 canvas 重绘
- [x] 存档键 `sw.archerymove.v1`（+ `.levels` / `.levels.ver`）；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；40 关独立验证 40/40 通过；theme-gates / audit-gates 全绿

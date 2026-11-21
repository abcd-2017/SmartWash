# 回转滑雪（gameKey：`ski-slalom`，category 建议：`arcade`）

- 玩法：竖屏回转竞速计时赛，滑行速度随下行距离自增；左右切进旗门（红蓝交替），漏门 +2s 罚时，撞树打滑减速 0.85s，冲出雪道边界会被弹回；用时 ≤ 目标时间过关，按裕量评星。
- 操作：触屏按住画布左右拖动控制切弯方向；键盘 ← → / A D 转向、空格开始 / 重滑；「重滑 / 选关」工具按钮；切后台自动暂停。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；40 关内嵌（旗门 10→32 组、门距 240→170、横摆幅度 70→140、树 2→10 棵递进），过关上报到达关卡（desc，metricExtra 为累计星数）；存档 `{lv, done:{关卡:星}, best}` 本地 + 账号双写；与 snow-trail（自由滑行）不同：本款是旗门路线 + 计时判定的竞速。

## 关卡验证

- 关卡结构 `{name, no, seed, len, gates[{x,y}], trees[{x,y}], par, nGates}`，确定性种子生成（gen 自带自愈重试）。
- 验证方式：内嵌**独立求解器** `GEN.solve`（P 控制器切弯 + 前瞻树回避，与游戏共用同一组物理参数、独立路径策略）：全程模拟要求零漏门、零撞树、完赛，且 `par ≥ 求解器用时×1.4 + 1.9s`（人类裕量 ≥40%）；`GEN.validateLevelsAll` 逐关复核，页面暴露 `window.__validateLevels` 可复跑。
- 验证结果：40/40 关通过（无 fails）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，雪道 canvas 自绘
- [x] 颜色全部走 `--g-*` / `--g-st-*` token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅双主题 canvas 重绘
- [x] 存档键 `sw.skislalom.v1`（+ `.levels` / `.levels.ver`）；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；40 关独立验证 40/40 通过；theme-gates / audit-gates 全绿

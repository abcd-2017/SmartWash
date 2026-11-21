# 冰壶擦冰（gameKey：`curling-sweep`，category 建议：`arcade`）

- 玩法：三阶段操作：方向摆针（±10°）锁定 → 力度摆条锁定 → 滑行中连点擦冰（每次 0.25s、耗 1 体力，减摩擦=滑更远+压直弧线）；壶带固定旋向走弧线，可与障碍壶碰撞互推；停壶后按距圆心评分（10/7/5/3/2/0），≥ 目标分过关；障碍壶 0→3 只、擦冰体力 14→8 递进。
- 操作：触屏点画布 / 键盘空格或回车完成锁定与擦冰（每阶段一按）；「重掷 / 选关」工具按钮；切后台自动暂停。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；40 关内嵌（确定性种子 + 求解器定标），过关上报到达关卡（desc，metricExtra 为累计星数）；存档 `{lv, done:{关卡:星}, best}` 本地 + 账号双写；擦冰摩擦控制 + 弧线旋度 + 等质量碰撞物理为合集独有；WebAudio 未接入。

## 关卡验证

- 关卡结构 `{name, no, seed, guards[], stamina, curl, target, best}`，确定性种子生成。
- 验证方式：内嵌**独立求解器** `GEN.solveBest`（角度 ±10°×力度 10 档×3 种擦冰节奏，与游戏共用同一 `physWorld` 物理步函数、独立搜索策略）：可达最高分必须 ≥5，且 `target` 必须恰为可达分的下一评分档（留一档人类容差）；`GEN.validate` 复核 + `GEN.validateLevelsAll` 逐关复核，页面暴露 `window.__validateLevels` 可复跑。
- 验证结果：40/40 关通过（无 fails），全量求解 0.8s。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，冰道 canvas 自绘
- [x] 颜色全部走 `--g-*` / `--g-st-*` token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅双主题 canvas 重绘
- [x] 存档键 `sw.curlingsweep.v1`（+ `.levels` / `.levels.ver`）；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；40 关独立验证 40/40 通过；theme-gates / audit-gates 全绿

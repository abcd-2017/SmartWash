# 蜜蜂采蜜（gameKey：`bee-lines`，category 建议：`arcade`）

- 玩法：草场上散布花丛（1-3 蜜/朵），蜂群载重与体力双约束，规划一条出巢采蜜再回巢的航线，一次带回归定的目标蜜量即过关；航线越顺越省体力。
- 操作：触屏点花朵加入/移出航线（航线上有顺序角标），点「起飞」看蜜蜂沿航线飞行采蜜；键盘 1-8 选中/取消花朵、Enter 起飞、R 清空航线、Backspace 撤销末朵。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；40 关内嵌，目标蜜量=离线最优解（Held-Karp 位掩码 DP），必须规划出最优路线才能过关。

## 关卡验证

- 内嵌 40 关，每关结构 `{name,hive,flowers[[x,y,n]],cap,energy,target,par,difficulty}`；`cap` 载重、`energy` 体力（环路距离上限）、`target` 目标蜜量。
- 验证方式：游戏内嵌**独立求解器** `BL.maxCollect`（Held-Karp DP：`dp[mask][i]` = 从巢出发访问 mask 集合停在 i 的最短航程，载重剪枝），对每关离线计算体力约束下的最大可采蜜量，`target` 直接取该最优值并复核（`BL.validate`），不可解关卡不进表；生成器带确定性重试（mulberry32 固定种子 + 尝试序号）。
- 锚点用例（`window.__validateLevels()` 共 7 条，node 抽取 XC-CORE 复现）：三花固定场 → 载重约束最优 5 且环路 120（精确）、体力 119 时最优降为 3（精确）、载重 2 时最优 2（精确）、航线距离 120（精确）、第 1 关可解且达标、40 关全可解、生成逐字节确定。
- 生成与验证结果：40/40 关可解（fail 空），且每关 `target` 均等于求解器最优（满规划挑战）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 `--g-*` / `--g-st-*` token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作；按钮热区 ≥44px
- [x] 飞行为纯动画推进（结果起飞前已由确定性 DP 判定），rAF 循环 visibilitychange 停帧/续跑（dt 钳制 0.05），无 Date.now 到期基点，无延迟回调竞态
- [x] 存档键 `sw.beelines.v1`（续玩从 `save.lv` 起步）+ `.levels`/`.ver` 缓存；无桥自动降级 localStorage
- [x] 关卡驱动：40 关内嵌 + 独立求解器（含锚点用例）；audit-gates 通过（0 违规）；theme-gates 全绿

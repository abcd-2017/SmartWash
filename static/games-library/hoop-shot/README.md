# 街机投篮（gameKey：`hoop-shot`，category 建议：`arcade`）

- 玩法：60 秒限时连投，篮筐左右游走（分数越高摆幅与速度越大，40 分后还会上下晃）；出手方式=拖动甩出方向与力度；进球从上而下穿筐面判定，空心 +3 / 擦筐 +2，连续命中提升热度倍数（x2/x3/x4），打铁清零热度；进球或打铁后 0.35s 快速补球。
- 操作：触屏按住画布拖出方向力度、松手出手（带弹道虚线预览）；键盘 ←→ 调角度、↑↓ 调力度、空格出手；「开始 / 暂停 / 继续 / 再来一局」同一按钮切换；切后台自动暂停。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；最高分 localStorage + 账号双写（裸最高分，snake 先例），结束上报最高分（desc）；与 penalty（回合制点球）、pitch-pot（投壶）不同：本款是移动筐 + 热度连击的街机快速投篮；WebAudio 未接入。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，球场 canvas 自绘
- [x] 颜色全部走 `--g-*` / `--g-st-*` token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅双主题 canvas 重绘
- [x] 存档键 `sw.hoopshot.v1`；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；theme-gates / audit-gates 全绿

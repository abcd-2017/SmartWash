# 塔吊堆楼（gameKey：`tower-crane`，category 建议：`other`）

- 玩法：塔吊吊块左右摆动，看准时机放块往基座上堆楼；层数越高摆得越快、块越窄，重心偏出基座会倒塌。
- 操作：触屏点画布放块；键盘空格放块、P 暂停。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；分数驱动街机，结束上报历史最高层数（`GB.reportScore(GAME_KEY,{metricValue:best})`，desc，越大越好）。

## 上报口径

- `metricValue` = 本轮历史最高堆楼层`数`（localStorage `sw.towercrane.v1` + 账号双写，账号优先）。
- 结束时上报一次；无关卡概念，不使用 `metricExtra`。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片
- [x] 单文件 ≤10MB；颜色全部走 `--g-*` / `--g-st-cr-*` token，深浅双主题由桥推送
- [x] 触屏 + 键盘双操作；含暂停/继续
- [x] 分数驱动：最高层数上报（desc）
- [x] 摆动为确定性正弦模型，内嵌引擎 `TC.selfTest()` 3 项锚点（摆顶连放不塌且重心偏移在物理期望区 / 高速相位连放必出事 / 落块计层）全 PASS
- [x] rAF 循环 visibilitychange 停续并自动暂停，dt 钳制防大步长穿透
- [x] audit-gates 通过（0 违规）；theme-gates 全绿

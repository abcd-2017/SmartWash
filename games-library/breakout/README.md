# 打砖块（gameKey：`breakout`，category 建议：`arcade`）

- 玩法：球撞砖块得分（越靠上的砖分值越高），清空全部砖块进入下一关（砖更多、更硬），球落底扣 1 命共 3 命；最高分与最高关卡 localStorage 保存。
- 操作：手指/鼠标左右拖动控制挡板（游戏区 touch-action:none），点按屏幕发球；右上角「暂停」按钮可随时暂停。
- gameKey 建议：`breakout`；分类建议：`arcade`。

## 自查清单
- [x] 无外部请求：CSS/JS 全内联，图形全部 Canvas 绘制
- [x] 触屏可完整玩一局：暂停等按钮 ≥44px，挡板跟随 pointermove 拖动并捕获指针
- [x] localStorage 读写均包裹 try/catch（`breakout.best` / `breakout.bestLevel`）
- [x] 375px 宽度不溢出：竖屏优先，砖块布局随宽度自适应
- [x] 切后台自动暂停：document.hidden 触发暂停层，dt 封顶防跳帧，不会掉命

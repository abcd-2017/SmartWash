# 指尖小鸟（flappy）

- 玩法：点按屏幕让小鸟向上飞，穿过管道缝隙 +1 分，碰到管道或落到地面即结束；最高分本地保存。
- 操作：点按/触摸画面上升（桌面端空格键亦可）；开始、暂停、结束均为大按钮交互。
- gameKey 建议：`flappy`；分类建议：`arcade`。

## 自查清单
- [x] 无外部请求：CSS/JS 全内联，图形全部 Canvas 绘制，无 CDN/字体/图片
- [x] 触屏可完整玩一局：按钮均 ≥48px，游戏区 touch-action:none 防误触缩放
- [x] localStorage 读写均包裹 try/catch（最高分 `flappy.best`）
- [x] 375px 宽度不溢出：竖屏优先，flex 自适应，物理参数随画布高度等比缩放
- [x] 切后台自动暂停：document.hidden 触发暂停层，返回后点按继续，不会偷跑掉分

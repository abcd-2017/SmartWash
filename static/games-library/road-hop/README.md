# 过街小蛙（gameKey：`road-hop`，category 建议：`arcade`）

- 玩法：小蛙从河岸下沿逐格向上横穿 5 条车流，看准车流缺口起跳，安全岛上可等待；每跳一次车辆前进一拍，被撞即失败，跳到对岸过关。
- 操作：触屏点按/滑动控制上下左右，键盘方向键 / WASD 跳跃，空格开始或暂停；可点「选关」进入 40 关选择面板。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）。40 关由种子确定性生成，内置 `window.__validateLevels()` 对每关做 (x,y,t mod P) 状态 BFS 可通关性验证（40/40 可通）。存档键 `sw.roadhop.v1`（含 `sw.roadhop.v1.levels` 关卡缓存），无桥自动降级 localStorage，过关上报总积分（desc）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量（仅恒白眼睛例外）
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 深浅色双主题；暂停 / 重开 / 结束面板齐全
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval
- [x] 关卡 40 关内嵌，`window.__validateLevels()` 返回 `{total:40,solvable:40,fail:[]}`

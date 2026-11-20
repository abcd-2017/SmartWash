# 踩木过河（gameKey：`log-hop`，category 建议：`arcade`）

- 玩法：小蛙从近岸跳上顺流横漂的原木过河，原木站不住即落水；落点没木、被原木带出屏幕都算失败，每过一次河得分与难度同时提升。
- 操作：触屏点按/滑动控制上/下/左/右，键盘方向键 / WASD 跳跃与调整站位，空格开始或暂停。
- 说明：单文件自包含 H5，卡片式布局。分数驱动，最高分接 GB 桥上报（desc），无桥降级 localStorage（`sw.loghop.v1`）。原木质心、速度随机生成，难度随过河次数递增。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量（仅恒白眼睛例外）
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 深浅色双主题；暂停 / 重开 / 结束面板齐全
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval
- [x] 分数驱动，GB 桥最高分上报 + localStorage 兜底

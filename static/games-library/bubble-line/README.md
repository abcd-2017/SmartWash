# 泡泡连线（gameKey：`bubble-line`，category 建议：`arcade`）

- 玩法：同色气泡自底部上浮，按住滑过相邻同色气泡连成 3 个以上松手消除；任何一颗顶到水面线即失败，连锁越长分越高。
- 操作：触屏按住滑动连线、松手结算，键盘方向键移动选泡、空格连线/继续、回车结算、Backspace 撤销、Esc 取消；空格开始或暂停。
- 说明：单文件自包含 H5，卡片式布局。分数驱动，最高分接 GB 桥上报（desc），无桥降级 localStorage（`sw.bubble-line.v1`）。气泡生成与上浮速度随分数微增，纯 canvas 自绘。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量（仅恒白高光例外）
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 深浅色双主题；暂停 / 重开 / 结束面板齐全
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval
- [x] 分数驱动，GB 桥最高分上报 + localStorage 兜底

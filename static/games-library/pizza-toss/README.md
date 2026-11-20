# 飞饼加料（gameKey：`pizza-toss`，category 建议：`arcade`）

- 玩法：面饼在下方旋转，拖动移动接住自上掉落配料，接住全部所需配料过关；黑火药要避开，接到扣一条命，限时内未完成即失败。
- 操作：触屏按住拖动移动面饼，键盘 ←/→ 或 A/D 移动，空格开始或暂停；「选关」可进入 40 关面板。
- 说明：单文件自包含 H5，卡片式布局。40 关由种子确定性生成配料落点与时刻，内置 `window.__validateLevels()` 逐关验证可达性（面饼限速下相邻落点可追）、黑火药可避开与限时覆盖（40/40 可完成）。存档键 `sw.pizza-toss.v1`（含关卡缓存），过关上报总积分（desc）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 深浅色双主题；暂停 / 重开 / 结束面板齐全
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval
- [x] 关卡 40 关内嵌，`window.__validateLevels()` 返回 `{total:40,solvable:40,fail:[]}`

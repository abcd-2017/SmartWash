# 寿司转盘（gameKey：`sushi-grab`，category 建议：`other`）

- 玩法：转盘逆时针转动，点按寿司抓取；抓取品类与顶部未交付订单一致则 +1 分，否则 -1 分，寿司放太久过期回收并 -1 分；限时内达到目标分过关。
- 操作：触屏点按转盘上的寿司抓取，键盘空格抓取取餐区最近一盘，P 暂停；「选关」可进入 40 关面板。
- 说明：单文件自包含 H5，卡片式布局。40 关由种子确定性生成订单与盘面，内置 `window.__validateLevels()` 校验订单品类在盘面上数量充足、限时/新鲜度/转速/目标分合法（40/40 可通关）。存档键 `sw.sushi-grab.v1`（含关卡缓存），过关上报总积分（desc）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 深浅色双主题；暂停 / 重开 / 结束面板齐全
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval
- [x] 关卡 40 关内嵌，`window.__validateLevels()` 返回 `{total:40,solvable:40,fail:[]}`

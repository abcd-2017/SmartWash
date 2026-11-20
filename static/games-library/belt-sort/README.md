# 传送分拣（gameKey：`belt-sort`，category 建议：`arcade`）

- 玩法：垃圾沿传送带进入右侧投放区后点下方对应垃圾桶，投对得 1 件，投错扣 2 秒，漏过不计；限时内达到目标件数即过关，品类与节奏逐关递增。
- 操作：触屏点按下方分类桶，键盘数字键 1-5 选择类别，空格开始或暂停；「选关」可进入 40 关面板。
- 说明：单文件自包含 H5，卡片式布局。40 关参数内嵌（品类 3→5、垃圾 10→32 件、节奏加快），内置 `window.__validateLevels()` 校验类别/数量/时限合法性并确认「全对可达」（40/40）。存档键 `sw.belt-sort.v1`（含关卡缓存），过关上报总积分（desc）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 深浅色双主题；暂停 / 重开 / 结束面板齐全
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval
- [x] 关卡 40 关内嵌，`window.__validateLevels()` 返回 `{total:40,solvable:40,fail:[]}`

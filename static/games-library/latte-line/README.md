# 拉花一笔（gameKey：`latte-line`，category 建议：`other`）

- 玩法：按住画面沿浅色虚线描出拉花图案，限时内覆盖越全、偏离越小分越高；得分 ≥60 且覆盖率 ≥70% 过关，图案器型与容差逐关递进。
- 操作：触屏/鼠标按住描线；键盘方向键移动画笔、回车开始/落笔、空格开始或暂停；可点「完成」提前结算，时间到自动评分。
- 说明：单文件自包含 H5，卡片式布局。40 关由种子确定性生成 6 类器型（心形/叶片/罗塞塔/螺旋/波浪/郁金），内置 `window.__validateLevels()` 校验限时可描完（路径长 ≤ 520px/s×时间×0.8）与容差合法（40/40 可完成）。存档键 `sw.latte-line.v1`（含关卡缓存），过关上报总积分（desc）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏描线可完整游玩（键盘用于开始/暂停）；按钮热区 ≥44px
- [x] 深浅色双主题；暂停 / 重开 / 结束面板齐全
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval
- [x] 关卡 40 关内嵌，`window.__validateLevels()` 返回 `{total:40,solvable:40,fail:[]}`

# 岩壁攀岩（gameKey：`rock-climb`，category 建议：`arcade`）

- 玩法：左右手必须交替抓岩点向上攀爬，大握点省力、小捏点费劲、休息台回耐力；耐力槽持续消耗，耗尽脱力坠落，攀爬高度即分数，岩点难度随高度递增。
- 操作：触屏直接点岩点抓握，键盘方向键在岩点间移动选择环、回车 / 空格抓握；空格开始或暂停；「开始 / 暂停 / 继续 / 重新开始」同一按钮按状态切换。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；耐力消耗由双手所抓岩点类型均值决定（crimp 10 / jug 4 / rest −14），抓小捏点另有爆发消耗；耐力低于 30 攀岩者变红警示。高度 localStorage + 账号存档双写（裸最高分），结束上报最高分（desc）；WebAudio 合成音效。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅色双主题 canvas 重绘
- [x] 存档键 `sw.rockclimb.v1`；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval

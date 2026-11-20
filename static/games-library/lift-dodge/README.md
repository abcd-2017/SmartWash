# 扶梯闪避（gameKey：`lift-dodge`，category 建议：`arcade`）

- 玩法：在电梯井里沿双向扶梯向上攀爬，每拍人物与扶梯移动一次；上跳消耗力槽（力槽每拍恢复、休息平台加倍），撞到人流或跌出井底即失败，高度越大分值越高。
- 操作：触屏点按/滑动控制上/下/左/右，键盘方向键 / WASD 移动，空格开始或暂停。
- 说明：单文件自包含 H5，卡片式布局。分数驱动，最高分接 GB 桥上报（desc），无桥降级 localStorage（`sw.lift-dodge.v1`）。行向人流由关卡号哈希确定性生成，密度随高度递增。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量（仅恒白眼睛例外）
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 深浅色双主题；暂停 / 重开 / 结束面板齐全
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval
- [x] 分数驱动，GB 桥最高分上报 + localStorage 兜底

# 弹跳小绿（gameKey：`doodle-jump`，category 建议：`arcade`）

- 玩法：操控小绿在不断生成的平台上弹跳攀升，普通 / 易碎 / 移动 / 弹簧四种板 + 顶面黑刺板，左右穿墙环绕，高度即分数，踩黑刺或坠落即结束。
- 操作：触屏在画布按住左右拖动跟随，键盘方向键 / A D 平移，空格开始或暂停；「开始 / 暂停 / 继续 / 重新开始」由同一按钮按状态切换。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；难度随高度递增（板距变大、易碎 / 移动 / 黑刺占比上升）；最高分 localStorage + 账号存档双写（裸最高分，snake 先例），结束上报最高分进排行榜（desc）；WebAudio 合成音效，失败静默。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量（恒白眼白例外）
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅色双主题 canvas 重绘
- [x] 存档键 `sw.doodlejump.v1`；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval

# 吞噬进化（gameKey：`blob-eat`，category 建议：`arcade`）

- 玩法：单屏吞噬球局，操控小绿球以小吃大、避大；吃 AI 球与胶粒增大体型，体型达阈值进化三级（提速 + 外观变化），被更大 AI 球吞掉即结束，分数即得分。
- 操作：触屏按住拖动引导方向（松手滑行停止），键盘方向键 / WASD 八向移动，空格开始或暂停；「开始 / 暂停 / 继续 / 重新开始」同一按钮按状态切换。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；AI 球带追逐 / 逃逸 / 游荡三态策略，体型越大移速越慢；进化阈值 r≥26 / r≥40。最高分 + 进化等级 localStorage + 账号存档双写，结束上报最高分（desc，附进化等级）；WebAudio 合成音效。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量（恒白眼白例外）
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅色双主题 canvas 重绘
- [x] 存档键 `sw.blobeat.v1`；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval

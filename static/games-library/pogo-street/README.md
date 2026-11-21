# 弹簧街（gameKey：`pogo-street`，category 建议：`arcade`）

- 玩法：弹簧杆沿街道自动前卷，落地后贴地滑行并可按住蓄力，松手才起跳（蓄力越满弹得越高）；贴地越久越容易被垃圾桶 / 路锥 / 消防栓撞上，腾跃时收集金币弧，撞障即结束。
- 操作：触屏按住画布蓄力、松手起跳；键盘空格按下蓄力、松开起跳，← → / A D 空中左右微调；「开始 / 暂停 / 继续 / 重新开始」同一按钮切换；切后台自动暂停。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；难度随街程递增（卷速加快、障碍更高更密）；最高分 localStorage + 账号双写（裸最高分，snake 先例），结束上报最高分（desc）；分数 = 街程 m + 金币×10；与 doodle-jump（竖屏爬升）、jump-jump（跳台）机制不同：本款是「落地蓄力-松手释放」的节奏弹跳；WebAudio 未接入。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 `--g-*` / `--g-st-*` token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩（触屏纯蓄力节奏即可通关）；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅双主题 canvas 重绘
- [x] 存档键 `sw.pogostreet.v1`；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；theme-gates / audit-gates 全绿

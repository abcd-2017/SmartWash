# 激流皮划艇（gameKey：`canoe-rapids`，category 建议：`arcade`）

- 玩法：纵向顺流而下，左右划桨绕开礁石与原木，横向流带会周期性推拽船身（双向水流），漩涡会把船吸向中心（卷入即损船），浪带里顺流更快（浮力节奏）；轻点冲刺可爆发提速；船体 2 点，撞 / 吸耗尽即结束，里程即分数。
- 操作：触屏按住左右拖动划桨，快速轻点（<180ms 且未拖动）冲刺；键盘 ← → / A D 划桨、空格冲刺；「开始 / 暂停 / 继续 / 重新开始」同一按钮切换；切后台自动暂停。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；流速随里程自增（170→300 + 冲刺/浪带加成）；最高分 localStorage + 账号双写（裸最高分，snake 先例），结束上报最高分（desc）；纵向水流 + 浮力 + 横向流带玩法为合集独有；WebAudio 未接入。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，河道 canvas 自绘
- [x] 颜色全部走 `--g-*` / `--g-st-*` token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅双主题 canvas 重绘
- [x] 存档键 `sw.canoerapids.v1`；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；theme-gates / audit-gates 全绿

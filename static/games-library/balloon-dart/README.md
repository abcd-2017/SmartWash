# 飞镖爆球（gameKey：`balloon-dart`，category 建议：`arcade`）

- 玩法：60 秒限时准心投镖，气球串左右漂移上下浮动；一镖穿透串联越多连爆加成越高（第 n 个 +10n），时间到结算，记录最高连爆。
- 操作：触屏移动准心、抬手即投（按住可预瞄），键盘 ←→ 沿弧微调准心、空格 / 回车投镖；空格也可开始 / 暂停（同一按钮按状态切换）。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；飞镖带轻微重力坠差，虚线为弹道预估；难度随时间递增（球速加快、生成间隔缩短）。最高分 + 最高连爆 localStorage + 账号存档双写，结束上报最高分（desc，附最高连爆）；WebAudio 合成音效，失败静默。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量（恒白高光例外）
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅色双主题 canvas 重绘
- [x] 存档键 `sw.balloondart.v1`；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval

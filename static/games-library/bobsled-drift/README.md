# 雪橇漂移（gameKey：`bobsled-drift`，category 建议：`arcade`）

- 玩法：俯视长雪道持续下冲，弯道里急转会降低侧向抓地产生漂移滑移；贴住弯道内壁攒火花获得持续加速（松开即衰减），撞外壁 -1 护甲并反弹，3 点护甲耗尽结束，里程即分数；速度越快越必须用漂移才能过弯。
- 操作：触屏按住画布左右拖动转向（按得越偏转得越急）；键盘 ← → / A D 转向；「开始 / 暂停 / 继续 / 重新开始」同一按钮切换；切后台自动暂停。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；基础速度随里程自增（210→350）+ 内壁火花加成（最高 +110）；最高分 localStorage + 账号双写（裸最高分，snake 先例），结束上报最高分（desc）；漂移滑移物理（抓地系数调制侧滑）为合集独有；WebAudio 未接入。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，雪道 canvas 自绘
- [x] 颜色全部走 `--g-*` / `--g-st-*` token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅双主题 canvas 重绘
- [x] 存档键 `sw.bobsleddrift.v1`；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；theme-gates / audit-gates 全绿

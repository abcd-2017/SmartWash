# 双管飞碟（gameKey：`clay-shoot`，category 建议：`arcade`）

- 玩法：双管霰弹枪交替打击抛物线飞碟：每发后换管、两发打完强制装弹 1.1s（准星外圈进度环），装弹间隙飞碟持续出碟；命中最近飞碟 +10（带红圈的加急碟 +20），连击每 3 次提升倍数（最高 x5），同一对双管都命中=双飞额外 +20，放空断连击；飞碟逃出 5 只结束；出碟更快更飘（分数越高越难）。
- 操作：触屏移动准星、点按开火；键盘方向键移动准星（长按连续微调）、空格开火；「开始 / 暂停 / 继续 / 重新开始」同一按钮切换；切后台自动暂停。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；最高分 localStorage + 账号双写（裸最高分，snake 先例），结束上报最高分（desc）；与 target-rush（点射标靶）、balloon-dart（飞镖）不同：本款核心是双管弹药管理 + 装弹节奏；WebAudio 未接入。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，靶场 canvas 自绘
- [x] 颜色全部走 `--g-*` / `--g-st-*` token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅双主题 canvas 重绘
- [x] 存档键 `sw.clayshoot.v1`；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；theme-gates / audit-gates 全绿

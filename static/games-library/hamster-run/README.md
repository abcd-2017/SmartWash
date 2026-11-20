# 仓鼠飞轮（gameKey：`hamster-run`，category 建议：`arcade`）

- 玩法：仓鼠在滚轮里奔跑，轮速 = 基础速度随里程自增 + 空中连点转轮的临时提速（会缓慢衰减）；跳过深坑、躲开尖刺、顺路吃种子，陷坑或撞刺即结束，里程 + 种子折算分数。
- 操作：触屏点画布（地面起跳 / 空中每点一次提速）或空格 / ↑ / W；「开始 / 暂停 / 继续 / 重新开始」同一按钮按状态切换；切后台自动暂停。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；难度随里程递增（坑更宽、刺更密更高、地面间隙更小）；最高分 localStorage + 账号存档双写（裸最高分，snake 先例），结束上报最高分（desc）；分数 = 里程 m + 种子×5；WebAudio 未接入（可选项）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 `--g-*` / `--g-st-*` token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅双主题 canvas 重绘
- [x] 存档键 `sw.hamsterrun.v1`；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval；theme-gates / audit-gates 全绿

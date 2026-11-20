# 割绳取糖（gameKey：`rope-cut`，category 建议：`puzzle`）

- 玩法：糖果挂在绳上做钟摆运动，划断绳子让它带着摆动速度飞进怪兽嘴里，沿弹道顺路吃星；单绳荡秋千与双绳接力两种局面，12 秒内进嘴过关，星星逐关记录。
- 操作：触屏在画布上划过绳即割（线段相交判定），键盘数字键 1 / 2 / 3 按钉点序号割对应绳（钉点带徽标）；「重试 / 选关 / 下一关」按状态切换。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）。自研 verlet 绳物理（固定步长 1/120s + 6 轮约束迭代，确定性可复现）。40 关**按解法构造**：生成器扫描割绳时刻找到穿越目标深度的弹道 → 沿弹道布 3 星、落点放嘴 → 整局按解法回放终验（必须进嘴且全星），不达标换种子重造；调试入口 `window.__validateLevels()` 复现全部回放（node 抽取 XC-CORE 段独立运行）。关卡缓存 `sw.ropecut.v1.levels`，DB 下发整批替换（解法回放不过整批放弃）；存档 `sw.ropecut.v1` 双写（星星逐关取多），过关上报到达关卡（desc，附星数）。

## 关卡与验证

- 关卡数据：`{name, pins:[[x,y]…], ropes:[节数…], candy:[x,y,vx,vy], stars:[[x,y]…], mouth:[x,y], tmax, solution:[{k,t}…]}`。
- 可解性：解法回放 40/40 过关、全星（均星 2.4/关，少数关因星距约束收 2 星）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量（恒白眼白例外）
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 深浅色双主题；存档键 `sw.ropecut.v1`；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval

# 风筝斗线（gameKey：`kite-duel`，category 建议：`arcade`）

- 玩法：斗线对决：风筝带线飞行（轨迹即线），己方风筝动段掠过对方线即剪断获胜、被对方掠过即败；锚点 60px 保护区剪不到，AI 按反应间隔追剪我方线，风力持续推移，60 秒内取胜过关。
- 操作：按住画布拖动指向飞行方向（松手直飞），键盘 ←→ / A D 左右转向；「重试 / 选关 / 下一关」按状态切换。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）。40 关由种子生成器确定性生成：AI 速度 100→140、转向 1.2→2.0、反应间隔 1.0→0.55s、风力 6→22 递增。**可胜性验证 = 参考策略确定性模拟**（飞向对方线上离其风筝 ≥40px 的最近点并穿过，每帧重选；无目标时绕自家锚点盘旋待机），40/40 全胜（AI 追线与我方反剪构成对抗性缠斗）；调试入口 `window.__validateLevels()` 复现（node 抽取 XC-CORE 段独立运行）。关卡缓存 `sw.kiteduel.v1.levels`，DB 下发整批替换（模拟不胜整批放弃）；存档 `sw.kiteduel.v1` 双写，过关上报到达关卡（desc，附剪断用时）。

## 关卡与验证

- 关卡数据：`{name, aiSpeed, aiTurn, aiReact, wind, windAng, seed}`；线 = 风筝轨迹折线（≥8px 采样），线长受锚点 430px 半径约束。
- 可胜性：参考策略模拟 40/40 胜（均剪断用时 ~2s，AI 追剪压力随反应间隔缩短而增大）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 深浅色双主题；存档键 `sw.kiteduel.v1`；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval

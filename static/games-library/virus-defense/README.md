# 消灭病毒（gameKey：`virus-defense`，category 建议：`arcade`）

- 玩法：固定底部炮台迎击一波波下压的病毒（普通 / 快速 / 坦克，每 10 关终波 Boss），炮台自动索敌最逼近底目标；击杀得金币，局内升级攻速与弹道（多束），病毒触底扣生命，清完全部波次过关。
- 操作：升级按钮 / 键盘 1（攻速）2（弹道）购买；炮台自动开火无需瞄准；「重试 / 选关 / 下一关」按状态切换。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）。40 关由种子生成器确定性生成（波数 3→8、每波 5→18 只、血量 17→347、速度 17→34，首波热身血量 0.55 折，Boss 血 ×9），**可通关性验证 = 贪心升级策略确定性模拟**（与游戏共用同一套出怪 / 开火 / 命中结算核心，0.05s 步长），40/40 全胜；调试入口 `window.__validateLevels()` 复现（node 抽取 XC-CORE 段独立运行）。关卡缓存 `sw.virusdefense.v1.levels`，DB 下发整批替换（模拟不过整批放弃）；存档 `sw.virusdefense.v1` 双写，过关上报到达关卡（desc，附剩余生命）。

## 关卡与验证

- 关卡数据：`{name, waves:[{n, kind:0-3, hp, spd}], gold0}`。
- 可通关性：贪心策略（攻速 Lv5 → 双弹道 → 攻速 MAX → 弹道 MAX）确定性模拟 40/40 全胜、零漏怪，难度曲线由血量 / 速度 / 波数递增承载。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量（恒白高光例外）
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 深浅色双主题；存档键 `sw.virusdefense.v1`；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval

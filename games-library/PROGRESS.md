# PROGRESS.md · 游戏主题化进度

> 范围：`library/` 实际存在的游戏（g2048 于 2026-10-31 接入本表）。
> 规则与 token 映射：`GAME-THEME.md`（决策 D1–D5，**D5 已修订：无手动切换层，桥唯一控制**）；
> 三关检查脚本：`theme-gates.mjs`。验收日期：2026-10-31（含当日二次修订）。全量 ✅。

| 游戏 | 渲染 | 存档键（未动） | 状态 | 体积 前→后 (B / 增幅) | 三关 | 375px 实开 |
|---|---|---|---|---|---|---|
| snake | canvas·事件驱动 | `sw_h5_snake_best` | ✅ | 12563→14432 / +14.9% | ✅✅✅ | ✅ 深浅切换·canvas 重绘·可玩 |
| breakout | canvas·rAF | `breakout.best` `breakout.bestLevel` | ✅ | 12421→14155 / +14.0% | ✅✅✅ | ✅ 游戏中切主题·开局可玩 |
| flappy | canvas·rAF | `flappy.best` | ✅ | 10458→12346 / +18.1% | ✅✅✅ | ✅ 夜间模式·起飞可玩 |
| gomoku | canvas·按需 | `gomoku.record` | ✅ | 9900→11629 / +17.5% | ✅✅✅ | ✅ 深色棋盘·落子 AI 应答 |
| minesweeper | DOM 网格 | （无存档键） | ✅ | 12857→14616 / +13.7% | ✅✅✅ | ✅ 数字提亮·泛洪翻开正常 |
| g2048 | DOM 网格 | `sw_h5_2048_best` | ✅ | 11782→13015 / +10.5% | ✅✅✅ | ✅ 桥推送深色·纯 DOM 级联重绘·合并计分可玩 |

## 修订记录

- **2026-10-31 三次修订（用户裁定）**：撤销 matchMedia 跟随层——**无桥恒浅色，深色
  只能由桥推送进入**（Android WebView 的 `prefers-color-scheme` 检测跨厂商/版本不可依赖，
  主题只信桥）。运行时进一步瘦身，三关复验全过。连带影响：深色模式开游戏先浅色后推送
  变深（App 侧 getTheme 返回具体值可消除，待拍板，见 GAME-THEME.md §4）。
- **2026-10-31 二次修订（用户裁定）**：撤销右上角手动切换按钮与 `localStorage g-theme`
  手动层（初版按原 goal 实现，当日裁撤）——主题**唯一控制源 = 桥**（`getTheme` 初值 +
  `__applySmartWashTheme` 推送）。已移除按钮 HTML/CSS、`__gThemeToggle`、让位布局与死 token。

三关 = JS 语法（提取 `new Function`） / 零外链 grep / 体积 ≤30%；
375px 实开 = 无横向溢出 + 桥模拟（无桥恒浅色、`__applySmartWashTheme` 推送生效、
非法值拒绝）+ 可玩抽查 + 双主题截图。

实开中修复/裁撤的记录（详见 GAME-THEME.md）：主题切换基准 bug 与 SVG 无引号属性问题
（初版按钮机制内发现，随按钮裁撤一并消解，教训留档 §5）。

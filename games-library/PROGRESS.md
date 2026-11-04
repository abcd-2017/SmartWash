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

- **2026-11-04（二批）新增五款（出生即主题化，游戏库 23 → 28 款）**：
  sokoban（推箱子）/ connect-four（四子棋）/ solitaire（空当接龙）/
  fruit-slice（切水果）/ air-hockey（空气曲棍球）。
  补齐牌类（接龙）、棋类（四子棋）、切割与球台对战玩法；全部按 §6 清单接入，
  存档键 `sw.<key>.v1`，触屏热区 ≥44px。

  | 游戏 | 渲染 | 存档键 | 体积 | 三关 | 375px 实开 | 专项校验 |
  |---|---|---|---|---|---|---|
  | sokoban | canvas·rAF | `sw.sokoban.v1` | 15737B | ✅✅✅ | ✅ 过关→下一关→撤销全链路 | BFS 求解器 12/12 可解，难度曲线 1→6 推 |
  | connect-four | canvas·rAF | `sw.connectfour.v1` | 17656B | ✅✅✅ | ✅ 落子+AI 应手+悬停预览 | AI 深度 4 alpha-beta 13 项断言（胜着/堵截/中列） |
  | solitaire | DOM 纸牌 | `sw.solitaire.v1` | 20348B | ✅✅✅ | ✅ 翻牌/点选移动/双击进 foundation | 规则函数 6 组断言（叠放/空列/foundation） |
  | fruit-slice | canvas·rAF | `sw.fruitslice.v1` | 18278B | ✅✅✅ | ✅ 挥刀切中得分 | 线段-圆相交判定断言；连击计分 |
  | air-hockey | canvas·rAF | `sw.airhockey.v1` | 15817B | ✅✅✅ | ✅ 跟手+开球+AI 对战 | 法向弹性碰撞+球拍速度传递，AI 限速回防 |

  Playwright 375px 全套（初开浅色 / 冒烟交互 / 深色推送 / 非法值拒绝 /
  无横向溢出 / 无 `<h1>` / 零 JS 异常）：5/5 通过。
  冒烟中修复 sokoban 两个真实缺陷：`save` 函数与状态变量同名导致过关时
  抛错（改名 persist）、过关后 `lv` 未自增导致重复加载同一关。

- **2026-11-04 新增六款（出生即主题化，游戏库 17 → 23 款）**：
  tetris（俄罗斯方块）/ sudoku（数独）/ idiom（猜成语，**word 类别首款**）/
  pacman（吃豆人）/ invaders（星空保卫战）/ lights-out（点灯谜阵）。
  全部按 §6 清单接入：无 `<h1>`、桥唯一控制、token 上色、卡片式 `full` chrome、
  存档键 `sw.<key>.v1`、触屏热区 ≥44px。

  | 游戏 | 渲染 | 存档键 | 体积 | 三关 | 375px 实开 | 专项校验 |
  |---|---|---|---|---|---|---|
  | tetris | canvas·rAF | `sw.tetris.v1` | 16503B | ✅✅✅ | ✅ 深浅切换·落块/旋转/硬降可玩 | 按住连移 130ms；7-bag 随机 |
  | sudoku | DOM 网格 | `sw.sudoku.v1` | 16519B | ✅✅✅ | ✅ 选格填数/笔记/冲突标红 | 生成器 24 局抽测：三档提示格全部命中（40/32/26）且唯一解 |
  | idiom | DOM 键盘 | `sw.idiom.v1` | 18318B | ✅✅✅ | ✅ 点字组词/逐字反馈/键盘染色 | wordle 双计法用例校验（含「一心一意」重复字） |
  | pacman | canvas·rAF | `sw.pacman.v1` | 21300B | ✅✅✅ | ✅ 滑动转向/幽灵 AI/能量豆反击 | 迷宫脚本校验：99 豆格全可达·零死胡同·镜像对称·G 区连通 |
  | invaders | canvas·rAF | `sw.invaders.v1` | 18714B | ✅✅✅ | ✅ 按住拖动跟随/自动开火/掩体侵蚀 | 复刻 susam/invaders（MIT）思路 |
  | lights-out | DOM 网格 | `sw.lightsout.v1` | 9191B | ✅✅✅ | ✅ 点灯翻转/★最优标记/关卡进度 | 生成器 200 局抽测：反向奇偶集校验全可解 |

  Playwright 375px 全套（初开浅色 / 冒烟交互 / `__applySmartWashTheme` 推深 /
  非法值拒绝 / 无横向溢出 / 无 `<h1>` / 零 JS 异常）：6/6 通过。
  同批把存量 README 的 category 建议统一到端上白名单：beat-tap→arcade、
  billiards→board、blackjack→board、falling-sand→other、moon-lander→arcade
  （slingshot 版本库中已为 puzzle，仅复位工作区陈旧副本），上传填表不再回落「其他」。

- **2026-11-02 四次修订（用户反馈「游戏与系统标题返回栏不协调」）**：
  1. **标题唯一来源 = App 原生页头**：17 个游戏**全部删除自绘 `<h1>`**（含孤立的 `h1{}` 规则），
     消除「原生页头 + 游戏大标题」两条标题栏叠加；游戏 `<header>` 只留记分块 / 状态行 / 玩法提示 / 操作按钮。
  2. **排版收敛 v0.4 八档**（36/28/22/18/16/14/12，weight 400–700）：`.sub` 13→14、`.label` 11→12、
     `.val` 19/20→22、`.overlay h2` 23/26→22、`.btn` 15/17→16、`.ov-text` 13→14 等；
     **UI 壳**圆角收敛 16/14/12/10（`.score-box` 8→12、`.ov-stats .st` 10→12 等）；
     **玩法素材圆角保留原设计值**（§3 D2）：nonogram 的解答网格 / 行列题板 / 像素揭示维持
     3px / 2px / 1px——细密像素面套 UI 圆角档会变成「泡泡格」与圆点，像素感尽失。
  3. **接入原生 chrome 控制**（`GAME-THEME.md` §5.1）：全屏画布游戏
     **breakout / flappy / gomoku / falling-sand / beat-tap** 调 `setChrome('immersive')`
     （隐藏系统状态栏、保留返回入口），其余保持 `full`。
  - 三关复验：语法 `new Function` 全 PASS / 零外链全 0 命中 / 体积 −1%～+0%（≤30% 预算）；
    Playwright 375px 实开：17/17 无 `<h1>`、无页面级横向溢出、主题运行时与深浅推送正常、无 JS 异常。

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

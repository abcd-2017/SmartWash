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

- **2026-11-09（游戏数据平台化·批量铺开）8 款街机型接入账号存档 / 成绩上报**：
  g2048、flappy、tetris、fruit-slice、air-hockey、moon-lander、snow-trail、forest-run
  复用 `GB` 桥适配层（无桥自动降级 localStorage）：存档双写（本地 + 账号，账号更优才覆盖），
  在胜利 / 结束点上报 metric——g2048 最高分（desc，附最大方块值）、flappy / tetris 最高分（desc）、
  fruit-slice 最高分（desc，附最高连击）、air-hockey 胜场（desc，附当前连胜）、
  moon-lander / snow-trail / forest-run 最高分（desc）。本批无关卡数据，不做关卡热更新。
  存档 JSON 与本地结构完全一致：单值存档（g2048 / flappy / snow-trail）按 snake 先例保持裸值，
  多键存档（moon-lander）按 billiards 先例把本地键名镜像进账号 JSON。
  顺带修复 tetris 既有回归：`saveBest` 误把 `best` 对象整体写入、`loadBest` 读出后 `parseInt` 恒为 0，
  最高分从未真正持久化。`theme-gates` + `audit-gates` 8/8 全绿。

- **2026-11-13（新游戏·自研入库·第七批）pitch-pot 投壶 / idiom-chain 成语接龙 / tile-tap 落块达人（游戏库 50 款）**：
  净室自研（零外链、零素材、canvas/DOM 自绘），出生即按 §6 规范并登记 audit-gates STRICT。pitch-pot 120 关
  （`{name,arrows,distance,wind,target,bonus}`，抛物线 + 风力，解析反解 + 模拟验证 120/120 可通关）；
  idiom-chain 120 关（`{name,start,length,time,hints,mask}`，自撰 1249 成语词库 + 接龙图可解性 DFS 实证）；
  tile-tap 120 关（`{name,rows,speed,seed}`，时钟驱动落块 + 种子确定性 + 引擎交叉比对）。
  三款严格模式 0 违规、theme-gates 全绿、零外链。

- **2026-11-13（新游戏·自研入库·第六批）maze 迷宫 / game24 24点 / whack-a-mole 打地鼠（游戏库 47 款）**：
  净室自研（零外链、零素材、canvas 自绘），出生即按 §6 规范并登记 audit-gates STRICT。maze 120 关
  （`{name,rows,cols,cells}`，生成树打通全通路 + 独立 BFS 复核）；game24 120 关
  （`{name,numbers,target}`，精确有理数求值 + 独立穷举求解器复核可解）；whack-a-mole 120 关
  （`{name,holes,time,speed,visible,moleRatio,bombRatio,variant,target}`，400 局蒙特卡洛验证目标分合理）。
  三款严格模式 0 违规、theme-gates 全绿、零外链。

- **2026-11-13（新游戏·自研入库·第五批）suika 合成大西瓜 / klotski 华容道 / pipes 管道工（游戏库 44 款）**：
  净室自研（零外链、零素材、canvas 自绘），出生即按 §6 规范并登记 audit-gates STRICT。suika 120 关
  （`{name,target,moves,seed,fruitSet}`，自研 2D 圆物理 + 合成，引擎同构求解回放验证）；klotski 128 关
  （`{name,blocks,goal}`，A* 精确最少步数求解器构造可解布局，经典布局复核 105 步）；pipes 120 关
  （`{name,size,tiles,src,snk,par}`，生成树构造连通解再打乱，独立回溯求解器 120/120 复核）。
  三款严格模式 0 违规、theme-gates 全绿、零外链。

- **2026-11-13（新游戏·自研入库·第四批）mahjong-solitaire 麻将连连看 / xiangqi 中国象棋（游戏库 41 款）**：
  净室自研（零外链、零素材、canvas 自绘），出生即按 §6 规范并登记 audit-gates STRICT。mahjong-solitaire 120 关
  （`{w,h,p,g}` 层叠牌对，反向放置构造保证可解 + 独立回溯求解器复核）；xiangqi 120 关
  （`{name,depth,handicap,first,rand}`，完整规则 + negamax/α-β AI，四难度参数组合递进）。
  两款严格模式 0 违规、theme-gates 全绿、零外链。

- **2026-11-13（新游戏·自研入库·第三批）hangman 猜单词 / mastermind 密码破译 / asteroids 小行星（游戏库 39 款）**：
  净室自研（零外链、零素材、canvas/DOM 自绘），出生即按 §6 规范并登记 audit-gates STRICT。hangman 122 关
  （`{name,word,hint,topic,maxWrong}`，词长 3→10 递进，提示不泄字母）；mastermind 120 关
  （`{name,slots,colors,maxTries,secret}`，色弱双区分，secret 可重现）；asteroids 120 关
  （`{name,waves,seed,lives}`，惯性物理 + 虚拟按键多指操作，540 波次布局脚本复现校验）。
  三款严格模式 0 违规、theme-gates 全绿、零外链。

- **2026-11-13（新游戏·自研入库·第二批）word-search 单词搜索 / dots-boxes 点格棋 / bubble-shooter 泡泡龙（游戏库 36 款）**：
  净室自研（零外链、零素材、canvas 自绘），出生即按 §6 规范并登记 audit-gates STRICT。word-search 100 关
  （`{name,size,words,grid}`，构造法摆词 + 逐词回读校验，10 主题 730 词）；dots-boxes 120 关
  （`{name,rows,cols,ai,first,handi}`，AI 三档，大师档 3 层 negamax + alpha-beta）；bubble-shooter 120 关
  （`{name,rows,colors,moves,target,seed}`，六边形网格 + 悬空簇掉落，引擎同构求解器回放 120/120 通关）。
  三款严格模式 0 违规、theme-gates 全绿、零外链。

- **2026-11-13（新游戏·自研入库）lianliankan 连连看 / match3 宝石消除（游戏库 33 款）**：
  净室自研（零外链、零素材、canvas 自绘），出生即按 GAME-THEME.md §6 规范实现（token 两块 CSS + head 运行时、
  无 h1、PAL + `__gOnTheme`、卡片式布局、热区 ≥44、375px 无横向溢出）。两款均带包内默认关 + DB 关卡下发
  （`GB.getLevels` + localStorage 版本缓存）与账号存档 / 成绩上报：lianliankan 120 关（`{r,c,p,g}` 棋盘串，
  反向放置构造保证可解，独立回溯求解器复核）；match3 120 关（`{name,moves,target,colors,seed,board}`，
  引擎同构贪心求解验证可通关）。已登记 audit-gates STRICT 名单，两款严格模式 0 违规，theme-gates 全绿。

- **2026-11-09（游戏数据平台化·批次三）crossword / idiom / beat-tap 接入账号存档 / 关卡热更新 / 排行榜**：
  复用 sokoban 沉淀的 `GB` 桥适配层铺开第三批，均为「DB 下发 + localStorage 版本缓存 +
  包内默认关兜底」，无桥自动降级。crossword 4 关 × 8 词（dataJson `{name,words,distractors}`，
  words 为 `[词,提示,方向,x,y]`），存档 `{lv,done}` 双写、账号按「关卡更高 + 已锁词并集」合并，
  通关上报到达关卡（desc）；idiom 114 词库整表作为关卡列表（dataJson `{word,meaning}`，
  校验四字），存档 `{played,streak,best}` 逐字段取更优，一局结束上报连胜 / 最佳连胜（desc）；
  beat-tap 3 谱曲配置原样下发（dataJson = 现有曲目结构 `{id,name,bpm,bars,seed,base,grid,tiers,diff}`），
  存档 `{曲id:{score,grade}}` 逐曲取更高分，结算上报最高分 / 准确率（desc）。
  两关（theme-gates / audit-gates）全绿；Playwright 冒烟 36/36（含无桥降级与缓存兜底两段）。
  另修复 beat-tap 既有回归：`abd791de`（静音 MVP）误删 `master = actx.createGain()`，
  导致音频图从未建立——无声且每次击打/鼓点抛 `connect` 错。

- **2026-11-09（游戏数据平台化·批量铺开）6 款无关卡游戏接入账号存档 / 成绩上报**：
  gomoku、connect-four、reversi、billiards、breakout、falling-sand
  按 sokoban / memory-cards 沉淀的 `GB` 桥适配层改造：存档双写（本地 localStorage + 账号，
  账号优先、更优才覆盖；存档 JSON 与本地结构完全一致，服务端不解析）——三款棋类取胜场更多者
  （reversi 保留本地难度偏好），billiards 三键镜像取更大，breakout 取最高分 / 最高关卡更大，
  falling-sand 发现进度取并集；在胜利 / 结束点上报 metricValue（+ metricExtra）——
  gomoku / connect-four / reversi 胜场（desc）、billiards 最高关卡（desc，附限时最佳）、
  breakout 最高分（desc，附最高关卡）；falling-sand 无排行榜，不上报成绩。
  本批无关卡数据，未做关卡热更新。无桥环境自动降级为纯本地，游戏照常可玩。
  `theme-gates` + `audit-gates` 6/6 全绿（connect-four / reversi 严格模式 0 违规）。

- **2026-11-09（游戏数据平台化·批量铺开）8 款无关卡游戏接入账号存档 / 成绩上报**：
  invaders、block-drop、lights-out、minesweeper、sudoku、pacman、blackjack、solitaire
  按 sokoban / memory-cards 沉淀的 `GB` 桥适配层改造：存档双写（本地 localStorage + 账号，
  账号优先、更优才覆盖；存档 JSON 与本地结构完全一致，服务端不解析）；在胜利 / 结束点上报
  metricValue（+ metricExtra）——invaders 最高分（desc，附最高波次）、block-drop 最高分（desc）、
  lights-out 最高关卡（desc，附 ★ 数）、minesweeper 用时（asc）、sudoku 用时（asc）、
  pacman 最高分（desc，附到达关卡）、blackjack 筹码（desc，附胜场）、solitaire 胜场（desc，附最佳用时）。
  本批无关卡数据，未做关卡热更新；minesweeper 原无存档，新增 `sw.minesweeper.v1` 记最佳用时。
  无桥环境自动降级为纯本地，游戏照常可玩。`theme-gates` + `audit-gates` 8/8 全绿。

- **2026-11-09（游戏数据平台化·批量铺开）snake 接入账号存档 / 成绩上报**：
  复用 sokoban / memory-cards 沉淀的 `GB` 桥适配层，改造第 1 款非关卡制游戏（无关卡热更新）。
  存档双写（本地 + 账号，账号更优才覆盖本地）：该游戏存档键为历史遗留的 `sw_h5_snake_best`，
  结构保持裸最高分不变（服务端不解析，未新增 / 改名任何本地键）；游戏结束上报最高分进排行榜
  （desc，无 metricExtra）。无桥环境自动降级为纯本地。`theme-gates` + `audit-gates` 全绿。

- **2026-11-09（游戏数据平台化·批量铺开）nonogram / magic-tower / slingshot 接入账号存档 / 关卡热更新 / 排行榜**：
  复用 sokoban / memory-cards 沉淀的 `GB` 桥适配层，一次落地 3 款关卡制游戏。
  关卡数组拆为 `DEFAULT_LEVELS`（包内兜底）+ `LEVELS`（DB 下发优先，`sw.<key>.v1.levels[.ver]` 缓存），
  按各游戏 `dataJson` 实际结构解析（nonogram `{name,grid}`、magic-tower 11×11 字符串数组、
  slingshot `{name,ammo,blocks}`）；存档双写（本地 + 账号，账号更优才覆盖本地：nonogram 取解锁更大 /
  通关并集 / 最佳步数更小，magic-tower 取楼层更高，slingshot 取最高分与解锁更大）；
  成绩上报到达关卡 / 层数（desc，分别附步数 / 剩余 HP / 剩余弹丸）。无桥环境自动降级为纯本地。
  三款 `theme-gates` + `audit-gates` 全绿（magic-tower 严格模式 0 违规）。

- **2026-11-09（游戏数据平台化·试点）sokoban 接入账号存档 / 关卡热更新 / 排行榜**：
  桥代理落地——Android `SmartWashGameBridge` 扩展 `loadState/saveState/reportScore/getLeaderboard/getLevels`，
  原生侧带 token 请求（token 不出 H5），白名单外 fail-closed；后端 3 表（`game_user_save` /
  `game_score_entry` / `game_level`）+ `/web/auth/game/*` 用户端接口 + `/admin/game/*` 管理端接口。
  sokoban 作为全链路试点改造：存档双写（本地 + 账号，账号优先）、关卡 DB 下发 + 版本缓存 +
  包内默认关兜底、过关上报到达关卡进榜。库内沉淀可复用「游戏桥适配层」（`GB`，无桥自动降级
  localStorage），后续 30 款按同一模式铺开。审计规则同步放宽：存档键允许 `sw.<key>.v1.<子键>`
  （关卡缓存键归入游戏存档命名空间）。

- **2026-11-09（游戏数据平台化·试点二）memory-cards 接入账号存档 / 关卡热更新 / 排行榜**：
  复用 sokoban 沉淀的 `GB` 桥适配层；存档双写并**按关合并历史最佳**（本地与账号取更优）；
  关卡 DB 下发 + 版本缓存 + 包内默认关兜底；通关上报**步数（asc，越少越好）**，
  验证榜单双向排序口径（与 sokoban 的 desc 到达关卡互补）。无桥环境自动降级。

- **2026-11-07（六批）crossword 填字（游戏库 30 → 31 款，word 品类第 2 款）**：
  槽位法内容生产落地（蛇形主链在部分主题下数学无解，改自由方向生成 + 极大连通串
  校验；校园层双独立连通块布局）。4 关 × 8 词，提示事实准确（校园场景口径），
  字池 = 答案字 ∪ 6 干扰字。DOM 网格 + 点词填字 + 自动锁定 + 撤回，
  存档 sw.crossword.v1。Playwright 冒烟全过。封面三件套补齐（青碧填字盘）。
  详见调研附录与 docs/crossword-tools/。

- **2026-11-07（五批）第三批收官 + 无障碍性能 MVP（游戏库 29 → 30 款）**：
  magic-tower（魔塔）落地，第三批三款（reversi/crossword/magic-tower）完成两款，
  crossword 因 2 字词网格几何约束转手工槽位设计（结论记档调研附录，工具存
  `docs/crossword-tools/cw-build.mjs`）。同批质量项：
  1. **dpr 封顶 2**（7 款）、**g2048 色盲修复**（128+ 蓝阶 + 全档对比 ≥4.5）、
     **ARIA 全库覆盖**（canvas role=img + 28 款 aria-live 状态区）、
     **beat-tap 静音**（sw.beattap.audio.v1）、**渐变缓存**（forest-run/snow-trail/slingshot）。
  2. **audit-gates 加固**：存档键全量提取（27 款 32 键零漏零误）、布局类型判定
     （全屏改查 T14）、热区选择器扩展、§4 裁撤残留、theme-color 同步、token 基线值、
     JS 颜色字面量、canvas 字号灰区、MATERIAL_PREFIX 补 st-/mt-。
  3. **存量 17 款硬差距收口**：tap-highlight×3、420px→480px×6、scrim 基线统一×8、§3 补登。
  4. **封面三件套**：游戏目录改为 index.html/README/`<gameKey>.png`（800×450），
     reversi（绿毯棋盘）与 magic-tower（靛蓝石塔）封面按库内扁平风格 PS 绘制。

  | 游戏 | 渲染 | 存档键 | 体积 | 三关 | 375px 实开 | 专项校验 |
  |---|---|---|---|---|---|---|
  | magic-tower | canvas·格子 | `sw.magictower.v1` | 19808B | ✅✅✅ | ✅ 寻路战斗/上楼存档/刷新恢复/重开清档 | 离线贪略求解 8 层可通（余 HP=1602） |

  全库终态：30 款严格+存量双模式 0 违规；theme-gates 30/30。

- **2026-11-04（三批）规范对齐与工具沉淀**：
  1. **布局对齐 §6**：snake（420px→480px、padding 16/12/28→16/8/30）、
     minesweeper（460px→480px、padding 16/6/28→16/8/30）两款历史版本
     遗留宽度/内边距统一到现行规范；375px 实开复验无溢出、深色正常。
  2. **审计工具入库**：`audit-gates.mjs`（§2/§4/§5.1/§6 全条款机器审计，
     严格模式核对 11 款新游戏、存量模式核对 17 款历史版本）与
     `theme-gates.mjs`（三关脚本，此前仅存在于仓库外）沉淀进本目录，
     PROGRESS 中引用的检查脚本现已可在库内直接运行。
  3. **全库审计结果**：28/28 通过（严格 0 违规、存量 0 违规；历史遗留的
     旧存档键/素材 token/早期触屏规则缺项按 §6「存档键不改」与 §3 例外
     记为信息项，不视为违规）。

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

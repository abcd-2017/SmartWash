# 汉诺塔（gameKey：`hanoi`，category 建议：`puzzle`）

- 玩法：经典汉诺塔。三根柱子，N 个大小不同的圆盘起始按「大在下小在上」堆叠，每次只能移动任一柱顶的盘到另一柱且大盘不可压小盘，把全部盘子移到带旗的目标柱即过关；步数等于最优得 3 星、不超 1.5 倍得 2 星、完成得 1 星，限步关超步失败可重试。
- 操作：点柱子选中顶盘，再点目标柱完成移动；再点同柱取消选中。375px 宽无横向溢出，柱热区为整列（≥44px），支持上一关 / 重玩 / 下一关。
- 说明：单文件自包含 H5，DOM 自绘零外链；包内内置 12 关 + DB 下发 107 关（盘数 3→10 × 起始/目标柱组合 × 经典/随机合法布局变体 × 限步挑战，最优步数经 BFS 全空间独立复核）；进度与星级存档跟随账号（`sw.hanoi.v1`），无桥自动降级 localStorage。

## 关卡数据

- 信封：`.levels.json` 顶层 JSON 数组，每项 `{levelNo, name, difficulty, data}`（levelNo 1..107 连续，difficulty 1-10 难度单调递进）。
- data 格式：`{ name, disks, startPeg, targetPeg, variant, layout?, limit? }`
  - `disks`：3–10 个盘；`startPeg`/`targetPeg`：柱号 1–3（左/中/右），恒不等；`variant`：`classic`（全塔在起始柱）或 `random`（随机合法散盘布局）。
  - `layout`（仅 random）：3 元素数组，每柱从底(大)到顶(小)列盘号，覆盖 1..N 恰一次；`startPeg` 恒等于最大盘所在柱。
  - `limit`（可选）：限步上限，取最优步数 ×1.3–2，恒 ≥ 最优；存在时进入限步模式。
- 最优步数：classic = 2^N−1；random = 任意状态贪心递推（`optSteps`），全部 107 关另经全空间 BFS（`bfsAll`，3^N ≤ 59049 状态）独立复核一致并确认有解。
- 生成/校验工具：`docs/level-gen/tools/hanoi/`（`generate.mjs` 确定性种子分层采样；`validate.mjs` 结构/BFS/唯一性/字节上限；`parse-test.mjs` 解析单测 62 项；`browser-test.mjs` 无头 Chrome e2e 37 项）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，棋盘/圆盘全部 DOM 自绘，颜色全走 `var(--g-*)`（无 canvas，无 JS 颜色字面量）
- [x] 主题只由桥控制（`<head>` 运行时，无按钮、无 matchMedia）；卡片式布局 `.wrap` max-width 480px，375px 无横向溢出
- [x] 不自绘标题（标题由 App 原生页头承载）；字号八档、圆角 16/14/12/10、按钮 min-height 44px
- [x] 存档键 `sw.hanoi.v1`（关卡缓存 `sw.hanoi.v1.levels` / `.levels.ver`）
- [x] 关卡驱动照 crossword/klotski 范式：DB 下发优先、任一条非法整批放弃、无桥用包内 12 关

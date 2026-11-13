# 24点（gameKey：`game24`，category 建议：`puzzle`）

- 玩法：经典 24 点。每关给出 4 个 1-13 的数字，用加、减、乘、除（可加括号）把 4 个数字各用一次算出目标点数（默认 24）。每关限时且答错扣机会，答对过关，共 120 关，难度递进。
- 操作：点下方数字块与运算符号组式；`(` `)` 加括号，`⌫` 撤回一步，`清空` 重来；「提交答案」判分，未闭合的括号会自动补齐；「重玩本关」重置本关时间与机会。触屏按钮热区均 ≥44px，375px 宽无横向溢出。
- 说明：单文件自包含 H5，CSS/JS 全内联，零外链、零素材文件；纯 DOM 实现（无 canvas），主题经 CSS 变量自动切换，故不设 `PAL`/`__gOnTheme` 重绘钩子。关卡数据采用包内 `DEFAULT_LEVELS` + DB 下发（`GB.getLevels("game24")`），任一条非法整批放弃，无桥时回退本地缓存/默认关卡；进度与最快用时经 `saveState` 持久化，过关经 `reportScore` 上报。

## 关卡数据格式

`.levels.json` 顶层数组，每项信封：

```json
{ "levelNo": 1, "name": "入门·01", "difficulty": 1, "data": { "name": "入门·01", "numbers": [1,2,6,6], "target": 24, "difficulty": 1 } }
```

- `numbers`：恰好 4 个 1-13 的整数；`target`：目标点数（缺省 24）；`difficulty`：1-10，决定限时（90/75/60/45 秒）与答错机会（5/4/3）。
- 全部 120 关均通过穷举求解器验证可解，组合唯一；首 10 关与 `index.html` 内 `DEFAULT_LEVELS` 逐字一致。

## 生成与校验工具

位于 `docs/level-gen/tools/game24/`（`docs/` 已被 .gitignore 排除）：

- `generate.mjs`：随机采样 + 最小代价解排序，生成 120 关并写出 `.levels.json`。
- `solve.mjs`：生成器用穷举求解器（状态记忆化，输出最小代价解用于难度排序）。
- `verify.mjs`：独立终检（不依赖生成器）——契约、结构、唯一性、规模，并用独立实现的「排列 × 两两合并」求解器逐关复核可解性。
- `test-parse.mjs`：`syncLevelsFromServer` 解析逻辑单测，覆盖合法批次完整接受与任一非法整批放弃。

```bash
node docs/level-gen/tools/game24/generate.mjs
node docs/level-gen/tools/game24/verify.mjs
node docs/level-gen/tools/game24/test-parse.mjs
```

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片
- [x] 无 `<h1>`（标题由 App 原生页头承载），卡片式布局
- [x] 主题仅由桥控制（`<head>` 运行时，无按钮、无 matchMedia）
- [x] 存档键统一在 `sw.game24.v1` 命名空间下

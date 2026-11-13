# 连连看（gameKey：`lianliankan`，category 建议：`puzzle`）

- 玩法：矩形牌阵（可含空位）上点选两张相同图案的牌，若连线转折不超过 2 次且不越过其他牌即可消除；清空全盘过关，无子可消时自动洗牌（保底可解）。
- 操作：触屏点选两张牌；「提示」高亮一对可消牌，「洗牌」重排余牌，「选关」进入已解锁关卡，「重玩」重置本关。
- 说明：单文件自包含 H5，Canvas 自绘图案（几何图形 × 8 色板，零素材、零外链）；进度存 `localStorage`（`sw.lianliankan.v1`），通关数经桥上报排行榜。

## 关卡数据格式

`.levels.json` 顶层数组，每项信封 `{"levelNo":N,"name":"…","difficulty":D,"data":{…}}`，`data` 结构：

| 字段 | 含义 |
|---|---|
| `r` / `c` | 牌阵行数 / 列数（r 3–14，c 3–6） |
| `p` | 牌对数 |
| `g` | 长度 `r*c` 的棋盘串：`.` 为空位，`0-9A-Z` 为图案编号（base36） |

紧凑（单关 ≤ 百余字节）、可校验（长度、字符合法、每种图案偶数个、对数与 `p` 一致、可解性），确定性由固定种子生成。

## 生成与可解性验证

- 生成：`docs/level-gen/tools/lianliankan/generate.mjs`。采用**反向放置构造法**——从空盘起每次挑两个当前可连的空格放同图案牌，放置时保证该对牌在已有障碍下仍可被 ≤2 折线连接，故按放置逆序消除必然可行；难度递进（尺寸 4×4→12×6、对数 4→30、图案 3→14、越往后越偏向 2 折连接）。
- 校验：`verify.mjs` 用**独立的 0-1 BFS（Dijkstra）**连线判定复核生成器记录的消除见证，再用**独立的记忆化回溯求解器**从终盘确认可完整消除；另检结构、唯一性、数量与字节上限。
- 交叉核对：`cross-check.mjs` 用 `index.html` 内游戏引擎实际使用的 `findLink` 逐对复核全部见证（1843 次消除，最大转折 2）。
- 解析单测：`test-parse.mjs` 把 `.levels.json` 全量 `dataJson` 喂给 `parseDbLevels`，并覆盖结构非法、无解棋盘、批次任一条非法整批放弃等用例。

## 自查清单

- [x] 单文件零外链：CSS/JS 全内联，无 `http://`、`https://` 与 `//域名` 字样
- [x] 严格模式审计通过：`node audit-gates.mjs lianliankan` 0 违规
- [x] 三关通过：`node theme-gates.mjs lianliankan` 语法 / 外链 / 体积全绿
- [x] 375px 无横向溢出：牌面 48px（≥44px 热区），Canvas 宽度随容器自适应
- [x] 主题桥接：head 内运行时与范例一致，`__applySmartWashTheme` 深色推送生效，Canvas 经 PAL + `__gOnTheme` 重绘
- [x] 120 关全部通过结构 / 见证 / 独立求解器三重可解性验证
- [x] 已接入游戏数据平台化：`GB.getLevels("lianliankan")` + 本地缓存 + 包内默认关兜底

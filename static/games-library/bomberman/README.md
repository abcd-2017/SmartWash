# 炸弹人（gameKey：`bomberman`，category 建议：`board`）

- 玩法：13×13 网格迷宫。玩家炸碎砖墙开路、放置定时炸弹（十字火焰），消灭本关全部敌人即通关；钢墙不可摧毁，火焰与敌人触碰会损失生命（共 3 条命）。敌人分普通 / 快速 / 追击 / 重甲四类，从顶部三点分批进攻，会巡逻或朝玩家推进；击毁携带闪光标记的敌人掉落道具——火力（火焰变长）/ 弹数（同时更多炸弹）/ 移速。
- 操作：左下虚拟十字键移动（按住连续移动，接近格子自动对齐），右下「放弹」按钮布置炸弹；键盘可用方向键 / WASD + 空格。开始页可点关卡号码选择已解锁关卡。
- 说明：单文件自包含 H5，素材全部 Canvas 自绘，零外链零素材文件。爆炸前引信 1.9 秒，火焰持续 0.5 秒，可 chain 引爆其它炸弹。进度（解锁 / 星级 / 最高分）经 `saveState` 双写本地与账号，过关上报到达关卡与得分。

## 关卡数据格式

`.levels.json` 顶层数组，每项信封：

```json
{ "levelNo": 1, "name": "开阔地·01", "difficulty": 1, "data": { "name": "开阔地·01", "map": ".................B....", "enemies": {"normal":3}, "seed": 20261115 } }
```

- `map`：169 字符（13×13），`.` 地板 / `B` 砖墙（可炸碎）/ `S` 钢墙（不可摧毁）；外圈恒为钢墙。
- `enemies`：四种敌人数量（`normal` / `fast` / `chaser` / `heavy`），总数 1-24。
- `seed`：0 - 2^31-1，决定出怪顺序与 AI 随机序列。
- 全部 120 关通过硬校验：地图尺寸与字符集、玩家出生点安全（不被围死）、三个敌人出生点连通（BFS，砖墙可炸通）、砖块数量下限、难度分单调不减、组合唯一。

## 生成与校验工具

位于 `docs/level-gen/tools/bomberman/`（`docs/` 已被 .gitignore 排除）：

- `model.mjs`：地图解析 / 通行判定 / BFS 连通性 / 单关硬校验（与游戏内引擎逻辑同口径）。
- `generate.mjs`：8 种模板（柱阵 / 回廊 / 迷宫 / 开阔地 / 环垒 / 砖窟 / 要塞 / 散布）+ 确定性种子变体 + 敌人配置递进，生成 120 关并写出 `.levels.json`。
- `verify.mjs`：独立终检（不依赖生成器状态）——契约、结构、出生点、连通性、唯一性、难度单调、数量、字节。
- `test-parse.mjs`：`syncLevelsFromServer` 解析逻辑单测，覆盖合法批次完整接受与任一非法整批放弃。
- `test-e2e.mjs`：无头 Chrome 真实操作通关第 1 关（机器人经真实键鼠事件驱动）+ 主题推送验证。

```bash
node docs/level-gen/tools/bomberman/generate.mjs
node docs/level-gen/tools/bomberman/verify.mjs
node docs/level-gen/tools/bomberman/test-parse.mjs
node docs/level-gen/tools/bomberman/test-e2e.mjs
```

## 自查清单

- [x] 单文件零外链：CSS/JS 全内联，无 `http://`、`https://` 与 `//域名` 字样
- [x] 严格模式审计通过：`node audit-gates.mjs bomberman` 0 违规
- [x] 三关检查通过：`node theme-gates.mjs bomberman` 语法 / 外链全绿
- [x] 无 `<h1>`（标题由 App 原生页头承载），全屏画布布局并调 `setChrome('immersive')`
- [x] 主题仅由桥控制（`<head>` 运行时，无按钮、无 matchMedia）；canvas 颜色全部经 `PAL` 读取 CSS 变量，`__gOnTheme` 重绘
- [x] 存档键统一在 `sw.bomberman.v1` 命名空间下（存档 / 关卡缓存 / 版本号）
- [x] 375px 无横向溢出；虚拟按键 52px、放弹键 72px、关卡号码 44px，热区均 ≥44px
- [x] 120 关全部通过硬校验与独立终检，难度分 3.0 → 26.8 单调递增，组合唯一

# 弹球砖块（gameKey：`ballz-bounce`，category 建议：`arcade`）

- 玩法：BBTAN 式回合制弹球打砖块。顶部数字砖块每回合结束整体下压一行，底部拖动瞄准、松手发射一整排弹球；小球在场内纯反弹（无重力）、命中砖块数字 -1，归零消除。命中「+」道具球下回合球数 +1。任意砖块触碰警戒线即失败；清空全部砖块或撑过目标回合数即过关。
- 操作：按住画布拖动瞄准（显示虚线弹道），松手发射；按钮点按开始 / 重玩 / 下一关 / 选关。开局页可点关卡号码选择已解锁关卡。
- 说明：单文件自包含 H5，素材全部 Canvas 自绘，零外链零素材文件。物理为固定步长积分，关卡补行由种子确定性生成，同一关卡结果可复现。进度（解锁 / 最高分）经 `saveState` 双写本地与账号，过关上报到达关卡与得分。

## 关卡数据格式

`.levels.json` 顶层数组，每项信封：

```json
{ "levelNo": 1, "name": "平铺·001", "difficulty": 1, "data": { "name": "平铺·001", "cols": 6, "rows": 2, "balls": 6, "target": 5, "hpCap": 2, "fill": 0.55, "power": 0.04, "seed": 20261115, "bricks": [[0,0,2],[5,0,1,1]] } }
```

- `cols`：砖阵列数（5-8）；`rows`：初始行数（1-11）；世界逻辑尺寸恒为 360×540，列宽随列数换算。
- `balls`：初始球数；`target`：目标回合数（撑过即过关）。
- `hpCap`：数字上限（含补行）；`fill`：每回合顶部补行的填充概率（0.05-0.95）；`power`：补行道具球概率（0-0.4）。
- `seed`：0 - 2^31-1，决定每回合补行的确定性随机序列。
- `bricks`：`[列, 行, 数字]` 或 `[列, 行, 1, 1]`（第 4 位为 1 表示「+」道具球，数字恒为 1）。坐标不得越界或重叠。
- 全部 120 关通过硬校验：结构与数值域、布局合法（不越界 / 不重叠）、组合唯一、难度阶梯、数量与字节上限；并经同一引擎的策略模拟确认每关在目标回合内可通关。

## 生成与校验工具

位于 `docs/level-gen/tools/ballz-bounce/`（`docs/` 已被 .gitignore 排除）：

- `engine.mjs`：从 `index.html` 逐字提取内联引擎（BB-ENGINE-BEGIN/END），供各工具共用同一份物理实现。
- `model.mjs`：8 种布局模板（平铺 / 金字塔 / 走廊 / 散点 / 菱形 / 双翼 / 蜂窝 / 堡垒）、数字分布与难度分。
- `simulate.mjs`：可通关性策略模拟——复刻引擎物理，对瞄准角度做束搜索（beam search），逐回合克隆重放，确认目标回合内可清空或可存活。
- `generate.mjs`：参数递进生成 120 关（行数 / 数字上限 / 初始球数 / 目标回合 / 补行密度），逐关模拟验证，不达标自动软化重生成；写出 `.levels.json` 与 `solutions.json`（参考解角度）。
- `inject-defaults.mjs`：把 `.levels.json` 前 12 关注入 `index.html` 的包内默认关卡（离线兜底），幂等。
- `verify.mjs`：独立终检（不依赖生成器状态）——契约、结构、布局合法性、唯一性、数量、字节，并用独立实现的贪心求解器复核可通关性。
- `test-parse.mjs`：`syncLevelsFromServer` 解析逻辑单测，覆盖合法批次完整接受与任一非法整批放弃。
- `test-e2e.mjs`：无头 Chrome 真实操作通关第 1 关（按参考解角度驱动真实指针事件）+ 非法 DB 批次拒绝 + 主题推送验证。

```bash
node docs/level-gen/tools/ballz-bounce/generate.mjs
node docs/level-gen/tools/ballz-bounce/inject-defaults.mjs
node docs/level-gen/tools/ballz-bounce/verify.mjs
node docs/level-gen/tools/ballz-bounce/test-parse.mjs
node docs/level-gen/tools/ballz-bounce/test-e2e.mjs
```

## 自查清单

- [x] 单文件零外链：CSS/JS 全内联，无 `http://`、`https://` 与 `//域名` 字样
- [x] 严格模式审计通过：`node audit-gates.mjs ballz-bounce` 0 违规
- [x] 三关检查通过：`node theme-gates.mjs ballz-bounce` 语法 / 外链全绿
- [x] 无 `<h1>`（标题由 App 原生页头承载），卡片式布局（`.wrap` max-width:480px）
- [x] 主题仅由桥控制（`<head>` 运行时，无按钮、无 matchMedia）；canvas 颜色全部经 `PAL` 读取 CSS 变量，`__gOnTheme` 重绘
- [x] 存档键统一在 `sw.ballz-bounce.v1` 命名空间下（存档 / 关卡缓存 / 版本号）
- [x] 375px 无横向溢出；按钮 48px、关卡号码 44px，热区均 ≥44px
- [x] 120 关全部通过硬校验与独立终检，组合唯一，难度 1 → 10 递进，每关经策略模拟确认可通关

# 打地鼠（gameKey：`whack-a-mole`，category 建议：`arcade`）

- 玩法：经典打地鼠闯关，共 120 关。地鼠从洞中随机探出，限时点击敲击得分：普通鼠 +10、黄金鼠 +30、误击炸弹 -15、漏掉地鼠 -5；时间结束时达到目标分即过关，否则可重试。
- 操作：触屏点按冒头的地鼠（洞位热区 ≥44px）；「上一关 / 重玩 / 选关 / 下一关」切换与重置，通关弹出成绩面板。进度（含每关最佳与历史最高分）自动保存并经游戏桥上报成绩。
- 说明：单文件自包含 H5，CSS/JS 全内联、零外链零素材文件；地鼠、洞口、草地、计时条全部 Canvas 自绘。主题只由桥控制（`getTheme` / `__applySmartWashTheme`，无桥恒浅色，无按钮、无 matchMedia），画布颜色经 `PAL` 读取 CSS 变量并在 `__gOnTheme` 钩子中重绘。关卡包内自带 12 关默认，优先使用管理后台下发关卡（DB 下发覆盖本地缓存）。

## 关卡数据格式

`.levels.json` 顶层为数组，每项信封 `{"levelNo","name","difficulty","data"}`，`data` 字段：

| 字段 | 含义 | 取值 |
|---|---|---|
| `name` | 关卡名（与信封一致） | 字符串 ≤32 字 |
| `time` | 本关时长（秒） | 10–60 整数 |
| `holes` | 洞数（决定网格） | 3–9 整数 |
| `speed` | 出鼠间隔（毫秒，越小越快） | 300–1500 整数 |
| `visible` | 地鼠停留时间（毫秒） | 400–2000 整数 |
| `moleRatio` | 特殊鼠（黄金/炸弹）占比 | 0–0.6 |
| `bombRatio` | 特殊鼠中炸弹占比（其余为黄金鼠） | 0–1 |
| `target` | 目标分 | 整数，≤ 理论上限 |
| `variant` | 布局变体（洞数不足满格时决定空位分布） | 0–999999 |

示例：

```json
{ "name": "青草·001", "holes": 3, "time": 20, "speed": 1000, "visible": 1400,
  "moleRatio": 0.1, "bombRatio": 0.2, "variant": 48, "target": 162 }
```

引擎侧 `validData` 逐条校验上述范围与 `target ≤ 理论上限`；`parseLevels` 在任一条非法时**整批放弃**（保留默认/缓存关卡），见解析单测。

## 生成与可通关性验证

生成器 `docs/level-gen/tools/whack-a-mole/generate.mjs`：按关卡号在 1..120 间线性插值参数（洞数分段 3→9、时长 20→50s、出鼠间隔 1000→550ms、停留 1400→860ms、特殊鼠比例 0.10→0.37、炸弹占比 0.20→0.47），`variant` 保证组合唯一。

目标分同时满足两条上界：

- **理论上限**：`出鼠次数 × (普通率×10 + 黄金率×30)`（完美发挥、避开全部炸弹），目标分取该值的 ≤75%；
- **期望可达**：按「命中率随停留时长变化、漏鼠扣分、5% 误点炸弹」的模型计算期望得分，目标分取期望的 ≤85%，并做非递减对齐。

独立校验器 `validate.mjs` 不依赖生成过程，重新读取 `.levels.json` 逐关复核结构、唯一性、数量、字节，并以 400 局蒙特卡洛模拟确认平均得分 ≥ 目标分（实测平均达标率 87.8%、最低 72.0%）。解析口径单测 `parse-test.mjs` 从 `index.html` 抽取真实 `validData` / `parseLevels` / `applyLevels`，覆盖全量合法批次、各类非法单条与「夹带一条非法 → 整批放弃」。

## 自查清单

- [x] 单文件零外链：CSS/JS 全内联，无 `http://`、`https://` 与 `//域名` 字样
- [x] 无 `<h1>`，标题由 App 原生页头承载
- [x] 主题仅由桥控制（`:root` + `[data-theme=dark]` 两块 token，无按钮、无 matchMedia）
- [x] 触屏热区：洞位命中半径 = max(22px, 格宽×0.42)，直径 ≥44px（375px 下格宽 ≈120px）；按钮均 ≥44px
- [x] 375px 无横向溢出：卡片 `.wrap` 最大 480px，Canvas 宽 100%，洞位自适应
- [x] 存档键 `sw.whackamole.v1`，关卡缓存 `sw.whackamole.v1.levels`
- [x] 关卡下发解析任一条非法整批放弃（`parse-test.mjs` 55 项全过）
- [x] 关卡目标分经解析期望 + 蒙特卡洛双重验证（`validate.mjs` PASS，120/120）
- [x] 严格模式审计通过：`node audit-gates.mjs whack-a-mole` 0 违规

## 工具脚本

| 脚本 | 用途 |
|---|---|
| `lib.mjs` | 参数插值、目标估算、模拟与校验共享逻辑 |
| `generate.mjs` | 生成 120 关并写出 `.levels.json`（`--defaults` 打印前 12 关回填用） |
| `validate.mjs` | 独立终检：信封/结构/唯一性/目标可达性 |
| `parse-test.mjs` | `syncLevelsFromServer` 解析逻辑单测（含整批放弃） |
| `smoke.py` | Playwright 冒烟：375px 视口、深色推送、自动敲击通关、模拟桥下发（需 playwright + chromium） |

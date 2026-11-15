# 火柴棒算式（gameKey：`matchstick`，category 建议：`puzzle`）

- 玩法：七段数码管摆出的错误等式（如 `6+4=4`），按关卡要求**移动 / 添加 / 移除指定根数**的火柴使等式成立即过关。移动一根 = 从某字符拆出一根放到另一字符上（或原地换位置）；添加 / 移除则从外部拿入或丢弃。以正确等式为基底做受控扰动生成谜面，求解器验证必存在不超过限定根数的修正方案。
- 操作：点字符块弹出候选形状面板，点候选即变换；移动关卡先点源字符「拆出」，再点目标字符放上（点回原字符可实现原地平移）；「撤销」回退一步，「重置」还原本关；完成后「提交答案」。触屏热区：字符 44×72、候选 56px、按钮均 ≥44px；375px 宽无横向溢出（长算式在卡片内横向滑动）。
- 说明：单文件自包含 H5，纯 DOM + 内联 SVG 自绘（fill=currentColor），零外链零素材文件。颜色全部走 CSS 变量，深浅主题随桥推送自动切换，故不设 canvas 的 `PAL`/`__gOnTheme` 重绘钩子。同一谜面可能存在多个正确解，游戏接受任意满足「使用根数 ≤ 限定」的正确等式。进度（解锁 / 星级 / 累计星数）经 `saveState` 双写本地与账号，过关上报到达关卡与用根数。

## 关卡数据格式

`.levels.json` 顶层数组，每项信封：

```json
{ "levelNo": 1, "name": "入门·01", "difficulty": 1, "data": { "name": "入门·01", "expr": "1+2=4", "op": "move", "count": 1 } }
```

- `expr`：错误起始等式，仅含 `0-9 + - =`，恰好一个等号，无前导零。
- `op`：`move`（移动）/ `add`（添加）/ `remove`（移除）；`count`：允许使用的火柴根数（1-5）。
- 全部 120 关通过硬校验：语法 / 结构 / 起始不成立 / 组合唯一 / 难度单调；并经 BFS 求解器逐关复核最短修正步数 = 允许根数。

## 生成与校验工具

位于 `docs/level-gen/tools/matchstick/`（`docs/` 已被 .gitignore 排除）：

- `model.mjs`：字形段集 / 单根变换（添加、移除、原地平移）/ 等式求值 / 结构校验 / BFS 求解器（带状态上限）。
- `generate.mjs`：12 个难度 tier（项数 2→3、位数 1→2、允许根数 1→3、操作类型权重递进）采样正确等式并施加受控扰动，逆序构造参考解证书，写出 `.levels.json` 与 `solutions.json`。
- `verify.mjs`：独立终检——契约、语法、唯一性、难度单调、参考解重放至成立等式、求解器复核可解性。
- `test-parse.mjs`：`syncLevelsFromServer` 解析逻辑单测，覆盖合法批次完整接受与任一非法整批放弃。
- `test-e2e.mjs`：无头 Chrome 真实点击参考解通关第 1 关（含撤销 / 重置）+ 主题推送 + 375px 溢出检查。

```bash
node docs/level-gen/tools/matchstick/generate.mjs
node docs/level-gen/tools/matchstick/verify.mjs
node docs/level-gen/tools/matchstick/test-parse.mjs
node docs/level-gen/tools/matchstick/test-e2e.mjs
```

## 自查清单

- [x] 单文件零外链：CSS/JS 全内联，无 `http://`、`https://` 与 `//域名` 字样
- [x] 严格模式审计通过：`node audit-gates.mjs matchstick` 0 违规
- [x] 三关检查通过：`node theme-gates.mjs matchstick` 语法 / 外链全绿
- [x] 无 `<h1>`（标题由 App 原生页头承载），卡片式布局（`.wrap` max-width 480px）
- [x] 主题仅由桥控制（`<head>` 运行时，无按钮、无 matchMedia）；纯 DOM/SVG 颜色随 CSS 变量自动切换
- [x] 存档键统一在 `sw.matchstick.v1` 命名空间下（存档 / 关卡缓存 / 版本号）
- [x] 热区 ≥44px；375px 视口实测无横向溢出
- [x] 120 关全部通过硬校验与独立终检：最短解分布 depth1/2/3 = 50/50/20，组合唯一，难度分单调

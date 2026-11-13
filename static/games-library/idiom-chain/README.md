# 成语接龙（gameKey：`idiom-chain`，category 建议：`word`）

- 玩法：给定起始成语，从候选中挑出首字与上一成语尾字相同的成语，限时连续接满目标长度即过关；共 120 关难度递进。
- 操作：点「开始游戏」，在下方候选中点选正确成语；「提示」高亮答案并显示释义；「重玩本关」重置；选错扣一次机会（共 3 次），倒计时归零或机会用完判负。
- 说明：单文件自包含 H5，零外链、零素材文件，纯 DOM 卡片式布局（无 canvas）。内置 1249 条自研四字成语词库（各带一句话释义）；接龙采用**严格同字**规则——下一个成语的首字必须与上一个成语的尾字完全相同，不以同音字替代。第 6 难度档起遮住候选首字，需凭词汇积累判断。

## 玩法规则

1. 每关给出一个起始成语，已自动计入接龙长度。
2. 屏幕上方卡片展示当前接龙链，末尾成语以品牌色高亮；中间提示下一成语必须以哪个字开头。
3. 下方 6 个候选成语中有且仅有 1 个以该字开头（干扰项首字必然不同），点选即可。
4. 点对：接龙 +1、+100 分；点错：扣 1 次机会并抖动提示。每关 3 次机会。
5. 「提示」按剩余次数高亮唯一正确候选，并展示该成语释义（不占用机会）。
6. 在倒计时结束前接满目标长度即过关，结算含时间、剩余机会、剩余提示奖励分。
7. 进度（已解锁关卡 / 各关完成标记 / 累计总分）自动保存，可在 App 端经游戏桥同步。

## 关卡数据

- 包内 `DEFAULT_LEVELS`：前 10 关（`levelNo` 1–10），DB 不可用时兜底。
- DB 下发：启动调用 `GB.getLevels("idiom-chain")`，响应 `{version, levels:[{levelNo,dataJson}]}`；`dataJson` 反序列化为下列结构，**任一条非法整批放弃**（保持默认/缓存不变），合法批次写入 `sw.idiomchain.v1.levels` 并记录版本号。
- 单关数据结构：`{name, start, length, time, hints, mask}`
  - `start`：起始四字成语，必须存在于包内词库
  - `length`：目标接龙长度（含起始成语），3–20
  - `time`：限时秒数，30–600
  - `hints`：提示次数，0–10
  - `mask`：1 = 遮住候选首字（高难），0 = 完整展示
- `.levels.json` 为顶层 JSON 数组，信封 `{levelNo,name,difficulty,data}`；共 120 关、10 个难度档，`start+length` 组合全局唯一。

## 存档与桥

- 存档键：`sw.idiomchain.v1`（进度）、`sw.idiomchain.v1.levels` / `.levels.ver`（关卡缓存）
- 进度经 `saveState` 持久化，成绩经 `reportScore` 上报（metricValue = 已过关数，metricExtra = 累计总分）
- 主题只信桥：`getTheme()` 初值 + `__applySmartWashTheme(mode)` 推送；DOM 颜色随 CSS 变量自动生效，无画布重绘

## 生成与校验工具

位于 `docs/level-gen/tools/idiom-chain/`（工作文档，不入库）：`build.mjs` 注入词库、`generate.mjs` 生成关卡、`validate.mjs` 独立终检、`parse-test.mjs` 解析器单测、`analyze.mjs` 词库/图分析、`browser-check.mjs` 无头浏览器实开验证。详见该目录 README。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片 / 素材文件
- [x] 单文件自包含，体积 85KB 量级
- [x] 375px 视口无横向溢出（无头 Chrome 实测 scrollWidth = 375）
- [x] 交互热区 ≥44px（候选按钮实测 52px）
- [x] 主题仅由桥控制：无按钮、无 matchMedia、无 localStorage 主题记忆
- [x] 不自绘标题（无 `<h1>`，原生页头唯一承载）
- [x] 严格模式审计 0 违规；零外链 grep 零命中
- [x] 全部 120 关经独立图搜索验证可通关；最高难度（长度 14、遮字、无提示）实开通关

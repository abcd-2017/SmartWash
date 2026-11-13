# 落块达人（gameKey：`tile-tap`，category 建议：`arcade`）

- 玩法：4 轨竖向落块（别踩白块式）。黑块自上而下滑落，每行恰有一个黑块；点中黑块即前进一行并得分（100 起，连击加成），全部点完 `rows` 行即过关。踩到白块、或黑块完全落出底线（漏接）立即失败。速度随关卡由 300 递进到 860 px/s（参考轨道高 600px，实际按屏高缩放），目标行数由 6 递进到 42，共 120 关。
- 操作：点击画布对应轨道（375px 宽时单轨约 90px 热区，远大于 44px）；键盘 `D F J K` 对应四轨，空格暂停/继续；切后台自动暂停。
- 说明：单文件自包含 H5，canvas 自绘零外链零素材文件。**已接入游戏数据平台化**：存档经桥代理跟随账号（本地 + 账号双写，结构 `{lv,done,best}`，best 为各关最佳得分之和，上报 `metricValue=总积分`、`metricExtra=到达关卡`）；关卡支持后台热更新（DB 下发 `{name,rows,speed,seed}` + 本地缓存 `sw.tiletap.v1.levels` + 包内 12 条默认关兜底，任一条非法整批放弃）。无桥环境自动降级为纯本地。主题为 D5 终版（`:root` 浅色 + `[data-theme=dark]` 深色，桥唯一控制，无切换按钮/无 matchMedia；canvas 颜色全部经 PAL 读 CSS 变量，`__gOnTheme → palLoad()` 重绘）。

## 关卡数据与生成

- 数据格式：`{name, rows, speed, seed}`——目标行数 / 下落速度(px/s @ 参考高 600) / 确定性种子。
- 生成：`docs/level-gen/tools/tile-tap/generate.mjs`（参数线性递进 + 逐关挑选种子保证组合唯一）；同轨最多连出 2 次，行数 ≥8 时四轨均出现、单轨占比 ≤60%。
- 校验：`validate.mjs` 独立复核结构/取值/唯一性/数量/字节、种子可复现（同种子两次生成逐位一致）、黑块行覆盖合法、引擎与生成器块序交叉一致、行间隔与点按窗口可达；`parse-test.mjs` 对 `syncLevelsFromServer` 解析逻辑做单测（含非法输入整批放弃）。

## 自查清单

- [x] 单文件零外链：CSS/JS 全内联，无 `http://`、`https://` 与 `//域名` 字样
- [x] 严格模式审计通过：`node audit-gates.mjs tile-tap` 0 违规（无 h1、字号八档、圆角档位、热区 ≥44px、存档键规范、token 无自造）
- [x] 主题门禁通过：`node theme-gates.mjs tile-tap` 语法 / 外链两关全绿
- [x] 375px 无横向溢出：卡片式 `.wrap` max-width 480px，画布宽度 100%
- [x] 主题桥接运行时已内置：head 内 IIFE 与范例一致，深色推送经 `__gOnTheme → palLoad()` 重绘
- [x] 120 关全部通过独立校验（结构 / 唯一性 / 种子可复现 / 分布合理 / 字节上限）
- [x] 包内默认关 12 条与 `.levels.json` 前 12 条逐字一致
- [x] Playwright 冒烟（375×667）：零 JS 异常、键盘通关第 1 关（6/6 得分 810）、踩白块判负并可重试、暂停冻结/继续滚动、深浅主题推送与 theme-color 同步、刷新后进入已解锁第 2 关

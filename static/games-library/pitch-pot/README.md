# 投壶（gameKey：`pitch-pot`，category 建议：`arcade`）

- 玩法：传统投壶：拖拽蓄力把箭抛向远处铜壶，按落点计分——壶口 10 分、壶耳 6 分、触沿 2 分，连续命中叠 bonus；限定箭数内达到目标分过关。
- 操作：在画布左下半区按住并向左下拖动瞄准（虚线为预估弹道），松手投掷；拖得越远力度越大，风向会推偏箭矢。按钮：重玩本关 / 选关。
- 说明：单文件自包含 H5，卡片式布局（原生页头承载标题，画面内不自绘标题）。自研确定性抛物线（固定步长积分 + 重力 + 可选风力，同参数可复现）；包内 10 关默认，支持 DB 下发 120 关、进度存档与成绩上报。

## 关卡与数值

- 关卡数据：`{name, arrows, distance, wind, target, bonus}`。
  - `arrows` 箭数 3→10；`distance` 壶心距发射点 205→354；`wind` ∈ [-1,1]（负逆风/正顺风）；
    `target` 目标分；`bonus` 连中加成（0→4）。
  - 计分：命中壶口 10 / 壶耳 6 / 触沿 2；第 k 次连中额外加 `bonus×(k-1)`。
- 满分行口全中总分 `M = arrows×10 + bonus×arrows×(arrows-1)/2`；目标分取 `M` 的 0.38→0.66 递增比例，保证可达成且留有挑战。
- 可通关性由工具逐关求解「壶口命中」拖拽参数，并以同一确定性参数重复投掷模拟验证 `M ≥ target`。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 严格模式审计通过：`node audit-gates.mjs pitch-pot`（运行时/布局/token/热区/存档/外链全项）
- [x] 三关通过：`node theme-gates.mjs pitch-pot`；375×812 实开无横向溢出、可真实命中过关
- [x] 主题：token 两块 CSS + head 运行时（无按钮、无 matchMedia）；canvas 颜色全部经 PAL 读 CSS 变量，`__gOnTheme` 重绘
- [x] 存档键 `sw.pitchpot.v1`；关卡缓存 `sw.pitchpot.v1.levels`；`GB.getLevels("pitch-pot")` 整批替换，任一条非法整批放弃
- [x] 关卡终检：`docs/level-gen/tools/pitch-pot/validate.mjs`（结构/唯一性/数量/字节/可通关复现）

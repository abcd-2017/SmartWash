# 盆景剪枝（gameKey：`bonsai-trim`，category 建议：`other`）

- 玩法：一棵随机长成的盆景，虚线圈是要保留的造型轮廓；点枝条把整段分枝一刀剪除，用最少刀数让剩下的树与轮廓完全吻合——剪在分枝根部才省刀，剪深了会留残枝。
- 操作：触屏点枝条节点剪除（整段子树）；键盘方向键移光标、Enter 剪枝、Z 撤销（接回枝条）、H 提示（直接剪掉一处待剪分枝）、R 重开。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；40 关内嵌，刀数预算=树 DP 最优解 +2，纯事件驱动无实时压力。

## 关卡验证

- 内嵌 40 关，每关结构 `{name,w,h,cells[[x,y,parentIdx]],target[节点下标],budget,par,difficulty}`；`cells` 父节点恒在前，目标轮廓=随机摘除 k 个**完整子树**后的剩余节点（保证轮廓可达）。
- 验证方式：游戏内嵌**独立树 DP 求解器** `BT.minCuts`（后序计算子树是否含保留节点，最少刀数=挂在「有保留枝节点」下的最大无保留子树个数），`BT.validate` 复核结构（父序/占位唯一/目标子集）+ 重算 par + budget≥par；`BT.matched` 以「cutMark[i] ⇔ 非目标节点」全等判定过关。
- 锚点用例（`window.__validateLevels()` 共 6 条，node 抽取 XC-CORE 复现）：摘一叶 par=1（精确）、全保留不成关（why=full）、双枝全摘 par=2（精确）、深剪留残枝不吻合（matched=false）、40 关结构+可解校验、生成逐字节确定。
- 生成与验证结果：40/40 关通过（fail 空），par 分布 1×10 / 2×11 / 3×10 / 4×9，未触发兜底关。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 `--g-*` / `--g-st-*` token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作；按钮热区 ≥44px
- [x] 纯事件驱动（rAF 仅重绘不推进状态），切后台 visibilitychange 停帧/续跑；过关判定在剪枝动作内同步触发，无延迟回调竞态
- [x] 存档键 `sw.bonsaitrim.v1`（续玩从 `save.lv` 起步）+ `.levels`/`.ver` 缓存；无桥自动降级 localStorage
- [x] 关卡驱动：40 关内嵌 + 独立树 DP 验证器（含锚点用例）；audit-gates 通过（0 违规）；theme-gates 全绿

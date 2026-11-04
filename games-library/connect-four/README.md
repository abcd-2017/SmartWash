# 四子棋（gameKey：`connect-four`，category 建议：`board`）

- 玩法：经典四子棋，7 列 × 6 行。你执黄先行，AI 执蓝后手，棋子从顶部落下，横、竖、斜任意方向先连成四子者获胜，棋盘下满为平局。
- 操作：点击棋盘所在列落子，指针移动可预览落点与列高亮；键盘 ← → 选择列，↓ 或空格落子。
- 说明：单文件自包含 H5。AI 采用 minimax + alpha-beta 剪枝（搜索深度 4），落子前思考 300–500ms；胜场 / 负场 / 当前连胜经 localStorage 本地保存，负场清零连胜，平局保留。

## 自查清单

- [x] 单文件零外链：CSS/JS 全内联，无 `http://`、`https://` 与 `//域名` 字样
- [x] 内联语法通过：`node theme-gates.mjs` 语法 / 外链两关全绿
- [x] 375px 无横向溢出：canvas 宽度由父容器实测宽度计算（含 dpr 处理）
- [x] 主题桥接运行时已内置：head 内 IIFE + `window.__gOnTheme` 钩子
- [x] AI 深度 4 alpha-beta：抽验「胜着」与「堵截」均选对列

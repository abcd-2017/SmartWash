# 空当接龙（gameKey：`solitaire`，category 建议：`board`）

- 玩法：经典 Klondike 翻一张：52 张牌洗匀，7 列牌堆第 i 列 i+1 张、顶牌明置；点牌库翻一张到废牌堆，牌库空后点回重洗循环。牌堆间按红黑交替、点数递减叠放，A 起同花色递增收进 4 个 foundation，四方收满即胜。
- 操作：点牌库翻牌；点废牌顶或牌堆明牌选中（高亮描边），再点目标牌堆或 foundation 完成移动；双击牌自动进 foundation；空列仅可放 K；「重开」洗牌新局。
- 说明：单文件自包含 H5，CSS/JS 全内联、零外链；DOM 牌面，红色用 `--g-danger`、黑色用 `--g-text`、牌面白底 `--g-surface`；计时切后台自动暂停；存档键 `sw.solitaire.v1`，记录场次 / 胜场 / 最佳用时。

## 自查清单

- [x] 单文件零外链：CSS/JS 全内联，无 `http://` / `https://` 与域名
- [x] 内联语法通过：theme-gates `new Function` 解析全 PASS
- [x] 375px 无横向溢出：7 列各约 44px 均分，牌面按列宽百分比定位叠放
- [x] 主题桥接运行时已内置：head 内 IIFE，桥唯一控制、无桥恒浅色
- [x] 点选 + 双击操作：选中高亮描边，双击自动进 foundation

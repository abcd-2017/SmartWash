# 接蛋篮（gameKey：`egg-catch`，category 建议：`arcade`）

- 玩法：楼上不断抛下鸡蛋，拖动底部篮子接住。鸡蛋越来越快，风向每隔几秒变化，接住得分，连中有加成，漏蛋扣生命。
- 操作：触屏按住篮子左右拖动；键盘 ←/→ 移动篮子，空格开始。
- 说明：单文件自包含 H5，canvas 自绘，零外链；最高分经 GB 桥上报（desc），无桥降级 localStorage。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片
- [x] 单文件 ≤10MB；canvas 颜色全部经 PAL 读 `--g-*`，深色的由桥推送
- [x] 触屏 + 键盘双操作；含重开、提示；分数 GB 上报
- [x] audit-gates 严格模式 0 违规；theme-gates 全绿

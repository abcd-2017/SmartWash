# 记忆翻牌（gameKey：`memory-cards`，category 建议：`puzzle`）

- 玩法：N×2 牌阵翻牌配对，共 6 关（3/4/6/8/10/12 对，列数 3→6 递进）。每次翻开两张：图案一致即配对成功并保持点亮（绿色描边脉冲），不一致约 750ms 后自动盖回。全部配对即通关，统计步数与用时；理论最少步数 = 对数。关卡顺序解锁，每关记录最少步数与最短用时（键 `sw.memory.v1`，localStorage try/catch 包裹）。
- 操作：点按翻牌，全程单指可玩；卡片 ≥44px（375px 宽 6 列时约 51×68px）。「重开本关」随时重置本关牌局（重新洗牌）。
- 说明：12 种图案（太阳/月亮/星星/爱心/云朵/水滴/松树/花朵/小鱼/小鸟/蘑菇/雪花）与牌背水滴气泡纹均为原创内联 SVG，颜色走 `var(--g-*)` CSS 变量随主题自动切换，无需重渲染；翻牌为 CSS 3D rotateY 动画。每关随机抽取图案子集并 Fisher-Yates 洗牌，重复游玩不重样。玩法参考经典记忆配对规则自研实现，未使用任何原型代码。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，全部图形为内联 SVG，零 CDN / 字体 / 图片（`grep -nE 'https?://'` 零命中）
- [x] 触屏可完整通关一遍：翻牌 → 配对/盖回 → 通关 → 下一关全流程触屏可达
- [x] localStorage（关卡进度/最佳步数/最短用时）全部 try/catch 包裹，键名 `sw.memory.v1`
- [x] 375px 宽不溢出：牌阵 grid 自适应（1fr 列），关卡条横向滚动，按钮 44px 高
- [x] 主题机制按 GAME-THEME.md §4/§5（D5 终版）：仅 `:root` + `[data-theme=dark]` 两块 CSS，`<head>` 运行时读桥，`__applySmartWashTheme` 推送生效；无 matchMedia、无切换按钮、无 `g-theme`；SVG fill/stroke 全部 `var(--g-*)`
- [x] `<script>` 提取过 `node --check`，无 console 残留，无 emoji，UTF-8 单文件约 16KB

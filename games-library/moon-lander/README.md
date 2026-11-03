# 月面轻降（gameKey：`moon-lander`，category 建议：`physics`）

原创实现的月面着陆物理游戏（玩法参考原型 `mini-browser-games/moon-lander.html`，代码全部重写，与原型无代码继承）。

- 玩法：
  - 着陆舱受重力 + 横向漂移 + 周期横风影响，燃料有限（按住点火才消耗），需控制垂直速度、水平速度与倾角，在指定平台软着陆。
  - 判定阈值：**垂直速度 ≤46、水平速度 ≤30、倾角 ≤28°**，且必须落在平台宽度内；触地超限、砸到月面或飞出空域即坠毁，结算给出具体失败原因。
  - 站台无限递增、难度递增：重力 26→46、横风 2→16、平台宽 160→66、燃料 100→70（第 L 站参数随种子生成，地形与平台可复现）。
  - 成功结算：得分 = 400 + 剩余燃料×4 + 软着陆精度加成 + 站台系数；**最高解锁站台（`g_moonlander_level`）与历史最佳（`g_moonlander_best`）存 localStorage**（try/catch），可跨会话续降。
- 操作：**按住屏幕任意处或中央「点火」键喷射减速**（火焰 + 尾焰粒子）；按「‹ 左转 / 右转 ›」键修正姿态（也可用键盘 A/D、方向键、空格）；HUD 实时显示高度 / 垂直 / 水平 / 倾角（超阈值变红）、燃料条与横风箭头，平台偏离时给出方向提示；切后台自动暂停。
- 说明：单文件自包含 H5；基于 dt 的双子步积分（重力 / 推力 / 风）；着陆舱、地形为清氧色板派生 token（`--g-sky1/--g-sky2/--g-moon/--g-moon2/--g-water`，深色有对应暗色变体），canvas 颜色全部经 `PAL` 从 CSS 变量读取。
- 主题：清氧色板（GAME-THEME.md §2）；深浅色按 **D5 裁定（二修）由桥唯一控制**——`<head>` 运行时读 `SmartWashGameBridge.getTheme()`，App 推送 `__applySmartWashTheme(mode)` 切换并经 `__gOnTheme` 触发 `palLoad()` + 重绘；无桥恒浅色；无手动切换按钮、无 `g-theme`、无 matchMedia 层。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片 / SVG 外链，画面纯 Canvas 绘制（`grep -E 'https?://'` 零命中）
- [x] 触屏可完整通关一遍：画布按住点火 + 左转/右转/点火三键（64px 高）、选站步进、成功「下一站/重飞/选站」、坠毁「重试/选站」全流程触屏可达（真实浏览器实测点火火焰与粒子；无头自动驾驶脚本实际着陆成功解锁第 2 站，证明阈值可达成）
- [x] 深浅色都验证：真实浏览器实测浅色 + `__applySmartWashTheme('dark')` 推送深色，天空/月面/平台/遥测重绘正常，`meta[name=theme-color]` 同步 `#141614`
- [x] 375px 不溢出：375×667 视口实测 `scrollWidth == 375`，画布按 `min(100%, (100dvh−320px)×0.75)` 自适应，结算浮层全宽完整可见
- [x] `node --check` 全部 `<script>` 语法通过；`theme-gates.mjs` 三关通过（26.5KB ≤ 1MB）
- [x] 切后台自动暂停；localStorage 读写 try/catch；无 console / debugger / alert 残留

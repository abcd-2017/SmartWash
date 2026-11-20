# 菜园保卫（gameKey：`lane-defense`，category 建议：`arcade`）

- 玩法：三车道菜园布防：阳光经济（天降 + 向日葵产出）种植豌豆 / 三连射手、坚果墙，抵御逐波右进入侵者（普通 / 快速 / 坦克逐怪混编），入侵者触底扣生命，清完全部波次过关。
- 操作：点植物卡（或键盘 1-4）选中，再点格子种下；X 选铲子移除；方向键移动光标、回车 / 空格种下；点阳光气泡收取（2.5s 后自动兜底）。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）。40 关由种子生成器确定性生成（波数 2→6、每波 4→14 只、gap≥2.6s、血量 / 速度全局乘子递增），出怪时间表由种子驱动（车道逐怪随机）。**可通关性验证 = 贪心布防策略确定性模拟**（建造队列 + 被吃重建 + 阳光延迟 1s 收取，与游戏共用同一核心），40/40 全胜；调试入口 `window.__validateLevels()` 复现（node 抽取 XC-CORE 段独立运行）。关卡缓存 `sw.lanedefense.v1.levels`，DB 下发整批替换（模拟不过整批放弃）；存档 `sw.lanedefense.v1` 双写，过关上报到达关卡（desc，附剩余生命）。

## 关卡与验证

- 关卡数据：`{name, no, sun0, hpm, spdm, waves:[{n, kind, gap, delay}], seed}`。
- 可通关性：贪心策略（向日葵→豌豆→坚果→三连→补阳光→补豌豆，首个可负担项扫描）确定性模拟 40/40 全胜。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量（恒白高光例外）
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 深浅色双主题；存档键 `sw.lanedefense.v1`；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval

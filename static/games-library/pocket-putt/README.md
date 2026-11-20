# 袖珍推杆（gameKey：`pocket-putt`，category 建议：`arcade`）

- 玩法：掌上迷你推杆，按住球场向目标方向拖动、松手击球；球撞墙与边界反弹，途中穿过星星加分，进洞过关，杆数超出标准杆 3 杆即失败。
- 操作：触屏按住拖动瞄准击球，键盘 ←/→ 调角度、↑/↓ 调力度、空格击球；「选关」可进入 40 关面板。
- 说明：单文件自包含 H5，卡片式布局。40 关由种子确定性生成起点/球洞/隔板，内置 `window.__validateLevels()` 用 36 向 × 3 档力度暴力采样验证至少一杆可进洞（40/40 可通关）；星星沿解轨迹布点，可吃。存档键 `sw.pocket-putt.v1`（含关卡缓存），过关上报总积分（desc）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 --g-* / --g-st-* token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 深浅色双主题；暂停 / 重开 / 结束面板齐全
- [x] JS 语法 node new Function 通过；grep 零 http/https；无 document.write / eval
- [x] 关卡 40 关内嵌，`window.__validateLevels()` 返回 `{total:40,solvable:40,fail:[]}`

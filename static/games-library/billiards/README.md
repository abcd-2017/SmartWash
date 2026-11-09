# 粉笔台球（gameKey：`billiards`，category 建议：`board`）

原创实现的竖屏台球物理游戏（玩法参考原型 `mini-browser-games/chalk-billiards.html`，代码全部重写，与原型无代码继承）。

- 玩法：
  - **清台闯关**：每关随机摆 N 颗编号目标球（3 → 8 随关卡递增），杆数有限（球数+2），全部打进袋口过关，剩余杆数折算清台奖励并解锁下一关；杆数用尽即失败，可重试本关。
  - **限时挑战**：60 秒内连续进球冲分，进球 +100、连续进球有连击加成（最多 +100），清空球架补架 +200，最佳分存档。
  - 目标球入袋 +100；**母球落袋罚 50 分**并在置球点重摆（占用本杆）。
- 操作：在母球附近（约 56px 容差）按住并向后拖拽——显示虚线瞄准线、幽灵球落点、目标球预测方向线、球杆后拉与环绕母球的力度环（百分比），松手出杆；拖距越远力度越大。球-球近似弹性碰撞、库边反弹、滚动摩擦指数减速。
- 说明：单文件自包含 H5；固定 1/120s 子步物理（基于 dt 累积器）；最高关卡 / 闯关最高分 / 限时最佳存 localStorage（键 `g_billiards_unlock`、`g_billiards_best_run`、`g_billiards_timed_best`，读写均 try/catch）；切后台自动暂停；球体彩色为玩法素材色（D2 保留字面），其余 canvas 颜色全部经 `PAL` 从 CSS 变量读取。**已接入游戏数据平台化**：存档经桥代理跟随账号（本地 + 账号双写，账号更优才覆盖本地）；上报最高关卡进排行榜（desc，附限时最佳）。无桥环境自动降级为纯本地。
- 主题：清氧色板（GAME-THEME.md §2）；深浅色按 **D5 裁定（二修）由桥唯一控制**——`<head>` 运行时读 `SmartWashGameBridge.getTheme()`，App 推送 `__applySmartWashTheme(mode)` 切换并经 `__gOnTheme` 触发 `palLoad()` + 重绘；无桥恒浅色；无手动切换按钮、无 `g-theme`、无 matchMedia 层。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片 / SVG 外链，画面纯 Canvas 绘制（`grep -E 'https?://'` 零命中）
- [x] 触屏可完整通关一遍：Pointer 拖拽瞄准出杆、菜单/重开/暂停按钮（≥48px）、过关进下一关、限时模式计时结算，全流程触屏可达（真实浏览器 375px 实测：出杆扣杆 5→4、限时到点结算、自动驾驶脚本通关验证）
- [x] 深浅色都验证：真实浏览器实测浅色 + `__applySmartWashTheme('dark')` 推送深色，台呢/球/文字重绘正常，`meta[name=theme-color]` 同步 `#141614`
- [x] 375px 不溢出：375×667 视口实测 `scrollWidth == 375`，台桌按 `min(100%, (100dvh−250px)×0.588)` 自适应整屏无滚动
- [x] `node --check` 全部 `<script>` 语法通过；`theme-gates.mjs` 三关通过（27.7KB ≤ 1MB）
- [x] 切后台自动暂停；localStorage 读写 try/catch；无 console / debugger / alert 残留

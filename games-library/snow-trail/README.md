# 雪岭滑行（gameKey：`snow-trail`，category 建议：`arcade`）

原创实现的竖屏滑雪竞速（玩法参考 `mini-browser-games/snow-ridge.html` 的滑雪题材，代码全部自写）。

- 玩法：竖屏无尽雪道，速度随距离渐增（250→630px/s + 冲刺）；穿过旗门 +100 并累积连击（连续穿门最高 5 倍），错过旗门重置连击；驶入加速带获得 1.6s 冲刺 +40 分；撞上树木 / 岩石立即结束；得分 = 距离（米）+ 旗门与加速奖励。
- 操作：手指在画布内左右拖动控制滑手走位（跟随手指横移），桌面支持 ←→ 键与空格（开始 / 暂停）；HUD 暂停按钮 44px；切后台（`document.hidden`）自动暂停。
- 说明：单文件自包含 H5；最高分 localStorage（`snow-trail-best`，读写均 try/catch）；主题按 D5 终版裁定由桥唯一控制（`getTheme` 初值 + `__applySmartWashTheme` 推送，'system'/空串/无桥=浅色），无 matchMedia 层与手动切换钮；canvas 颜色全部经 PAL 对象读 CSS 变量，主题切换经 `__gOnTheme → palLoad()` 实时重绘。

## 与原型的机制差异（复刻取舍）

原型 `snow-ridge.html` 是 12 赛段横版物理竞速（跳台起跳 / 空中旋转翻转 / 落地判定 / 雪崩追逐 / 三选角色雪板 / 档案码）。本作为竖屏走位躲避向：去掉跳跃 / 空翻 / 雪崩 / 装备与赛段系统，保留「速度渐增 + 障碍躲避 + 收集得分」核心节奏，新增旗门连击与加速带作为得分手段。

## 自查清单

- [x] 无外部请求：CSS/JS/SVG 全内联，零 CDN / 字体 / 图片 / 外链（`grep -E 'https?://'` 零命中，静态校验通过）
- [x] 触屏可完整游玩一遍：拖动走位、开始 / 暂停 / 继续 / 结束重开全流程触屏可达，控件均 ≥44px（代码走查覆盖）
- [x] 深浅色都验证过：`:root` 浅色 + `[data-theme=dark]` 深色两层 CSS（无 matchMedia 层），模拟桥推送切换后 data-theme / meta theme-color / canvas 重绘均实测生效
- [x] 375px 宽不溢出：画布随容器 min(100%, 420px) 自适应，DPR 缩放，固定元素仅右上角 44px 圆钮（代码走查覆盖）
- [x] localStorage 读写全部 try/catch；运动更新全部基于 dt（帧率无关）；无 console 残留

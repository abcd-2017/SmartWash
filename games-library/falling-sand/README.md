# 元素落沙（gameKey：`falling-sand`，category 建议：`other`）

- 玩法：240×400 元胞自动机沙盒。从底部元素栏选沙 / 水 / 石 / 火 / 植物 / 油 / 岩浆 / 烟 / 橡皮，在画布拖拽倾倒；元素按各自规则运动（沙沉降、水流动铺展、火焰上升燃尽、蒸汽/烟上浮消散、岩浆迟滞结壳、植物静止），双元素接触触发反应：水+火→蒸汽、水+岩浆→岩石+蒸汽、沙+水→湿沙、水+植物→生长、火/岩浆+油→点燃、火/岩浆+植物→燃烧、岩浆+沙→玻璃、蒸汽冷凝成雨，共 9 种反应，页脚实时记录发现进度（localStorage 持久化）。
- 操作：手指在画布拖拽倾倒（多指同时、笔迹插值补点），底部横滚元素栏切换（每项 ≥44px 触控），顶部细/中/粗三档笔刷，清屏 / 暂停按钮；切后台自动暂停、回前台自动恢复。
- 说明：单文件自包含 H5；typed array 网格 + 自底向上交替方向扫描 + moved 标记防双移；渲染走离屏 ImageData 直写 + 最近邻放大；主题为 D5 终版（`:root` 浅色 + `[data-theme=dark]` 深色，桥唯一控制，canvas 颜色零字面量全部经 PAL 读 CSS 变量）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片 / 音频，画面纯 Canvas（`grep -E 'https?://'` 零命中）
- [x] `<script>` 提取过 `node --check` 语法检查
- [x] 触屏可完整玩一遍：倾倒 / 换元素 / 换笔刷 / 清屏 / 暂停全部触屏可达
- [x] 触控目标 ≥44px（元素 chip 46px、笔刷段 44px、按钮 44px）
- [x] 375px 竖屏自适应：画布按 stage 剩余空间保持 3:5 自适配，DPR 缩放
- [x] 切后台 `document.hidden` 自动暂停模拟，回前台恢复（手动暂停过则不抢恢复）
- [x] 运动基于 dt：固定 1/60s 步长 + dt 累积器（每帧最多 3 步防螺旋）
- [x] localStorage 读写均包裹 try/catch（键 `sw_h5_fallsand_prefs`）
- [x] 无 console 残留；D5 主题：无 matchMedia / 无切换按钮 / 无 g-theme，桥 + `__applySmartWashTheme` 推送经 `__gOnTheme → palLoad()` 重绘
- [x] 元素栏横滚区 `[hidden]{display:none!important}` 兜底；入场动效仅 transform（无 opacity:0 起帧）

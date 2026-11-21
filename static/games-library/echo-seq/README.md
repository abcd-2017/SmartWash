# 回声序列（gameKey：`echo-seq`，category 建议：`other`）

- 玩法：四块彩色音块按随机序列逐一点亮（每轮加一步、节奏逐渐加快），看完整段后按原顺序复现，记错任意一步即结束。
- 操作：触屏点音块复现，点「开始挑战 / 再来一局」开局；键盘 1-4 对应四块音块、空格开始或重开、M 静音。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；WebAudio 合成音效（失败静默、可一键静音）；切后台播放自动中断、回前台自动重播本序列。

## 分数上报

- 分数口径：**完成轮数**（完整复现的序列长度），`desc` 越大越好；存档 `sw.echoseq.v1` 记录历史最佳。
- 上报时机：记错结束时 `GB.reportScore("echo-seq",{metricValue:历史最佳,metricExtra:本局轮数})`；存档双写（账号优先 + localStorage 兜底），无桥自动降级可玩。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片
- [x] 单文件 ≤10MB；颜色全部走 `--g-*` / `--g-st-*` token（四音块为素材色），深浅双主题由桥推送
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px（音块 112px）
- [x] 计时/播放类：切后台（visibilitychange）自动中断并在回前台重播
- [x] WebAudio 音效 try/catch 静默降级；prefers-reduced-motion 动效降级
- [x] audit-gates 通过（0 违规）；theme-gates 全绿

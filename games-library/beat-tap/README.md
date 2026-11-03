# 节拍点按（gameKey：`beat-tap`，category 建议：`arcade`）

- 玩法：竖屏四轨下落式音击。3 首固定谱（晨露 96 BPM / 午后气泡 118 BPM / 霓虹疾走 140 BPM）由种子固定的 mulberry32 按 BPM 程序化生成——音符即旋律（轨道固定五声音阶音高），鼓组（底鼓/军鼓/踩镲）与贝斯（Am-F-C-G 进行）由 WebAudio 振荡器 + 噪声 + 包络现场合成，零外部音频文件。音符落至判定线时点击对应轨道：±60ms Perfect（300 分）、±120ms Good（150 分）、超窗未击为 Miss；连击加成（每连击 +2/+1，封顶 50），空击不惩罚。结算按加权准确率评级 S≥95% / A≥85% / B≥70% / C，最高分与评级按曲存 localStorage。
- 操作：底部 D/F/J/K 四个 ≥58px 触控垫（支持多指），或直接点画布对应轨道；键盘 D F J K 击打、空格暂停、Esc 返回选曲；暂停经 `AudioContext.suspend()` 冻结音频时钟，回前台/点继续无缝续奏不丢判定；切后台自动暂停（音频挂起）。
- 说明：单文件自包含 H5；游戏时钟取自 `audioCtx.currentTime`（音符画面位置由时间差推导，无累积漂移），音频调度器 30ms lookahead；WebAudio 在首次用户手势后创建/resume。主题为 D5 终版（`:root` 浅色 + `[data-theme=dark]` 深色，桥唯一控制，canvas 颜色零字面量全部经 PAL 读 CSS 变量）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，音乐纯 WebAudio 合成（`grep -E 'https?://'` 零命中）
- [x] `<script>` 提取过 `node --check` 语法检查
- [x] 触屏可完整通关一遍：选曲 → 击打 → 暂停/继续 → 结算 → 重开/返回全流程触屏可达
- [x] 触控目标 ≥44px（轨道垫 58px、头部按钮 44px、曲目卡 64px）
- [x] 375px 竖屏自适应：flex 布局 + 画布随 stage 自适应，DPR 缩放
- [x] 切后台 `document.hidden` 自动暂停且音频挂起（suspend 冻结时钟）
- [x] 运动基于 dt：音符位置由音频时钟推导，粒子/飘字/闪光按 dt 更新
- [x] localStorage 读写均包裹 try/catch（键 `sw_h5_beattap_records`）
- [x] WebAudio 在首次用户手势（选曲/轨道垫/画布）后才创建并 resume
- [x] 无 console 残留；D5 主题：无 matchMedia / 无切换按钮 / 无 g-theme，桥 + `__applySmartWashTheme` 推送经 `__gOnTheme → palLoad()` 重绘
- [x] `[hidden]{display:none!important}` 兜底（overlay/panel 均为 flex 类）；结算入场动效仅 transform（无 opacity:0 起帧）

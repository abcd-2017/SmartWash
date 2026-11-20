# 密码推理（gameKey：`lock-combo`，category 建议：`puzzle`）

- 玩法：一组同时为真的条件线索（位值 / 奇偶 / 大小 / 含与不含 / 求和 / 重复 / 极值 / 位序）唯一锁定一组三位密码，调出答案验证；猜错会标红违反的线索辅助消元，3 次不中本关失败。
- 操作：触屏 +/− 调三位数字、点数字格选位，「验证密码」提交；键盘 ←→ 选位、↑↓ 加减、直接按数字键快速填位、回车验证；切后台自动暂停。
- 说明：单文件自包含 H5（纯 DOM，无 canvas），卡片式布局（标题由 App 原生页头承载）；40 关内嵌（数字域 1-5→1-9、线索 2→6 条递进，10 关后移除直给线索），过关上报到达关卡（desc，metricExtra 为累计星数）；存档 `{lv, done:{关卡:星}, best}` 本地 + 账号双写；无 WebAudio。

## 关卡验证

- 关卡结构 `{name, no, seed, hi, code, clues[]}`，由确定性种子生成（贪心加入真线索直至全枚举解空间收敛到唯一，gen 内自愈重试）。
- 验证方式：内嵌**独立验证器** `GEN.validate`：线索结构合法性核对 + 对 1..hi 全部三位组合暴力枚举，复核满足全部线索的解恰为一组且等于密码；`GEN.validateLevelsAll` 逐关复核，页面暴露 `window.__validateLevels` 可复跑。
- 验证结果：40/40 关通过（无 fails）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片
- [x] 颜色全部走 `--g-*` token（纯 DOM，无 canvas 调色板），深浅双主题由桥推送
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅双主题样式随桥切换
- [x] 存档键 `sw.lockcombo.v1`（+ `.levels` / `.levels.ver`）；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；40 关独立验证 40/40 通过；theme-gates / audit-gates 全绿

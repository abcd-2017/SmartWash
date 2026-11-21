# 接力交棒（gameKey：`relay-baton`，category 建议：`arcade`）

- 玩法：4×50m 四棒接力，自动奔跑无速度操控；进入交接区（浅绿段）后摆针开始摆动，在绿窗按键=完美交接（0 罚时），黄窗=安全交接（+0.45s/棒），白区按键或跑满未按键=掉棒直接失败；四棒总用时 ≤ 目标时间过关。
- 操作：触屏点画布 / 键盘空格或回车完成交接（其余时间无操作，专注时机判定）；「重跑 / 选关」工具按钮；切后台自动暂停。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；40 关内嵌（配速 8.6→10.2 m/s、摆针周期 0.95s→0.60s、绿窗 30°→12° 递进），过关上报到达关卡（desc，metricExtra 为累计星数）；存档 `{lv, done:{关卡:星}, best}` 本地 + 账号双写；交接窗口时机玩法为合集独有。

## 关卡验证

- 关卡结构 `{name, no, seed, legs[4], T, greenW, amber, target}`，确定性种子生成（gen 自带自愈重试）。
- 验证方式：内嵌**独立验证器** `GEN.validate`：按摆针运动学计算绿窗单次可触碰时长 `2T·asin(greenW)/π` 必须 ≥75ms（人类反应裕量）；目标时间必须恰为完美交接总时（Σ50/配速）+ 0.9s（容许 2 次普通交接的硬余量）；配速与窗口参数区间校验；`GEN.validateLevelsAll` 逐关复核，页面暴露 `window.__validateLevels` 可复跑。
- 验证结果：40/40 关通过（无 fails）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，跑道 canvas 自绘
- [x] 颜色全部走 `--g-*` / `--g-st-*` token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅双主题 canvas 重绘
- [x] 存档键 `sw.relaybaton.v1`（+ `.levels` / `.levels.ver`）；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；40 关独立验证 40/40 通过；theme-gates / audit-gates 全绿

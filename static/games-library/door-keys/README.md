# 钥匙匹配（gameKey：`door-keys`，category 建议：`puzzle`）

- 玩法：门锁下方显示锁孔齿形缺口（齿深三档：平 / 中 / 深），从钥匙串中点选齿形逐位完全吻合的钥匙开门；钥匙串里混有只差一两个齿的干扰钥匙，连错 2 次本关失败，开完所有门过关。
- 操作：触屏直接点画布里的钥匙，键盘 1-8 选对应钥匙、空格 / 回车开始或重试；「重玩 / 选关」为工具按钮，切后台自动暂停。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；40 关内嵌（门数 3→5、干扰钥 1→3、齿位 4→6 递进），过关上报到达关卡（desc，metricExtra 为累计星数）；存档 `{lv, done:{关卡:星}, best}` 本地 + 账号双写；WebAudio 未接入（可选项）。

## 关卡验证

- 关卡结构 `{name, no, seed, teeth, doors[][], keys[{profile,door}]}`，由确定性种子生成（gen 内带自愈重试，任意 seed 均产出可通过验证的关卡）。
- 验证方式：内嵌**独立验证器** `GEN.validate`（与玩法匹配逻辑相互独立）：逐门统计齿形完全吻合的钥匙数必须恰为 1、门齿形两两互异、干扰钥不得吻合任何门、door 标记与齿形一致性核对；`GEN.validateLevelsAll` 对 40 关逐关复核，页面暴露 `window.__validateLevels` 可复跑。
- 验证结果：40/40 关通过（无 fails）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 `--g-*` / `--g-st-*` token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作可完整游玩；按钮热区 ≥44px
- [x] 切后台自动暂停；深浅双主题 canvas 重绘
- [x] 存档键 `sw.doorkeys.v1`（+ `.levels` / `.levels.ver`）；无桥自动降级 localStorage
- [x] JS 语法 node new Function 通过；grep 零 http/https；40 关独立验证 40/40 通过；theme-gates / audit-gates 全绿

# 洗衣房值班（gameKey：`laundry-day`，category 建议：`other`）

- 玩法：洗衣房高峰班——客人按班表陆续上门（快洗 8s / 标准 12s / 大件 16s），把队首订单塞进空闲机位，洗完及时收衣折叠腾出机位；排队超过耐心时限客人会投诉，班内完成单数达标且投诉不超限即过关。
- 操作：触屏点机位：空闲时上队首订单、洗完（闪绿框）再点一次收衣；键盘 1-4 对应机位上机/收衣、空格 暂停/继续、R 重开。
- 说明：单文件自包含 H5，卡片式布局（标题由 App 原生页头承载）；40 班内嵌，机数/利用率/耐心随班次递增——主题与 SmartWash 洗衣业务同源。

## 关卡验证

- 内嵌 40 班，每班结构 `{name,time,machines,patience,arrivals[[t,type]],targetK,maxL,difficulty}`；到店班表确定性生成（mulberry32 固定种子，间隔=均值时长/(利用率×机数)±抖动）。
- 验证方式：游戏内嵌**贪婪基准回放器** `LD.sim`（有空机即上队首、洗完立即收衣，0.25s 步长纯函数），生成期以基准结果定标（targetK=基准完成数-1~2、maxL=基准投诉数下限 1），`LD.validate` 复核结构+基准可达标；玩家需接近满分操作才能达标。
- 锚点用例（`window.__validateLevels()` 共 6 条，node 抽取 XC-CORE 复现）：单机两单不重叠 served=2（精确）、大件占机 8s 耐心超时投诉=1（精确）、双机并行两标准单 served=2（精确）、快洗 8s 完成（机时精确）、40 班贪婪基准可达标、生成逐字节确定。
- 生成与验证结果：40/40 班基准可达标（fail 空）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片，素材全部 canvas 自绘
- [x] 颜色全部走 `--g-*` token（PAL 读 CSS 变量，__gOnTheme 重绘），JS 零颜色字面量
- [x] 触屏 + 键盘双操作；按钮热区 ≥44px
- [x] 计时为固定 tick 的 dt 累积制，rAF 循环 visibilitychange 停帧/续跑（dt 钳制 0.05 + 单帧 tick 上限），无 Date.now 到期基点；结算在 tick 内同步触发，无延迟回调竞态
- [x] 存档键 `sw.laundryday.v1`（续玩从 `save.lv` 起步）+ `.levels`/`.ver` 缓存；无桥自动降级 localStorage
- [x] 关卡驱动：40 关内嵌 + 独立基准回放验证器（含锚点用例）；audit-gates 通过（0 违规）；theme-gates 全绿

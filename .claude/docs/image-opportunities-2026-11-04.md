# Android 全页面「图片替代机会」调研文档

> **调研日期**：2026-11-04
> **调研范围**：SmartWash Android 全部 59 个页面（app 壳层 8 + feature 模块 51）
> **调研方法**：5 个并发子代理分组调研（壳层 / 订单支付券 / 用户登录设置 / 观象台 / 实验游戏工具箱）
> **调研目标**：找出可用图片替代的位置，区分「PS 自绘位图」与「SVG/VectorDrawable」两类方案，并澄清 new/hot 角标需求

---

## 一、现状基线（决定机会边界）

### 1.1 资源现状：全项目几乎零图片

| 项 | 现状 |
|---|---|
| 位图资源 | 仅 `app/src/main/res/drawable/logo.png`（309KB，**全仓零引用**，死资产）+ 启动器 webp |
| VectorDrawable | 仅 10 个：8 个底部导航 + `ic_washing_machine.xml` + `ic_water_add.xml` + 支付渠道 `ic_wechat/ic_alipay` |
| 图片加载 | Coil 2.7.0 **仅在 `app` 与 `feature/user/impl`** 声明；其余模块无 |
| 网络配图 | 仅工作台 `WorkbenchImage`（Coil `AsyncImage`，由服务端 `imageUrl` 驱动） |
| 空态 | `EmptyState` 组件**忽略传入的 icon 参数，恒渲染 48dp DrumMark**（`AppComponents.kt:546-591`）→ 全仓 12+ 处空态视觉完全同构 |
| 角标 | **无硬编码「NEW/HOT」字符串**；游戏/工作台角标完全由服务端 `badge` 文案驱动（纯文字胶囊） |

### 1.2 设计系统约束（红线）

- **位图图标一律矢量**；AI 生图只能出草图，最终人工重绘为 VectorDrawable（`ui-design-spec.md:439-440`）
- 自绘范围锁死为「tab 8 + 动效 4 + 品牌标识 2」，其余 104 处 `Icons.*` 保持 Material 内置、**不替换**（`ui-design-spec.md:314-325`）
- 空态规范：滚筒圆 `DrumMark` 静止态 + 引导文案 + 行动按钮
- 票券规范：白卡 + 左右半圆打孔 + 中间虚线（用于优惠券、取件票）
- 六色浅底图标容器（绿#E8F6EF/蓝#EEF2FB/橙#FDF3E7/粉#FDEEF0/青#F0FBF4/紫#F4F0FB）

### 1.3 关键发现

1. **最大规范缺口**：`IndexPage` 的「暂无进行中的订单」是**纯文字空态**（无滚筒圆、无 CTA），违反空态规范 §3.6
2. **最大系统性机会**：`EmptyState` 恒渲染 DrumMark，12+ 处空态同构 → 支持可选插画即可统一升级
3. **最大规范未落地**：票券规范（打孔+虚线）仅在 `feature/coupon` 内部实现，取件票/选券列表未复用
4. **仪式页机会**：支付成功页规范定义了「环闭合 + 涟漪扩散」但**未实现**
5. **角标现状**：游戏/工作台已有服务端文案角标（纯文字胶囊），但**无 NEW/HOT 图形区分**；设置页检查更新**无 NEW 角标**

---

## 二、需求清单（按优先级）

### P0 — 高优先级（规范缺口 + 高 ROI）

| # | 页面 | 需求 | 类型 | 说明 |
|---|---|---|---|---|
| 1 | **IndexPage** | 空态补滚筒圆 + 引导文案 + CTA | 规范修复 | 当前纯文字，违反 §3.6 |
| 2 | **EmptyState 组件** | 支持可选插画参数（保留 DrumMark 为默认） | 系统性 | 一处改动，12+ 处空态受益 |
| 3 | **TicketShape 上提** | 票券打孔+虚线从 `feature/coupon` 上提到 `common/ui` | 系统性 | 取件票/选券列表才能复用 |
| 4 | **PaySuccessPage** | 涟漪扩散动效（规范已定义未落地） | SVG/Canvas | 支付成功仪式感核心 |
| 5 | **PickupPage** | 取件卡票券化（打孔+虚线） | SVG drawable | 规范明列票券用于取件票 |
| 6 | **PaymentPage** | 选券列表票券化 | SVG drawable | 与优惠券页同一语言 |
| 7 | **CouponPage** | 历史券核销印章（已使用/已过期） | SVG drawable | 替代当前 55% 透明+置灰 |
| 8 | **GameCenterPage** | 游戏封面图（服务端 imageUrl + Coil） | **PS 位图** | ROI 最高，复用工作台范式 |
| 9 | **LabHomePage** | 10 张玩具入口卡定制矢量插画 | SVG drawable | 实验田门面 |
| 10 | **MoodTodayPage** | 5 级心情天气矢量（暴雨/阴/多云/晴/大晴） | SVG drawable | 直接呼应「情绪气象站」命名 |
| 11 | **观象台** | 爻象 vector 一套（阳/阴/老阳○/老阴×） | SVG drawable | 摇卦/卦盘/手动爻值三处复用 |
| 12 | **观象台** | 四术 glyph vector 一套（六爻/梅花/奇门/六壬） | SVG drawable | 首页/chip/工作台三处复用 |
| 13 | **观象台** | 空态插画（首页案卷/卦历/追问/解读四处裸文本） | PS/SVG | 模块最大视觉空白 |
| 14 | **观象台** | 玄墨卡片层级修复（卡片与背景同色） | 规范修复 | 对齐约束口径 #1E1D1B 或加 32% 金线 |

### P1 — 中优先级

| # | 页面 | 需求 | 类型 |
|---|---|---|---|
| 15 | WorkbenchPage | 运营配图位补视觉规范（占位图/失败兜底） | PS 位图 |
| 16 | WorkbenchPage | 角标图形化（NEW 星形 / HOT 火焰矢量徽章） | SVG |
| 17 | WorkbenchPage | 观象台卡背景纹理/插画 | PS 位图 |
| 18 | OrderDetailPage | `DrumIllustration` 抽 VectorDrawable 复用 | SVG（重构） |
| 19 | OrderDetailPage | 状态头插画多态化（按订单状态） | PS 位图 |
| 20 | AiWorkPage | 空态 AI 助手形象插画 | PS/SVG |
| 21 | LoginPage | 品牌 hero 插画（洗衣机+滚筒圆+水波） | SVG |
| 22 | RegisterPage | 注册引导插画（与登录页同母题不同场景） | SVG |
| 23 | Login/Register | 背景装饰母题（DrumMark/水波/泡沫淡色） | SVG/Canvas |
| 24 | UserInfoPage | 默认头像插画 + 补空态 | SVG |
| 25 | UpdateUserInfoPage | Hero 校园主题插画 + 空态 | SVG |
| 26 | SettingPage | 检查更新「NEW」角标（消费 UpdateEventBus） | SVG |
| 27 | RechargePage | 赠额档「HOT/推荐」badge | SVG |
| 28 | CouponPage | 可用券「NEW/可领取」badge | SVG |
| 29 | 各模块 | 空态插画差异化（每模块 1 张主题矢量） | PS/SVG |
| 30 | SoundscapePage | 10 路声道定制矢量图标 | SVG |
| 31 | DrumPage | 8 声部鼓垫/音色图标 | SVG |
| 32 | DeliverySessionPage | kind 图标（文本/图片/文件）重绘 | SVG |
| 33 | PaintingPage | 山水母题矢量化（可复用/可分享） | SVG |
| 34 | PetPage | 泡泡精灵角色矢量化（进化态复用） | SVG |
| 35 | SubscriptionDetailPage | 详情页头图（服务端 imageUrl） | PS 位图 |
| 36 | ReviewStudyPage | 复习完成态庆祝插画 | PS/SVG |
| 37 | 观象台 | `DivSeal` 矢量化（卦/梅/奇/壬/签/止/空） | SVG |
| 38 | 观象台 | 罗盘 vector 复用（首页/工作台/空态） | SVG |
| 39 | 观象台 | 六神 mini 图标（青龙/朱雀/勾陈/腾蛇/白虎/玄武） | SVG |
| 40 | 观象台 | 背景纹理（宣纸纤维/玄墨噪点，透明度 ≤6%） | SVG/Canvas |

### P2 — 低优先级

| # | 页面 | 需求 | 类型 |
|---|---|---|---|
| 41 | HomePage | 底栏 path 抽取自绘资源（已是矢量） | SVG（重构） |
| 42 | ServicePage | 4 个服务图标品牌化 | SVG |
| 43 | WebViewPage | 错误态主题插画（断链/无网络） | PS 位图 |
| 44 | ShortCodeDetailPage | 二维码中心 logo（滚筒圆 24dp） | SVG |
| 45 | 各 Canvas 页 | 背景肌理（水池/沙盒/星空/声景） | PS 位图 |
| 46 | 全仓 | 清理 `logo.png` 死位图 | 清理 |

---

## 三、角标专题（new/hot）

### 3.1 现状

| 位置 | 实现 | 文件 |
|---|---|---|
| 游戏中心 badge | 服务端文案 → `primaryLight` 胶囊 + `primary` 字，TopEnd | `GameCenterPage.kt:300-311` |
| 工作台 badge | 同上（服务端文案） | `WorkbenchPage.kt:1129-1140` |
| 复习「新卡」 | `primaryContainer/onPrimaryContainer` 胶囊 | `ReviewUiCommon.kt:69-76` |
| 复习到期类型 | 分钟级橙 / 天级蓝 | `ReviewUiCommon.kt:83-105` |
| 订阅试用倒计时 | ≤3 天粉 / 其余橙，tnum | `SubscriptionListPage.kt:388-404` |
| 订阅未读 | 8dp `StatusDot` danger | `SubscriptionListPage.kt:341-344` |
| 观象台「推 荐」 | **纯文本**（11sp/字距 2sp/金 75%），无底色无描边 | `DivHomePage.kt:336-344` |
| 设置页检查更新 | **无角标**（仅版本号文字） | `UpdateFlow.kt:331-365` |
| 实验/情绪/工具箱 | **无角标** | — |

### 3.2 角标需求建议

1. **新增语义色变体**（而非新组件）：
   - `NEW` = `primaryLight` 底 + `primary` 字（现有）
   - `HOT` = `iconContainerOrange` 底 + `ongoingInk` 字
   - 可加小图形：NEW 星形 / HOT 火焰（SVG）
2. **落点建议**：
   - 设置页检查更新行（消费 `UpdateEventBus` 新版本事件）
   - 充值页赠额档（¥50/¥100/¥200 的「送 ¥x」旁）
   - 优惠券可用券卡（「可领取」）
   - 游戏中心/工作台（已有服务端文案，可升级为双色变体）
   - 观象台「推 荐」（当前纯文本不像 badge，建议 14% 金底 + 金线 + 胶囊）
3. **文案资源**：当前 `strings.xml` 无 NEW/HOT 文案，需新增
4. **字阶**：角标档为 `AppTextStyles.CaptionStrong`（12sp/500）

---

## 四、跨页面系统性机会（建议优先于单页微调）

| # | 机会 | 说明 | 优先级 |
|---|---|---|---|
| C1 | **EmptyState 支持可选插画** | 新增 `illustration: ImageVector?/Painter?` 参数，保留 DrumMark 为默认 | **高** |
| C2 | **TicketShape 上提 common/ui** | 当前是 `feature/coupon` 的 internal 实现，上提后取件票/选券列表可复用 | **高** |
| C3 | **支付成功涟漪** | 规范 `ui-design-spec.md:581` 定义「环闭合+涟漪扩散一圈」，未实现 | **高** |
| C4 | **Coil 依赖下沉** | 把 `coil-compose` 加到 `common/ui`，提供统一 `SmartWashAsyncImage`（surfaceVariant 占位 + 180ms crossfade + reduced motion 关淡变） | **中** |
| C5 | **DrumIllustration 矢量化** | OrderDetailPage 的 120dp Canvas 插画抽成 VectorDrawable | **中** |
| C6 | **矢量资产命名规范** | 建议在 `common/ui/res/drawable/` 统一存放跨模块矢量（如 `ic_lab_bubble.xml`、`ic_mood_sunny.xml`） | **中** |
| C7 | **清理死位图** | `app/src/main/res/drawable/logo.png` 无引用，与红线相悖 | **低** |

---

## 五、落地前置条件

1. **Coil 下沉**：游戏封面、运营 banner、订阅头图依赖网络图，需先下沉 Coil 并提供统一 `SmartWashAsyncImage`
2. **服务端字段**：游戏/工作台需 `imageUrl`（工作台已有）；实验舱入口可复用 `workbench_entry.image_url`
3. **矢量资产命名**：跨模块矢量放 `common/ui/res/drawable/`，模块专属放各 feature
4. **主题适配**：任何新增 vector 必须支持主题着色（清氧绿 / 玄墨金），避免硬编码颜色
5. **动画保留**：铜钱翻转、罗盘旋转、六壬天盘旋转、爻线 draw-on 等动画在矢量化迁移时需保留
6. **字体**：观象台仪式文本（卦名、干支、六神）必须继续用 `FontFamily.Serif`，矢量化时不要把文字转 path

---

## 六、PS 位图适用面（规范允许的三类）

根据红线，PS 位图**仅适用**于以下三类场景：

1. **运营配图**：工作台入口图（已有 `imageUrl` 链路）、游戏封面、订阅服务图
2. **背景纹理/底纹**：玄墨宣纸纹理、实验页氛围背景、票券底纹
3. **仪式页庆祝背景**：支付成功庆祝背景

**不适用**：所有图标位（IconBox 内、kind 图标、状态图标、声道图标）必须 SVG/VectorDrawable。

---

## 七、红线提醒

- **位图图标一律矢量**：所有图标位禁止 PNG
- **AI 生图只能出草图**：最终须人工重绘为 VectorDrawable
- **空态规范**：DrumMark 母题 + 引导文案 + 行动按钮；新空态插画不得替换母题本身，只能作为主题化装饰层
- **六色浅底**：新增图标必须进容器，禁止裸图标着色
- **Material 图标不替换**：104 处 `Icons.*` 保持内置（规范 §4.1）

---

## 八、附：各模块详细调研

- **壳层页面**（HomePage/IndexPage/WorkbenchPage/AiWorkPage/WebViewPage/PickupPage/OrderDetailPage）：见子代理报告 1
- **订单/支付/优惠券/充值**（12 页）：见子代理报告 2
- **用户/登录/注册/设置**（6 页）：见子代理报告 3
- **观象台**（7 页 + 公共组件）：见子代理报告 4
- **实验/游戏/工具箱/情绪/复习/投递/订阅**（26 页）：见子代理报告 5

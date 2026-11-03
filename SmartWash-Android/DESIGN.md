---
name: SmartWash Android 用户端
description: 清氧呼吸舱 — 清氧绿双主题（清氧·日 / 清氧·夜）校园洗衣 App 设计系统
colors:
  primary: "#2D9B6A"
  primary-deep: "#1E8C5C"
  primary-soft: "#E8F6EF"
  on-primary-soft: "#0B3625"
  primary-light: "#F0FBF4"
  background: "#F5F6F3"
  group-background: "#E7EAE4"
  surface: "#FFFFFF"
  surface-variant: "#EFF1EE"
  hairline: "#EFF1EE"
  outline: "#E6E9E6"
  text-primary: "#1C1C1E"
  text-secondary: "#6B6E73"
  text-tertiary: "#A9AEA9"
  success: "#2D9B6A"
  ongoing: "#D98A26"
  danger: "#A8435A"
  water: "#4A9FD4"
  water-soft: "#DCEFF7"
  icon-bg-green: "#F0FBF4"
  icon-bg-blue: "#EEF2FB"
  icon-bg-orange: "#FDF3E7"
  icon-bg-pink: "#FDEEF0"
  icon-bg-teal: "#F0FBF4"
  icon-bg-purple: "#F4F0FB"
  icon-fg-green: "#2D9B6A"
  icon-fg-blue: "#4A67C9"
  icon-fg-orange: "#D98A26"
  icon-fg-pink: "#C9526B"
  icon-fg-teal: "#3F9D78"
  icon-fg-purple: "#7C3AED"
  service-wash: "#2D9B6A"
  service-dry: "#7C3AED"
  service-press: "#E07B00"
  service-luxury: "#0EA5E9"
  service-shoes: "#DC2626"
  wechat-green: "#07C160"
  alipay-blue: "#1677FF"
  dark-primary: "#5BC894"
  dark-background: "#141614"
  dark-surface: "#1D201E"
  dark-surface-variant: "#262A27"
  dark-group-background: "#1A1E1B"
  dark-hairline: "#262A27"
  dark-outline: "#2C312D"
  dark-text-primary: "#E6E9E6"
  dark-text-secondary: "#A9AEA9"
  dark-text-tertiary: "#7E837D"
  dark-primary-soft: "#1D3229"
  dark-on-primary-soft: "#B9E8CF"
  dark-ongoing: "#E5AC5C"
  dark-danger: "#E795A5"
  dark-water: "#5FAEE0"
typography:
  display:
    fontSize: "36sp"
    fontWeight: 700
    lineHeight: "44sp"
    fontFeature: "tnum"
  headline:
    fontSize: "28sp"
    fontWeight: 700
    lineHeight: "36sp"
  title-large:
    fontSize: "22sp"
    fontWeight: 600
    lineHeight: "28sp"
  title:
    fontSize: "18sp"
    fontWeight: 600
    lineHeight: "24sp"
  body-large:
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
  body:
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
  caption:
    fontSize: "12sp"
    fontWeight: 400
    lineHeight: "16sp"
  caption-strong:
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
  button:
    fontSize: "15sp"
    fontWeight: 600
    letterSpacing: "1sp"
rounded:
  sm: "10dp"
  md: "12dp"
  lg: "16dp"
  xl: "20dp"
  full: "999dp"
  input: "14dp"
spacing:
  xxs: "4dp"
  xs: "8dp"
  sm: "12dp"
  md: "16dp"
  lg: "20dp"
  xl: "24dp"
  xxl: "32dp"
  page: "24dp"
  card-padding: "20dp"
  card-gap: "16dp"
components:
  app-button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "#FFFFFF"
    rounded: "{rounded.full}"
    height: "48dp"
    typography: "{typography.button}"
  app-button-primary-disabled:
    backgroundColor: "{colors.primary}"
    textColor: "#FFFFFF"
    rounded: "{rounded.full}"
    height: "48dp"
  app-button-secondary:
    backgroundColor: "{colors.surface-variant}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.full}"
    height: "48dp"
    typography: "{typography.button}"
  app-button-text:
    textColor: "{colors.primary}"
  card:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.lg}"
    padding: "20dp"
  hero-card:
    backgroundColor: "{colors.primary}"
    textColor: "#FFFFFF"
    rounded: "{rounded.xl}"
  icon-box-primary:
    backgroundColor: "{colors.primary-light}"
    rounded: "{rounded.md}"
    size: "44dp"
  input-field:
    backgroundColor: "{colors.surface-variant}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.input}"
    height: "48dp"
---

# Design System: SmartWash Android 用户端（清氧设计系统）

## Overview

**Creative North Star: "清氧呼吸舱"**

整个 App 是一间氧气充足的舱室：学生把换洗衣物送进舱里（投柜），衣物在舱内呼吸一轮——滚筒转动、水波起伏、泡沫上浮——然后干爽地回来（凭取件码取件）。界面的职责是让这台「呼吸机器」被感知：绿是舱内的空气，白卡是舱内的器物，滚筒圆是唯一被记住的几何。界面安静、留白慷慨，但绝不简陋——洗衣的物质（水、泡沫、织物、打孔洗衣票）必须以矢量与 Canvas 的形式出现在界面里，让新用户一眼看出这是洗衣产品。

气质基调是「简约而非简陋」：层次靠底色差、1dp 描边、两档轻阴影与大面积留白共同撑起，而不是靠分割线与重色块。品牌绿 #2D9B6A 是氛围不是按钮色——它出现在 hero 渐变、选中底、图标容器、数据强调等非功能位置，按钮反而是它最不显眼的用途。动效全部来自真实状态（订单在洗、金额到账），并全树尊重系统「减少动态效果」。

**Key Characteristics:**

- 每屏至多一个视觉锚点（36sp 数据大字 / 状态大字 / hero 卡三选一），其余全部安静
- 卡片只有一种画法：纯白底 + 1dp 描边 + 着地影，禁止灰底卡、裸白卡、0.5dp 描边卡并存
- 所有功能图标必须进六色浅底容器；裸 Material 图标直接着色即违规
- 金额、数量、取件码、订单号一律 tabular numbers（`fontFeatureSettings = "tnum"`）
- 双主题（清氧·日 / 清氧·夜）共享全部结构令牌，只换强调层；观象台另有玄墨子主题
- 零外部动画库（Lottie/Rive/Shimmer/Vico 禁用）、零外部图标库；动效全部 Compose 原生
- 任何动效必须有 `LocalReduceMotion` 降级分支，无降级不合入

## Colors

色板 = 一口清氧绿 + 一汪水蓝 + 三个语义色 + 一条绿灰中性阶 + 六个语义化浅底容器。绿色是空气（氛围），白卡是器物（内容），中性阶是光线（层级）。

### Primary
- **清氧绿** (#2D9B6A)：主色，红线规定不改。用于主按钮渐变亮端、选中底、图标容器前景、进度弧、链接与数据强调。按下态/渐变暗端为深绿 #1E8C5C；浅绿选中底 #E8F6EF 配深绿字 #0B3625；图标容器/票券底为超浅绿 #F0FBF4（与 #E8F6EF 是两个绿，勿合并）。

### Secondary
- **水蓝** (#4A9FD4)：次要强调、进度、水滴意象；浅底容器 #DCEFF7。它的存在让「洗」这件事有水的在场感，但永远不与主绿争夺锚点。

### Tertiary（语义色）
- **进行中橙** (#D98A26)：进行中/待处理语义，浅底 #FDF3E7。
- **危险玫红** (#A8435A)：异常/取消/危险操作，on 白对比度 ≥4.5:1；表单校验错误也收敛到这一角色（已弃 iOS 红 #FF3B30）。
- **服务五色**：标准洗绿 #2D9B6A / 干洗紫 #7C3AED / 熨烫暖橙 #E07B00 / 奢护蓝 #0EA5E9 / 洗鞋红 #DC2626，各自配浅底，用于洗衣项目卡的类型识别。
- **第三方品牌色**：微信 #07C160、支付宝 #1677FF——支付渠道图标与品牌色不得替换为通用组件。

### Neutral（绿灰中性阶）
- **页面底·氧泡米白** (#F5F6F3)：微绿调米白，与白卡形成可见但不喧哗的底卡分离。
- **分组底** (#E7EAE4)：比页面底再深一档的分组容器。
- **卡片** (#FFFFFF)：纯白 surface，唯一卡片底色。
- **卡内次级块** (#EFF1EE)：输入框底、嵌套卡；同时是容器内部发丝线的颜色。
- **卡片描边** (#E6E9E6)：1dp 标准描边。
- **文本三级**：主文本 #1C1C1E / 次文本 #6B6E73 / 弱文本 #A9AEA9（弱文本仅装饰性使用）。

### 图标容器六色浅底（按语义分配，不随机）

| 底色 | 值 | 语义 | 前景色 |
|---|---|---|---|
| 绿 | #F0FBF4 | 洗护主流程 | #2D9B6A |
| 蓝 | #EEF2FB | 物流/寄件/订单查询 | #4A67C9 |
| 橙 | #FDF3E7 | 取件/通知/待办 | #D98A26 |
| 粉 | #FDEEF0 | 异常/取消 | #C9526B |
| 青 | #F0FBF4 | 账户/安全/充值 | #3F9D78 |
| 紫 | #F4F0FB | 观象台/AI | #7C3AED |

### 清氧·夜（深色主题）

跟随系统为默认，设置页三档切换。结构令牌整体下移两档、语义色提亮一档；品牌绿在暗底上对文字强调提亮为 #5BC894（`primaryDeep` 语义反转为「暗底亮绿」），主按钮白字切换为深绿字 #17322B 保证对比度：

| 令牌 | 浅色 | 深色 |
|---|---|---|
| background | #F5F6F3 | #141614 |
| surface | #FFFFFF | #1D201E |
| surfaceVariant | #EFF1EE | #262A27 |
| outline / hairline | #E6E9E6 / #EFF1EE | #2C312D / #262A27 |
| textPrimary / Secondary / Tertiary | #1C1C1E / #6B6E73 / #A9AEA9 | #E6E9E6 / #A9AEA9 / #7E837D |
| primarySoft / onPrimarySoft | #E8F6EF / #0B3625 | #1D3229 / #B9E8CF |
| ongoing / danger | #D98A26 / #A8435A | #E5AC5C / #E795A5 |
| 深色图标容器六色 | — | #1D3229 / #1F2637 / #332917 / #341E24 / #17322B / #272038（绿/蓝/橙/粉/青/紫） |

登录/注册等品牌渐变页（160° 三段 #1A9E6E → #0D7A4A → #0B5C3A + 毛玻璃卡）深浅同款。

### Named Rules
**The 一屏一锚点规则 (One Anchor Rule).** 每屏最多一个「重」元素——36sp 数据大字、状态大字、hero 卡，三者至多留一。锚点超过一个等于没有锚点；标题不参与大字竞争（根页 ≤22sp、内页 ≤22sp）。

**The 品牌绿是空气规则 (Green-is-Air Rule).** 绿色必须出现在至少三处非功能位置（hero 渐变、区块标题装饰、选中底、图标容器、数据强调），它在界面里是「空气中的氧」，不是按钮的专利。大面积纯绿底只属于 hero 与主 CTA。

**The 六色容器规则.** 图标容器底色按语义从六色浅底中取（洗护绿、物流蓝、待办橙、异常粉、账户青、观象台紫），禁止随机取色、禁止裸图标直接着色。

## Typography

**Display/Body Font:** 系统默认无衬线栈（Compose 默认，设备端 Roboto / 厂商中文黑体），不引入外部字体。清氧主题不用衬线；玄墨子主题的仪式文本（卦名、干支）用 `FontFamily.Serif`。

**Character:** 靠 8 档固定字阶与字重（只允许 400/500/600/700）做层级，不造夹档——不存在 15sp 正文、17sp/20sp/21sp/24sp 标题档。数字槽位启用 tnum。

### Hierarchy（v0.4 定稿 8 档）
- **Display** (700, 36sp/44sp, tnum)：数据大字——余额、应付金额、取件码。每屏至多一处。
- **Headline** (700, 28sp/36sp)：结果页大标题；数据大字在窄屏的降级档（36 → 28 → 22）。
- **Title Large** (600, 22sp/28sp)：内页页头（PageHeader）、根页大标题（RootTitle）、hero 状态锚点字（StatusLarge）。
- **Title** (600, 18sp/24sp)：区块标题、卡片标题、金额中字（AmountMedium，+tnum）。
- **Body Large** (400, 16sp/24sp)：主要正文；表单输入正文的最小档（输入禁用 14sp 及以下）。
- **Body** (400, 14sp/20sp)：列表行主文案（特许配 500）、次要正文。
- **Caption** (400, 12sp/16sp)：辅助说明、时间戳、占位符。
- **Caption Strong** (500, 12sp/16sp)：tab、角标、底部导航标签。
- **Button** (600, 15sp, letterSpacing 1sp)：按钮文字——组件级规格（§3.3），不在 8 档字阶内。

语义别名（`AppTextStyles`）`DataLarge / StatusLarge / AmountMedium / RootTitle / PageHeader / CardTitle / SectionTitle` 保留，值已映射到上述 8 档，既有调用点无需改动。

### Named Rules
**The 数字等宽规则 (tnum Rule).** 金额、数量、取件码、订单号一律 `fontFeatureSettings = "tnum"`——数字变化时左右跳即违规。取件码等关键数字优先用 display/tnum 大字。

## Layout

单列手机布局，8pt 网格。垂直节奏自上而下：页头 → hero/锚点 → 信息卡流 → 底部导航（68dp 高、4 个 tab，不超过 5 项）。

- **页面左右边距 24dp**，卡片内边距 20dp，卡片间距 16dp，区块间距 32dp。
- **列表行高 56dp**（设置页两行卡实测 64dp），触控热区 ≥44dp，返回箭头等豁免入容器的图标仍强制 48dp 热区。
- **底栏导航**：4 个 tab（首页/服务/工作台/我的），自绘线性/实心成对图标 26dp，未选中 #A9AEA9、选中 #2D9B6A 并放大 1.06。
- 浮层（底部导航、固定 CTA 条、输入条）与滚动内容之间可用 0.5dp 分隔线；页面区块之间禁止用分割线切分——层次靠底色、边界、留白三件套。
- 稀疏比拥挤大气：宁可一屏少放一个模块，不压缩间距。间距是本系统最便宜的「高级感」。

## Elevation & Depth

两档阴影制——全应用只允许「着地」与「浮层」两种阴影，Compose 侧经 `AppElevation` 映射 CSS 规格：

### Shadow Vocabulary
- **着地（卡片）** (`AppElevation.level1` 1dp ≈ CSS `0 2px 12px rgba(20,40,30,.05)`)：所有卡片（`GroupCard` / `AppCard` / 页面侧 `Surface`）共用同一条路径，深色主题下加强为 `rgba(0,0,0,.28)`。
- **浮层（弹窗/Sheet）** (`AppElevation.floatLayer` 10dp ≈ CSS `0 10px 24px rgba(20,40,30,.10)`)：确认弹窗、输入弹窗、更新弹窗；禁再叠加 tonalElevation。

Hero 卡、按钮、深色卡一律不加投影——层级由渐变/描边/深色自身承担。

### Named Rules
**The 两档阴影规则 (Two-Shadow Rule).** 出现第三种阴影（更大扩散、更高海拔、彩色影）即返工。浮层之下不加 tonalElevation，着地影只属于卡片。

## Shapes

圆角五档 + 一个输入档，随体量递增：徽标/小图标容器 10dp → 图标容器/卡内嵌套块 12dp → **卡片 16dp（唯一卡片值）** → hero/大容器 20dp → 胶囊 999dp（chip、头像、按钮）。输入框/金额格保留 14dp 档。

- **按钮一律胶囊**（48dp 高 + r999，2026-11-01 全应用定调），旧 52dp + 14dp 圆角作废。
- **卡片描边 1dp** #E6E9E6，配合着地影；0.5dp 发丝线只出现在同一容器内部的行之间。
- **打孔票券**：白卡 + 左右半圆打孔 + 中间虚线，用于优惠券、取件票——洗衣票的物质记忆。
- 底部 tab 图标为自绘 24 栅格矢量，线性版关键线 3–21、实心版 2–22，圆润派（round 端点/转角），几何需过包围盒/视觉尺寸/对称/角度四项校验。

## Components

组件语言一句话：白卡装内容，浅底容器装图标，胶囊装动作，滚筒圆装灵魂。交互反馈一律自绘 `pressable`（scale 0.97 + 100ms，`indication = null`），禁用 Material ripple / state layer。

### Buttons（AppButton，业务层唯一按钮入口）
- **Shape:** 48dp 高胶囊（r999），按压 scale 0.97 / 100ms。
- **Primary:** 品牌渐变底（135° primary → primaryDeep），白字 15sp/600/ls 1sp；深色主题白字切换为深绿字 #17322B。禁用/加载态容器降透明度 0.5，loading 换 22dp spinner（reduced motion 时为静态 270° 圆弧）。
- **Secondary:** surfaceVariant 底 + 主文本色，同尺寸同字。语义例外：退出登录 = SECONDARY + `contentColor = dangerInk`。
- **Text:** 主色 13sp/600，无底无框，44dp 最小热区。
- 内置连击防抖（`rememberDebouncedClick`），文字 ↔ spinner 交叉淡变 180ms。

### Cards / Containers（GroupCard / AppCard，唯一画法）
- **Corner Style:** 16dp。
- **Background:** 纯白 surface；置于 #F5F6F3 页面底上。
- **Shadow Strategy:** 着地影 level1 + 1dp 描边（见 Elevation）。
- **Internal Padding:** 默认 20dp；列表行卡传 `PaddingValues(horizontal = 20dp)` 铺满。
- **变体:** Hero 卡（品牌渐变底 + 白字 + 白色 CTA 胶囊，每页至多 1 个）；票券卡（打孔 + 虚线）。

### IconBox（图标容器）
- **主 44dp/12dp 圆角/24dp 图标** · 次 36dp/12dp/20dp · 小 32dp/12dp/20dp，底色从六色浅底按语义取，前景用对应深色版。返回箭头、表单前缀图标可豁免入容器但须 48dp 热区。

### Inputs / Fields
- **Style:** 输入正文 16sp（§3.7 硬规则），行高 48–50dp，占位符 textSecondary 同字号；前缀图标 16dp/透明度 .5。
- **尾缀操作一律图标化**: 密码可见性 = `Icons.Rounded.Visibility/VisibilityOff`（内置图标，禁止文字替代），热区 ≥44dp。
- **验证码发送**: 描边按钮 1dp + 12dp 圆角 + 高 40dp；倒计时禁用态描边与文字降至 .55。
- **Error:** 13sp error 色，仅提交时展示（输入中不报错）。

### ListRow / SettingRow（列表行）
- 高 56dp，左图标容器（36dp）+ 主文案 14sp/500 + 副文案 caption + 右侧值/箭头；行间 0.5dp 发丝线只在本卡片内部。

### StatusDot / 订单状态（状态语义）
- **8dp 圆点**位于卡片左缘外或行首；语义色：进行中 #D98A26 / 已完成 #2D9B6A / 完成 #34C759 / 异常 #C9526B（深色各提亮一档）。
- 订单 8 态（0 待支付 → 7 已完成，-1 已退款 / -2 已取消）→ 标签文案与语义色的映射以 `feature:order:api` 的 `OrderStatus`/`ShowOrderStatus` 枚举（descriptionRes）为单一事实来源，页面禁止自写 switch。

### 签名组件：滚筒圆（DrumMark）与四个动效图标
- **DrumMark**: 同心圆环 + 一条穿过它的水波线，64 栅格 Canvas 参数化绘制，无位图、双主题可 tint。进度弧由真实状态传入（订单进度/支付成功环闭合），`progress = null` 即静止空态。
- **动效图标四件**（Compose Canvas 自绘，路径在规范 §4.5 附）：滚筒（提升筋旋转 3200ms linear，仅订单==清洗中）、水滴（缩放呼吸 1→1.07，圆心 (12,14)）、水波（同相位横移 ±2，替代 Loading 转圈）、泡沫（三圆错峰上浮，第二个延迟 700ms）。
- 硬规则：动画只能由真实状态触发；`LocalReduceMotion` 时全部停驻但保持完整可读。

### EmptyState / 加载（骨架屏）
- **空态**: DrumMark 静止态（48dp 主色）+ 一句引导文案（bodyLarge）+ 行动按钮；禁止灰色 Material 图标了事。
- **加载**: 骨架屏（形状与真实内容一致，原生渐变微光 1400ms 扫过；reduced motion 退化为静态浅灰块），禁止居中转圈占位列表。分页尾部用静态三点灰 `LoadingDots`。

### Dialogs（AppConfirmDialog / AppInfoDialog / AppInputDialog）
- 16dp 圆角 + floatLayer 阴影；入场 scale 0.92→1 + 淡入（弹簧，reduced motion 降级 180ms 纯淡入）；不做退出动画（Dialog 窗口机制限制）。
- 底部分区按钮（DialogAction）左右贴合边缘、按压整区透明度变化（iOS 惯例）；危险确认用 dangerInk，不复用表单 error 色。

### Navigation
- **底部导航**: 68dp 高，自绘 tab 图标（线性/实心成对，8 个路径定稿于规范 §4.5），26dp 显示；未选中 #A9AEA9 实心、选中 #2D9B6A + scale 1.06；选中动效 = 外轮廓勾边 0–400ms + 灰/绿交叉淡变 140–170ms + 终态 600ms（reduced motion：直接以品牌色实心终态出现）。
- **页头（PageHeader）**: 返回箭头 24dp（48dp 热区，行首让位 12dp 使图形左缘落在 24）+ 22sp/600 标题 + 右侧 actions；内置连击防抖防双 pop 黑屏。

### Motion（动效 vocabulary，`common/utils/AnimationUtils.kt` 全应用唯一来源）
- `defaultSpring()` spring(1.0, 350)——卡片入场、选中态、非手势位移；位移动画禁用固定时长 tween。
- `momentumSpring()` (0.8, 350) 仅惯性动作；`heavySpring()` (0.8, 200) 大质量物体。
- `pressTween()` 100ms + scale 0.97（按压要「立刻」不要「弹」）；`CROSSFADE_ALT_MS` 180ms 为 reduced motion 降级档。
- 时长层级：micro 200ms / card 300ms / screen 400ms，退出时长 ≈ 进入的 60–70%（`enterTween` 减速进 / `exitTween` 加速出）。
- 列表逐项入场间隔 ≤60ms、总时长 ≤400ms；页面转场用 NavHost 默认 + fade。

## Do's and Don'ts

### Do:
- **Do** 从 `common/ui/theme/`（`AppColors.colorScheme` / `AppDimens` / `AppTextStyles` / MaterialTheme）取令牌；用户可见文案一律 `strings.xml` + `stringResource()`。
- **Do** 可点击组件一律走 `pressable`（内置 indication = null + scale 0.97）或 `AppButton`；组合期副作用（Toast/导航/状态回写）一律放 `LaunchedEffect`/`SideEffect`。
- **Do** 每个新动效同时给出 reduced motion 分支（`motionSpec()` / `LocalReduceMotion`），无限旋转降级为静态可读图形。
- **Do** 金额/数量/取件码/订单号开 tnum；订单状态标签映射走 `OrderStatus` 枚举单一事实来源。
- **Do** 支付渠道用微信/支付宝品牌图标与品牌色；改任何用户端界面先对照 `docs/design/redesigned_pages.html` 原型与 `.claude/ui-design-spec.md`。
- **Do** 交付前按规范 §9 十条自检（底色/卡片画法/锚点唯一/tnum/间距/圆角五档/发丝线/降级分支/无新增 Icons.*/无硬编码文案）。

### Don't:
- **Don't** 引入 Lottie / Rive / Shimmer / Vico 等动画库，或引入任何外部图标库（已调研否决，见规范 §4.1）；自绘图标仅限 tab 8 + 动效 4 + 品牌标识 2。
- **Don't** 新增 `Icons.*` 使用点；存量 104 处 Material 内置图标保持现状，不替换、不重绘。
- **Don't** 改主色 #2D9B6A（玄墨子主题除外）；微信/支付宝图标与品牌色不得换成通用组件。
- **Don't** 使用第二种卡片画法（灰底卡/裸白卡/0.5dp 描边卡）、第三种阴影、字阶外的夹档字号、五档外的圆角。
- **Don't** 伪造状态动画（支付成功/订单完成必须由真实接口返回触发）；订单没在「清洗中」时转动滚筒/泡沫图标。
- **Don't** 在页面里写动效魔法数值（改 `AnimationUtils.kt` 等于全应用换手感）、写死色值/URL、用位图图标或 AI 生图直入。
- **Don't** 劫持系统返回手势、破坏 edge-to-edge insets；Material 交互组件（Button/TextField 等自带 state layer）禁止直接用于可点击场景。

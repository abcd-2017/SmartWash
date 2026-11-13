---
name: SmartWash Web 管理后台
description: 靛蓝工作台 — Indigo/Slate 亮色数据管理后台
colors:
  primary: "#6366f1"
  primary-light: "#818cf8"
  primary-dark: "#4f46e5"
  primary-tint: "#eef2ff"
  success: "#10b981"
  success-tint: "#d1fae5"
  danger: "#ef4444"
  danger-tint: "#fee2e2"
  warning: "#f59e0b"
  warning-tint: "#fef3c7"
  info-blue: "#3b82f6"
  sidebar-bg: "#0f172a"
  text-primary: "#0f172a"
  text-input: "#1e293b"
  text-body: "#334155"
  text-regular: "#475569"
  text-secondary: "#64748b"
  text-muted: "#94a3b8"
  border-hover: "#cbd5e1"
  border: "#e2e8f0"
  border-subtle: "#f1f5f9"
  bg-page: "#f1f5f9"
  bg-subtle: "#f8fafc"
  bg-card: "#ffffff"
typography:
  headline:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei', 'Segoe UI', Roboto, sans-serif"
    fontSize: "20px"
    fontWeight: 700
    lineHeight: 1.4
  stat:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei', 'Segoe UI', Roboto, sans-serif"
    fontSize: "24px"
    fontWeight: 700
    lineHeight: 1.2
  title:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei', 'Segoe UI', Roboto, sans-serif"
    fontSize: "16px"
    fontWeight: 600
    lineHeight: 1.5
  body:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei', 'Segoe UI', Roboto, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.6
  label:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei', 'Segoe UI', Roboto, sans-serif"
    fontSize: "13px"
    fontWeight: 500
    lineHeight: 1.5
  micro:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei', 'Segoe UI', Roboto, sans-serif"
    fontSize: "12px"
    fontWeight: 400
    lineHeight: 1.5
  mono:
    fontFamily: "'SF Mono', Menlo, Consolas, 'Courier New', monospace"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: 1.6
rounded:
  sm: "6px"
  md: "8px"
  lg: "10px"
  xl: "12px"
  2xl: "16px"
spacing:
  xs: "8px"
  sm: "12px"
  md: "16px"
  lg: "20px"
  xl: "24px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "#ffffff"
    rounded: "{rounded.md}"
    height: "32px"
  button-primary-hover:
    backgroundColor: "{colors.primary-light}"
  button-primary-active:
    backgroundColor: "{colors.primary-dark}"
  button-default:
    backgroundColor: "{colors.bg-card}"
    textColor: "{colors.text-body}"
    rounded: "{rounded.md}"
    height: "32px"
  button-default-hover:
    backgroundColor: "{colors.primary-tint}"
    textColor: "{colors.primary}"
  card:
    backgroundColor: "{colors.bg-card}"
    rounded: "{rounded.xl}"
    padding: "20px 24px"
  input:
    backgroundColor: "{colors.bg-subtle}"
    textColor: "{colors.text-input}"
    rounded: "{rounded.md}"
    height: "32px"
  tag-success:
    backgroundColor: "{colors.success-tint}"
    textColor: "#065f46"
    rounded: "{rounded.sm}"
  tag-danger:
    backgroundColor: "{colors.danger-tint}"
    textColor: "#991b1b"
    rounded: "{rounded.sm}"
  tag-warning:
    backgroundColor: "{colors.warning-tint}"
    textColor: "#92400e"
    rounded: "{rounded.sm}"
  tag-info:
    backgroundColor: "{colors.border}"
    textColor: "{colors.text-body}"
    rounded: "{rounded.sm}"
---

# Design System: SmartWash Web 管理后台

## Overview

**Creative North Star: "靛蓝工作台"**

深色靛蓝侧栏是工作台的外壳，白色卡片是摊开的工具盘，靛蓝只出现在需要手去碰的地方——按钮、焦点、激活项。这是一套纯亮色、数据优先的运营后台（明确不跟随系统暗色模式），气质克制、精准、低压力：界面退后，表格与数字是主角。品牌上与 Android 用户端的清氧绿体系刻意分离——管理后台是内部工具，靛蓝是它自己的身份。

密度中高但不紧张：信息靠淡彩底色标签和清晰的圆角层级组织，而不是靠线条分隔。动效全站共享三档时长 token（150/220/320ms）与标准缓动曲线，进场减速、离场加速，且全站尊重系统「减少动态效果」设置。

**Key Characteristics:**

- 深色 Slate 侧栏（#0f172a）+ 白色顶栏 + 浅灰页面底（#f1f5f9）的三段式框架
- 白卡片承载一切内容，圆角 12px，静止时几乎无阴影（0 1px 3px / 4% 黑）
- 状态表达一律「淡彩底 + 同色系深字」，实心饱和色只留给按钮
- 靛蓝是唯一强调色，出现在 ≤10% 的界面上；其余全部交给 Slate 中性色阶
- 全站 tabular numbers：金额、数量、ID 列数字不抖动
- 基于 Element Plus 深度换肤：所有组件圆角/配色对齐全局 token，禁止组件自说自话

## Colors

色板 = 一个靛蓝强调色 + 四个语义色 + 一条完整的 Slate 中性色阶。语义色只以「淡彩底标签」和「实心按钮」两种形态出现。

### Primary
- **靛蓝 Indigo** (#6366f1)：主操作按钮、焦点环、激活菜单项、链接与选中态。浅阶 #818cf8 用于 hover 提亮，深阶 #4f46e5 用于 active 加深，#eef2ff 是它的淡彩底（默认按钮 hover、下拉选中、primary 标签底）。

### Secondary
- **翡翠绿 Emerald** (#10b981)：成功语义——已支付、已完成、空闲、成功充值。淡彩底 #d1fae5 配深字 #065f46。
- **警琥珀 Amber** (#f59e0b)：待处理语义——待支付、配送中、故障、处理中。淡彩底 #fef3c7 配深字 #92400e。
- **警示红 Red** (#ef4444)：失败/危险操作——删除按钮、支付失败、使用中（储物柜）。淡彩底 #fee2e2 配深字 #991b1b。
- **信息蓝 Blue** (#3b82f6)：统计卡等需要第四色时的辅助，不参与状态语义。

### Neutral（Slate 色阶）
- **墨黑** (#0f172a)：主文字与侧栏底色——同一个值两种角色，是这套体系「外壳即墨色」的锚点。
- **正文灰阶**：输入文字 #1e293b、表单标签/默认按钮字 #334155、次级正文 #475569、说明文字 #64748b、占位符/弱文字 #94a3b8。
- **边框灰阶**：hover 加深的边框 #cbd5e1、标准边框 #e2e8f0、卡内分隔线 #f1f5f9。
- **背景灰阶**：页面底 #f1f5f9、弱底色（表头/输入框底/分页条）#f8fafc、卡片白 #ffffff。

### Named Rules
**The 淡彩底规则 (Tint-on-Tint Rule).** 状态标签永远用「淡彩底 + 同色系深字」，无边框；实心饱和底色只属于按钮。表格里出现一排实心彩色块即视为破坏体系。底色（`--*-tint`）与深字（`--*-tint-text`）在 base.css 成对进 token，页面与皮肤层一律取 var 引用，禁止再抄 hex。

**The 一成靛蓝规则 (One-Voice Rule).** 靛蓝出现在任何一屏上的面积不超过 ~10%。它稀缺，焦点才清晰。大面积使用靛蓝底色是错误。

**The 外壳即墨色规则.** 侧栏、登录页品牌区共用 #0f172a 墨色系（可渐变到 #1e293b/#312e81），侧栏上的文字用 #cbd5e1、激活 #a5b4fc——亮暗两界各用各的文字灰阶，不混用。

## Typography

**Display/Body Font:** 系统中文栈 `-apple-system, BlinkMacSystemFont, 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei', 'Segoe UI', Roboto, sans-serif`（不引入外部字体，14px 基线与 Element Plus 对齐）
**Mono Font:** `'SF Mono', Menlo, Consolas, 'Courier New', monospace`（全站唯一等宽栈）

**Character:** 无衬线系统字，靠字重（400/500/600/700）与灰阶做层级，不靠字号跳跃。中文优先 PingFang/鸿蒙黑体/微软雅黑。

### Hierarchy
- **Headline** (700, 20px, 1.4): 页面标题（.page-header h2），每页唯一。
- **Stat** (700, 24px, 1.2): 统计卡数值，配 tabular numbers。金额带 ￥ 前缀。
- **Title** (600, 16px, 1.5): 弹窗标题、卡片区块标题。
- **Body** (400, 14px, 1.6): 全站默认，表格内容、表单值、菜单项（500）。
- **Label** (500, 13px, 1.5): 表头、筛选区标签、侧栏二级项、说明文字。
- **Micro** (400, 12px, 1.5): 弱化辅助文字、页脚、卡片内补充说明。
- **Mono** (400, 13px, 1.6): 短码、deeplink 路径、验证码等机器标识符——等宽只服务于代码与数据，不做风格装饰。
- **Display** (700, 64px, 1.2): 仅 404 页展示数字——登记在册的唯一大号展示档，不得挪作他用。
- **品牌字** (700, 18px, 1.4): 仅侧栏品牌名「SmartWash」与 404 标题——18px 是登记在册的例外，正文与控件不得使用。

> 图标字形尺寸（el-icon 的 font-size）不属文字字阶管辖，检测器命中按误报处理；图标尺寸需成体系时再补 icon-size 词汇。

### Named Rules
**The 数字等宽规则.** 表格单元格全局 `font-variant-numeric: tabular-nums`；所有金额、数量、ID 列数字必须等宽，右对齐金额列配人民币符号。

## Layout

左侧固定侧栏 220px（Slate 墨色，含品牌区 24px 内边距 + 菜单 12px 水平内边距），右侧纵向依次是 52px 白色顶栏（面包屑 + 用户下拉）与 20px 内边距的内容区。内容区是页面唯一的滚动容器（overflow-y: auto），路由切换时回顶。

页面骨架三件套：`.page-container`（flex 纵向，间距 16px）→ `.page-header`（标题 + 说明）→ `.filter-container`（筛选白卡，20px 24px 内边距）+ `.table-card`（表格 + 分页一体白卡）。筛选表单 flex-wrap 换行，筛选项右间距 12px，操作按钮组 `margin-left: auto` 靠右。分页条内嵌表格卡底部（12px 20px 内边距，#f8fafc 底 + 顶部细分隔线）。

响应式：统计卡 2 列、≥900px 4 列；快捷操作 2 列、≥600px 4 列；登录页 ≥880px 双栏、以下单栏隐藏品牌区。断点就是 600/880/900px 三个，没有更多。

## Elevation & Depth

平面为常态（flat-by-default）：卡片静止时只有一层几乎不可见的着地阴影（`0 1px 3px rgba(15, 23, 42, 0.04)`），层级靠背景灰阶（#f1f5f9 → #ffffff → 弹层）表达。阴影只在响应时出现——hover 上浮或弹层浮起。

### Shadow Vocabulary
- **着地** (`0 1px 3px rgba(15, 23, 42, 0.04)`)：卡片、筛选区、表格卡的静止阴影。
- **上浮** (`0 4px 12px rgba(15, 23, 42, 0.08)`)：hover 时的可点元素（快捷操作卡），配 translateY(-2px)。
- **浮起** (`0 10px 30px rgba(15, 23, 42, 0.1)`)：下拉面板、日期面板等气泡层。
- **弹窗** (`0 20px 60px rgba(15, 23, 42, 0.15)`)：对话框、确认框，最高层级。
- **品牌光** (`0 4px 32px rgba(99, 102, 241, 0.08)` 叠加着地)：仅登录页面板，全站唯一靛蓝味阴影。

### Named Rules
**The 平面常态规则 (Flat-By-Default Rule).** 阴影是响应不是装饰：静止的界面里只有着地阴影，任何悬浮阴影都必须对应 hover/浮层状态。

## Shapes

圆角随体量递增，五档阶梯：标签/小元素 6px → 按钮/输入框/菜单项/下拉项 8px → 气泡面板/快捷操作块 10px → 卡片/表格/统计卡图标容器 12px → 弹窗/确认框/登录面板 16px。递增方向永远是「越大的表面越圆」。

**登记例外：** 投递会话页（DeliverySession）状态角标使用 999px 全圆胶囊——有意的形态语言，源码有注释声明，不落入五档阶梯。

边框语言极轻：标准 1px #e2e8f0，卡内分隔线降为 #f1f5f9，表格竖线全部隐藏（只留横向细分隔）。输入框不用边框而是 1px 内描边 ring（box-shadow inset）+ #f8fafc 弱底，focus 时靛蓝 ring + 3px 15% 靛蓝光晕。表单控件以「弱底色块」而非「描边框」的形式存在。

## Components

组件语言一句话：白卡片装内容，弱底块做输入，淡彩标签表状态，实心靛蓝做动作。

### Buttons
- **Shape:** 圆角 8px，高 32px（小号 24px），按压 scale(0.97)。
- **Primary:** 靛蓝实心（#6366f1）白字；hover 提亮至 #818cf8，active 加深至 #4f46e5——亮进暗出的因果方向。
- **Default:** 白底 #334155 字 1px #e2e8f0 边；hover 转淡彩底 #eef2ff + 靛蓝字边，即「按钮被聚焦进靛蓝世界」。
- **Danger/Success/Warning:** 同 primary 逻辑，各自语义色，hover 提亮 active 加深。
- **Focus:** 键盘 focus-visible 时 2px 靛蓝 outline 外扩 2px。

### Tags（状态标签）
- **Style:** 淡彩底 + 同色系深字，无边框，圆角 6px，字重 500；状态切换时颜色 150ms 过渡。
- **语义映射:** 订单/支付/充值状态 → el-tag 类型的映射统一收敛在 `src/constants/dict.js`，页面禁止自写 switch。

### Cards / Containers
- **Corner Style:** 12px。
- **Background:** #ffffff，置于 #f1f5f9 页面底上。
- **Shadow Strategy:** 见 Elevation——静止仅着地阴影。
- **Border:** 无边框。
- **Internal Padding:** 筛选区/统计卡/快捷操作区 20px 24px；统计卡内图标容器 52×52px、圆角 12px、语义色 10% 透明底 + 实色图标。

### Inputs / Fields
- **Style:** #f8fafc 弱底 + 1px #e2e8f0 内描边 ring，圆角 8px，文字 #1e293b，占位符 #94a3b8。
- **Focus:** 靛蓝 1px ring + 3px rgba(99,102,241,0.15) 光晕；hover 边框加深至 #cbd5e1。
- **Error / Disabled:** Element Plus 默认错误红；禁用态由 EP 灰化。

### Tables
- **Style:** 白卡包裹（.table-card），表头 #f8fafc 底 13px/600 #0f172a 字，行 hover #f8fafc，行间 1px #f1f5f9 分隔，竖线全隐藏，容器圆角 12px 裁切。
- **Cell:** 垂直内边距 10px，全表 tabular numbers，金额列右对齐。

### Dialogs
- **Shape:** 圆角 16px，阴影弹窗级；头部/底部 1px #e2e8f0 分隔，标题 16px/600。
- **Padding:** 头 20px 24px 16px，体 24px，底 16px 24px 20px。
- **Motion:** 进场 scale(0.96)→1 + 淡入（220ms 减速），离场 150ms 加速淡出；表单项间距 20px，末项归零。

### Navigation
- **Sidebar:** 墨色底，菜单项 44px 高圆角 8px，默认 #cbd5e1 字；hover 10% 靛蓝底 + #e2e8f0 字；激活 20% 靛蓝底 + #a5b4fc 字。分组标题与一级项同规格，二级项 38px 高 13px 字拉开层级。当前所在分组自动展开。
- **Navbar:** 白底 52px，面包屑 + 右侧用户下拉（靛蓝 32px 圆头像 + 用户名首字）。

### 签名组件：统计卡（Stat Card）
全站统计卡统一为「纵向堆叠」语言（live/generate 打磨采纳，Home/订阅/用量看板一致）：白卡 12px 圆角，头行 = 28px 语义色淡底图标与 13px 灰标签同行，数值 26px/700 全墨色 tabular-nums 独占一行，同排卡片共享基线。仪表盘四卡中今日收入带 ¥ 与滚动动画（reduced-motion 直接落定）。这套「淡底图标容器」语言同样出现在快捷操作区。

## Do's and Don'ts

### Do:
- **Do** 从 `:root` token 取色（`var(--color-primary)` 等），新颜色先进 base.css 再使用。
- **Do** 动效只用全局三档时长（--motion-fast/base/slow）与三条缓动（--ease-standard/decelerate/accelerate）；进场减速、离场加速、离场时长 ≤ 进场 60%。
- **Do** 新页面复用 `.page-container / .filter-container / .table-card` 骨架与 useTableList composable，禁止另起炉灶。
- **Do** 状态 → 标签颜色的映射进 `src/constants/dict.js` 统一维护。
- **Do** 全部交互元素可达：键盘 focus-visible 有 2px 靛蓝 outline，触控/点击目标 ≥ 24px。
- **Do** 尊重 `prefers-reduced-motion`：CSS 过渡/动画全局降级，JS 数字滚动等数据动效直接落定终值。

### Don't:
- **Don't** 引入暗色模式——本系统是明确的纯亮色后台，不做暗色适配。
- **Don't** 在组件里写死色值绕过 token；淡彩标签必须「淡彩底 + 深字」，实心饱和色块只属于按钮。
- **Don't** 为组件另立时长/缓动/阴影常量，或在静止元素上加浮起阴影。
- **Don't** 在 scoped 样式里重新定义 Element Plus 已被全局换肤的属性（表格表头、分页、弹窗圆角等）。
- **Don't** 使用 v-html、裸 localStorage 存权限、前端 localStorage 判权限（安全红线，非视觉但同样硬性）。

# SmartWash Web UI 重设计 — 设计方案

日期: 2026-05-03
状态: 已确认

## 设计目标

将现有 Element Plus 默认风格的后台管理系统升级为更简约、高级的视觉效果。核心方向：**深邃高级**风格 + **Indigo/Slate** 配色。

## 配色系统

| 用途 | CSS 变量 | 色值 |
|---|---|---|
| 侧边栏底色 | `--sidebar-bg` | `#0f172a` |
| 侧边栏激活 | `--sidebar-active` | `rgba(99,102,241,0.15)` |
| 主色调 | `--color-primary` | `#6366f1` |
| 主色浅 | `--color-primary-light` | `#818cf8` |
| 内容区背景 | `--bg-page` | `#f1f5f9` |
| 卡片背景 | `--bg-card` | `#ffffff` |
| 边框色 | `--border-color` | `#e2e8f0` |
| 主文字 | `--text-primary` | `#0f172a` |
| 次要文字 | `--text-secondary` | `#64748b` |
| 弱化文字 | `--text-muted` | `#94a3b8` |
| 成功色 | `--color-success` | `#10b981` |
| 危险色 | `--color-danger` | `#ef4444` |

通过覆盖 Element Plus CSS 变量实现全局生效，无需逐个组件修改。

## 侧边栏 (Sidebar.vue)

- 宽度: 220px
- 背景色: `#0f172a`，无右边框
- Logo 区域: 顶部品牌名 "SmartWash"（白色粗体）+ 副标题 "管理系统"（slate-500 小字）
- 菜单项: 圆角 8px，左侧留白 12px
- 激活态: Indigo 半透明背景 + `#a5b4fc` 高亮文字
- 未激活: `#94a3b8` 浅灰文字，hover 时变亮
- 每个菜单项前增加 Element Plus 图标（HomeFilled、Document、User 等），从 `@element-plus/icons-vue` 引入

## 顶栏 (Navbar.vue)

- 高度: 52px
- 背景: 纯白，底部 `#e2e8f0` 1px 边框
- 左侧: Element Plus 面包屑，最后一级加粗
- 右侧: 首字母圆形头像（Indigo 底色 + 白色字母）+ 用户名
- 头像改为 computed 计算首字母，移除硬编码的外部图片 URL

## 内容区 (Layout.vue)

- 背景: `#f1f5f9`
- 内边距: 20px
- 统计卡片: 白色圆角 12px，微阴影
- 数据表格: 白色圆角卡片包裹，表头浅灰底色

## 登录页 (LoginPage.vue)

- 保持双栏布局（左品牌区 + 右表单区）
- 左侧: 深色渐变 `linear-gradient(135deg, #0f172a, #1e293b, #312e81)`，无 SVG 点阵装饰
- 右侧: 白色底，表单保持现有结构
- 输入框: 圆角 8px，聚焦时 Indigo 光晕 `box-shadow: 0 0 0 3px rgba(99,102,241,0.15)`
- 登录按钮: Indigo `#6366f1`，圆角 8px，hover 时变深 `#4f46e5` 并上浮 1px

## Element Plus 全局主题覆盖

在 `main.js` 或单独的 `styles/theme.css` 中覆盖 Element Plus CSS 变量：

```css
:root {
  --el-color-primary: #6366f1;
  --el-color-primary-light-3: #818cf8;
  --el-border-radius-base: 8px;
  --el-border-radius-small: 6px;
}
```

## 实施范围

需修改的文件：
- `src/components/Layout/Sidebar.vue` — 重写样式，增加图标
- `src/components/Layout/Navbar.vue` — 重写样式，头像改为首字母
- `src/components/Layout/Layout.vue` — 背景色调整
- `src/views/LoginPage.vue` — 配色和细节升级
- `src/assets/main.css` — 覆盖 Element Plus 全局 CSS 变量
- 各 CRUD 页面 — 无需逐个修改，通过全局变量自动生效

无需新增依赖，所有改动仅涉及 CSS 和模板微调。

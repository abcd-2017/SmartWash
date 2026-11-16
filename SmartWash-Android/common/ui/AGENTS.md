# common:ui 模块

## 模块身份
- **Gradle 坐标**: `common:ui`
- **职责**: 清氧设计系统实现 + 共享 UI 组件

## 包结构
```
com.smartwash.common.ui
├── components/
│   ├── AppComponents.kt    — PageHeader, AppCard, AppButton, AppTabBar, SettingRow
│   ├── InfoRow.kt          — 信息行组件
│   ├── InfoSection.kt      — 信息分组卡片
│   ├── AppTextField.kt            — 统一输入框（规范 §3.7 唯一标准）
│   ├── AppPasswordTextField.kt    — 密码输入框（尾缀图标化）
│   └── AppVerificationCodeField.kt — 验证码输入框（描边发送按钮）
├── navigation/
│   ├── ShellRoute.kt            — 壳层路由契约
│   ├── NavGraphContributor.kt   — 页面注册贡献者（fun interface + @IntoSet）
│   ├── NavGraphAmbient.kt       — 图注册期组合值快照（reduceMotion）
│   └── LocalSharedTransitionScope.kt — 共享元素转场 scope 的 CompositionLocal
└── theme/
    ├── AppDesign.kt        — 设计 Token + 可复用组件
    ├── Color.kt            — 颜色定义（自然绿 #2D9B6A 等）
    ├── Theme.kt            — 主题配置
    └── Type.kt             — 字体排版
```

## 公开 API
- `AppDesign` — 设计系统入口（卡片、间距、圆角等 Token）
- `AppButton / AppCard / AppTabBar / PageHeader` — 通用组件
- `ShellRoute` — 壳层路由契约接口
- `NavGraphContributor` — 页面注册贡献者契约（各域/变体 `@Provides @IntoSet`，壳层 `MainActivity` 注入 `Set` 统一注册；替代硬编码 `xxxGraph` 调用）
- `LocalSharedTransitionScope` — 共享元素转场 scope 的 CompositionLocal（消费页读取，null 时退化为普通转场）
- `SmartWashTheme` — Compose 主题包装

## 依赖关系
- **依赖**: Compose Material3、Compose Navigation、common:model
- **被依赖**: app 壳层 + 所有 feature:impl 页面

## 内部约定
- ⚠️ **设计系统规范**: 
  - 米白底 + 白卡 + 自然绿(#2D9B6A) 主色调
  - 认证页使用毛玻璃效果，功能页极简白卡风格
  - 新页面必须遵循现有 token，禁止自定义颜色/间距
- 组件默认使用 AppDesign 中的 token，覆写需传参而非硬编码
- ShellRoute 定义壳层导航结构，feature 模块实现具体路由
- **页面注册统一走 `NavGraphContributor`**：域 impl 或 flavor 源集用 `@Provides @IntoSet` 贡献，`MainActivity` 注入 `Set<NavGraphContributor>` 后统一 `register(navController)`；禁止在壳层硬编码逐个 `xxxGraph` 调用。
- `NavGraphBuilder` 不是组合上下文，图内无法读 `LocalReduceMotion`；`NavGraphAmbient` 在组合期快照该值，转场 lambda 在导航调用时读取。

## 已知坑
- 组件默认样式在深色模式下可能未完全适配（token 覆盖不全）
- InfoSection 的卡片嵌套层级过深时需注意性能

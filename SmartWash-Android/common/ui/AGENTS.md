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
│   ├── PasswordInput.kt    — 密码输入框
│   └── PhoneNumberInput.kt — 手机号输入框
├── navigation/
│   └── ShellRoute.kt       — 壳层路由契约
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

## 已知坑
- 组件默认样式在深色模式下可能未完全适配（token 覆盖不全）
- InfoSection 的卡片嵌套层级过深时需注意性能

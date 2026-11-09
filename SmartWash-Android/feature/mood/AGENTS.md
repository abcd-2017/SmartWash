# AGENTS.md — feature:mood（情绪气象站）

## 1. 模块身份

- **Gradle 坐标**：`:feature:mood`
- **包名**：`com.smartwash.feature.mood`
- **职责**：学生情绪记录与可视化——今日打卡（1-5 级「暴雨/阴/多云/晴/大晴」）、月历视图、月趋势统计。单模块域（无 api/impl 拆分），结构照 `:feature:toolbox` 先例。
- **入口**：
  - 深链 `smartwash://mood`（本模块 `di/MoodDeepLinkModule` 经 `@IntoSet` 贡献 host `mood` → `MoodRoute.Today`）
  - 后端 `workbench_entry` 配置的工作台卡片
  - 无底栏入口

## 2. 包结构

```
com.smartwash.feature.mood/
├── MoodRoute.kt                   # Today / Calendar / Trend
├── MoodConstant.kt                # 分页大小、月份格式等常量
├── di/
│   ├── MoodModule.kt              # Hilt 模块（MoodRepository 绑定）
│   └── MoodDeepLinkModule.kt      # host "mood" → MoodRoute.Today
├── network/MoodApi.kt             # PUT today、GET calendar、GET stats
├── repository/
│   ├── MoodRepository.kt
│   └── MoodRepositoryImpl.kt
└── ui/
    ├── MoodGraph.kt               # moodGraph 聚合 3 个页面
    ├── MoodLevel.kt               # 情绪等级模型与色映射
    ├── MoodWidgets.kt             # 共享小组件
    ├── today/
    │   ├── MoodTodayPage.kt       # 今日打卡
    │   └── MoodTodayViewModel.kt
    ├── calendar/
    │   ├── MoodCalendarPage.kt    # 月历
    │   └── MoodCalendarViewModel.kt
    └── trend/
        ├── MoodTrendPage.kt       # 月趋势
        └── MoodTrendViewModel.kt
```

## 3. 公开 API

| 暴露点 | 消费方 | 说明 |
|--------|--------|------|
| `NavGraphBuilder.moodGraph` | app 壳 MainActivity NavHost | 一行聚合 3 个页面 |
| `MoodDeepLinkModule`（Hilt） | 壳层 DeepLinkRegistry | `@IntoSet` 贡献 host `mood` |
| `MoodRoute` | 模块内自用 | 路由单一事实来源 |

## 4. 依赖关系

- **本模块依赖**：`:common:utils`（ApiResult / AnimationUtils）、`:common:network`（Retrofit / `@RequireAuthorization`）、`:common:ui`（清氧令牌 + 共享组件）。
- **谁依赖本模块**：`:app`（壳层聚合 `moodGraph`）。
- **明确不依赖**：`:common:database`、`:common:init`。

## 5. 接口契约

> 契约源：后端 `SmartWash/src/main/java/com/smartwash/mood/controller/MoodController.java`，前缀 `/web/auth/mood`，需 ROLE_USER。

| 方法 | 端点 | 说明 |
|------|------|------|
| PUT | `web/auth/mood/today` | 今日情绪 upsert（同用户同月内覆盖） |
| GET | `web/auth/mood/calendar?ym=yyyy-MM` | 月历（每日记录 + 等级） |
| GET | `web/auth/mood/stats?ym=yyyy-MM` | 月统计（记录数 / 用户数 / 等级分布） |

## 6. 内部约定

1. **契约以后端为源**：路径 / 参数 / 响应结构以后端 `MoodController` 为准，字段全可空容错。
2. **时间口径**：`ym` 统一 `yyyy-MM` 字符串；展示换算用 `TimeUtil`/本地格式化，业务判定信服务端。
3. **动效**：只允许 `:common:utils` AnimationUtils vocabulary，且每个动效必须有 `LocalReduceMotion` 降级分支。
4. **文案与色值**：文案进本模块 `strings.xml`；情绪等级色取 `:common:ui` 语义令牌，禁止写死色值。
5. **数字显示**：计数 / 百分比必须 tabular numbers（`fontFeatureSettings = "tnum"`）。

## 7. 已知坑

- **无底栏 tab**：`mood` 只经深链与工作台卡片进入；未登录时深链由壳层拦截。
- **测试**：`feature/mood/src/test/` 当前只有空包目录，无测试文件（共享规则下新增测试不入库）。

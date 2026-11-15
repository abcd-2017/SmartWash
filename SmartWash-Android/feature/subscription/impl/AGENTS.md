# AGENTS.md — :feature:subscription:impl（订阅逃生舱实现）

## 1. 模块身份

- **Gradle 坐标**：`:feature:subscription:impl`
- **包名**：`com.smartwash.feature.subscription`（与模块路径解耦，契约见 `:feature:subscription:api`）
- **职责**：订阅全生命周期管理——订阅列表 / 新建编辑 / 详情 / 用量打卡 / 到期提醒。
- **入口**：
  - 深链 `smartwash://subscription`（本模块 `di/SubscriptionDeepLinkModule` 经 `@IntoSet` 贡献 host `subscription` → `SubscriptionRoute.List`）
  - 后端 `workbench_entry` 配置的工作台卡片
  - 无底栏入口

## 2. 包结构

```
com.smartwash.feature.subscription/（契约在 :feature:subscription:api）
├── logic/SubscriptionRules.kt     # 纯逻辑：状态流转 / 周期文案 / 到期量级
├── paging/
│   ├── SubscriptionPagingSource.kt
│   └── SubscriptionUsagePagingSource.kt
├── repository/
│   ├── SubscriptionRepository.kt
│   └── SubscriptionRepositoryImpl.kt
├── di/
│   ├── SubscriptionModule.kt      # Hilt 模块（Repository 绑定）
│   └── SubscriptionDeepLinkModule.kt
└── ui/
    ├── SubscriptionGraph.kt       # subscriptionGraph 聚合 4 个页面
    ├── SubscriptionUiCommon.kt    # 共享组件（状态 chip / 周期标签 / 空态）
    ├── list/                      # 订阅列表（分页 + 状态筛选）
    ├── edit/                      # 新建 / 编辑
    ├── detail/                    # 详情（含用量打卡入口）
    └── reminder/                  # 到期提醒列表
```

## 3. 公开 API

| 暴露点 | 消费方 | 说明 |
|--------|--------|------|
| `NavGraphBuilder.subscriptionGraph` | app 壳 MainActivity NavHost | 一行聚合 4 个页面 |
| `SubscriptionDeepLinkModule`（Hilt） | 壳层 DeepLinkRegistry | `@IntoSet` 贡献 host `subscription` |
| `SubscriptionRoute` | 模块内自用 | 路由单一事实来源 |
| `SubscriptionRules` | 模块内 + 页面 | 状态流转与文案纯逻辑（可测） |

## 4. 依赖关系

- **本模块依赖**：`:common:utils`（ApiResult / PageData / AnimationUtils / pagingFlow）、`:common:network`（Retrofit / `@RequireAuthorization`）、`:common:ui`（清氧令牌 + 共享组件）。
- **谁依赖本模块**：`:app`（壳层聚合 `subscriptionGraph`）。
- **明确不依赖**：`:common:database`、`:common:init`。

## 5. 接口契约

> 契约源：后端 `SmartWash/src/main/java/com/smartwash/subscription/controller/UserSubscriptionController.java`，前缀 `/web/auth/subscription`，需 ROLE_USER。

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `web/auth/subscription` | 新建订阅 |
| PUT | `web/auth/subscription/{id}` | 更新订阅 |
| DELETE | `web/auth/subscription/{id}` | 删除订阅 |
| GET | `web/auth/subscription?page=&size=&status=&keyword=` | 订阅分页 |
| GET | `web/auth/subscription/{id}` | 订阅详情 |
| POST | `web/auth/subscription/{id}/pause` `/resume` `/cancel` `/finish` | 状态流转（暂停/恢复/取消/完成） |
| POST | `web/auth/subscription/{id}/usages` | 用量打卡 |
| GET | `web/auth/subscription/{id}/usages?page=&size=` | 用明明细分页 |
| DELETE | `web/auth/subscription/{id}/usages/{usageId}` | 删除明细 |
| GET | `web/auth/subscription/reminders?unread=` | 到期提醒列表 |
| POST | `web/auth/subscription/reminders/{id}/read` | 标记提醒已读 |
| GET | `web/auth/subscription/stats` | 订阅统计 |

## 6. 内部约定

1. **状态以后端为准**：状态枚举（active/paused/canceled/finished）与可流转动作由后端裁定，端上只做展示与按钮可见性；金额 / 次数用 tabular numbers。
2. **分页**：统一用 `pagingFlow()`（`:common:utils`），列表给 `key`，避免重复项。
3. **跨页同步**：新建 / 编辑 / 删除成功后经 `SubscriptionRoute.CHANGED_KEY`（savedStateHandle 信号）通知列表重拉（模式同 `ToolboxRoute.CHANGED_KEY`）。
4. **动效**：只允许 `:common:utils` AnimationUtils vocabulary，且每个动效必须有 `LocalReduceMotion` 降级分支。
5. **文案**：用户可见文本一律进本模块 `res/values/strings.xml`。

## 7. 已知坑

- **无底栏 tab**：只经深链与工作台卡片进入；未登录时深链由壳层拦截。
- **提醒已读幂等在后端**：重复标记静默成功，端上不做请求去重。
- **测试**：`feature/subscription/src/test/` 当前无测试文件（共享规则下新增测试不入库）。

# AGENTS.md — :feature:notice:impl

## 模块身份

- **Gradle 坐标**: `:feature:notice:impl`
- **包名**: `com.smartwash.feature.notice`（注意：实际包路径短于 Gradle 路径，与模块路径解耦）
- **职责**: 站内信域实现——站内信网络链路与收件箱页（未读高亮/已读灰化 + 全部已读 + 点击标记已读与深链跳转）；对外经 `:feature:notice:api` 的 `NoticeRoute` + `NoticeApi` 暴露（di `@Binds`）。

## 包结构

```
src/main/java/com/smartwash/feature/notice/
├── di/
│   └── NoticeModule.kt          # Hilt 模块（@Binds NoticeApi + 提供 NoticeServiceApi + 深链 host "messages"）
├── logic/
│   └── NoticeLogic.kt           # 纯逻辑（NoticeType 文案映射 + 时间解析/相对分桶，JVM 单测）
├── repository/
│   ├── NoticeRepository.kt      # 站内信仓库（网络链路唯一出口）
│   └── NoticeApiImpl.kt         # NoticeApi @Binds 实现（跨域未读数）
└── ui/
    ├── NoticeGraph.kt           # 路由聚合（noticeGraph）
    └── inbox/
        ├── NoticeInboxPage.kt   # 收件箱页 Composable
        └── NoticeInboxViewModel.kt # 收件箱页 ViewModel
```

## 公开 API

| 符号 | 说明 |
|------|------|
| `NavGraphBuilder.noticeGraph(navController)` | 路由聚合扩展，壳层 `MainActivity` 一行调用 |
| `NoticeApiImpl` | `@Binds` 实现，供 user-impl 经 `NoticeApi` 注入未读数 |

## 依赖关系

- **此模块依赖**: `:feature:notice:api`、`:common:utils`、`:common:network`、`:common:ui`；禁止依赖 app 壳与其他 feature-impl。
- **谁依赖此模块**: `:app`（NavHost 聚合 + Hilt 图）、`:feature:user:impl` 经 `NoticeApi`（api 模块）。

## 内部约定

- **状态划分**: `listState`（首载）/ `loadMoreState`（加载更多）/ `readAllState`（全部已读）/ `unreadCount`（未读数）独立，未读数失败仅留痕不拖垮列表。
- **乐观更新**: 标记已读立即置灰 + 未读减一，失败回滚并静默重拉未读数；全部已读成功整列表置已读 + 未读清零。
- **防重**: 列表加载中忽略重复 load；加载更多加载中/无更多忽略；全部已读加载中忽略 + 二次确认；已读条目重复点击不再请求。
- **深链复用壳层入口**: `smartwash://` 走 ACTION_VIEW（MainActivity onNewIntent → DeepLinkHandler）；`http(s)://` 落 `ShellRoute.WebView`。
- **分页**: 首载 20 条 + 「加载更多」手动累加（公告量级小，未引 Paging 3）。
- **时间文案**: 纯逻辑 `noticeTimeParts` 只返回分桶与分量，中文文案（今天/昨天/M月d日）由 UI 经 stringResource 组装。

## 已知坑

- **ACTION_VIEW 依赖 MainActivity**: smartwash:// 跳转走 Activity intent-filter（singleTask + onNewIntent），不是 navController 直接导航；未注册 host 由壳层 Toast「暂不支持该链接」。
- **包名与模块路径解耦**: 实际包名 `com.smartwash.feature.notice`（非 `com.smartwash.feature.notice.impl`）。
- **深链 host**: `messages` 未进壳层 `PUBLIC_DEEP_LINK_HOSTS`，未登录由壳层拦截落登录页（对齐受保护 host 语义）。

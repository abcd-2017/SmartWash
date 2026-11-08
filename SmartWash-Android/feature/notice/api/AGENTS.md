# AGENTS.md — :feature:notice:api

## 模块身份

- **Gradle 坐标**: `:feature:notice:api`
- **包名**: `com.smartwash.feature.notice.api`
- **职责**: 站内信域对外契约——`NoticeRoute` 路由常量 + `NoticeVo` 模型（与后端 `notice/vo/NoticeVo` 一一对应）+ `NoticeServiceApi` 网络接口 + `NoticeApi` 跨域服务契约（我的页未读角标）。

## 包结构

```
src/main/java/com/smartwash/feature/notice/api/
├── NoticeRoute.kt               # 路由常量密封接口（Inbox，路由串 "Messages"）
├── NoticeServiceApi.kt          # Retrofit 接口（inbox/unreadCount/markRead/readAll）
├── NoticeApi.kt                 # 跨域服务契约（getUnreadCount）
└── model/
    └── NoticeVo.kt              # 站内信条目模型（id/title/content/type/deepLink/isRead/createdAt）
```

## 公开 API

| 符号 | 类型 | 说明 |
|------|------|------|
| `NoticeRoute` | `sealed interface` | 站内信域路由常量 |
| `NoticeRoute.Inbox` | `data object` | 收件箱页（深链 host "messages" 落点） |
| `NoticeServiceApi` | `interface` | Retrofit 接口（`@RequireAuthorization`，路径 `/web/auth/notices`） |
| `NoticeApi` | `interface` | 跨域服务契约（`suspend getUnreadCount(): Long`） |
| `NoticeVo` | `data class` | 收件箱条目（全字段可空 + safe 系兜底） |

## 依赖关系

- **此模块依赖**: `:common:utils`（ApiResult/PageData/SmartWashRoute）、`:common:network`（@RequireAuthorization/retrofit 传递）；不引 Hilt 运行时（铁律 1）。
- **谁依赖此模块**:
  - `:feature:notice:impl` — 域内页面 + `NoticeApiImpl`（`@Binds` 实现）+ 深链贡献者
  - `:feature:user:impl` — 我的页「消息中心」入口（`NoticeRoute`）+ 未读角标（`NoticeApi`）
  - `:app` — NavHost 经 `noticeGraph` 聚合

## 内部约定

- **契约以后端为源**: 路径/参数/响应结构以后端 `WebNoticeController` 为契约源；`page` 从 1 起、`size` 上限 100（后端 `BaseSearchFrom`）。
- **模型全可空**: Gson 缺字段/类型漂移置 null，消费方一律走 `safeXxx`；`isRead` 缺失按未读处理（宁可高亮也不误灰化）。
- **createdAt 保持字符串**: Gson 默认不支持 `java.time`，时间解析/格式化由 impl 的 `noticeTimeParts` 容错处理。
- **NoticeApi 类型面收窄**: 只暴露未读数（Long），不泄漏 `NoticeVo` 全量字段；实现见 impl 的 `NoticeApiImpl`。

## 已知坑

- **data 为 null 语义**: 收件箱 data 缺失按失败上抛 `NetworkException`；未读数 data 缺失按 0 处理（角标隐藏，不拖垮我的页）。
- **已读幂等在后端**: 重复标记/并发撞唯一索引均静默成功，端上不做请求去重。
- **包名与模块路径解耦**: impl 实际包名 `com.smartwash.feature.notice`（非 `.impl`），与 coupon/points 同模式。

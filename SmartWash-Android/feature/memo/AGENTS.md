# AGENTS.md — :feature:memo（速记手账）

flomo 式随手记子系统（单模块，照 `:feature:mood`/`:feature:toolbox` 先例）：顶部随手记输入区 +
记录卡片列表（置顶优先，排序由后端保证）+ 标签筛选（tags 聚合接口驱动）+ 搜索（实时过滤）+ 分页。

## 包结构

```
com.smartwash.feature.memo/
├── MemoRoute.kt               # 类型安全路由（MemoRoute.Page，@SerialName("Memo")）
├── MemoConstant.kt            # 模块内常量（分页条数 / 内容上限）
├── logic/MemoTimeFormat.kt    # createTime/updateTime 展示格式化（纯函数）
├── network/MemoApi.kt         # Retrofit 接口 + 请求体/VO（/web/auth/memo 五端点）
├── paging/MemoPagingSource.kt # 列表分页源（keyword/tag 过滤透传）
├── repository/MemoRepository.kt        # 数据契约（接口）
├── repository/MemoRepositoryImpl.kt    # 薄封装 Api，data 为 null 即失败
├── di/MemoModule.kt           # Hilt 供给（Api @Provides + Repository @Binds）
├── di/MemoDeepLinkModule.kt   # 深链贡献者：host "quick-memo" → MemoRoute.Page
└── ui/
    ├── MemoGraph.kt           # NavGraphBuilder.memoGraph 聚合 + navigateToMemo
    └── memo/MemoPage.kt + MemoViewModel.kt   # 单页面 + VM（composer/搜索/筛选/分页流）
```

## 对外依赖关系

- 仅被 `:app` 壳依赖（`implementation project(":feature:memo")`），不依赖任何其他 feature 域。
- 上游：`common:utils` / `common:network` / `common:ui`。

## 内部约定

- 后端契约：`POST /web/auth/memo`（content≤2000 + tags[]）、`GET /web/auth/memo/list`（page/size/
  keyword/tag，PageData 分页，置顶优先由后端排序）、`PUT /web/auth/memo/{id}`（content/tags/pinned）、
  `DELETE /web/auth/memo/{id}`（逻辑删除，端上删除即移出列表）、`GET /web/auth/memo/tags`（tag+count 聚合）。
- 列表刷新统一走「refreshTick 合入 pagingFlow 触发流」；搜索词/标签筛选变化即换 Pager（debounce 300ms）。
- 编辑复用顶部输入区回填（editingId/editingPinned 随 composer 携带，更新走 PUT 保持原 pinned）。
- 深链 host `quick-memo` 为受保护 host（全部接口 @RequireAuthorization），壳层登录守卫默认拦截。
- 用户可见文案全部在 `res/values/strings.xml`；非用户可见常量在 `MemoConstant`。

## 已知坑

- 下拉刷新/变更重建会清空列表，空态判定沿用 toolbox 的 refreshSession/everLoaded 会话门控，防闪一帧空态。
- PagingSource.getRefreshKey 必须由锚点反推页码（key 是页码不是位置，见 toolbox 2026-11-01 闪退修复）。

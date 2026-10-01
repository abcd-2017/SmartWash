# AGENTS.md — :feature:laundry:impl

## 模块身份

- **Gradle 坐标**: `:feature:laundry:impl`
- **包名**: `com.smartwash.feature.laundry`（注意：实际包路径短于 Gradle 路径，与模块路径解耦）
- **职责**: 洗衣域实现——洗衣项目 + 学校（同属校园洗衣基础数据）的三级缓存仓储与服务/洗衣预约页面，对外经 `:feature:laundry:api` 的 `LaundryRoute` + `SchoolSearchSource` 暴露。

## 包结构

```
src/main/java/com/smartwash/feature/laundry/
├── LaundryImplConstant.kt                   # 模块内常量（APP_NAME）
├── di/
│   └── LaundryModule.kt                     # Hilt 模块（@Binds SchoolSearchSource + 提供 LaundryItemsApi/SchoolApi）
├── network/
│   ├── api/
│   │   ├── LaundryItemsApi.kt               # Retrofit 洗衣项目接口（GET /web/laundryItems/all）
│   │   └── SchoolApi.kt                     # Retrofit 学校接口（GET /web/schools/allName）
│   └── vo/
│       ├── LaundryItem.kt                   # 洗衣项目 VO
│       └── SchoolName.kt                    # 学校 VO
├── repository/
│   ├── LaundryRepository.kt                 # 洗衣项目仓库（cache-first 降级）
│   ├── SchoolRepository.kt                  # 学校仓库（内存→Room→网络 三级缓存）
│   ├── SchoolSearchSourceImpl.kt            # SchoolSearchSource @Binds 实现
│   └── EntityMappers.kt                     # VO ↔ Entity 映射
└── ui/
    ├── LaundryGraph.kt                      # 路由聚合（laundryGraph）
    ├── laundry/
    │   ├── LaundryPage.kt                   # 洗衣预约页
    │   └── LaundryViewModel.kt             # 注入 UserApi / OrderApi
    └── service/
        ├── ServicePage.kt                   # 服务 tab 页
        └── ServiceViewModel.kt
```

## 公开 API

| 符号 | 说明 |
|------|------|
| `NavGraphBuilder.laundryGraph(navController)` | 路由聚合扩展，壳层 `MainActivity` 一行调用 |
| `SchoolSearchSourceImpl` | `@Binds` 实现，供外部（user-impl）经接口注入 |

## 依赖关系

- **此模块依赖**:
  - `:feature:laundry:api` — 本域路由常量 + 学校搜索契约
  - `:feature:user:api` — `UserApi`（预约页展示用户学校）
  - `:feature:order:api` — `OrderApi`（选套餐下单）
  - `:feature:payment:api` — `PaymentRoute`（预约成功跳支付页）
  - `:common:model` / `:common:network` / `:common:ui` / `:common:utils` / `:common:database`
- **谁依赖此模块**: 无（外部经 api 接口 `SchoolSearchSource` 消费，不触碰本模块实现）

## 内部约定

- **三级缓存策略**: `SchoolRepository` 内存 → Room → 网络，整条加载链路持 `Mutex`（single-flight，冷启动并发请求串行等待后命中内存缓存）。
- **Cache-first 降级**: `LaundryRepository.getLaundryItems` 有缓存时网络失败静默返回缓存；无缓存时网络失败抛异常。
- **事务保护**: `deleteAll + insertAll` 包进 `appDatabase.withTransaction`，中途失败不清空缓存。
- **取消传播**: `catch` 协程异常先 `rethrow CancellationException`，避免取消被当网络错误。
- **DAO 不暴露**: DAO 统一收回 Repository 内部，不对 ViewModel 暴露。
- **跨域数据经接口**: `LaundryViewModel` 注入 `UserApi` / `OrderApi`，禁止触碰 `user-impl` / `order-impl` 实现。

## 已知坑

- **包名与模块路径解耦**: 实际包名 `com.smartwash.feature.laundry`（非 `com.smartwash.feature.laundry.impl`），与 coupon:impl 同模式——引用时注意路径。
- **Room 缓存表共库**: `LaundryItem`/`SchoolName` 表在 `:common:database`（三表共库），拆表归 T8.2 统一评估，本模块不扩面。
- **Mutex 不可重入**: `refreshFromNetwork` 仅在 `getAllSchools` 持锁期间调用，内部不再加锁。
- **后台刷新静默**: `SchoolRepository.refreshFromNetwork` 失败静默处理，不抛异常。

# AGENTS.md — :feature:laundry:api

## 模块身份

- **Gradle 坐标**: `:feature:laundry:api`
- **包名**: `com.smartwash.feature.laundry.api`
- **职责**: 洗衣域对外契约——`LaundryRoute` 路由常量 + `SchoolSearchSource` 学校搜索接口 + `SchoolOption` 模型（洗衣域存在真实跨模块数据消费方：user-impl 资料编辑页按关键字搜学校）。

## 包结构

```
src/main/java/com/smartwash/feature/laundry/api/
├── LaundryRoute.kt              # 路由常量密封类（Laundry / Service）
├── SchoolSearchSource.kt        # 学校搜索契约接口
└── model/
    └── SchoolOption.kt          # 学校选项模型（schoolId / schoolName）
```

## 公开 API

| 符号 | 类型 | 说明 |
|------|------|------|
| `LaundryRoute` | `sealed class` | 洗衣域路由常量 |
| `LaundryRoute.Laundry` | `data object` | 洗衣预约页 |
| `LaundryRoute.Service` | `data object` | 服务 tab 页 |
| `SchoolSearchSource` | `interface` | 学校搜索契约（`suspend search(keyword): List<SchoolOption>`） |
| `SchoolOption` | `data class` | `{schoolId: Long, schoolName: String}` |

## 依赖关系

- **此模块依赖**: 无项目依赖（纯 Kotlin，不引 Hilt 运行时）
- **谁依赖此模块**:
  - `:feature:laundry:impl` — 域内页面 + `SchoolSearchSourceImpl`（`@Binds` 实现）
  - `:feature:user:impl` — 资料编辑页注入 `SchoolSearchSource` 搜学校
  - `:app` — NavHost 注册（`PageConstant` 委托 `LaundryRoute` 值）

## 内部约定

- **契约归数据持有方**: `SchoolSearchSource` 由洗衣域 api 持有（学校数据在洗衣域 `SchoolRepository`），消费方（user-impl）经接口访问，不得触碰 `laundry:impl` 实现。
- **模型不带 `@Keep`**: `SchoolOption` 是领域载荷，不直接参与 Gson 反序列化（网络层 `SchoolName` 在洗衣域内映射到本类）。
- **空关键字语义**: `search("")` 返回全量，与 `SchoolRepository.getAllSchools` 一致。
- **命名模式**: 仿 `user-api` 的 `UserApi` / `order-api` 的 `OrderApi`，路由常量仿 `PaymentRoute`。

## 已知坑

- **契约升级来源**: `SchoolSearchSource` 自 user-impl 的过渡 seam 迁入，原 app 壳 `UserImplSeamModule` 实现已删——不要回退到旧位置查找实现。
- **跨域消费方唯一**: 当前仅 user-impl 资料编辑页消费 `SchoolSearchSource`，不要假设其他消费方存在。

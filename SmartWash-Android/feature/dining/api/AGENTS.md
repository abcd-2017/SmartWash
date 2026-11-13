# AGENTS.md — :feature:dining:api

## 模块身份

- **Gradle 坐标**: `:feature:dining:api`
- **包名**: `com.smartwash.feature.dining.api`
- **职责**: 吃啥域对外契约——`DiningRoute` 类型安全路由 + 模型（与后端 `dining/vo`/`dining/from` 一一对应）+ `DiningServiceApi` 网络接口 + `DiningApi` 跨域服务契约。04 号票落菜谱竖切；07 号票落订单发起链路（订单列表/创建/分享）。

## 包结构

```
src/main/java/com/smartwash/feature/dining/api/
├── DiningRoute.kt               # 类型安全路由（OrderList/OrderCreate/OrderDetail/OrderShare/ShoppingList + RecipeLibrary/RecipeDetail/RecipeCreate）
├── DiningServiceApi.kt          # Retrofit 接口（菜谱 5 端点 + 订单 8 端点：create/my/orderDetail/join/brief/reset/finish/items）
├── DiningApi.kt                 # 跨域服务契约（getOnShelfRecipes，跨域消费方使用）
└── model/
    ├── DiningOrderStatus.kt     # 订单状态枚举（ONGOING/FINISHED，fromCode 白名单外返回 null 由映射层保守降级）
    ├── CreateDiningOrderRequest.kt # 创建订单请求体（title + recipeIds，或 fromOrderId 复用历史菜单——13 号票「再来一单」）
    ├── DiningOrderCreatedVo.kt  # 创建响应（orderId + joinToken——端上唯一凭证来源）
    ├── DiningOrderListVo.kt     # 我的订单列表条目（id/title/status/isCreator/createdAt/finishedAt）
    ├── DiningOrderDetailVo.kt   # 订单详情（轮询主接口：version/unchanged 轻量协议 + menu/items/members 嵌套）
    ├── DiningOrderFinishVo.kt   # 结束点单响应（幂等返回原快照，11 号票）
    ├── DiningShoppingListSnapshot.kt # 购买清单快照载荷（ingredientNames 并集 + perRecipe 按菜视图，11 号票）
    ├── DiningRecipeVo.kt        # 菜谱模型（id/categoryId/categoryName/name/coverUrl/ingredients/steps/source/status）
    ├── DiningCategoryVo.kt      # 分类模型（id/name/sort）
    └── AddDiningRecipeRequest.kt # 新增菜谱请求体（categoryId 哨兵协议 null/0=未分类）
```

## 公开 API

| 符号 | 类型 | 说明 |
|------|------|------|
| `DiningRoute` | `sealed interface` | dining 域路由常量（SmartWashRoute） |
| `DiningRoute.OrderList` | `data object` | 订单列表页（域首页；深链 host "dining" 落点 + 工作台入口 target） |
| `DiningRoute.OrderCreate` | `data class(fromOrderId, sourceTitle)` | 创建订单页（带参=复用历史菜单模式，13 号票） |
| `DiningRoute.OrderShare` | `data class(orderId, title, joinToken)` | 订单分享视图（joinToken 仅创建链路携带，null=从列表进入降级） |
| `DiningRoute.OrderDetail` | `data class(orderId)` | 点单页（09/10 号票；11 号票接入结束点单与 FINISHED 只读态） |
| `DiningRoute.ShoppingList` | `data class(orderId)` | 购买清单结果页（11 号票：按食材/按菜双视图） |
| `DiningRoute.RecipeLibrary/RecipeDetail/RecipeCreate` | — | 菜谱三页（04 号票） |
| `DiningServiceApi` | `interface` | Retrofit 接口（`@RequireAuthorization`，路径 `/web/auth/dining/**`） |
| `DiningApi` | `interface` | 跨域服务契约（上架菜谱分页，跨域消费方） |
| `DiningOrderStatus` | `enum` | 订单状态（ONGOING/FINISHED；文案由消费方 strings.xml 映射） |
| 各 Vo | `data class` | 全字段可空 + safe 系兜底 |

## 依赖关系

- **此模块依赖**: `:common:utils`（ApiResult/PageData/SmartWashRoute）、`:common:network`（@RequireAuthorization/retrofit 传递）；不引 Hilt 运行时（铁律 1）。
- **谁依赖此模块**:
  - `:feature:dining:impl` — 域内页面 + `DiningApiImpl`（`@Binds` 实现）+ 深链贡献者
  - `:app` — NavHost 经 `diningGraph` 聚合
  - 跨域消费方 — 经 `DiningApi` 拉上架菜谱（铁律 2：impl → 他人 api）

## 内部约定

- **契约以后端为源**: 路径/参数/响应结构以后端 `WebDiningRecipeController` / `WebDiningCategoryController` / `WebDiningOrderController` 为契约源；`page` 从 1 起、`size` 上限 100（后端 `BaseSearchFrom`）。
- **模型全可空**: Gson 缺字段/类型漂移置 null，消费方一律走 `safeXxx`；时间字段为 ISO 字符串原文（对齐洗衣订单 OrderVo.createdAt 先例，端上不反序列化 LocalDateTime）。
- **状态保守降级**: `DiningOrderStatus.fromCode` 白名单外返回 null，映射层（impl DiningMappers）统一降级 FINISHED（未知状态按不可写处理）。
- **joinToken 契约**: 仅创建响应（`DiningOrderCreatedVo.joinToken`）与重置接口可得；09 号票修补后发起者的详情全量响应也回传（供发起者从列表/详情再次生成二维码），参与者响应与轻量响应不回传。
- **version 轮询协议**: 详情接口 `knownVersion` 与服务端一致返回 `unchanged=true` 轻量响应（仅 id/version/unchanged）；10 号票已由点单页消费（带 version 轮询 + 写 409 冲突重放），写接口信封 409 由 impl 仓库转译为 `DiningVersionConflictException`。
- **仅上架契约**: 菜谱列表与详情后端固定过滤 status=1，本 api 无 status 入参——下架菜谱对端上不可见。
- **分类哨兵协议**: 菜谱新增/修改传 `null`/`0` 均为未分类、`>0` 由后端校验存在（GLOSSARY 10.10）。
- **DiningApi 类型面收窄**: 只暴露上架菜谱分页；跨域所需其他读取面按需扩展。
- **订单接口消费面**: create/my/orderDetail 为 07 号票消费面；brief/join 为 08 号票扫码加入消费；选菜增改删为 09/10 号票点单页消费（写接口携带 version，409 冲突重放）；finish 为 11 号票结束点单消费（仅发起者，幂等返回快照，端上据此进购买清单结果页）；create 的 fromOrderId 复用为 13 号票「再来一单」消费（订单列表卡入口）。

## 已知坑

- **路由串锚点**: 六个路由串由 `@SerialName` 固定，改动会破坏导航与深链（impl 有 DiningDeepLinkModuleTest 锚定 OrderList/OrderCreate/OrderShare/RecipeLibrary/RecipeCreate）。
- **域首页落点**: 深链 host "dining" → `DiningRoute.OrderList`（07 号票定稿，原菜谱库占位已移除）——见 impl DiningDeepLinkModule 注释。
- **包名与模块路径解耦**: impl 实际包名 `com.smartwash.feature.dining`（非 `.impl`），同 notice/coupon 先例。

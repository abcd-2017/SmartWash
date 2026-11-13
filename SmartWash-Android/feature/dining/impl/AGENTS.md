# AGENTS.md — :feature:dining:impl

## 模块身份

- **Gradle 坐标**: `:feature:dining:impl`
- **包名**: `com.smartwash.feature.dining`（注意：实际包路径短于 Gradle 路径，与模块路径解耦）
- **职责**: 家庭点单域实现——订单六页（07 号票发起链路：订单列表[域首页] → 创建订单 → 分享视图；09 号票点单页；11 号票购买清单结果页；12 号票站内信深链加入入口）+ 菜谱三页（04 号票：菜谱库/详情/新增，入口在订单列表页右上角）+ 共享购物车同步（10 号票：SyncChannel 抽象 + 轮询 + 版本乐观并发）+ 结束点单（11 号票：finish 接线 + FINISHED 只读态）+ 历史菜单复用（13 号票：列表卡「再来一单」→ 创建页复用模式带 fromOrderId）；对外经 `:feature:dining:api` 的 `DiningRoute` + `DiningApi` 暴露（di `@Binds`）。

## 包结构

```
src/main/java/com/smartwash/feature/dining/
├── di/
│   └── DiningModule.kt          # Hilt 模块（@Binds DiningApi + 提供 DiningServiceApi + 深链 host "dining"→OrderList）
├── logic/
│   ├── DiningOrderFormValidator.kt # 创建订单表单校验纯函数（JVM 单测，规则对齐后端 Bean Validation+service 二选一）
│   ├── DiningJoinUrlBuilder.kt  # 二维码内容构造纯函数（BASE_URL origin → /dining/join/{orderId}?t={token}，JVM 单测）
│   ├── DiningRecipeFormValidator.kt # 新增菜谱表单校验纯函数（JVM 单测，规则对齐后端 Bean Validation）
│   ├── DiningMappers.kt         # Vo → UI 模型映射（safe 兜底+状态保守降级+时间格式化集中层，JVM 单测）
│   ├── DiningCartReplay.kt      # 重放差分纯函数（服务端我的选菜 → 本地意图的最小操作集，JVM 单测）
│   ├── DiningCartSyncCoordinator.kt # 同步状态机（真相/意图/冲突重放/失败回滚，JVM 单测）
│   ├── DiningPollScheduler.kt   # 轮询调度（基准 3s + 失败指数退避封顶，JVM 单测）
│   ├── DiningSyncLifecycle.kt   # 订阅生命周期状态机（首载门禁 + 可见启停，JVM 单测）
│   ├── DiningJoinEntry.kt       # 站内信深链加入入口决策（成员直达/确认加入/已结束，JVM 单测）
│   └── DiningOrderBusinessCode.kt # 业务码 409 + DiningVersionConflictException
├── repository/
│   ├── DiningRepository.kt      # 域网络链路唯一出口（菜谱+订单；data 缺失上抛 NetworkException；写 409 转译冲突异常）
│   └── DiningApiImpl.kt         # DiningApi @Binds 实现（跨域上架菜谱查询）
├── sync/
│   ├── DiningOrderSyncChannel.kt # 订单变更订阅通道抽象（传输层可替换；Flow 语义 + 数据源窄契约）
│   └── PollingDiningOrderSyncChannel.kt # 轮询首实现（knownVersion 轻量 + 退避；@Binds 绑定）
└── ui/
    ├── DiningGraph.kt           # 路由聚合（diningGraph：OrderList/OrderCreate/Scan/OrderJoin/OrderDetail/OrderShare/ShoppingList + Library/Detail/Create 十页）
    ├── order/                   # 订单五页（列表[分区+FAB+菜谱库入口+卡内「再来一单」]/创建[分组勾选+固定 CTA+复用模式]/分享[二维码+复制链接]/点单[左右联动+共享购物车+结束点单]/购买清单结果[双视图]）
    ├── join/                    # 站内信深链加入入口页（12 号票：brief 判重→成员直达/非成员确认加入→详情）
    ├── library/                 # 菜谱库页（分类 chips + 封面卡片列表 + 加载更多）
    ├── detail/                  # 菜谱详情页（食材 chips + 步骤分段）
    ├── create/                  # 新增菜谱页（表单 + 封面上传 + 提交）
    └── scan/                    # 扫码加入页（CameraX + ML Kit → brief 确认 → join）
```

## 公开 API

| 符号 | 说明 |
|------|------|
| `NavGraphBuilder.diningGraph(navController)` | 路由聚合扩展，壳层 `MainActivity` 一行调用 |
| `DiningApiImpl` | `@Binds` 实现，供跨域消费方经 `DiningApi` 注入 |

## 依赖关系

- **此模块依赖**: `:feature:dining:api`、`:common:utils`、`:common:network`、`:common:ui`；禁止依赖 app 壳与其他 feature-impl。
- **谁依赖此模块**: `:app`（NavHost 聚合 + Hilt 图）；跨域消费方经 `DiningApi`（api 模块）。

## 内部约定

- **状态划分**: 订单列表页 `listState`（首载/刷新复用，分区派生放页面 remember）；创建页 `loadState`（分类+菜谱首载）/ `submitState`（提交动作态防重）独立；分享页 `detailState` 独立于二维码生成（produceState 异步，生成期占位）。
- **表单校验两层**: 端上 `DiningOrderFormValidator` / `DiningRecipeFormValidator` 先拦（错误枚举 → strings.xml 文案就近提示），后端 Bean Validation + service 兜底；分类存在性不做端上校验（后端裁决）。
- **订单卡/菜品行**: GroupCard 唯一画法 + `pressable`（规范 §7 禁 M3 交互组件/ripple）；勾选框自绘（border/primary 实底 + Check 图标），FAB 自绘（圆形 + 品牌渐变 + Canvas 十字，项目无 M3 FAB 先例）。
- **创建链路**: 提交成功 → `consumeCreatedResult()` 一次性取走创建响应（orderId + joinToken）→ navigate 分享视图并 `popUpTo(OrderCreate) { inclusive = true }`（分享返回直回列表）。
- **历史菜单复用（13 号票）**: 订单列表卡「再来一单」→ `DiningRoute.OrderCreate(fromOrderId, sourceTitle)`。创建页 VM 首载在常规分类/菜谱之后追加拉取源单详情（`getOrderDetail`，源单须为当前用户成员），把源单菜单预勾选并锁定（`toggleRecipe` 忽略），提示「将复用历史订单「x」的 N 道菜」；提交只校验标题（`validateReuse`），请求带 `fromOrderId`、`recipeIds=null`，由后端整份复制菜单（仍过上架把关）。
- **二维码内容**: `DiningJoinUrlBuilder`（logic 纯函数）——host 取 `@Named("baseUrl")` 的 origin（scheme://host[:port]），scheme 随环境（demo http / 生产 https），路径 `/dining/join/{orderId}?t={token}` 与深链 host "dining" 对齐；不用 `ServerConfig.shortLinkBaseUrl`（位于 app 壳层，feature 反向依赖禁止；语义为短链域名）。
- **封面上传**: PhotoPicker（`PickVisualMedia.ImageOnly`）→ contentResolver 读 bytes → 按真实 MIME 推扩展名 → Multipart `"file"`（头像上传先例模式）→ 返回对象键随新增提交回传。
- **防重**: 列表加载中忽略重复 load；提交/封面上传 Loading 期间忽略；菜品行切换勾选 `debounce = false`（连续快触）。
- **输入控件**: `OutlinedTextField` + `tokenTextFieldColors()` 令牌色（subscription/toolbox 先例），正文 16sp（规范 §3.7）。
- **共享购物车同步（10 号票）**: `DiningOrderSyncChannel` 为传输层可替换抽象（业务只依赖它），首实现 `PollingDiningOrderSyncChannel` 在页面可见时约 3s 带 knownVersion 轮询、unchanged 轻量；VM 经 `DiningSyncLifecycle`（纯状态机）启停收集（页面不可见即停，防泄露）。写操作携带已知 version，`DiningCartSyncCoordinator` 以「服务端真相 + 本地意图」差分光重放：409 → 拉取最新（含他人变更）→ 在新基准重放本地意图（他人变更永不覆盖）；失败经全量刷新按最新真相回滚本地意图并提示。`DiningRepository` 把写接口信封 409 转译为 `DiningVersionConflictException`。
- **未决视觉区分**: 本地未决操作期间，底部栏显示「同步中…」，菜品行尾加静态橙色小点（非动效）；取舍：同步状态机以「本地意图」为单一事实，未决是全局态，故用轻量全局提示 + 行级小点而非逐行骨架/动效，避免 3s 轮询窗口内闪烁。
- **结束点单（11 号票）**: 发起者点击「结束点单」→ 确认弹窗 → `finishOrder` → 成功后 `popUpTo(OrderDetail) { inclusive = true }` 进购买清单结果页（不可逆动作用二次确认）。FINISHED 详情（轮询/快照）到达后点单页进入只读态：`readOnly = !isOngoing` 隐藏整行勾选/备注/结束/分享入口，底部栏改为「查看购买清单」；购买清单快照经 `parseDiningShoppingList` + `toShoppingListResult` 纯函数映射双视图（结果页不重复聚合）。
- **站内信深链（12 号票）**: 后端 DINING_JOIN/DINING_FINISH 的 `deep_link` 统一为 `smartwash://dining/order/{orderId}?t={joinToken}`（携带加入凭证，与 `DiningJoinUrlBuilder` 的 token 同源）。端上 `DiningDeepLinkModule` 在 host "dining" 内解析：`order/{orderId}` + query `t` → `DiningRoute.OrderJoin`（加入入口页）；无路径/非法 orderId → 域首页订单列表。加入入口页经 brief 判重（`DiningJoinEntry` 纯函数）：成员直达详情，非成员展示标题预览 + 「加入」→ join 后进详情；未登录由壳层深链守卫落登录页。改深链串必须同步后端 `buildOrderDeepLink`、`DiningDeepLinkModuleTest` 与本节。

## 已知坑

- **深链 host**: `dining` 未进壳层 `PUBLIC_DEEP_LINK_HOSTS`，未登录由壳层拦截落登录页（对齐受保护 host 语义）；站内信深链落 `OrderJoin`（见内部约定「站内信深链」）。
- **joinToken 可见性**: 创建响应必得；发起者的详情全量响应也回传（09 号票修补），参与者响应与轻量响应不回传——参与者从列表进分享视图二维码区降级提示；重置凭证入口为后续票。
- **写失败回滚必须全量刷新**: 失败写不前进 version，带 knownVersion 拉取会返回 unchanged 而拿不到最新选菜记录；故写循环的 `refreshFromServer` 固定 knownVersion=null（写频率低，全量可接受）。
- **分享视图二维码 host 的环境语义**: demo BASE_URL 为明文 http，二维码内容同为 http（usesCleartextTraffic 现状一致）；生产注入 https BASE_URL 后同源变 https。
- **创建页全量拉取**: 菜谱分页循环取满（page 递增至 total，每页 100，防御上限 100 页）；分类目录失败随主流程进错误态（分类名缺失会让分组残缺）。
- **列表刷新时机**: 订单列表 `LaunchedEffect(Unit)` 只兜首载；返回本页（离栈再回）/回前台由 `LocalLifecycleOwner` 的 `ON_RESUME` 观察者触发 `load()`（13 号票修「列表返回不刷新」遗留）——首载时 nav entry 已 RESUMED，观察者收不到事件，故两条路径并存。
- **包名与模块路径解耦**: 实际包名 `com.smartwash.feature.dining`（非 `com.smartwash.feature.dining.impl`）。
- **local.properties**: worktree 首次构建需自建 `sdk.dir`（.gitignore 已忽略，不入库）。

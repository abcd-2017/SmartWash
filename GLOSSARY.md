# SmartWash 术语表（GLOSSARY）

> 本文件是 SmartWash 四端（Spring Boot 后端 / Android / 鸿蒙 NEXT / Web 管理后台）共享的领域词汇表。
> 事实来源：**当前代码**（2026-11-13 同步）+ 根目录 `smart_wash.sql` + `docs/adr/`（ADR 尚未创建，正文中的 ADR 编号为引用占位）。
> 与 ADR 冲突时，以 ADR 的裁定为准；本表只描述「代码现在是什么」，不替代 ADR 的「为什么这样定」。

## 维护约定

1. **何时加新术语**：当同一个概念在四端（或后端 + 任一端）有两个以上不同命名、或一个词在不同模块含义不同、或新人/agent 容易望文生义时，必须收录。仅在单端单文件出现、无跨端歧义的实现细节不收。
2. **何时改术语**：代码事实变化（字段改名、枚举取值变更、机制替换）时同步更新本表，并在 PR 描述里点名受影响条目。**不允许**出现「代码已改、术语表还是旧名」的漂移。
3. **与 ADR 的关系**：ADR 记录**决策与理由**（为什么），本表记录**词汇与事实**（是什么）。一个术语若已有 ADR 覆盖，本表条目末尾以「→ ADR-XXXX」标注，不复制 ADR 的论证过程。ADR 与本表冲突时改 ADR 并同步本表，不得让两者各说各话。
4. **四端对应物的写法**：每条给出「后端字段/枚举 · Android 类 · 鸿蒙 VO/常量 · Web 字典/页面」。某一端没有对应实现时明确写「无对应实现」，不要留空让人误以为漏查。
5. **易混淆点**：只写真实发生过的误解或代码里已注释的坑，不写「理论上可能」的臆测。
6. **命名口径**：术语名以代码标识符为准（如 `gameKey`、`entryPath`），中文名只是检索别名；同一概念在不同端的拼写差异（`bindCampus`，历史拼写 `bingCampus` 已四端统一）在条目内点明。

---

## 1. 通用契约与身份

### 1.1 统一响应信封（Result / ResultCodeEnum）

- **代码标识**：`Result<T>`、`ResultCodeEnum`、`code / message / data`
- **定义**：所有 HTTP 响应一律包装为 `{ code, message, data }`；业务成功 `code = 200`；HTTP 状态码只反映传输层，业务语义只看 `code`。
- **四端对应物**：
  - 后端：`SmartWash/.../common/Result.java`（唯一出口）、`ResultCodeEnum`（`SUCCESS=200 / FAIL=201 / UNAUTHORIZED=401 / SERVICE_ERROR=2012`）。
  - Android：`:common:network` 的 `ResponseInterceptor.kt` 双通道识别（HTTP 401 与业务码 401），保留信封由各域解包。
  - 鸿蒙：`network/BaseResponse.ets` + `network/HttpUtil.ets` / `Axios.ets`。
  - Web：`src/utils/http.js` 成功时解包信封（调用方直接拿 `data`）。
- **易混淆点**：`FAIL=201` 与 HTTP 201 毫无关系；`SERVICE_ERROR=2012` 是历史取值，已入库三端映射，**不可顺手重编**。三端解包层次不同（Web 在 http 层解包，Android/鸿蒙保留信封由各域解包）是既有现实。
- → ADR-0001

### 1.2 401 语义与登录失效

- **代码标识**：`UNAUTHORIZED=401`、`handleSessionExpired` / `handleUnauthorized`
- **定义**：HTTP 401 与业务码 401 同义，统一行为为「清本地 token → 清内存登录态 → 跳登录页（replace 语义，不压栈）」，并发多请求同时 401 去重，只跳一次。
- **四端对应物**：
  - 后端：`JwtAuthenticationFilter`（过滤器层 HTTP 401）+ 业务层信封 401；`JwtAuthenticationEntryPoint`。
  - Android：`ResponseInterceptor.kt` → `handleSessionExpired`（clearToken + `SessionEventNotifier`）→ `MainActivity` `popBackStack + navigate(launchSingleTop)`。
  - 鸿蒙：`Axios.ets` 1.5s 防抖窗 → `handleUnauthorized`（清 token/role/loginFlag）→ `replacePathByName`。
  - Web：`http.js` `redirectingToLogin` 1.5s 去重 + 登录页自身 401 不再跳转；**当前是 `router.push('/login?redirect=…')`，非字面 replace**（有去重窗与登录页守卫兜底，行为等价）。
- **易混淆点**：只清 token 会残留角色/登录标志（鸿蒙 `clearLoginState` 注释）；replace 而非 push 是防返回键退回已失效页面。
- → ADR-0001

### 1.3 URL 前缀与权限边界

- **代码标识**：`/auth/**`、`/admin/**`、`/web/auth/**`、`/web/**`
- **定义**：由后端 `SecurityConfig` 单点裁决，三端不得绕过。`/auth/**` 公开（登录/注册/验证码/刷新令牌）；`/admin/**` 需 `ROLE_ADMIN`（Web 消费）；`/web/auth/**` 需 `ROLE_USER`（Android/鸿蒙消费）；`/web/**` 公开只读（如 `/web/config`、`/web/deeplink/routes`、`/web/t/**`）。
- **四端对应物**：
  - 后端：`config/SecurityConfig.java:37-58`。
  - Android / 鸿蒙：所有用户端接口走 `/web/auth/**`，公开接口走 `/web/**`。
  - Web：所有管理接口走 `/admin/**`。
- **易混淆点**：`/web/**` 是公开**只读**，不代表「用户端接口都不需要登录」——用户端写操作在 `/web/auth/**`。改权限只动 `SecurityConfig` 一处。
- → ADR-0001、ADR-0004

### 1.4 JWT 访问令牌

- **代码标识**：`JWT`、`typ`（`access` / `refresh`）、`jti`、`sub`（`admin-{用户名}` / `user-{手机号}`）、`JwtUtil`、`LoginVo.token / refreshToken`
- **定义**：双 token 制——access（typ=access，TTL 2 小时，访问受保护端点）+ refresh（typ=refresh，TTL 7 天，仅用于 `/auth/refresh` 换发）。登录/注册自动登录在 `LoginVo` 一并下发双 token 与角色；`sub` 带端别前缀；`jti` 是令牌唯一 ID（黑名单与轮换的键）。**旧格式（无 `typ` claim）一律视为 access**，兼容存量会话。
- **四端对应物**：
  - 后端：`utils/JwtUtil.java`（`JWT_SECRET` 环境变量，Base64 解码后 ≥32 字节，缺失/过弱拒绝启动）。
  - Android：token 存本地，经 `ResponseInterceptor` 注入 `Authorization: Bearer`。
  - 鸿蒙：`CommonConstants.USER_TOKEN`（键名 `"token"`）+ `StorageUtil`。
  - Web：`stores/auth.js` + `http.js` 注入。
- **易混淆点**：`sub` 前缀是**端别**（admin/user），不是角色；角色变更通过下次登录生效，刷新端点不重查角色。`typ=refresh` 的令牌**不能**当 access 用——访问受保护端点必 401，只能进刷新端点。
- → ADR-0007

### 1.5 刷新端点（/auth/refresh）

- **代码标识**：`POST /auth/refresh`、`from/auth/RefreshTokenFrom`、`vo/TokenPairVo`
- **定义**：公开端点，一次性轮换。校验链「格式/签名 → typ=refresh（否则 401 信封）→ jti 黑名单（命中即 401，视作重放）」→ 以 `sub` 原样重签**新的一对** token → **旧 refreshToken 的 jti 立即拉黑**（TTL=剩余有效期）。请求体 `{"refreshToken": "..."}`；响应 `data` 为 `{token, refreshToken}`（不含 role）。
- **定义**：公开端点，一次性轮换。校验链「格式/签名 → typ=refresh（否则 401 信封）→ jti 黑名单（命中即 401，视作重放）」→ 以 `sub` 原样重签**新的一对** token → **旧 refreshToken 的 jti 立即拉黑**（TTL=剩余有效期）。请求体 `{"refreshToken": "..."}`；响应 `data` 为 `{token, refreshToken}`（不含 role）。**三端均已接入端上续签**：响应拦截器捕获 401 → 单飞（in-flight 去重）调本端点换新双 token → 落盘后重试原请求一次，仍 401 则清本地登录态跳登录页。
- **四端对应物**：
  - 后端：`controller/LoginController.java`（`/auth/refresh`）、`JwtUtil.generatorAccessToken / generatorRefreshToken`。
  - Android：`:common:network` 的 `ResponseInterceptor.kt`（401 时经 `TokenRefresher` 接口单飞续签，实现为 `feature/user/impl` 的 `TokenRefresherImpl.kt`）。
  - 鸿蒙：`network/Axios.ets` 响应拦截器 401 单飞续签（模块级 in-flight Promise 去重，落盘先于兑现）。
  - Web：`src/utils/http.js` 的 `refreshTokens()`（模块级 in-flight Promise 单飞续签）。
- **易混淆点**：refreshToken 是**一次性**凭证——同一 refreshToken 第二次使用必 401（重放拦截），这不是 bug；登出请求体可携带 refreshToken 一并拉黑，不携带仅拉黑 access（向后兼容）；不做 60s 频控与家族撤销（取舍见 ADR-0007 修订节）。
- → ADR-0007

### 1.6 登出黑名单

- **代码标识**：`jwt:blacklist:{jti}`、`JwtBlacklistService`、`JwtLogoutHandler`
- **定义**：登出时提取 token 的 `jti` 写入 Redis，TTL = token 剩余有效期（到期自动清除）；认证过滤器对每个受保护请求查黑名单，命中即 401。双 token 制下登出**双拉黑**：请求体可选携带 `refreshToken` 一并拉黑，不携带仅拉黑 access（向后兼容）。
- **四端对应物**：
  - 后端：`service/JwtBlacklistService.java`、`filter/JwtAuthenticationFilter.java`、`JwtLogoutHandler`（`/auth/logout`）。
  - 三端：只负责调登出接口 + 清本地态，不感知黑名单。
- **易混淆点**：黑名单只对「登出过/已轮换的 token」生效；Redis 故障期间登出失效（fail-open，可接受降级）。7 天 refresh 窗口内被窃取且未登出的 token 无法远程吊销。
- → ADR-0007

### 1.7 用户角色（admin / user）

- **代码标识**：`DefaultConstant.ADMIN_USER_LOGIN_TYPE = "admin"`、`USER_LOGIN_TYPE = "user"`、`ROLE_admin` / `ROLE_user`
- **定义**：`admin` 是管理端账号类型（`admin_users` 表），`user` 是 C 端用户类型（`users` 表）；两者是**不同的用户体系**，由 `SecurityConfig` 分别要求 `ROLE_ADMIN` / `ROLE_USER`。
- **四端对应物**：
  - 后端：`common/DefaultConstant.java`、`config/SecurityConfig.java`。
  - Android / 鸿蒙：登录响应携带角色/端别，用于路由守卫。
  - Web：`stores/auth.js` 保存角色，路由守卫据此判管理端权限。
- **易混淆点**：不要把它与 §1.8 管理端角色（root/schools_admin/plant）混为一谈——前者是端别，后者是管理端内部的权限等级。

### 1.8 管理端角色（root / schools_admin / plant）

- **代码标识**：`roles.role_name`、`roleId`
- **定义**：管理端账号的角色等级。种子数据：`root`（1，超级管理员）、`schools_admin`（2，学校管理员）、`plant`（3，厂房管理员，**已预留未启用**，权限未细化）。
- **四端对应物**：
  - 后端：`entity/Roles.java`、`admin_users.role_id`。
  - Web：`views/system/RoleList.vue`、`views/system/AdminUserList.vue`、`api/role.js`。
  - Android / 鸿蒙：无对应实现（管理端专属）。
- **易混淆点**：`plant` 角色在数据库里有种子，但业务代码未启用，不要按「已支持」处理；角色变更通过下次登录生效。

---

## 2. 用户与组织

### 2.1 用户（users）

- **代码标识**：`users`、`userId`、`phoneNumber`、`studentId`
- **定义**：C 端学生用户，以手机号唯一标识；可归属学校、填学号、绑校园卡、有余额与头像。
- **四端对应物**：
  - 后端：`entity/Users.java`（`user_id` / `school_id` / `phone_number` / `student_id` / `campus_card` / `password` / `balance` / `avatar`）。
  - Android：`:feature:user:api` `model/UserInfo.kt`、`:feature:user:impl` `network/vo/user/UserInfoVo.kt`。
  - 鸿蒙：`network/vo/UserInfoVo.ets`。
  - Web：`views/system/UserList.vue`、`api/user.js`。
- **易混淆点**：`users`（C 端）与 `admin_users`（管理端）是两张表、两套登录；`student_id`（学号）与 `campus_card`（校园卡号）是两个独立字段，都有唯一索引。

### 2.2 校园卡绑定（campus_card / bindCampus）

- **代码标识**：`campus_card`、`bindCampus` / `unBindCampus`、`uk_users_campus_card`
- **定义**：为当前 C 端用户绑定/解绑一个校园卡号；卡号全局唯一（唯一索引），绑定后用于身份识别。
- **四端对应物**：
  - 后端：`WebUsersController` `POST /web/auth/user/bindCampus/{campusCard}`、`POST /web/auth/user/unBindCampus`；`users.campus_card`。
  - Android：`:feature:user:impl` `UserAccountApi`、`ui/userinfo/UserInfoPage.kt`。
  - 鸿蒙：`pages/BingCampusCardAlert.ets`、`network/api/UserApi.ets`。
  - Web：管理端不直接代管校园卡（用户自绑），仅在用户列表展示。
- **易混淆点**：绑定的是**卡号字符串**，不是实体卡或支付工具；历史 typo `bingCampus` 已统一为 `bindCampus`，检索旧代码时注意。

### 2.3 学校与组织层级（schools）

- **代码标识**：`schools`、`schoolId`、`province / city / district`、`lockerCount`
- **定义**：运营的最小组织单元。行政区划分省/市/区三级 + 学校，**没有**集团/校区等更高层级；用户、柜子、订单均按 `school_id` 归属。
- **四端对应物**：
  - 后端：`entity/Schools.java`（`school_name` / `school_code` / `location` / `province` / `city` / `district` / `longitude` / `latitude` / `logo_url` / `contact_name` / `contact_phone` / `locker_count`）。
  - Android：`:feature:laundry:api` `model/SchoolOption.kt`、`SchoolSearchSource.kt`。
  - 鸿蒙：`network/vo/SchoolVo.ets`（`schoolId / schoolName / location / lockerCount`）。
  - Web：`views/system/SchoolList.vue`、`api/school.js`、`utils/optionCache.js` 的 `schoolOptionsCache`。
- **易混淆点**：`location` 是详细地址文本，`province/city/district` 是行政区划；`locker_count` 是学校的柜子容量声明，与 `lockers` 表实际记录数不一定相等。

### 2.4 余额（balance）

- **代码标识**：`users.balance`、`decrUserBalance` / `incrUserBalance`
- **定义**：C 端用户的钱包余额（`decimal(10,2)`），用于钱包支付与充值入账。
- **四端对应物**：
  - 后端：`entity/Users.java`、`UsersMapper`（条件 UPDATE 谓词 `balance >= amount` 防超扣）。
  - Android：`:feature:user:api` `UserInfo.balance`。
  - 鸿蒙：`network/vo/UserInfoVo.ets`。
  - Web：用户列表/详情展示。
- **易混淆点**：加/减采用方向不同的防错——**加**靠上游幂等闸门保证只入一次，**减**靠 SQL 谓词 `balance >= amount` 防超扣；两者不可互换。

---

## 3. 洗衣主航道

### 3.1 洗衣项目 / 服务项（laundry_items）

- **代码标识**：`laundry_items`、`itemId`、`basePrice`
- **定义**：可购买的洗衣套餐（服务项），种子 5 档：基础洗护 29.90 / 快速洗护 39.90 / 精致洗护 49.90 / 深度去渍 69.90 / 奢侈品护理 199.90。订单通过 `laundry_items_id` 引用。
- **四端对应物**：
  - 后端：`entity/LaundryItems.java`、`WebLaundryItemsController` `GET /web/laundryItems/all`。
  - Android：`:feature:laundry:impl` `network/api/LaundryItemsApi.kt`。
  - 鸿蒙：`network/vo/LaundryItem.ets`。
  - Web：`views/system/LaundryList.vue`、`api/laundry.js`。
- **易混淆点**：代码里没有独立的「洗衣模式」枚举——「模式/服务项/套餐」都指 `laundry_items`；价格一律以后端 `basePrice` 为准，不信任客户端传价。

### 3.2 订单（orders）

- **代码标识**：`orders`、`orderId`、`orderNo`、`totalPrice` / `payPrice`
- **定义**：一次洗衣预约单据，归属用户与学校，关联洗衣项目、（寄件）柜子、（可选）优惠券，有状态机与取件码。
- **四端对应物**：
  - 后端：`entity/Orders.java`、`OrdersServiceImpl`。
  - Android：`:feature:order:api` `model/OrderInfo.kt` / `OrderBrief.kt` / `OrderItemCount.kt`、`:feature:order:impl` `network/vo/order/OrderInfoVo.kt`。
  - 鸿蒙：`network/vo/OrderInfo.ets` / `OrderVo.ets` / `OrderItemCountVo.ets`。
  - Web：`views/system/OrderList.vue`、`api/order.js`。
- **易混淆点**：`total_price` 是套餐原价，`pay_price` 是扣券后实付；`order_no` 是雪花 ID 订单号，与幂等键 `out_trade_no` 不是一回事。

### 3.3 订单状态机（OrderStatus）

- **代码标识**：`OrderStatus`、`VALID_TRANSITIONS`、`ORDER_GROUP_ALL = "001"`
- **定义**：订单状态全集为字符串码 `-2 ~ 7`：

  | 码 | 含义 | 码 | 含义 |
  |---|---|---|---|
  | `-2` | 已取消 | `3` | 清洗中 |
  | `-1` | 已退款 | `4` | 已烘干 |
  | `0` | 待支付 | `5` | 配送中 |
  | `1` | 待寄件 | `6` | 待取件 |
  | `2` | 已收取 | `7` | 已完成 |

  合法流转（`VALID_TRANSITIONS`）：`0→{1,-2}`、`1→{2,-2}`、`2→{3}`、`3→{4,6}`、`4→{5}`、`5→{6}`、`6→{7}`。管理员取消区分「待支付直接取消」与「已支付走退款」两条链路；流转到待取件时一次条件写入状态 + 新柜子 + 取件码。
- **四端对应物**：
  - 后端：`common/OrderStatus.java`、`OrdersServiceImpl.VALID_TRANSITIONS`、`OrdersMapper.xml`（`casStatus` / `casStatusKeepLocker` / `casStatusAssignPickup`）。
  - Android：`:feature:order:api` `model/OrderStatus.kt`（10 态）、`ShowOrderStatus.kt`（页签子集，含 `ALL_ORDER = "001"`）。
  - 鸿蒙：`constant/OrderStatus.ets`（10 态）、`constant/ShowOrderStatus.ets`（页签 5 态，`OrderStatusList() = ['001','0','1','3','6']`）。
  - Web：`src/constants/dict.js` `ORDER_STATUS_TAG_TYPES`（文案由后端 `/admin/orders/status` 下发）。
- **易混淆点**：
  1. **`"001"` 不是订单状态**，是「全部订单」分组的魔法值（`ORDER_GROUP_ALL`），前端按该 key 解析摘要/列表，取值不可变更。
  2. 状态码是**字符串**（`"-2"`），不是整数；`-1/-2` 是终态。
  3. 页签子集只有 5 项（全部/待支付/待寄件/清洗中/待取件），**不含**已收取/已烘干/配送中/已完成/已取消/已退款。
- → ADR-0002

### 3.4 柜子 / 格口（lockers）

- **代码标识**：`lockers`、`lockerId`、`lockerNumber`、`LockerStatusEnum`
- **定义**：学校下的寄存柜位。状态：`0` 空闲 / `1` 使用中 / `2` 故障。订单创建时锁定一个空闲柜（`SELECT FOR UPDATE`），转待取件时切换到新柜并释放旧柜。
- **四端对应物**：
  - 后端：`entity/Lockers.java`、`common/LockerStatusEnum.java`、`WebLockersController` `GET /web/lockers/status`（枚举）、`GET /web/lockers/all`（分页）。
  - Android：`:feature:order:impl` `network/vo/order/`（柜子信息随订单下发）。
  - 鸿蒙：`network/vo/LockerVo.ets`。
  - Web：`views/system/LockerList.vue`、`dict.js` `LOCKER_STATUS_TAG_TYPES`。
- **易混淆点**：代码里**没有独立「格口」实体**——「柜子」与「格口」都指 `lockers` 记录，`locker_number` 是柜位编号；唯一索引 `uk_school_locker(school_id, locker_number)` 保证同校编号唯一。

### 3.5 寄存 / 取件与取件码（pickup_code）

- **代码标识**：`pickup_code`、`PickupCodeUtils`、`ownsPickupCode`
- **定义**：订单流转到「待取件」时生成取件码，格式为 `userId:orderId:6位随机数字`（三段冒号结构）。学生凭码/二维码取件。
- **四端对应物**：
  - 后端：`utils/PickupCodeUtils.java`（SecureRandom 6 位）、`OrdersServiceImpl.ownsPickupCode`（按 `:随机段` 后缀匹配并限定 `user_id` 归属）、`orders.pickup_code` 可空唯一索引。
  - Android：`:feature:order:api` `PickupDeliveryType`（PICKUP/DELIVERY）、`ui/page/pickup/PickupPage.kt`。
  - 鸿蒙：`pages/PickUp.ets`（取件）、`RouteNames.PICKUP_DELIVERY`。
  - Web：管理端不直接处理取件码（仅订单详情展示）。
- **易混淆点**：三段冒号结构是三端解析契约，**不可变更**；客户端只传随机段（4~6 位数字），后端按后缀匹配 + 归属校验；取件码在订单转待取件时才生成，不是下单时。

### 3.6 订单评价（order_reviews）

- **代码标识**：`order_reviews`、`rating`、`uk_order_reviews_order_id`
- **定义**：用户对订单的评价，评分 1-5，内容 ≤500 字；一单一评（唯一索引）。
- **四端对应物**：
  - 后端：`entity/OrderReviews.java`、`WebReviewController`（接口存在）。
  - Android / 鸿蒙 / Web：**均无对应实现**（无评价页面与 API 封装，评价能力尚未落到端上）。
- **易混淆点**：`uk_order_reviews_order_id` 保证一单一评，重复提交会被唯一索引拦住；不要因为后端有接口就以为端上已有评价入口。

---

## 4. 资金链路

### 4.1 支付流水（payments）

- **代码标识**：`payments`、`PaymentStatus`、`PayType`、`outTradeNo`
- **定义**：订单支付记录。`PaymentStatus`：`0` 待支付 / `1` 已支付 / `2` 失败 / `3` 处理中。`PayType`：`1` 钱包余额 / `2` 支付宝 / `3` 微信。
- **四端对应物**：
  - 后端：`entity/Payments.java`、`common/PaymentStatus.java`、`common/PayType.java`、`WebPaymentsController` `GET /web/payments/payType` / `payStatus`。
  - Android：`:feature:payment:impl` `ui/payment/PaymentType.kt`。
  - 鸿蒙：`constant/PaymentType.ets`（1/2/3）、`constant/PaymentMethod.ets`（微信=1/支付宝=2）。
  - Web：`dict.js` `PAY_STATUS_TAG_TYPES`、`RECHARGE_TYPE_OPTIONS`；`views/system/PaymentList.vue`。
- **易混淆点**：`PaymentStatus.SUCCESS = "1"` 被 Dashboard 收入统计依赖，**不可变更**；`payment_method`（payments 表）与 `pay_type` 取值一致但分属两个枚举；当前支付网关是桩实现 `StubPaymentGatewayServiceImpl`。
- → ADR-0002

### 4.2 充值流水（recharge_records）

- **代码标识**：`recharge_records`、`RechargeType`、`outTradeNo`
- **定义**：余额充值记录。`recharge_type`：`1` 微信支付 / `2` 支付宝支付；`status` 复用 `PaymentStatus` 语义（`0` 待支付 / `1` 成功 / `2` 失败 / `3` 处理中，**默认 3**）。
- **四端对应物**：
  - 后端：`entity/RechargeRecords.java`、`common/RechargeType.java`、`WebRechargeRecordsController`。
  - Android：`:feature:payment:impl` `network/api/RechargeApi.kt`、`ui/recharge/RechargeRecordPage.kt`。
  - 鸿蒙：`constant/RechargeType.ets`（WECHAT="1"/ALIPAY="2"）、`network/vo/RechargeRecordVo.ets`。
  - Web：`dict.js` `RECHARGE_TYPE_TEXT` / `RECHARGE_STATUS_TEXT`；`views/system/RechargeList.vue`。
- **易混淆点**：`recharge_type` 与 `PayType` 取值相同（1/2）但语义不同（充值渠道 vs 订单支付方式）；`status` 默认 3（处理中）而非 0。
- → ADR-0002

### 4.3 幂等键（out_trade_no）

- **代码标识**：`out_trade_no`、`uk_payments_out_trade_no`、`uk_recharge_records_out_trade_no`
- **定义**：网关统一订单号，格式 `PAY|RCH + yyyyMMdd + 雪花ID`；支付/充值记录的唯一索引，是资金幂等闸门的键。
- **四端对应物**：
  - 后端：`Payments.outTradeNo`、`RechargeRecords.outTradeNo`、`PaymentsServiceImpl` / `RechargeRecordsServiceImpl`。
  - Android / 鸿蒙 / Web：不直接感知（由后端生成与判重）。
- **易混淆点**：`out_trade_no` 是**资金流水**的幂等键，不是订单号 `order_no`；两表唯一索引是幂等模式的最后一道防线，禁止物理删除流水。
- → ADR-0002

### 4.4 优惠券模板（coupon）

- **代码标识**：`coupon`、`CouponStatus`、`discount` / `threshold` / `validDays` / `issuedCount`
- **定义**：运营配置的优惠券模板。`CouponStatus`（整数枚举）：`EXPIRED=1` / `ACTIVE=0` / `RECEIVE=2`；模板自身 `status` 列是 varchar（`0` 正常 / `1` 失效）。含面额 `discount`、门槛 `threshold`、领取后有效天数 `valid_days`、发放上限 `total_limit` 与已发放 `issued_count`。
- **四端对应物**：
  - 后端：`entity/Coupon.java`、`common/CouponStatus.java`、`background/CouponController.java`。
  - Android：`:feature:coupon:impl` `CouponStatus.kt`（String "0"/"1"/"2"）。
  - 鸿蒙：`constant/CouponStatus.ets`（String "0"/"1"/"2"）。
  - Web：`dict.js` `COUPON_STATUS_TEXT`；`views/system/CouponList.vue`。
- **易混淆点**：`CouponStatus` 枚举是 Integer，但实体 `coupon.status` 是 varchar，且 Android/鸿蒙已收敛为 String 契约；枚举里的 `RECEIVE=2`（已领取）与模板状态无关，是用户券侧的状态。
- → ADR-0002

### 4.5 用户优惠券（user_coupon）

- **代码标识**：`user_coupon`、`isUsed`、`expiredAt` / `usedAt` / `receivedAt`、`uk_user_coupon`
- **定义**：用户领取的券实例。生命周期：领取（`received_at` + 按 `valid_days` 计算 `expired_at`）→ 核销（`is_used=1` + `used_at` + `order_id`）或过期（`expired_at`）。
- **四端对应物**：
  - 后端：`entity/UserCoupon.java`、`UserCouponServiceImpl`（`increaseIssuedCount` 原子递增防超发）、`WebUserCouponController`。
  - Android：`:feature:coupon:api` `model/UsableCoupon.kt`、`:feature:coupon:impl` `CouponServiceApi.kt`。
  - 鸿蒙：`network/vo/UserCouponVo.ets`、`network/vo/AllCouponsVo.ets`。
  - Web：`views/system/UserCouponList.vue`、`api/userCoupon.js`。
- **易混淆点**：用户券的过期时间是**实例上的 `expired_at`**（领取时算好），不是模板的 `end_time`；`uk_user_coupon(user_id, coupon_id)` 防重复领取，并发超发由 `increaseIssuedCount` + 唯一索引双重兜底。
- → ADR-0002

---

## 5. 游戏库与游戏数据平台

### 5.1 游戏配置（game_config / gameKey）

- **代码标识**：`game_config`、`gameKey`、`uk_game_key`
- **定义**：H5 小游戏的下发配置。`game_key` 是管理端、端上与转发 URL 共同引用的业务唯一标识，**创建后不可修改**（改 key 等于换游戏）。
- **四端对应物**：
  - 后端：`game/entity/GameConfig.java`、`game/service/impl/GameConfigServiceImpl.java`、`WebGameListController`（`/web/auth/game/list` 下发）。
  - Android：`:feature:game` `network/GameApi.kt`、`model/GameItem.kt`、`ui/center/GameCenterPage.kt`。
  - 鸿蒙：`feature/game/network/GameApi.ets` + `ui/GameCenterPage.ets`（路由 `GameCenter`），深链 host `game-center`。
  - Web：`views/game/GameList.vue`、`api/game.js`。
- **易混淆点**：`id` 是自增主键，`game_key` 才是业务标识；种子字典 `app_deeplink_route` 的 `game-center` 标 `platform=3` 双端，Android / 鸿蒙均已接入。
- → ADR-0005

### 5.2 来源类型（source_type）

- **代码标识**：`source_type`、`SOURCE_TYPE_PACKAGE = 1` / `SOURCE_TYPE_EXTERNAL = 2`
- **定义**：`1` 上传包（存 MinIO `game/{gameKey}/` 前缀，可玩地址读时拼装）；`2` 外链（外部 https 地址，原样下发）。
- **四端对应物**：
  - 后端：`GameConfigServiceImpl.playUrlOf`、`GameConfig.sourceType`。
  - Android：游戏详情消费下发的 `url`，不感知 source_type。
  - 鸿蒙：游戏中心消费下发 `url`，不感知 source_type。
  - Web：`dict.js` `GAME_SOURCE_TYPE_TEXT`（1 上传 / 2 外链）。
- **易混淆点**：`source_type=1` 时 `url` 列**不落库**，读时由 `object_key + entry_path` 拼 MinIO 直链；`source_type=2` 的 `url` 不可当对象键重写。

### 5.3 入口路径（entry_path）

- **代码标识**：`entry_path`、`entryPath`
- **定义**：上传包内入口相对路径，默认 `index.html`；单 html 上传固定落为 `{gameKey}/index.html`，zip 上传时探测入口。
- **四端对应物**：
  - 后端：`GameConfig.entryPath`、`GamePackageValidator.resolveEntryPath`。
  - Android / 鸿蒙 / Web：不直接感知（消费拼装后的 `url`）。
- **易混淆点**：`entry_path` 是包内相对路径，不含 `game/{gameKey}/` 前缀（前缀在 `object_key`）；`entry` 一词单指入口，不含游戏本体。

### 5.4 游戏分类 / 图标 / 色组（category / icon / color）

- **代码标识**：`category`（puzzle/arcade/word/board/other）、`icon`（gamepad/arcade/puzzle/grid/board/word/default）、`color`（green/blue/orange/pink/teal/purple）
- **定义**：游戏卡的展示元数据。分类 5 类（OTHER 无独立筛选 chip，仅在「全部」下展示）；图标与色组都是**端上白名单 key**，未知值兜底默认。
- **四端对应物**：
  - 后端：`GameConfig.category/icon/color`（管理端二次校验白名单）。
  - Android：`:feature:game` `GameConstant.kt`（`GameCategory`、`gameIconFromKey`、`GameTint`）。
  - 鸿蒙：`GameCenterPage` 消费 `category/icon/color` 做分类筛选与封面渲染。
  - Web：`dict.js` `GAME_CATEGORY_OPTIONS` / `GAME_ICON_OPTIONS` / `GAME_COLOR_OPTIONS`。
- **易混淆点**：游戏色组与工作台入口色组**共用同一份六色白名单**（`GAME_COLOR_OPTIONS = WORKBENCH_ENTRY_COLOR_OPTIONS`）；未知 color key 兜底 GREEN，未知 icon 兜底「应用格」图标。

### 5.5 游戏主题桥（SmartWashGameBridge / getTheme / setChrome）

- **代码标识**：`window.SmartWashGameBridge`、`getTheme()`、`setChrome(mode)`、`__applySmartWashTheme(mode)`、`GameChrome`
- **定义**：Android 通用 WebView 容器（`WebViewPage`）向 H5 游戏暴露的单一 JS 桥。`getTheme()` 返回 `'light' | 'dark'`（**永不返回 `'system'`**，空串 = 非白名单来源）；`setChrome(mode)` 取 `'full' | 'immersive' | 'none'`（非法值回落 `full`）；主题变化经 `__applySmartWashTheme` 主动推送。
- **四端对应物**：
  - Android：`ui/page/webview/WebViewPage.kt`（挂载）、`GameThemeBridge.kt`、`GameChrome.kt`。
  - 后端：无（纯端侧协议）。
  - 鸿蒙：`pages/WebPage.ets` 内置浏览器承接 H5 游戏；主题桥能力待与 Android 对齐。
  - H5：`games-library/GAME-THEME.md`（H5 侧权威文档，20 款游戏内联同一运行时）。
- **易混淆点**：桥生效的**host 白名单**只含「后端 API host + 运行期下发的 MinIO endpoint」；白名单外 `getTheme` 返回空串、`setChrome` 忽略、推送跳过。`system` 是协议保留值，H5 已裁撤 matchMedia 监听，无桥恒浅色。
- → ADR-0005

### 5.6 游戏数据平台（game_user_save / game_score_entry / game_level）

- **代码标识**：`game_user_save`、`game_score_entry`、`game_level`、`GameStateVo`、`GameLeaderboardVo`、`GameLevelsVo`、`WebGameDataController`
- **定义**：游戏账号存档（按 `game_key` + 用户）、成绩上报与排行榜、关卡热更新下发。Android 经 app 壳的 `GameBridgeApi` 访问 `/web/auth/game/state|score|leaderboard|levels`；后端 `WebGameDataController`（用户端）+ `GameDataAdminController`（管理端查询/关卡维护）。
- **四端对应物**：
  - 后端：`game/entity/{GameUserSave,GameScoreEntry,GameLevel}.java`、`game/service/impl/*`。
  - Android：`app/network/api/GameBridgeApi.kt`（壳层桥链，非 `:feature:game`）。
  - 鸿蒙：无对应实现（游戏中心仅列表 + WebView）。
  - Web：`views/game/GameData.vue` + `api/gameData.js`（关卡/存档/成绩管理）。
- **易混淆点**：`state` 是客户端自定义结构的整块 JSON 存档；`score` 是单条成绩，排行榜按其聚合；`levels` 由管理端热更新，端上拉取即可玩新关卡，无需发版。

---

## 6. 观象台（占卜）

### 6.1 四术（liuyao / meihua / qimen / liuren）

- **代码标识**：`method`、`liuyao`（六爻纳甲）、`meihua`（梅花易数）、`qimen`（奇门遁甲）、`liuren`（大六壬）
- **定义**：占卜的四种术数方法，`method` 是字符串（非枚举）。由服务端 `DivinationCore` 分派到四个引擎重算权威盘面。
- **四端对应物**：
  - 后端：`divination/core/DivinationCore.java`、`divination/core/{liuyao,meihua,qimen,liuren}/*Engine`、`DivinationWebController.getMethods()`（`/web/auth/div/methods`）。
  - Android：`:feature:divination` `network/DivinationApi.kt`、`core/{liuyao,meihua,qimen,liuren}`。
  - 鸿蒙：`feature/divination/network/DivinationApi.ets`（`METHOD_NAMES` 映射六爻/梅花易数/奇门遁甲/大六壬）。
  - Web：`dict.js` `DIV_METHOD_OPTIONS`（筛选/表单下拉）。
- **易混淆点**：「六爻」在代码里全称「六爻纳甲」；`method` 是**字符串标识**，不是整数枚举，新增术数需四端同步。

### 6.2 卦例（div_record）

- **代码标识**：`div_record`、`DivRecord`、`cast_method`、`client_chart` / `server_chart`
- **定义**：一次起卦的记录。`method` 为四术之一；`category` 为问题领域（`general/career/wealth/...`）；`cast_method` 为 `auto/manual/time`；`source` 为 `app/today`；`lines` 是起卦原始输入；`client_chart` 是端上排盘快照，`server_chart` 是服务端 core 重算的**权威盘面**，`chart_verified` 标记两者是否一致。
- **四端对应物**：
  - 后端：`divination/entity/DivRecord.java`、`divination/service/impl/DivRecordServiceImpl.java`。
  - Android：`:feature:divination` `core/` 排盘引擎 + `ui/page/chart/DivChartPage.kt`。
  - 鸿蒙：`feature/divination/data/DivChartCodec.ets` / `DivChartReader.ets` / `DivinationStore.ets`。
  - Web：`views/divination/DivAuditList.vue`（卦例审核）。
- **易混淆点**：`server_chart` 是权威，`client_chart` 只是快照；两端不一致时 `chart_verified=0`，不要以端上盘面为准。

### 6.3 模型目录与用户模型配置（div_model_config / div_user_api_config）

- **代码标识**：`div_model_config`（模型目录：vendor/name/base_url/model_id，**不存密钥**）、`div_user_api_config`（用户 BYOK 配置，双模式：`model_config_id` 目录引用 / `custom_base_url`+`custom_model` 自定义）、`div_platform_setting`（`div_use_enabled` 总开关 + `byok_daily_limit`）、`DIV_MASTER_KEY`、`key_version`
- **定义**：平台**零兜底**——解读只消费用户 BYOK 配置，用户未配置时后端 SSE error **428**（客户端路由拦截跳设置页）；无平台默认/备用模型（2026-11-11 起 `default_model_id`/`fallback_model_id`/`platform_daily_limit` 已移除）。用户在客户端**分步选择**：第一步选厂商、第二步选该厂商的模型（目录模式，接入点服务端从目录取）或全自定义（url 自填）；保存前服务端真实 1-token 试呼，通过才 AES-256-GCM 加密入库，列格式 `v{keyVersion}:{base64(iv)}:{base64(ciphertext+tag)}`；主密钥走 `DIV_MASTER_KEY` 环境变量。
- **四端对应物**：
  - 后端：`divination/service/AesGcmCipher.java`、`DivUserApiConfigService`（双模式保存/解析）、`DivModelConfigService`（目录管理+批量导入+树形组装 `listEnabledTree`）、`llm/NoProviderConfiguredException`；用户端目录接口 `GET /web/auth/div/models` 返回**树形** `VendorGroupVo{vendor, provider, baseUrl, priority, models[]}`（一次请求返回整棵树，客户端本地过滤分步选择）。
  - Android：`:feature:user:impl` `DivSettingApi.kt` + `ApiConfigSheet.kt`（设置页「解读模型」，目录模式分步：厂商 → 模型）、`:feature:divination` `DivinationApi.kt`（配置查询+428 拦截）。
  - 鸿蒙：`feature/divination/network/DivSettingsApi.ets` + `pages/Setting.ets`（同分步）、解读页 428 跳设置。
  - Web：`views/divination/DivModelList.vue`（目录管理+批量导入）、`DivSettings.vue`（总开关+限额）。
- **易混淆点**：`div_model_config` 为**扁平存储**（一厂商 × 一模型 = 一行，vendor 分组字段），树形是**接口层组装**（`listEnabledTree`：单条 SQL + 内存 O(n) 分组，一次请求全量返回，无 N+1、当前量级不加缓存）；`api_key_cipher` 恒空串占位（目录不存密钥，管理端录入 key 仅用于该条目连通性测试）；列表/回显只出掩码 `****` + 明文尾 4 位，**永不出明文与密文**（管理端列表走 `DivModelConfigAdminVo` 裁剪密文）；`divUseEnabled` 是功能总开关（0=客户端隐藏入口并拦截），不是 BYOK 子开关。
- → ADR-0006

### 6.4 SSRF 校验边界（DivUrlSafetyValidator）

- **代码标识**：`DivUrlSafetyValidator`（`divination/support/`）、`div.ssrf.dns-check`（DNS 层开关，默认 true）
- **定义**：观星台外呼 URL 校验（fail-closed），校验链：① 结构（仅 HTTPS、拒绝 userinfo、host 非空、端口合法）② 主机快拒（localhost 变体 / `*.local` / `*.internal` / `*.home.arpa`）③ IP 字面量本地判定 + 域名 DNS **全解析逐 IP** 判定（任一内网 IP 即整体拒绝；解析失败拒绝）④ IP 禁段：环回/任意本地/链路本地/RFC1918/CGNAT(100.64/10)/0.0.0.0/组播/ULA(fc00::/7)/IPv4 映射内网/**benchmark 保留段(198.18/15、2001:2::/64)**。校验对象覆盖用户 BYOK 自定义接入点与管理端目录 base_url（试呼入口统一在 `DivModelConfigService.testConnectivity`）。
- **四端对应物**：
  - 后端：`divination/support/DivUrlSafetyValidator.java`（单测 `DivUrlSafetyValidatorTest` 9 例）。
  - Android / 鸿蒙 / Web：无对应实现（纯服务端校验）。
- **易混淆点**：① `div.ssrf.dns-check=false` 仅限本机 Fake-IP 代理调试环境（Fake-IP 把一切域名劫持到 198.18/15，DNS 层必然误报），生产不得关闭；② DNS 校验通过到真实外呼之间存在 DNS 重绑定窗口（TOCTOU），彻底根治需外呼 pin IP + SNI——Reactor Netty 默认不跟随重定向（首跳即终跳）已消除 302 绕过；③ IPv4 映射 v6（`::ffff:10.0.0.5`）按内嵌 v4 再判（`isIPv4MappedAddress` 包私有，手写字节判定）。
- → ADR-0006

---

## 7. 工作台与工具箱

### 7.1 工作台入口（workbench_entry / entryKey）

- **代码标识**：`workbench_entry`、`entryKey`、`uk_entry_key`、`WorkbenchEntryVo`
- **定义**：移动端工作台入口卡片的配置。`entry_key` 是端上兜底列表与下发数据对齐的业务唯一标识，**创建后不可修改**；`target` 是跳转 URI（`smartwash://...` 或 `https://...`），空串 = 功能建设中。
- **四端对应物**：
  - 后端：`workbench/entity/WorkbenchEntry.java`、`WorkbenchEntryServiceImpl`（`/web/auth/workbench/entries` 下发）、`WorkbenchEntryAdminController`。
  - Android：`ui/page/workbench/WorkbenchTool.kt`（`WorkbenchTint`、`workbenchIconFromKey`）、`WorkbenchViewModel.kt`。
  - 鸿蒙：`feature/workbench/api.ets`（`WorkbenchTool`、`workbenchTintFromKey`、`workbenchIconFromKey`）、`feature/workbench/Workbench.ets`。
  - Web：`views/workbench/WorkbenchEntryList.vue`、`api/workbench.js`、`dict.js` `WORKBENCH_ENTRY_ICON_OPTIONS` / `WORKBENCH_ENTRY_COLOR_OPTIONS`。
- **易混淆点**：图标 key 白名单（calendar/search/book/calculate/history/chat/link/default）与色组 key（green/blue/orange/pink/teal/purple）由**后端下发、端上白名单映射**，未知值兜底默认，两端需同步维护。

### 7.2 短码内核（toolbox_short_code / contentType）

- **代码标识**：`toolbox_short_code`、`code`、`contentType`、`uk_code`
- **定义**：工具箱短码主表，五入口共用：`1` 短链 / `2` 活码 / `3` 交付箱 / `4` 图床 / `5` 时间胶囊。差异全部体现为 `content_type` + 可空列（`target` / `content` / `password_hash` / `burn_after_read` / `unlock_at`），不做子表。`code` 为 7 位随机或 4-16 位自定义别名。
- **四端对应物**：
  - 后端：`toolbox/entity/ToolboxShortCode.java`、`ToolboxShortCodeServiceImpl`、`ToolboxShortCodeController`（`/web/auth/toolbox/short-codes`）。
  - Android：`:feature:toolbox` `logic/ShortCodeRules.kt`、`ToolboxRoute`。
  - 鸿蒙：`feature/toolbox/ToolboxShortCodeList.ets` / `Create` / `Detail`、`feature/toolbox/api.ets`。
  - Web：`views/toolbox/ShortCodeList.vue`、`api/toolbox.js`、`dict.js` `SHORT_CODE_CONTENT_TYPE_OPTIONS`。
- **易混淆点**：日常说的「短链」通常指 `content_type=1`；「短码」是 `code`（7 位随机或别名）；已实现类型：1 短链、2 活码（储物柜资产码首消费）、6 设备投递、7 私密文本（阅后即焚）、8 物品标签（一物一码）；交付箱（3）/图床（4）/时间胶囊（5）为表结构预留。

### 7.3 短码访问明细（toolbox_short_visit）

- **代码标识**：`toolbox_short_visit`、`ipHash`、`code`（冗余）
- **定义**：短码访问流水，与主表解耦（无外键，主表删除后明细保留，统计历史不丢）。IP 只存前 8 位摘要，不存明文。
- **四端对应物**：
  - 后端：`toolbox/entity/ToolboxShortVisit.java`、`ToolboxShortVisitService`。
  - Android / 鸿蒙：不直接消费（管理端统计用）。
  - Web：`views/toolbox/ShortCodeList.vue`（详情弹窗最近访问）。
- **易混淆点**：`code` 是冗余列，不随主表删除级联；`ip_hash` 是摘要不是明文 IP。

### 7.4 短链对外域名（shortLinkBaseUrl）

- **代码标识**：`ServerConfigVo.shortLinkBaseUrl`、`PublicBaseUrlResolver`
- **定义**：短链对外跳转域名，由 `GET /web/config` 运行期下发；后端拼 `shortUrl` 时显式配置优先，否则按请求推导。
- **四端对应物**：
  - 后端：`vo/ServerConfigVo.java`、`toolbox/toolkit/PublicBaseUrlResolver.java`、`WebConfigController`。
  - Android：`config/ServerConfig.kt`（`shortLinkBaseUrl`，仅展示不拼接）。
  - 鸿蒙：`network/api/ServerConfigApi.ets`（`GET /web/config`，启动预热）。
  - Web：管理端展示。
- **易混淆点**：端上**只展示、不拼接**——后端返回的 `shortUrl` 即完整地址（`{shortLinkBaseUrl}/web/t/{code}`）。

### 7.5 设备投递（toolbox_delivery_item / delivery session）

- **代码标识**：`toolbox_delivery_item`、`DeliverySessionVo`、`/web/auth/toolbox/delivery/**`、`/web/delivery/**`
- **定义**：设备间文件/文本投递会话——创建会话得 code，会话内发布文本或文件（MinIO）、取走条目、关闭会话；对端经公开页 `/web/delivery/{code}/enter` 免登录进入。会话过期 10 分钟由 `ToolboxDeliveryCleaner` 清理（MinIO 对象 → 条目 → 会话）。
- **四端对应物**：
  - 后端：`toolbox/delivery/`（`ToolboxDeliveryController` 创作端 + `ToolboxDeliveryPublicController` 公开对端）。
  - Android：`:feature:toolbox` `DeliveryApi.kt` + `ui/delivery/`。
  - 鸿蒙：`feature/toolbox/DeliveryApi.ets` + `ui/delivery/`。
  - Web：`views/delivery/DeliveryPickup.vue` / `DeliverySession.vue`（公开对端页）。
- **易混淆点**：公开对端接口 `/web/delivery/**` 与创作端 `/web/auth/toolbox/delivery/**` 是**两组**，不要混用；文件上限 20MB，业务码 403/404/409/413 由页面分支。

### 7.6 私密文本 / 阅后即焚（contentType=7 / burned_at / SecretState）

- **代码标识**：`contentType=7`、`burned_at`、`SecretState`（ACTIVE/BURNED/EXPIRED）、`ToolboxSecretController`（`/web/s/**`）、`ToolboxGcmCipher`（`TOOLBOX_MASTER_KEY`）、`toolbox.web-base-url`
- **定义**：短码内核的私密文本入口——创建（content ≤2000 字符 AES-256-GCM 加密落库、可选口令 BCrypt、时效预设 1小时/1天/7天必填、恒公开）→ 分享 `{webBaseUrl}/s/{code}` → 接收者经 Web 公开页 `POST /web/s/{code}/view` 点击揭示（事务内 `SELECT FOR UPDATE` + 条件焚毁，成功即 `burned_at` 写入并清空 `content`）→ 二次访问 410。错误口令 403 **不焚毁**（500ms 延迟防爆破）；meta 只读不焚毁。分享链接 `secretUrl` 未配置 `toolbox.web-base-url` 时为空串（端上降级仅展示短码）。
- **四端对应物**：
  - 后端：`toolbox/controller/ToolboxSecretController`、`ToolboxShortCodeServiceImpl.viewSecret/secretMeta`、`common/crypto/GcmCipherService`（通用内核，占卜域 `AesGcmCipher` 薄继承同源）、`common/log/LogMasker`（content 入脱敏清单）。
  - Android：`:feature:toolbox` `ShortCodeRules`（`SecretState`/`SecretExpiryPreset`）+ 创建页模式切换 + 详情/列表状态徽章。
  - 鸿蒙：`feature/toolbox/ShortCodeRules.ets` + `api.ets`（`toolboxSecretStateText`）+ 三页同构。
  - Web：`api/secret.js`（`SECRET_CODE` 403/404/410 信封分支）+ `views/secret/SecretView.vue`（公开揭示页 `/s/:code?`）。
- **易混淆点**：揭示必须 **POST**（GET 会被链接预览爬虫提前烧毁）；`content` 列存**密文**，任何端/管理端永不回显（连掩码不给）；`/web/t/{code}` 对私密文本一律 410（target 为空），不要用短链跳转链路打开它；分享域名 `toolbox.web-base-url` 与短链域名 `toolbox.public-base-url` 是**两个配置**（前者指向 Web SPA，无法按请求推导）。
- → ADR-0008

### 7.7 物品标签（一物一码 / contentType=8 / toolbox_asset_tag）

- **代码标识**：`contentType=8`、`toolbox_asset_tag`、`status`（0 正常/1 寻物中/2 已寻回）、`contact_cipher`、`last_scanned_at`、`/web/auth/toolbox/asset-tags`、`/web/tag/{code}`
- **定义**：给用户自己的物品建一张二维码标签——创建（物品名必填/描述与联系方式可选/联系方式 AES-256-GCM 加密落库）→ 生成标签图（物品名 + 二维码，Android 端合成）打印张贴 → 捡到者扫码经 Web 公开寻物页 `/tag/{code}` 看到物品信息；**联系方式仅「寻物中 + 失主已留」时解密下发**（防批量扫码扒信息）；每次解析为失主留下扫码线索（访问明细 IP 摘要 + 最近扫码时间）。状态流转（正常↔寻物中↔已寻回）裁定在后端。
- **四端对应物**：
  - 后端：`toolbox/assettag/`（镜像 delivery 子包：entity/mapper/service/三 controller）、`ToolboxAssetTagServiceImpl`。
  - Android：`:feature:toolbox` `network/AssetTagApi.kt` + `ui/assettag/`（List/Create/Detail + `AssetTagLabelImage` 标签图合成）。
  - 鸿蒙：`feature/toolbox/api.ets` + `ToolboxAssetTagList/Create/Detail.ets`（标签图合成未做，以复制链接承载分享，见 ADR-0009 取舍）。
  - Web：`api/assetTag.js` + `views/assettag/AssetTagLanding.vue`（公开寻物页 `/tag/:code`）。
- **易混淆点**：主记录复用 `toolbox_short_code`（type=8），明细表 `toolbox_asset_tag` 按 `code` 关联（uk_tag_code）；owner 删除与管理端治理删除均**级联**（明细 + 访问明细 + 主表）；联系方式**永不回显**（连掩码不给），编辑时留空 = 不修改、空串 = 清空；管理端仅有治理删除接口，无物品信息读取通道。
- → ADR-0009

---

## 8. 深链路由

### 8.1 DeepLinkRegistry（host 注册机制）

- **代码标识**：`DeepLinkRegistry`、`DeepLinkContributor`、`DeepLinkRouteFactory`、`HOST_ROUTES`
- **定义**：`smartwash://{host}` 的 host → 路由目标注册表。**Android**：各域 impl 模块在自己的 Hilt Module 里以 `@Provides @IntoSet` 贡献 `DeepLinkContributor`，壳层 `DeepLinkRegistry` 构造时合并为总表；host 一律小写，**重复 host 后到者忽略并告警**（不让分发结果依赖聚合顺序）。**鸿蒙**：单 entry 集中式路由，收敛为一张 `HOST_ROUTES` Map（`RouteNames` 为单一事实来源），重复 key 由 TS 编译器报错兜底。
- **四端对应物**：
  - Android：`app/.../deeplink/DeepLinkRegistry.kt`、各域 `di/DeepLinkModule.kt`、`common/utils/deeplink/DeepLinkContributor.kt`。
  - 鸿蒙：`utils/deeplink/DeepLinkRegistry.ets`（`lookup`）。
  - 后端 / Web：无对应实现（后端只有路由**字典**，见 §8.3）。
- **易混淆点**：Android 是**运行时聚合**（Hilt 多绑定），鸿蒙是**编译期集中表**；两端 host 清单需逐字对齐，否则同包游戏/同链接行为分叉。**已知缺口**：鸿蒙已注册 `order-detail/{orderId}`，Android 尚未注册（只有 `order`）——同链接两端行为不一致，待补齐。

### 8.2 DeepLinkParser 与 DeepLinkAction

- **代码标识**：`DeepLinkParser`、`DeepLinkAction`（`NativeRoute` / `WebViewUrl` / `Unknown`）、`DeepLinkResult`
- **定义**：纯函数解析器，零路由知识、零 UI 依赖，解析全程不抛异常。内建判定：`http(s)://` → WebView；`smartwash://web?url=&title=` → WebView（url 必填且须 http/https）；其余 host → `routeLookup`（命中 → NativeRoute，未命中 → Unknown）。
- **四端对应物**：
  - Android：`common/utils/deeplink/DeepLinkParser.kt`、`DeepLinkAction.kt`、`app/.../deeplink/DeepLinkHandler.kt`。
  - 鸿蒙：`utils/deeplink/DeepLinkParser.ets`（`DeepLinkKind` 枚举 + `DeepLinkResult` 接口）、`DeepLinkHandler.ets`。
  - 后端 / Web：无对应实现。
- **易混淆点**：字面 `+` **不转空格**（URI query 语义，与表单编码区分）；`smartwash://web` 是内建分支，不经注册表；`t/{code}` 的 code 大小写敏感，由注册表自行校验。

### 8.3 路由字典（app_deeplink_route / platform）

- **代码标识**：`app_deeplink_route`、`host`、`platform`、`uk_host`、`DeeplinkRouteDict`
- **定义**：App deeplink 路由字典，双重用途：① 管理端配置 `workbench_entry.target` 的数据源（可选 host 下拉）；② 经 `GET /web/deeplink/routes` 公开下发，端上冷启动拉取做**未知 host 的元数据兜底**。`platform`：`1` Android / `2` HarmonyOS / `3` 双端。`host` 创建后不可修改。
- **四端对应物**：
  - 后端：`workbench/entity/AppDeeplinkRoute.java`、`DeeplinkRouteServiceImpl`、`DeeplinkRouteAdminController`、`WebDeeplinkController`（公开下发）。
  - Android：`network/api/DeepLinkApi.kt`、`DeeplinkRouteVo`、`DeepLinkRepository`（内存缓存 + 单飞锁）。
  - 鸿蒙：`network/api/workbench.ets`（`getDeeplinkRoutes`）、`DeepLinkHandler.ets`（仅已登录时拉取）。
  - Web：`views/workbench/DeeplinkRouteList.vue`、`api/deeplink.js`、`dict.js` `DEEPLINK_PLATFORM_OPTIONS`。
- **易混淆点**：字典只做「未知 host 是否合法」的**诊断留痕与预热**，不改变导航语义——host 合法但端上未注册仍回 `Unknown`（提示「暂不支持该链接」）；字典拉取失败静默，不影响本地解析。

### 8.4 自有 scheme（smartwash://）

- **代码标识**：`smartwash://`、`SCHEME = "smartwash"`、`DEEPLINK_SCHEME`
- **定义**：App 自有 URL scheme，形态 `smartwash://{host}/{path}?{query}`。内建 host `web`（WebView 分支）；`t/{code}` 为短码跳转；其余 host 由注册表分发。
- **四端对应物**：
  - Android：`DeepLinkParser.SCHEME`、`ShellRoute.WEB_VIEW`。
  - 鸿蒙：`DeepLinkParser.DEEPLINK_SCHEME`、`RouteNames.WEB_PAGE`。
  - 后端 / Web：无（scheme 由端上消费）。
- **易混淆点**：`smartwash://web?url=` 是通用容器入口（可打开任意网页，但 url 必须 http/https）；`smartwash://t/{code}` 的自有 scheme 版对应 Web 短链 `{shortLinkBaseUrl}/web/t/{code}`。

---

## 9. 环境与设计系统

### 9.1 BASE_URL 构建期注入

- **代码标识**：`BuildConfig.BASE_URL`（Android）、`BuildProfile.BASE_URL`（鸿蒙）、`__SMARTWASH_BASE_URL__`（Web）
- **定义**：API 根地址构建期注入、源码零硬编码、release 未注入即空串（不可用）。Android 经 `-PbaseUrl` + `buildConfigField`；鸿蒙经 `buildProfileFields`；Web 经 Vite `define`，兜底链 `SMART_WASH_BASE_URL → VITE_BASE_URL → '/api'`。
- **四端对应物**：
  - Android：`app/build.gradle`、`di/NetworkModule.kt`。
  - 鸿蒙：`entry/build-profile.json5`、`network/Axios.ets`（`BuildProfile.BASE_URL`）。
  - Web：`vite.config.js`、`src/utils/http.js`。
  - 后端：无（三端各自的机制）。
- **易混淆点**：三端机制名不同（BuildConfig / BuildProfile / Vite define），但策略一致（构建期、空串失败、debug-only 演示兜底）；**运行期可变的服务端地址不走本机制**（见 §9.2）。
- → ADR-0004

### 9.2 MinIO 端点运行期下发（/web/config）

- **代码标识**：`GET /web/config`、`ServerConfigVo`、`minioEndpoint`、`shortLinkBaseUrl`、`webBaseUrl`、`ServerConfig`、`ServerConfigInitTask`
- **定义**：MinIO 对外端点、短链对外域名与 Web 前端对外地址经 `GET /web/config` **运行期下发**，替代早期编译期注入 `BuildConfig.MINIO_ENDPOINT`（域名变更免发版）。端上启动预热写入内存，WebView 主题桥白名单消费该运行期值。`webBaseUrl`（`toolbox.web-base-url`，`TOOLBOX_WEB_BASE_URL` 注入）是私密文本分享链接的域名来源，只认显式配置、无法按请求推导（Web SPA 与后端不同源）。
- **四端对应物**：
  - 后端：`controller/web/WebConfigController.java`、`vo/ServerConfigVo.java`、`MinioConfig.publicEndpoint()`（`externalEndpoint` 优先，缺失回退 `endpoint`）、`toolbox/toolkit/WebBaseUrlResolver`。
  - Android：`config/ServerConfig.kt`（`minioEndpoint` / `shortLinkBaseUrl`）、`init/ServerConfigInitTask.kt`（优先级 300，非阻塞，失败静默）。
  - 鸿蒙：`network/api/ServerConfigApi.ets`（启动预热；H5 主题桥未接入）。
  - Web：无（管理端不消费）。
- **易混淆点**：`minioConfig.urlOf(objectKey)` 是所有拼给端上直链（游戏可玩地址、头像）的统一口径，**禁止直接用 `getEndpoint()`**（那是内部通信地址）；`webBaseUrl` 未配置时后端返回空串、私密文本 `secretUrl` 同为空串（端上降级仅展示短码），生产必须 HTTPS。
- → ADR-0004

### 9.3 清氧设计系统（清氧·日 / 清氧·夜 / 玄墨）

- **代码标识**：`清氧`、`清氧·日` / `清氧·夜`、`玄墨`、`DrumMark`（滚筒圆）、`#2D9B6A`
- **定义**：Android 用户端设计系统（`docs/design/DESIGN-SPEC.md` v0.4 + `SmartWash-Android/DESIGN.md`）。双主题「清氧·日 / 清氧·夜」共享结构令牌，只换强调层；观象台另有「玄墨」子主题（仪式文本用衬线）。签名图形为「滚筒圆」DrumMark。
- **四端对应物**：
  - Android：`common:ui`（theme + components）、`SmartWash-Android/DESIGN.md`、`docs/design/design-tokens.json`。
  - 鸿蒙：按 Android 基准对齐（`docs/design/harmony-align/`），复用色板/字阶但不引入 Android 组件。
  - Web：**独立的「靛蓝工作台」设计系统**（`SmartWashWeb/DESIGN.md`，Indigo/Slate），**不适用清氧**。
  - 后端：无。
- **易混淆点**：绿色 `#2D9B6A` 是红线（玄墨子主题除外）；Android 禁用动画库（Lottie/Rive/Shimmer/Vico）、禁新增 `Icons.*`、动效必须带 reduced motion 分支；Web 与 Android 是两套设计系统，不要跨端套用色板。

---

## 10. 平台模块

### 10.1 站内信（notice / notice_read / NoticeType）

- **代码标识**：`notice`、`notice_read`、`NoticeType`（SYSTEM / ANNOUNCEMENT / DINING_JOIN / DINING_FINISH）、`WebNoticeController`
- **定义**：管理端发送站内信（title / content / type / deepLink），用户端收件箱分页 + 未读数 + 标记已读（幂等）+ 全部已读；已读记录按用户落库。通知分广播（`user_id` NULL，全平台可见）与定向（`user_id` 非空，按接收人一行，如点单加入/结束通知）；收件箱与未读数口径 = 广播或定向给我。
- **四端对应物**：
  - 后端：`notice/controller/WebNoticeController.java` + `NoticeAdminController`。
  - Android：`:feature:notice`（`NoticeServiceApi` + 收件箱页）。
  - 鸿蒙：`feature/notice/`（`NoticeApi.ets` + `NoticeInboxPage`）。
  - Web：`views/system/NoticeList.vue` + `api/notice.js`。
- **易混淆点**：未读数 data 缺失按 0（角标隐藏，不拖垮我的页）；已读标记 / 全部已读在后端幂等，端上不做请求去重。

### 10.2 积分（point_account / point_ledger / point_sign_in / PointReason）

- **代码标识**：`point_account`、`point_ledger`、`point_sign_in`、`PointReason`、`PointsOverviewVo`
- **定义**：平台积分账户（余额 / 累计获得），签到得基础分（5）+ 连签奖励（7 天周期 +10），流水记账，月榜 TOP 50。
- **四端对应物**：
  - 后端：`points/`（`PointsWebController` + `PointsProperties`）。
  - Android：`:feature:points`（`PointsServiceApi` + 签到有礼页）。
  - 鸿蒙：`feature/points`（`PointsApi.ets` + `Points.ets`）。
  - Web：无对应管理页（积分仅移动端消费）。
- **易混淆点**：`signIn` 幂等——重复请求 `code=200` 且 `alreadySigned=true`，不重复加分；月榜手机号后端已脱敏。

### 10.3 订阅（user_subscription / user_subscription_usage_log / subscription_reminder_log）

- **代码标识**：`user_subscription`、`SubscriptionStatus`（active/paused/canceled/finished）、`SubscriptionRemindType`
- **定义**：订阅全生命周期——新建 / 编辑 / 详情 / 列表，状态流转 pause / resume / cancel / finish；用量打卡与明细分页；到期提醒（已读幂等）。后端每日 07:30 `SubscriptionRenewJob` 推进自动续费。
- **四端对应物**：
  - 后端：`subscription/`（`UserSubscriptionController` + `SubscriptionAdminController` + `SubscriptionRenewJob`）。
  - Android：`:feature:subscription`（15 个端点 + 4 个页面）。
  - 鸿蒙：`feature/subscription/`（`api.ets` + 4 个页面）。
  - Web：`views/system/SubscriptionList.vue` + `api/subscription.js`（只读汇总/列表）。
- **易混淆点**：状态流转动作由后端裁定，端上只做按钮可见性；提醒已读在后端幂等。

### 10.4 记忆复习（review_card / study_material / FSRS）

- **代码标识**：`review_card`、`study_material`、`review_log`、`FsrsScheduler`、`ReviewRating`
- **定义**：资料 → 卡片；FSRS-6 调度（后端 `review/scheduler/FsrsScheduler` 纯函数，移植自 py-fsrs）；端上只传 `(cardId, rating 1..4)`，不参与调度计算。
- **四端对应物**：
  - 后端：`review/`（`ReviewWebController` + `FsrsScheduler`）。
  - Android：`:feature:review`（7 个端点 + 队列/学习/资料页）。
  - 鸿蒙：`feature/review/`（`api.ets` + 3 个页面）。
  - Web：无管理页。
- **易混淆点**：rating 语义 1=Again / 2=Hard / 3=Good / 4=Easy（Anki 惯例）；新卡 `stability/difficulty` 为 null；`durationMs` 由端上以自身时钟折算，服务端以自身时钟为准。

### 10.5 操作审计（admin_audit_log / @Audited / HMAC 链）

- **代码标识**：`admin_audit_log`、`@Audited`、`AdminAuditLogController`、`audit.hmac-secret`
- **定义**：管理端写操作留痕，HMAC 链防篡改；`/admin/audit-logs` 分页查询，`/admin/audit-logs/verify` 校验链完整性。`@Audited` 已覆盖角色/储物柜/订单/用户券/优惠券/支付/用户/学校/管理员/洗衣项目等管理端写接口。
- **四端对应物**：
  - 后端：`common/audit/`（`AdminAuditLogController` + 切面 + HMAC）。
  - Web：`views/system/AuditLogList.vue` + `api/audit.js`。
  - Android / 鸿蒙：无对应实现。
- **易混淆点**：`audit.hmac-secret` 生产 profile 无默认值（围栏），未配置则审计链无法校验；新增管理端写接口必须加 `@Audited`。

### 10.6 限流与请求追踪（@RateLimit / TraceId / AccessLog）

- **代码标识**：`@RateLimit`、`RateLimitConfig`、`TraceIdFilter`、`AccessLogFilter`、`X-Request-Id`
- **定义**：`@RateLimit` 用 Redis Lua 做分布式限流（登录/注册/验证码/短链跳转/占卜解读按 IP 或用户限流，超限 429）；`TraceIdFilter` 生成/复用 `X-Request-Id` 写入 MDC；`AccessLogFilter` 每请求一行日志（方法/路径/状态/耗时/用户）+ 敏感字段脱敏。
- **四端对应物**：
  - 后端：`common/ratelimit/` + `common/log/`。
  - Android / 鸿蒙 / Web：无感知（Web 经响应头可观测 request_id）。
- **易混淆点**：`application.yaml` 中 `request-log` 缩进在 `toolbox:` 下（实际生效 `toolbox.request-log.*`），而绑定前缀是 `smartwash.request-log`——配置未生效，需统一。

### 10.7 深链主机缺口

- **代码标识**：`order-detail/{orderId}`
- **定义**：鸿蒙 `DeepLinkRegistry` 已注册 `order-detail/{orderId}` → `OrderDetail`；Android 尚未注册（只有 `order` 带 tab）。两端 host 清单需逐字对齐。
- **四端对应物**：鸿蒙 `utils/deeplink/DeepLinkRegistry.ets`；Android `DeepLinkRegistry`（缺该 host）。
- **易混淆点**：同链接在鸿蒙可直达订单详情、在 Android 回 `Unknown`；补注册时注意 Android 订单详情页留壳、路由常量归 `OrderRoute`。

### 10.9 吃啥（dining_category / 菜谱分类）

- **命名**：产品显示名「吃啥」（2026-11-14 由曾用名「家庭点单」更名；代码标识 `dining` 不变）。扫码加入链路上移为壳层全局扫一扫（工作台常驻入口），dining 域经 `DiningApi.normalizeScanToDeepLink` 归一加入链接。
- **代码标识**：`dining_category`、`DiningCategory`、`/admin/dining/categories`
- **定义**：平台公共菜谱库的分类目录（name 唯一 / sort 升序 / 物理删除），运营在 Web 后台增删改查；菜谱按 `dining_recipe.category_id` 挂靠（可空=未分类），用户端按分类组织点单菜单左侧分类栏。点单订单 / 本单菜单 / 选菜记录 / 购买清单 / join_token 见 10.11，同步通道与复用入口见 10.12。
- **四端对应物**：
  - 后端：`dining/`（管理端 `DiningCategoryAdminController`，ROLE_ADMIN，写接口 @Audited；用户端只读目录 `WebDiningCategoryController` `/web/auth/dining/categories/all`，ROLE_USER）。
  - Android：`:feature:dining:{api,impl}`（`DiningServiceApi.categories`，菜谱库分类筛选 chips；04 号票）。鸿蒙：已实现（2026-11-14，`entry/src/main/ets/feature/dining/`，`DiningApi.ets` 对齐同契约）。契约以 `WebDiningCategoryController` 为源。
  - Web：`views/dining/DiningCategoryList.vue` + `api/dining.js`（菜单组「吃啥」）。
- **易混淆点**：dining 是独立业务域（避开既有洗护 `orders` 域）；删除分类为物理删除且无级联——**被菜谱引用时服务层拒绝删除**（按 category_id 计数，提示引用数量），先校验后删保证批量整体拒绝。

### 10.10 菜谱（dining_recipe）

- **代码标识**：`dining_recipe`、`DiningRecipe`、`/admin/dining/recipes`
- **定义**：平台公共菜谱库的正片条目：菜名（不做唯一约束）、食材名数组（`ingredients` JSON 文本，服务层与结构化 `List<String>` 互转）、步骤、封面（`cover_object_key` 存 MinIO `dining/covers/` 前缀对象键，直链由 `MinioConfig.urlOf` 拼装）、来源 `source`（ADMIN=运营录入 / USER=用户新增直上架）、上下架 `status`（1=上架 0=下架）。下架语义：不再出现在用户端上架查询，历史点单订单按菜谱 ID 引用不受影响；购买清单 = 所选菜谱食材名并集去重。
- **四端对应物**：
  - 后端：`dining/`（`DiningRecipeAdminController`，ROLE_ADMIN，写接口全 @Audited；上传校验 `RecipeImageValidator`：JPG/PNG/WebP + 魔数 + 扩展名一致性 + 5MB，走统一 `FileStorageService`）。批量导入：`DiningRecipeImportService` 两阶段——`POST /import` 解析校验（xlsx/CSV，缺列/非法分类/超长给行号级错误，**预览前不落库**）→ `POST /import/confirm` 整批事务写入（任一失败行全批不落库，`@Audited` DINING_RECIPE_IMPORT）；模板由 `GET /import/template` 动态生成。
  - Android：`:feature:dining:{api,impl}`（`DiningServiceApi`：`/web/auth/dining/recipes` 上架列表/详情/新增直上架/封面上传，用户端新增 source=USER 直上架；04 号票，封面直链匿名读依赖 MinIO 白名单 `dining/covers/`）。鸿蒙：已实现（菜谱库/详情/新增三页，封面上传同契约）。契约以 `WebDiningRecipeController` 为源。
  - Web：`views/dining/DiningRecipeList.vue`（列表/筛选/CRUD/上下架/封面上传回显）+ `api/dining.js`；分类下拉走 `optionCache.diningCategoryOptionsCache`。导入交互 `views/dining/components/RecipeImportDialog.vue`（下载模板→上传解析→预览确认→逐行结果报告）。
- **易混淆点**：接口分类字段协议——新增传 `null`/`0` 均为未分类；修改传 `null`=不修改、`0`=改回未分类、`>0`=换分类（哨兵归一在服务层）。管理端修改为全量字段语义（字段不传即不修改，MyBatis-Plus 跳过 null 列）；上下架走专用 `{id}/status/{status}` 接口而非修改接口。批量导入的两阶段语义：预览只解析校验不落库，确认端点再次逐行校验——任一行失败全批不落库（rolledBack=true 全行回显失败，非半批）；导入模板「分类名」必须与已有分类完全一致（不存在即行级报错，不自动建）；「封面链接」列仅校验格式不落库（对象键与外链语义不同，封面走单独上传）；导入行数上限 500、文件上限 5MB。

### 10.11 点单订单（dining_order / dining_order_member / dining_order_menu / dining_order_item）

- **代码标识**：`dining_order`、`DiningOrder`、`/web/auth/dining/orders`（ROLE_USER）；成员 `dining_order_member` / 本单菜单 `dining_order_menu` / 选菜记录 `dining_order_item`。
- **定义**：一人发起点单（title + 从菜谱库勾选本单菜单，支持 `fromOrderId` 复用历史菜单）、生成二维码分享（内容 `https://{host}/dining/join/{orderId}?t={join_token}`），他人扫码 → brief 确认弹窗 → 加入（创建者即首个成员）；共享购物车勾选想吃的菜（一人一菜一条，note 备注，取消即删行）；发起者结束后生成购买清单（食材名并集去重 + 按菜视图，`ShoppingListAssembler` 纯函数，快照落 `shopping_list` 列）。
- **version 轮询协议**：`dining_order.version` 乐观并发——所有选菜变更走条件 UPDATE `WHERE id=? AND version=? SET version=version+1`，不匹配返回业务错误码 409（客户端拉取最新后重放）；详情接口为轮询主接口，客户端带 `knownVersion`，一致返回 `unchanged=true` 轻量响应，不一致/未带返回全量。
- **四端对应物**：
  - 后端：`dining/`（`WebDiningOrderController` / `WebDiningOrderItemController`，ROLE_USER；`DiningRecipeGate` 为点单对菜谱库的窄访问门面）。
  - Android：`:feature:dining:{api,impl}`（07 号票发起链路）——`DiningServiceApi` 订单三接口（create/my/orderDetail）+ `DiningOrderStatus` 枚举映射；订单列表页（域首页，深链 host `dining` 落点 + 工作台入口）/ 创建订单页 / 分享视图 / 点单页 `OrderDetail`（09 号票：美团式左分类栏 + 右菜品列表 + 底部已选栏 + 参与者区 + 发起者「结束点单」占位入口；左右联动派生逻辑在 `DiningMenuNavigator` 纯函数，本地勾选/备注即时生效，写接口 version 冲突重放留 10 号票）。二维码内容 `{BASE_URL origin}/dining/join/{orderId}?t={join_token}`，token 由创建响应与**发起者**详情响应下发。鸿蒙：已实现（订单列表/创建/分享/加入入口/点单页/购买清单全链路；深链 host `dining` 已注册；version 冲突简化为失败全量刷新，差分重放未实现）。契约以 `WebDiningOrderController` / `WebDiningOrderItemController` 为源。Web：无用户端（管理后台不消费点单订单）。
- **易混淆点**：join_token 重置即旧二维码全部作废（仅发起者，仅 ONGOING）；加入三类失败各有明确业务提示（凭证无效/已结束/重复加入）；FINISHED 后拒绝一切选菜写入且不递增 version；version 只由选菜变更驱动，重置凭证不触碰 version；详情接口仅向发起者回传 join_token（参与者与轻量 `unchanged=true` 响应不下发，端上沿用首次全量值）。

### 10.12 同步通道与复用入口（DiningOrderSyncChannel / fromOrderId / 再来一单）

- **代码标识**：`DiningOrderSyncChannel`、`PollingDiningOrderSyncChannel`、`DiningSyncLifecycle`、`DiningCartSyncCoordinator`、`CreateDiningOrderFrom.fromOrderId`、`DiningRoute.OrderCreate(fromOrderId)`
- **定义**：共享购物车同步的传输层抽象——业务只依赖 `DiningOrderSyncChannel`「订阅订单变更」语义，首实现 `PollingDiningOrderSyncChannel` 在点单页可见时约 3s 带 `knownVersion` 轮询详情（unchanged 轻量），后续可换 SSE/WebSocket 而不动业务代码。写操作携带已知 `version`，服务端不匹配返回业务码 409，端上经 `DiningCartSyncCoordinator` 以「服务端真相 + 本地意图」差分光重放（他人变更永不覆盖），失败全量刷新回滚。历史订单「再来一单」= 创建时携带 `fromOrderId` 复用源单本单菜单（当前用户须为源单成员，复用菜谱仍须上架，后端整份复制、逐道保序）。
- **四端对应物**：
  - 后端：`dining/`（`WebDiningOrderController` 的 `knownVersion` 轮询协议 + version 乐观并发；`CreateDiningOrderFrom.fromOrderId` 复用解析在 `DiningOrderServiceImpl`）。
  - Android：`:feature:dining:impl`（`sync/DiningOrderSyncChannel` + `PollingDiningOrderSyncChannel` + `DiningSyncLifecycle`，点单页消费；订单列表卡「再来一单」→ `DiningRoute.OrderCreate(fromOrderId)` → 创建页复用模式，13 号票）。
  - 鸿蒙：已实现（3s knownVersion 轮询 + 再来一单 fromOrderId；扫码壳层扫一扫为鸿蒙后续项）。契约以后端 `knownVersion` 轮询协议与 `fromOrderId` 创建契约为准。
  - Web：无用户端（管理后台不消费点单订单）。
- **易混淆点**：version 只由选菜变更驱动（重置凭证/结束点单都不递增）；复用创建仍过上架把关，源单含已下架菜时整体拒绝；复用模式端上菜单只读回显（`toggleRecipe` 忽略），提交不传 recipeIds。

---

## 附录 A：四端枚举对照速查

| 概念 | 后端 | Android | 鸿蒙 | Web |
|---|---|---|---|---|
| 订单状态 | `OrderStatus`（-2~7） | `OrderStatus` | `OrderStatusEnum` | `dict.js ORDER_STATUS_TAG_TYPES` |
| 订单页签 | `"001"` 全部分组 | `ShowOrderStatus` | `ShowOrderStatusEnum` | 同后端下发 |
| 柜子状态 | `LockerStatusEnum`（0/1/2） | 随订单 VO | `LockerVo.status` | `LOCKER_STATUS_TAG_TYPES` |
| 支付状态 | `PaymentStatus`（0/1/2/3） | 随支付 VO | `PaymentType` / `PaymentMethod` | `PAY_STATUS_TAG_TYPES` |
| 支付方式 | `PayType`（1/2/3） | `PaymentType.kt` | `PaymentType.ets` | `RECHARGE_TYPE_OPTIONS` |
| 充值类型 | `RechargeType`（1/2） | `RechargeApi` | `RechargeType.ets` | `RECHARGE_TYPE_TEXT` |
| 优惠券状态 | `CouponStatus`（0/1/2） | `CouponStatus.kt` | `CouponStatus.ets` | `COUPON_STATUS_TEXT` |
| 短码内容类型 | `contentType`（1-8） | `ShortCodeRules.kt` | `feature/toolbox/api.ets` | `SHORT_CODE_CONTENT_TYPE_TEXT` |
| 私密文本状态 | `SecretState`（ACTIVE/BURNED/EXPIRED） | `ShortCodeRules.kt` | `ShortCodeRules.ets` | `SECRET_STATE_TEXT` |
| 物品标签状态 | `status`（0/1/2） | `AssetTagRules.kt` | `api.ets` | `ASSET_TAG_STATUS_TEXT` |
| 柜码别名 | `L{lockerId}` | `ToolboxLockerTagServiceImpl` | 无（Web 管理端打印） | `lockerTag.js` |
| 游戏分类 | `category`（puzzle/arcade/word/board/other） | `GameCategory` | 无 | `GAME_CATEGORY_OPTIONS` |
| 游戏来源 | `source_type`（1/2） | 消费 `url` | 无 | `GAME_SOURCE_TYPE_TEXT` |
| 占卜术数 | `method`（liuyao/meihua/qimen/liuren） | `DivinationApi` | `METHOD_NAMES` | `DIV_METHOD_OPTIONS` |
| 深链平台 | `platform`（1/2/3） | `DeeplinkRouteDict` | `DeeplinkRouteDict` | `DEEPLINK_PLATFORM_OPTIONS` |
| 站内信类型 | `NoticeType`（SYSTEM/ANNOUNCEMENT） | `NoticeVo.type` | `feature/notice` | `NOTICE_TYPE_OPTIONS` |
| 订阅状态 | `SubscriptionStatus`（active/paused/canceled/finished） | `SubscriptionEnums.kt` | `feature/subscription` | `SUBSCRIPTION_STATUS_OPTIONS` |
| 复习评分 | `ReviewRating`（1 Again / 2 Hard / 3 Good / 4 Easy） | `ReviewModels.kt` | `feature/review` | — |
| 游戏存档/成绩 | `gameKey` + `state` / `score` | `GameBridgeApi` | 无对应实现 | `views/game/GameData.vue` |

## 附录 B：术语与 ADR 索引

| 术语 | ADR |
|---|---|
| 统一响应信封、401 语义、URL 前缀 | ADR-0001 |
| 订单状态机、支付/充值流水、幂等键、优惠券 | ADR-0002 |
| 数据库结构以 `smart_wash.sql` 为准 | ADR-0003 |
| BASE_URL 构建期注入、MinIO 端点运行期下发 | ADR-0004 |
| 游戏库宿主契约与主题桥 | ADR-0005 |
| 观象台 BYOK 与 SSRF 边界 | ADR-0006 |
| JWT 黑名单与 `/auth/refresh` | ADR-0007 |
| 游戏数据平台（存档 / 排行榜 / 关卡热更新） | 待补 ADR |
| 站内信 / 积分 / 订阅 / 复习 | 待补 ADR |
| 操作审计 HMAC 链 / 分布式限流 / TraceId | 待补 ADR |

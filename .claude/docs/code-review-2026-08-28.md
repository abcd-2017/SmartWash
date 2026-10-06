# SmartWash 四端代码评审报告

- **评审日期**：2026-08-28
- **评审范围**：后端 `SmartWash/`、Android `SmartWash-Android/`、鸿蒙 `SmartWash_Harmony/`、Web `SmartWashWeb/`，以及根目录 `smart_wash.sql`
- **评审方法**：四端并行深度静态评审，所有问题均带 `文件:行号` 证据；鸿蒙端与 Android 端网络层做了逐接口交叉比对
- **问题统计**：后端 51 条 / Android 56 条 / Web 36 条 / 鸿蒙 51 条，另提取跨端共性问题 6 条

## 总体结论

四端架构骨架均高于同规模项目平均水准：后端锁与防注入意识好（`FOR UPDATE`、条件扣款、`#{}` 全覆盖）、Android Hilt 注入链与 Repository 完整、鸿蒙接口与 Android 100% 对齐、Web API 分层清晰无 XSS 高危点。

但存在三类必须优先处理的问题：

1. **资金/安全级硬伤（后端为主）**：订单取消与支付的 check-then-act 竞态可导致已扣款订单被取消；优惠券可并发重复使用/领取；充值支付绕过网关无幂等；JWT 密钥默认值入库；验证码可穷举且从未真正发送。
2. **三端环境与传输不对齐**：Android release BASE_URL 是占位符，鸿蒙/Web 硬编码公网 IP 明文 HTTP，Web 生产密钥泄露。
3. **「做了等于没做」类缺陷**：Android 全项目 31 处按压缩放反馈实际全部无效；鸿蒙 `@ComponentV2` 使用不存在的 `onDidUpdate` 导致轮询启停永不生效；Web 唯一测试文件断言与实现不符必挂。

---

## 第一章 后端（Spring Boot 3.4）

路径约定：`…` = `SmartWash/src/main/java/com/smartwash/`，SQL = 根目录 `smart_wash.sql`。

### 严重问题

1. **超时取消与支付竞态可导致"已扣款订单被取消"** — `task/OrderTimeoutManager.java:67-84` 无锁读取 status 后直接 `nextStatus` 更新，与 `…/service/impl/PaymentsServiceImpl.java:141` 的 `FOR UPDATE` 支付事务并发时，检查与更新之间支付可能已提交 → 改为 `UPDATE orders SET status='CANCELED' WHERE order_id=? AND status='0'` 条件更新并判断影响行数。
2. **用户取消订单同样是 check-then-act** — `…/service/impl/OrdersServiceImpl.java:222-238` `getById` 后无条件 `nextStatus`，与支付事务并发可取消已支付订单且不退款 → 同上用条件更新。
3. **优惠券可跨订单重复使用（并发）** — `…/service/impl/PaymentsServiceImpl.java:152-200` 优惠券校验与 `updateById(isUsed=true)` 之间无锁，用户并发支付两笔订单可复用同一张券 → 改为 `UPDATE user_coupon SET is_used=1 WHERE user_coupon_id=? AND is_used=0` 校验影响行数。
4. **优惠券领取无事务、无唯一约束、无库存上限** — `…/service/impl/UserCouponServiceImpl.java:55-79` 并发重复领取；`user_coupon` 表（smart_wash.sql:864-874）无 `(user_id,coupon_id)` 唯一索引；`coupon` 表（smart_wash.sql:45-59）无发放总量字段，无法限量防超发。
5. **充值接口无支付网关/幂等直接加余额** — `…/service/impl/RechargeRecordsServiceImpl.java:90-108` 直插记录 + `addUserBalance`，无第三方支付回调、验签、幂等键，重复请求即重复加钱 → 接入支付回调并对 recharge 记录做幂等。
6. **支付流程完全绕过支付网关** — `…/service/impl/PaymentsServiceImpl.java:176-184` 直接把支付记录写成 `SUCCESS` 并改订单状态，`PaymentGatewayService`（StubPaymentGatewayServiceImpl）无任何调用方 → 真实支付链路/回调验签缺失。
7. **管理员取消在途已支付订单不退款、不回滚优惠券** — `…/service/impl/OrdersServiceImpl.java:150-183` 允许 `PENDING_SHIPMENT→CANCELED`，只释放柜子且不清 `locker_id`、不退余额、不还券；`REFUNDED` 状态从未使用 → 补退款事务链路。
8. **JWT 密钥默认值提交在仓库** — `src/main/resources/application.yaml:41`（JWT_SECRET 默认 Base64 密钥）、:11（DB 密码默认 admin123）、:50-51（minioadmin）→ 生产密钥泄露于 git 历史，任何持源码者可伪造任意用户/管理员 token；应强制无默认值启动失败。
9. **验证码可被穷举 + 从未真正发送** — `…/controller/LoginController.java:115-132、162-179` 校验无尝试次数限制、错误不销毁、注册与重置密码共用同一 Redis key，6 位码 10 分钟内可暴力破解实现账号接管；且 `StubSmsServiceImpl`（:14-23）无任何调用方，验证码根本没发出 → 加验证失败计数、purpose 隔离、接真实 SMS。
10. **过滤器抛 RuntimeException 导致 500 而非 401** — `…/filter/JwtAuthenticationFilter.java:67、90、96` 在 `ExceptionTranslationFilter` 之前抛 `UserAuthenticationException`，`@RestControllerAdvice` 捕不到 → 设置 `response.setStatus(401)` 写 JSON 或改抛 `AuthenticationException` 交给 EntryPoint。
11. **Dashboard 统计 SQL 引用不存在的列** — `src/main/resources/mapper/DashboardMapper.xml:6、10、14` 的 `WHERE is_delete=0`，但 users/schools/orders 表均无 `is_delete` 列 → 运行时必报 SQL 错误被兜底 Handler 吞成"系统异常"。
12. **管理员密码种子数据为 MD5 弱哈希** — smart_wash.sql:38 admin 用户 `password_hash` 为 32 位 MD5，与 BCrypt 校验器不匹配（该账号无法登录）→ 种子数据统一 BCrypt。

### 中等问题

13. **可探测他人订单金额（轻度越权）** — `…/service/impl/UserCouponServiceImpl.java:82-88` `getCanUseCoupon` 不校验订单归属。
14. **评价不校验订单状态/完成态** — `…/service/impl/OrderReviewsServiceImpl.java:33-55` 待支付订单也能评价，且查重与插入无事务/唯一索引。
15. **绑定校园卡不查重** — `…/service/impl/UsersServiceImpl.java:182-187` 未检查 campus_card 已被他人绑定，DB 也无唯一索引（smart_wash.sql:881-894）。
16. **逻辑删除名存实亡** — 实体无 `@TableLogic`、表无 `is_delete` 列，`deleteOrders/removeByIds`（`…/service/impl/OrdersServiceImpl.java:59-65` 等）全为物理删除，支付/充值凭证可被管理员直接抹掉。
17. **JWT 无刷新机制** — `…/utils/JwtUtil.java:145-160` 的 `canRefresh/refreshToken` 无任何端点调用，7 天长效 token 无滑动续期。
18. **登录锁定可被滥用锁死任意账号** — `…/controller/LoginController.java:58-62、185-194` 按 username/phone 锁定，攻击者可恶意锁任意账号（DoS）→ 增加 IP 维度限制与渐进式延迟。
19. **优惠券缓存与领取状态不一致** — `…/service/impl/CouponServiceImpl.java:96` `@Cacheable("coupon")` 缓存了"已领取"标记，但领取后不清该缓存 → `@CacheEvict`。
20. **订单超时调度基于单机内存** — `…/task/OrderTimeoutManager.java:32` ConcurrentHashMap 存任务，多实例部署会重复/遗漏；`createOrder` 在事务内注册非事务资源（`…/service/impl/OrdersServiceImpl.java:95`），回滚后任务残留 → Redis ZSet 延迟队列或 DB 扫描兜底。
21. **取消订单后 locker_id 悬挂** — `OrdersServiceImpl.java:174-177` 只释放柜不清 `orders.locker_id`，而 `nextStatus` SQL（mapper/OrdersMapper.xml:15-21）会清 → 统一走同一 SQL。
22. **管理端可伪造资金流水** — `…/controller/background/PaymentsController.java:82-98` 手工新增/修改支付记录（任意 status/amount）无审计与限制。
23. **头像上传类型校验可绕过 + 桶公共读** — `…/controller/web/WebUsersController.java:103-135` 仅信 contentType；`…/service/impl/MinioStorageServiceImpl.java:134` bucket 公共读 → 校验魔数，改私有桶 + 预签名 URL。
24. **交易流水全量加载无分页** — `…/service/impl/UsersServiceImpl.java:210-254` 充值+支付记录全查进内存。
25. **订单摘要一次 10 条 SQL** — `…/service/impl/OrdersServiceImpl.java:241-270`（5 次 list + 5 次 count）；`getOrderItemCount`（:125-137）4 次 count 可合并 `GROUP BY status`。
26. **评价列表 N+1** — `…/service/impl/OrderReviewsServiceImpl.java:66-79` 循环内 `usersMapper.selectById` → `selectBatchIds` 批量。
27. **柜子汇总 N+1 + 全表加载** — `…/service/impl/LockersServiceImpl.java:121-141` `list()` 全表 + 循环 `getById(school)`。
28. **支付方式未做枚举校验** — `…/from/payment/PaymentOrderFrom.java:12` `paymentType` 任意字符串入库 → 白名单校验 PayType。
29. **管理员更新用户可造成手机号重复** — `…/controller/background/UsersController.java:63-76` 未校验重复，依赖唯一索引报 500。
30. **Swagger 与内网通配 CORS 无环境隔离** — `…/config/SecurityConfig.java:50`、`…/config/CorsConfig.java:11-17` + application.yaml:45 默认放行 `http://192.168.*`。
31. **无多环境 profile** — application.yaml 单文件，SMS/支付桩实现无条件开关 → 增加 application-{dev,prod}.yaml 并按 profile 装配桩。
32. **CSV 导出一次性 1 万条进内存且无转义** — `…/service/impl/ExportServiceImpl.java:27-30、40-49`。

### 轻微问题

33. **注入风格混用** — `PaymentsServiceImpl.java:48-57` `@Autowired` 字段注入 vs `OrdersServiceImpl` 构造注入 → 统一构造注入。
34. **业务逻辑泄漏到 controller** — `…/controller/background/PaymentsController.java:66-70` 手机号正则、`WebOrdersController.java:147-157` 内联二维码 → 下沉 service。
35. **魔法值** — `OrdersServiceImpl.java:247-267` 状态码 "001"/"0"/"1"/"3"/"6" 作 map key、`UsersServiceImpl.java:232` `.eq(status,"1")`、`CouponServiceImpl.java:100` `"active"`。
36. **死代码/误导字段** — `ReservationOrderFrom.java:12` 前端传入 totalPrice 未使用；`LoginController.java:66` `authenticate=null` 冗余；`Result.java:57` `failMsg` 返回原始类型。
37. **日志刷屏** — `…/service/impl/CustomUserDetailsService.java:36、46` 每请求 log.info"认证成功" → 降为 debug。
38. **依赖问题** — pom.xml `mysql-connector-j 9.7.0` 脱离 Boot 3.4 BOM、`hutool-all` 全量引入、fastjson2 兼作缓存序列化（建议统一 Jackson）、jjwt 聚合包引入 impl 全量。
39. **测试严重不足** — 仅 3 个测试类；`SmartWashApplicationTests.java:6` 未加 `@ActiveProfiles("test")`，`mvn test` 会连真实 MySQL/Redis。
40. **取件码可预测成分** — `OrdersServiceImpl.java:169、204` `userId:orderId:4位随机` → 纯随机并加频控。
41. **DB 细节** — smart_wash.sql:774 `pickup_code` 唯一索引建在可空列；:768 status 注释与枚举不一致；全库无外键约束；排序规则 utf16 非常规（建议 utf8mb4）。
42. **取件二维码接口无归属校验** — `WebOrdersController.java:146-158` 任意认证用户可对任意字符串生成二维码。

### 亮点

43. 订单行 `SELECT FOR UPDATE` 悲观锁防同订单重复支付 — `…/mapper/OrdersMapper.java:22-23` + `PaymentsServiceImpl.java:141`。
44. 扣款用条件更新防超扣 — `src/main/resources/mapper/UsersMapper.xml:9-13`（`balance >= amount` 才更新）。
45. 寄存柜分配 `FOR UPDATE` 防一柜多占 — `mapper/LockersMapper.xml:16-24`。
46. JWT 登出黑名单按 jti+剩余 TTL 入 Redis — `…/service/JwtBlacklistService.java` + `…/config/JwtLogoutHandler.java`。
47. 订单状态机白名单流转校验 — `…/service/impl/OrdersServiceImpl.java:140-148`。
48. 列表查询批量装配消除 N+1（Payments/Recharge/Users/AdminUsers 均 `selectBatchIds`）。
49. 全部 mapper XML 使用 `#{}` 预编译，未发现 `${}` SQL 注入点。
50. 下单价格以 DB 为准不信任前端（`OrdersServiceImpl.java:77-84`）、BCrypt 密码、登录失败锁定、手机号脱敏日志、SecureRandom 验证码。
51. V3 迁移系统性补齐索引/唯一约束/时间戳修复 — `src/main/resources/db/migration/V3__add_indexes_and_fixes.sql`。

---

## 第二章 Android（Jetpack Compose）

路径约定：`A` = `SmartWash-Android/app/src/main/java/com/smartwash`，`AM` = `SmartWash-Android/app/src/main`，`H` = `SmartWash_Harmony/entry/src/main/ets`。

### 严重问题

1. **release 包 BASE_URL 是占位符 `https://api.smartwash.example.com/`，debug 是内网 IP**，且鸿蒙端硬编码另一台公网 IP `http://8.148.70.81:9000`，三端环境完全不对齐 → 统一环境配置。`A` 同级 `app/build.gradle:33-38`、`H/network/Axios.ets:13`
2. **`usesCleartextTraffic="true"` 全局放行明文 HTTP** — `AM/AndroidManifest.xml:19` → 只在 debug 的 networkSecurityConfig 放行内网域名。
3. **Token 明文存 DataStore 且 backup_rules 为默认模板**（云备份会带上 token）→ Keystore 加密或 backup_rules 排除。`A/utils/SharePreferenceUtils.kt:20-23`、`AM/res/xml/backup_rules.xml:8`
4. **`pressScale()/pressAlpha()` 内部自建 `MutableInteractionSource` 且从未接入任何 clickable**，按下事件永远收不到 → 全项目 31 处调用按压缩放反馈全部无效，是死代码 → 接收外部 interactionSource 参数或用 `Modifier.Node`。`A/utils/PressFeedbackModifier.kt:16-29,34-44`
5. **大量 Toast + resetState 直接写在组合期 `when(state)` 分支里**（一次重组可能弹多次、状态在组合中回写）→ 全部迁到 `LaunchedEffect(state)`。`A/ui/page/payment/PaymentPage.kt:92-106`、`A/ui/page/payment/PaySuccessPage.kt:71-81`、`A/ui/page/detail/OrderDetailPage.kt:76-87`、`A/ui/page/index/IndexPage.kt:93-104`、`A/ui/page/register/RegisterPage.kt:104-119`、`A/ui/page/order/OrderPage.kt:102-105`
6. **ResponseInterceptor 对空 body（204/无响应体）`Gson().fromJson` 返回 null 后直接取 `.code` 会 NPE** → 先判空。`A/network/interceptor/ResponseInterceptor.kt:36-39`
7. **`peekBody(Long.MAX_VALUE)` 把整个响应读成字符串做两次解析**，大响应内存翻倍 → 只 peek 有限字节或只读 code 字段。`A/network/interceptor/ResponseInterceptor.kt:36`
8. **Room 未配置任何 Migration/fallbackToDestructiveMigration，version=1 且 exportSchema=false**；缓存写入先 `deleteAll()` 再 `insertAll()`、无 `@Transaction`，中途失败缓存被清空 → 补 migration + `@Transaction`。`A/database/AppDatabase.kt:12-26`、`A/repository/LaundryRepository.kt:26-28`、`A/repository/CouponRepository.kt:20-22`
9. **拦截器依赖 `App.globalRequestBefore/AfterCallback` 两个静态 lateinit**，请求发生在 setContent 之前即崩溃 → 改 Hilt 注入的 Navigator/EventFlow。`A/App.kt:11-12`、`A/network/interceptor/RequestInterceptor.kt:26`、`A/network/interceptor/ResponseInterceptor.kt:27,43`
10. **主线程 runBlocking 读写 DataStore**，ANR 风险 → 全部改 suspend/flow。`A/ui/activity/MainActivity.kt:83`、`A/ui/page/login/LoginPage.kt:88`、`A/ui/page/setting/SettingPage.kt:182`、`A/utils/SharePreferenceUtils.kt:50-56`

### 中等问题

11. OrderViewModel 的 `hasMoreMap` 是私有普通 Map 未 asStateFlow，两次独立发射间重组可能读到陈旧 hasMore → 并入单一 UiState。`A/ui/page/order/OrderViewModel.kt:26-27,94-97`
12. 加载更多的 `LaunchedEffect` 嵌在 item 内容里且靠 last() 条件触发，易漏/重复触发 → `derivedStateOf`+snapshotFlow 或统一 Paging 3。`A/ui/page/order/OrderPage.kt:170-174`
13. LoginViewModel 登录响应 data 为 null 时照样存空 token 并置 Success → 闪屏循环 → 按失败处理。`A/ui/page/login/LoginViewModel.kt:27-34`
14. LaundryViewModel/CouponViewModel 直接注入 API 绕过 repository 层（7 个 Repository 有 2 个被架空）→ 补 Repository 方法。`A/ui/page/laundry/LaundryViewModel.kt:26-28`、`A/ui/page/coupon/CouponViewModel.kt:25`
15. SchoolRepository `@Singleton` 里 `private var allSchools` 可变缓存，多协程并发读写无同步 → StateFlow/Mutex。`A/repository/SchoolRepository.kt:14`
16. RegisterPage 发送验证码先调 `getCaptcha(phone)` 再校验手机号；倒计时结束 `countDown = 60` 写反 → 调整顺序与赋值。`A/ui/page/register/RegisterPage.kt:283-300`
17. PickupDeliveryPage 在 `remember(pickupCode){}` 里同步生成 512×512 二维码（逐像素双循环），主线程组合期卡顿 → 移到 Dispatchers.Default。`A/ui/page/pickup/PickupDeliveryPage.kt:88-90`、`A/utils/BitmapUtil.kt:13-24`
18. RequestInterceptor 每个请求都 `runBlocking` 读一次 DataStore → 内存缓存 token。`A/network/interceptor/RequestInterceptor.kt:22`
19. 401 回调 `navigate(Login)` 无 `launchSingleTop`，连发 401 堆叠多个 Login；且回调重复清 token → 加去重。`A/ui/activity/MainActivity.kt:82-95`
20. `catch (e: Exception)` 把 CancellationException 当网络错误吞掉 → 先 rethrow。`A/ui/page/order/OrderViewModel.kt:71,98,117`、`A/ui/page/recharge/RechargeViewModel.kt:30`
21. PaymentPage 的 `LaunchedEffect(navController.currentBackStackEntry)` 语义脆弱，backstack 任何变化都重复请求 → 用一次性事件。`A/ui/page/payment/PaymentPage.kt:89`
22. OrderViewModel 手写 Map 分页与已有 Paging 3 双轨并存 → 统一。`A/ui/page/order/OrderViewModel.kt:43-104` vs `A/paging/PagingUtils.kt:17-28`
23. PagingSource `nextKey = if (isEmpty) null else page+1`：整页恰好 10 条时多打一次空页 → 用 `size < pageSize` 判断。`A/paging/OrderPagingSource.kt:20-21`
24. IndexPage/PaymentPage 的 `items(list.size)` 无 key → `key = { it.orderId }`。`A/ui/page/index/IndexPage.kt:198`、`A/ui/page/payment/PaymentPage.kt:327`
25. 鸿蒙端 401 只跳转登录页不清 token，与 Android 不一致；且 push 不清栈会堆叠 Login → 对齐。`H/network/Axios.ets:36-42,64-67`
26. 鸿蒙超时 100s vs Android 10s/30s → 统一 15~30s。`H/network/Axios.ets:14`
27. 鸿蒙请求拦截器只按 URL 前缀 `/web` 判断是否带 token，新增公开 `/web` 接口会被误拦 → 白名单化。`H/network/Axios.ets:24-34`
28. accompanist-permissions 已维护收尾，且申请的 READ/WRITE_EXTERNAL_STORAGE 在 targetSdk 35 基本无效 → 迁 ActivityX Permissions。`A/utils/PermissionsUtil.kt:25-31`、`gradle/libs.versions.toml:2`
29. RequestState.Error 要求 UI 层自备 Context 解析文案、39 处 Toast 各写一遍 → 引入一次性 UiEvent（Channel/SharedFlow）。`A/utils/RequestState.kt:10-17`
30. kapt + ksp 混用（Hilt 走 kapt）→ Hilt 2.51 已支持 KSP，统一迁移。`app/build.gradle:5-8`
31. Repository 大量 `?: emptyList()` 静默吞错，UI 无法区分"无数据"和"失败" → 失败时抛异常。`A/repository/OrderRepository.kt:33-35`

### 轻微问题

32. `OrderStatus.kt`/`HttpStatusCode.kt` 依赖 R.string 做枚举属性，非 UI 层引用时耦合资源；"001" 魔法值建议具名常量。`A/utils/OrderStatus.kt:21`
33. HapticUtils LIGHT 与 SELECTION 都映射 CLOCK_TICK 无区分度；建议加 `@RequiresApi(30)` 注释说明隐式前提。`A/utils/HapticUtils.kt:28-33`
34. `isReduceMotionEnabled` 每次调用读 Settings.Global，非状态驱动 → 包成 ProduceState 或监听 ContentObserver。`A/utils/AnimationUtils.kt:14-20`
35. `springIfAnimated` 返回 null 的约定无调用方，死代码。`A/utils/AnimationUtils.kt:41-43`
36. IndexPage 问候语每次重组 `Calendar.getInstance()` → remember。`A/ui/page/index/IndexPage.kt:249`
37. ResponseInterceptor 每次 `Gson()` 新建实例 → 单例常量。`A/network/interceptor/ResponseInterceptor.kt:4,37`
38. OkHttpClient 10MB HTTP 缓存但后端无缓存头支持，基本无效 → 去掉。`A/network/RetrofitClient.kt:40-46`
39. "Room 暂未启用"说法不成立：Laundry/School/Coupon 三个 DAO 已在 Repository + ViewModel 中使用（还有 VM 直接注入 DAO）→ DAO 收回 Repository 内部。`A/ui/page/laundry/LaundryViewModel.kt:26`、`A/ui/page/service/ServiceViewModel.kt:22`
40. proguard 缺 `-keepattributes SourceFile,LineNumberTable` 导致 release 崩溃栈无法定位。`app/proguard-rules.pro:17,23-50`
41. gradle.properties 中文注释乱码；`enableResourceShrinking` 位置重复配置易误解。`gradle.properties:26`、`app/build.gradle:41`
42. 测试仅模板，网络层/倒计时/状态映射零覆盖 → 优先给 ResponseInterceptor/ParamValidUtils 补单测。`A` 同级 `app/src/test/.../ExampleUnitTest.kt`
43. Groovy DSL 与 root kts 风格不一；settings.gradle maven 顺序冗余。`settings.gradle:14-30`
44. RequestState.Success 无数据负载，8 个 VM 样板重复开 `StateFlow<T?>` → Success<T> 泛型化。`A/utils/RequestState.kt:6-18`
45. MainActivity 16 个路由集中在 300 行 NavHost、动画重复 6 段 → 抽 transitionSpec 扩展。`A/ui/activity/MainActivity.kt:199-266`
46. PageConstant 字符串路由 → 迁移 Navigation 2.8 类型安全导航。`A/ui/page/PageConstant.kt:6-24`
47. OrderPage 取消订单弹窗复用 `confirmPayShow` 变量名 → 改名。`A/ui/page/order/OrderPage.kt:92-93,202-213`
48. 鸿蒙首页有 5 秒轮询（有启停控制），Android 首页无轮询 → 若产品要求实时性需补受生命周期控制的轮询。`H/view/IndexPage.ets:36-53` vs `A/ui/page/index/IndexPage.kt:90-92`

### 亮点

1. `@RequireAuthorization` 运行时注解 + `retrofit2.Invocation` tag 按 API 方法注入 token，机制轻巧、零反射成本。`A/network/interceptor/RequestInterceptor.kt:17-34`
2. ResponseInterceptor 将五类错误统一转译为带 @StringRes 的 `NetworkException`，且正确处理"HTTP 401 先于通用失败判断"的顺序。`A/network/interceptor/ResponseInterceptor.kt:23-63`
3. Repository 层真实成型：7 个 @Singleton Repository + Hilt 注入链完整，Laundry/School/Coupon 实现"内存 → Room → 网络"缓存降级。`A/repository/SchoolRepository.kt:18-45`
4. Paging 3 封装 `pagingFlow`（debounce 300 + distinctUntilChanged + flatMapLatest + cachedIn）标准漂亮。`A/paging/PagingUtils.kt:17-28`
5. RequestState sealed class 携带 @StringRes 错误资源，i18n 友好。`A/utils/RequestState.kt:6-18`
6. 主题双轨完整：MaterialTheme colorScheme + 自定义 AppColorScheme CompositionLocal，明暗齐备。`A/ui/theme/Theme.kt:82-99`
7. 无障碍与细节意识好：全局尊重"减弱动态效果"、底部导航 restoreState、EdgeToEdge、触感分层。
8. 工程基建齐备：libs.versions.toml 版本目录、Compose BOM、Room 走 KSP、release 混淆 + 资源收缩。

---

## 第三章 Web 管理后台（Vue 3）

### 严重问题

1. **高德地图 securityJsCode 明文硬编码并提交入库** — `SmartWashWeb/index.html:10` → 走后端代理签名或环境注入并轮换该密钥。
2. **Token 存 localStorage 且角色硬编码** — `src/views/LoginPage.vue:82-83` 登录后无条件 `setItem("role","admin")` → 以后端返回角色写入并由 Pinia 管理。
3. **权限控制仅在前端且只判一个魔法字符串** — `src/router/index.js:159`（`role !== 'admin'` 即踢出，localStorage 可篡改），按钮级操作无任何权限判断（`UserList.vue:89-92`）→ 后端接口鉴权 + 前端双重校验。
4. **生产 API 硬编码明文 HTTP + 固定 IP** — `.env.production:1`（`http://8.148.70.81:9000`）→ HTTPS 域名 + Nginx 反代。
5. **VITE_AMAP_KEY 未在任何 env 文件定义** — `RegionCascader.vue:66` 与 `AmapPicker.vue:152` 依赖该变量但 env 均未配置 → 学校管理的地图选点/行政区划在构建产物中必然失效。
6. **测试已失效且无执行入口** — `package.json` 无 `test` 脚本；`src/__tests__/http.test.js:8` 断言 baseURL `127.0.0.1:8080`（现为 `/api`）、`:45-47` 断言返回 `result.code/data`（拦截器实际返回 `res.data`）→ 跑必挂，需修断言 + 补 script。

### 中等问题

7. **Pinia 完全未使用** — `main.js:29` 注册了 Pinia 但 `src/stores/` 为空目录，登录态散落 7 处读写 → 建 `useAuthStore`。
8. **路由无懒加载** — `src/router/index.js:5-18` 全部静态 import 13 个页面 → `() => import(...)`。
9. **Element Plus 全量引入 + 全量图标注册** — `src/main.js:13-14、18-19` → `unplugin-vue-components` 按需引入。
10. **401 处理用 `window.location.reload()`** — `src/utils/http.js:34-38、46-50`，两处重复逻辑、无并发去重 → 统一 `router.push('/login')`。
11. **非 401 的 HTTP 错误无统一提示** — `src/utils/http.js:52`（ElMessage.error 被注释）；`UserList.vue:252-255` 无 try/catch 产生未处理 rejection。
12. **11 个列表页复制粘贴同一套 CRUD 逻辑** — `handleSearch/resetSearch/handlePageChange/fetchData` 在 `UserList.vue:277-296`、`OrderList.vue:300-322`、`RechargeList.vue:195-215` 等全部重复 → 抽 `useTableList()` composable。
13. **`formatTime` 在 11 个页面重复定义** — 如 `UserList.vue:361-363` → 放 `src/utils/format.js`。
14. **时间范围 computed 在 4 个页面逐字重复** — `OrderList.vue:228-234` 等 → 抽 `useTimeRange()`。
15. **下拉选项用 `size:1000` 拉全量** — `UserList.vue:253`、`OrderList.vue:264,274` 等 → 后端下拉专用接口或全局缓存。
16. **枚举颜色映射硬编码 switch** — `OrderList.vue:371-396` 等 4 处 → 建 `src/constants/` 字典模块。
17. **高德安全码与 key 配置割裂** — `index.html:8-12` 内联写死 securityJsCode 而 key 走 env → 统一收敛。
18. **无 ESLint/Prettier/editorconfig** — 缩进/分号风格混乱 → 接入 eslint + prettier + lint-staged。
19. **纯 JS 无类型约束** — `http.js:42` 拦截器无类型契约 → 至少加 JSDoc 或迁移 TS。

### 轻微问题

20. 超时魔法值 5000ms 偏短无注释 — `src/utils/http.js:8`。
21. console.error 残留 — `Navbar.vue:51`、`Home.vue:91`。
22. 死代码：注释掉的删除功能 — `RechargeList.vue:97-103、218-233`；`:121` 未使用导入。
23. `error !== 'cancel'` 字符串比较判断取消 — `UserList.vue:355` 等 5+ 处 → 统一 useConfirm。
24. 无 404 页面 — `router/index.js:142-144` 通配符直接 redirect。
25. 分页固定 size=10 无每页条数选择 — 11 处。
26. 菜单图标映射硬编码 route name — `Sidebar.vue:42-56` → 收敛进路由 meta。
27. 页面标题与 lang 未配置 — `index.html:2,6`。
28. 仓库残留杂物 — `.DS_Store`、`dist/`、`docs/superpowers/`、`.superpowers/`；Web 子目录自身无 `.gitignore`。
29. README 为脚手架默认模板，无启动/部署/环境变量说明。
30. 登录无验证码/失败次数限制（需后端配合），前端可先加错误次数提示。

### 亮点

31. API 层按领域拆分清晰 — `src/api/` 12 个模块统一 `request({url, method, params})` 模式。
32. 无 v-html、无 XSS 注入点。
33. 拦截器统一解包响应信封 — `http.js:39-42`；且有拦截器单测意识（vitest + happy-dom 已配好）。
34. 表单校验规范 — `UserList.vue:228-243` 手机号正则、密码规则完整。
35. 路由 meta 驱动菜单与面包屑，单一数据源；图标用 `markRaw`。
36. 多轮空安全修复：可选链在列表渲染中普遍使用。

---

## 第四章 鸿蒙端（ArkTS / HarmonyOS NEXT）

路径约定：`R` = `SmartWash_Harmony/entry/src/main/ets`。

### 严重问题

1. **baseURL 硬编码公网明文 HTTP 且与 Android 不对齐** — `R/network/Axios.ets:17` `http://8.148.70.81:9000`（Android 端为 BuildConfig 分环境）→ HTTPS + 环境化配置。
2. **业务码 401 未清除本地 token/登录态** — `R/network/Axios.ets:45-49` 只 toast+push Login（Android 会清 token）→ 旧 token 残留，重启循环失效。
3. **401/未登录时 `pushPathByName("Login")` 被并发请求重复压栈** — `R/network/Axios.ets:32,49,66` → `replacePathByName` + 防抖去重。
4. **`@ComponentV2` 中不存在 `onDidUpdate` 生命周期，Tab 轮询启停逻辑永不执行** — `R/view/IndexPage.ets:32-41`（V2 仅有 aboutToAppear/onReuse/aboutToRecycle/aboutToDisappear），5s 轮询切 Tab 不停、异常时每 5s 弹 toast → 改 `@Monitor('isActive')`。
5. **验证码倒计时定时器泄漏** — `R/pages/Register.ets:27-37` setInterval 后无 aboutToDisappear 清理。
6. **Laundry→Payment 传参类型错位** — `R/pages/Laundry.ets:216` 传字符串，`Payment.ets:30` `as number` 强转，最终 orderId 以字符串提交 → 统一 number。
7. **启动闪登录页且无兜底** — `R/pages/Index.ets:14-30` 初值直接渲染 Login，已有 token 用户先闪登录页 → 增加 loading 态。
8. **订单列表刷新回调失效** — `R/pages/OrderList.ets:165-177` onPop 仅在带 result 的 pop 时触发，而支付/寄件均 `pop()` 无 result → 用 NavDestination.onShown。

### 中等问题

9. 超时 100s 远超 Android（10s/30s）— `R/network/Axios.ets:18`。
10. 响应拦截器空死分支 `else if (response.status != Ok) {}`；`!=`/`==` 宽松比较应收紧 — `R/network/Axios.ets:50-52,28,45`。
11. 拦截器声明返回 `AxiosResponse` 实际 return body，类型谎言 + any 逃逸 — `R/network/Axios.ets:42-54`。
12. 登录/注册/支付按钮无 loading、无防重，连点重复提交；已有支持 loading 的 AppButton 未复用 — `R/pages/Login.ets:145-172`、`R/pages/Register.ets:244-262`、`R/pages/Payment.ets:382-410`。
13. 全局 20+ 处 `showToast({message: res.message})`，undefined 时弹 "undefined" → 封装统一 toast 工具。如 `R/pages/Login.ets:170`。
14. PickUp onShown 不重置 page，返回后可能追加重复数据 — `R/pages/PickUp.ets:225-227`。
15. 优惠券 threshold==0 文案逻辑错误且三处重复实现 — `R/pages/Payment.ets:149-151`、`Coupon.ets:220/303/373` → 抽公共 thresholdText。
16. 前端判余额才允许支付、金额未经后端二次校验易被绕过/双击重复扣款 — `R/pages/Payment.ets:383`（需后端配合幂等）。
17. 支付券 Radio 无法取消选择（缺"不使用"项）— `R/pages/Payment.ets:166-181`。
18. Recharge 品牌色硬编码、brandColor 形参未用 — `R/pages/Recharge.ets:93,104-105`。
19. 充值金额校验不完整：check() 用 parseInt 截断小数、无 >0 校验 — `R/pages/Recharge.ets:262,148`。
20. 学校搜索每字符一次请求无防抖 — `R/pages/AddSchool.ets:73-76` → 300ms debounce。
21. NavDestination 页面误标 `@Entry`（仅 Index 需要）— `R/pages/AddSchool.ets:15-16`、`Login.ets:15`、`Register.ets:15`、`Home.ets:11`。
22. 用 AppStorage 布尔 flag+@Watch 模拟事件总线，时序脆弱 — `R/pages/Index.ets:16-48` → 换 emitter 或 @Monitor。
23. 退出登录未重置内存登录态 — `R/pages/Setting.ets:133-135`。
24. VO 为 class 但 JSON 直接赋值无映射无校验，全局依赖 `xx?.xx?.` 链 — `R/network/vo/*.ets` → interface + 转换函数。
25. release 也输出完整响应体日志（含手机号/余额）— `R/network/Axios.ets:44`、`Register.ets:254` → 按构建模式条件输出并脱敏。
26. Logger format 固定两个占位符却传 args 数组，多参被截断 — `R/utils/Logger.ets:13-27`。
27. 深色模式必然错乱：全量硬编码色值 + ColorMode.NOT_SET — `R/constant/DesignSystem.ets`、`R/entryability/EntryAbility.ets:10` → 资源色或锁定 LIGHT。
28. Preferences 依赖 `preferencesStorage!` 非空断言，initPreferences 未 await 有时序隐患；token 明文存 → Asset Store/HUKS — `R/utils/StorageUtil.ets:15,19`、`EntryAbility.ets:21`。
29. "联系客服/常见问题" onClick 为空 TODO、设置页大量占位 — `R/view/UserInfo.ets:386-392` → 隐藏未实现入口。
30. 充值类型魔法字符串 "1"/"2" 散落 — `R/pages/RechargeRecord.ets:62-67`、`Recharge.ets:277`。

### 轻微问题

31. 6 处 `getParamByName(name)[0] as X` 强转，参数缺失即崩溃 — `R/pages/OrderDetail.ets:20` 等 6 处 → 封装安全取参。
32. 魔法默认值 "-10"/"001" 兜底状态 — `R/pages/OrderDetail.ets:74,218`。
33. 订单"完成时间"硬编码 "-"（updatedAt 未接）— `R/pages/OrderDetail.ets:219`。
34. 柜号空时显示 "-1柜" — `R/pages/PaymentSuccess.ets:73`。
35. 死代码文件：`R/view/CouponCard.ets`、`UserCouponCard.ets`、`OrderStatusCard.ets`（及仅被其引用的 `R/utils/TimeUtil.ets`）无 import 引用 → 删除。
36. RowSplit/ColumnSplit 被当 spacer 用 — `R/pages/Laundry.ets:115,154,163,176` 等 → 用 Blank。
37. TextInput 同时双向绑定又 onChange 手写赋值冗余 — `R/pages/Register.ets:127,155,205`。
38. padding/margin 传字符串 "20"/"22" — `R/pages/AddSchool.ets:72,115`。
39. `TextInput.showError` 已废弃 — `R/pages/BingCampusCardAlert.ets:49`。
40. `this.coupons = []` 再立即赋 res.data 冗余 — `R/pages/Coupon.ets:33-34,44-45`。
41. getLaundryItems 失败分支静默无提示 — `R/view/Service.ets:26-29`。
42. 空的 `onUserInfoChange` 残留 — `R/view/IndexPage.ets:439-440`。
43. 学号校验语义靠 `!data` 反推（true=可注册），无注释易被误修 — `R/pages/AddSchool.ets:163`（与 Android `UserRepository.kt:44-45` 对齐确认）。
44. INTERNET 权限 reason 引用 $string:app_name 无实际描述 — `module.json5:52-57`。
45. 品牌信息不一致：vendor "example"、"清氧洗衣" vs "智能洗衣" — `AppScope/app.json5:4`、`Setting.ets:88`、`Login.ets:56`。

### 工程化

46. release 混淆关闭（obfuscation enable:false，规则文件空挂）— `entry/build-profile.json5:9-18` → 启用并补 keep 规则。
47. product 引用 `"signingConfig": "default"` 但 signingConfigs 为空，CI 无法出包（好在签名材料未入库）— `build-profile.json5:3,7` → 环境变量注入签名。
48. 依赖错位：@ohos/axios 声明在根 oh-package.json5，entry 模块 dependencies 为空；测试仅 hypium 模板无真实用例 → 依赖下沉 + 补单测。

### 亮点

49. **Navigation + 系统路由表实践规范**：main_pages 仅保留 Index 入口，16 个页面统一走 router_map.json + `@Builder` 导出，全局 NavPathStack 单例使用一致。
50. **网络层与 Android 端接口 100% 对齐**：7 个 api 文件的路径、方法、参数逐条比对全部一致；`/web` 前缀注入 Bearer 思路等价于 Android 的 @RequireAuthorization。
51. 权限最小化（仅 INTERNET）、签名材料零入库、code-linter.json5 已配置 @performance/@security 规则集、敏感操作均有二次确认弹窗。

---

## 第五章 跨端共性问题

| # | 问题 | 涉及端 | 统一修复方向 |
|---|------|--------|-------------|
| 1 | 后端地址三端不对齐：Android release 占位符 / debug 内网 IP，鸿蒙+Web 硬编码 `http://8.148.70.81:9000` 明文 HTTP | Android、鸿蒙、Web | 建立统一环境矩阵（dev 内网 / prod HTTPS 域名），各端按构建模式注入 |
| 2 | 401 处理策略不一致：Android 清 token+回调跳登录；鸿蒙只跳转不清 token 且重复压栈；Web reload 页面 | 三端 | 统一语义：清 token → 清内存登录态 → replace 到登录页，防抖去重 |
| 3 | 网络超时三端不一：Android 10s/30s、鸿蒙 100s、Web 5s | 三端 | 统一 connect 10s / read 30s 基准 |
| 4 | 资金操作前端校验形同虚设：鸿蒙/Web 前端判余额即允许支付，金额依赖后端二次校验但后端支付链路本身绕过网关无幂等 | 全部 | 后端为唯一事实源：幂等键 + 条件更新 + 回调验签（对应后端 P0） |
| 5 | 测试近乎为零：后端 3 个类且连真实 MySQL/Redis、Android 仅模板、鸿蒙仅 hypium 模板、Web 唯一测试断言失效 | 全部 | 各端先补网络层/纯逻辑单测，后端补 test profile |
| 6 | 密钥/敏感信息入库：后端 JWT_SECRET/DB 密码、Web 高德 securityJsCode | 后端、Web | 全部轮换 + 外置到环境变量/配置中心，git 历史视已泄露 |

## 第六章 优先级路线图

**P0 安全/资金（立即，1-2 天）**
- 后端 #1-#9：订单取消/超时改条件更新、优惠券核销/领取原子化 + 唯一索引、充值支付接网关幂等、密钥外置轮换、验证码防护、401 语义修复
- 后端 #11 + Web #1/#5：Dashboard SQL 修列、高德密钥轮换并环境注入（地图功能当前必失效）
- 三端环境矩阵对齐（跨端 #1）+ Web #2/#3 角色后端化

**P1 功能正确性（1 周内）**
- Android #4 pressScale 全量修复、#5 组合期副作用迁移、#10 runBlocking 清理
- 鸿蒙 #2/#3/#4/#5/#6：401 清 token、防重复压栈、@Monitor 替代 onDidUpdate、定时器清理、传参类型
- Web #6 修复测试断言 + 补 test script；#7 建 useAuthStore

**P2 性能/体验（2 周内）**
- 后端 #24-#27：流水分页、订单摘要合并 SQL、N+1 批量化
- Web #8/#9/#12-#16：路由懒加载、按需引入、useTableList/useTimeRange/formatTime 抽取、下拉接口化
- Android #22/#23/#24：分页统一 Paging 3、nextKey 修正、LazyColumn key

**P3 工程化（持续）**
- 三端测试补齐（后端 test profile、Android 拦截器单测、Web vitest script、鸿蒙核心逻辑）
- Web ESLint/Prettier、后端多环境 profile + 依赖收敛、鸿蒙 release 混淆 + 依赖下沉
- Android kapt→KSP 统一、proguard 行号保留

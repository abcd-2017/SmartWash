# AGENTS.md — SmartWash Android 项目指南

> 智慧校园洗衣服务 App，面向学生用户提供在线预约洗衣、寄存柜投递、订单追踪、优惠券、充值支付等功能。

---

## 一、技术栈

| 维度 | 技术选型 |
|------|----------|
| 语言 | Kotlin，JVM Target 17 |
| UI 框架 | Jetpack Compose + Material 3 |
| 架构模式 | MVVM（Page + ViewModel + Repository） |
| 依赖注入 | Hilt（KAPT 注解处理） |
| 网络 | Retrofit + Gson + OkHttp |
| 本地存储 | DataStore Preferences（主用）、Room（缓存层） |
| 分页 | Paging 3 |
| 图片加载 | Coil Compose |
| 二维码 | ZXing |
| 权限 | Accompanist Permissions |
| 导航 | Compose Navigation |
| 编译 | compileSdk 35 / minSdk 30 / targetSdk 35 |
| 注解处理 | KAPT（Hilt）、KSP（Room） |

---

## 二、目录结构

```
app/src/main/java/com/smartwash/
├── App.kt                          # Application 类（@HiltAndroidApp），全局回调
├── database/                        # Room 数据库（缓存层）
│   ├── AppDatabase.kt              # 数据库定义（v1，3 张表）
│   ├── dao/                        # DAO 接口
│   │   ├── CouponVoDao.kt
│   │   ├── LaundryItemDao.kt
│   │   └── SchoolNameDao.kt
│   └── entity/                     # Room Entity（含 toVo()/fromVo() 转换）
│       ├── CouponVoEntity.kt
│       ├── LaundryItemEntity.kt
│       └── SchoolNameEntity.kt
├── network/                         # 网络层
│   ├── RetrofitClient.kt           # Hilt @Module，提供 Retrofit/OkHttp/API/Room 单例
│   ├── annotation/
│   │   └── RequireAuthorization.kt # 鉴权注解
│   ├── api/                        # Retrofit 接口定义
│   │   ├── CouponApi.kt           # 5 个端点
│   │   ├── LaundryItemsApi.kt     # 1 个端点
│   │   ├── OrderApi.kt            # 10 个端点
│   │   ├── PaymentApi.kt          # 1 个端点
│   │   ├── RechargeApi.kt         # 2 个端点
│   │   ├── SchoolApi.kt           # 1 个端点
│   │   └── UserApi.kt             # 10 个端点
│   ├── entity/                     # 请求体对象
│   │   ├── ApiResult.kt           # 统一响应包装 {code, message, data}
│   │   ├── PageData.kt            # 分页响应 {records, total, size, current}
│   │   ├── OrderPayment.kt
│   │   ├── order/                  # 订单相关请求体
│   │   ├── recharge/              # 充值相关请求体
│   │   └── user/                  # 用户相关请求体
│   ├── exception/
│   │   └── Exceptions.kt          # NetworkException（含 @StringRes 错误文案）
│   ├── interceptor/
│   │   ├── RequestInterceptor.kt  # 自动注入 Bearer Token
│   │   └── ResponseInterceptor.kt # 401 处理、错误映射
│   └── vo/                         # 服务端返回的值对象
│       ├── coupon/                 # CouponVo, UserCouponVo, AllCouponsVo
│       ├── laundry/                # LaundryItem
│       ├── locker/                 # LockerVo
│       ├── order/                  # OrderInfo, OrderVo, OrderGroupVo, OrderItemCountVo
│       ├── recharge/               # RechargeRecordVo
│       ├── school/                 # SchoolName, SchoolVo
│       └── user/                   # UserInfoVo
├── paging/                          # Paging 3 分页实现
│   ├── OrderPagingSource.kt       # 订单列表分页
│   ├── RechargeRecordPagingSource.kt # 充值记录分页
│   ├── UserCouponPagingSource.kt  # 用户优惠券分页
│   └── PagingUtils.kt             # pagingFlow() 扩展（300ms 防抖）
├── repository/                      # 数据仓库层（缓存优先策略）
│   ├── CouponRepository.kt
│   ├── LaundryRepository.kt
│   ├── OrderRepository.kt
│   ├── PaymentRepository.kt
│   ├── RechargeRepository.kt
│   ├── SchoolRepository.kt        # 三级缓存：内存 → 数据库 → 网络
│   └── UserRepository.kt
├── ui/
│   ├── activity/
│   │   └── MainActivity.kt        # 单 Activity，NavHost 承载所有页面
│   ├── common/                     # 共通 UI 组件
│   │   ├── AppComponents.kt       # PageHeader, AppCard, AppButton, AppTabBar, SettingRow
│   │   ├── InfoRow.kt             # 信息行组件
│   │   ├── InfoSection.kt         # 信息分组卡片
│   │   ├── PasswordInput.kt       # 密码输入框
│   │   └── PhoneNumberInput.kt    # 手机号输入框
│   ├── page/                       # 页面（每个功能一个目录）
│   │   ├── PageConstant.kt        # 路由常量密封类（16 个路由 + 3 个底部 Tab）
│   │   ├── coupon/                # 优惠券（三 Tab：可领取/已领取/历史）
│   │   ├── detail/                # 订单详情
│   │   ├── home/                  # 主页框架（底部导航 + 嵌套 NavHost）
│   │   ├── index/                 # 首页 Tab（余额卡/服务网格/进行中订单）
│   │   ├── laundry/               # 洗衣预约（寄存柜 + 洗衣包选择）
│   │   ├── login/                 # 登录（毛玻璃设计）
│   │   ├── order/                 # 订单列表（五 Tab 分页）
│   │   ├── payment/               # 支付 + 支付成功页
│   │   ├── pickup/                # 取件列表 + 取件/寄件详情（QR 码）
│   │   ├── recharge/              # 充值 + 充值记录
│   │   ├── register/              # 注册（毛玻璃设计）
│   │   ├── service/               # 服务目录
│   │   ├── setting/               # 设置
│   │   ├── update_userinfo/       # 学校信息补全
│   │   └── userinfo/              # 个人中心 Tab
│   └── theme/                      # 设计系统
│       ├── AppDesign.kt           # 设计 Token + 可复用组件
│       ├── Color.kt               # 颜色定义
│       ├── Theme.kt               # 主题配置
│       └── Type.kt                # 字体排版
└── utils/                           # 工具类
    ├── AppConstant.kt             # 应用常量（APP_NAME, TOKEN key 等）
    ├── BitmapUtil.kt              # ZXing 二维码生成
    ├── CouponStatus.kt            # 优惠券状态枚举
    ├── HttpStatusCode.kt          # HTTP 状态码枚举
    ├── OrderStatus.kt             # 订单状态枚举（10 种）+ Tab 显示状态（5 种）
    ├── ParamValidUtils.kt         # 手机号校验
    ├── PaymentType.kt             # 支付方式枚举
    ├── PermissionsUtil.kt         # 权限请求工具
    ├── PickupDeliveryType.kt      # 取件/寄件类型枚举
    ├── RequestState.kt            # 异步状态密封类（Idle/Loading/Success/Error）
    ├── SharePreferenceUtils.kt    # DataStore 封装（suspend + 阻塞双模式）
    └── UserCouponStatus.kt        # 用户优惠券状态枚举
```

---

## 三、页面清单与路由

### 路由常量（PageConstant）— 16 个页面路由

| 常量 | 路由字符串 | 参数 | 说明 |
|------|-----------|------|------|
| `Login` | `"Login"` | — | 登录页 |
| `Register` | `"Register"` | — | 注册页 |
| `Home` | `"Home"` | — | 主页框架（底部导航） |
| `UpdateUserInfoPage` | `"UpdateUserInfoPage"` | — | 学校信息补全 |
| `Order` | `"Order"` | `itemId: Int` | 订单列表 |
| `OrderDetail` | `"OrderDetail"` | `orderId: Long` | 订单详情 |
| `Payment` | `"Payment"` | `orderId: Long` | 支付页 |
| `PaySuccess` | `"PaySuccess"` | `orderId: Long` | 支付成功 |
| `Laundry` | `"Laundry"` | — | 洗衣预约 |
| `Pickup` | `"Pickup"` | — | 取件列表 |
| `PickupDelivery` | `"PickupDelivery"` | `orderId: Long`, `pickupType: Int` | 取件/寄件详情 |
| `Recharge` | `"Recharge"` | — | 充值页 |
| `RechargeRecord` | `"RechargeRecord"` | — | 充值记录 |
| `Coupon` | `"Coupon"` | — | 优惠券管理 |
| `Service` | `"Service"` | — | 服务页（独立访问） |
| `Setting` | `"Setting"` | — | 设置页 |

### 底部导航 Tab（HomePageConstant）

| Tab | 图标 | 对应页面 |
|-----|------|---------|
| `Index` | Home | `IndexPage` |
| `Service` | List | `ServicePage` |
| `UserInfo` | Person | `UserInfoPage` |

---

## 四、核心架构模式

### 4.1 MVVM 分层

```
Page.kt（纯 UI 渲染）
  ↓ 收集 StateFlow
ViewModel.kt（业务逻辑，@HiltViewModel）
  ↓ 调用
Repository.kt（数据策略，缓存优先）
  ↓ 调用
Api.kt（Retrofit 接口） + Dao.kt（Room DAO）
```

**规则：**
- 每个页面对应一个 `*Page.kt` + 一个 `*ViewModel.kt`
- ViewModel 通过 `@Inject constructor` 注入 Repository / API / Application
- Page 只负责 UI 渲染，不包含业务逻辑
- Repository 负责缓存策略和数据源调度

### 4.2 状态管理

所有 ViewModel 使用 `RequestState` 密封类管理异步状态：

```kotlin
sealed class RequestState {
    data object Idle : RequestState()
    data object Loading : RequestState()
    data object Success : RequestState()
    data class Error(
        @StringRes val messageResId: Int,
        val message: String? = null
    ) : RequestState()
}
```

ViewModel 暴露方式：
```kotlin
private val _state = MutableStateFlow<RequestState>(RequestState.Idle)
val state: StateFlow<RequestState> = _state.asStateFlow()
```

Page 收集方式：
```kotlin
val state by viewModel.state.collectAsStateWithLifecycle()
```

### 4.3 鉴权流程

1. API 接口加 `@RequireAuthorization` 注解
2. `RequestInterceptor` 通过 Retrofit 反射检测该注解，从 DataStore 读取 token 并注入 `Bearer <token>` 请求头
3. `ResponseInterceptor` 处理响应：
   - HTTP 401 → 清除 token → 触发 `App.globalRequestAfterCallback` → 跳转登录页
   - 业务码 401/201 → 同上
   - 非 2xx → 映射为 `NetworkException`（含 `@StringRes` 错误文案）
4. Token 存储在 DataStore，key 为 `AppConstant.TOKEN`

### 4.4 缓存策略

Repository 层实现缓存优先：
- **LaundryRepository / CouponRepository**：先读 Room 缓存 → 发起网络请求 → 更新缓存
- **SchoolRepository**：三级缓存（内存 → Room → 网络）
- **其他 Repository**：直接网络请求（无缓存）

### 4.5 分页

使用 Paging 3，自定义 `PagingSource` 实现页码分页：
- `OrderPagingSource` — 订单列表（按状态筛选）
- `UserCouponPagingSource` — 用户优惠券
- `RechargeRecordPagingSource` — 充值记录

`PagingUtils.kt` 提供 `ViewModel.pagingFlow()` 扩展函数，从触发 Flow 创建响应式 Pager（300ms 防抖）。

### 4.6 导航

- 单 Activity 架构，`MainActivity` 通过 `NavHost` 承载所有页面
- 全局淡入淡出动画（300ms），支付相关页面使用滑动+淡入淡出（350ms）
- `HomePage` 内部使用嵌套 `NavHost` 实现底部 Tab 切换
- 路由参数：`Order/{itemId}`、`OrderDetail/{orderId}`、`Payment/{orderId}`、`PaySuccess/{orderId}`、`PickupDelivery/{orderId}/{pickupType}`

---

## 五、API 接口一览

> 所有端点以 `BuildConfig.BASE_URL` 为根。需鉴权的接口使用 `@RequireAuthorization` 注解，路径前缀为 `/web/auth/`。

### 用户模块（UserApi）— 10 个端点

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/auth/user/captcha/{phoneNumber}` | 否 | 获取短信验证码 |
| POST | `/auth/user/register` | 否 | 用户注册 |
| POST | `/auth/user/login` | 否 | 用户登录 |
| POST | `/web/auth/user/updateUserInfo` | 是 | 更新学校信息 |
| GET | `/web/auth/user/school` | 是 | 获取用户学校 ID |
| GET | `/web/auth/user/getUserByStudentId?studentId=` | 是 | 按学号查用户（Query 参数） |
| GET | `/web/auth/user/getUserInfo` | 是 | 获取用户详情 |
| POST | `/web/auth/user/bingCampus/{campusCard}` | 是 | 绑定校园卡 |
| POST | `/web/auth/user/unBingCampus` | 是 | 解绑校园卡 |
| POST | `/web/auth/user/avatar` | 是 | 上传头像（Multipart） |

### 订单模块（OrderApi）— 10 个端点

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/web/auth/orders/reservation` | 是 | 预约洗衣 |
| GET | `/web/auth/orders/{orderId}` | 是 | 订单详情 |
| GET | `/web/auth/orders?status=&page=&size=` | 是 | 订单列表（分页） |
| GET | `/web/auth/orders/summary?size=` | 是 | 按状态分组订单 |
| GET | `/web/auth/orders/itemCount` | 是 | 各状态订单数量 |
| POST | `/web/auth/orders/shipping` | 是 | 寄件（投递到柜） |
| POST | `/web/auth/orders/pickup` | 是 | 取件 |
| GET | `/web/auth/orders/getWashingOrder` | 是 | 洗涤中订单 |
| DELETE | `/web/auth/orders/{orderId}` | 是 | 取消订单 |
| GET | `/web/auth/orders/{orderId}/calculation?userCouponId=` | 是 | 计算价格 |

### 优惠券模块（CouponApi）— 5 个端点

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/web/auth/coupon/allCoupon` | 是 | 所有可领取优惠券 |
| POST | `/web/auth/userCoupon/receiveCoupon/{couponId}` | 是 | 领取优惠券 |
| GET | `/web/auth/userCoupon/getUserCoupon?status=&page=&pageSize=` | 是 | 用户已领优惠券（分页） |
| GET | `/web/auth/userCoupon/available/{orderId}` | 是 | 可用优惠券（支付时） |
| GET | `/web/auth/userCoupon/allCoupons` | 是 | 聚合接口（可领+已领+历史） |

### 充值模块（RechargeApi）— 2 个端点

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/web/auth/recharge/userRecharge` | 是 | 用户充值 |
| GET | `/web/auth/recharge/list?page=&size=` | 是 | 充值记录（分页） |

### 支付模块（PaymentApi）— 1 个端点

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/web/auth/payments/payment` | 是 | 订单支付 |

### 洗衣服务模块（LaundryItemsApi）— 1 个端点

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/web/laundryItems/all` | 否 | 获取所有洗衣项目 |

### 学校模块（SchoolApi）— 1 个端点

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/web/schools/allName?schoolName=` | 否 | 搜索学校（按名称模糊匹配） |

---

## 六、设计系统（清氧设计系统）

面向学生用户，设计理念：高级感 + 环保 + 年轻化（参考 MUJI/Aesop）。

### 配色

| 用途 | 色值 |
|------|------|
| 主色（自然绿） | `#2D9B6A` |
| 主色浅（标签/图标底色） | `#E8F6EF` |
| 主色超浅（选中态背景） | `#F0FAF5` |
| 页面底色（微暖米白） | `#FAFAF8` |
| 卡片底色 | `#FFFFFF` |
| 主文字 | `#1C1C1E` |
| 次要文字 | `#8E8E93` |
| 弱化文字 | `#C7C7CC` |
| 分隔线 | `#F2F2F7` |
| 错误色 | `#FF3B30` |
| 警告色 | `#FF9500` |
| 成功色 | `#34C759` |

### 圆角

- 大卡片：20dp
- 小卡片/按钮/输入框：14-16dp
- 图标容器：12dp

### 阴影

- **不使用阴影**，用底色差（米白底 `#FAFAF8` vs 白卡 `#FFFFFF`）创造层次
- 需要浮起效果时用 0.5dp 分隔线

### 排版

| 元素 | 字号 | 字重 |
|------|------|------|
| 页面标题 | 28sp | Bold |
| 区块标题 | 18sp | SemiBold |
| 卡片标题 | 16sp | Medium |
| 正文 | 15sp | Normal |
| 辅助文字 | 13sp | Normal |

### 认证页（登录/注册）

- 全屏渐变：`#1A9E6E` → `#0B5C3A`
- 毛玻璃卡片：`Color.White.copy(alpha = 0.12f)` + 1dp `alpha 0.18` 白边

### 功能页

- 统一 `Box(#FAFAF8)` 背景
- 自定义 `PageHeader`（不用 Scaffold + TopAppBar）
- `AppCard`：白底 20dp 圆角无阴影
- `AppButton`：绿底 14dp 圆角 52dp 高
- `AppTabBar`：文字标签 + 底部短横线指示器
- 底部导航：白底 + 0.5dp 顶部分隔线 + 短横线选中指示

### 相关文件

- `ui/theme/Color.kt` — 颜色定义
- `ui/theme/Theme.kt` — 主题配置
- `ui/theme/Type.kt` — 字体排版
- `ui/theme/AppDesign.kt` — 设计 Token + 可复用组件
- `ui/common/AppComponents.kt` — 共通组件（PageHeader, AppCard, AppButton, SettingRow, AppTabBar）
- `ui/common/InfoSection.kt` — 信息分组卡片
- `ui/common/InfoRow.kt` — 信息行

---

## 七、编码规范

### 7.1 基本规则

- **必须使用中文** — 所有对话、代码注释、提交信息均使用中文
- **提交格式** — `<type>(Android): <中文描述>`（如 `feat(Android): 新增订单详情页面`）
- **一个 commit 对应一个完整功能点**，不要逐文件提交
- 提交前使用 `commit-commands:commit` skill 检查变更范围

### 7.2 新增页面清单

新增页面必须完成以下步骤：

1. 在 `PageConstant` 中添加路由常量
2. 在 `MainActivity` 的 `NavHost` 中注册 composable
3. 创建 `*Page.kt` + `*ViewModel.kt`
4. ViewModel 使用 `@HiltViewModel` + `@Inject constructor`
5. 异步状态使用 `RequestState`
6. 所有用户可见文本定义在 `strings.xml`，通过 `stringResource()` 引用
7. 遵循清氧设计系统规范

### 7.3 新增 API 接口

1. 在对应 `*Api.kt` 中添加方法
2. 需要认证的接口加 `@RequireAuthorization`
3. 返回值统一使用 `ApiResult<T>` 包装
4. 请求体放在 `network/entity/` 对应子目录
5. 响应 VO 放在 `network/vo/` 对应子目录

### 7.4 字符串规范

- **禁止硬编码** — 所有用户可见文本必须定义在 `res/values/strings.xml`
- Page 中通过 `stringResource(R.string.xxx)` 引用
- ViewModel 中通过 `application.getString(R.string.xxx)` 获取
- 带参数的字符串使用 `%s`、`%d` 占位符
- 常量字符串（SharedPreferences key、日志 TAG）定义在常量类中

### 7.5 状态管理规范

- ViewModel 使用 `MutableStateFlow<T>` → `asStateFlow()` 暴露状态
- 页面使用 `collectAsStateWithLifecycle()` 收集
- 网络请求统一使用 `RequestState`（Idle → Loading → Success/Error）

---

## 八、构建与运行

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK（ProGuard 混淆 + 资源压缩）
./gradlew assembleRelease

# 运行单元测试（JVM，不需要设备）
./gradlew test

# 运行 Android 插桩测试（需要设备或模拟器）
./gradlew connectedAndroidTest

# 运行单个测试类
./gradlew test --tests "com.smartwash.ExampleTest"

# 代码检查
./gradlew lint
```

### 构建配置

- Debug BASE_URL: `http://192.168.1.61:8080/`（通过 `BuildConfig.BASE_URL` 获取）
- Release: 开启 ProGuard 混淆和资源压缩
- Maven 仓库使用阿里云镜像（国内下载更快），海外构建需改回 `google()` / `mavenCentral()`
- `usesCleartextTraffic=true`（后端为 HTTP 协议）

---

## 九、数据模型速查

### 枚举常量

| 枚举类 | 值 | 说明 |
|--------|-----|------|
| `OrderStatus` | -2 ~ 7（10 种） | 订单全生命周期状态 |
| `ShowOrderStatus` | 5 种 | 订单列表 Tab 显示状态 |
| `CouponStatus` | ACTIVE(0), EXPIRED(1), RECEIVE(2) | 优惠券状态 |
| `UserCouponStatus` | ACTIVE(0), OVERDUE(1) | 用户优惠券状态 |
| `PaymentType` | PURSE(1), ALI_PAY(2), WECHAT_PAY(3) | 支付方式 |
| `PickupDeliveryType` | PICKUP(0), DELIVERY(1) | 取件/寄件类型 |
| `HttpStatusCode` | 200, 201, 401, 404, 500 | HTTP 业务状态码 |

### 统一响应格式

```kotlin
data class ApiResult<out T>(
    val code: Int,      // 业务状态码
    val message: String, // 提示信息
    val data: T?,        // 数据载荷
)
```

### 分页响应格式

```kotlin
data class PageData<T>(
    val records: List<T>,
    val total: Long,
    val size: Int,
    val current: Int,
)
```

---

## 十、已知问题与待办

参见项目记忆文件中的完整清单，主要包括：

- **安全**：数据库密码硬编码（S5）、默认密码过弱（S6）待修复
- **流程断裂**：短信验证码未接入真实 SMS（F1）、支付无真实 SDK（F2）、部分按钮无交互（F3-F5）
- **架构**：无数据库迁移工具（A4）、无单元测试（A5）
- **待加功能**：订单状态追踪（N1）、推送通知（N2）、密码找回（N3）等 15 项

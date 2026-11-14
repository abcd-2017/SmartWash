# AGENTS.md — SmartWash Android 项目指南

> 智慧校园洗衣服务 App，面向学生用户提供在线预约洗衣、寄存柜投递、订单追踪、优惠券、充值支付等功能。
>
> 相关文档：编码 agent 硬规则见 [CLAUDE.md](CLAUDE.md)；子代理定义见 [.claude/agents/](.claude/agents/)；四端联动约定见仓库根目录 CLAUDE.md。
>
> 各模块独立文档：每个 Gradle 模块根目录下有 `AGENTS.md`（域规则以模块文档为准）。

---

## 一、技术栈

| 维度 | 技术选型 |
|------|----------|
| 语言 | Kotlin，JVM Target 17 |
| UI 框架 | Jetpack Compose + Material 3 |
| 架构模式 | 多模块 MVVM（Page + ViewModel + Repository） |
| 依赖注入 | Hilt（KSP 注解处理，与 Room 一致） |
| 网络 | Retrofit + Gson + OkHttp |
| 本地存储 | DataStore Preferences（主用）、Room（缓存层） |
| 分页 | Paging 3 |
| 图片加载 | Coil Compose |
| 二维码 | ZXing |
| 权限 | Accompanist Permissions |
| 导航 | Compose Navigation（单 Activity + NavHost 聚合） |
| 编译 | compileSdk 35 / minSdk 30 / targetSdk 35 |
| 注解处理 | KSP（Hilt 与 Room 统一走 KSP） |
| 构建工具 | Gradle + convention plugin（build-logic） |

---

## 二、模块结构

项目已从单模块重构为 **36 个 Gradle 模块**（1 app + 5 common + 30 feature 子模块），**15 个业务域全部采用 api/impl 拆分**（契约与实现分离）。

### 模块地图

```
:app                             壳：Application + MainActivity(NavHost 聚合 16 个 graph) + 更新弹窗 UI + 取件留守页 + 深链注册表
:common:init                      InitTask 契约 + InitEngine（零业务依赖：仅 Android SDK + Hilt + coroutines）
:common:utils                   跨模块共享：ApiResult 信封 / PageData / HttpStatusCode / RequestState / DataStore 封装 / 深链解析契约 / 动效触感 / 二维码 / pagingFlow
:common:network                 OkHttp/Retrofit 供给 / 鉴权+错误转译拦截器 / 401 单飞续签 / SSE 客户端 / @RequireAuthorization / NetworkException
:common:database                Room 主缓存库(AppDatabase v3)：洗衣项目/学校/优惠券三表（共库，T8.2 决策不拆）
:common:ui                      清氧设计系统(theme) + 共享组件(components) + ShellRoute 壳层路由契约
:feature:update:api             AppVersionVo + AppUpdateApi 契约
:feature:update:impl            热更新全链路（Worker/Installer/InitTask/ViewModel）
:feature:divination:api         DivRoute + DivinationApi/VO 契约
:feature:divination:impl        观象台（四套算法内核 + 卦历库 + SSE 解读网关 + BYOK + 7 页面）
:feature:game:api               GameRoute + GameApi/GameVo 契约
:feature:game:impl              游戏中心（封面网格 + WebView 承载）
:feature:lab:api                LabRoute + LabToy 契约
:feature:lab:impl               解压实验舱（10 个纯本地玩具，零网络/零存储）
:feature:review:api             ReviewRoute/Constant + ReviewApi/VO 契约
:feature:review:impl            记忆复习（FSRS 队列/学习/资料）
:feature:subscription:api       SubscriptionRoute/Constant/Enums + SubscriptionApi/VO 契约
:feature:subscription:impl      订阅逃生舱（列表/编辑/详情/提醒）
:feature:toolbox:api            ToolboxRoute/Constant + 短码/资产标签/投递三套 Api/VO 契约
:feature:toolbox:impl           工具箱（短链 + 设备投递会话）
:feature:user:api               UserApi 契约 + User 模型 + UserRoute（登录态/用户信息/登录事件）
:feature:user:impl              SessionManager + UserRepository + 登录/注册/重置修改密码/用户中心/资料编辑/设置/权限/钱包流水
:feature:order:api              OrderApi 契约 + 订单模型 + OrderRoute（含寄件取件路由常量）
:feature:order:impl             OrderRepository + OrderGroup + OrderPagingSource + OrderServiceApi + 订单页
:feature:payment:api            PaymentRoute 路由常量（零跨模块数据消费）
:feature:payment:impl           支付 + 充值网络链路与页面
:feature:laundry:api            LaundryRoute + 学校搜索最小契约（SchoolSearchSource）
:feature:laundry:impl           Laundry/School Repository + 洗衣预约/服务页（内存→Room→网络 缓存降级）
:feature:coupon:api             CouponApi 契约 + 优惠券模型 + CouponRoute
:feature:coupon:impl            CouponRepository + 三 Tab 优惠券页
:feature:points:api             PointsRoute + 积分 Gson 载荷（全可空容错）+ PointsServiceApi 网络接口
:feature:points:impl            PointsRepository + 签到有礼页（签到/余额/月榜/流水单页）+ 深链 host "points"
:feature:notice:api             NoticeApi 契约 + 站内信模型 + NoticeRoute + NoticeServiceApi
:feature:notice:impl            NoticeRepository + 消息中心收件箱页（未读高亮/全部已读/深链 messages）
build-logic                     convention plugin（smartwash.android.library / smartwash.compose / smartwash.hilt）
```

> **无孤儿目录**：历史残留（`feature/{ai,lostfound,accounting,almanac,bookshelf,habit,memo,poem,readlater,rss,studycenter}`、`common/security`）的 `build/` 已清理；当前 `feature/` 下 15 个域均为 api/impl 模块。

### 新增模块 SOP（必须使用脚手架）

**新增任何 Gradle 模块一律经 `scripts/scaffold-module.py` 创建，禁止手搓目录。** 脚本对齐 `kmp-ohos-template/script/scaffold-module.py`，已适配本仓库架构（Groovy + `config/` convention + Hilt / NavGraph / 深链贡献 / 每模块 `AGENTS.md`）。

```bash
# 业务域：单模块（默认）
python scripts/scaffold-module.py --type feature --name game
# 业务域：api/impl 拆分
python scripts/scaffold-module.py --type feature --name order --split
# 通用库（按需追加 compose / hilt / serialization）
python scripts/scaffold-module.py --type common --name util --with-compose
# 只看不建（验证模板落点）
python scripts/scaffold-module.py --type feature --name demo --dry-run
# 删除（settings 注销 + app 依赖移除 + git rm）
python scripts/scaffold-module.py --type feature --name game --delete --yes
```

- Unix / Git Bash 用 `scripts/scaffold-module.sh`，Windows 用 `scripts/scaffold-module.bat`（均薄包装调用 Python 核心）。
- **脚本自动完成**：目录与包结构、`build.gradle`（apply `config/` convention）、`AndroidManifest.xml`、`AGENTS.md` 骨架、`settings.gradle` 注册、`app/build.gradle` 实现模块依赖注入、`git add`。
- **脚本不代劳的收尾**（创建后必须手工做）：
  1. `MainActivity` 加 `import com.smartwash.feature.<name>.ui.<name>Graph` 与 NavHost 内一行 `<name>Graph(navController)`；
  2. 与后端对齐 `<Class>ServiceApi` 的路径/参数/返回（返回统一 `ApiResult<T>`）；
  3. 补全模块 `AGENTS.md`；
  4. 运行 `./scripts/check-deps.sh` 校验依赖铁律。
- **深链 host**：默认等于模块名，可 `--host` 指定；落点为 `<Class>Route.Home`。
- **api/impl 拆分判断**：本仓库 15 个业务域已全部 api/impl 拆分；新域一律 `--split`（契约与实现分离，见「4.2 api/impl 服务化」）。

### 各模块独立文档

每个模块根目录下有 `AGENTS.md`，记录该模块的包结构、公开 API、依赖关系、内部约定和已知坑。**在某个模块内工作时，应以该模块的 AGENTS.md 为第一手参考**。

| 模块 | 文档 |
|------|------|
| `:app` | [app/AGENTS.md](app/AGENTS.md) |
| `:common:init` | [common/init/AGENTS.md](common/init/AGENTS.md) |
| `:common:utils` | [common/utils/AGENTS.md](common/utils/AGENTS.md) |
| `:common:network` | [common/network/AGENTS.md](common/network/AGENTS.md) |
| `:common:database` | [common/database/AGENTS.md](common/database/AGENTS.md) |
| `:common:ui` | [common/ui/AGENTS.md](common/ui/AGENTS.md) |
| `:feature:user:api` | [feature/user/api/AGENTS.md](feature/user/api/AGENTS.md) |
| `:feature:user:impl` | [feature/user/impl/AGENTS.md](feature/user/impl/AGENTS.md) |
| `:feature:order:api` | [feature/order/api/AGENTS.md](feature/order/api/AGENTS.md) |
| `:feature:order:impl` | [feature/order/impl/AGENTS.md](feature/order/impl/AGENTS.md) |
| `:feature:payment:api` | [feature/payment/api/AGENTS.md](feature/payment/api/AGENTS.md) |
| `:feature:payment:impl` | [feature/payment/impl/AGENTS.md](feature/payment/impl/AGENTS.md) |
| `:feature:laundry:api` | [feature/laundry/api/AGENTS.md](feature/laundry/api/AGENTS.md) |
| `:feature:laundry:impl` | [feature/laundry/impl/AGENTS.md](feature/laundry/impl/AGENTS.md) |
| `:feature:coupon:api` | [feature/coupon/api/AGENTS.md](feature/coupon/api/AGENTS.md) |
| `:feature:coupon:impl` | [feature/coupon/impl/AGENTS.md](feature/coupon/impl/AGENTS.md) |
| `:feature:points:api` | [feature/points/api/AGENTS.md](feature/points/api/AGENTS.md) |
| `:feature:points:impl` | [feature/points/impl/AGENTS.md](feature/points/impl/AGENTS.md) |
| `:feature:notice:api` | [feature/notice/api/AGENTS.md](feature/notice/api/AGENTS.md) |
| `:feature:notice:impl` | [feature/notice/impl/AGENTS.md](feature/notice/impl/AGENTS.md) |
| `:feature:divination:api` / `:impl` | [feature/divination/api/AGENTS.md](feature/divination/api/AGENTS.md) / [impl](feature/divination/impl/AGENTS.md) |
| `:feature:game:api` / `:impl` | [feature/game/api/AGENTS.md](feature/game/api/AGENTS.md) / [impl](feature/game/impl/AGENTS.md) |
| `:feature:lab:api` / `:impl` | [feature/lab/api/AGENTS.md](feature/lab/api/AGENTS.md) / [impl](feature/lab/impl/AGENTS.md) |
| `:feature:review:api` / `:impl` | [feature/review/api/AGENTS.md](feature/review/api/AGENTS.md) / [impl](feature/review/impl/AGENTS.md) |
| `:feature:subscription:api` / `:impl` | [feature/subscription/api/AGENTS.md](feature/subscription/api/AGENTS.md) / [impl](feature/subscription/impl/AGENTS.md) |
| `:feature:toolbox:api` / `:impl` | [feature/toolbox/api/AGENTS.md](feature/toolbox/api/AGENTS.md) / [impl](feature/toolbox/impl/AGENTS.md) |
| `:feature:update:api` / `:impl` | [feature/update/api/AGENTS.md](feature/update/api/AGENTS.md) / [impl](feature/update/impl/AGENTS.md) |
```

### 各模块内关键文件

#### `:app` 壳层

```
com.smartwash/
├── App.kt                          # Application 类（@HiltAndroidApp），InitEngine 调度入口
├── di/
│   └── BaseUrlModule.kt            # @Named("baseUrl") 供给（BuildConfig.BASE_URL）
└── ui/
    ├── activity/
    │   └── MainActivity.kt         # 单 Activity 壳，NavHost 聚合各域 xxxGraph + 登录态事件收集
    ├── navigation/
    │   └── ShellGraph.kt           # shellGraph 扩展：壳层留守页面（主页壳/订单详情/寄件取件/取件）注册
    ├── page/
    │   ├── PageConstant.kt         # 壳层自有路由常量（仅剩 Pickup）
    │   ├── detail/                 # 订单详情页（页面留壳，路由常量归 OrderRoute）
    │   ├── home/                   # 主页壳（底部 Tab 嵌套 NavHost）
    │   ├── index/                  # 首页 Tab（余额卡/服务网格/进行中订单）
    │   ├── pickup/                 # 取件列表 + 取件/寄件详情（QR 码）
    │   └── update/                 # 更新弹窗 UI（UpdateDialog/UpdateFlow/CheckUpdateSettingRow）
```

#### `:common:init`

```
com.smartwash.common.init/
├── InitTask.kt                    # 启动任务抽象（taskId/priority/dependencies/blocking/timeoutMs/execute）
├── InitEngine.kt                  # 拓扑排序 + 阻塞串行/非阻塞 launch + 超时保护 + 进度 StateFlow
└── InitTaskRegistry.kt            # 任务注册表（Hilt @IntoSet 多绑定收集）
```

#### `:common:utils`

```
com.smartwash.common.utils/
├── model/                         # 原 common:model 的数据契约
│   ├── ApiResult.kt               # 统一响应包装 {code, message, data}
│   ├── PageData.kt                # 分页响应 {records, total, size, current}
│   ├── HttpStatusCode.kt          # HTTP 业务状态码枚举
│   └── RequestState.kt            # 异步状态密封类（Idle/Loading/Success/Error）
├── SharePreferenceUtils.kt        # DataStore 封装（suspend + 阻塞双模式）
├── AppConstant.kt                 # 应用常量（APP_NAME, TOKEN key 等）
├── AnimationUtils.kt              # 动效工具（含 defaultSpring）
├── HapticUtils.kt                 # 触感反馈工具
├── BitmapUtil.kt                  # ZXing 二维码生成
├── ParamValidUtils.kt             # 手机号校验
├── PressFeedbackModifier.kt       # 按压反馈（已知坑：未接入 clickable，勿模仿）
└── PagingUtils.kt                 # pagingFlow() 扩展（debounce + flatMapLatest + cachedIn）
```

#### `:common:network`

```
com.smartwash.common.network/
├── NetworkModule.kt               # OkHttp/Retrofit 供给（拦截器 + debug 日志）
├── TokenProvider.kt               # token 供给契约（user:impl SessionManager @Binds）
├── SessionEventNotifier.kt        # 会话事件通知契约（user:impl SessionEventBus @Binds）
├── annotation/
│   └── RequireAuthorization.kt    # 鉴权注解
├── exception/
│   └── Exceptions.kt              # NetworkException（含 @StringRes 错误文案）
└── interceptor/
    ├── RequestInterceptor.kt      # 自动注入 Bearer Token
    └── ResponseInterceptor.kt     # 401 处理、错误映射
```

#### `:common:database`

```
com.smartwash.common.database/
├── AppDatabase.kt                 # Room 数据库（v3，3 张表）
├── DatabaseModule.kt              # Room 实例供给
├── dao/
│   ├── CouponVoDao.kt
│   ├── LaundryItemDao.kt
│   └── SchoolNameDao.kt
└── entity/
    ├── CouponVoEntity.kt
    ├── LaundryItemEntity.kt
    └── SchoolNameEntity.kt
```

#### `:common:ui`

```
com.smartwash.common.ui/
├── components/
│   ├── AppComponents.kt           # PageHeader, AppCard, AppButton, AppTabBar, SettingRow
│   ├── InfoRow.kt                 # 信息行组件
│   ├── InfoSection.kt             # 信息分组卡片
│   ├── PasswordInput.kt           # 密码输入框
│   └── PhoneNumberInput.kt        # 手机号输入框
├── navigation/
│   └── ShellRoute.kt              # 壳层路由契约（HOME 常量）
└── theme/
    ├── Color.kt                   # 颜色定义
    ├── Theme.kt                   # 主题配置
    ├── Type.kt                    # 字体排版
    └── AppDesign.kt               # 设计 Token + 可复用组件
```

#### `:feature:update`

```
com.smartwash.feature.update/
├── di/
│   ├── UpdateModule.kt            # 更新相关依赖提供
│   └── UpdateInitModule.kt        # UpdateInitTask @IntoSet 注册
├── event/
│   └── UpdateEventBus.kt          # 更新事件总线
├── init/
│   └── UpdateInitTask.kt          # 热更新检查启动任务（非阻塞，priority=900）
├── model/
│   └── AppVersionVo.kt            # 版本信息 VO
├── network/
│   └── AppUpdateApi.kt            # 2 个端点（版本检查 + 预签名下载）
├── repository/
│   └── AppUpdateRepository.kt     # 应用更新（版本检查 + 下载）
├── service/
│   ├── ApkDownloadWorker.kt       # WorkManager APK 后台下载
│   └── ApkInstaller.kt            # APK 安装器
└── ui/
    └── UpdateViewModel.kt         # 更新状态管理
```

#### `:feature:divination`

```
com.smartwash.feature.divination/
├── DivRoute.kt                    # 观象台路由常量（7 个）
├── core/                          # 四套算法内核 + 公共基础
│   ├── liuren/                    # 六壬
│   ├── liuyao/                    # 六爻
│   ├── meihua/                    # 梅花
│   ├── qimen/                     # 奇门
│   ├── DivinationCommon.kt        # 公共定义
│   ├── GanZhi.kt                  # 干支
│   └── Yao.kt                     # 爻
├── data/
│   ├── DivChartCodec.kt           # 卦盘编解码
│   ├── DivLlmRepository.kt        # LLM 解读网关
│   └── DivRecordRepository.kt     # 卦历案卷
├── database/                      # 观象台独立 Room 库
│   ├── DivinationDatabase.kt
│   ├── DivRecordDao.kt
│   └── DivRecordEntity.kt
├── di/
│   └── DivinationModule.kt        # Hilt 模块
├── network/
│   └── DivinationApi.kt           # 观象台 Retrofit 接口
├── ui/
│   ├── DivinationGraph.kt         # 观象台路由聚合
│   ├── DivinationScreen.kt
│   ├── components/                # 卦盘/罗盘/钱币组件
│   └── page/
│       ├── ask/                   # 心中所问
│       ├── cast/                  # 摇卦
│       ├── chart/                 # 卦盘
│       ├── followup/              # 继续追问
│       ├── history/               # 卦历案卷
│       ├── home/                  # 观象台首页
│       └── reading/               # 解读
```

#### `:feature:user:api`

```
com.smartwash.feature.user.api/
├── UserApi.kt                     # 用户域对外服务契约（isLogin/getUserInfo/loginEvents）
├── UserRoute.kt                   # 用户域路由常量（Login/Register/UserInfo/UpdateUserInfo/Setting）
└── model/
    ├── LoginEvent.kt              # 登录事件（LoggedIn/LoggedOut）
    └── UserInfo.kt                # 用户信息模型
```

#### `:feature:user:impl`

```
com.smartwash.feature.user.impl/
├── UserApiImpl.kt                 # UserApi 实现（@Binds）
├── UserImplConstant.kt            # 用户域常量
├── di/
│   └── UserImplModule.kt          # Hilt 模块（UserApi/TokenProvider/SessionEventNotifier 绑定）
├── init/
│   └── SessionInitTask.kt         # 会话预热启动任务（阻塞，priority=10）
├── network/
│   ├── api/
│   │   └── UserAccountApi.kt      # 用户 Retrofit 接口（登录/注册/用户信息）
│   ├── entity/user/               # 请求体（LoginUser/RegisterUser/UpdateUserInfo）
│   └── vo/user/                   # 响应 VO（LoginVo/UserInfoVo）
├── repository/
│   └── UserRepository.kt          # 用户数据仓库
├── session/
│   ├── SessionEventBus.kt         # 会话事件总线（实现 SessionEventNotifier）
│   └── SessionManager.kt          # 会话管理器（实现 TokenProvider）
└── ui/
    ├── UserGraph.kt               # 用户域路由聚合
    ├── login/                     # 登录页（毛玻璃设计）
    ├── register/                  # 注册页（毛玻璃设计）
    ├── setting/                   # 设置页（含登出入口）
    ├── update_userinfo/           # 学校信息补全
    └── userinfo/                  # 用户中心 Tab
```

#### `:feature:order:api`

```
com.smartwash.feature.order.api/
├── OrderApi.kt                    # 订单域对外服务契约
├── OrderRoute.kt                  # 订单域路由常量（Order/OrderDetail/PickupDelivery）
├── PickupDeliveryType.kt          # 取件/寄件类型枚举
└── model/
    ├── OrderBrief.kt              # 订单摘要
    ├── OrderInfo.kt               # 订单详情
    ├── OrderItemCount.kt          # 各状态订单计数
    └── OrderStatus.kt             # 订单状态枚举（10 种）+ ShowOrderStatus（5 种）
```

#### `:feature:order:impl`

```
com.smartwash.feature.order.impl/
├── OrderApiImpl.kt                # OrderApi 实现（@Binds）
├── OrderImplConstant.kt           # 订单域常量
├── di/
│   └── OrderImplModule.kt         # Hilt 模块
├── network/
│   ├── api/
│   │   └── OrderServiceApi.kt     # 订单 Retrofit 接口
│   ├── entity/order/             # 请求体（ReservationLaundry/OrderNextStatus）
│   └── vo/order/                  # 响应 VO（OrderInfoVo/OrderVo/OrderGroupVo/OrderItemCountVo）
├── paging/
│   └── OrderPagingSource.kt       # 订单列表分页
├── repository/
│   ├── OrderGroup.kt              # 按状态分组订单
│   └── OrderRepository.kt         # 订单数据仓库
└── ui/
    ├── OrderGraph.kt              # 订单域路由聚合
    └── order/                     # 订单列表页（五 Tab 分页）
```

#### `:feature:payment:api`

```
com.smartwash.feature.payment.api/
└── PaymentRoute.kt                # 支付域路由常量（Payment/PaySuccess/Recharge/RechargeRecord）
```

#### `:feature:payment:impl`

```
com.smartwash.feature.payment.impl/
├── PaymentImplConstant.kt         # 支付域常量
├── di/
│   └── PaymentApiModule.kt        # Hilt 模块
├── network/
│   ├── api/
│   │   ├── PaymentApi.kt          # 支付 Retrofit 接口
│   │   └── RechargeApi.kt         # 充值 Retrofit 接口
│   ├── entity/                   # 请求体（OrderPayment/UserRecharge）
│   └── vo/recharge/              # 响应 VO（RechargeRecordVo）
├── paging/
│   └── RechargeRecordPagingSource.kt # 充值记录分页
├── repository/
│   ├── PaymentRepository.kt       # 支付数据仓库
│   └── RechargeRepository.kt      # 充值数据仓库
└── ui/
    ├── PaymentGraph.kt            # 支付域路由聚合
    ├── payment/                   # 支付页 + 支付成功页
    └── recharge/                  # 充值页 + 充值记录页
```

#### `:feature:laundry:api`

```
com.smartwash.feature.laundry.api/
├── LaundryRoute.kt                # 洗衣域路由常量（Laundry/Service）
├── SchoolSearchSource.kt          # 学校搜索最小契约
└── model/
    └── SchoolOption.kt            # 学校选项模型
```

#### `:feature:laundry:impl`

```
com.smartwash.feature.laundry/
├── LaundryImplConstant.kt         # 洗衣域常量
├── di/
│   └── LaundryModule.kt           # Hilt 模块
├── network/
│   ├── api/
│   │   ├── LaundryItemsApi.kt     # 洗衣项目 Retrofit 接口
│   │   └── SchoolApi.kt          # 学校搜索 Retrofit 接口
│   └── vo/
│       ├── LaundryItem.kt         # 洗衣项目 VO
│       └── SchoolName.kt         # 学校名称 VO
├── repository/
│   ├── EntityMappers.kt           # Entity ↔ VO 转换
│   ├── LaundryRepository.kt       # 洗衣数据仓库（内存→Room→网络 缓存降级）
│   ├── SchoolRepository.kt        # 学校数据仓库（三级缓存）
│   └── SchoolSearchSourceImpl.kt  # SchoolSearchSource 实现
└── ui/
    ├── LaundryGraph.kt            # 洗衣域路由聚合
    ├── laundry/                   # 洗衣预约页
    └── service/                   # 服务目录页
```

#### `:feature:coupon:api`

```
com.smartwash.feature.coupon.api/
├── CouponApi.kt                   # 优惠券域对外服务契约（getCanUseCoupon）
├── CouponRoute.kt                 # 优惠券域路由常量（Coupon）
└── model/
    └── UsableCoupon.kt            # 可用优惠券模型
```

#### `:feature:coupon:impl`

```
com.smartwash.feature.coupon/
├── CouponImplConstant.kt          # 优惠券域常量
├── CouponStatus.kt                # 优惠券状态枚举
├── di/
│   └── CouponModule.kt            # Hilt 模块
├── network/
│   ├── api/
│   │   └── CouponServiceApi.kt    # 优惠券 Retrofit 接口
│   └── vo/coupon/                # 响应 VO（AllCouponsVo/CouponVo/UserCouponVo）
├── repository/
│   ├── CouponApiImpl.kt           # CouponApi 实现（@Binds）
│   ├── CouponRepository.kt        # 优惠券数据仓库
│   └── EntityMappers.kt           # Entity ↔ VO 转换
└── ui/
    ├── CouponGraph.kt             # 优惠券域路由聚合
    └── coupon/
        ├── CouponPage.kt          # 优惠券页
        └── tab/                   # 三 Tab（可领取/已领取/历史）
```

#### `:feature:game`

```
com.smartwash.feature.game/
├── GameRoute.kt                   # GameRoute.Center = "GameCenter"
├── GameConstant.kt                # 分类/图标/色组白名单
├── di/
│   ├── GameModule.kt              # Hilt 模块
│   └── DeepLinkModule.kt          # host "game-center" → GameRoute.Center
├── model/GameItem.kt
├── network/GameApi.kt             # GET web/auth/game/list
├── repository/
│   ├── GameRepository.kt
│   └── GameRepositoryImpl.kt
└── ui/
    ├── GameGraph.kt               # gameGraph（游戏卡复用 ShellRoute.WebView）
    └── center/
        ├── GameCenterPage.kt      # 分类 chips + 两列封面网格 + 骨架/空/错误三态
        └── GameCenterViewModel.kt
```

#### `:feature:lab`

```
com.smartwash.feature.lab/
├── LabRoute.kt                    # Home/BubbleWrap/Breath/Fish/Sand/Fluid/Drum/Painting/Soundscape/Pet/StarMap + LabToy
├── di/LabDeepLinkModule.kt        # host "lab" → LabRoute.Home
├── util/PopSoundPlayer.kt         # AudioTrack 程序化合成 pop 短音（零音频资产）
└── ui/
    ├── LabGraph.kt                # labGraph（11 个 composable）
    ├── home/                      # 实验舱首页（10 张玩具入口卡）
    └── {breath,bubble,drum,fish,fluid,painting,pet,sand,soundscape,starmap}/  # 10 个玩具页
```

> 纯本地模块：零网络、零存储、零 Api/Repository；玩具状态会话内即弃。

#### `:feature:review`

```
com.smartwash.feature.review/
├── ReviewRoute.kt                 # Queue / Study(startCardId) / Materials + CHANGED_KEY
├── ReviewConstant.kt
├── model/ReviewModels.kt          # ReviewRating(1..4) / ReviewCardState(1/2/3)
├── network/ReviewApi.kt           # 7 个端点（资料/卡片/队列/评分/统计）
├── repository/                    # ReviewRepository 接口 + Impl
├── di/ReviewModule.kt
├── logic/ReviewFormat.kt          # 到期量级换算 + 日期截取
└── ui/
    ├── ReviewGraph.kt             # reviewGraph + notifyReviewDataChanged
    ├── ReviewUiCommon.kt
    ├── queue/                     # 今日队列
    ├── study/                     # 复习（翻面 + 四档评分）
    └── material/                  # 资料与建卡
```

#### `:feature:subscription`

```
com.smartwash.feature.subscription/
├── SubscriptionRoute.kt           # List / Edit(id) / Detail(id) / Reminders + CHANGED_KEY
├── SubscriptionConstant.kt
├── SubscriptionEnums.kt
├── network/SubscriptionApi.kt     # 15 个端点（订阅 CRUD/暂停/恢复/取消/完成/用量/提醒/统计）
├── logic/SubscriptionRules.kt
├── paging/
│   ├── SubscriptionPagingSource.kt
│   └── SubscriptionUsagePagingSource.kt
├── repository/
│   ├── SubscriptionRepository.kt
│   └── SubscriptionRepositoryImpl.kt
├── di/
│   ├── SubscriptionModule.kt
│   └── SubscriptionDeepLinkModule.kt  # host "subscription" → SubscriptionRoute.List
└── ui/
    ├── SubscriptionGraph.kt  SubscriptionUiCommon.kt
    └── list/ edit/ detail/ reminder/
```

#### `:feature:toolbox`

```
com.smartwash.feature.toolbox/
├── ToolboxRoute.kt                # List / Create / Detail / Delivery / DeliverySession + CHANGED_KEY
├── ToolboxConstant.kt
├── network/
│   ├── ToolboxApi.kt              # 短链 6 个端点
│   ├── DeliveryApi.kt             # 设备投递 7 个端点
│   └── DeliveryBusinessCode.kt
├── logic/
│   ├── ShortCodeRules.kt
│   └── DeliveryRules.kt
├── paging/
│   ├── ShortCodePagingSource.kt
│   └── ShortCodeStatsPagingSource.kt
├── repository/
│   ├── ToolboxRepository.kt (+ Impl)
│   └── DeliveryRepository.kt (+ Impl)
├── di/
│   ├── ToolboxModule.kt
│   └── DeepLinkModule.kt          # toolbox-short-code / delivery / t/{code}
└── ui/
    ├── ToolboxGraph.kt  ToolboxUiCommon.kt
    ├── list/ create/ detail/
    └── delivery/
        ├── DeliveryGraph.kt
        ├── DeliveryCreatePage.kt
        ├── DeliverySessionPage.kt
        ├── DeliveryCountdown.kt  DeliveryDeviceSaver.kt  DeliveryPreviewDialog.kt
```

---

## 三、页面清单与路由

### 路由常量分布

路由常量以各域 `*Route` 密封类为**单一事实来源**，不再集中在 `PageConstant`：

| 域 | Route 类 | 位置 |
|----|---------|------|
| 壳层留守 | `PageConstant`（Pickup / AiWork） | `:app` |
| 壳层路由契约 | `ShellRoute`（HOME / WebView） | `:common:ui` |
| 用户 | `UserRoute` | `:feature:user:api` |
| 订单 | `OrderRoute` | `:feature:order:api` |
| 支付 | `PaymentRoute` | `:feature:payment:api` |
| 洗衣 | `LaundryRoute` | `:feature:laundry:api` |
| 优惠券 | `CouponRoute` | `:feature:coupon:api` |
| 站内信 | `NoticeRoute` | `:feature:notice:api` |
| 积分 | `PointsRoute` | `:feature:points:api` |
| 观象台 | `DivRoute` | `:feature:divination` |
| 游戏 | `GameRoute` | `:feature:game` |
| 实验舱 | `LabRoute` | `:feature:lab` |
| 复习 | `ReviewRoute` | `:feature:review` |
| 订阅 | `SubscriptionRoute` | `:feature:subscription` |
| 工具箱 | `ToolboxRoute` | `:feature:toolbox` |

### 路由常量一览

| 常量 | 路由字符串 | 参数 | 所属域 | 说明 |
|------|-----------|------|--------|------|
| `UserRoute.Login` | `"Login"` | — | user | 登录页 |
| `UserRoute.Register` | `"Register"` | — | user | 注册页 |
| `UserRoute.UserInfo` | `"UserInfo"` | — | user | 用户中心 Tab |
| `UserRoute.UpdateUserInfo` | `"UpdateUserInfoPage"` | — | user | 学校信息补全 |
| `UserRoute.Setting` | `"Setting"` | — | user | 设置页 |
| `UserRoute.PermissionManagement` | `"PermissionManagement"` | — | user | 权限管理 |
| `UserRoute.WalletTransactions` | `"WalletTransactions"` | — | user | 钱包流水 |
| `UserRoute.ChangePassword` | `"ChangePassword"` | — | user | 修改密码 |
| `UserRoute.ResetPassword` | `"ResetPassword"` | — | user | 重置密码 |
| `OrderRoute.Order` | `"Order/{itemId}"` | `itemId: Int` | order | 订单列表 |
| `OrderRoute.OrderDetail` | `"OrderDetail/{orderId}"` | `orderId: Long` | order | 订单详情 |
| `OrderRoute.PickupDelivery` | `"PickupDelivery/{orderId}/{pickupType}"` | `orderId: Long`, `pickupType: Int` | order | 寄件取件 |
| `PaymentRoute.Payment` | `"Payment/{orderId}"` | `orderId: Long` | payment | 支付页 |
| `PaymentRoute.PaySuccess` | `"PaySuccess/{orderId}"` | `orderId: Long` | payment | 支付成功 |
| `PaymentRoute.Recharge` | `"Recharge"` | — | payment | 充值页 |
| `PaymentRoute.RechargeRecord` | `"RechargeRecord"` | — | payment | 充值记录 |
| `LaundryRoute.Laundry` | `"Laundry?itemId={itemId}"` | `itemId: Long` | laundry | 洗衣预约 |
| `LaundryRoute.Service` | `"Service"` | — | laundry | 服务目录（底栏 tab） |
| `CouponRoute.Coupon` | `"Coupon"` | — | coupon | 优惠券管理 |
| `NoticeRoute.Inbox` | `"Messages"` | — | notice | 消息中心收件箱 |
| `PointsRoute.Home` | `"PointsHome"` | — | points | 签到有礼 |
| `DivRoute.Home` | `"DivHome"` | — | divination | 观象台首页 |
| `DivRoute.Ask` | `"DivAsk?method={method}"` | `method: String` | divination | 心中所问 |
| `DivRoute.Cast` | `"DivCast?question={question}&category={category}"` | `question`, `category` | divination | 摇卦 |
| `DivRoute.Chart` | `"DivChart/{recordId}?animate={animate}"` | `recordId: Long`, `animate: Boolean` | divination | 卦盘 |
| `DivRoute.Reading` | `"DivReading/{recordId}"` | `recordId: Long` | divination | 解读 |
| `DivRoute.FollowUp` | `"DivFollowUp/{recordId}"` | `recordId: Long` | divination | 继续追问 |
| `DivRoute.History` | `"DivHistory"` | — | divination | 卦历案卷 |
| `GameRoute.Center` | `"GameCenter"` | — | game | 游戏中心 |
| `LabRoute.Home` | `"LabHome"` | — | lab | 实验舱首页（另有 10 个玩具路由） |
| `ReviewRoute.Queue` | `"ReviewQueue"` | — | review | 今日复习队列 |
| `SubscriptionRoute.List` | `"SubscriptionList"` | — | subscription | 订阅列表 |
| `ToolboxRoute.List` | `"ToolboxShortCodeList"` | — | toolbox | 短链列表 |
| `ToolboxRoute.Delivery` | `"ToolboxDelivery"` | — | toolbox | 设备投递创建 |
| `PageConstant.Pickup` | `"Pickup"` | — | 壳层 | 取件列表 |
| `PageConstant.AiWork` | `"AiWork"` | — | 壳层 | AI 助手 |

### 底部导航 Tab（HomePageConstant）— 4 个 Tab

| Tab | 图标 | 路由值来源 | 对应页面 |
|-----|------|-----------|---------|
| `Index` | Home | 壳层自持 | `IndexPage` |
| `Service` | List | `LaundryRoute.Service` | `ServicePage` |
| `Workbench` | Grid | 壳层自持 | `WorkbenchPage` |
| `UserInfo` | Person | `UserRoute.UserInfo` | `UserInfoPage` |

---

## 四、核心架构模式

### 4.1 MVVM 分层

```
*Page.kt（纯 UI 渲染，各域 impl 模块内）
  ↓ 收集 StateFlow
*ViewModel.kt（业务逻辑，@HiltViewModel，各域 impl 模块内）
  ↓ 调用
*Repository.kt（数据策略，各域 impl 模块内）
  ↓ 调用
XxxApi（Retrofit 接口，各域 impl 模块内） + Dao.kt（Room DAO，:common:database）
```

**规则：**
- 每个页面对应一个 `*Page.kt` + 一个 `*ViewModel.kt`，位于对应 feature 的 impl 模块
- ViewModel 通过 `@Inject constructor` 注入 Repository / 跨域 Api 接口
- Page 只负责 UI 渲染，不包含业务逻辑
- Repository 负责缓存策略和数据源调度

### 4.2 api/impl 服务化

feature 域拆为 **api**（接口+模型+路由常量，纯 Kotlin/轻 Android，不引 Hilt 运行时）和 **impl**（实现+页面，带 Hilt）：

```
api 模块                          impl 模块
├── XxxApi.kt  ←──── @Binds ──── XxxApiImpl.kt
├── XxxRoute.kt                     ├── di/XxxImplModule.kt
└── model/                          ├── repository/XxxRepository.kt
                                    └── ui/XxxGraph.kt + 页面
```

- 消费方（跨域或 app 壳）一律 `@Inject` 接口，不得触碰 impl 实现细节
- impl 模块的 Hilt Module 通过 `@Binds` 绑定接口到实现
- api 模块可带 `res` 先例：`feature:order:api`（OrderStatus 枚举的 descriptionRes）、`feature:coupon:api`（枚举带 res）

### 4.3 状态管理

所有 ViewModel 使用 `RequestState` 密封类管理异步状态（位于 `:common:utils`）：

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

### 4.4 鉴权流程

1. Retrofit 接口方法加 `@RequireAuthorization` 注解
2. `RequestInterceptor`（`:common:network`）经 `retrofit2.Invocation` tag 检测该注解，通过 `TokenProvider` 契约从 `SessionManager`（`:feature:user:impl`）同步读取 token，注入 `Bearer <token>` 请求头
3. `ResponseInterceptor`（`:common:network`）处理响应：
   - HTTP 401 / 业务码 401 → 经 `TokenProvider.clearToken()` 清 token → 经 `SessionEventNotifier.notifyUnauthorized()` 通知 UI 跳登录页
   - 非 2xx → 映射为 `NetworkException`（含 `@StringRes` 错误文案）
4. `TokenProvider` / `SessionEventNotifier` 是 `:common:network` 的契约接口，由 `:feature:user:impl` 的 `SessionManager` / `SessionEventBus` 通过 `@Binds` 绑定实现
5. 壳层 `MainActivity` 收集 `UserApi.loginEvents`（SharedFlow），处理未登录/登录失效 → 跳转登录页（带去重，避免并发 401 堆叠多个登录页）

> ⚠️ `App.globalRequestAfterCallback` 已不存在（原静态回调在模块化中移除）。新增早期请求路径时勿引用该回调。

### 4.5 缓存策略

Repository 层（各域 impl 模块）实现缓存优先：
- **LaundryRepository / SchoolRepository**（`:feature:laundry:impl`）：三级缓存（内存 → Room → 网络），`SchoolSearchSource` 契约对外暴露
- **CouponRepository**（`:feature:coupon:impl`）：先读 Room 缓存 → 发起网络请求 → 更新缓存
- **其他 Repository**：直接网络请求（无缓存）

### 4.6 分页

使用 Paging 3，`pagingFlow()` 统一封装在 `:common:utils/PagingUtils.kt`（debounce + flatMapLatest + cachedIn）：

- `OrderPagingSource` — 订单列表（`:feature:order:impl`）
- `UserCouponPagingSource` — 用户优惠券（`:feature:coupon:impl`）
- `RechargeRecordPagingSource` — 充值记录（`:feature:payment:impl`）

### 4.7 导航

- 单 Activity 架构，`MainActivity` 通过 `NavHost` 承载所有页面
- 各域暴露 `NavGraphBuilder.xxxGraph` 扩展函数，壳层 `MainActivity` 聚合调用：

```kotlin
NavHost(navController, startDestination = UserRoute.Login.text, ...) {
    shellGraph(navController)          // 壳层留守页面
    userGraph(navController, ...)      // 用户域
    orderGraph(navController)          // 订单域
    paymentGraph(navController, ...)   // 支付域
    laundryGraph(navController)        // 洗衣域
    couponGraph(navController)         // 优惠券域
    divinationGraph(navController)     // 观象台
}
```

- 壳层留守页面（主页壳/订单详情/寄件取件/取件）注册于 `shellGraph`
- 全局入场/出场动画（`shellEnterTransition`/`shellExitTransition`），reduceMotion 时降级短 tween
- 路由常量单一事实来源在各域 `*Route`，壳层 `PageConstant` 仅剩 `Pickup`

### 4.8 启动任务（InitTask）

`InitEngine`（`:common:init`）统一调度启动任务：

```kotlin
// 任务定义（各 impl 模块）
class XxxInitTask @Inject constructor(...) : InitTask() {
    override val taskId = "xxx"
    override val priority = 100        // 越小越先
    override val blocking = true       // true=串行阻塞, false=异步
    override suspend fun execute() { ... }
}

// Hilt @IntoSet 注册（各 impl 模块的 di/ 包）
@Module @InstallIn(SingletonComponent::class)
object XxxInitModule {
    @Provides @IntoSet
    fun provideXxxInitTask(...): InitTask = XxxInitTask(...)
}
```

已接入任务：
- `SessionInitTask`（`:feature:user:impl`，blocking=true，priority=10，预热 token）
- `UpdateInitTask`（`:feature:update`，blocking=false，priority=900，静默检查更新）

---

## 五、API 接口一览

> 所有端点以 `BuildConfig.BASE_URL` 为根。需鉴权的接口使用 `@RequireAuthorization` 注解，路径前缀为 `/web/auth/`。
>
> **接口类位置已分散到各域 api/impl 模块**（不再集中在 `app/network/api/`）。

### 用户模块（UserAccountApi）— 13 个端点

> 接口类位置：`:feature:user:impl/network/api/UserAccountApi.kt`

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/auth/user/captcha/{phoneNumber}` | 否 | 获取短信验证码 |
| POST | `/auth/user/register` | 否 | 用户注册 |
| POST | `/auth/user/login` | 否 | 用户登录 |
| POST | `/auth/user/resetPassword` | 否 | 重置密码（手机号 + 验证码） |
| GET | `/web/auth/user/getUserInfo` | 是 | 获取用户详情 |
| POST | `/web/auth/user/updateUserInfo` | 是 | 更新学校信息 |
| GET | `/web/auth/user/school` | 是 | 获取用户学校 ID |
| GET | `/web/auth/user/getUserByStudentId?studentId=` | 是 | 按学号查用户（Query 参数） |
| POST | `/web/auth/user/bingCampus/{campusCard}` | 是 | 绑定校园卡 |
| POST | `/web/auth/user/unBingCampus` | 是 | 解绑校园卡 |
| POST | `/web/auth/user/avatar` | 是 | 上传头像（Multipart） |
| GET | `/web/auth/user/transactions?page=&pageSize=` | 是 | 钱包流水（分页） |
| PUT | `/web/auth/user/password` | 是 | 修改密码 |

### 观象台设置（DivSettingApi）— 5 个端点

> 接口类位置：`:feature:user:impl/network/api/DivSettingApi.kt`（设置页「解读模型」入口）

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/web/auth/div/settings/api` | 是 | 获取 BYOK 配置（掩码回显） |
| PUT | `/web/auth/div/settings/api` | 是 | 保存 BYOK 配置 |
| DELETE | `/web/auth/div/settings/api` | 是 | 删除 BYOK 配置 |
| POST | `/web/auth/div/settings/api/test` | 是 | 试呼校验 |
| GET | `/web/auth/div/models` | 是 | 平台模型目录 |

### 订单模块（OrderServiceApi）— 10 个端点

> 接口类位置：`:feature:order:impl/network/api/OrderServiceApi.kt`

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

### 优惠券模块（CouponServiceApi）— 4 个端点

> 接口类位置：`:feature:coupon/impl/network/api/CouponServiceApi.kt`

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/web/auth/coupon/allCoupon` | 是 | 所有可领取优惠券 |
| POST | `/web/auth/userCoupon/receiveCoupon/{couponId}` | 是 | 领取优惠券 |
| GET | `/web/auth/userCoupon/available/{orderId}` | 是 | 可用优惠券（支付时） |
| GET | `/web/auth/userCoupon/allCoupons` | 是 | 聚合接口（可领+已领+历史） |

### 充值模块（RechargeApi）— 2 个端点

> 接口类位置：`:feature:payment/impl/network/api/RechargeApi.kt`

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/web/auth/recharge/userRecharge` | 是 | 用户充值 |
| GET | `/web/auth/recharge/list?page=&size=` | 是 | 充值记录（分页） |

### 支付模块（PaymentApi）— 1 个端点

> 接口类位置：`:feature:payment/impl/network/api/PaymentApi.kt`

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/web/auth/payments/payment` | 是 | 订单支付 |

### 洗衣服务模块（LaundryItemsApi）— 1 个端点

> 接口类位置：`:feature:laundry/network/api/LaundryItemsApi.kt`

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/web/laundryItems/all` | 否 | 获取所有洗衣项目 |

### 应用更新模块（AppUpdateApi）— 2 个端点

> 接口类位置：`:feature:update/network/AppUpdateApi.kt`

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/web/app/version` | 否 | 获取最新版本信息 |
| GET | `/web/app/download` | 否 | 获取 APK 预签名下载 URL |

### 学校模块（SchoolApi）— 1 个端点

> 接口类位置：`:feature:laundry/impl/network/api/SchoolApi.kt`

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/web/schools/allName?schoolName=` | 否 | 搜索学校（按名称模糊匹配） |

### 站内信模块（NoticeServiceApi）— 4 个端点

> 接口类位置：`:feature:notice/api/NoticeServiceApi.kt`

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/web/auth/notices?page=&size=` | 是 | 收件箱分页 |
| GET | `/web/auth/notices/unread-count` | 是 | 未读数（我的页角标） |
| POST | `/web/auth/notices/{id}/read` | 是 | 标记已读（幂等） |
| POST | `/web/auth/notices/read-all` | 是 | 全部已读（幂等） |

### 积分模块（PointsServiceApi）— 3 个端点

> 接口类位置：`:feature:points/api/PointsServiceApi.kt`

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/web/auth/points/me` | 是 | 概览（余额/连签/流水） |
| POST | `/web/auth/points/sign-in` | 是 | 签到（幂等，重复返回 `alreadySigned=true`） |
| GET | `/web/auth/points/leaderboard?scope=month` | 是 | 月榜 |

### 观象台模块（DivinationApi）— 7 个端点 + 2 个 SSE

> 接口类位置：`:feature:divination/network/DivinationApi.kt`（SSE 经 `common:network` 的 `SseClient`）

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `web/auth/div/records` | 是 | 建档起卦 |
| GET | `web/auth/div/records?method=&dateFrom=&dateTo=&page=&size=` | 是 | 卦历分页 |
| GET | `web/auth/div/records/{id}` | 是 | 卦例详情 |
| GET | `web/auth/div/today` | 是 | 今日一签 |
| POST | `web/auth/div/records/{id}/feedback` | 是 | 反馈 |
| GET | `web/auth/div/methods` | 是 | 术数方法列表 |
| GET | `web/auth/div/models` | 是 | 平台模型目录 |
| POST | `web/auth/div/records/{id}/interpret`（SSE） | 是 | 流式解读 |
| POST | `web/auth/div/records/{id}/followup`（SSE） | 是 | 流式追问 |

### 游戏模块（GameApi + GameBridgeApi）— 6 个端点

> 接口类位置：`:feature:game/network/GameApi.kt`、`app/network/api/GameBridgeApi.kt`

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `web/auth/game/list` | 是 | 游戏下发列表 |
| GET | `web/auth/game/state` | 是 | 读取游戏存档 |
| PUT | `web/auth/game/state` | 是 | 保存游戏存档 |
| POST | `web/auth/game/score` | 是 | 上报成绩 |
| GET | `web/auth/game/leaderboard` | 是 | 排行榜 |
| GET | `web/auth/game/levels` | 是 | 关卡列表 |

### 复习模块（ReviewApi）— 7 个端点

> 接口类位置：`:feature:review/network/ReviewApi.kt`

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `web/auth/review/materials` | 是 | 创建资料 |
| GET | `web/auth/review/materials?page=&size=` | 是 | 资料分页 |
| POST | `web/auth/review/materials/{id}/cards` | 是 | 建卡 |
| GET | `web/auth/review/cards?materialId=&page=&size=` | 是 | 卡片分页 |
| GET | `web/auth/review/queue` | 是 | 今日队列（FSRS 调度在后端） |
| POST | `web/auth/review/answer` | 是 | 复习评分（rating 1..4） |
| GET | `web/auth/review/stats` | 是 | 复习统计 |

### 订阅模块（SubscriptionApi）— 15 个端点

> 接口类位置：`:feature:subscription/network/SubscriptionApi.kt`

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `web/auth/subscription` | 是 | 新建订阅 |
| PUT | `web/auth/subscription/{id}` | 是 | 更新订阅 |
| DELETE | `web/auth/subscription/{id}` | 是 | 删除订阅 |
| GET | `web/auth/subscription?page=&size=&status=&keyword=` | 是 | 订阅分页 |
| GET | `web/auth/subscription/{id}` | 是 | 订阅详情 |
| POST | `web/auth/subscription/{id}/pause` `/resume` `/cancel` `/finish` | 是 | 状态流转 |
| POST | `web/auth/subscription/{id}/usages` | 是 | 用量打卡 |
| GET | `web/auth/subscription/{id}/usages?page=&size=` | 是 | 明细分页 |
| DELETE | `web/auth/subscription/{id}/usages/{usageId}` | 是 | 删除明细 |
| GET | `web/auth/subscription/reminders?unread=` | 是 | 提醒列表 |
| POST | `web/auth/subscription/reminders/{id}/read` | 是 | 标记提醒已读 |
| GET | `web/auth/subscription/stats` | 是 | 统计 |

### 工具箱模块（ToolboxApi + DeliveryApi）— 13 个端点

> 接口类位置：`:feature/toolbox/network/ToolboxApi.kt`、`DeliveryApi.kt`

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| POST/GET/PUT/DELETE | `web/auth/toolbox/short-codes...` | 是 | 短链 CRUD |
| GET | `web/auth/toolbox/short-codes/{id}/stats` | 是 | 访问统计 |
| GET | `web/auth/toolbox/short-codes/resolve` | 是 | 短码解析 |
| POST | `web/auth/toolbox/delivery/sessions` | 是 | 创建投递会话 |
| GET | `web/auth/toolbox/delivery/sessions/{code}` | 是 | 会话详情 |
| POST | `web/auth/toolbox/delivery/sessions/{code}/close` | 是 | 关闭会话 |
| POST | `web/auth/toolbox/delivery/sessions/{code}/items` | 是 | 发布文本 |
| POST | `web/auth/toolbox/delivery/sessions/{code}/items/file` | 是 | 发布文件（Multipart） |
| POST | `web/auth/toolbox/delivery/sessions/{code}/items/{itemId}/take` | 是 | 取走条目 |
| GET | `web/auth/toolbox/delivery/sessions/{code}/items/{itemId}/content` | 是 | 读取文本内容 |

---

## 六、设计系统（清氧设计系统）

> **唯一约束来源**：[.claude/ui-design-spec.md](.claude/ui-design-spec.md)（含 2026-10-29 DESIGN-SPEC v0.4 覆写）。本节只列高频取值，**冲突以规范为准**。代码位于 `:common:ui/theme/`（`AppDesign.kt` / `Color.kt` / `Theme.kt` / `Type.kt`）。

### 色彩（v0.4 结构层）

| 令牌 | 值 | 用途 |
|------|------|------|
| `background` | `#F5F6F3` | 页面底 |
| `surface` | `#FFFFFF` | 卡片 |
| `surfaceVariant` | `#EFF1EE` | 卡内次级块 |
| `groupBackground` | `#E7EAE4` | 分组容器底 |
| `outline` | `#E6E9E6` | 卡片描边 |
| `hairline` | `#EFF1EE` | 卡内行分隔线（0.5dp） |
| `textPrimary` | `#1C1C1E` | 主文本 |
| `textSecondary` | `#6B6E73` | 次文本 |
| `textTertiary` | `#A9AEA9` | 弱文本 / tab 未选中 |
| `primary` | `#2D9B6A` | 主色（**不改**，玄墨子主题除外） |
| `primaryDeep` | `#1E8C5C` | 按下态 / 渐变暗端 |
| `primarySoft` | `#E8F6EF` | 浅绿容器 / 选中底 |
| `water` / `waterSoft` | `#4A9FD4` / `#DCEFF7` | 次要强调 / 水滴意象 |
| `ongoing` / `ongoingInk` | `#D98A26` / `#8F5B0C` | 进行中 / 橙色小字 |
| `danger` / `dangerInk` | `#C9526B` / `#A8435A` | 异常 / 红色小字 |

### 排版（v0.4 八档，weight 仅 400/500/600/700）

| 档 | 字号 / 行高 / 字重 | 语义 |
|---|---|---|
| `display` | 36sp / 44sp / 700 + tnum | 数据大字（余额、应付金额、取件码） |
| `headline` | 28sp / 36sp / 700 | — |
| `titleLarge` | 22sp / 28sp / 600 | `PageHeader`（内页标题）、`RootTitle`（根页大标题） |
| `title` | 18sp / 24sp / 600 | 卡片标题、区块标题、金额中字 |
| `bodyLarge` | 16sp / 24sp / 400 | 正文 |
| `body` | 14sp / 20sp / 400 | 列表行主文案 |
| `caption` | 12sp / 16sp / 400 | 辅助 / 角标 |
| `captionStrong` | 12sp / 16sp / 500 | — |

- 金额 / 数量 / 取件码 / 订单号**必须** tabular numbers（`fontFeatureSettings = "tnum"`）。
- 一屏只允许一个锚点级大字；标题不参与大字竞争。

### 圆角与阴影

- 圆角：`radiusCard` 16dp（卡片唯一值）· `radiusHero` 20dp · `radiusInner` 12dp · `radiusSmall` 10dp · `radiusFull` 胶囊。
- 层级：卡片 1px `outline` + `level1` 阴影；浮层 / 弹窗 `floatLayer`；hero 无描边靠渐变。
- 按钮：`AppButton` **48dp 高 + 胶囊（`radiusFull`）+ 文字 15sp·600·ls 1sp**；主/次按钮共用。

### 认证页 / 功能页

- 认证页（登录/注册）：品牌渐变（`primary` → `primaryDeep`）+ 毛玻璃卡片。
- 功能页：`background #F5F6F3` + 白卡；共用 `PageHeader`（内页标题 titleLarge 22sp）、`AppCard`、`AppButton`、`AppTabBar`。
- 底部导航：白底 + 4 个 tab（Index / Service / Workbench / UserInfo），选中 `primary`、未选中 `textTertiary`。

### 相关文件（`:common:ui`）

- `common/ui/src/main/java/com/smartwash/common/ui/theme/{Color,Theme,Type,AppDesign}.kt` — 令牌与主题
- `common/ui/src/main/java/com/smartwash/common/ui/components/AppComponents.kt` — PageHeader / AppCard / AppButton / AppTabBar / SettingRow
- `common/ui/src/main/java/com/smartwash/common/ui/components/{InfoSection,InfoRow,PasswordInput,PhoneNumberInput}.kt`
- `common/ui/src/main/java/com/smartwash/common/ui/navigation/ShellRoute.kt` — 壳层路由契约

---

## 七、编码规范

### 7.1 基本规则

- **必须使用中文** — 所有对话、代码注释、提交信息均使用中文
- **提交格式** — `<type>(Android): <中文描述>`（如 `feat(Android): 新增订单详情页面`）
- **一个 commit 对应一个完整功能点**，不要逐文件提交
- 提交前使用 `commit-commands:commit` skill 检查变更范围

### 7.2 新增页面清单

新增页面必须完成以下步骤：

1. 在对应域的 `*Route` 密封类（api 模块）中添加路由常量
2. 在对应域的 `*Graph.kt`（impl 模块）中注册 composable
3. 创建 `*Page.kt` + `*ViewModel.kt`（impl 模块内）
4. ViewModel 使用 `@HiltViewModel` + `@Inject constructor`
5. 异步状态使用 `RequestState`（`:common:utils`）
6. 所有用户可见文本定义在模块内 `strings.xml`，通过 `stringResource()` 引用
7. 遵循清氧设计系统规范（`:common:ui`）

### 7.3 新增 API 接口

1. 在对应域的 `network/api/` 目录下创建/编辑 `*Api.kt`
2. 需要认证的接口加 `@RequireAuthorization`（`:common:network`）
3. 返回值统一使用 `ApiResult<T>`（`:common:utils`）包装
4. 请求体放在 `network/entity/` 对应子目录
5. 响应 VO 放在 `network/vo/` 对应子目录
6. 如需跨域暴露服务，在 api 模块定义契约接口，impl 模块 `@Binds` 实现

### 7.4 字符串规范

- **禁止硬编码** — 所有用户可见文本必须定义在模块内 `res/values/strings.xml`
- Page 中通过 `stringResource(R.string.xxx)` 引用
- ViewModel 中通过 `application.getString(R.string.xxx)` 获取
- 带参数的字符串使用 `%s`、`%d` 占位符
- 常量字符串（SharedPreferences key、日志 TAG）定义在对应常量类中（impl 模块的 `XxxImplConstant.kt`）

### 7.5 状态管理规范

- ViewModel 使用 `MutableStateFlow<T>` → `asStateFlow()` 暴露状态
- 页面使用 `collectAsStateWithLifecycle()` 收集
- 网络请求统一使用 `RequestState`（Idle → Loading → Success/Error）

### 7.6 Compose 硬规则

- **组合期禁止副作用**：Toast、导航、状态回写一律放 `LaunchedEffect`/`SideEffect`，禁止写在 `when(state)` 渲染分支里
- **禁止主线程阻塞 IO**：`runBlocking` 读写 DataStore 已知会阻塞 UI 线程，一律用 suspend/flow
- **LazyColumn 必须给 `key`**；列表参数注意稳定性，昂贵计算用 `remember`
- **catch 协程异常先 rethrow `CancellationException`**，否则取消会被当网络错误

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

# 观象台算法锚点单测
./gradlew feature:divination:test

# 代码检查
./gradlew lint
```

### 构建配置

- BASE_URL 通过 Gradle 属性 `baseUrl` 注入（`app/build.gradle`），由 `BaseUrlModule.kt` 以 `@Named("baseUrl")` 供给；代码中一律读 `BuildConfig.BASE_URL`，**禁止硬编码 URL**；兜底值为无 IP 占位(未注入时请求失败)，生产通过 `-PbaseUrl=https://your-domain.com/` 注入
- `DIVINATION_BASE_URL` 与 BASE_URL 同步注入（观象台 LLM 网关）
- Release: 开启 ProGuard 混淆和资源压缩，签名配置优先读取 Gradle 属性 / 环境变量（`SMART_WASH_STORE_FILE` 等），未配置则构建失败
- Maven 仓库使用阿里云镜像（当前注释，启用需同时取消 `settings.gradle` 与 `build-logic` 两处注释），海外构建需改回 `google()` / `mavenCentral()`
- `usesCleartextTraffic=true`（demo 全局放行明文 HTTP，发版前须按生产地址改为 HTTPS 并移除）
- 构建配置统一 convention plugin（`build-logic`），模块 `build.gradle` 只声明差异依赖

---

## 九、数据模型速查

### 枚举常量

| 枚举类 | 位置 | 值 | 说明 |
|--------|------|-----|------|
| `OrderStatus` | `:feature:order:api` | -2 ~ 7（10 种） | 订单全生命周期状态 |
| `ShowOrderStatus` | `:feature:order:api` | 5 种 | 订单列表 Tab 显示状态 |
| `PickupDeliveryType` | `:feature:order:api` | PICKUP(0), DELIVERY(1) | 取件/寄件类型 |
| `CouponStatus` | `:feature:coupon` | ACTIVE(0), EXPIRED(1), RECEIVE(2) | 优惠券状态 |
| `PaymentType` | `:feature:payment:impl` | PURSE(1), ALI_PAY(2), WECHAT_PAY(3) | 支付方式 |
| `HttpStatusCode` | `:common:utils` | 200, 201, 401, 404, 500 | HTTP 业务状态码 |
| `RequestState` | `:common:utils` | Idle/Loading/Success/Error | 异步状态密封类 |

### 统一响应格式（`:common:utils`）

```kotlin
data class ApiResult<out T>(
    val code: Int,      // 业务状态码
    val message: String, // 提示信息
    val data: T?,        // 数据载荷
)
```

### 分页响应格式（`:common:utils`）

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

完整四端评审清单（含行号与修复方向）见 `docs/code-review-2026-08-28.md` 第二章。Android 端 P0/P1 级：

- **按压反馈全量失效**：`:common:utils/PressFeedbackModifier.kt` 的 `pressScale/pressAlpha` 自建 InteractionSource 未接入 clickable，全项目 31 处调用无效——修复前不要模仿该写法
- **组合期副作用**：6 个页面（PaymentPage、PaySuccessPage、OrderDetailPage、IndexPage、RegisterPage、OrderPage）在 `when(state)` 渲染分支里直接 Toast/回写状态，需迁 `LaunchedEffect`
- **主线程阻塞**：MainActivity/LoginPage/SettingPage 经 `runBlocking` 读写 DataStore，ANR 风险
- **环境不对齐**：BASE_URL 兜底为无 IP 占位(未注入时请求失败,生产须通过 `-PbaseUrl=` 注入）+ `usesCleartextTraffic` 全局放行；Token 明文存 DataStore 且 backup_rules 未排除
- **网络层健壮性**：`:common:network` 的 ResponseInterceptor 空 body NPE、`peekBody(Long.MAX_VALUE)` 双重解析、`:common:database` 的 Room 无 migration、缓存写入无事务
- **分层破洞**：LaundryViewModel/CouponViewModel 直连 Api 绕过 Repository；订单分页手写 Map 与 Paging 3 双轨并存
- **RechargePage 组合期副作用**：`RechargePage.kt` 的金额选择/支付状态分支中存在写在 `when(state)` 渲染分支里的副作用，待独立任务修复
- **测试覆盖**：当前 19 个测试文件，分布在 app（ExampleUnitTest、ParamValidUtilsTest、EntityMappersTest×2、SessionEventBusTest、GameThemeBridgePolicyTest）、common:init（InitEngineTest）、common:utils（DeepLinkParserTest）、feature:divination（5 个算法锚点单测）、feature/game（GameConstantTest、GameDeepLinkModuleTest）、feature/payment:impl（RechargeAmountValidatorTest）、feature/toolbox（ToolboxDeepLinkModuleTest）、feature/update（ApkDownloadWorkerNotificationTest、ApkInstallerTest）；优先给 ResponseInterceptor、OrderStatus 映射补 JVM 单测

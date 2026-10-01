# :feature:user:api

## 模块身份

- **Gradle 坐标**: `:feature:user:api`
- **职责**: 用户域对外服务契约——登录态、用户信息、登录事件、路由常量的纯 Kotlin 接口与领域模型，零 Hilt 运行时 / 零 Compose / 零 Android UI 依赖。

## 包结构

```
com.smartwash.feature.user.api
├── UserApi.kt                # 服务契约接口（isLogin / getUserInfo / loginEvents）
├── UserRoute.kt              # 路由常量密封类（Login/Register/UserInfo/UpdateUserInfo/Setting）
└── model/
    ├── LoginEvent.kt         # 登录事件密封类（LoggedIn / LoggedOut）+ LogoutReason 枚举
    └── UserInfo.kt           # 用户信息领域模型（含嵌套 School）
```

## 公开 API

| 符号 | 类型 | 说明 |
|------|------|------|
| `UserApi` | `interface` | 用户域服务契约。`isLogin(): Boolean`（同步读 token）、`suspend getUserInfo(): UserInfo?`（内存缓存 + 网络回源）、`val loginEvents: SharedFlow<LoginEvent>`（登录态变化广播） |
| `UserRoute` | `sealed class` | 用户域路由常量。`Login`/`Register`/`UserInfo`/`UpdateUserInfo`/`Setting`，`text` 值对齐 app 壳 `PageConstant`/`HomePageConstant` |
| `LoginEvent` | `sealed class` | 登录事件。`LoggedIn`（无载荷）、`LoggedOut(reason: LogoutReason)` |
| `LogoutReason` | `enum` | 登出原因：`LOGOUT`（主动登出）、`NEED_LOGIN`（未登录拦截）、`UNAUTHORIZED`（401 失效） |
| `UserInfo` | `data class` | 用户信息领域模型。`phoneNumber`/`studentId`/`campusCard`/`balance`/`avatar`/`school`，嵌套 `School(schoolId/schoolName/location/lockerCount)` |

**契约要点**:
- `loginEvents` 使用 `SharedFlow`（非 StateFlow），登录态变化是离散事件，订阅不回放历史
- `getUserInfo()` 带内存缓存，登出时失效；未登录返回 null 不抛错
- `UserInfo` 不带 `@Keep`，不直接参与 Gson 反序列化（映射在 impl 层完成）
- `UserRoute.text` 是单一事实来源，feature 不得反向依赖 app 壳 `PageConstant`

## 依赖关系

### 本模块依赖
- `kotlinx.coroutines.core`（SharedFlow / Flow 契约）

### 谁依赖本模块
- `:feature:user:impl`（UserApiImpl 实现 UserApi）
- `:feature:order:impl`（用户中心订单计数跳转，T6.3）
- `:feature:payment:impl`（用户中心入口）
- `:feature:laundry:impl`（用户中心入口）
- `:feature:coupon:impl`（用户中心入口）
- `:app` 壳（401 跳转、首页绑定引导、用户中心）

## 内部约定

1. **零依赖原则**: 不引 Hilt、不引 Compose、不引任何 feature-impl 或 app 壳
2. **模型即契约**: 字段名对齐消费方现有访问路径（如 `UserInfo.School.schoolId`），改字段须同步所有消费方
3. **路由单一来源**: 用户域页面路由文本以 `UserRoute.text` 为准，app 壳 `PageConstant.Login` 等委托取值
4. **事件面精简**: 网络层 NeedLogin/Unauthorized 归并为 `LoggedOut(reason)` 单一事件，保留 `LogoutReason` 区分度

## 已知坑

- **DataStore 键名不可改**: `UserImplConstant.TOKEN = "user_token"` / `USER_ROLE = "user_role"` 沿用 app 时期取值，改键名等于丢登录态（需数据迁移）
- **school 字段容错**: 后端对未绑定学校用户返回空 `schoolVo`（字段全 null），`UserInfoVo.toUserInfo()` 映射时 `schoolId` 须落 `-1L` 对齐原 `getUserSchoolId` 空值语义，否则触发 NPE
- **预热时序**: `isLogin()` 是同步读内存缓存，`SessionInitTask`（impl 层）预热完成前 token 可能为空——启动早期判断登录态依赖预热时序保证
- **SharedFlow 无回放**: 订阅 `loginEvents` 时不会收到历史事件，页面按需调 `isLogin()`/`getUserInfo()` 取现值

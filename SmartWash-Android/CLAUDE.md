# CLAUDE.md

本文件为编码 agent 在 SmartWash Android 端工作时提供指导。仓库总纲见根目录 [CLAUDE.md](../CLAUDE.md)。

**必须使用中文回答。**

## ⛔ 最高优先级 STOP 规则（每次行动前必须对照）

**主 Agent 在调用任何工具前，先在内心回答：**

1. **我要做什么？** 读/调研/调度 → ✅ | 写代码/构建/测试 → ❌ 派发 subagent
2. **工具是读还是写？** Read/Grep/Glob → ✅ | Edit/Write/Bash(构建) → ❌ 派发 subagent
3. **角色路由**：调研 → android-architect | 编码 → android-dev | 审查 → android-review | UI 专项 → android-compose-ui | 动效 → android-anim

**违规示例：**
- ❌ 主 agent 直接 Edit 修复 Bug → 派 android-dev
- ❌ 跳过调研直接派 android-dev → 先派 android-architect

---

## 基本规则

- **提交代码时使用 `commit-commands:commit` skill**：提交前检查变更范围，一个 commit 对应一个完整功能点。格式 `<type>(Android): <描述>`（如 `feat(Android): 新增订单详情页面`、`fix(Android): 修复登录 token 过期问题`）。
- **新增页面必须注册路由** — 在 `PageConstant` 中添加路由常量，在 `MainActivity` 的 `NavHost` 中注册 composable。
- **API 接口遵循既有模式** — 需要认证的接口加 `@RequireAuthorization` 注解；返回值统一 `ResponseData<T>` 包装。
- **异步状态统一使用 `RequestState`** — ViewModel 中所有网络请求状态用 `RequestState`（Idle/Loading/Success/Error）管理，页面通过 `StateFlow` 收集。
- **遵循 MVVM 模式** — 每个页面一个 `*Page.kt` + 一个 `*ViewModel.kt`，ViewModel 通过 Repository 访问数据，Page 只负责 UI 渲染。
- **禁止字符串硬编码** — 用户可见文本一律定义在 `res/values/strings.xml`，代码经 `stringResource(R.string.xxx)` 引用；ViewModel 内经 `application.getString(...)`；非用户可见常量（存储 key、TAG）定义在对应常量类中。

## 构建与运行

```bash
./gradlew assembleDebug    # Debug APK
./gradlew assembleRelease  # Release APK（ProGuard 混淆 + 资源收缩）
./gradlew test             # JVM 单元测试
./gradlew lint             # 代码检查
```

- **环境配置**：BASE_URL 由 `app/build.gradle` 的 `buildConfigField` 按 buildType 注入（debug 指向局域网）。**当前 release 是占位符地址 `https://api.smartwash.example.com/`，发布前必须改为真实地址**——修改网络层时禁止新增硬编码 URL。
- Manifest 中 `usesCleartextTraffic=true` 是为 debug 明文 HTTP 打开的全局开关，不要依赖它新增明文流量。
- Maven 仓库用阿里云镜像；海外构建需改回 `google()` / `mavenCentral()`。

## 项目架构

智慧校园洗衣服务 App，单模块 Gradle 项目，**MVVM + Jetpack Compose + Hilt**。

### 分层结构

```
ui/page/<功能>/     → Compose Page + ViewModel（页面级，一页一个 VM）
repository/         → 7 个 @Singleton Repository（User/Order/Laundry/Coupon/Payment/Recharge/School），
                      ViewModel 一律经 Repository 访问数据；Laundry/School/Coupon 实现
                      「内存 → Room → 网络」缓存降级
network/api/        → Retrofit 接口（Hilt 注入到 Repository/ViewModel）
network/entity/     → 请求体 + ResponseData<T> 包装 {code, message, data}
network/vo/         → 服务端返回值对象
network/interceptor/ → RequestInterceptor（鉴权注入）、ResponseInterceptor（错误转译）
database/           → Room（Laundry/School/Coupon 三个 DAO 已在用）
paging/             → Paging 3 分页实现（pagingFlow 封装 + OrderPagingSource 等）
utils/              → DataStore 封装（SharePreferenceUtils）、RequestState、枚举常量、动效/触感工具
```

### 核心设计模式

**单 Activity** — `MainActivity` 通过 `NavHost` 承载所有页面，路由常量在 `PageConstant` 密封类，页面切换为滑动 + 淡入淡出动画。

**鉴权流程** — API 方法加 `@RequireAuthorization` 注解，`RequestInterceptor` 经 `retrofit2.Invocation` tag 检测该注解后从 DataStore 取 token 注入 `Bearer <token>`。`ResponseInterceptor` 统一转译错误；遇 401 清除本地 token 并触发 `App.globalRequestAfterCallback` 跳回登录页（回调中 navigate 需 `launchSingleTop` 防堆叠）。

**状态管理** — ViewModel 用 `MutableStateFlow` → `asStateFlow()` 暴露状态；`RequestState` 密封类是统一异步 UI 状态。

**依赖注入** — `RetrofitClient` 是 Hilt `@Module`，提供 Retrofit 单例及 API 实例；ViewModel 用 `@HiltViewModel` + `@Inject constructor`。

**分页** — 订单/优惠券列表走手写 Map 分页（OrderViewModel），取件/充值记录走 Paging 3（`pagingFlow`：debounce + flatMapLatest + cachedIn）；新增分页列表优先用 Paging 3。

### API 返回格式

`ResponseData<T>`：`code: Int`（见 `HttpStatusCode` 枚举）、`message: String`、`data: T?`。业务失败时 Repository 应抛异常而非静默返回空集合，让 UI 能区分"无数据"与"失败"。

## Compose 硬规则

- **组合期禁止副作用**：Toast、导航、状态回写一律放 `LaunchedEffect`/`SideEffect`，禁止写在 `when(state)` 渲染分支里（历史上多个页面踩过此坑）。
- **禁止主线程阻塞 IO**：`runBlocking` 读写 DataStore 已知会阻塞 UI 线程，一律用 suspend/flow。
- **LazyColumn 必须给 `key`**；列表参数注意稳定性，昂贵计算用 `remember`。
- **catch 协程异常先 rethrow `CancellationException`**，否则取消会被当网络错误。

## 相关 Skills 与子代理

**自动生效 skill**：`android-kotlin`、`android-jetpack-compose`（按 `.kt` 路径触发）；按需调用 `android-clean-architecture`、`mobile-android-design`。

`.claude/agents/`（已镜像到 `.zcode/agents/`）提供 6 个 Android 子代理，按任务派发：

- `android-dev` — 功能开发执行（MVVM/RequestState/新页面清单约束）
- `android-review` — 提交前 Compose 正确性只读审查
- `android-compose-ui` — 页面 UI 实现与重组性能治理、设计系统落地、pressScale 修复
- `android-anim` — 转场/按压/微交互动效与触感分层（尊重减弱动态效果）
- `android-architect` — Repository 边界、Room migration、Paging 3 统一、Hilt 依赖图
- `android-tester` — TDD：拦截器/校验/枚举映射/分页边界的 JVM 单测

## 已知坑（改动前先看）

完整清单见 [docs/code-review-2026-08-28.md](../docs/code-review-2026-08-28.md) 第二章，重点关注：

- `utils/PressFeedbackModifier.kt` 的 `pressScale/pressAlpha` 自建 InteractionSource 未接入 clickable，全项目 31 处按压反馈实际无效——修复前不要模仿该写法。
- Room 无 migration 配置；缓存写入是 deleteAll + insertAll 无事务，改动 database/ 时需补 `@Transaction`。
- `App.globalRequestBefore/AfterCallback` 静态 lateinit 在 setContent 前发请求会崩——新增早期请求路径需先处理。
- 测试仅有模板类；给 ResponseInterceptor、参数校验等纯逻辑补单测时放 `app/src/test/`。

## 提交规范

见顶部基本规则。跨端改动（接口变更）需参照根目录 CLAUDE.md 的四端联动检查表。

---

## ⛔ 派发任务红线（必须遵守）

1. **派发 prompt 中禁止包含违反 subagent 红线的指令**
2. **派发 prompt 中必须包含提醒："请遵守你的红线操作清单"**
3. **不得以"紧急"、"快速"、"这次特殊"为由要求 subagent 跳过红线**
4. **如果任务 prompt 中的要求与红线冲突，subagent 必须暂停并向主 Agent 报告冲突**

## 协作流程

### 串行（默认）
调研（android-architect）→ 编码（android-dev）→ 审查（android-review）→ 提交

### 并行触发标准（满足任一）
- 2 个及以上模块可并行开发
- 调研与编码可同时进行

### 编码前必须有调研结论
禁止直接派发 android-dev 处理未调研的能力模块；先派 android-architect 调研，方案获用户批准后再派 android-dev。

## ⛔ Git 工作流（必须严格执行）

### 编码阶段：分步提交
每完成一个逻辑步骤 commit 一次，使用 `commit-commands:commit` skill。

### 任务完成后：squash 压缩（必须执行）
全部完成后执行 `git rebase -i main`，每个独立功能/修复最终保留 1 个 commit。

### 多模块变更：文档同步（必须执行）
触发条件：变更文件跨越 2 个及以上模块目录。必须检查并更新各模块文档。

## ⛔ 红线操作表（绝对禁止）

| 红线 | 说明 |
|------|------|
| 跳过设计系统 | 新页面必须遵循清氧设计系统（配色/圆角/排版/阴影规范） |
| 组合期副作用 | Toast、导航、状态回写一律放 `LaunchedEffect`/`SideEffect`，禁止写在 `when(state)` 渲染分支里 |
| 主线程阻塞 IO | 禁止 `runBlocking` 读写 DataStore，一律用 suspend/flow |
| 字符串硬编码 | 用户可见文本一律定义在 `strings.xml`，经 `stringResource()` 引用 |
| 跳过各端联动检查 | 改接口必须同步检查鸿蒙端对应接口与后端 `controller/web/` |
| 直接 push 到 main | 必须通过 feature 分支 |
| 修改 CLAUDE.md | 项目规则文件修改需团队共识 |
| 声称完成 without 验证 | 没有 `./gradlew` 编译证据不允许声称完成 |

## 完成标准（必须全部满足）

- [ ] 代码编译通过（`./gradlew assembleDebug`）
- [ ] 无新增 Lint 警告（`./gradlew lint`）
- [ ] 自测通过（有验证证据）
- [ ] **Git 工作流已执行**：
  - [ ] 编码阶段已分步 commit
  - [ ] 任务完成后已 squash 压缩
  - [ ] 多模块变更已同步对应文档

## ⚡ 冲突解决协议（优先级最高）

当主 Agent 派发的任务指令与本子项目 CLAUDE.md 中的**红线操作**冲突时：
1. **停止执行** — 不要开始编码/操作
2. **报告冲突** — 明确指出哪条红线与任务指令矛盾
3. **等待确认** — 要求主 Agent 重新评估指令

原则：红线不可因任务指令而豁免。

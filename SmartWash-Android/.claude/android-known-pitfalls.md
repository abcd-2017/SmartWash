# Android 端已知坑

完整清单见 [.claude/docs/code-review-2026-08-28.md](../../.claude/docs/code-review-2026-08-28.md) 第二章，重点关注：

- `utils/PressFeedbackModifier.kt` 的 `pressScale/pressAlpha` 自建 InteractionSource 未接入 clickable，全项目 31 处按压反馈实际无效——修复前不要模仿该写法。
- Room 无 migration 配置；缓存写入是 deleteAll + insertAll 无事务，改动 database/ 时需补 `@Transaction`。
- ⚠️ `App.globalRequestBefore/AfterCallback` 已不存在（原静态 lateinit 回调在模块化中移除，鉴权失败改走 `SessionEventNotifier` 契约）；新增早期请求路径时勿引用该回调。
- 测试除模板类 `ExampleUnitTest` 外，还有观象台四套算法内核的锚点单测（`feature:divination/src/test/.../liuren|liuyao|meihua|qimen/*AnchorTest.kt`）；给拦截器、参数校验等纯逻辑补单测时放对应模块的 `src/test/`。
- ⚠️ **`@HiltViewModel` 必须用 `hiltViewModel()` 获取**：全项目 27 个 ViewModel 均为 `@HiltViewModel` + `@Inject constructor`（无无参构造），使用标准 `viewModel()` 会因反射找不到无参构造抛 `NoSuchMethodException`，进入对应页面时 FATAL EXCEPTION。新建 ViewModel 时统一用 `androidx.hilt.navigation.compose.hiltViewModel`，禁止 `androidx.lifecycle.viewmodel.compose.viewModel`。
- ⚠️ **RechargePage 组合期副作用**（独立任务，本任务不修）：`RechargePage.kt` 的金额选择/支付状态分支中存在写在 `when(state)` 渲染分支里的副作用，违反 Compose 硬规则，待独立任务修复。

# Android 端已知坑

完整清单见 [docs/code-review-2026-08-28.md](../../docs/code-review-2026-08-28.md) 第二章，重点关注：

- `utils/PressFeedbackModifier.kt` 的 `pressScale/pressAlpha` 自建 InteractionSource 未接入 clickable，全项目 31 处按压反馈实际无效——修复前不要模仿该写法。
- Room 无 migration 配置；缓存写入是 deleteAll + insertAll 无事务，改动 database/ 时需补 `@Transaction`。
- ⚠️ `App.globalRequestBefore/AfterCallback` 已不存在（原静态 lateinit 回调在模块化中移除，鉴权失败改走 `SessionEventNotifier` 契约）；新增早期请求路径时勿引用该回调。
- 测试当前 19 个文件，除模板类 `ExampleUnitTest` 与观象台五套算法锚点单测外，还覆盖 InitEngine、DeepLinkParser、Game 常量/深链、充值金额校验、更新下载/安装器等；给拦截器等纯逻辑补单测时放对应模块的 `src/test/`（共享规则：新增测试文件不入库）。
- **孤儿目录**：`feature/ai`、`feature/lostfound`、`common/security` 只有历史 `build/` 残留，无源码/无 `build.gradle`/未被 `settings.gradle` include，**不是模块**，不要在文档或代码中引用。
- ⚠️ **`@HiltViewModel` 必须用 `hiltViewModel()` 获取**：全项目 27 个 ViewModel 均为 `@HiltViewModel` + `@Inject constructor`（无无参构造），使用标准 `viewModel()` 会因反射找不到无参构造抛 `NoSuchMethodException`，进入对应页面时 FATAL EXCEPTION。新建 ViewModel 时统一用 `androidx.hilt.navigation.compose.hiltViewModel`，禁止 `androidx.lifecycle.viewmodel.compose.viewModel`。
- ⚠️ **RechargePage 组合期副作用**（独立任务，本任务不修）：`RechargePage.kt` 的金额选择/支付状态分支中存在写在 `when(state)` 渲染分支里的副作用，违反 Compose 硬规则，待独立任务修复。
- ⚠️ **FileProvider 授权配置只此一份**：`${applicationId}.fileprovider` 的 `file_paths.xml` 唯一权威来源在 `:feature:update:core`（`res/xml/`）。app 模块曾残留一份同名旧文件，资源合并时 app 层覆盖 library 层，导致 filesDir/update 下的 APK 匹配不到 root，下载完成通知构建失败被误报为「下载失败」（2026-11-17 修复）。往 cacheDir/filesDir 新增需经 FileProvider 授权的消费方时，在 `:feature:update:core` 的 file_paths.xml 同步扩项，禁止在 app 层另立同名文件。
- ⚠️ **PullToRefreshBox 的 `isRefreshing` 必须在刷新全程翻转**：M3 `PullToRefreshBox` 松手过阈值后内部 `startRefresh()`，指示器复位的唯一途径是参数翻转回 false（库内无人替你 `endRefresh()`）。`isRefreshing = Loading && 数据非空` 这类门控在空态页恒 false → 下拉松手后转圈永卡（2026-11-18 订单列表/游戏中心/复习队列/用户信息四处修复）。新列表页一律用 `refreshSession` 会话标志模式：`onRefresh` 置 true，`LaunchedEffect(加载状态)` 在本轮加载完成/失败时复位（参照 SubscriptionListPage / OrderPage）。
- ⚠️ **`verticalScroll` 给子项传无限高约束，子项 `fillMaxSize` 失效**：滚动容器内要「铺满视口并垂直居中」必须用 `BoxWithConstraints` 取视口高 + `heightIn(min = maxHeight)`，且 **`BoxWithConstraints` 必须放在 scroll 外层**——滚动 modifier 包在它外面时它收到的约束已是无限高，`maxHeight` 变 Infinity 使 `heightIn` 失效（2026-11-18 订单列表空态修复，实机复测抓过这个错）。空态/错误态分支需保留滚动包装——PullToRefreshBox 的下拉手势依赖子项可滚动派发嵌套滚动，非滚动组件拉不动。

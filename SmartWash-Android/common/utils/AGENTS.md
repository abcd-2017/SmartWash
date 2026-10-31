# common:utils 模块

## 模块身份
- **Gradle 坐标**: `common:utils`
- **职责**: 通用工具函数集合（动效、常量、校验、触感、DataStore）

## 包结构
```
com.smartwash.common.utils
├── model/                    — 原 common:model 的数据契约
│   ├── ApiResult.kt          — 统一响应信封 {code, message, data}
│   ├── PageData.kt           — 分页响应 {records, total, size, current}
│   ├── HttpStatusCode.kt     — HTTP 业务状态码枚举
│   └── RequestState.kt       — 异步状态密封类
├── deeplink/                 — 深链解析契约（纯 Kotlin，零路由知识、零 feature 依赖）
│   ├── DeepLinkAction.kt     — 解析结果密封类（NativeRoute/WebViewUrl/Unknown）
│   ├── DeepLinkParser.kt     — 纯函数解析器：内建仅 http(s)/smartwash://web，其余 host 交 routeLookup
│   └── DeepLinkContributor.kt— 贡献者契约（fun interface）：各域 impl @IntoSet 贡献 host → 路由工厂
├── AnimationUtils.kt       — 动效工具函数
├── AppConstant.kt          — 应用常量（APP_NAME, TOKEN_KEY 等）
├── BitmapUtil.kt           — ZXing 二维码生成
├── HapticUtils.kt          — 触感反馈工具
├── PagingUtils.kt          — pagingFlow() 扩展
├── ParamValidUtils.kt      — 手机号等格式校验
├── PressFeedbackModifier.kt — 按压反馈 Modifier
└── SharePreferenceUtils.kt — DataStore 封装
```

## 公开 API
- `AppConstant` — 全局常量访问点
- `SharePreferenceUtils` — DataStore 的 suspend / 阻塞双模式读写
- `PressFeedbackModifier` — 按压缩放动效 Modifier 扩展
- `PagingUtils.pagingFlow()` — Flow 分页扩展（debounce + flatMapLatest + cachedIn）
- `ApiResult<T>` — 后端响应包装，配合 Retrofit Response 使用
- `PageData<T>` — 分页列表响应
- `HttpStatusCode` — 业务码枚举（200/401/500 等）
- `RequestState<T>` — sealed class: Idle / Loading / Success(data) / Error(exception)
- `deeplink.DeepLinkParser` — 深链解析（全程不抛异常；host→路由知识经 routeLookup 注入）
- `deeplink.DeepLinkAction` — 解析结果三态密封类
- `deeplink.DeepLinkContributor` / `DeepLinkRouteFactory` — 贡献者契约（各域 impl `@Provides @IntoSet` 注册，模式同 common:init 的 InitTask）

## 依赖关系
- **依赖**: Android SDK、DataStore、ZXing、Hilt（仅 @Singleton 标注）、androidx.annotation
- **被依赖**: common:network、common:ui 及所有 feature 模块（含原 common:model 的数据契约）

## 内部约定
- 工具类以 object / top-level 函数组织，禁止有状态单例
- DataStore 操作必须提供 suspend 版本，阻塞版本标注 @Blocking
- 常量统一在 AppConstant 中定义，禁止散落魔法字符串
- model 子包：所有模型类必须是 immutable data class / sealed class
- model 子包：禁止引入 Android 框架依赖（保持可单元测试）
- model 子包：ApiResult 的 `code` 字段与后端契约严格对齐（200 = 业务成功）

## 已知坑
- ⚠️ `PressFeedbackModifier` 未接入 clickable，单独使用按压反馈无效，需配合 `Modifier.clickable` 使用
- DataStore 阻塞读取（runBlocking）不可在主线程调用
- model 子包：新增字段时注意 Retrofit Gson 反序列化的 null 安全
- model 子包：RequestState.Error 的 exception 可能为 null（需处理未知错误场景）

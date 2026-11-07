# common:network 模块

## 模块身份
- **Gradle 坐标**: `common:network`
- **职责**: 网络基础设施（Retrofit/OkHttp 配置、Token 管理、拦截器）

## 包结构
```
com.smartwash.common.network
├── NetworkModule.kt              — Hilt 模块（提供 Retrofit/OkHttp/Api 单例）
├── TokenProvider.kt              — Token 获取/清除契约接口
├── TokenRefresher.kt             — 双 token 续签契约接口（401 时单飞刷新）
├── SessionEventNotifier.kt       — 登出事件通知契约接口
├── annotation/
│   └── RequireAuthorization.kt  — 鉴权注解
├── exception/
│   └── Exceptions.kt            — NetworkException（含 @StringRes 错误文案）
└── interceptor/
    ├── RequestInterceptor.kt    — 自动注入 Bearer Token
    └── ResponseInterceptor.kt   — 401 续签重试/登录失效处理、错误映射
```

## 公开 API
- `TokenProvider` — 实现该接口以提供 Token（由 app 壳层实现）
- `TokenRefresher` — 实现该接口提供 401 续签（`refreshBlocking()` 阻塞、单飞共享结果，由 user:impl 实现）
- `SessionEventNotifier` — 实现该接口接收 401 登出事件
- `@RequireAuthorization` — 标记需要鉴权的 API 接口方法

## 依赖关系
- **依赖**: Retrofit、OkHttp、Hilt、common:utils
- **被依赖**: 所有 feature:api 模块（通过 Retrofit Api 接口）

## 内部约定
- ⚠️ **双契约模式**: TokenProvider + SessionEventNotifier 必须由壳层注入实现，模块本身只定义接口
- API 接口方法需标注 `@RequireAuthorization` 才会自动携带 Bearer Token
- RequestInterceptor 通过 Invocation tag 检测注解，未标注不注入 Token
- 401 响应（HTTP/业务码两通道）由 ResponseInterceptor 拦截：非 /auth/ 前缀且未重试过的请求先经
  `TokenRefresher.refreshBlocking()` 单飞续签，成功换新 token 重试一次（okhttp tag 打已重试标记防死循环），
  失败/认证接口自身 401/重试再 401 → 清 token + SessionEventNotifier 通知跳登录

## 已知坑
- 并发 401 可能触发多次登出跳转（需在上层做去重）
- NetworkException 的 @StringRes 文案需确保 string resource 存在

# common:model 模块

## 模块身份
- **Gradle 坐标**: `common:model`
- **职责**: 跨模块共享的基础数据模型与状态封装

## 包结构
```
com.smartwash.common.model
├── ApiResult.kt           — 统一响应信封 {code, message, data}
├── PageData.kt            — 分页响应 {records, total, size, current}
├── HttpStatusCode.kt      — HTTP 业务状态码枚举
└── RequestState.kt        — 异步状态密封类
```

## 公开 API
- `ApiResult<T>` — 后端响应包装，配合 Retrofit Response 使用
- `PageData<T>` — 分页列表响应
- `HttpStatusCode` — 业务码枚举（200/401/500 等）
- `RequestState<T>` — sealed class: Idle / Loading / Success(data) / Error(exception)

## 依赖关系
- **依赖**: 无项目依赖（纯 Kotlin 数据类）
- **被依赖**: 所有 feature:api + feature:impl 模块

## 内部约定
- 所有模型类必须是 immutable data class / sealed class
- 禁止在此模块引入 Android 框架依赖（保持可单元测试）
- ApiResult 的 `code` 字段与后端契约严格对齐（200 = 业务成功）

## 已知坑
- 新增字段时注意 Retrofit Gson 反序列化的 null 安全
- RequestState.Error 的 exception 可能为 null（需处理未知错误场景）

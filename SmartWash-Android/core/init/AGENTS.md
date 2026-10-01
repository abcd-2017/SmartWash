# core:init 模块

## 模块身份
- **Gradle 坐标**: `core:init`
- **职责**: 应用启动任务调度引擎，提供声明式任务注册、拓扑排序、超时保护

## 包结构
```
com.smartwash.core.init
├── InitTask.kt            — 启动任务契约
├── InitEngine.kt          — 拓扑排序 + 调度引擎
└── InitTaskRegistry.kt    — Hilt @IntoSet 注册表
```

## 公开 API
- `InitTask` — 实现该接口定义启动任务（taskId/priority/dependencies/blocking/timeoutMs/execute）
- `InitEngine.runAll()` — 触发所有已注册任务执行
- `InitTaskRegistry` — Hilt 自动收集 `@IntoSet` 注入的 InitTask 集合

## 依赖关系
- **依赖**: 仅 Android SDK + Hilt + Kotlin Coroutines
- **被依赖**: 所有 feature 模块通过实现 InitTask 注册启动任务

## 内部约定
- ⚠️ **零项目依赖**：禁止引入任何业务模块（common:*、feature:*），保持纯基础设施层
- 任务按 `priority` 升序排列，同 priority 按依赖拓扑排序
- `blocking = true` 的任务串行执行，`false` 的任务 launch 并发
- 超时任务记录日志但不阻塞后续流程（fail-open）

## 已知坑
- 不要在 InitTask 中执行 UI 相关操作（Application.onCreate 阶段）
- 避免在 InitTask 中同步等待其他 blocking 任务，可能死锁

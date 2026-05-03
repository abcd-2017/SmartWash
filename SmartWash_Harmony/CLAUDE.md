# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指导。

## 基本规则

- **必须使用中文回答** — 所有对话、代码注释、提交信息均使用中文。
- **提交代码时使用 `commit-commands:commit` skill**：每次提交前通过该 skill 检查变更范围、生成规范的 commit message，确保一个 commit 对应一个完整的功能点而非单个文件。提交信息格式：`<type>(Harmony): <描述>`（如 `feat(Harmony): 新增订单详情页面`、`fix(Harmony): 修复登录 token 过期问题`），使用中文描述变更内容。
- **优先使用 `@ComponentV2`** — 新组件使用 V2 版本，配合 `@Param`、`@Local`、`@Event` 等新装饰器。
- **新增页面必须注册路由** — 在 `entry/src/main/resources/base/profile/router_map.json` 中注册路由映射，页面对外暴露 `@Builder` 函数。
- **API 接口遵循既有模式** — 需要认证的接口 URL 前缀使用 `/web/auth/`，返回值统一使用 `BaseResponse<T>` 包装，通过 `HttpUtil` 调用。
- **导航统一使用全局 NavPathStack** — 所有页面跳转通过 `PathStackUtil.ets` 中的全局 `pathStack` 操作，禁止直接使用 `@ohos.router`。
- **数据存储使用 `@kit.ArkData` preferences** — 全局存储通过 `StoreageUtil.ets` 封装，不要直接操作 raw API。

## 构建与运行

```bash
# 安装依赖
ohpm install

# 通过 DevEco Studio 构建运行（推荐）
# Build → Run 'entry' 或直接点击运行按钮

# 通过命令行构建（需要配置 hvigorw）
hvigorw assembleHap

# 运行本地单元测试
hvigorw test

# Lint 检查
code-linter --fix
```

## 项目架构

智慧校园洗衣服务 App（鸿蒙端），基于 **HarmonyOS NEXT（API 12+）Stage 模型**，使用 **ArkTS + ArkUI 声明式框架**。

### 分层结构

```
entry/src/main/ets/
  entryability/       → EntryAbility（主 UIAbility，应用入口）
  pages/              → 页面组件（每个页面暴露 @Builder 函数）
  view/               → 可复用 UI 组件
  network/
    api/              → API 函数（按业务域拆分，返回 Promise<BaseResponse<T>>）
    entity/           → 请求体 DTO
    vo/               → 响应值对象 DTO
    Axios.ets         → Axios 实例 + 请求/响应拦截器
    BaseResponse.ets  → 通用响应包装接口 {data, code, message}
    HttpUtil.ets      → GET/POST 封装（单例）
  constant/           → 枚举、状态常量、通用常量
  utils/              → 存储、日志、时间、路径栈工具
```

### 核心设计模式

**Stage 模型 + NavPathStack 路由** — `EntryAbility` 加载 `pages/Index` 作为根页面。`Index` 根据 token 和学校信息判断初始页（Login / AddSchool / Home）。`router_map.json` 注册 15 条命名路由，页面通过全局 `pathStack` 跳转。

**鉴权流程** — Axios 请求拦截器检测 URL 是否以 `/web` 开头，是则自动从 `@kit.ArkData` preferences 读取 token 并注入 `Authorization: Bearer <token>`。响应拦截器遇到 401 时弹出提示并跳转回登录页。

**全局状态** — 使用 `@StorageLink('userLoginFlag')` 监听登录状态变化，跨页面共享登录态。

**HTTP 请求链路** — `页面 -> HttpUtil.get/post<T>() -> Axios 实例（含拦截器） -> errorHandle 钩子 -> BaseResponse<T>`。Axios 请求拦截器注入 token，响应拦截器统一处理 401 和网络错误。

**页面组件模式** — 每个页面是一个 `@Component` 或 `@ComponentV2`，被包含在 `NavDestination` 中，通过对应的 `@Builder` 函数暴露给路由系统。

### API 返回格式

所有接口返回数据统一使用 `BaseResponse<T>` 接口：

```typescript
interface BaseResponse<T> {
  data: T | null    // 数据载荷
  code?: number     // 业务状态码
  message?: string  // 提示信息
}
```

### 依赖说明

- HTTP 客户端：`@ohos/axios` v2.2.6
- 测试框架：`@ohos/hypium` + `@ohos/hamock`
- SDK 版本：5.0.5(17)，API 类型为 `stageMode`
- 包管理器使用 ohpm，Registry 为 `https://ohpm.openharmony.cn/ohpm/`
- 后端 API 地址：`http://8.148.70.81:9000`（HTTP 明文，非 HTTPS）
- Lint 规则包含 `@performance/recommended`、`@typescript-eslint/recommended` 及多项 `@security/*` 加密安全规则

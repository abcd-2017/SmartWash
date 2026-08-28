# CLAUDE.md

本文件为编码 agent 在 SmartWash 鸿蒙端工作时提供指导。仓库总纲见根目录 [CLAUDE.md](../CLAUDE.md)。

**必须使用中文回答。**

## 基本规则

- **提交代码时使用 `commit-commands:commit` skill**：提交前检查变更范围，一个 commit 对应一个完整功能点。格式 `<type>(Harmony): <描述>`（如 `feat(Harmony): 新增订单详情页面`、`fix(Harmony): 修复登录 token 过期问题`）。
- **优先使用 `@ComponentV2`** — 新组件使用 V2 版本，配合 `@Param`、`@Local`、`@Event` 等新装饰器。
- **新增页面必须注册路由** — 在 `entry/src/main/resources/base/profile/router_map.json` 中注册路由映射，页面对外暴露 `@Builder` 函数；`main_pages` 只保留 Index 入口，不要给 NavDestination 页面标 `@Entry`。
- **API 接口遵循既有模式** — 需要认证的接口 URL 前缀使用 `/web/auth/`，返回值统一 `BaseResponse<T>` 包装，通过 `HttpUtil` 调用。
- **导航统一使用全局 NavPathStack** — 页面跳转一律通过 `utils/PathStackUtil.ets` 的全局 `pathStack`，禁止直接使用 `@ohos.router`；需要替换当前页（如跳登录）用 `replacePathByName`，禁止连续 `push` 同一页面。
- **数据存储使用 `@kit.ArkData` preferences** — 全局存储经 `utils/StorageUtil.ets` 封装，不要直接操作 raw API。

## ArkTS 硬规则

- **`@ComponentV2` 没有 `onDidUpdate` 生命周期**（V2 仅有 aboutToAppear/onReuse/aboutToRecycle/aboutToDisappear）。需要监听状态变化启停逻辑（如轮询）时用 `@Monitor('属性名')`——`IndexPage.ets` 曾因误用 `onDidUpdate` 导致轮询永不停止。
- **定时器必须清理**：`setInterval`/`setTimeout` 在 `aboutToDisappear` 中对应 clear，验证码倒计时等页面级定时器不得泄漏。
- **严格模式约束**：禁用 `!=`/`==` 宽松比较（用 `!==`/`===`）；禁止 `as` 强转代替类型转换（如路由传参 `as number`）；VO 定义用 interface + 转换函数，不要依赖 class 直接接 JSON。
- **401 必须清空本地 token** 并复位登录态后再跳转登录页——只跳转不清 token 会导致重启后循环 401（`Axios.ets` 此处为待修项，改网络层时先修这里）。
- **日志按构建模式输出**：release 下禁止打印完整响应体（含手机号、余额等敏感字段），参照 Android 端仅 DEBUG 开启 body 日志。

## 构建与运行

```bash
ohpm install                 # 安装依赖
hvigorw assembleHap          # 命令行构建
hvigorw test                 # 本地单元测试
code-linter --fix            # Lint 检查
```

- 推荐直接用 DevEco Studio：Build → Run 'entry'。
- SDK 5.0.5(17)，`stageMode`；签名配置当前为空（本地 DevEco 注入），CI 出包需用环境变量注入 signingConfigs。
- Lint 已配置 `@performance/recommended`、`@typescript-eslint/recommended` 及 `@security/*` 加密安全规则。

## 项目架构

智慧校园洗衣服务 App（鸿蒙端），基于 **HarmonyOS NEXT Stage 模型** + **ArkTS/ArkUI 声明式框架**。接口与 Android 端保持 100% 对齐（`network/api/` 与 Android `network/api/` 逐接口一致），改接口时参照根目录 CLAUDE.md 的四端联动检查表。

### 分层结构

```
entry/src/main/ets/
  entryability/       → EntryAbility（主 UIAbility，应用入口）
  pages/              → 页面组件（每个页面暴露 @Builder 函数）
  view/               → 可复用 UI 组件（view/common/AppComponents 有支持 loading 的 AppButton）
  network/
    api/              → API 函数（按业务域拆分，返回 Promise<BaseResponse<T>>）
    entity/           → 请求体 DTO
    vo/               → 响应值对象 DTO
    Axios.ets         → Axios 实例 + 请求/响应拦截器
    BaseResponse.ets  → 通用响应包装 {data, code, message}
    HttpUtil.ets      → GET/POST 封装（单例）
  constant/           → 枚举、状态常量、设计系统色值
  utils/              → StorageUtil、PathStackUtil、Logger、TimeUtil、CheckStatus
```

### 核心设计模式

**Stage 模型 + NavPathStack 路由** — `EntryAbility` 加载 `pages/Index`；`Index` 根据 token 和学校信息决定初始页（Login / AddSchool / Home）。`router_map.json` 注册 15+ 条命名路由，页面经全局 `pathStack` 跳转。

**鉴权流程** — Axios 请求拦截器检测 URL 以 `/web` 开头时，从 preferences 读取 token 注入 `Authorization: Bearer <token>`；响应拦截器统一处理 401 和网络错误（401 当前未清 token，见上方硬规则）。

**全局状态** — `@StorageLink('userLoginFlag')` 跨页面共享登录态。注意：AppStorage 布尔 flag + @Watch 只是粗糙的事件模拟，跨组件通知优先用 `@Monitor` 或 emitter。

**HTTP 请求链路** — `页面 → HttpUtil.get/post<T>() → Axios 实例（拦截器）→ errorHandle 钩子 → BaseResponse<T>`。

**页面组件模式** — 每个页面是 `@Component` 或 `@ComponentV2`，被包含在 `NavDestination` 中，通过对应 `@Builder` 函数暴露给路由系统。按钮统一用 `AppButton`（自带 loading 防重），不要裸写可点击组件。

### API 返回格式

```typescript
interface BaseResponse<T> {
  data: T | null    // 数据载荷
  code?: number     // 业务状态码
  message?: string  // 提示信息
}
```

toast 提示统一封装兜底（`message` 为 undefined 时不要弹 "undefined"）。

## 相关 Skills

- 编写/排查 `.ets` 文件时调用 `arkts-development`（ArkUI 组件与状态管理）、`arkts-syntax-assistant`（语法迁移与严格模式约束）、`harmonyos-app`（Stage 模型与最佳实践）。

## 已知坑（改动前先看）

完整清单见 [docs/code-review-2026-08-28.md](../docs/code-review-2026-08-28.md) 第四章，重点关注：

- `network/Axios.ets:17` 硬编码公网明文 HTTP 地址（与 Android 分环境不一致）——改网络层时一并环境化。
- `view/IndexPage.ets` 的 `onDidUpdate` 轮询启停失效（见硬规则）。
- `pages/Register.ets` 验证码倒计时定时器泄漏；`pages/Laundry.ets → Payment` 传参字符串/number 错位。
- `pages/Payment.ets` 支付按钮无防重、Radio 无法取消选券；`pages/Recharge.ets` 金额校验用 parseInt 会截断小数。
- 死代码：`view/CouponCard.ets`、`UserCouponCard.ets`、`OrderStatusCard.ets`、`utils/TimeUtil.ets` 无引用，待删除，不要复用。
- `StorageUtil` 依赖非空断言且初始化未 await，存在时序隐患；token 明文存储待迁移 HUKS/Asset Store。

## 依赖说明

- HTTP 客户端：`@ohos/axios` v2.2.6（注意：声明在根 `oh-package.json5`，entry 模块 dependencies 为空，属待修的依赖错位）
- 测试框架：`@ohos/hypium` + `@ohos/hamock`（当前仅模板用例）
- 包管理器 ohpm，Registry `https://ohpm.openharmony.cn/ohpm/`
- 后端 API 地址当前硬编码于 `Axios.ets`（明文 HTTP，待环境化）

## 提交规范

见顶部基本规则。涉及接口变更时参照根目录 CLAUDE.md 的四端联动检查表。

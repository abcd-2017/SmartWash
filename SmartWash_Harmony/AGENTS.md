# AGENTS.md — SmartWash 鸿蒙端指南

> 智慧校园洗衣服务 App（HarmonyOS NEXT）：与 Android 端功能对齐——注册登录、洗衣套餐选购下单、订单状态追踪、优惠券、钱包充值、储物柜取寄件。接口与 Android 端 100% 对齐。
>
> 相关文档：编码 agent 硬规则见 [CLAUDE.md](CLAUDE.md)；子代理定义见 [.claude/agents/](.claude/agents/)；四端联动约定见仓库根目录 CLAUDE.md。

---

## 一、技术栈

| 维度 | 技术选型 |
|------|----------|
| 语言 | ArkTS（严格模式） |
| UI | ArkUI 声明式框架 |
| 模型 | Stage 模型（UIAbility），API 5.0.5(17) |
| HTTP | @ohos/axios 2.2.6（声明在根 oh-package.json5） |
| 存储 | @kit.ArkData preferences（经 StorageUtil 封装） |
| 路由 | Navigation + router_map.json 系统路由表 + 全局 NavPathStack |
| 测试 | @ohos/hypium + @ohos/hamock（仅模板用例） |
| 包管理 | ohpm（Registry ohpm.openharmony.cn） |
| Lint | code-linter（@performance/@typescript-eslint/@security 规则集） |

---

## 二、目录结构

```
entry/src/main/ets/
├── entryability/EntryAbility.ets   # 主 UIAbility，加载 pages/Index
├── pages/                          # 页面（Index.ets 为唯一 @Entry 入口页）
│   ├── Index.ets                   # 启动分流：token + 学校信息 → Login / AddSchool / Home
│   ├── Login.ets  Register.ets  Home.ets  AddSchool.ets
│   ├── Laundry.ets  Payment.ets  PaymentSuccess.ets
│   ├── OrderList.ets  OrderDetail.ets
│   ├── PickUp.ets  PickupDelivery.ets
│   ├── Recharge.ets  RechargeRecord.ets
│   ├── Coupon.ets  Setting.ets  UserInfo.ets  BingCampusCardAlert.ets
├── view/                           # 可复用组件
│   ├── common/AppComponents.ets    # AppButton（自带 loading 防重）等
│   ├── IndexPage.ets               # 首页框架（Tab + 轮询）
│   ├── Service.ets  UserInfo.ets   # Tab 内容页
│   └── CouponCard.ets 等           # 死代码，待删除，勿引用
├── network/
│   ├── Axios.ets                   # Axios 实例 + 请求/响应拦截器
│   ├── HttpUtil.ets                # GET/POST 封装（单例）
│   ├── BaseResponse.ets            # { data, code?, message? } 信封
│   ├── api/                        # 7 个 API 模块（与 Android network/api/ 逐接口一致）
│   ├── entity/                     # 请求体 DTO
│   └── vo/                         # 响应 VO
├── constant/                       # 枚举、状态常量、DesignSystem 色值
└── utils/                          # StorageUtil、PathStackUtil、Logger、TimeUtil、CheckStatus
```

---

## 三、页面与路由

- **系统路由表**：`entry/src/main/resources/base/profile/router_map.json` 注册 16 条命名路由；`main_pages` 仅保留 Index 入口；页面经 `@Builder` 函数导出给路由系统；NavDestination 页面**禁止标 `@Entry`**。
- **导航**：一律经 `utils/PathStackUtil.ets` 全局 `pathStack`（`pushPathByName` / `replacePathByName` / `pop`），禁止 `@ohos.router`。需要替换当前页（如跳登录）用 `replacePathByName`，并发 401 场景禁止连续 push。
- **传参**：页面参数用 `getParamByName` 获取——禁止 `[0] as X` 裸强转（参数缺失即崩溃），禁止字符串/number 类型错位（Laundry→Payment 曾 orderId 传成字符串）。

---

## 四、核心模式

- **鉴权流程**：请求拦截器检测 URL 以 `/web` 开头 → 从 preferences 读 token 注入 `Authorization: Bearer <token>`；响应拦截器统一处理 401 与网络错误。**401 已统一为「清 token → 清内存登录态 → replace 到登录页」+ 1.5s 防抖**（`Axios.ets` `handleUnauthorized`）。
- **HTTP 链路**：`页面 → HttpUtil.get/post<T>() → Axios 实例（拦截器）→ errorHandle → BaseResponse<T>`。
- **全局状态**：`@StorageLink('userLoginFlag')` 共享登录态；AppStorage flag + @Watch 是粗糙的事件模拟，跨组件通知优先 `@Monitor` 或 emitter。
- **按钮**：统一用 `AppButton`（自带 loading 防重），提交类操作必须接 loading。
- **色值**：走 `constant/DesignSystem.ets`，不硬编码。

---

## 五、ArkTS 硬规则

1. **`@ComponentV2` 没有 `onDidUpdate`**（只有 aboutToAppear/onReuse/aboutToRecycle/aboutToDisappear）；状态变化启停逻辑用 `@Monitor('属性名')`。历史事故：IndexPage 轮询启停静默失效。
2. **定时器必须清理**：`setInterval`/`setTimeout` 在 `aboutToDisappear` 中 clear（验证码倒计时曾泄漏）。
3. **严格模式**：`!==`/`===`（禁宽松比较）；VO 用 interface + 转换函数（不靠 class 直接接 JSON）；padding/margin 传 number；金额用 parseFloat 并校验 > 0（禁 parseInt 截断小数）。
4. **日志脱敏**：release 禁止打印完整响应体（手机号/余额），仅 DEBUG 开 body 日志。
5. **BASE_URL 禁止新增硬编码**（当前为明文 HTTP 演示服务器地址 `http://8.148.70.81:9000`，待环境化，与 Android 分环境机制对齐）。

---

## 六、编码规范

- 所有对话、注释、提交信息使用中文；提交格式 `<type>(Harmony): <描述>`，一个 commit 一个完整功能点。
- 新组件优先 `@ComponentV2` + `@Param`/`@Local`/`@Event`。
- 存储经 `utils/StorageUtil.ets` 封装；toast 统一封装兜底（message 为 undefined 不弹 "undefined"）。

---

## 七、构建与运行

```bash
ohpm install           # 安装依赖
hvigorw assembleHap    # 命令行构建
hvigorw test           # 本地单元测试
code-linter --fix      # Lint
```

- 推荐 DevEco Studio 直接 Run 'entry'；签名配置当前为空（本地注入），CI 出包需环境变量注入 signingConfigs。
- 依赖错位待修：@ohos/axios 声明在根 oh-package.json5，entry 模块 dependencies 为空。

---

## 八、接口变更联动

`network/api/` 与 Android `network/api/` 逐接口对齐。改任何接口，必须同步检查 Android 端与后端 `controller/web/`，并参照根目录 CLAUDE.md 四端联动检查表。

---

## 九、已知问题

完整评审清单（含行号）见 `/Users/admin/code/Android/SmartWash/docs/code-review-2026-08-28.md` 第四章。P0 级：

- baseURL 硬编码演示服务器明文 HTTP 且与 Android 不对齐（`network/Axios.ets:22`）——待环境化
- 401 处理已修复：清 token + 清内存登录态 + replace 登录 + 1.5s 防抖（原 `Axios.ets:32,45-49` 问题已解决）
- `@ComponentV2` 轮询已改用 `@Monitor('isActive')`（原 `view/IndexPage.ets:32-41` 问题已解决）
- Laundry→Payment 传参已统一 `number` 类型（已修复）；支付按钮已加 `paying` 防重、Radio 已支持取消选券（已修复）
- 金额校验已改用 `parseFloat` 并校验 >0 与上限（`pages/Recharge.ets` 已修复）
- 启动闪登录页无 loading 态（`pages/Index.ets:14-30`）

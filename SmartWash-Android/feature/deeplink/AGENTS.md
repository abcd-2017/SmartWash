# AGENTS.md — feature:deeplink（深链接入）

## 1. 模块身份

- **Gradle 坐标**：`:feature:deeplink`
- **包名**：`com.smartwash.feature.deeplink`
- **职责**：`smartwash://` 深链与 http(s) 直链的解析、本地路由注册、后端路由字典兜底、统一导航分发。页面承载在 app 壳 WebView 页（`ShellRoute.WEB_VIEW`），本模块零 Composable、零 Android UI。
- **入口声明**：MainActivity 的 VIEW intent-filter（scheme `smartwash`）在 app 壳 Manifest，冷启动/热启动深链均由壳层 MainActivity 收集后调用本模块。

## 2. 包结构

```
com.smartwash.feature.deeplink
├── DeepLinkAction.kt          # 解析结果密封类：NativeRoute / WebViewUrl / Unknown
├── DeepLinkParser.kt          # 纯函数解析器（零 Android 依赖，JVM 单测全覆盖；全程不抛异常）
├── DeepLinkRegistry.kt        # DeepLinkHosts（host 清单）+ DeepLinkRoutes（路由串登记）+ 注册表
├── di/
│   └── DeepLinkModule.kt      # Hilt：DeepLinkApi @Provides + Registry/Repository 接口供给
├── handler/
│   └── DeepLinkHandler.kt     # @Singleton：resolve（可挂起查字典）+ navigate（导航分发）
├── network/
│   └── DeepLinkApi.kt         # GET /web/deeplink/routes（公开接口，无 @RequireAuthorization）+ DeeplinkRouteVo
└── repository/
    └── DeepLinkRepository.kt  # 字典拉取 + 内存缓存（失败静默返回空列表，不缓存失败）
```

## 3. 公开 API

| 暴露点 | 消费方 | 说明 |
|--------|--------|------|
| `DeepLinkHandler.resolve(uri)` | app 壳 MainActivity / WorkbenchViewModel | 挂起解析：先本地（Parser + Registry），未命中且是 smartwash:// 时查后端字典缓存（仅预热与日志，导航语义仍是 Unknown） |
| `DeepLinkHandler.navigate(navController, action)` | 同上 | NativeRoute → navigate(route)；WebViewUrl → ShellRoute.WEB_VIEW（url/title 经 Uri.encode）；Unknown → Toast「暂不支持该链接」 |
| `DeepLinkAction` | 同上 | 三态密封类 |
| `DeepLinkParser` | 本模块 Handler + JVM 单测 | 纯函数 object，调用方无需 try-catch |
| `DeepLinkHosts` | app 壳 WorkbenchViewModel（兜底列表构造 target） | host 清单一处登记 |

## 4. 依赖关系

- **本模块依赖**：`:common:utils`（ApiResult）、`:common:network`（Retrofit 供给）、`:common:ui`（ShellRoute.WEB_VIEW 单一事实来源）、`libs.androidx.navigation.compose`（NavHostController）
- **谁依赖本模块**：`:app`（MainActivity 深链消费 + 工作台点击分发）
- **零 feature 依赖**：遵循「feature 间仅 impl → 他人 api」铁律与 toolbox/update/divination 单模块先例；各域路由串为字面量副本（`DeepLinkRoutes` 内注释标明出处），**改动须与各域 *Route 密封类两处同步**

## 5. host 清单对照表

| host | 目标路由（DeepLinkRoutes 副本） | 单一事实来源 | 参数 |
|------|------|------|------|
| `login` | `Login` | UserRoute.Login | — |
| `register` | `Register` | UserRoute.Register | — |
| `setting` | `Setting` | UserRoute.Setting | — |
| `recharge` | `Recharge` | PaymentRoute.Recharge | — |
| `recharge-record` | `RechargeRecord` | PaymentRoute.RechargeRecord | — |
| `coupon` | `Coupon` | CouponRoute.Coupon | — |
| `order` | `Order/{tab}` | OrderRoute.Order（注册形态 `Order/{itemId}`） | query `tab`（初始 tab，缺省 0，防御夹取 0-4） |
| `pickup` | `Pickup` | PageConstant.Pickup（app 壳） | — |
| `laundry` | `Laundry` | LaundryRoute.Laundry | — |
| `divination` | `DivHome` | DivRoute.Home | — |
| `divination-history` | `DivHistory` | DivRoute.History | — |
| `ai-work` | `AiWork` | PageConstant.AiWork（app 壳） | — |
| `toolbox-short-code` | `ToolboxShortCodeList` | ToolboxRoute.List | — |
| `web` | Parser 内建分支 → WebViewUrl | — | query `url`（必填，须 http/https）、`title`（可选），均 URL decode |
| `t` | Parser 内建分支 → 短链详情 | ToolboxRoute.Detail（注册形态见 ToolboxGraph） | path 段 `code`（4-16 位字母数字）；详情其余字段走注册缺省值 |
| `user-info` / `service` | **未注册** | HomePage 底部 tab 页 | 见下方「为何不注册」 |

**为何 user-info / service 不注册**：两页是 HomePage 内 HorizontalPager 的 tab 页（路由在 HOME 壳内，无独立路由栈）。跳转等价于「navigate(Home) 并切 pager 到指定页」，需要给 `ShellRoute.HOME` 加可选 tab 参数、改 shellGraph 注册形态并让 HomePage 从 savedStateHandle 观察 tab——会波及 user-impl / payment-impl 两个既有跨模块消费方（`navigate(ShellRoute.HOME)` 语义）与 Pager 初始页生命周期，**不满足低成本标准**；待 HomePage 支持外部指定初始 tab 后再补注册。

## 6. 内部约定

1. **解析全程不抛异常**：`DeepLinkParser.parse` 任何输入（含畸形 URI）都返回 Unknown，调用方无需 try-catch。
2. **本地优先**：resolve 先走本地解析；后端字典（`GET /web/deeplink/routes`，公开接口）只做未知 host 的元数据兜底与预热，失败静默返回空列表、不缓存失败结果（下次自动重试），**绝不阻断本地解析**。
3. **host 大小写不敏感**：scheme 与 host 统一转小写再分支；query key 大小写敏感（自有 scheme 的 key 均为 ASCII 约定值）。
4. **百分号解码**：web 分支的 url/title 解码用自实现 `decodePercent`（字面 `+` 不转空格，与表单编码区分），畸形 % 序列丢弃该参数（对应必填参数缺失 → Unknown）。
5. **WebView 路由单一事实来源**：`ShellRoute.WEB_VIEW`（common:ui），注册形态 `WebView?url={url}&title={title}`；Handler 构造路由时 url/title 必须经 `Uri.encode`。
6. **t/{code} 语义**：映射短链详情页仅 code 必填，其余详情字段走注册缺省值（id=-1 等）——详情页展示字段在深链场景不完整，属已知取舍；完整数据路径仍走工作台/列表页入口。

## 7. 已知坑

1. **路由串副本漂移**：`DeepLinkRoutes` 是各域 *Route 的字面量副本，域内改路由字符串必须同步本模块（AGENTS.md 对照表即 checklist），否则深链跳转失败——`DeepLinkHandler.navigate` 已兜住 IllegalArgumentException 降级为「暂不支持」提示，不会崩导航栈，但功能不可达。
2. **`+` 解码歧义**：若改用 `URLDecoder` 直接解码，字面 `+` 会被错解为空格；改动 `decodePercent` 必须保住单测「web 分支字面加号不转空格」。
3. **`t` 分支 host 与 web 短链域名不同**：`smartwash://t/{code}` 是 App 内深链；`{toolbox.public-base-url}/web/t/{code}` 是 Web 跳转链接，后者作为 http(s) 直链只会进 WebView，不会走本分支。
4. **字典 requireLogin 未消费**：未登录深链直接放行跳目标（401 兜底拉回登录，取舍见 MainActivity 注释），requireLogin 字段仅落库备用；若未来做登录前置判断，须与字典预热结果联动，勿再单独请求。

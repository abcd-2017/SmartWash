# AGENTS.md — feature:toolbox（工具箱：短链 + 设备投递）

## 1. 模块身份

- **Gradle 坐标**：`:feature:toolbox`
- **包名**：`com.smartwash.feature.toolbox`
- **职责**：工具箱域——短链（创建 / 列表 / 详情 / 访问统计 / 解析）与设备投递箱（创建会话 / 会话内发布文本·文件 / 取走 / 关闭）。单模块域（无 api/impl 拆分），DTO 放模块内 `network/`，路由常量模块内单一事实来源。
- **入口**：
  - 深链 host：`toolbox-short-code`（列表）、`delivery`（投递创建）、`t/{code}`（短码详情，code 校验 `^[0-9a-zA-Z]{4,16}$`）
  - 后端 `workbench_entry` 配置的工作台卡片
  - 无底栏入口

## 2. 包结构

```
com.smartwash.feature.toolbox/
├── ToolboxRoute.kt                # List / Create / Detail / Delivery / DeliverySession + CHANGED_KEY
├── ToolboxConstant.kt
├── network/
│   ├── ToolboxApi.kt              # 短链 6 个端点
│   ├── DeliveryApi.kt             # 设备投递 7 个端点
│   └── DeliveryBusinessCode.kt    # 投递业务码
├── logic/
│   ├── ShortCodeRules.kt          # 短码规则（code 格式 / contentType / 可见性）
│   └── DeliveryRules.kt           # 投递规则（文件大小 / 倒计时 / 取走）
├── paging/
│   ├── ShortCodePagingSource.kt
│   └── ShortCodeStatsPagingSource.kt
├── repository/
│   ├── ToolboxRepository.kt (+ Impl)
│   └── DeliveryRepository.kt (+ Impl)
├── di/
│   ├── ToolboxModule.kt           # Hilt 模块（Repository 绑定）
│   └── DeepLinkModule.kt          # toolbox-short-code / delivery / t/{code}
└── ui/
    ├── ToolboxGraph.kt            # toolboxGraph 聚合短链 3 页
    ├── ToolboxUiCommon.kt
    ├── list/                      # 短链列表（分页）
    ├── create/                    # 创建短链
    ├── detail/                    # 短链详情（含访问统计）
    └── delivery/
        ├── DeliveryGraph.kt       # deliveryGraph 聚合投递 2 页
        ├── DeliveryCreatePage.kt  # 创建投递会话
        ├── DeliverySessionPage.kt # 会话页（发布 / 取走 / 轮询）
        ├── DeliveryComponents.kt
        ├── DeliveryCountdown.kt
        ├── DeliveryDeviceSaver.kt
        └── DeliveryPreviewDialog.kt
```

## 3. 公开 API

| 暴露点 | 消费方 | 说明 |
|--------|--------|------|
| `NavGraphBuilder.toolboxGraph` / `deliveryGraph` | app 壳 MainActivity NavHost | 聚合短链 3 页 / 投递 2 页 |
| `ToolboxDeepLinkModule`（Hilt） | 壳层 DeepLinkRegistry | 贡献 3 个 host |
| `ToolboxRoute` | 模块内自用 | 路由单一事实来源 |

## 4. 依赖关系

- **本模块依赖**：`:common:utils`（ApiResult / PageData / 深链契约 / pagingFlow / 二维码）、`:common:network`（Retrofit / `@RequireAuthorization`）、`:common:ui`（清氧令牌 + 共享组件）。
- **谁依赖本模块**：`:app`（壳层聚合 `toolboxGraph` / `deliveryGraph`）。
- **明确不依赖**：`:common:database`、`:common:init`。

## 5. 接口契约

> 契约源：后端 `toolbox/controller/ToolboxShortCodeController.java`、`ToolboxDeliveryController.java`，前缀 `/web/auth/toolbox`，需 ROLE_USER。

**ToolboxApi（短链，6 个）**

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `web/auth/toolbox/short-codes` | 创建短码 |
| GET | `web/auth/toolbox/short-codes?page=&size=&contentType=` | 分页 |
| PUT | `web/auth/toolbox/short-codes/{id}` | 更新 |
| DELETE | `web/auth/toolbox/short-codes/{id}` | 删除 |
| GET | `web/auth/toolbox/short-codes/{id}/stats?page=&size=` | 访问统计 |
| GET | `web/auth/toolbox/short-codes/resolve?code=` | 短码解析 |

**DeliveryApi（设备投递，7 个）**

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `web/auth/toolbox/delivery/sessions` | 创建会话 |
| GET | `web/auth/toolbox/delivery/sessions/{code}` | 会话详情 |
| POST | `web/auth/toolbox/delivery/sessions/{code}/close` | 关闭会话 |
| POST | `web/auth/toolbox/delivery/sessions/{code}/items` | 发布文本 |
| POST | `web/auth/toolbox/delivery/sessions/{code}/items/file` | 发布文件（Multipart） |
| POST | `web/auth/toolbox/delivery/sessions/{code}/items/{itemId}/take` | 取走条目 |
| GET | `web/auth/toolbox/delivery/sessions/{code}/items/{itemId}/content` | 读取文本内容 |

> 公开对端页（Web `views/delivery/`）走后端 `/web/delivery/**` 公开接口，与本模块的 `/web/auth/toolbox/delivery/**` 不是同一组，不要混用。

## 6. 内部约定

1. **短码规则**：`code` 7 位随机或 4-16 位自定义别名；`contentType` 1-5（1 短链 / 2 活码 / 3 交付箱 / 4 图床 / 5 时间胶囊），当前端上主要消费短链。
2. **短链对外地址**：后端返回的 `shortUrl` 即完整地址（`{shortLinkBaseUrl}/web/t/{code}`），端上**只展示、不拼接**。
3. **二维码兜底**：无网络 / 加载失败时用 `QrFallback` 本地生成，禁止空白占位。
4. **投递会话轮询**：会话页条目流按 2-3s 轮询，页面不可见时停止；`take` 后本地移除条目。
5. **跨页同步**：创建 / 更新 / 删除成功后经 `ToolboxRoute.CHANGED_KEY`（savedStateHandle 信号）通知列表重拉。
6. **动效**：只允许 `:common:utils` AnimationUtils vocabulary，且每个动效必须有 `LocalReduceMotion` 降级分支；短链列表 → 详情支持一镜到底共享元素转场。
7. **文案**：用户可见文本一律进本模块 `res/values/strings.xml`。

## 7. 已知坑

- **`t/{code}` 深链只带 code**：详情页其余字段走注册缺省值（id=-1 等），深链场景详情展示字段不完整，完整数据仍走列表页入口。
- **投递文件大小上限**：20MB（`DeliveryBusinessCode` / Web 端常量一致），超限提示走业务码 413。
- **测试**：`ToolboxDeepLinkModuleTest` 为本地验证用；共享规则下新增测试文件不入库。

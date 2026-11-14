# AGENTS.md — :feature:game:impl（游戏中心实现）

## 1. 模块身份

- **Gradle 坐标**：`:feature:game:impl`
- **包名**：`com.smartwash.feature.game`（与模块路径解耦，契约见 `:feature:game:api`）
- **职责**：H5 游戏中心的端上承载——拉取后端下发的游戏列表，以「分类 chips + 两列封面网格」展示，点击经 `ShellRoute.WebView` 打开 H5 游戏；并提供游戏存档 / 排行榜 / 关卡的桥链接口（`GameBridgeApi` 在 app 壳）。
- **入口**：
  - 深链 `smartwash://game-center`（本模块 `di/DeepLinkModule` 经 `@IntoSet` 贡献 host `game-center` → `GameRoute.Center`）
  - 后端 `workbench_entry` 配置的工作台卡片（`target = smartwash://game-center`）
  - 无底栏入口

## 2. 包结构

```
com.smartwash.feature.game/（契约在 :feature:game:api）
├── GameConstant.kt                # 分类 / 图标 / 色组白名单映射
├── di/
│   ├── GameModule.kt              # Hilt 模块
│   └── DeepLinkModule.kt          # host "game-center" → api.GameRoute.Center
├── model/GameItem.kt              # 游戏卡模型（与后端 GameVo 对齐）
├── repository/
│   ├── GameRepository.kt
│   └── GameRepositoryImpl.kt
└── ui/
    ├── GameGraph.kt               # gameGraph（游戏卡点击复用 ShellRoute.WebView）
    └── center/
        ├── GameCenterPage.kt      # 分类 chips + 两列封面网格 + 骨架/空/错误三态
        └── GameCenterViewModel.kt
```

## 3. 公开 API

| 暴露点 | 消费方 | 说明 |
|--------|--------|------|
| `NavGraphBuilder.gameGraph` | app 壳 MainActivity NavHost | 一行聚合游戏中心页 |
| `GameDeepLinkModule`（Hilt） | 壳层 DeepLinkRegistry | `@IntoSet` 贡献 host `game-center` |
| `GameRoute` / `GameApi` / `GameVo` | `:feature:game:api` | 路由与网络契约单一事实来源 |
| `GameConstant` | 模块内 + 游戏卡渲染 | 分类/图标/色组白名单（未知值兜底默认） |

## 4. 依赖关系

- **本模块依赖**：`:feature:game:api`（GameRoute / GameApi / GameVo）、`:common:utils`（ApiResult / 深链契约 / AnimationUtils）、`:common:network`（Retrofit / `@RequireAuthorization`）、`:common:ui`（清氧令牌 + 共享组件 + `ShellRoute`）。
- **谁依赖本模块**：`:app`（壳层聚合 `gameGraph` + `GameBridgeApi` 桥链）。
- **明确不依赖**：`:common:database`（列表走网络，不缓存）、`:common:init`。

## 5. 接口契约

| 方法 | 端点 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `web/auth/game/list` | 是 | 游戏下发列表（后端过滤可玩地址为空的残缺记录） |

游戏存档 / 排行榜 / 关卡接口在 `app/network/api/GameBridgeApi.kt`（桥链专用，非本模块）：

| 方法 | 端点 | 说明 |
|------|------|------|
| GET/PUT | `web/auth/game/state` | 读取 / 保存账号存档（按 `gameKey`） |
| POST | `web/auth/game/score` | 上报成绩 |
| GET | `web/auth/game/leaderboard` | 排行榜 |
| GET | `web/auth/game/levels` | 关卡列表（热更新） |

## 6. 内部约定

1. **列表以后端下发为准**：`game_key` 是业务唯一标识（创建后不可改），`id` 只是自增主键；可玩地址消费后端拼装好的 `url`，端上不拼 MinIO 前缀。
2. **三态齐全**：骨架屏（首拉）、空态（无下发游戏）、错误态（网络失败）必须实现，空态文案走本模块 `strings.xml`。
3. **封面图**：统一经 `SmartWashAsyncImage` / `LocalCoverArt` 展示，缺失时用兜底图，不得空白。
4. **点击承载**：游戏卡点击 → `ShellRoute.WebView`（H5 游戏），由 WebView 主题桥（`SmartWashGameBridge`）提供 `getTheme` / `setChrome`。
5. **文案**：用户可见文本一律进本模块 `res/values/strings.xml`，经 `stringResource` 引用。
6. **动效**：只允许 `:common:utils` AnimationUtils vocabulary，且每个动效必须有 `LocalReduceMotion` 降级分支。

## 7. 已知坑

1. **`game-center` 不是底栏 tab**：只经深链与工作台卡片进入，未登录时深链由壳层拦截落登录页。
2. **白名单映射**：分类/图标/色组 key 由后端下发，端上做白名单映射，未知值兜底默认（分类 OTHER 无独立筛选 chip）；两端白名单需同步维护。
3. **测试**：`GameConstantTest` / `GameDeepLinkModuleTest` 为本地验证用；共享规则下新增测试文件不入库。

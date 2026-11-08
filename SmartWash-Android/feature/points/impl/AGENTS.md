# AGENTS.md — :feature:points:impl

## 模块身份

- **Gradle 坐标**: `:feature:points:impl`
- **包名**: `com.smartwash.feature.points`（实际包路径短于 Gradle 路径，与模块路径解耦，同 coupon 先例）
- **职责**: 积分域实现——积分仓库 + 签到有礼页（签到 / 余额 / 月榜 / 最近流水单页），深链 host `points` 贡献。

## 包结构

```
src/main/java/com/smartwash/feature/points/
├── PointsReason.kt                      # 流水 reason 枚举文案映射（SIGN_IN/STREAK_BONUS/UNKNOWN→reasonDesc 回退）
├── domain/
│   └── PointsDomainLogic.kt             # 纯逻辑：连签周期进度/nextBonusIn 重算/名次档位/月份时间解析/签到并入概览
├── di/
│   └── PointsModule.kt                  # PointsServiceApi 供给（retrofit.create）+ 深链贡献者（@IntoSet host "points"）
├── repository/
│   └── PointsRepository.kt              # 积分仓库（data null → NetworkException 上抛，对齐 coupon 模式）
└── ui/
    ├── PointsGraph.kt                   # 路由聚合（pointsGraph）
    └── points/
        ├── PointsPage.kt                # 签到有礼页（hero 余额卡 + 月榜卡 + 明细卡，LazyColumn 分区）
        └── PointsViewModel.kt           # 三路 RequestState（loadState/leaderboardState/signInState）
└── res/values/strings.xml               # 模块文案（全量入资源，无硬编码）
```

## 公开 API

| 符号 | 说明 |
|------|------|
| `NavGraphBuilder.pointsGraph(navController)` | 路由聚合扩展，壳层 `MainActivity` 一行调用 |

## 依赖关系

- **此模块依赖**: `:feature:points:api` + `:common:utils` / `:common:network` / `:common:ui`
- **谁依赖此模块**: `:app`（NavHost 注册 + Hilt 聚合）

## 内部约定

- **三路状态独立**: `loadState`（/me 整页骨架/错误/内容）、`leaderboardState`（月榜独立，失败卡内小态不拖垮整页）、`signInState`（签到动作，Loading 期间 VM 忽略重复调用 = 防重第一层，后端幂等兜底）。
- **签到成功链路**: SignInResultVo 乐观并入概览（`updatedWith`：余额/连签/已签/累计获得立即生效）→ 静默刷新概览与月榜（流水与名次以后端为准，失败仅日志留痕）。
- **纯逻辑归 domain/**: `PointsDomainLogic.kt` 全部 internal 纯 JVM 函数（禁止 Android 框架类型），配 JVM 锚点单测。
- **取消传播**: 所有 catch 先 `rethrow CancellationException`。
- **错误文案**: `RequestState.Error.getMessage(context)` 消费 ResponseInterceptor 转译的 data 语义；重试按钮走 AppButton SECONDARY。

## 已知坑

- **包名与模块路径解耦**: 引用时用 `com.smartwash.feature.points.*`（非 `.impl.`）。
- **无本地缓存表**: v1 流水/榜单无离线诉求，失败显式 Error 态 + 重试，不要擅自加 Room 表（缓存表归 :common:database 共库决策 T8.2）。
- **进度条为静态填充**: 连签进度由真实 streak 数据驱动（规范「动画只能由真实状态触发」），勿加动画（加了就必须配 reduced-motion 分支）。

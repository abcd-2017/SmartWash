# AGENTS.md — :feature:game:api

## 模块身份

- **Gradle 坐标**: `:feature:game:api`
- **包名**: `com.smartwash.feature.game.api`
- **职责**: 游戏域对外契约——`GameRoute` 路由常量 + `GameApi` / `GameVo`。

## 包结构

```
com.smartwash.feature.game.api/
├── GameRoute.kt          # GameRoute.Center = "GameCenter"
└── network/GameApi.kt    # GameApi（GET web/auth/game/list）+ GameVo
```

## 依赖关系

- **此模块依赖**: `:common:utils`（SmartWashRoute/ApiResult）+ `:common:network`（@RequireAuthorization；retrofit 经其 api 传递）
- **谁依赖此模块**: `:feature:game:impl`（深链贡献 / NavGraph / 仓库）

## 内部约定

- 零 Hilt 运行时、零 Compose；仅契约与模型。
- `GameVo` 字段与后端 `GameConfig` 下发结构一一对应；白名单 key（icon/color/category）由 impl 映射。

## 已知坑

- `GameCategory` / `GameTint` / `GameItem`（含 Compose `ImageVector`）属 UI 层，留在 `:feature:game:impl`。

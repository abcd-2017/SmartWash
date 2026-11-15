# AGENTS.md — :feature:lab:api

## 模块身份

- **Gradle 坐标**: `:feature:lab:api`
- **包名**: `com.smartwash.feature.lab.api`
- **职责**: 解压实验舱对外契约——`LabRoute` 路由常量 + `LabToy` 入口枚举。

## 包结构

```
com.smartwash.feature.lab.api/
└── LabRoute.kt    # LabRoute（各玩具页路由）+ LabToy（首页入口枚举）
```

## 依赖关系

- **此模块依赖**: `:common:utils`（`SmartWashRoute` 契约）
- **谁依赖此模块**: `:feature:lab:impl`（深链贡献 / NavGraph 注册）

## 内部约定

- 路由常量 `LabRoute` 为单一事实来源；新增玩具页在本文件加 `@Serializable` 路由对象。
- 零 Hilt 运行时、零 Compose、零 feature 依赖。

## 已知坑

- `LabToy` 与 `LabRoute` 同文件（枚举引用路由，避免循环拆分）。

# feature/ 域目录约定

> 依据差异分析报告 01-architecture.md §5.3（工程结构对比与风险）建立，为工作台 / 观象台 / 工具箱等大功能同步做结构准备。
> 安卓基准：19 Gradle 模块 + api/impl 拆分 + 依赖铁律；鸿蒙单 entry 平铺，无法照搬模块化，以「目录即模块」模拟同等边界。

## 目录结构

```
ets/feature/
  <域名>/                 # 一个业务域一个目录（如 workbench/、divination/、toolbox/）
    api.ets               # 契约文件：本域对外暴露的接口函数签名 + VO + 路由/枚举常量（只放声明层）
    ...                   # 域内实现：页面组件、视图模型、域内工具（外部不得直接 import，须经 api.ets）
```

## 规则（模拟安卓依赖铁律）

1. **页面禁绕契约**：其他域 / `pages/` / `view/` 只允许 import `<域>/api.ets`，禁止直接引用域内实现文件。
2. **api.ets 只放声明**：接口函数签名（`network/api/` 的调用封装）、VO interface、路由常量、枚举；不放 UI 组件与实现细节。
3. **域间禁止横向依赖**：域 A 的实现不得 import 域 B 的实现；需要协作时经各自 api.ets 或上收到 `utils/`、`network/` 公共层。
4. **枚举随域走**：域私有枚举放 `<域>/api.ets`；全局通用枚举仍放 `constant/`（现有 OrderStatus / CouponStatus 等保留原位，不强行迁移）。
5. **路由注册**：域页面仍需在 `resources/base/profile/router_map.json` 注册，路由名一律用 `constant/RouteNames.ets` 常量，禁止魔法字符串。

## 新增域的落地步骤

1. `feature/<域>/api.ets` 定义契约（VO interface + 转换函数 + API 封装 + 路由常量补充进 RouteNames）。
2. 域内页面组件放 `feature/<域>/`，经 `@Builder` 函数导出给 router_map 使用。
3. 页面跳转、401、存储、toast 等横切能力复用 `utils/`、`network/Axios.ets` 既有约定，不在域内另造。

# common:database 模块

## 模块身份
- **Gradle 坐标**: `common:database`
- **职责**: Room 主缓存库，提供本地数据持久化

## 包结构
```
com.smartwash.common.database
├── AppDatabase.kt              — Room 数据库（v3，三表共库）
├── DatabaseModule.kt           — Hilt 模块
├── dao/
│   ├── CouponVoDao.kt          — 优惠券 DAO
│   ├── LaundryItemDao.kt       — 洗衣商品 DAO
│   └── SchoolNameDao.kt        — 学校名称 DAO
└── entity/
    ├── CouponVoEntity.kt       — 优惠券 Entity（含 toVo()/fromVo()）
    ├── LaundryItemEntity.kt    — 洗衣商品 Entity
    └── SchoolNameEntity.kt     — 学校名称 Entity
```

## 公开 API
- `AppDatabase` — Room 数据库实例（通过 DatabaseModule 注入）
- 各 DAO 接口 — 数据库操作入口
- Entity 的 `toVo()` / `fromVo()` — 与业务模型互转扩展

## 依赖关系
- **依赖**: Room、Hilt、common:model
- **被依赖**: feature 模块的数据层（impl）通过 DAO 访问缓存

## 内部约定
- ⚠️ **三表共库现状**: CouponVo / LaundryItem / SchoolName 共用单个 AppDatabase
- Entity 定义与业务 VO 分离，通过 toVo()/fromVo() 互转
- DAO 查询方法返回 Flow 类型以支持响应式更新
- 数据库版本升级需在 AppDatabase 配套 `fallbackToDestructiveMigration` 或迁移脚本

## 已知坑
- ⚠️ **@Transaction 缺失**: 当前多步 DAO 操作未包裹 @Transaction，并发写入可能不一致
- 三表共库导致 schema 变更需谨慎，任一表改动触发版本号递增

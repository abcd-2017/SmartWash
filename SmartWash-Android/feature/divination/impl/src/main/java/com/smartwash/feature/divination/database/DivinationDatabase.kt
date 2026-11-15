package com.smartwash.feature.divination.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 观象台卦历独立库（T2.4 自 AppDatabase 拆出，解除 database → divination 反向依赖；
 * T4.1 随 feature:divination 整体迁入本模块）。
 *
 * version 1 起步由 Room 自动建表；旧 smartwash_db 中的 div_records 存量不迁移
 * （demo 可接受）。
 *
 * version 2（issue #78）：div_records 增 serverRecordId / interpretationId 两列，
 * v1→v2 走 DI 侧显式 Migration（DivinationModule.MIGRATION_1_2，ALTER TABLE 增列
 * NOT NULL DEFAULT 0）——卦历案卷是用户数据（question/castAt/服务端关联 id）不可
 * 重建，禁止 fallbackToDestructiveMigration 破坏性清库。
 */
@Database(
    entities = [DivRecordEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class DivinationDatabase : RoomDatabase() {
    abstract fun divRecordDao(): DivRecordDao
}

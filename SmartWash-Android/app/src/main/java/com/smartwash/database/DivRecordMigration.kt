package com.smartwash.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库 v1 → v2 迁移：新增观象台卦历表 [div_records]。
 *
 * v1 仅有 laundry_items / school_names / coupon_vos（缓存表，允许破坏性重建）；
 * v2 新增 div_records（用户卦历，需保留），故以显式 Migration 替代该表的 fallback 重建。
 */
class DivRecordMigration : Migration(1, 2) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `div_records` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `method` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `question` TEXT NOT NULL,
                `lines` TEXT NOT NULL,
                `castAt` INTEGER NOT NULL,
                `chartJson` TEXT NOT NULL,
                `status` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

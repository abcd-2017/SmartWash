package com.smartwash.common.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.smartwash.common.database.dao.CouponVoDao
import com.smartwash.common.database.dao.LaundryItemDao
import com.smartwash.common.database.dao.SchoolNameDao
import com.smartwash.common.database.entity.CouponVoEntity
import com.smartwash.common.database.entity.LaundryItemEntity
import com.smartwash.common.database.entity.SchoolNameEntity

/**
 * 主缓存库：洗衣项目 / 学校 / 优惠券三张网络数据缓存表。
 *
 * 版本沿革：v1 三表 → v2 增 div_records（观象台卦历）→ v3 拆出 div_records
 * （卦历独立为 divination 域的 DivinationDatabase，T2.4）。v2→v3 拆表属 schema
 * 变更，缓存表允许破坏性重建（见 [DatabaseModule]）。
 *
 * 三表共库决策（T8.2 评估，维持现状不拆）：三表均为「内存 → Room → 网络」降级
 * 链路里的纯网络数据缓存，无跨表事务、无表间外键，拆成三个域库只增加连接与
 * DatabaseModule 供给复杂度，无任何正确性收益；且缓存表允许破坏性重建，域归属
 * 变化时迁移成本低。若未来出现跨表事务需求再行拆分。
 */
@Database(
    entities = [
        LaundryItemEntity::class,
        SchoolNameEntity::class,
        CouponVoEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun laundryItemDao(): LaundryItemDao
    abstract fun schoolNameDao(): SchoolNameDao
    abstract fun couponVoDao(): CouponVoDao
}

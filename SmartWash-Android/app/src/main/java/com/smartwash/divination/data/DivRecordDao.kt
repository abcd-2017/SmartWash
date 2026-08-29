package com.smartwash.divination.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DivRecordDao {

    @Query("SELECT * FROM div_records WHERE status = :status ORDER BY castAt DESC")
    fun observeAll(status: Int = DivRecordEntity.STATUS_NORMAL): Flow<List<DivRecordEntity>>

    @Query("SELECT * FROM div_records WHERE status = :status AND method = :method ORDER BY castAt DESC")
    fun observeByMethod(method: String, status: Int = DivRecordEntity.STATUS_NORMAL): Flow<List<DivRecordEntity>>

    @Query("SELECT * FROM div_records WHERE id = :id")
    suspend fun getById(id: Long): DivRecordEntity?

    @Query("SELECT * FROM div_records WHERE id = :id")
    fun observeById(id: Long): Flow<DivRecordEntity?>

    @Query("SELECT * FROM div_records WHERE status = :status ORDER BY castAt DESC LIMIT :limit")
    suspend fun recent(limit: Int, status: Int = DivRecordEntity.STATUS_NORMAL): List<DivRecordEntity>

    /** 今日一签去重：当天已入库的梅花时间卦记录 */
    @Query(
        "SELECT * FROM div_records WHERE method = :method AND question = :question " +
            "AND castAt >= :dayStartMillis AND castAt < :dayEndMillis LIMIT 1"
    )
    suspend fun findTodaySign(
        method: String,
        question: String,
        dayStartMillis: Long,
        dayEndMillis: Long,
    ): DivRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DivRecordEntity): Long
}

package com.smartwash.feature.divination.database

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

    /** 回写服务端卦例 id（建档/今日一签关联；issue #78） */
    @Query("UPDATE div_records SET serverRecordId = :serverRecordId WHERE id = :id")
    suspend fun updateServerRecordId(id: Long, serverRecordId: Long)

    /** 回写最近一次解读的服务端 id（SSE done 帧；供追问/反馈使用） */
    @Query("UPDATE div_records SET interpretationId = :interpretationId WHERE id = :id")
    suspend fun updateInterpretationId(id: Long, interpretationId: Long)

    /** 卦历同步：全部未关联服务端的本地记录（按 method/question/castAt 与服务端分页对账） */
    @Query("SELECT * FROM div_records WHERE serverRecordId = 0 AND status = :status")
    suspend fun findUnlinked(status: Int = DivRecordEntity.STATUS_NORMAL): List<DivRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DivRecordEntity): Long
}

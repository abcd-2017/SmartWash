package com.smartwash.divination.data

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 卦历记录（观象台）。本地单用户，不关联 userId。
 *
 * @param method    术数标识 liuyao/meihua/qimen/liuren（DivMethod.id）
 * @param category  所问领域（DivCategory.id）
 * @param question  心中所问
 * @param lines     爻值 JSON（如 [2,2,1,4,2,1]；非摇卦方法为 []）
 * @param castAt    起卦时刻（epoch millis）——复盘/追问一律沿用原时刻，不重算
 * @param chartJson 排盘结果 JSON（DivChartBundle）
 * @param status    状态：0=正常
 * @param createdAt 入库时间
 */
@Keep
@Entity(tableName = "div_records")
data class DivRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val method: String,
    val category: String,
    val question: String,
    val lines: String,
    val castAt: Long,
    val chartJson: String,
    val status: Int = STATUS_NORMAL,
    val createdAt: Long,
) {
    companion object {
        const val STATUS_NORMAL = 0
    }
}

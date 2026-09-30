package com.smartwash.common.database.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 学校名称缓存行（网络 SchoolName 的本地快照；↔VO 映射见 app 侧 EntityMappers） */
@Keep
@Entity(tableName = "school_names")
data class SchoolNameEntity(
    @PrimaryKey val schoolId: Long,
    val schoolName: String,
)

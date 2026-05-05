package com.smartwash.database.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smartwash.network.vo.school.SchoolName

@Keep
@Entity(tableName = "school_names")
data class SchoolNameEntity(
    @PrimaryKey val schoolId: Long,
    val schoolName: String,
) {
    fun toVo() = SchoolName(schoolId, schoolName)

    companion object {
        fun fromVo(vo: SchoolName) = SchoolNameEntity(
            schoolId = vo.schoolId,
            schoolName = vo.schoolName,
        )
    }
}

package com.smartwash.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartwash.database.entity.SchoolNameEntity

@Dao
interface SchoolNameDao {
    @Query("SELECT * FROM school_names")
    suspend fun getAll(): List<SchoolNameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SchoolNameEntity>)

    @Query("DELETE FROM school_names")
    suspend fun deleteAll()
}

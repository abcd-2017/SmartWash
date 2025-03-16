package com.smartwash.database

import androidx.room.RoomDatabase

//@Database(entities = [Service::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

//    companion object {
//        private val instance: AppDatabase? = null
//
//        private val DATABASE_NAME = "smart_wash_db.db"
//
//        fun getInstance(context: Context): AppDatabase {
//            return instance ?: synchronized(this) {
//                instance ?: Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
//                    .build()
//            }
//        }
//    }

    //访问数据库对象
//    abstract fun getServiceDao(): ServiceDao
}
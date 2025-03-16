package com.smartwash

import android.app.Application
import com.smartwash.database.AppDatabase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    companion object {
        lateinit var db: AppDatabase
        lateinit var instance: App

        //全局请求之前的回调方法
        lateinit var globalRequestBeforeCallback: () -> Unit

        //全局请求之后的回调方法
        lateinit var globalRequestAfterCallback: () -> Unit
    }

    override fun onCreate() {
        super.onCreate()
        //app启动,设置全局变量，便于后续自动注入
        val context = applicationContext
//        db = AppDatabase.getInstance(context)
        instance = this
    }
}
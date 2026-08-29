package com.smartwash

import android.app.Application
import com.smartwash.network.session.SessionManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {

    @Inject
    lateinit var sessionManager: SessionManager

    companion object {
        lateinit var instance: App
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 应用启动即预热 token 内存缓存：拦截器只读内存，不再 runBlocking 读 DataStore，
        // 同时避免 setContent 之前的早期请求依赖未初始化的静态回调而崩溃
        sessionManager.warmUp()
    }
}

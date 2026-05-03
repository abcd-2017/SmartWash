package com.smartwash

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    companion object {
        lateinit var instance: App

        lateinit var globalRequestBeforeCallback: () -> Unit
        lateinit var globalRequestAfterCallback: () -> Unit
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
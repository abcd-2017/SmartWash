package com.smartwash.network.vo

import androidx.annotation.Keep

/**
 * 应用版本信息（对应后端 GET /web/app/version 响应 data 字段）
 */
@Keep
data class AppVersionVo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val fileName: String = "",
    val fileSize: Long = 0L,
    val sha256: String = "",
    val forceUpdate: Boolean = false,
    val minVersionCode: Int = 0,
    val changelog: String = "",
    val releaseDate: String = "",
)

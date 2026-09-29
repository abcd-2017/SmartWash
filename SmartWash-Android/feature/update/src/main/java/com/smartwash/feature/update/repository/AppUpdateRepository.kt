package com.smartwash.feature.update.repository

import android.content.Context
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.update.model.AppVersionVo
import com.smartwash.feature.update.network.AppUpdateApi
import com.smartwash.feature.update.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用更新数据层
 * 提供最新版本信息查询与本地版本比对逻辑
 */
@Singleton
class AppUpdateRepository @Inject constructor(
    private val appUpdateApi: AppUpdateApi,
    @ApplicationContext private val context: Context,
) {
    /**
     * 宿主 app 的版本信息（模块化前读 app 的 BuildConfig，library 不可见，改为 PackageManager 读取；
     * context.packageName 恒为宿主包名，运行期不变，构造时读一次缓存）
     */
    private val packageInfo = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()

    /**
     * 从服务端获取最新版本信息，data 为空抛异常
     */
    suspend fun getLatestVersion(): AppVersionVo {
        return appUpdateApi.getLatestVersion().data
            ?: throw NetworkException("版本信息为空", R.string.update_check_failed)
    }

    /**
     * 从服务端获取 APK 预签名下载地址
     */
    suspend fun getPresignedDownloadUrl(): String {
        val response = appUpdateApi.getPresignedDownloadUrl()
        return response.data ?: throw NetworkException("下载地址为空", R.string.download_url_empty)
    }

    /**
     * 当前客户端 versionCode（读取失败兜底 MAX_VALUE：系统异常时不打扰用户弹更新）
     */
    fun getCurrentVersionCode(): Int = packageInfo?.longVersionCode?.toInt() ?: Int.MAX_VALUE

    /**
     * 当前客户端 versionName
     */
    fun getCurrentVersionName(): String = packageInfo?.versionName ?: "1.0.0"

    /**
     * 远程版本是否高于本地（是否需要更新）
     */
    fun shouldUpdate(remoteVersionCode: Int): Boolean =
        remoteVersionCode > getCurrentVersionCode()

    /**
     * 是否强制更新（本地版本低于服务端要求的最低版本）
     */
    fun shouldForceUpdate(remoteVo: AppVersionVo): Boolean =
        getCurrentVersionCode() < remoteVo.minVersionCode
}

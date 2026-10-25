package com.smartwash.feature.update.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * requestInstall 结果：已发起系统安装 / 需先授予「安装未知应用」权限
 */
sealed interface InstallRequestResult {
    data object Installed : InstallRequestResult
    data object NeedPermission : InstallRequestResult
}

/**
 * APK 安装器
 * 封装 FileProvider URI 获取、安装 Intent 跳转、权限检查等逻辑
 */
object ApkInstaller {

    /**
     * 安装点击的完整序列（自壳层 UpdateFlow 下沉）：有安装权限则拉起系统安装器；
     * 无权限跳「允许安装未知应用」设置页。
     *
     * startActivity 返回后系统安装器才经 FileProvider 异步读取 APK，
     * 本序列内禁止删除文件——清理由下次下载开始时（ApkDownloadWorker 入口）兜底。
     */
    fun requestInstall(context: Context, apkFile: File): InstallRequestResult {
        return if (canInstallApk(context)) {
            installViaIntent(context, apkFile)
            InstallRequestResult.Installed
        } else {
            openInstallPermissionSettings(context)
            InstallRequestResult.NeedPermission
        }
    }

    /**
     * 通过系统安装器 Intent 安装 APK
     */
    fun installViaIntent(context: Context, apkFile: File) {
        val uri = getFileUri(context, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /**
     * 获取 APK 文件的 FileProvider URI
     */
    fun getFileUri(context: Context, apkFile: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
    }

    /**
     * 跳转系统「允许安装未知应用」设置页
     */
    fun openInstallPermissionSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    /**
     * 检查是否拥有安装未知应用的权限
     */
    fun canInstallApk(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            // Android 8.0 以下通过 Settings 控制，此处默认返回 true
            true
        }
    }

    /**
     * 删除本地缓存的 APK 文件
     */
    fun deleteLocalApk(context: Context) {
        val apkFile = File(context.cacheDir, ApkDownloadWorker.APK_FILE_NAME)
        if (apkFile.exists()) apkFile.delete()
    }
}

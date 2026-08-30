package com.smartwash.ui.page.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.R
import com.smartwash.network.exception.NetworkException
import com.smartwash.network.vo.AppVersionVo
import com.smartwash.repository.AppUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 应用更新状态密封类，覆盖检查→下载→安装全链路异步状态
 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpdateAvailable(val version: AppVersionVo) : UpdateState
    data class Downloading(val progress: Int) : UpdateState
    data class Downloaded(val file: File) : UpdateState
    data object Installing : UpdateState
    data object LatestVersion : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * 应用更新 ViewModel
 * 管理版本检查、下载进度、安装触发等状态
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** 预签名下载地址，由 startDownload 获取后暴露给 UI 层调度 Worker */
    private val _downloadUrl = MutableStateFlow<String?>(null)
    val downloadUrl: StateFlow<String?> = _downloadUrl.asStateFlow()

    /** 防重复触发检查 */
    private var isChecking = false

    /** 缓存最新版本信息，供下载使用 */
    private var latestVersion: AppVersionVo? = null

    /**
     * 检查更新
     * @param silent 静默模式（启动检查，出错不展示提示）
     */
    fun checkForUpdate(silent: Boolean = true) {
        if (isChecking) return
        isChecking = true
        _state.value = UpdateState.Checking

        viewModelScope.launch {
            try {
                val version = repository.getLatestVersion()
                latestVersion = version
                if (repository.shouldUpdate(version.versionCode)) {
                    _state.value = UpdateState.UpdateAvailable(version)
                } else {
                    _state.value = UpdateState.LatestVersion
                }
                isChecking = false
            } catch (e: NetworkException) {
                if (!silent) {
                    _state.value = UpdateState.Error(e.message ?: "检查更新失败")
                } else {
                    // 静默检查失败回到 Idle，不打断用户
                    _state.value = UpdateState.Idle
                }
                isChecking = false
            } catch (e: Exception) {
                if (!silent) {
                    _state.value = UpdateState.Error(e.message ?: "检查更新失败")
                } else {
                    _state.value = UpdateState.Idle
                }
                isChecking = false
            }
        }
    }

    /**
     * 启动下载流程：先获取预签名下载地址，成功后通过 downloadUrl 暴露给 UI 层
     */
    fun startDownload(context: Context) {
        val version = latestVersion ?: return
        viewModelScope.launch {
            try {
                val url = repository.getPresignedDownloadUrl()
                _downloadUrl.value = url
            } catch (e: NetworkException) {
                _state.value = UpdateState.Error(e.message ?: context.getString(R.string.download_url_empty))
            } catch (e: Exception) {
                _state.value = UpdateState.Error(e.message ?: context.getString(R.string.download_url_empty))
            }
        }
    }

    /**
     * 消费已获取的预签名 URL（防止重复调度 Worker）
     */
    fun consumeDownloadUrl() {
        _downloadUrl.value = null
    }

    /**
     * 下载进度回调
     */
    fun onDownloadProgress(progress: Int) {
        _state.value = UpdateState.Downloading(progress)
    }

    /**
     * 下载完成回调
     */
    fun onDownloadComplete(file: File) {
        _state.value = UpdateState.Downloaded(file)
    }

    /**
     * 下载失败回调
     */
    fun onDownloadFailed(message: String) {
        _state.value = UpdateState.Error(message)
    }

    /**
     * 安装开始
     */
    fun onInstallStarted() {
        _state.value = UpdateState.Installing
    }

    /**
     * 重置状态（用户关闭弹窗或安装完成后）
     */
    fun reset() {
        _state.value = UpdateState.Idle
        _downloadUrl.value = null
        isChecking = false
    }

    /**
     * 获取当前版本名称
     */
    fun getCurrentVersionName(): String = repository.getCurrentVersionName()

    /**
     * 获取缓存的最新版本信息
     */
    fun getLatestVersion(): AppVersionVo? = latestVersion
}

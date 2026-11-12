package com.smartwash.feature.update.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.update.R
import com.smartwash.feature.update.model.AppVersionVo
import com.smartwash.feature.update.repository.AppUpdateRepository
import com.smartwash.feature.update.settings.UpdateSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Error 来源：决定失败提示的呈现位置——手动检查失败由设置页 Toast 反馈，
 * 下载失败由壳层错误弹窗反馈（含重试入口），互不抢占。
 */
enum class ErrorSource { Check, Download }

/**
 * 应用更新状态密封类，覆盖检查→下载→安装全链路异步状态
 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpdateAvailable(val version: AppVersionVo) : UpdateState
    /** 准备下载中（获取预签名地址阶段），UI 应展示加载指示器 */
    data object Preparing : UpdateState
    data class Downloading(
        val progress: Int,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
    ) : UpdateState
    data class Downloaded(val file: File) : UpdateState
    data object Installing : UpdateState
    data object LatestVersion : UpdateState
    data class Error(
        val message: String,
        val source: ErrorSource = ErrorSource.Check,
    ) : UpdateState
}

/**
 * 应用更新 ViewModel
 * 管理版本检查、下载进度、安装触发等状态
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
    private val settings: UpdateSettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** 设置页「自动更新」开关（数据源为 [UpdateSettingsStore] 的内存态） */
    private val _autoUpdateEnabled = MutableStateFlow(false)
    val autoUpdateEnabled: StateFlow<Boolean> = _autoUpdateEnabled.asStateFlow()

    /**
     * 自动下载标记（2026-11-12）：为 true 时下载中不弹进度窗（用户未主动点击，
     * 静默下载，进度交给系统通知栏），完成时仍弹「安装包已就位」窗。
     */
    private val _autoDownload = MutableStateFlow(false)
    val autoDownload: StateFlow<Boolean> = _autoDownload.asStateFlow()

    /** 用户是否已手动切换过自动更新：为真后初值补读不再覆盖内存态 */
    private var userToggled = false

    init {
        // 设置载入与启动任务（UpdateInitTask）各自幂等 load；失败按默认值（不自动更新）
        viewModelScope.launch {
            runCatching { settings.load() }
                .onSuccess {
                    // 守卫：用户已手动切换过就不再用初读值盖回（DataStore 首读异步，
                    // 否则用户在初读完成前切换会被旧值覆盖——同设置页触感开关的历史缺陷）
                    if (!userToggled) {
                        _autoUpdateEnabled.value = settings.autoUpdateEnabled
                    }
                }
                .onFailure { AppLogger.warn(TAG, "更新设置载入失败，按默认值（不自动更新）", it) }
        }
    }

    /** 预签名下载地址，由 startDownload 获取后暴露给 UI 层调度 Worker */
    private val _downloadUrl = MutableStateFlow<String?>(null)
    val downloadUrl: StateFlow<String?> = _downloadUrl.asStateFlow()

    /** 防重复触发检查 */
    private var isChecking = false

    /**
     * 后台下载标记（2026-11-01 用户反馈）：DownloadProgressDialog 点「后台下载」后置真，
     * 弹窗隐藏但 WorkManager 下载继续（进度承接交给系统前台通知）；
     * 完成/失败/取消时复位，让完成/错误弹窗正常回弹
     */
    private val _backgroundDownload = MutableStateFlow(false)
    val backgroundDownload: StateFlow<Boolean> = _backgroundDownload.asStateFlow()

    /** 缓存最新版本信息，供下载使用 */
    private var latestVersion: AppVersionVo? = null

    /** 用户已关闭弹窗的版本：UpdateEventBus replay 重放同版本时不再打扰（下次冷启动重新检查） */
    private var dismissedVersionCode: Int? = null

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
                // 外部调用失败（版本检查网络失败）= error，静默/手动仅作来源区分
                AppLogger.error(TAG, "检查更新失败（${if (silent) "静默" else "手动"}）", e)
                if (!silent) {
                    _state.value = UpdateState.Error(e.message ?: "检查更新失败")
                } else {
                    // 静默检查失败回到 Idle，不打断用户
                    _state.value = UpdateState.Idle
                }
                isChecking = false
            } catch (e: Exception) {
                AppLogger.error(TAG, "检查更新失败（${if (silent) "静默" else "手动"}）", e)
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
     * 壳层事件入口：UpdateEventBus 广播的「发现新版本」事件转弹窗状态
     * （启动静默检查在 UpdateInitTask，壳层收集事件后调本方法）。
     *
     * 事件总线 replay=1 会在壳层重建时重放最近事件，这里挡下两类重放：
     * 已被用户关闭的同版本、下载/安装流程进行中的版本（不打断进度弹窗）。
     */
    fun onUpdateAvailable(version: AppVersionVo) {
        if (version.versionCode == dismissedVersionCode) {
            // 幂等命中（事件重放），debug 足够
            AppLogger.debug(TAG, "版本事件重放命中已关闭版本 versionCode=${version.versionCode}，忽略")
            return
        }
        val current = _state.value
        if (current is UpdateState.Downloading ||
            current is UpdateState.Downloaded ||
            current is UpdateState.Installing
        ) {
            AppLogger.debug(TAG, "版本事件在下载/安装流程中到达（state=${current::class.simpleName}），忽略以不打断进度")
            return
        }
        latestVersion = version
        // 说明弹窗路径：清掉可能残留的自动下载标记，避免完成窗被静默态压制
        _autoDownload.value = false
        _state.value = UpdateState.UpdateAvailable(version)
    }

    /**
     * 自动下载入口（壳层收到 UpdateEvent.AutoDownload 后调用）：记录版本信息并进入
     * 准备下载态，不弹更新说明弹窗（用户已在设置页授权自动更新）。
     * 下载中不弹进度窗（见 [_autoDownload]）。
     */
    fun onAutoDownload(version: AppVersionVo) {
        val current = _state.value
        if (current is UpdateState.Downloading ||
            current is UpdateState.Downloaded ||
            current is UpdateState.Installing
        ) {
            AppLogger.debug(TAG, "自动下载事件在下载/安装流程中到达（state=${current::class.simpleName}），忽略以不打断进度")
            return
        }
        latestVersion = version
        _autoDownload.value = true
        _state.value = UpdateState.Preparing
    }

    /**
     * 设置页「自动更新」开关：先改内存态（UI 与启动检查即时生效），再异步落盘。
     * 落盘失败回滚内存态与 UI（本次会话内生效，重启后丢失）。
     */
    fun setAutoUpdateEnabled(enabled: Boolean) {
        userToggled = true
        val previous = settings.autoUpdateEnabled
        settings.autoUpdateEnabled = enabled
        _autoUpdateEnabled.value = enabled
        viewModelScope.launch {
            // NonCancellable：本开关在设置页，用户切换后一切走即清空 VM、取消写盘协程，
            // 会导致开关静默回滚、重启丢失；包在 NonCancellable 中保证写入做完
            withContext(NonCancellable) {
                runCatching { settings.persistAutoUpdateEnabled(enabled) }
                    .onFailure {
                        AppLogger.warn(TAG, "自动更新开关落盘失败，已回滚内存态", it)
                        settings.autoUpdateEnabled = previous
                        _autoUpdateEnabled.value = previous
                    }
            }
        }
    }

    /**
     * 「不再提醒此版本」：记录 versionCode，启动静默检查跳过该版本（手动检查不受影响）。
     * 内存态即时生效，落盘失败仅记日志——下次冷启动恢复为未忽略（可接受的弱一致）。
     */
    fun ignoreVersion(versionCode: Int) {
        // 单调递增：低版本值不得覆盖已忽略的高版本（服务端版本号只增不减）
        if (versionCode <= settings.ignoredVersionCode) return
        val previous = settings.ignoredVersionCode
        settings.ignoredVersionCode = versionCode
        viewModelScope.launch {
            runCatching { settings.persistIgnoredVersion(versionCode) }
                .onFailure {
                    AppLogger.warn(TAG, "忽略版本落盘失败，已回滚内存态", it)
                    settings.ignoredVersionCode = previous
                }
        }
    }

    /**
     * 准备下载：切换至 Preparing 状态（UI 展示加载指示器），由 UI 层在确认通知权限后调用。
     * 调用方需在调用本方法前确保已获取 POST_NOTIFICATIONS 权限。
     */
    fun preparingDownload() {
        _state.value = UpdateState.Preparing
    }

    /**
     * 启动下载流程：先获取预签名下载地址，成功后通过 downloadUrl 暴露给 UI 层
     */
    fun startDownload(context: Context) {
        val version = latestVersion ?: run {
            // 业务拒绝：未先完成版本检查即请求下载（调用时序异常）
            AppLogger.warn(TAG, "startDownload 在未持有最新版本信息时被调用，忽略")
            return
        }
        viewModelScope.launch {
            try {
                val url = repository.getPresignedDownloadUrl()
                // 低频关键动作成功：预签名地址不含敏感个人信息，但签名凭证不打入日志
                AppLogger.info(TAG, "已获取 APK 预签名下载地址，准备调度下载（versionCode=${version.versionCode}）")
                _downloadUrl.value = url
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "获取 APK 预签名下载地址失败", e)
                _state.value = UpdateState.Error(
                    e.message ?: context.getString(R.string.download_url_empty),
                    ErrorSource.Download,
                )
            } catch (e: Exception) {
                AppLogger.error(TAG, "获取 APK 预签名下载地址失败", e)
                _state.value = UpdateState.Error(
                    e.message ?: context.getString(R.string.download_url_empty),
                    ErrorSource.Download,
                )
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
    fun onDownloadProgress(progress: Int, downloadedBytes: Long, totalBytes: Long) {
        _state.value = UpdateState.Downloading(progress, downloadedBytes, totalBytes)
    }

    /**
     * 下载完成回调（后台下载时复位标记，完成弹窗正常回弹）
     */
    fun onDownloadComplete(file: File) {
        _backgroundDownload.value = false
        // 自动下载完成：清标记让「安装包已就位」窗正常弹出
        _autoDownload.value = false
        _state.value = UpdateState.Downloaded(file)
    }

    /** 最小化下载弹窗转后台下载（下载不中断，进度由系统通知承接） */
    fun minimizeDownloadDialog() {
        _backgroundDownload.value = true
    }

    /**
     * 下载失败回调
     */
    fun onDownloadFailed(message: String) {
        _backgroundDownload.value = false
        // 失败要让用户看到错误弹窗（含重试），不能被静默态压制
        _autoDownload.value = false
        _state.value = UpdateState.Error(message, ErrorSource.Download)
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
        // 记录已关闭版本：事件重放（如屏幕旋转重建壳层）不再重复弹窗
        latestVersion?.let { dismissedVersionCode = it.versionCode }
        _state.value = UpdateState.Idle
        _downloadUrl.value = null
        _backgroundDownload.value = false
        _autoDownload.value = false
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

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "UpdateViewModel"
    }
}

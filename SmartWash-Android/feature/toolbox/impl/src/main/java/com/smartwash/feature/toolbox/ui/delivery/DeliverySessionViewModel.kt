package com.smartwash.feature.toolbox.ui.delivery

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.logic.DeliveryRules
import com.smartwash.feature.toolbox.api.network.DeliveryBusinessCode
import com.smartwash.feature.toolbox.api.network.DeliveryException
import com.smartwash.feature.toolbox.api.network.DeliveryItemVo
import com.smartwash.feature.toolbox.api.network.DeliverySessionVo
import com.smartwash.feature.toolbox.repository.DeliveryRepository
import com.smartwash.feature.toolbox.ui.copyToClipboard
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 设备投递会话 ViewModel：2-3s 轮询条目流（契约 docs/delivery-api.md §一「轮询」；
 * coroutine loop + delay，随 ViewModel 离开自动取消，连续失败指数退避封顶 10s），
 * 发布（文本/图片/文件）与取走（预览→复制/保存→焚毁）动作完成后立即补拉一次免等下轮。
 *
 * 失效语义：轮询遇业务码 404（过期/关闭）即置 [sessionGone]，页面提示后退出——
 * 404 在投递场景表示「会话已失效」，不与全局资源 404 混淆，也**不触发全局 401**。
 */
@HiltViewModel
class DeliverySessionViewModel @Inject constructor(
    private val repository: DeliveryRepository,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** 条目预览弹窗状态（取走/焚毁的编排在本类完成，页面只渲染） */
    sealed interface PreviewState {
        val item: DeliveryItemVo

        /** 文本：正文读取中 */
        data class TextLoading(override val item: DeliveryItemVo) : PreviewState

        /** 文本：正文就绪（text=null 表示已焚毁） */
        data class TextReady(override val item: DeliveryItemVo, val text: String?) : PreviewState

        /** 图片：取走确认（契约：文件类内容须先 take 认领，未取走请求内容后端 409） */
        data class ConfirmImage(override val item: DeliveryItemVo) : PreviewState

        /** 文件：取走确认 */
        data class ConfirmFile(override val item: DeliveryItemVo) : PreviewState

        /** 图片：认领并下载完成（bitmap 解码失败为 null，仍可保存原文件） */
        data class ImageReady(override val item: DeliveryItemVo, val bitmap: Bitmap?, val cacheFile: File) : PreviewState

        /** 文件/图片：认领成功但内容未到手（下载失败）——条目未焚毁，弹窗转可重试错误态（P0 端侧兜底） */
        data class ContentFailed(override val item: DeliveryItemVo, val claimed: Boolean) : PreviewState
    }

    /** 一次性提示（Toast 语义）；message 保留后端原文，优先于 resId 展示 */
    data class ToastEvent(
        @StringRes val resId: Int,
        val message: String? = null,
    )

    companion object {
        const val ARG_CODE = "code"

        /** 轮询基准间隔（契约 2-3s，取中值） */
        private const val POLL_INTERVAL_MS = 2500L

        /** 连续失败指数退避封顶 */
        private const val MAX_POLL_INTERVAL_MS = 10_000L

        private const val TAG = "DeliverySessionViewModel"

        /** 后端兜底型泛化文案（信封 201 / ExceptionControllerAdvice 输出）：无上下文价值，
         *  Toast 展示时回退端侧 resId 文案（规范 §3.6：错误须说明发生了什么+怎么解决） */
        private val GENERIC_BACKEND_MESSAGES = setOf("失败", "系统异常，请稍后再试")
    }

    private var sessionCode: String? = null
    private var pollingJob: Job? = null

    /** 路由入参还原：会话码经 query 携带（DeliveryGraph 统一 Uri.encode） */
    private val routeCode: String = savedStateHandle.get<String>(ARG_CODE).orEmpty()

    /** 预览弹窗代次：openItem/dismiss 递增，使在途动作完成后不再回写已关闭或已切换的弹窗 */
    private var previewGeneration = 0

    private val _session = MutableStateFlow<DeliverySessionVo?>(null)
    val session = _session.asStateFlow()

    private val _items = MutableStateFlow<List<DeliveryItemVo>>(emptyList())
    val items = _items.asStateFlow()

    /** 首次加载状态（骨架屏/错误重试承载）；轮询后续失败不改写（由 [pollFailed] 横幅表达） */
    private val _loadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadState = _loadState.asStateFlow()

    /** 轮询连续失败横幅（列表非空时展示「同步失败，自动重试中」） */
    private val _pollFailed = MutableStateFlow(false)
    val pollFailed = _pollFailed.asStateFlow()

    /** 会话失效（业务码 404：过期/关闭/占用达上限），非 null 即退出会话页 */
    private val _sessionGone = MutableStateFlow<ToastEvent?>(null)
    val sessionGone = _sessionGone.asStateFlow()

    /** 文本草稿（单向数据流：Page 调 updateDraft，发送成功后由本类清空） */
    private val _draft = MutableStateFlow("")
    val draft = _draft.asStateFlow()

    /** 发布状态（发送按钮 loading + 表单下方内联错误，规范 §3.7） */
    private val _sendState = MutableStateFlow<RequestState>(RequestState.Idle)
    val sendState = _sendState.asStateFlow()

    private val _preview = MutableStateFlow<PreviewState?>(null)
    val preview = _preview.asStateFlow()

    /** 预览弹窗内动作（取走/下载/保存）的 loading，错误经 [toast] 提示 */
    private val _previewAction = MutableStateFlow<RequestState>(RequestState.Idle)
    val previewAction = _previewAction.asStateFlow()

    /** 「认领后内容未到手」的可重试条目（P0 兜底）：列表行保持待取走可点，重开直达失败态续取 */
    private val _retryableItemIds = MutableStateFlow<Set<Long>>(emptySet())
    val retryableItemIds = _retryableItemIds.asStateFlow()

    /** 关闭会话提交状态 */
    private val _closeState = MutableStateFlow<RequestState>(RequestState.Idle)
    val closeState = _closeState.asStateFlow()

    private val _toast = MutableStateFlow<ToastEvent?>(null)
    val toast = _toast.asStateFlow()

    /** 进入会话页启动轮询（重复调用幂等；页面 LaunchedEffect(Unit) 触发） */
    fun start() {
        if (routeCode.isBlank()) return
        if (sessionCode == routeCode && pollingJob?.isActive == true) return
        sessionCode = routeCode
        _loadState.value = RequestState.Loading
        startPolling()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            var intervalMs = POLL_INTERVAL_MS
            while (isActive) {
                val code = sessionCode ?: break
                try {
                    val detail = repository.fetchSessionDetail(code)
                    _session.value = detail.session
                    _items.value = detail.items
                    _loadState.value = RequestState.Success
                    _pollFailed.value = false
                    intervalMs = POLL_INTERVAL_MS
                    // 防御：倒计时归零即刻退出，不等下一次 404（后端读路径到期即失效）
                    val remaining = DeliveryRules.remainingSeconds(detail.session?.expireAt)
                    if (remaining != null && remaining <= 0L) {
                        markSessionGone(null)
                        break
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: DeliveryException) {
                    if (e.businessCode == DeliveryBusinessCode.GONE) {
                        // 会话过期/关闭：读路径即时失效语义，立即退出（不与全局 404/401 语义混淆）
                        AppLogger.warn(TAG, "轮询遇会话失效 businessCode=${e.businessCode}，退出会话")
                        markSessionGone(e.message)
                        break
                    }
                    AppLogger.debug(TAG, "轮询条目流业务异常 businessCode=${e.businessCode}，退避重试")
                    onPollFailure(e.message)
                    intervalMs = backoff(intervalMs)
                } catch (e: NetworkException) {
                    AppLogger.debug(TAG, "轮询条目流网络异常，退避重试")
                    onPollFailure(e.message)
                    intervalMs = backoff(intervalMs)
                } catch (e: Exception) {
                    AppLogger.debug(TAG, "轮询条目流未知异常", e)
                    onPollFailure(null)
                    intervalMs = backoff(intervalMs)
                }
                delay(intervalMs)
            }
        }
    }

    private fun onPollFailure(message: String?) {
        if (_loadState.value is RequestState.Loading) {
            // 首次加载即失败（非轮询途中自愈场景），error 留痕
            AppLogger.error(TAG, "首次拉取条目流失败 message=$message")
            _loadState.value = RequestState.Error(R.string.toolbox_delivery_error_load, message)
        }
        _pollFailed.value = true
    }

    private fun backoff(current: Long): Long = (current * 2).coerceAtMost(MAX_POLL_INTERVAL_MS)

    private fun markSessionGone(message: String?) {
        _sessionGone.value = ToastEvent(R.string.toolbox_delivery_session_gone_toast, message)
    }

    /** 动作完成后立即补拉一次条目流（重启轮询循环，退避一并复位） */
    fun refreshNow() {
        if (sessionCode != null) startPolling()
    }

    fun updateDraft(value: String) {
        _draft.value = value
    }

    /** 发送文本条目（10 万字符上限端侧前置校验，与后端 413 终审一致） */
    fun sendText() {
        val code = sessionCode ?: return
        if (_sendState.value is RequestState.Loading) return
        val text = _draft.value.trim()
        if (text.isEmpty()) {
            _sendState.value = RequestState.Error(R.string.toolbox_delivery_error_text_empty)
            return
        }
        if (text.length > DeliveryRules.MAX_TEXT_LENGTH) {
            _sendState.value = RequestState.Error(R.string.toolbox_delivery_error_text_too_long)
            return
        }
        viewModelScope.launch {
            _sendState.value = RequestState.Loading
            try {
                repository.publishText(code = code, textContent = text, title = null)
                _draft.value = ""
                _sendState.value = RequestState.Success
                AppLogger.info(TAG, "文本条目发布成功 code=$code length=${text.length}")
                refreshNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DeliverySessionViewModel.sendText: ${e.message}", e)
                _sendState.value = RequestState.Error(e.resId, e.message)
            } catch (e: DeliveryException) {
                AppLogger.warn(TAG, "DeliverySessionViewModel.sendText: 业务拒绝 businessCode=${e.businessCode}")
                _sendState.value = RequestState.Error(R.string.toolbox_delivery_error_send, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "DeliverySessionViewModel.sendText: ${e.message}", e)
                _sendState.value = RequestState.Error(R.string.toolbox_delivery_error_send)
            }
        }
    }

    /** 发布图片条目（图片选择器回调入口） */
    fun publishImage(uri: android.net.Uri) = publishPicked(uri, DeliveryRules.ItemKind.IMAGE)

    /** 发布文件条目（文件选择器回调入口） */
    fun publishPickedFile(uri: android.net.Uri) = publishPicked(uri, DeliveryRules.ItemKind.FILE)

    private fun publishPicked(uri: android.net.Uri, kind: DeliveryRules.ItemKind) {
        val code = sessionCode ?: return
        if (_sendState.value is RequestState.Loading) return
        viewModelScope.launch {
            _sendState.value = RequestState.Loading
            try {
                val picked = resolvePickedFile(appContext, uri)
                if (picked.size > DeliveryRules.MAX_FILE_BYTES) {
                    _sendState.value = RequestState.Error(R.string.toolbox_delivery_error_file_too_large)
                    return@launch
                }
                repository.publishFile(
                    code = code,
                    kindCode = kind.code,
                    fileName = picked.displayName,
                    size = picked.size,
                    // 工厂幂等：OkHttp 重试 writeTo 时按需重开流
                    content = { appContext.contentResolver.openInputStream(uri) },
                )
                _sendState.value = RequestState.Success
                AppLogger.info(TAG, "文件条目发布成功 code=$code kind=${kind.name} fileName=${picked.displayName} size=${picked.size}B")
                refreshNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DeliverySessionViewModel.publishPicked: ${e.message}", e)
                _sendState.value = RequestState.Error(e.resId, e.message)
            } catch (e: DeliveryException) {
                AppLogger.warn(TAG, "DeliverySessionViewModel.publishPicked: 业务拒绝 businessCode=${e.businessCode}")
                _sendState.value = RequestState.Error(R.string.toolbox_delivery_error_send, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "DeliverySessionViewModel.publishPicked: ${e.message}", e)
                _sendState.value = RequestState.Error(R.string.toolbox_delivery_error_send)
            }
        }
    }

    // ---------- 条目预览与取走（保存/复制即触发 take + 焚毁） ----------

    /** 点条目：文本先读内容（非破坏预览），图片/文件出取走确认（内容获取须先认领） */
    fun openItem(item: DeliveryItemVo) {
        if (item.taken != 0) {
            // 已取走条目默认焚毁占位不可再点；唯「认领后内容未到手」的条目例外（P0 兜底）：
            // 重开即上次失败态，可重试续取——内容未到手前不进入焚毁语义
            if (item.id !in _retryableItemIds.value) return
            _preview.value = PreviewState.ContentFailed(item, claimed = true)
            return
        }
        // 条目已回到待取走（认领被服务端释放）时清掉残留重试标记
        _retryableItemIds.value = _retryableItemIds.value - item.id
        val code = sessionCode ?: return
        val generation = ++previewGeneration
        when (DeliveryRules.ItemKind.fromCode(item.kind)) {
            DeliveryRules.ItemKind.TEXT -> {
                _preview.value = PreviewState.TextLoading(item)
                viewModelScope.launch {
                    try {
                        val vo = repository.fetchTextContent(code, item.id)
                        writePreview(generation, PreviewState.TextReady(item, vo.textContent))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: DeliveryException) {
                        AppLogger.warn(TAG, "文本预览业务拒绝 businessCode=${e.businessCode} itemId=${item.id}")
                        closePreviewWithError(generation, toastFor(e, R.string.toolbox_delivery_preview_error))
                    } catch (e: NetworkException) {
                        AppLogger.error(TAG, "DeliverySessionViewModel.openItem: ${e.message}", e)
                        closePreviewWithError(generation, ToastEvent(R.string.toolbox_delivery_preview_error, informativeMessage(e.message)))
                    } catch (e: Exception) {
                        AppLogger.error(TAG, "DeliverySessionViewModel.openItem: ${e.message}", e)
                        closePreviewWithError(generation, ToastEvent(R.string.toolbox_delivery_preview_error))
                    }
                }
            }
            DeliveryRules.ItemKind.IMAGE -> _preview.value = PreviewState.ConfirmImage(item)
            DeliveryRules.ItemKind.FILE -> _preview.value = PreviewState.ConfirmFile(item)
        }
    }

    /** 图片：take 原子认领 → 内容流落缓存 → 解码预览（流完成服务端即焚毁） */
    fun takeImageForPreview() {
        val confirm = _preview.value as? PreviewState.ConfirmImage ?: return
        downloadItemContent(confirm.item, claimed = false)
    }

    /** 文件：take 认领 → 内容流直存系统「下载」目录（保存动作即焚毁） */
    fun takeFileAndSave() {
        val confirm = _preview.value as? PreviewState.ConfirmFile ?: return
        downloadItemContent(confirm.item, claimed = false)
    }

    /** 失败态重试入口：按条目类型续走「下载 → 预览/落盘」（认领已完成时跳过 take） */
    fun retryContentTake() {
        val failed = _preview.value as? PreviewState.ContentFailed ?: return
        downloadItemContent(failed.item, claimed = failed.claimed)
    }

    /**
     * 图片/文件共用内容获取编排。契约要求文件类条目先 take 认领才可拉内容（未认领 409），
     * 无法改为「先下载后 take」；P0 端侧兜底落在失败分支——认领成功而内容未到手时
     * **不进入焚毁语义**：条目记入 [_retryableItemIds] 保持待取走可点、弹窗转可重试错误态，
     * 也不触发会话关闭类清理（仅本地缓存清扫），绝不静默吞掉。
     */
    private fun downloadItemContent(item: DeliveryItemVo, claimed: Boolean) {
        val code = sessionCode ?: return
        if (_previewAction.value is RequestState.Loading) return
        val generation = previewGeneration
        viewModelScope.launch {
            _previewAction.value = RequestState.Loading
            var claimedNow = claimed
            try {
                if (!claimedNow) {
                    repository.takeItem(code, item.id)
                    claimedNow = true
                }
                val content = repository.openFileContent(code, item.id)
                when (DeliveryRules.ItemKind.fromCode(item.kind)) {
                    DeliveryRules.ItemKind.IMAGE -> {
                        val cache = writeToCache(
                            appContext,
                            content.stream,
                            item.title ?: "delivery_image_${item.id}",
                        )
                        writePreview(previewGeneration, PreviewState.ImageReady(item, decodeSampledBitmap(cache), cache))
                    }
                    else -> {
                        saveDeliveryContent(
                            context = appContext,
                            isImage = false,
                            displayName = item.title ?: "delivery_file_${item.id}",
                            mimeType = item.mimeType,
                            source = content.stream,
                        )
                        AppLogger.info(TAG, "投递文件已保存并焚毁 itemId=${item.id}")
                        writePreview(previewGeneration, null)
                        _toast.value = ToastEvent(R.string.toolbox_delivery_toast_saved_file)
                        refreshNow()
                    }
                }
                // 内容已到手，恢复「已取走 = 焚毁占位」语义
                _retryableItemIds.value = _retryableItemIds.value - item.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: DeliveryException) {
                AppLogger.warn(TAG, "内容获取业务拒绝 businessCode=${e.businessCode} itemId=${item.id}")
                onContentDownloadFailure(generation, item, claimedNow, toastFor(e, R.string.toolbox_delivery_preview_error))
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DeliverySessionViewModel.downloadItemContent: ${e.message}", e)
                onContentDownloadFailure(
                    generation,
                    item,
                    claimedNow,
                    ToastEvent(R.string.toolbox_delivery_preview_error, informativeMessage(e.message)),
                )
            } catch (e: DeliveryMediaException) {
                // 媒体库写入失败：内容流已到手（服务端随流焚毁，中断自动释放认领），非认领链路问题，
                // 维持原提示口径——条目可待下次认领重试
                AppLogger.error(TAG, "内容落盘媒体库写入失败 resId=${e.resId}", e)
                closePreviewWithError(generation, ToastEvent(e.resId))
            } catch (e: Exception) {
                AppLogger.error(TAG, "DeliverySessionViewModel.downloadItemContent: ${e.message}", e)
                onContentDownloadFailure(generation, item, claimedNow, ToastEvent(R.string.toolbox_delivery_preview_error))
            } finally {
                _previewAction.value = RequestState.Idle
            }
        }
    }

    /** 内容获取失败分流：认领未完成走原口径（收起弹窗提示）；认领已成功则转可重试错误态 */
    private suspend fun onContentDownloadFailure(generation: Int, item: DeliveryItemVo, claimed: Boolean, event: ToastEvent) {
        if (!claimed) {
            closePreviewWithError(generation, event)
            return
        }
        _retryableItemIds.value = _retryableItemIds.value + item.id
        writePreview(generation, PreviewState.ContentFailed(item, claimed = true))
        refreshNow()
    }

    /** 文本：复制即触发 take + 焚毁（认领响应的正文优先，兜底预览正文） */
    fun copyTextAndTake() {
        val ready = _preview.value as? PreviewState.TextReady ?: return
        val code = sessionCode ?: return
        runPreviewAction {
            val take = repository.takeItem(code, ready.item.id)
            val text = take.textContent ?: ready.text.orEmpty()
            copyToClipboard(appContext, text)
            AppLogger.info(TAG, "投递文本已复制并焚毁 itemId=${ready.item.id}")
            writePreview(previewGeneration, null)
            _toast.value = ToastEvent(R.string.toolbox_delivery_toast_copy_success)
            refreshNow()
        }
    }

    /** 图片：从缓存文件保存到相册（Pictures/SmartWash） */
    fun saveImageToGallery() {
        val ready = _preview.value as? PreviewState.ImageReady ?: return
        runPreviewAction {
            saveDeliveryContent(
                context = appContext,
                isImage = true,
                displayName = ready.item.title ?: "delivery_image_${ready.item.id}",
                mimeType = ready.item.mimeType,
                source = ready.cacheFile.inputStream(),
            )
            AppLogger.info(TAG, "投递图片已保存相册并焚毁 itemId=${ready.item.id}")
            writePreview(previewGeneration, null)
            _toast.value = ToastEvent(R.string.toolbox_delivery_toast_saved_gallery)
            refreshNow()
        }
    }

    /** 关闭预览（清扫预览缓存；在途动作完成后因代次不匹配不再回写弹窗） */
    fun dismissPreview() {
        previewGeneration++
        _preview.value = null
        if (_previewAction.value !is RequestState.Loading) {
            _previewAction.value = RequestState.Idle
        }
        viewModelScope.launch { cleanupDeliveryCache(appContext) }
    }

    /** 手动关闭会话（读路径立即失效；成功后页面提示并退出） */
    fun closeSession() {
        val code = sessionCode ?: return
        if (_closeState.value is RequestState.Loading) return
        viewModelScope.launch {
            _closeState.value = RequestState.Loading
            try {
                repository.closeSession(code)
                _closeState.value = RequestState.Success
                AppLogger.info(TAG, "投递会话关闭成功 code=$code")
                pollingJob?.cancel()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DeliverySessionViewModel.closeSession: ${e.message}", e)
                _closeState.value = RequestState.Error(e.resId, e.message)
            } catch (e: DeliveryException) {
                AppLogger.warn(TAG, "DeliverySessionViewModel.closeSession: 业务拒绝 businessCode=${e.businessCode}")
                _closeState.value = RequestState.Error(R.string.toolbox_delivery_error_close, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "DeliverySessionViewModel.closeSession: ${e.message}", e)
                _closeState.value = RequestState.Error(R.string.toolbox_delivery_error_close)
            }
        }
    }

    fun resetCloseState() {
        _closeState.value = RequestState.Idle
    }

    fun consumeToast() {
        _toast.value = null
    }

    /** 会话失效事件消费（页面提示并退出后复位，防重组重复处理） */
    fun consumeSessionGone() {
        _sessionGone.value = null
    }

    /** 预览动作统一编排：loading 态 + 错误转提示并收起弹窗（409 冲突提示「已被取走」） */
    private fun runPreviewAction(block: suspend () -> Unit) {
        if (_previewAction.value is RequestState.Loading) return
        val generation = previewGeneration
        viewModelScope.launch {
            _previewAction.value = RequestState.Loading
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: DeliveryException) {
                AppLogger.warn(TAG, "预览动作业务拒绝 businessCode=${e.businessCode}")
                closePreviewWithError(generation, toastFor(e, R.string.toolbox_delivery_preview_error))
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DeliverySessionViewModel.runPreviewAction: ${e.message}", e)
                closePreviewWithError(generation, ToastEvent(R.string.toolbox_delivery_preview_error, informativeMessage(e.message)))
            } catch (e: DeliveryMediaException) {
                // 媒体库写入失败：文案由 saver 以资源 id 携带，此处映射为 Toast（无硬编码中文）
                AppLogger.error(TAG, "预览动作媒体库写入失败 resId=${e.resId}", e)
                closePreviewWithError(generation, ToastEvent(e.resId))
            } catch (e: Exception) {
                AppLogger.error(TAG, "DeliverySessionViewModel.runPreviewAction: ${e.message}", e)
                closePreviewWithError(generation, ToastEvent(R.string.toolbox_delivery_preview_error))
            } finally {
                _previewAction.value = RequestState.Idle
            }
        }
    }

    private fun toastFor(e: DeliveryException, fallbackRes: Int): ToastEvent =
        if (e.businessCode == DeliveryBusinessCode.CONFLICT) {
            ToastEvent(R.string.toolbox_delivery_item_taken_toast, e.message)
        } else {
            // 业务文案（如「投递会话不存在或已失效」）保留原文；泛化兜底词回退端侧文案
            ToastEvent(fallbackRes, informativeMessage(e.message))
        }

    /** 过滤后端泛化原文：保留有信息量的业务文案，泛化词返回 null 由调用方回退 resId 展示 */
    private fun informativeMessage(message: String?): String? =
        message?.takeIf { it.isNotBlank() && it !in GENERIC_BACKEND_MESSAGES }

    private suspend fun closePreviewWithError(generation: Int, event: ToastEvent) {
        writePreview(generation, null)
        _toast.value = event
        refreshNow()
    }

    /** 弹窗回写须通过本函数：代次不匹配（已 dismiss 或已切到新条目）的迟到结果一律丢弃 */
    private fun writePreview(generation: Int, state: PreviewState?) {
        if (previewGeneration == generation) {
            _preview.value = state
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }
}

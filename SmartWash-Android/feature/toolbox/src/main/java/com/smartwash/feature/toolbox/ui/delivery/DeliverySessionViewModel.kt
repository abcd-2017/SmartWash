package com.smartwash.feature.toolbox.ui.delivery

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.logic.DeliveryRules
import com.smartwash.feature.toolbox.network.DeliveryBusinessCode
import com.smartwash.feature.toolbox.network.DeliveryException
import com.smartwash.feature.toolbox.network.DeliveryItemVo
import com.smartwash.feature.toolbox.network.DeliverySessionVo
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
                        markSessionGone(e.message)
                        break
                    }
                    onPollFailure(e.message)
                    intervalMs = backoff(intervalMs)
                } catch (e: NetworkException) {
                    onPollFailure(e.message)
                    intervalMs = backoff(intervalMs)
                } catch (e: Exception) {
                    onPollFailure(null)
                    intervalMs = backoff(intervalMs)
                }
                delay(intervalMs)
            }
        }
    }

    private fun onPollFailure(message: String?) {
        if (_loadState.value is RequestState.Loading) {
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
                refreshNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _sendState.value = RequestState.Error(e.resId, e.message)
            } catch (e: DeliveryException) {
                _sendState.value = RequestState.Error(R.string.toolbox_delivery_error_send, e.message)
            } catch (e: Exception) {
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
                refreshNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _sendState.value = RequestState.Error(e.resId, e.message)
            } catch (e: DeliveryException) {
                _sendState.value = RequestState.Error(R.string.toolbox_delivery_error_send, e.message)
            } catch (e: Exception) {
                _sendState.value = RequestState.Error(R.string.toolbox_delivery_error_send)
            }
        }
    }

    // ---------- 条目预览与取走（保存/复制即触发 take + 焚毁） ----------

    /** 点条目：文本先读内容（非破坏预览），图片/文件出取走确认（内容获取须先认领） */
    fun openItem(item: DeliveryItemVo) {
        if (item.taken != 0) return
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
                        closePreviewWithError(generation, toastFor(e, R.string.toolbox_delivery_preview_error))
                    } catch (e: NetworkException) {
                        closePreviewWithError(generation, ToastEvent(R.string.toolbox_delivery_preview_error, e.message))
                    } catch (e: Exception) {
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
        val code = sessionCode ?: return
        runPreviewAction {
            repository.takeItem(code, confirm.item.id)
            val content = repository.openFileContent(code, confirm.item.id)
            val cache = writeToCache(
                appContext,
                content.stream,
                confirm.item.title ?: "delivery_image_${confirm.item.id}",
            )
            writePreview(previewGeneration, PreviewState.ImageReady(confirm.item, decodeSampledBitmap(cache), cache))
        }
    }

    /** 文件：take 认领 → 内容流直存系统「下载」目录（保存动作即焚毁） */
    fun takeFileAndSave() {
        val confirm = _preview.value as? PreviewState.ConfirmFile ?: return
        val code = sessionCode ?: return
        runPreviewAction {
            repository.takeItem(code, confirm.item.id)
            val content = repository.openFileContent(code, confirm.item.id)
            saveDeliveryContent(
                context = appContext,
                isImage = false,
                displayName = confirm.item.title ?: "delivery_file_${confirm.item.id}",
                mimeType = confirm.item.mimeType,
                source = content.stream,
            )
            writePreview(previewGeneration, null)
            _toast.value = ToastEvent(R.string.toolbox_delivery_toast_saved_file)
            refreshNow()
        }
    }

    /** 文本：复制即触发 take + 焚毁（认领响应的正文优先，兜底预览正文） */
    fun copyTextAndTake() {
        val ready = _preview.value as? PreviewState.TextReady ?: return
        val code = sessionCode ?: return
        runPreviewAction {
            val take = repository.takeItem(code, ready.item.id)
            val text = take.textContent ?: ready.text.orEmpty()
            copyToClipboard(appContext, text)
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
                pollingJob?.cancel()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _closeState.value = RequestState.Error(e.resId, e.message)
            } catch (e: DeliveryException) {
                _closeState.value = RequestState.Error(R.string.toolbox_delivery_error_close, e.message)
            } catch (e: Exception) {
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
                closePreviewWithError(generation, toastFor(e, R.string.toolbox_delivery_preview_error))
            } catch (e: NetworkException) {
                closePreviewWithError(generation, ToastEvent(R.string.toolbox_delivery_preview_error, e.message))
            } catch (e: Exception) {
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
            ToastEvent(fallbackRes, e.message)
        }

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

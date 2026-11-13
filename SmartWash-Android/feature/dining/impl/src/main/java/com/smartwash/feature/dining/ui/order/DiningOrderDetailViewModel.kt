package com.smartwash.feature.dining.ui.order

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.api.model.AddDiningOrderItemRequest
import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import com.smartwash.feature.dining.api.model.UpdateDiningOrderItemRequest
import com.smartwash.feature.dining.logic.DiningCartReplayOp
import com.smartwash.feature.dining.logic.DiningCartState
import com.smartwash.feature.dining.logic.DiningCartSyncCoordinator
import com.smartwash.feature.dining.logic.DiningOrderPageDetail
import com.smartwash.feature.dining.logic.DiningSyncLifecycle
import com.smartwash.feature.dining.logic.DiningSyncLifecycle.Command
import com.smartwash.feature.dining.logic.DiningSyncLifecycle.Event
import com.smartwash.feature.dining.logic.DiningVersionConflictException
import com.smartwash.feature.dining.logic.resolveMyUserId
import com.smartwash.feature.dining.logic.toOrderPageDetail
import com.smartwash.feature.dining.repository.DiningRepository
import com.smartwash.feature.dining.sync.DiningOrderSyncChannel
import com.smartwash.feature.user.api.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

/** 全量拉取分页大小（与创建页一致：后端 BaseSearchFrom 上限 100） */
private const val PAGE_SIZE = 100

/**
 * 点单页 ViewModel（09 号票落地 + 10 号票共享购物车同步）。
 *
 * 同步链路：
 * - [DiningOrderSyncChannel]（传输层可替换抽象）负责「订阅订单变更」——点单页可见时经轮询
 *   带 knownVersion 拉详情，unchanged 轻量处理；页面不可见由 [DiningSyncLifecycle] 停止收集；
 * - [DiningCartSyncCoordinator]（纯状态机）维护服务端真相与本地意图，写操作携带已知 version，
 *   收到 409 → 拉取最新 → 以本地意图为基准重放差分；写失败回滚本地态到服务端真相并提示。
 *
 * 当前用户标识：详情成员只回传脱敏手机号，端上无 userId 契约，故经 [UserApi.getUserInfo]
 * 的手机号按同源脱敏规则比对定位（[resolveMyUserId]）；未取到即不识别「我的选菜」（降级不误判）。
 *
 * 状态划分：[loadState] 主流程 / [detail] 页面模型 / [cart] 本地意图 / [pendingRecipeIds] 未决区分 /
 * [syncPending] 同步中提示 / [message] 一次性提示。
 */
@HiltViewModel
class DiningOrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val diningRepository: DiningRepository,
    private val syncChannel: DiningOrderSyncChannel,
    private val userApi: UserApi,
    private val application: Application,
) : ViewModel() {

    /** 路由参数：订单 ID（0=非法，不发起请求） */
    val orderId: Long = savedStateHandle.get<Long>(ARG_ORDER_ID) ?: 0L

    private val _loadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadState = _loadState.asStateFlow()

    private val _detail = MutableStateFlow<DiningOrderPageDetail?>(null)
    val detail = _detail.asStateFlow()

    /** 本地意图（用户即时操作后的期望选菜 + 备注） */
    private val _cart = MutableStateFlow(DiningCartState())
    val cart = _cart.asStateFlow()

    /** 未决操作涉及的菜品（页面轻微视觉区分） */
    private val _pendingRecipeIds = MutableStateFlow<Set<Long>>(emptySet())
    val pendingRecipeIds = _pendingRecipeIds.asStateFlow()

    /** 是否有未决工作（待写差分或待刷新版本），底部栏提示「同步中」 */
    private val _syncPending = MutableStateFlow(false)
    val syncPending = _syncPending.asStateFlow()

    /** 一次性提示（页面 Toast 后consume；空=无待提示） */
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    /** 结束点单请求态（Idle/Loading/Success/Error；成功由页面导航到购买清单结果页） */
    private val _finishState = MutableStateFlow<RequestState>(RequestState.Idle)
    val finishState = _finishState.asStateFlow()

    /** 共享购物车同步状态机（纯 Kotlin） */
    private val coordinator = DiningCartSyncCoordinator()

    /** 订阅生命周期状态机（纯 Kotlin） */
    private val syncLifecycle = DiningSyncLifecycle()

    /** 当前用户标识（首载时解析；null=不识别我的选菜） */
    private var myUserId: Long? = null

    /** 分类目录/封面（渲染增强，首载时缓存） */
    private var categoryNames: Map<Long, String> = emptyMap()
    private var coverUrls: Map<Long, String> = emptyMap()

    private val uncategorizedLabel: String by lazy {
        application.getString(R.string.dining_order_detail_uncategorized)
    }

    /** 订阅任务（页面可见时启动，不可见/销毁时取消——取消即停止轮询，防泄露） */
    private var syncJob: Job? = null

    /** 写操作串行锁：保证同一时刻只有一笔写，版本基准始终最新 */
    private val writeMutex = Mutex()

    /** 首载/重试（防重：加载中忽略） */
    fun load() {
        if (_loadState.value is RequestState.Loading) return
        if (orderId <= 0L) {
            _loadState.value = RequestState.Error(R.string.dining_order_detail_load_failed)
            return
        }
        viewModelScope.launch {
            _loadState.value = RequestState.Loading
            try {
                val detailVo = diningRepository.getOrderDetail(orderId)
                val phone = userApi.getUserInfo()?.phoneNumber
                val myId = resolveMyUserId(detailVo.safeMembers, phone)
                // 分类目录/菜谱封面是渲染增强，失败不阻断主流程（分类名按「分类 {id}」兜底、封面占位）
                val categories = soften { diningRepository.getCategories() } ?: emptyList()
                val covers = soften { loadAllRecipeCovers() } ?: emptyMap()
                myUserId = myId
                categoryNames = categories.associate { it.safeId to it.safeName }
                coverUrls = covers
                coordinator.onInitialSnapshot(detailVo.safeVersion, myItemsOf(detailVo, myId))
                _detail.value = detailVo.toOrderPageDetail(
                    myUserId = myId,
                    categoryNames = categoryNames,
                    coverUrls = covers,
                    uncategorizedLabel = uncategorizedLabel,
                )
                publishCart()
                _loadState.value = RequestState.Success
                onLifecycle(Event.FirstLoadReady)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DiningOrderDetailViewModel.load: ${e.message}", e)
                _loadState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 页面可见（Lifecycle ON_START / 首次进入）：启动订阅 */
    fun onPageVisible() {
        onLifecycle(Event.PageVisible)
    }

    /** 页面不可见（Lifecycle ON_STOP）：停止订阅 */
    fun onPageInvisible() {
        onLifecycle(Event.PageInvisible)
    }

    /**
     * 勾选/取消（本地意图即时生效 + 异步写）。
     * 写操作携带当前已知 version；冲突/失败由写循环收口（重放/回滚 + 提示）。
     */
    fun toggleRecipe(recipeId: Long) {
        if (recipeId <= 0L) return
        val current = _detail.value ?: return
        if (!current.isOngoing) {
            _message.value = application.getString(R.string.dining_order_detail_finished_locked)
            return
        }
        coordinator.onToggle(recipeId)
        publishCart()
        requestWrite()
    }

    /** 改备注（本地意图即时生效 + 异步写；未选该菜忽略） */
    fun updateNote(recipeId: Long, note: String) {
        if (recipeId <= 0L) return
        val current = _detail.value ?: return
        if (!current.isOngoing) {
            _message.value = application.getString(R.string.dining_order_detail_finished_locked)
            return
        }
        if (!coordinator.cart.isSelected(recipeId)) return
        coordinator.onNote(recipeId, note)
        publishCart()
        requestWrite()
    }

    /**
     * 结束点单（仅发起者且订单进行中）：调 finish 接口锁定订单并生成购买清单快照。
     * 成功由页面导航到购买清单结果页；失败（含非发起者/状态异常）以 [RequestState.Error] 上抛提示。
     */
    fun finishOrder() {
        val current = _detail.value ?: return
        if (!current.isOngoing || !current.isCreator) return
        if (_finishState.value is RequestState.Loading) return
        viewModelScope.launch {
            _finishState.value = RequestState.Loading
            try {
                diningRepository.finishOrder(orderId)
                _finishState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "结束点单失败 orderId=$orderId: ${e.message}", e)
                _finishState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 消费结束点单请求态（页面导航/Toast 后调用，避免重放） */
    fun consumeFinishState() {
        _finishState.value = RequestState.Idle
    }

    /** 消费一次性提示（页面 Toast 后调用，避免重放） */
    fun consumeMessage() {
        _message.value = null
    }

    override fun onCleared() {
        onLifecycle(Event.Reset)
        syncJob?.cancel()
        super.onCleared()
    }

    // ==================== 订阅生命周期 ====================

    private fun onLifecycle(event: Event) {
        when (syncLifecycle.onEvent(event)) {
            Command.Start -> startSync()
            Command.Stop -> stopSync()
            Command.None -> Unit
        }
    }

    /** 启动订阅（幂等）：以当前详情 version 为 knownVersion，变更时经通道流回灌 */
    private fun startSync() {
        if (orderId <= 0L) return
        val current = _detail.value ?: return
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            syncChannel.subscribe(orderId, current.version).collect { vo ->
                if (vo.safeUnchanged) {
                    // 轻量响应：只收敛新鲜度/执行挂起回滚，不重建页面
                    coordinator.onUnchanged()
                    publishCart()
                } else {
                    applySnapshot(vo)
                }
            }
        }
    }

    private fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }

    /**
     * 合并服务端快照（含他人变更）：更新页面模型与状态机；
     * 状态机返回的差分非空即触发写循环在新基准上重放本地意图。
     */
    private fun applySnapshot(vo: DiningOrderDetailVo) {
        if (vo.safeVersion < coordinator.serverVersion) return
        val replayOps = coordinator.onSnapshot(vo.safeVersion, myItemsOf(vo, myUserId))
        _detail.value = vo.toOrderPageDetail(myUserId, categoryNames, coverUrls, uncategorizedLabel)
        publishCart()
        if (replayOps.isNotEmpty()) requestWrite()
    }

    // ==================== 写循环（版本乐观并发） ====================

    /** 触发写循环（已有循环在跑则并入，不并发） */
    private fun requestWrite() {
        if (orderId <= 0L) return
        viewModelScope.launch {
            if (!writeMutex.tryLock()) return@launch
            try {
                runWriteLoop()
            } finally {
                writeMutex.unlock()
            }
        }
    }

    /**
     * 串行写循环：刷新 → 取差分首项 → 写。
     * - 成功：版本未知，先刷新再写下一笔；
     * - 409：拉取最新（含他人变更）后按本地意图重放差分；
     * - 失败：回滚该菜本地态、提示，并拉取服务端最新。
     */
    private suspend fun runWriteLoop() {
        while (currentCoroutineContext().isActive) {
            if (coordinator.needsRefresh && !refreshFromServer()) return
            val op = coordinator.pendingWrite() ?: return
            val version = coordinator.serverVersion
            try {
                writeOp(op, version)
                coordinator.onWriteSuccess()
                AppLogger.info(TAG, "选菜写入成功 orderId=$orderId recipeId=${op.recipeId} version=$version")
            } catch (e: CancellationException) {
                throw e
            } catch (e: DiningVersionConflictException) {
                AppLogger.info(TAG, "选菜版本冲突，拉取最新后重放 orderId=$orderId recipeId=${op.recipeId} version=$version")
                coordinator.onWriteConflict()
                if (!refreshFromServer()) return
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "选菜写入失败 orderId=$orderId recipeId=${op.recipeId} version=$version: ${e.message}", e)
                coordinator.onWriteFailure(op.recipeId)
                _message.value = e.message
                    ?: application.getString(R.string.dining_order_detail_write_failed)
                publishCart()
                if (!refreshFromServer()) return
            } catch (e: Exception) {
                AppLogger.error(TAG, "选菜写入未知失败 orderId=$orderId recipeId=${op.recipeId}", e)
                coordinator.onWriteFailure(op.recipeId)
                _message.value = application.getString(R.string.dining_order_detail_write_failed)
                publishCart()
                if (!refreshFromServer()) return
            }
        }
    }

    /** 把差分操作写成后端契约（version 为发起时的已知版本） */
    private suspend fun writeOp(op: DiningCartReplayOp, version: Long) {
        when (op) {
            is DiningCartReplayOp.Add -> diningRepository.addOrderItem(
                orderId,
                AddDiningOrderItemRequest(
                    recipeId = op.recipeId,
                    note = op.note.ifBlank { null },
                    version = version,
                ),
            )
            is DiningCartReplayOp.Remove -> diningRepository.removeOrderItem(orderId, op.recipeId, version)
            is DiningCartReplayOp.UpdateNote -> diningRepository.updateOrderItemNote(
                orderId,
                UpdateDiningOrderItemRequest(
                    recipeId = op.recipeId,
                    note = op.note.ifBlank { null },
                    version = version,
                ),
            )
        }
    }

    /**
     * 拉取最新详情并合并（写后刷新/冲突重放/失败回滚共用）。
     * 固定全量拉取（knownVersion=null）：写失败不前进 version，轻量协议会返回 unchanged
     * 而拿不到最新选菜记录，无法完成回滚；写频率低，全量可接受。
     * 返回 false 表示拉取失败：写循环退出，交由下一次快照或用户操作重试（自愈）。
     */
    private suspend fun refreshFromServer(): Boolean {
        return try {
            val vo = diningRepository.getOrderDetail(orderId)
            if (vo.safeUnchanged) coordinator.onUnchanged() else applySnapshot(vo)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "写后刷新失败 orderId=$orderId: ${e.message}")
            false
        }
    }

    // ==================== 辅助 ====================

    /** 由详情选菜记录取当前用户条目（他人记录一律排除——重放永不覆盖他人变更） */
    private fun myItemsOf(vo: DiningOrderDetailVo, myId: Long?): Map<Long, String> {
        if (myId == null) return emptyMap()
        return vo.safeItems
            .filter { it.safeUserId == myId }
            .associate { it.safeRecipeId to it.safeNote }
    }

    /** 推送本地意图与未决标识到页面 */
    private fun publishCart() {
        _cart.value = coordinator.cart
        _pendingRecipeIds.value = coordinator.pendingRecipeIds()
        _syncPending.value = coordinator.hasPendingWork()
    }

    /** 上架菜谱封面映射全量拉取（page 递增至取满；仅作封面增强，失败由 soften 吞掉） */
    private suspend fun loadAllRecipeCovers(): Map<Long, String> {
        val covers = mutableMapOf<Long, String>()
        var fetched = 0L
        var page = 1
        while (page <= MAX_PAGE_COUNT) {
            val pageData = diningRepository.getRecipes(page, PAGE_SIZE)
            pageData.records.forEach { recipe ->
                val url = recipe.safeCoverUrl
                if (url.isNotBlank()) covers[recipe.safeId] = url
            }
            fetched += pageData.records.size
            if (pageData.records.size < PAGE_SIZE || fetched >= pageData.total) break
            page++
        }
        return covers
    }

    /** 渲染增强型请求的失败软化（重抛取消，其余返回 null 由调用方兜底） */
    private suspend fun <T> soften(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.warn(TAG, "渲染增强请求失败，按兜底继续: ${e.message}")
        null
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "DiningOrderDetailViewModel"

        /** 类型安全路由参数键（DiningRoute.OrderDetail 属性名即键名） */
        const val ARG_ORDER_ID = "orderId"

        /** 分页循环防御上限（100×100=1 万道菜，远超现实量级；触发即数据异常） */
        const val MAX_PAGE_COUNT = 100
    }
}

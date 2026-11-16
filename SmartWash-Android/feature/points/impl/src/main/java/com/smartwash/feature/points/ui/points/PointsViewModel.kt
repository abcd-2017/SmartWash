package com.smartwash.feature.points.ui.points

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.points.api.model.PointLedgerItemVo
import com.smartwash.feature.points.api.model.PointsLeaderboardVo
import com.smartwash.feature.points.api.model.PointsOverviewVo
import com.smartwash.feature.points.api.model.SignInResultVo
import com.smartwash.feature.points.domain.updatedWith
import com.smartwash.feature.points.repository.PointsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 积分流水分页大小（后端 ledger size 上限 50，取 10 与站内信/订单列表对齐） */
private const val LEDGER_PAGE_SIZE = 10

/**
 * 四路状态独立（请求状态全程 [RequestState]）：
 * - [loadState]：/me 概览（余额/签到），决定整页骨架/错误/内容；
 * - [leaderboardState]：月榜独立加载——榜单失败不拖垮签到/余额/流水区块（区块内小态 + 重试）；
 * - [ledgerState] / [ledgerLoadMoreState]：流水独立分页（首载 + 加载更多），失败仅卡内小态；
 * - [signInState]：签到动作态，Loading 期间按钮禁点（防重第一层，AppButton/自绘 CTA
 *   的防抖与禁点为第二层，后端幂等为最终兜底）。
 *
 * 流水不再取自 /me 的 recentLedger，改由 [getLedger] 分页加载（时间倒序，触底加载更多）。
 * 签到成功 = SignInResultVo 乐观并入概览（余额/连签/已签立即生效）+ 静默刷新概览、月榜与
 * 流水首页（名次/流水以后端为准）；刷新失败不影响乐观结果，仅日志留痕。
 */
@HiltViewModel
class PointsViewModel @Inject constructor(
    private val pointsRepository: PointsRepository,
) : ViewModel() {

    private val _loadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadState = _loadState.asStateFlow()

    private val _overview = MutableStateFlow<PointsOverviewVo?>(null)
    val overview = _overview.asStateFlow()

    private val _leaderboardState = MutableStateFlow<RequestState>(RequestState.Idle)
    val leaderboardState = _leaderboardState.asStateFlow()

    private val _leaderboard = MutableStateFlow<PointsLeaderboardVo?>(null)
    val leaderboard = _leaderboard.asStateFlow()

    /** 积分流水（分页累积，时间倒序） */
    private val _ledger = MutableStateFlow<List<PointLedgerItemVo>>(emptyList())
    val ledger = _ledger.asStateFlow()

    /** 流水首载态（Idle/Loading/Success/Error），驱动卡内骨架/错误小态 */
    private val _ledgerState = MutableStateFlow<RequestState>(RequestState.Idle)
    val ledgerState = _ledgerState.asStateFlow()

    /** 是否还有未加载的流水（records 累积数 < 后端 total） */
    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()

    /** 流水加载更多态（独立于首载，失败不影响已加载列表） */
    private val _ledgerLoadMoreState = MutableStateFlow<RequestState>(RequestState.Idle)
    val ledgerLoadMoreState = _ledgerLoadMoreState.asStateFlow()

    private var ledgerPage = 1
    private var ledgerTotal = 0L

    private val _signInState = MutableStateFlow<RequestState>(RequestState.Idle)
    val signInState = _signInState.asStateFlow()

    /** 最近一次签到结果载荷（幂等命中也保留），页面据此区分「签到成功 +N」与「今日已签」反馈 */
    private val _lastSignInResult = MutableStateFlow<SignInResultVo?>(null)
    val lastSignInResult = _lastSignInResult.asStateFlow()

    /** 首载概览（页面 LaunchedEffect 触发；重试复用） */
    fun load() {
        viewModelScope.launch {
            _loadState.value = RequestState.Loading
            try {
                _overview.value = pointsRepository.getOverview()
                _loadState.value = RequestState.Success
            } catch (e: CancellationException) {
                // 取消不作为网络错误，直接上抛
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "PointsViewModel.load: ${e.message}", e)
                _loadState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 首载/重试月榜（独立状态，失败不污染整页） */
    fun loadLeaderboard() {
        viewModelScope.launch {
            _leaderboardState.value = RequestState.Loading
            try {
                _leaderboard.value = pointsRepository.getMonthLeaderboard()
                _leaderboardState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "PointsViewModel.loadLeaderboard: ${e.message}", e)
                _leaderboardState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 首载/重试流水首页（防重：加载中直接忽略） */
    fun loadLedger() {
        if (_ledgerState.value is RequestState.Loading) return
        viewModelScope.launch {
            _ledgerState.value = RequestState.Loading
            try {
                val page = pointsRepository.getLedger(1, LEDGER_PAGE_SIZE)
                ledgerPage = 1
                ledgerTotal = page.total
                _ledger.value = page.records
                _hasMore.value = page.records.size < page.total
                _ledgerState.value = RequestState.Success
            } catch (e: CancellationException) {
                // 取消不作为网络错误，直接上抛
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "PointsViewModel.loadLedger: ${e.message}", e)
                _ledgerState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 加载更多流水（防重：加载中/无更多直接忽略；失败保留已加载列表，卡内 footer 出重试） */
    fun loadMoreLedger() {
        if (_ledgerLoadMoreState.value is RequestState.Loading || !_hasMore.value) return
        viewModelScope.launch {
            _ledgerLoadMoreState.value = RequestState.Loading
            try {
                val nextPage = ledgerPage + 1
                val page = pointsRepository.getLedger(nextPage, LEDGER_PAGE_SIZE)
                ledgerPage = nextPage
                ledgerTotal = page.total
                _ledger.value = _ledger.value + page.records
                _hasMore.value = _ledger.value.size < ledgerTotal
                _ledgerLoadMoreState.value = RequestState.Success
            } catch (e: CancellationException) {
                // 取消不作为网络错误，直接上抛
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "PointsViewModel.loadMoreLedger: ${e.message}", e)
                _ledgerLoadMoreState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /**
     * 每日签到（防重：Loading 期间重复调用直接忽略——与按钮禁点/防抖共同构成端上防重，
     * 后端幂等为最终兜底）。成功后乐观并入概览 + 静默刷新概览、月榜与流水首页。
     */
    fun signIn() {
        if (_signInState.value is RequestState.Loading) return
        viewModelScope.launch {
            _signInState.value = RequestState.Loading
            try {
                val result = pointsRepository.signIn()
                _lastSignInResult.value = result
                // 乐观局部更新：余额/连签/已签立即生效（幂等命中同样置已签，余额取当前值）
                _overview.value = _overview.value?.updatedWith(result)
                _signInState.value = RequestState.Success
                // 局部刷新流水与名次（静默：不回整页 Loading，失败保留乐观结果仅留痕）
                refreshQuietly()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "PointsViewModel.signIn: ${e.message}", e)
                _signInState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 页面消费完签到反馈后复位（Toast 触发在页面 LaunchedEffect，副作用不落渲染分支） */
    fun resetSignInState() {
        _signInState.value = RequestState.Idle
        _lastSignInResult.value = null
    }

    /** 签到后的静默刷新：概览（余额/连签以服务端为准）+ 月榜（名次变化）+ 流水首页（新增条目），失败仅日志留痕 */
    private suspend fun refreshQuietly() {
        try {
            _overview.value = pointsRepository.getOverview()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "签到后静默刷新概览失败（保留乐观结果）：${e.message}")
        }
        try {
            _leaderboard.value = pointsRepository.getMonthLeaderboard()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "签到后静默刷新月榜失败：${e.message}")
        }
        try {
            val page = pointsRepository.getLedger(1, LEDGER_PAGE_SIZE)
            ledgerPage = 1
            ledgerTotal = page.total
            _ledger.value = page.records
            _hasMore.value = page.records.size < page.total
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "签到后静默刷新流水失败：${e.message}")
        }
    }

    private companion object {
        const val TAG = "PointsViewModel"
    }
}

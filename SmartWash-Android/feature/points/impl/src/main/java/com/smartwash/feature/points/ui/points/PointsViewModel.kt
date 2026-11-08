package com.smartwash.feature.points.ui.points

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
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

/**
 * 签到有礼页 ViewModel。
 *
 * 三路状态独立（请求状态全程 [RequestState]）：
 * - [loadState]：/me 概览（余额/签到/最近流水），决定整页骨架/错误/内容；
 * - [leaderboardState]：月榜独立加载——榜单失败不拖垮签到/余额/流水区块（区块内小态 + 重试）；
 * - [signInState]：签到动作态，Loading 期间按钮禁点（防重第一层，AppButton/自绘 CTA
 *   的防抖与禁点为第二层，后端幂等为最终兜底）。
 *
 * 签到成功 = SignInResultVo 乐观并入概览（余额/连签/已签立即生效）+ 静默刷新概览与
 * 月榜（流水与名次以后端为准）；刷新失败不影响乐观结果，仅日志留痕。
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

    /**
     * 每日签到（防重：Loading 期间重复调用直接忽略——与按钮禁点/防抖共同构成端上防重，
     * 后端幂等为最终兜底）。成功后乐观并入概览 + 静默刷新概览与月榜。
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

    /** 签到后的静默刷新：概览（流水以服务端为准）+ 月榜（名次变化），失败仅日志留痕 */
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
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "PointsViewModel"
    }
}

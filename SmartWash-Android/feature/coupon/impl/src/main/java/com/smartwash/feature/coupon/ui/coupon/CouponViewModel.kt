package com.smartwash.feature.coupon.ui.coupon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.coupon.R
import com.smartwash.feature.coupon.network.vo.coupon.CouponVo
import com.smartwash.feature.coupon.network.vo.coupon.UserCouponVo
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.coupon.repository.CouponRepository
import com.smartwash.feature.coupon.repository.CouponSection
import com.smartwash.feature.coupon.repository.CouponSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 用户优惠券分页大小（后端 size 默认 10） */
private const val USER_COUPON_PAGE_SIZE = 10

/** 用户优惠券状态口径：0=可用（已领取 tab），1=已使用或过期（历史 tab） */
private const val COUPON_STATUS_AVAILABLE = 0
private const val COUPON_STATUS_HISTORICAL = 1

/**
 * 优惠券页状态（请求状态全程 [RequestState]）：
 * - [loadState]：首载整体态——仅驱动整页骨架/错误态；三组并行独立加载，部分失败不拖垮整页；
 * - 可领取（[availableCoupons]）：运营目录量小，不分页，Room cache-first 降级（[availableSource]）；
 * - 已领取 / 历史（[claimedCoupons] / [historicalCoupons]）：getUserCoupon 分页，各自首载态 +
 *   hasMore + 加载更多态，失败仅该 tab 内小态 + 重试，不影响其他 tab；
 * - [receiveCouponState]：领取动作态。
 *
 * 整页错误态仅在「三组皆无数据」时给出（此时各 tab 也无可渲染内容）；只要任一组有数据，
 * 即进入内容区，由失败 tab 自行出错误小态。
 */
@HiltViewModel
class CouponViewModel @Inject constructor(
    private val couponRepository: CouponRepository,
) : ViewModel() {

    private val _loadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadState = _loadState.asStateFlow()

    // ---- 可领取（不分页，Room cache-first 降级） ----
    private val _availableCoupons = MutableStateFlow<List<CouponVo>>(emptyList())
    val availableCoupons = _availableCoupons.asStateFlow()

    private val _availableSource = MutableStateFlow(CouponSource.NETWORK)
    val availableSource = _availableSource.asStateFlow()

    // ---- 已领取（status=0，分页） ----
    private val _claimedCoupons = MutableStateFlow<List<UserCouponVo>>(emptyList())
    val claimedCoupons = _claimedCoupons.asStateFlow()

    private val _claimedState = MutableStateFlow<RequestState>(RequestState.Idle)
    val claimedState = _claimedState.asStateFlow()

    private val _claimedHasMore = MutableStateFlow(false)
    val claimedHasMore = _claimedHasMore.asStateFlow()

    private val _claimedLoadMoreState = MutableStateFlow<RequestState>(RequestState.Idle)
    val claimedLoadMoreState = _claimedLoadMoreState.asStateFlow()

    private var claimedPage = 1
    private var claimedTotal = 0L

    // ---- 历史（status=1，分页） ----
    private val _historicalCoupons = MutableStateFlow<List<UserCouponVo>>(emptyList())
    val historicalCoupons = _historicalCoupons.asStateFlow()

    private val _historicalState = MutableStateFlow<RequestState>(RequestState.Idle)
    val historicalState = _historicalState.asStateFlow()

    private val _historicalHasMore = MutableStateFlow(false)
    val historicalHasMore = _historicalHasMore.asStateFlow()

    private val _historicalLoadMoreState = MutableStateFlow<RequestState>(RequestState.Idle)
    val historicalLoadMoreState = _historicalLoadMoreState.asStateFlow()

    private var historicalPage = 1
    private var historicalTotal = 0L

    private val _receiveCouponState = MutableStateFlow<RequestState>(RequestState.Idle)
    val receiveCouponState = _receiveCouponState.asStateFlow()

    /** 首载/重试：三组并行独立加载（防重：加载中直接忽略） */
    fun loadAllCoupons() {
        if (_loadState.value is RequestState.Loading) return
        _loadState.value = RequestState.Loading
        viewModelScope.launch {
            coroutineScope {
                launch { loadAvailable() }
                launch { loadClaimed() }
                launch { loadHistorical() }
            }
            // 三组皆空是合法空态（如新账号），交由各 tab 的空态承担，不再整页误报「加载失败」；
            // 真正的失败由各组的 NetworkException 分支置 CouponSource.FAILED 在 tab 内承担
            _loadState.value = RequestState.Success
        }
    }

    /** 加载更多已领取（防重：加载中/无更多直接忽略） */
    fun loadMoreClaimed() {
        if (_claimedLoadMoreState.value is RequestState.Loading || !_claimedHasMore.value) return
        viewModelScope.launch {
            _claimedLoadMoreState.value = RequestState.Loading
            try {
                val nextPage = claimedPage + 1
                val page = couponRepository.getUserCoupon(COUPON_STATUS_AVAILABLE, nextPage, USER_COUPON_PAGE_SIZE)
                claimedPage = nextPage
                claimedTotal = page.total
                _claimedCoupons.value = _claimedCoupons.value + page.records
                _claimedHasMore.value = _claimedCoupons.value.size < claimedTotal
                _claimedLoadMoreState.value = RequestState.Success
            } catch (e: CancellationException) {
                // 取消不作为网络错误，直接上抛
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "CouponViewModel.loadMoreClaimed: ${e.message}", e)
                _claimedLoadMoreState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 加载更多历史（防重：加载中/无更多直接忽略） */
    fun loadMoreHistorical() {
        if (_historicalLoadMoreState.value is RequestState.Loading || !_historicalHasMore.value) return
        viewModelScope.launch {
            _historicalLoadMoreState.value = RequestState.Loading
            try {
                val nextPage = historicalPage + 1
                val page = couponRepository.getUserCoupon(COUPON_STATUS_HISTORICAL, nextPage, USER_COUPON_PAGE_SIZE)
                historicalPage = nextPage
                historicalTotal = page.total
                _historicalCoupons.value = _historicalCoupons.value + page.records
                _historicalHasMore.value = _historicalCoupons.value.size < historicalTotal
                _historicalLoadMoreState.value = RequestState.Success
            } catch (e: CancellationException) {
                // 取消不作为网络错误，直接上抛
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "CouponViewModel.loadMoreHistorical: ${e.message}", e)
                _historicalLoadMoreState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun receiveCoupon(couponId: Long) {
        viewModelScope.launch {
            try {
                couponRepository.receiveCoupon(couponId)
                // 领取成功 = 低频关键动作成功，info 级（couponId 为业务键，无敏感信息）
                AppLogger.info(TAG, "领取优惠券成功，couponId=$couponId")
                _receiveCouponState.value = RequestState.Success
                // 领取改变可用目录与已领取列表，重载三组首页（历史不变，一并重置分页）
                loadAllCoupons()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "CouponViewModel.receiveCoupon: ${e.message}", e)
                _receiveCouponState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun resetReceiveState() {
        _receiveCouponState.value = RequestState.Idle
    }

    fun resetState() {
        _loadState.value = RequestState.Idle
    }

    /** 可领取：cache-first（网络失败回退 Room 缓存；无缓存上抛标 FAILED） */
    private suspend fun loadAvailable() {
        try {
            val section: CouponSection<CouponVo> = couponRepository.getAvailableCoupons()
            _availableCoupons.value = section.items
            _availableSource.value = section.source
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.error(TAG, "CouponViewModel.loadAvailable: ${e.message}", e)
            _availableCoupons.value = emptyList()
            _availableSource.value = CouponSource.FAILED
        }
    }

    /** 已领取首页（status=0） */
    private suspend fun loadClaimed() {
        _claimedState.value = RequestState.Loading
        try {
            val page = couponRepository.getUserCoupon(COUPON_STATUS_AVAILABLE, 1, USER_COUPON_PAGE_SIZE)
            claimedPage = 1
            claimedTotal = page.total
            _claimedCoupons.value = page.records
            _claimedHasMore.value = page.records.size < page.total
            _claimedState.value = RequestState.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException) {
            AppLogger.error(TAG, "CouponViewModel.loadClaimed: ${e.message}", e)
            _claimedState.value = RequestState.Error(e.resId, e.message)
        }
    }

    /** 历史首页（status=1） */
    private suspend fun loadHistorical() {
        _historicalState.value = RequestState.Loading
        try {
            val page = couponRepository.getUserCoupon(COUPON_STATUS_HISTORICAL, 1, USER_COUPON_PAGE_SIZE)
            historicalPage = 1
            historicalTotal = page.total
            _historicalCoupons.value = page.records
            _historicalHasMore.value = page.records.size < page.total
            _historicalState.value = RequestState.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException) {
            AppLogger.error(TAG, "CouponViewModel.loadHistorical: ${e.message}", e)
            _historicalState.value = RequestState.Error(e.resId, e.message)
        }
    }

    private companion object {
        const val TAG = "CouponViewModel"
    }
}

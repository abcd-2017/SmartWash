package com.smartwash.ui.page.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.order.api.OrderApi
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.feature.order.api.model.OrderReview
import com.smartwash.R
import com.smartwash.common.utils.model.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    private val orderApi: OrderApi,
) : ViewModel() {
    private val _getOrderInfoDetail = MutableStateFlow<RequestState>(RequestState.Idle)
    val getOrderInfoDetail = _getOrderInfoDetail.asStateFlow()
    private val _orderInfo = MutableStateFlow<OrderInfo?>(null)
    val orderInfo = _orderInfo.asStateFlow()

    fun getOrderDetail(orderId: Long) {
        _getOrderInfoDetail.value = RequestState.Loading
        viewModelScope.launch {
            try {
                _orderInfo.value = orderApi.getOrderInfo(orderId)
                // 订单详情为低频加载入口（进页面一次），成功留 info 便于对账
                AppLogger.info(TAG, "订单详情加载成功：orderId=$orderId")
                _getOrderInfoDetail.value = RequestState.Success
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "OrderDetailViewModel.getOrderDetail 失败：orderId=$orderId", e)
                _getOrderInfoDetail.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun resetState() {
        _getOrderInfoDetail.value = RequestState.Idle
    }

    /** 订单评价提交状态（Idle/Loading/Success/Error；Success 后弹层关闭由页面消费） */
    private val _submitReviewState = MutableStateFlow<RequestState>(RequestState.Idle)
    val submitReviewState = _submitReviewState.asStateFlow()

    /** 本订单已提交评价（首条），驱动"评价此单/查看评价/已评价"入口语义 */
    private val _myReview = MutableStateFlow<OrderReview?>(null)
    val myReview = _myReview.asStateFlow()

    fun loadMyReview(orderId: Long) {
        viewModelScope.launch {
            try {
                _myReview.value = orderApi.getOrderReviews(orderId, 1, 10).firstOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.warn(TAG, "OrderDetailViewModel.loadMyReview: ${e.message}", e)
            }
        }
    }

    fun submitReview(orderId: Long, rating: Int, content: String) {
        if (_submitReviewState.value is RequestState.Loading) return
        _submitReviewState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val accepted = orderApi.submitReview(orderId, rating, content)
                if (accepted) {
                    AppLogger.info(TAG, "订单评价提交成功：orderId=$orderId rating=$rating")
                    _submitReviewState.value = RequestState.Success
                    _myReview.value = OrderReview(orderId = orderId, rating = rating, content = content)
                } else {
                    _submitReviewState.value =
                        RequestState.Error(R.string.review_submit_rejected, null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "OrderDetailViewModel.submitReview 失败：orderId=$orderId", e)
                _submitReviewState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun resetReviewState() {
        _submitReviewState.value = RequestState.Idle
    }

    private companion object {
        const val TAG = "OrderDetailViewModel"
    }
}

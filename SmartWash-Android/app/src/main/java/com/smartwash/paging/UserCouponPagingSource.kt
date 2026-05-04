package com.smartwash.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.smartwash.network.api.CouponApi
import com.smartwash.network.vo.coupon.UserCouponVo
import com.smartwash.utils.AppConstant

class UserCouponPagingSource(private val couponApi: CouponApi, private val status: String) :
    PagingSource<Int, UserCouponVo>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UserCouponVo> {
        return try {
            val currentPage = params.key ?: 1

            val couponVoList =
                couponApi.getUserCoupon(status, currentPage).data ?: emptyList()
            val prevKey = if (currentPage == 1) null else currentPage - 1
            val nextKey = if (couponVoList.isEmpty()) null else currentPage + 1


            LoadResult.Page(
                data = couponVoList,
                prevKey = prevKey,
                nextKey = nextKey,
            )
        } catch (e: Exception) {
            Log.e(AppConstant.APP_NAME, "UserCouponPagingSource.load: ${e.message}", e)
            LoadResult.Error(throwable = e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, UserCouponVo>): Int? {
        return state.anchorPosition
    }
}
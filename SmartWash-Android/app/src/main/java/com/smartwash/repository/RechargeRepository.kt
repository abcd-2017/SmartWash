package com.smartwash.repository

import com.smartwash.network.api.RechargeApi
import com.smartwash.network.entity.PageData
import com.smartwash.network.entity.recharge.UserRecharge
import com.smartwash.network.vo.recharge.RechargeRecordVo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RechargeRepository @Inject constructor(
    val rechargeApi: RechargeApi,
) {
    suspend fun userRecharge(userRecharge: UserRecharge) {
        rechargeApi.userRecharge(userRecharge)
    }

    suspend fun getRechargeRecordList(page: Int?, size: Int? = 10): PageData<RechargeRecordVo> {
        return rechargeApi.getRechargeRecordList(page, size).data
            ?: PageData(emptyList(), 0, size ?: 10, page ?: 1)
    }
}

package com.smartwash.feature.order.impl.network.entity.order

import androidx.annotation.Keep

/** 预约下单请求体（原 app 的 network/entity/order/ReservationLaundry，T6.1 迁入） */
@Keep
data class ReservationLaundry(val itemsId: Long, val totalPrice: Float)

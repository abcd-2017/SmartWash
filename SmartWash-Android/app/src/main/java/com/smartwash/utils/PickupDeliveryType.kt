package com.smartwash.utils

import androidx.annotation.StringRes
import com.smartwash.R

enum class PickupDeliveryType(val type: Int, @StringRes val descriptionRes: Int) {
    PICKUP(0, R.string.pickup_type),
    DELIVERY(1, R.string.delivery_type)
}

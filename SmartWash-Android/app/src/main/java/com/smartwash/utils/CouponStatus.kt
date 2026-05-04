package com.smartwash.utils

import androidx.annotation.StringRes
import com.smartwash.R

enum class CouponStatus(val status: String, @StringRes val descriptionRes: Int) {
    EXPIRED("1", R.string.coupon_status_expired),
    ACTIVE("0", R.string.coupon_status_active),
    RECEIVE("2", R.string.coupon_status_receive)
}

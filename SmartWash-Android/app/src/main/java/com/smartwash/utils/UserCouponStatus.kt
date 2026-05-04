package com.smartwash.utils

import androidx.annotation.StringRes
import com.smartwash.R

enum class UserCouponStatus(val status: String, @StringRes val descriptionRes: Int) {
    OVERDUE("1", R.string.user_coupon_status_overdue),
    ACTIVE("0", R.string.user_coupon_status_active)
}

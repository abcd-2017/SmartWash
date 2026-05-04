package com.smartwash.utils

import androidx.annotation.StringRes
import com.smartwash.R

enum class PaymentType(val type: String, @StringRes val descriptionRes: Int) {
    PURSE("1", R.string.payment_type_purse),
    ALI_PAY("2", R.string.payment_type_ali_pay),
    WECHAT_PAY("3", R.string.payment_type_wechat)
}

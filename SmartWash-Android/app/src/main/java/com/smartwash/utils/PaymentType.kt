package com.smartwash.utils

enum class PaymentType(val type: String, val description: String) {
    PURSE("1", "钱包支付"),
    ALI_PAY("2", "支付宝"),
    WECHAT_PAY("3", "微信")
}
package com.smartwash.utils

//手机号校验正则
fun isValidPhone(phone: String): Boolean {
    return phone.matches(Regex("^1[3-9]\\d{9}$"))
}
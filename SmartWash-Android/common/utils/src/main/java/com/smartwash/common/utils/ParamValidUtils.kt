package com.smartwash.common.utils

//手机号校验正则
fun isValidPhone(phone: String): Boolean {
    return phone.matches(Regex("^1[3-9]\\d{9}$"))
}

// 密码校验：长度 6-16（空格计入长度，与登录/注册页约束一致）
fun isValidPassword(password: String): Boolean {
    return password.length in 6..16
}
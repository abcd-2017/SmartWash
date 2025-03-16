package com.smartwash.utils

/**
 * http响应状态码
 */
enum class HttpStatusCode(val code: Int, val message: String) {
    Success(200, "请求成功"),
    Fail(201, "请求失败"),
    Unauthorized(401, "登录异常"),
    NotFound(404, "未找到资源"),
    Error(500, "服务器内部错误")
}
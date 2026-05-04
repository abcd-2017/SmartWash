package com.smartwash.utils

import androidx.annotation.StringRes
import com.smartwash.R

/**
 * http响应状态码
 */
enum class HttpStatusCode(val code: Int, @StringRes val messageRes: Int) {
    Success(200, R.string.http_success),
    Fail(201, R.string.http_fail),
    Unauthorized(401, R.string.http_unauthorized),
    NotFound(404, R.string.http_not_found),
    Error(500, R.string.http_server_error)
}

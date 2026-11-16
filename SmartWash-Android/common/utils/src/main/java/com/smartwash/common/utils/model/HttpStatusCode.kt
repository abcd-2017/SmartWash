package com.smartwash.common.utils.model

enum class HttpStatusCode(val code: Int) {
    Success(200),
    Fail(201),
    Unauthorized(401),
    NotFound(404),
    Error(500)
}

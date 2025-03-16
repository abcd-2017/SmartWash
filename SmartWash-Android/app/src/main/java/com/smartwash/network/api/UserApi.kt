package com.smartwash.network.api

import com.smartwash.network.entity.ResponseData
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path


/**
 * auth Api请求接口
 */
interface UserApi {
    /**
     * 根据手机号获取验证码
     */
    @GET("/auth/user/Captcha/{phoneNumber}")
    suspend fun getCaptcha(
        @Path("phoneNumber") phoneNumber: String
    ): ResponseData<String>

    /**
     * 注册
     */
    @POST("/auth/user/register")
    suspend fun register(
        username: String,
        password: String
    ): ResponseData<String>

    /**
     * 登录
     */
    @POST("/auth/user/login")
    suspend fun login(
        username: String, password: String
    ): ResponseData<String>
}
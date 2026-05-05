package com.smartwash.network.api

import com.smartwash.network.annotation.RequireAuthorization
import com.smartwash.network.entity.ApiResult
import com.smartwash.network.entity.user.LoginUser
import com.smartwash.network.entity.user.RegisterUser
import com.smartwash.network.entity.user.UpdateUserInfo
import com.smartwash.network.vo.user.UserInfoVo
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query


/**
 * auth Api请求接口
 */
interface UserApi {
    /**
     * 根据手机号获取验证码
     */
    @GET("/auth/user/captcha/{phoneNumber}")
    suspend fun getCaptcha(
        @Path("phoneNumber") phoneNumber: String,
    ): ApiResult<String>

    /**
     * 注册
     */
    @POST("/auth/user/register")
    suspend fun register(
        @Body registerUser: RegisterUser,
    ): ApiResult<String>

    /**
     * 登录
     */
    @POST("/auth/user/login")
    suspend fun login(
        @Body loginUser: LoginUser,
    ): ApiResult<String>

    /**
     * 完善用户信息
     */
    @RequireAuthorization
    @POST("/web/auth/user/updateUserInfo")
    suspend fun updateUserInfo(
        @Body updateUser: UpdateUserInfo,
    ): ApiResult<String>

    /**
     * 获取当前登录用户学校id
     */
    @RequireAuthorization
    @GET("/web/auth/user/school")
    suspend fun getUserSchoolId(): ApiResult<Long>

    /**
     * 判断当前学号是否已经存在
     */
    @RequireAuthorization
    @GET("/web/auth/user/getUserByStudentId")
    suspend fun getUserByStudentId(
        @Query("studentId") studentId: String,
    ): ApiResult<Boolean>

    /**
     * 获取用户详细信息
     */
    @RequireAuthorization
    @GET("/web/auth/user/getUserInfo")
    suspend fun getUserInfo(): ApiResult<UserInfoVo>

    /**
     * 绑定校园卡
     */
    @RequireAuthorization
    @POST("/web/auth/user/bingCampus/{campusCard}")
    suspend fun bindCampus(
        @Path("campusCard") campusCard: String,
    ): ApiResult<Boolean>

    /**
     * 解绑校园卡
     */
    @RequireAuthorization
    @POST("/web/auth/user/unBingCampus")
    suspend fun unBindCampus(): ApiResult<Boolean>

    /**
     * 上传头像
     */
    @RequireAuthorization
    @Multipart
    @POST("/web/auth/user/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): ApiResult<String>
}
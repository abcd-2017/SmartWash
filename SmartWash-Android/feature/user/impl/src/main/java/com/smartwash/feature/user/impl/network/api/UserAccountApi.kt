package com.smartwash.feature.user.impl.network.api

import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.user.impl.network.entity.user.ChangePasswordRequest
import com.smartwash.feature.user.impl.network.entity.user.LoginUser
import com.smartwash.feature.user.impl.network.entity.user.RegisterUser
import com.smartwash.feature.user.impl.network.entity.user.ResetPasswordRequest
import com.smartwash.feature.user.impl.network.entity.user.UpdateUserInfo
import com.smartwash.feature.user.impl.network.vo.user.LoginVo
import com.smartwash.feature.user.impl.network.vo.user.TransactionVo
import com.smartwash.feature.user.impl.network.vo.user.UserInfoVo
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 用户账户 Retrofit 接口（auth 域 API 请求）。
 *
 * 命名说明：原 app 内本接口与 user-api 模块的服务契约接口同名（均为 UserApi），
 * T5.2 迁入本模块时重命名为 UserAccountApi 消除同名冲突——它只是网络层端点声明，
 * "账户"语义也更贴合其覆盖面（验证码/注册/登录/资料/校园卡/头像）；
 * 对外服务契约见 com.smartwash.feature.user.api.UserApi（UserApiImpl 经本接口取数）。
 */
interface UserAccountApi {
    /**
     * 根据手机号获取验证码
     */
    @GET("/auth/user/captcha/{phoneNumber}")
    suspend fun getCaptcha(
        @Path("phoneNumber") phoneNumber: String,
    ): ApiResult<String>

    /**
     * 注册（注册即自动登录：data 已升级为 {token, refreshToken, role} 对象，见 LoginVo，
     * 与登录响应同构）
     */
    @POST("/auth/user/register")
    suspend fun register(
        @Body registerUser: RegisterUser,
    ): ApiResult<LoginVo>

    /**
     * 登录（data 为 {token, refreshToken, role} 对象，见 LoginVo，与注册响应同构）
     */
    @POST("/auth/user/login")
    suspend fun login(
        @Body loginUser: LoginUser,
    ): ApiResult<LoginVo>

    /**
     * 钱包流水（分页；pageSize 上限 50）
     */
    @RequireAuthorization
    @GET("/web/auth/user/transactions")
    suspend fun getTransactions(
        @Query("page") page: Int?,
        @Query("pageSize") pageSize: Int? = 20,
    ): ApiResult<PageData<TransactionVo>>

    /**
     * 修改密码（原密码不正确时后端回业务失败信封 code=201，ResponseInterceptor
     * 已转译为携带「原密码不正确」的 NetworkException）
     */
    @RequireAuthorization
    @PUT("/web/auth/user/password")
    suspend fun changePassword(
        @Body request: ChangePasswordRequest,
    ): ApiResult<String>

    /**
     * 重置密码（忘记密码链路，免鉴权；验证码错误等业务失败同经信封转译）
     */
    @POST("/auth/user/resetPassword")
    suspend fun resetPassword(
        @Body request: ResetPasswordRequest,
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

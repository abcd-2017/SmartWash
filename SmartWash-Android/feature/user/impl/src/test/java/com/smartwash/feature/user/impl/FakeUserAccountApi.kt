package com.smartwash.feature.user.impl

import com.smartwash.common.utils.model.ApiResult
import com.smartwash.feature.user.impl.network.api.UserAccountApi
import com.smartwash.feature.user.impl.network.entity.user.ChangePasswordRequest
import com.smartwash.feature.user.impl.network.entity.user.LoginUser
import com.smartwash.feature.user.impl.network.entity.user.RegisterUser
import com.smartwash.feature.user.impl.network.entity.user.ResetPasswordRequest
import com.smartwash.feature.user.impl.network.entity.user.UpdateUserInfo
import com.smartwash.feature.user.impl.network.vo.user.LoginVo
import com.smartwash.feature.user.impl.network.vo.user.UserInfoVo
import okhttp3.MultipartBody

/**
 * UserAccountApi 手写桩（对齐 dining 测试的伪数据源风格，无 Mockito 依赖）：
 * 各端点可编程返回值 + 调用计数，供 UserApiImpl/UserRepository 单测消费。
 */
class FakeUserAccountApi(
    var loginResult: ApiResult<LoginVo> = ApiResult(200, "成功", null),
    var registerResult: ApiResult<LoginVo> = ApiResult(200, "成功", null),
    var userInfoResult: ApiResult<UserInfoVo> = ApiResult(200, "成功", null),
    var schoolIdResult: ApiResult<Long> = ApiResult(200, "成功", null),
    var studentIdResult: ApiResult<Boolean> = ApiResult(200, "成功", null),
    var bindCampusResult: ApiResult<Boolean> = ApiResult(200, "成功", null),
) : UserAccountApi {

    var loginCalls = 0
        private set
    var registerCalls = 0
        private set
    var getUserInfoCalls = 0
        private set
    var getCaptchaCalls = 0
        private set

    override suspend fun getCaptcha(phoneNumber: String): ApiResult<String> {
        getCaptchaCalls++
        return ApiResult(200, "成功", "123456")
    }

    override suspend fun register(registerUser: RegisterUser): ApiResult<LoginVo> {
        registerCalls++
        return registerResult
    }

    override suspend fun login(loginUser: LoginUser): ApiResult<LoginVo> {
        loginCalls++
        return loginResult
    }

    override suspend fun changePassword(request: ChangePasswordRequest): ApiResult<String> =
        ApiResult(200, "成功", null)

    override suspend fun resetPassword(request: ResetPasswordRequest): ApiResult<String> =
        ApiResult(200, "成功", null)

    override suspend fun updateUserInfo(updateUser: UpdateUserInfo): ApiResult<String> =
        ApiResult(200, "成功", null)

    override suspend fun getUserSchoolId(): ApiResult<Long> = schoolIdResult

    override suspend fun getUserByStudentId(studentId: String): ApiResult<Boolean> = studentIdResult

    override suspend fun getUserInfo(): ApiResult<UserInfoVo> {
        getUserInfoCalls++
        return userInfoResult
    }

    override suspend fun bindCampus(campusCard: String): ApiResult<Boolean> = bindCampusResult

    override suspend fun unBindCampus(): ApiResult<Boolean> = ApiResult(200, "成功", true)

    override suspend fun uploadAvatar(file: MultipartBody.Part): ApiResult<String> =
        ApiResult(200, "成功", "https://cdn.example.com/a.png")
}

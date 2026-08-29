package com.smartwash.repository

import com.smartwash.R
import com.smartwash.network.api.UserApi
import com.smartwash.network.entity.ApiResult
import com.smartwash.network.entity.user.LoginUser
import com.smartwash.network.entity.user.RegisterUser
import com.smartwash.network.entity.user.UpdateUserInfo
import com.smartwash.network.exception.NetworkException
import com.smartwash.network.vo.user.LoginVo
import com.smartwash.network.vo.user.UserInfoVo
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userApi: UserApi,
) {
    suspend fun getCaptcha(phoneNumber: String): ApiResult<String> {
        return userApi.getCaptcha(phoneNumber)
    }

    suspend fun register(registerUser: RegisterUser): String {
        return userApi.register(registerUser).data ?: ""
    }

    suspend fun login(loginUser: LoginUser): LoginVo {
        // data 为 {token, role} 对象（后端 LoginVo）；为空按失败处理，不静默返回
        return userApi.login(loginUser).data
            ?: throw NetworkException("登录响应数据为空", R.string.error_login_failed)
    }

    suspend fun getUserInfo(): UserInfoVo {
        return userApi.getUserInfo().data
            ?: throw NetworkException("用户信息为空", R.string.error_network_fail)
    }

    suspend fun getUserSchoolId(): Long {
        return userApi.getUserSchoolId().data ?: -1
    }

    suspend fun updateUserInfo(updateUser: UpdateUserInfo) {
        userApi.updateUserInfo(updateUser)
    }

    suspend fun getUserByStudentId(studentId: String): Boolean {
        return userApi.getUserByStudentId(studentId).data == true
    }

    suspend fun bindCampus(campusCard: String): Boolean {
        return userApi.bindCampus(campusCard).data == true
    }

    suspend fun unBindCampus(): Boolean {
        return userApi.unBindCampus().data == true
    }

    suspend fun uploadAvatar(file: MultipartBody.Part) {
        userApi.uploadAvatar(file)
    }
}

package com.smartwash.repository

import com.smartwash.network.api.UserApi
import com.smartwash.network.entity.ApiResult
import com.smartwash.network.entity.user.LoginUser
import com.smartwash.network.entity.user.RegisterUser
import com.smartwash.network.entity.user.UpdateUserInfo
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

    suspend fun login(loginUser: LoginUser): String {
        return userApi.login(loginUser).data ?: ""
    }

    suspend fun getUserInfo(): UserInfoVo {
        return userApi.getUserInfo().data!!
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

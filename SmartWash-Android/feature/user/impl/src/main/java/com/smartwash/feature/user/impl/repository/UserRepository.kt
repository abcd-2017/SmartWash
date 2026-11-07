package com.smartwash.feature.user.impl.repository

import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.PageData
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.user.impl.R
import com.smartwash.feature.user.impl.network.api.UserAccountApi
import com.smartwash.feature.user.impl.network.entity.user.ChangePasswordRequest
import com.smartwash.feature.user.impl.network.entity.user.LoginUser
import com.smartwash.feature.user.impl.network.entity.user.RegisterUser
import com.smartwash.feature.user.impl.network.entity.user.ResetPasswordRequest
import com.smartwash.feature.user.impl.network.entity.user.UpdateUserInfo
import com.smartwash.feature.user.impl.network.vo.user.LoginVo
import com.smartwash.feature.user.impl.network.vo.user.TransactionVo
import com.smartwash.feature.user.impl.network.vo.user.UserInfoVo
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户域数据仓库（T5.2 自 app 迁入，仅换包与 UserAccountApi 重命名，逻辑零改动）。
 *
 * 过渡说明：app 内 laundry/index/home 等消费方仍直接注入本类（import 切换），
 * T5.3 起统一切换为 user-api 的 UserApi 契约。
 */
@Singleton
class UserRepository @Inject constructor(
    private val userApi: UserAccountApi,
) {
    suspend fun getCaptcha(phoneNumber: String): ApiResult<String> {
        return userApi.getCaptcha(phoneNumber)
    }

    suspend fun register(registerUser: RegisterUser): LoginVo {
        // 注册即自动登录：响应已升级为 {token, refreshToken, role} 对象（后端 LoginVo），
        // 与 login 同构；为空按失败处理，不静默返回
        return userApi.register(registerUser).data
            ?: throw NetworkException("注册响应数据为空", R.string.error_login_failed)
    }

    suspend fun login(loginUser: LoginUser): LoginVo {
        // data 为 {token, refreshToken, role} 对象（后端 LoginVo）；为空按失败处理，不静默返回
        return userApi.login(loginUser).data
            ?: throw NetworkException("登录响应数据为空", R.string.error_login_failed)
    }

    /** 钱包流水（分页，pageSize 上限 50） */
    suspend fun getTransactions(page: Int, pageSize: Int): PageData<TransactionVo> {
        return userApi.getTransactions(page, pageSize).data
            ?: throw NetworkException("钱包流水为空", R.string.error_network_fail)
    }

    /** 修改密码（原密码不正确经 ResponseInterceptor 转译为携带后端文案的 NetworkException） */
    suspend fun changePassword(oldPassword: String, newPassword: String) {
        userApi.changePassword(ChangePasswordRequest(oldPassword, newPassword))
    }

    /** 重置密码（忘记密码链路，免鉴权） */
    suspend fun resetPassword(phoneNumber: String, code: String, newPassword: String) {
        userApi.resetPassword(ResetPasswordRequest(phoneNumber, code, newPassword))
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

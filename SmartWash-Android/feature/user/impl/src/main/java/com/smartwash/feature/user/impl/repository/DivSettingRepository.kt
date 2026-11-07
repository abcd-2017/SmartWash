package com.smartwash.feature.user.impl.repository

import com.smartwash.feature.user.impl.network.api.DivSettingApi
import com.smartwash.feature.user.impl.network.entity.div.SaveUserApiConfig
import com.smartwash.feature.user.impl.network.vo.div.PlatformModelVo
import com.smartwash.feature.user.impl.network.vo.div.UserApiConfigVo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 解读模型 BYOK 数据仓库（设置页「解读模型」入口，issue #79）。
 *
 * 端上零缓存零持久化：配置只存服务端（密钥 AES-GCM 加密入库），每次进弹层经
 * GET 现拉；明文 API Key 仅存在于输入框内存态，本仓库与网络层均不落盘、不打日志。
 * 业务失败经 ResponseInterceptor 转为 NetworkException（message 已是用户可读文案）
 * 直接上抛，由 ViewModel 转 RequestState.Error。
 */
@Singleton
class DivSettingRepository @Inject constructor(
    private val api: DivSettingApi,
) {

    /** 用户 BYOK 配置（null = 未配置，解读走平台默认模型） */
    suspend fun getUserApiConfig(): UserApiConfigVo? = api.getUserApiConfig().data

    /** 保存/更换 BYOK（服务端真实试呼通过才入库；失败抛 NetworkException 带原因文案） */
    suspend fun saveApiConfig(baseUrl: String, model: String, apiKey: String) {
        api.saveApiConfig(SaveUserApiConfig(baseUrl, model, apiKey))
    }

    /** 删除 BYOK 配置 */
    suspend fun deleteApiConfig() {
        api.deleteApiConfig()
    }

    /** 连通性测试（不保存）：后端失败也回 200 信封，以 data==true 判定通过 */
    suspend fun testApiConfig(baseUrl: String, model: String, apiKey: String): Boolean =
        api.testApiConfig(SaveUserApiConfig(baseUrl, model, apiKey)).data == true

    /** 平台启用模型目录（表单输入建议；拉取失败由调用方静默降级为空目录） */
    suspend fun getPlatformModels(): List<PlatformModelVo> =
        api.getPlatformModels().data.orEmpty()
}

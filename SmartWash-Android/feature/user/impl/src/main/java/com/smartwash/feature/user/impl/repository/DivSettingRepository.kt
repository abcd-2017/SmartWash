package com.smartwash.feature.user.impl.repository

import com.smartwash.feature.user.impl.network.api.DivSettingApi
import com.smartwash.feature.user.impl.network.entity.div.SaveUserApiConfig
import com.smartwash.feature.user.impl.network.vo.div.PlatformVendorGroupVo
import com.smartwash.feature.user.impl.network.vo.div.UserApiConfigVo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 解读模型配置数据仓库（设置页「解读模型」入口，issue #79；2026-11-11 双模式契约）。
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

    /** 用户解读模型配置（null = 未配置；平台零兜底，未配置解读统一 428） */
    suspend fun getUserApiConfig(): UserApiConfigVo? = api.getUserApiConfig().data

    /** 保存/更换配置（双模式请求体由 ViewModel 组装；服务端真实试呼通过才入库，失败抛 NetworkException 带原因文案） */
    suspend fun saveApiConfig(request: SaveUserApiConfig) {
        api.saveApiConfig(request)
    }

    /** 删除配置 */
    suspend fun deleteApiConfig() {
        api.deleteApiConfig()
    }

    /** 连通性测试（不保存）：后端失败也回 200 信封，以 data==true 判定通过 */
    suspend fun testApiConfig(request: SaveUserApiConfig): Boolean =
        api.testApiConfig(request).data == true

    /** 平台启用模型目录树（目录模式分步选择；拉取失败由调用方静默降级为空目录） */
    suspend fun getPlatformModels(): List<PlatformVendorGroupVo> =
        api.getPlatformModels().data.orEmpty()
}

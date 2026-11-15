package com.smartwash.common.network.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 当前网络类型（仅取类型，不取 SSID / 运营商 / 网络 ID 等可识别信息，隐私最小化）。
 *
 * 用于请求头 [com.smartwash.common.network.interceptor.RequestInterceptor] 的 X-Network-Type。
 * 取值口径：
 * - WIFI：具备 TRANSPORT_WIFI（含以太网等被系统归入 wifi 的传输）；
 * - CELLULAR：具备 TRANSPORT_CELLULAR；
 * - NONE：无可用网络或能力未知。
 */
enum class NetworkType(val wireName: String) {
    WIFI("wifi"),
    CELLULAR("cellular"),
    NONE("none"),
}

/**
 * 网络类型解析器（[Singleton]）。
 *
 * 只读 [ConnectivityManager] 的传输类型，不申请任何危险权限（ACCESS_NETWORK_STATE 为普通权限，
 * 由 androidx 网络依赖/宿主 Manifest 提供；不读取位置相关网络标识）。
 */
@Singleton
class NetworkTypeResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun current(): NetworkType {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return NetworkType.NONE
        val network = manager.activeNetwork ?: return NetworkType.NONE
        val capabilities = manager.getNetworkCapabilities(network) ?: return NetworkType.NONE
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            else -> NetworkType.NONE
        }
    }
}

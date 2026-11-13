package com.smartwash.feature.toolbox

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 工具箱类型安全路由（模块内单一事实来源）。
 *
 * 迁移前为 `sealed class ToolboxRoute(val text: String)` 字符串常量；现改为
 * sealed interface + @Serializable 路由对象，@SerialName 固定路由串与迁移前
 * 完全一致（"ToolboxShortCodeList"/...）；模块内页面跳转一律引用本类
 * （feature 不得反向依赖 app 壳层的 PageConstant）。
 *
 * 参数形态：全部参数带默认值 → query（与迁移前注册形态一致）；属性名即
 * SavedStateHandle key，与 ui/detail/ShortCodeDetailViewModel、
 * ui/delivery/DeliverySessionViewModel 的 ARG_* 常量对齐。
 *
 * 当前无任何入口指向本路由（工作台 tab 未改造），页面仅作为独立路由注册在
 * 宿主 NavHost，接入方式见 SmartWash-Android/docs/短链工具箱对接文档.md。
 */
sealed interface ToolboxRoute : SmartWashRoute {

    /** 我的短链列表 */
    @Serializable
    @SerialName("ToolboxShortCodeList")
    data object List : ToolboxRoute

    /** 新建短链 */
    @Serializable
    @SerialName("ToolboxShortCodeCreate")
    data object Create : ToolboxRoute

    /** 短链详情（字段经 query 携带，见 [com.smartwash.feature.toolbox.ui.ToolboxGraph]） */
    @Serializable
    @SerialName("ToolboxShortCodeDetail")
    data class Detail(
        val id: Long = -1L,
        val code: String = "",
        val contentType: Int = 1,
        val isPublic: Int = 1,
        val clickCount: Long = 0L,
        val target: String = "",
        val expireAt: String = "",
        val shortUrl: String = "",
        val createdAt: String = "",
        // ---------- 私密文本（contentType=7） ----------
        val secretState: String = "",
        val burnedAt: String = "",
        val secretUrl: String = "",
    ) : ToolboxRoute

    /** 设备投递发起页（表单 + 会话信息，见 ui/delivery/DeliveryGraph） */
    @Serializable
    @SerialName("ToolboxDelivery")
    data object Delivery : ToolboxRoute

    /** 物品标签列表（一物一码 M2） */
    @Serializable
    @SerialName("ToolboxAssetTagList")
    data object AssetTagList : ToolboxRoute

    /** 新建物品标签 */
    @Serializable
    @SerialName("ToolboxAssetTagCreate")
    data object AssetTagCreate : ToolboxRoute

    /** 物品标签详情（字段经 query 携带，对齐短链详情模式） */
    @Serializable
    @SerialName("ToolboxAssetTagDetail")
    data class AssetTagDetail(
        val id: Long = -1L,
        val code: String = "",
        val itemName: String = "",
        val itemDesc: String = "",
        val status: Int = 0,
        val hasContact: Boolean = false,
        val tagUrl: String = "",
        val scanCount: Long = 0L,
        val lastScannedAt: String = "",
    ) : ToolboxRoute

    /** 设备投递会话页（会话码经 query 携带，见 ui/delivery/DeliveryGraph） */
    @Serializable
    @SerialName("ToolboxDeliverySession")
    data class DeliverySession(val code: String = "") : ToolboxRoute

    companion object {
        /**
         * 列表页刷新信号 key（存于列表页 backStackEntry 的 savedStateHandle）：
         * 创建/续期/删除成功后由发起页置 true，列表页消费后复位，驱动 pagingFlow 重新加载。
         */
        const val CHANGED_KEY = "toolbox_short_code_changed"

        /** 物品标签列表刷新信号 key（语义同 CHANGED_KEY，域内二组信号互不干扰） */
        const val ASSET_TAG_CHANGED_KEY = "toolbox_asset_tag_changed"
    }
}

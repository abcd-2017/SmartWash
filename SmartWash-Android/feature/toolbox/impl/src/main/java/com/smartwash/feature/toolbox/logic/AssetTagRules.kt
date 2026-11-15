package com.smartwash.feature.toolbox.logic

/**
 * 物品标签（一物一码 M2）表单与展示纯逻辑（零 Android 依赖，JVM 单测锚点）。
 * 校验规则对齐后端 CreateAssetTagFrom / UpdateAssetTagFrom。
 */
object AssetTagRules {

    /** 内容类型：物品标签（一物一码），对齐后端 ShortCodeContentType.ASSET_TAG */
    const val CONTENT_TYPE_ASSET_TAG = 8

    /** 寻物状态（对齐后端 status：0 正常 / 1 寻物中 / 2 已寻回） */
    const val STATUS_NORMAL = 0
    const val STATUS_LOST = 1
    const val STATUS_FOUND = 2

    /** 字段上限（对齐后端 @Size） */
    const val ITEM_NAME_MAX = 64
    const val ITEM_DESC_MAX = 256
    const val CONTACT_MAX = 64

    /** 物品名称校验（后端终审）：非空 ≤64 字符 */
    fun isValidItemName(name: String): Boolean =
        name.isNotBlank() && name.length <= ITEM_NAME_MAX

    /** 联系方式校验（可空 = 不留）：≤64 字符 */
    fun isValidContact(contact: String): Boolean = contact.length <= CONTACT_MAX

    /** 状态合法性（0/1/2 之外兜底未知，端上白名单惯例） */
    fun isKnownStatus(status: Int): Boolean =
        status == STATUS_NORMAL || status == STATUS_LOST || status == STATUS_FOUND
}

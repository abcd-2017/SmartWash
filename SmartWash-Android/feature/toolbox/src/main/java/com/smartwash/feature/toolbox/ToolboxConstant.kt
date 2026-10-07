package com.smartwash.feature.toolbox

/**
 * 工具箱模块内常量（非用户可见；用户可见文案一律进 res/values/strings.xml）。
 */
object ToolboxConstant {

    /** 列表/明细分页每页条数（后端 BaseSearchFrom 上限 100） */
    const val PAGE_SIZE = 10

    /** 可见性：1公开 2私有（对齐后端 is_public） */
    const val VISIBILITY_PUBLIC = 1
    const val VISIBILITY_PRIVATE = 2
}

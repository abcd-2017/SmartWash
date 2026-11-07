package com.smartwash.feature.memo

/**
 * 速记手账模块内常量（非用户可见；用户可见文案一律进 res/values/strings.xml）。
 */
object MemoConstant {

    /** 记录列表分页每页条数（对齐 toolbox 的 PAGE_SIZE 基线） */
    const val PAGE_SIZE = 10

    /** 单条记录内容上限（后端契约：content ≤ 2000，端上输入即截断） */
    const val CONTENT_MAX_LENGTH = 2000

    /** 标签聚合接口最多展示的「常用标签」数量（仅展示层裁剪，非后端契约） */
    const val FREQUENT_TAGS_LIMIT = 8
}

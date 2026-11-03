package com.smartwash.feature.review

/**
 * 记忆复习模块内常量（非用户可见；用户可见文案一律进 res/values/strings.xml）。
 */
object ReviewConstant {

    /** 资料列表/卡片列表分页每页条数（对齐后端 BaseSearchFrom 惯例） */
    const val PAGE_SIZE = 20

    /** 建资料表单：标题上限（对齐后端 AddMaterialFrom @Size(max=100)） */
    const val TITLE_MAX = 100

    /** 建资料表单：原文上限（对齐后端 AddMaterialFrom @Size(max=100_000)） */
    const val RAW_TEXT_MAX = 100_000

    /** 建卡：问题上限（对齐后端 AddCardFrom @Size(max=1000)） */
    const val QUESTION_MAX = 1000

    /** 建卡：答案上限（对齐后端 AddCardFrom @Size(max=2000)） */
    const val ANSWER_MAX = 2000

    /** 资料标题映射最多翻页数（队列页补齐「所属资料」标题的防御上限，防止异常大分页拖死首屏） */
    const val MATERIAL_TITLE_MAX_PAGES = 10
}

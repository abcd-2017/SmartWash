package com.smartwash.feature.review

/**
 * 记忆复习路由常量（模块内单一事实来源，模式同 ToolboxRoute/DivRoute）。
 *
 * 模块内页面跳转一律引用本常量（feature 不得反向依赖 app 壳层的 PageConstant）；
 * 页面经 app 壳 NavHost 一行 [com.smartwash.feature.review.ui.reviewGraph] 聚合，
 * 入口宿主后续接入（本次不改壳层导航）。
 */
sealed class ReviewRoute(val text: String) {

    /** 今日队列页（到期卡 + 新卡计数条与队列列表） */
    data object Queue : ReviewRoute("ReviewQueue")

    /** 复习页（翻面 + 四档评分；可选 startCardId 指定从某张卡开始，-1 = 从队首） */
    data object Study : ReviewRoute("ReviewStudy")

    /** 资料页（资料列表 + 粘贴文本建资料 + 卡片列表） */
    data object Materials : ReviewRoute("ReviewMaterials")

    companion object {
        /** 复习页路由入参：起始卡片 id（query 携带，LongType，缺省 -1） */
        const val ARG_START_CARD_ID = "startCardId"

        /**
         * 数据变更信号 key（存于队列页 backStackEntry 的 savedStateHandle）：
         * 资料页建资料/建卡、复习页每次评分后置 true，队列页消费后复位并重拉队列
         * （复习评分改变到期状态，新建卡改变新卡配额，均影响今日队列）。
         */
        const val CHANGED_KEY = "review_data_changed"
    }
}

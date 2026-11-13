package com.smartwash.feature.dining.logic

/**
 * 点单页左右联动模型（纯 Kotlin，无 Android/Compose 依赖，JVM 单测锚点）。
 *
 * 布局语义：右侧是一个扁平 [androidx.compose.foundation.lazy.LazyColumn]——每个分类一段，
 * 段首为分类头（1 行），随后为该分类的菜品行。左侧分类栏与高亮分类的映射、点击分类的
 * 滚动落点，全部由本文件的纯函数派生（`LazyListState + derivedStateOf` 模式在页面侧组合：
 * 页面把 `listState.firstVisibleItemIndex` 喂给 [DiningMenuLink.activeCategoryAt]，
 * 把点击的分类交给 [DiningMenuLink.scrollTargetIndex]）。
 *
 * 约定：分类 ID 为 null 表示「未分类」段（收尾渲染）；分段顺序即右列表的渲染顺序。
 */
data class DiningSelector(
    /** 选菜人用户 ID */
    val userId: Long,
    /** 选菜人标识（脱敏手机号；缺失时调用方给兜底文案） */
    val label: String,
    /** 该选菜人的备注（可空） */
    val note: String,
    /** 是否当前用户 */
    val isMe: Boolean,
)

/** 点单页菜品行模型（右列表渲染单元） */
data class DiningMenuItem(
    /** 菜谱 ID（列表 key / 勾选参数） */
    val recipeId: Long,
    /** 菜名 */
    val name: String,
    /** 封面直链（空串=无封面，渲染占位底） */
    val coverUrl: String,
    /** 所属分类 ID，null=未分类 */
    val categoryId: Long?,
    /** 是否当前用户已勾选 */
    val selectedByMe: Boolean,
    /** 当前用户备注（未勾选/无备注为空串） */
    val myNote: String,
    /** 全部选菜人（共享购物车明细，按选菜记录顺序） */
    val selectors: List<DiningSelector>,
)

/** 点单页分类段模型（左栏一项 + 右列表一段） */
data class DiningMenuSection(
    /** 分类 ID，null=未分类段 */
    val categoryId: Long?,
    /** 分类名（左栏文案 / 右列表段头文案） */
    val categoryName: String,
    /** 该分类菜品（保持本单菜单顺序） */
    val dishes: List<DiningMenuItem>,
)

/** 左右联动派生纯函数（无状态，可复用于单测与页面）。 */
object DiningMenuNavigator {

    /**
     * 每个分类段在右侧扁平列表中的起始 index（段头占 1 行 + 菜品行）。
     * 返回有序 Map（分类 ID → 起始 index）；空分段返回空 Map。
     */
    fun sectionStartIndices(sections: List<DiningMenuSection>): Map<Long?, Int> {
        val starts = LinkedHashMap<Long?, Int>()
        var index = 0
        for (section in sections) {
            starts[section.categoryId] = index
            index += 1 + section.dishes.size
        }
        return starts
    }

    /**
     * 由右侧当前可见的首个扁平 index 反算应高亮的分类：
     * 取「段起始 index ≤ flatIndex」的最后一个分类（即段头已滚到顶或以上的最近分类）。
     * 空分段返回 null；flatIndex 为负时归首段。
     */
    fun activeCategoryAt(sections: List<DiningMenuSection>, flatIndex: Int): Long? {
        if (sections.isEmpty()) return null
        var active = sections.first().categoryId
        var index = 0
        for (section in sections) {
            if (index > flatIndex) break
            active = section.categoryId
            index += 1 + section.dishes.size
        }
        return active
    }

    /**
     * 由左侧点击的分类计算滚动目标 index（该段段头位置；分类不存在返回 0）。
     */
    fun scrollTargetIndex(sections: List<DiningMenuSection>, categoryId: Long?): Int =
        sectionStartIndices(sections)[categoryId] ?: 0
}

/**
 * 点单页左右联动状态类：持有当前分段，把滚动位置 ↔ 高亮分类的双向派生收敛到一处，
 * 页面以 `remember(sections) { DiningMenuLink(sections) }` 持有（纯逻辑，可单测）。
 */
class DiningMenuLink(
    val sections: List<DiningMenuSection>,
) {
    /** 右侧首个可见扁平 index → 应高亮的分类 ID（派生逻辑见 [DiningMenuNavigator.activeCategoryAt]） */
    fun activeCategoryAt(firstVisibleItemIndex: Int): Long? =
        DiningMenuNavigator.activeCategoryAt(sections, firstVisibleItemIndex)

    /** 左侧点击分类 → 右侧滚动目标 index（派生逻辑见 [DiningMenuNavigator.scrollTargetIndex]） */
    fun scrollTargetIndex(categoryId: Long?): Int =
        DiningMenuNavigator.scrollTargetIndex(sections, categoryId)
}

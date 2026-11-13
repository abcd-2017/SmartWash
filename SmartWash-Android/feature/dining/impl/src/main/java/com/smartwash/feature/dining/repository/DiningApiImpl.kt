package com.smartwash.feature.dining.repository

import com.smartwash.feature.dining.api.DiningApi
import com.smartwash.feature.dining.api.model.DiningRecipeVo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DiningApi] 的 @Binds 实现（模式同 notice 的 NoticeApiImpl）：
 * 跨模块消费方（后续订单域创建订单页）经接口取上架菜谱，不触碰本域 Retrofit 接口与仓库细节。
 * 本票先落最小面（上架菜谱分页），订单域所需的其他读取面随后续票扩展本类与契约。
 */
@Singleton
class DiningApiImpl @Inject constructor(
    private val diningRepository: DiningRepository,
) : DiningApi {

    override suspend fun getOnShelfRecipes(
        page: Int,
        size: Int,
        categoryId: Long?,
        keyword: String?,
    ): List<DiningRecipeVo> = diningRepository.getRecipes(page, size, categoryId, keyword).records
}

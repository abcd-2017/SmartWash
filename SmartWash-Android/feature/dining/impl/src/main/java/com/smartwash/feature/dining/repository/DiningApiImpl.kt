package com.smartwash.feature.dining.repository

import com.smartwash.feature.dining.api.DiningApi
import com.smartwash.feature.dining.api.model.DietaryPreferenceVo
import com.smartwash.feature.dining.api.model.DiningRecipeVo
import com.smartwash.feature.dining.api.model.DiningScanResult
import com.smartwash.feature.dining.logic.DiningJoinUrlParseResult
import com.smartwash.feature.dining.logic.DiningJoinUrlParser
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * [DiningApi] 的 @Binds 实现（模式同 notice 的 NoticeApiImpl）：
 * 跨模块消费方（订单域创建订单页 / 壳层全局扫码页）经接口取数据，
 * 不触碰本域 Retrofit 接口与仓库细节。
 *
 * [deepLinkScheme] 由宿主按变体注入（full=`smartwash`、lite=`todayeat`，见 app 的
 * BaseUrlModule）——加入链接归一化产出的深链必须落本端 scheme，否则 lite 的
 * DeepLinkHandler 判定非本端 scheme 直接 Unknown「暂不支持」。
 */
@Singleton
class DiningApiImpl @Inject constructor(
    private val diningRepository: DiningRepository,
    @Named("deepLinkScheme") private val deepLinkScheme: String,
) : DiningApi {

    override suspend fun getOnShelfRecipes(
        page: Int,
        size: Int,
        categoryId: Long?,
        keyword: String?,
        ingredient: String?,
    ): List<DiningRecipeVo> =
        diningRepository.getRecipes(page, size, categoryId, keyword, ingredient).records

    override suspend fun getFavoriteRecipes(page: Int, size: Int): List<DiningRecipeVo> =
        diningRepository.getFavoriteRecipes(page, size).records

    override suspend fun favoriteRecipe(recipeId: Long): Boolean =
        diningRepository.favoriteRecipe(recipeId)

    override suspend fun unfavoriteRecipe(recipeId: Long): Boolean =
        diningRepository.unfavoriteRecipe(recipeId)

    override suspend fun getDietaryPreference(): DietaryPreferenceVo =
        diningRepository.getDietaryPreference()

    override suspend fun saveDietaryPreference(preference: DietaryPreferenceVo) {
        diningRepository.saveDietaryPreference(preference)
    }

    override suspend fun normalizeScanToDeepLink(raw: String): DiningScanResult =
        when (val parsed = DiningJoinUrlParser.parse(raw)) {
            // 深链串与 dining 深链贡献者（DiningDeepLinkModule）的 order/{id}?t= 协议同源；
            // scheme 按本端变体注入（full=smartwash / lite=todayeat）；改深链串必须同步
            // DiningDeepLinkModuleTest 锚点（impl AGENTS.md「站内信深链」节）
            is DiningJoinUrlParseResult.Join ->
                DiningScanResult.DeepLink("$deepLinkScheme://dining/order/${parsed.orderId}?t=${parsed.token}")
            DiningJoinUrlParseResult.NotDining -> DiningScanResult.NotDining
            DiningJoinUrlParseResult.Malformed -> DiningScanResult.Malformed
        }
}

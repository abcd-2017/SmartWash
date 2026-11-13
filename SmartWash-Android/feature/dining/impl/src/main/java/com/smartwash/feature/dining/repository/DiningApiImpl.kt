package com.smartwash.feature.dining.repository

import com.smartwash.feature.dining.api.DiningApi
import com.smartwash.feature.dining.api.model.DiningRecipeVo
import com.smartwash.feature.dining.api.model.DiningScanResult
import com.smartwash.feature.dining.logic.DiningJoinUrlParseResult
import com.smartwash.feature.dining.logic.DiningJoinUrlParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DiningApi] 的 @Binds 实现（模式同 notice 的 NoticeApiImpl）：
 * 跨模块消费方（订单域创建订单页 / 壳层全局扫码页）经接口取数据，
 * 不触碰本域 Retrofit 接口与仓库细节。
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

    override suspend fun normalizeScanToDeepLink(raw: String): DiningScanResult =
        when (val parsed = DiningJoinUrlParser.parse(raw)) {
            // 深链串与 dining 深链贡献者（DiningDeepLinkModule）的 order/{id}?t= 协议同源；
            // 改深链串必须同步 DiningDeepLinkModuleTest 锚点（impl AGENTS.md「站内信深链」节）
            is DiningJoinUrlParseResult.Join ->
                DiningScanResult.DeepLink("smartwash://dining/order/${parsed.orderId}?t=${parsed.token}")
            DiningJoinUrlParseResult.NotDining -> DiningScanResult.NotDining
            DiningJoinUrlParseResult.Malformed -> DiningScanResult.Malformed
        }
}

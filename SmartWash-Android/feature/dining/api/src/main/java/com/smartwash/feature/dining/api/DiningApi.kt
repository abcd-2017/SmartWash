package com.smartwash.feature.dining.api

import com.smartwash.feature.dining.api.model.DiningRecipeVo

/**
 * 家庭点单域对外服务契约（跨模块数据消费方持有，命名对齐 NoticeApi/CouponApi 先例）。
 *
 * 消费方：后续订单域（创建订单页按分类拉上架菜谱组成「本单菜单」）经本契约访问，
 * 不触碰 :feature:dining:impl 的 Repository 实现细节；实现为 dining 域的 DiningApiImpl
 * （Hilt @Binds，di/DiningModule），依赖方向 order-impl → dining-api（铁律 2：feature 间仅 impl → 他人 api）。
 *
 * 本票先落最小面：上架菜谱分页查询；订单域需要的面（按 id 批量取菜谱等）随后续票扩展。
 */
interface DiningApi {

    /**
     * 上架菜谱分页（categoryId 精确筛选，null=全部含未分类；仅返回上架，新菜谱在前）
     */
    suspend fun getOnShelfRecipes(
        page: Int,
        size: Int,
        categoryId: Long? = null,
        keyword: String? = null,
    ): List<DiningRecipeVo>
}

package com.smartwash.feature.dining.api

import com.smartwash.feature.dining.api.model.DiningRecipeVo
import com.smartwash.feature.dining.api.model.DiningScanResult

/**
 * 吃啥域对外服务契约（跨模块数据消费方持有，命名对齐 NoticeApi/CouponApi 先例）。
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

    /**
     * 扫码内容归一化（全局扫码页消费，08 号票能力随扫码页上移壳层后经本契约回注）：
     * 识别本域加入链接（`{origin}/dining/join/{orderId}?t={token}`，与
     * DiningJoinUrlBuilder 生成的二维码同源），命中返回对应的
     * `smartwash://dining/order/{orderId}?t={token}` 深链串（dining 深链贡献者同协议单源），
     * 由壳层深链体系分发进加入入口页；未命中/结构非法语义见 [DiningScanResult]。
     * 加入 URL 的识别知识留在本域（JoinUrlParser 单一事实源），壳层扫码零业务感知。
     */
    suspend fun normalizeScanToDeepLink(raw: String): DiningScanResult
}

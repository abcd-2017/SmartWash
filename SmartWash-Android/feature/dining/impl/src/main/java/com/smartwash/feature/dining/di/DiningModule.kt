package com.smartwash.feature.dining.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.dining.api.DiningApi
import com.smartwash.feature.dining.api.DiningRoute
import com.smartwash.feature.dining.api.DiningServiceApi
import com.smartwash.feature.dining.repository.DiningApiImpl
import com.smartwash.feature.dining.repository.DiningRepository
import com.smartwash.feature.dining.sync.DiningOrderDetailSource
import com.smartwash.feature.dining.sync.DiningOrderSyncChannel
import com.smartwash.feature.dining.sync.PollingDiningOrderSyncChannel
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 家庭点单域服务契约绑定（模式同 notice 的 NoticeModule）：
 * - [DiningApi] → [DiningApiImpl]：跨模块消费方（后续订单域创建订单页）经接口访问；
 * - [DiningServiceApi] 实例由本域提供（Retrofit 实例由 common:network 的 NetworkModule 提供）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DiningModule {

    @Binds
    @Singleton
    abstract fun bindDiningApi(impl: DiningApiImpl): DiningApi

    /**
     * 订单变更订阅通道（10 号票）：绑定轮询首实现；他日换 SSE/WebSocket 只需改本绑定，
     * 业务代码（ViewModel）只依赖 [DiningOrderSyncChannel] 抽象。
     */
    @Binds
    @Singleton
    abstract fun bindDiningOrderSyncChannel(impl: PollingDiningOrderSyncChannel): DiningOrderSyncChannel
}

/** 家庭点单域网络供给 */
@Module
@InstallIn(SingletonComponent::class)
object DiningApiModule {

    @Provides
    @Singleton
    fun provideDiningServiceApi(retrofit: Retrofit): DiningServiceApi =
        retrofit.create(DiningServiceApi::class.java)

    /**
     * 订单变更通道（10 号票）的数据源接缝：把仓库的详情读取适配成 [DiningOrderDetailSource]，
     * 通道只依赖窄契约（便于单测伪实现驱动）。传输层替换时业务代码不动。
     */
    @Provides
    @Singleton
    fun provideDiningOrderDetailSource(repository: DiningRepository): DiningOrderDetailSource =
        DiningOrderDetailSource { orderId, knownVersion ->
            repository.getOrderDetail(orderId, knownVersion)
        }
}

/**
 * 家庭点单域深链贡献者（对齐 common:init 的 InitTask 模式：@IntoSet 汇入壳层
 * DeepLinkRegistry 的贡献者集合，host 字符串就地定义）。
 *
 * 本域 host：
 * - dining → 订单加入入口（`smartwash://dining/order/{orderId}?t={token}`，12 号票）
 *   或域首页订单列表（工作台入口 smartwash://dining 与 app_deeplink_route 字典同落点）。
 *
 * 落点定稿（07 号票重指向）：spec 规划 dining 域首页为「订单列表页」——04 号票曾因路由
 * 未落位占位指向菜谱库（[DiningRoute.RecipeLibrary]），07 号票订单列表落位后本 host
 * 正式重指向 [DiningRoute.OrderList]（工作台入口 smartwash://dining 与
 * app_deeplink_route 字典同批上线）。12 号票在 host 内细分订单加入路径，未带路径时
 * 仍落订单列表。取舍变更须同步 DiningDeepLinkModuleTest 与 impl AGENTS.md 两处锚点。
 *
 * 登录守卫：订单列表消费 /web/auth 鉴权接口，host 不进壳层 PUBLIC_DEEP_LINK_HOSTS
 * 白名单（未登录由壳层拦截落登录页，对齐受保护 host 默认语义）。
 */
@Module
@InstallIn(SingletonComponent::class)
object DiningDeepLinkModule {

    /** 订单加入入口路径前缀（与后端 DiningOrderServiceImpl#buildOrderDeepLink 同口径） */
    private const val ORDER_PATH_SEGMENT = "order"

    /** 加入凭证查询键（与 DiningJoinUrlParser / 二维码 URL 的 t 键同口径） */
    private const val TOKEN_QUERY_KEY = "t"

    @Provides
    @IntoSet
    fun provideDiningDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "dining" to DeepLinkRouteFactory { pathSegments, query -> diningRouteOf(pathSegments, query) },
        )
    }

    /**
     * dining host 路由解析：
     * - `smartwash://dining/order/{orderId}?t={token}` → [DiningRoute.OrderJoin]（12 号票）；
     * - 其余（含工作台入口 `smartwash://dining`、字典兜底、非法/缺失 orderId）→ [DiningRoute.OrderList]。
     * orderId 非法时回退订单列表，避免深链落到必然失败的页面。
     */
    private fun diningRouteOf(pathSegments: List<String>, query: Map<String, String>): DiningRoute {
        if (pathSegments.size == 2 && pathSegments[0] == ORDER_PATH_SEGMENT) {
            val orderId = pathSegments[1].toLongOrNull()
            if (orderId != null && orderId > 0L) {
                return DiningRoute.OrderJoin(orderId = orderId, token = query[TOKEN_QUERY_KEY].orEmpty())
            }
        }
        return DiningRoute.OrderList
    }
}

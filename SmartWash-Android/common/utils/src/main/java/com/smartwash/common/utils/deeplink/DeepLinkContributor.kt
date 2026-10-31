package com.smartwash.common.utils.deeplink

/**
 * 深链路由贡献者契约 —— 对齐 common:init 的 [com.smartwash.common.init.InitTask] 模式：
 * 契约放公共层（零 feature 依赖），**各域 impl 模块在自己的 Hilt Module 中以
 * `@Provides @IntoSet` 贡献本域 host 的路由工厂**，壳层 DeepLinkRegistry 注入
 * `Set<@JvmSuppressWildcards DeepLinkContributor>` 合并全部贡献。
 *
 * 示例（某域 impl 模块 di/DeepLinkModule.kt）：
 * ```
 * @Module
 * @InstallIn(SingletonComponent::class)
 * object XxxDeepLinkModule {
 *     @Provides
 *     @IntoSet
 *     fun provideXxxDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
 *         mapOf(
 *             "xxx-home" to DeepLinkRouteFactory { _, _ -> XxxRoute.Home.text },
 *         )
 *     }
 * }
 * ```
 */
fun interface DeepLinkContributor {

    /**
     * 本域可处理的 host → 路由工厂映射。
     * host 一律小写（与 DeepLinkParser 归一化后的 host 对齐）；
     * 返回空 map 表示本域无深链路由（无意义，但允许）。
     */
    fun routes(): Map<String, DeepLinkRouteFactory>
}

/**
 * 单个 host 的路由工厂：由 host 之后的 path 段与（已百分号解码的）query 参数，
 * 构造可直接 navigate 的路由字符串。
 *
 * @param pathSegments host 之后的剩余 path 段（如 smartwash://t/ab12 → ["ab12"]），保留原始大小写
 * @param query query 参数（value 已百分号解码；key 大小写敏感）
 * @return 可直接 navigate 的路由字符串；参数不归本域处理/非法时返回 null（上层降级 Unknown）
 */
fun interface DeepLinkRouteFactory {

    fun route(pathSegments: List<String>, query: Map<String, String>): String?
}

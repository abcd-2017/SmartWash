package com.smartwash.deeplink

import android.util.Log
import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 深链路由注册表（壳层导航聚合点自持）：
 * 注入 Hilt 多绑定集合 [DeepLinkContributor]（各域 impl 模块 `@Provides @IntoSet` 贡献，
 * 模式同 common:init 的 InitTaskRegistry），构造时合并为 host → 路由工厂总表。
 *
 * 重复 host 处理：后到者忽略并告警日志（host 应全域唯一，重复即配置错误，
 * 不让深链分发结果依赖 @IntoSet 的聚合顺序）。
 */
@Singleton
class DeepLinkRegistry @Inject constructor(
    contributors: Set<@JvmSuppressWildcards DeepLinkContributor>,
) {

    private val routes: Map<String, DeepLinkRouteFactory> = buildMap {
        contributors.forEachIndexed { index, contributor ->
            contributor.routes().forEach { (host, factory) ->
                val previous = put(host.lowercase(), factory)
                if (previous != null) {
                    Log.w(
                        TAG,
                        "深链 host 重复注册，忽略后到者（贡献者#$index）：host=$host"
                    )
                }
            }
        }
    }

    /** 全部已注册 host（壳层日志/诊断用） */
    val hosts: Set<String> get() = routes.keys

    /**
     * host → 路由串查找（供 DeepLinkParser.parse 的 routeLookup 消费）。
     *
     * @param host 已归一化小写的 scheme host
     * @param pathSegments host 之后的剩余 path 段（保留原始大小写）
     * @param query query 参数（value 已百分号解码）
     * @return 可直接 navigate 的路由字符串；未注册/工厂返回 null 均为 null
     */
    fun lookup(host: String, pathSegments: List<String>, query: Map<String, String>): String? {
        return routes[host]?.route(pathSegments, query)
    }

    private companion object {
        const val TAG = "DeepLinkRegistry"
    }
}

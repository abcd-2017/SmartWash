package com.smartwash.feature.toolbox.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.toolbox.ToolboxRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 工具箱深链贡献者（对齐 common:init 的 InitTask 模式：@IntoSet 汇入壳层
 * DeepLinkRegistry 的贡献者集合，host 字符串就地定义）。
 *
 * 本域 host：
 * - toolbox-short-code → ToolboxRoute.List（我的短链列表）
 * - t → ToolboxRoute.Detail（短链详情，path 段携带 code，形态对齐 web 端
 *   短链跳转链接 {toolbox.public-base-url}/web/t/{code} 的自有 scheme 版）
 */
@Module
@InstallIn(SingletonComponent::class)
object ToolboxDeepLinkModule {

    /** 短链码规则：4-16 位字母数字（URL path 段安全集，不含 -_ 与自定义别名规则区分） */
    private val SHORT_CODE_REGEX = Regex("^[0-9a-zA-Z]{4,16}$")

    @Provides
    @IntoSet
    fun provideToolboxDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "toolbox-short-code" to DeepLinkRouteFactory { _, _ -> ToolboxRoute.List.text },
            // t/{code}：仅接受 t + code 两段形态（parser 已滤空段）；code 校验在本域——
            // 详情路由其余字段走注册缺省值（见 ToolboxGraph），完整数据路径仍走列表页入口
            "t" to DeepLinkRouteFactory { pathSegments, _ ->
                val code = pathSegments.singleOrNull()
                if (code != null && SHORT_CODE_REGEX.matches(code)) {
                    "${ToolboxRoute.Detail.text}?code=$code"
                } else {
                    null
                }
            },
        )
    }
}

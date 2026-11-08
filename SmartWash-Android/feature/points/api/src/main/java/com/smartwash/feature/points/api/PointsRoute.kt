package com.smartwash.feature.points.api

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 积分域类型安全路由（模块内单一事实来源，模式对齐 CouponRoute/MoodRoute）。
 *
 * 页面取舍（单页 vs 两页）：签到 + 余额 + 排行榜 + 最近流水合并单页——后端
 * GET /web/auth/points/me 一次返回签到/余额/最近流水三块数据，月榜为独立小数据接口
 * （TOP 50 上限）；单页分区呈现可避免 tab 切换重复加载与跨 tab 状态同步，签到成功后
 * 的「局部刷新余额与流水」也只在单页内完成，无页面间联动成本。
 */
sealed interface PointsRoute : SmartWashRoute {

    /** 积分主页（签到 + 余额 + 月榜 + 最近流水单页，深链 host "points" 的落点） */
    @Serializable
    @SerialName("PointsHome")
    data object Home : PointsRoute
}

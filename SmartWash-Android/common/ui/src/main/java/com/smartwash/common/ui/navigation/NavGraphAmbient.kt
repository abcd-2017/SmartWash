package com.smartwash.common.ui.navigation

/**
 * 导航图注册期的组合期环境值快照。
 *
 * 存在原因：`NavHost` 的 `builder: NavGraphBuilder.() -> Unit` 不是 composable 上下文
 * （NavHost 内部经 `remember` 计算调用它），故各域图在注册时无法读取 `LocalReduceMotion`
 * 等 CompositionLocal。壳层 `MainActivity` 在组合期把当前值写入本对象，图内读取，
 * 语义与「图内直接读 CompositionLocal」等价。
 *
 * 当前仅承载 reduced-motion：userGraph / paymentGraph 的 per-destination 转场在注册期
 * 构造，需要该值；转场 lambda 在导航时才调用，届时读取本快照的最新值。
 */
object NavGraphAmbient {

    /** 全应用动效降级开关快照（壳层在组合期写入，与 [com.smartwash.common.utils.LocalReduceMotion] 同源） */
    @Volatile
    var reduceMotion: Boolean = false
}

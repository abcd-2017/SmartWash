package com.smartwash.common.init

import android.util.Log

/**
 * 统一日志内核（Android 端日志平台最底层实现，仅依赖 Android SDK，
 * 不引 Hilt / 协程 / Compose，保持本模块「零项目依赖」承诺）。
 *
 * 对外 API 由 :common:utils 的 `AppLogger` 委托暴露（调用方 import 路径不变）；
 * API 形状与鸿蒙端 `entry/src/main/ets/utils/Logger.ets` 对齐：debug / info / warn / error 四级。
 *
 * - TAG 规范：调用方只传模块/页面名，统一前缀 `SmartWash/` 在此拼接，
 *   最终输出形如 `SmartWash/IndexViewModel`；
 * - 输出门控：debug 构建全量输出；release 只输出 warn / error（BuildConfig.DEBUG
 *   门控，library 变体跟随 app debug/release，同 NetworkModule 的门控先例）；
 * - 级别约定：外部调用失败/异常 = error；业务拒绝/幂等命中/降级/空态预警 = warn；
 *   低频关键动作成功（登录、支付完成、订单流转等）= info；高频路径（渲染、轮询、
 *   每输入回调、每请求成功）禁止 info / warn，最多 debug；
 * - 敏感红线：手机号入日志前必须经 [maskPhone] 脱敏；密码、token、验证码、
 *   完整请求/响应体一律不打日志。
 */
object LogCore {

    private const val TAG_PREFIX = "SmartWash/"

    /** 中国大陆手机号（11 位，1[3-9] 开头，前后不能紧邻数字），用于日志脱敏 */
    private val PHONE_REGEX = Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)")

    /** debug 构建；release 静默 */
    fun debug(tag: String, msg: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.d(TAG_PREFIX + tag, msg, throwable)
    }

    /** 低频关键动作成功；release 静默 */
    fun info(tag: String, msg: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.i(TAG_PREFIX + tag, msg, throwable)
    }

    /** 业务拒绝 / 幂等命中 / 降级 / 空态预警；release 保留 */
    fun warn(tag: String, msg: String, throwable: Throwable? = null) {
        Log.w(TAG_PREFIX + tag, msg, throwable)
    }

    /** 外部调用失败 / 异常；release 保留 */
    fun error(tag: String, msg: String, throwable: Throwable? = null) {
        Log.e(TAG_PREFIX + tag, msg, throwable)
    }

    /**
     * 手机号脱敏（`138****5678`）：URL / 消息文本入日志前调用，
     * 避免验证码等接口路径或参数中的手机号明文进入日志。
     */
    fun maskPhone(text: String): String =
        PHONE_REGEX.replace(text) { match -> match.value.take(3) + "****" + match.value.takeLast(4) }
}

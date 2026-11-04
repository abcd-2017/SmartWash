package com.smartwash.common.utils

import com.smartwash.common.init.LogCore

/**
 * 统一日志工具（全端统一对外 API，实现委托 :common:init 的 [LogCore] 内核——
 * 内核仅依赖 Android SDK，common:init 得以保持零项目依赖承诺）。
 * API 形状与鸿蒙端 `entry/src/main/ets/utils/Logger.ets` 对齐：debug / info / warn / error 四级。
 *
 * - TAG 规范：调用方只传模块/页面名，统一前缀 `SmartWash/` 由内核拼接，
 *   最终输出形如 `SmartWash/IndexViewModel`；
 * - 输出门控（见 [LogCore]）：debug 构建全量输出；release 只输出 warn / error；
 * - 级别约定：外部调用失败/异常 = error；业务拒绝/幂等命中/降级/空态预警 = warn；
 *   低频关键动作成功（登录、支付完成、订单流转等）= info；高频路径（渲染、轮询、
 *   每输入回调、每请求成功）禁止 info / warn，最多 debug；
 * - 敏感红线：手机号入日志前必须经 [maskPhone] 脱敏；密码、token、验证码、
 *   完整请求/响应体一律不打日志。
 */
object AppLogger {

    /** debug 构建；release 静默 */
    fun debug(tag: String, msg: String, throwable: Throwable? = null) = LogCore.debug(tag, msg, throwable)

    /** 低频关键动作成功；release 静默 */
    fun info(tag: String, msg: String, throwable: Throwable? = null) = LogCore.info(tag, msg, throwable)

    /** 业务拒绝 / 幂等命中 / 降级 / 空态预警；release 保留 */
    fun warn(tag: String, msg: String, throwable: Throwable? = null) = LogCore.warn(tag, msg, throwable)

    /** 外部调用失败 / 异常；release 保留 */
    fun error(tag: String, msg: String, throwable: Throwable? = null) = LogCore.error(tag, msg, throwable)

    /**
     * 手机号脱敏（`138****5678`）：URL / 消息文本入日志前调用，
     * 避免验证码等接口路径或参数中的手机号明文进入日志。
     */
    fun maskPhone(text: String): String = LogCore.maskPhone(text)
}

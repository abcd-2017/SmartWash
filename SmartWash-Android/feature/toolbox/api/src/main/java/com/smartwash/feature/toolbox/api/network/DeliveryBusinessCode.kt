package com.smartwash.feature.toolbox.api.network

import java.io.IOException

/**
 * 设备投递业务码语义（信封 `code` 字段，非 HTTP 状态码）——契约 docs/delivery-api.md §一。
 *
 * 关键约束：403/404/409/413 是本功能新增的业务码，**禁止与全局 401 登出语义混淆**。
 * :common:network 的 ResponseInterceptor 只特判信封 401（登出）与 201（通用失败转
 * NetworkException），本组错误码原样穿过拦截器，由 DeliveryRepositoryImpl 统一转译为
 * [DeliveryException]，UI 按 code 映射文案。
 */
object DeliveryBusinessCode {

    /** 通用业务失败（参数非法、条目类型错误、上传失败等；已被拦截器转译为 NetworkException，列出供语义对照） */
    const val FAIL = 201

    /** 进入密码错误（对端 enter 侧；owner 侧一般不出现，保留映射防契约扩展） */
    const val WRONG_PASSWORD = 403

    /** 会话/条目不存在或已失效（过期、已关闭、归属不符——不区分暴露，读路径即时失效） */
    const val GONE = 404

    /** 状态冲突（单对端占用互斥；条目已被取走；文件条目未 take 就请求 content） */
    const val CONFLICT = 409

    /** 负载超限（条目数 > 50 / 单文件 > 20MB / 会话总字节 > 200MB） */
    const val PAYLOAD_TOO_LARGE = 413
}

/**
 * 投递业务异常：[businessCode] 携带信封 code 的契约语义（见 [DeliveryBusinessCode]），
 * message 保留后端原文供 UI 展示；传输层失败仍走既有 NetworkException。
 */
class DeliveryException(
    val businessCode: Int,
    message: String,
) : IOException(message)

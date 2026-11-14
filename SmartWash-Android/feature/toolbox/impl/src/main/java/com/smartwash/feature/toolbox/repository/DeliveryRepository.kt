package com.smartwash.feature.toolbox.repository

import com.smartwash.feature.toolbox.api.network.DeliveryItemVo
import com.smartwash.feature.toolbox.api.network.DeliverySessionDetailVo
import com.smartwash.feature.toolbox.api.network.DeliverySessionVo
import com.smartwash.feature.toolbox.api.network.DeliveryTakeVo
import com.smartwash.feature.toolbox.api.network.DeliveryTextContentVo
import java.io.InputStream

/**
 * 设备投递数据契约（接口抽象，模式对齐 ToolboxRepository）。
 *
 * 错误语义：
 * - 信封 403/404/409/413/500 → [DeliveryException]（携带业务码，UI 按码映射文案，**不触发全局 401**）；
 * - 信封 201（通用业务失败）→ 拦截器已转译为 NetworkException；
 * - 传输层失败 → NetworkException；
 * - data 为 null 一律按失败抛 NetworkException，区分「无数据」与「请求失败」。
 */
interface DeliveryRepository {

    suspend fun createSession(durationMinutes: Int?, password: String?): DeliverySessionVo

    /** 关闭会话（expire_at 前置，读路径立即失效；后端 data=null，成功以 code=200 为准） */
    suspend fun closeSession(code: String)

    /** 会话详情 + 全量条目（含已焚毁占位行，轮询消费，条目按 createdAt 正序） */
    suspend fun fetchSessionDetail(code: String): DeliverySessionDetailVo

    /** 发布文本条目（title 可空 = 后端截取正文前 30 字） */
    suspend fun publishText(code: String, textContent: String, title: String?): DeliveryItemVo

    /**
     * 发布文件/图片条目。[content] 为惰性流工厂：multipart 写出时按需打开
     * （OkHttp 连接重试会重复调用 writeTo，工厂须可重复开流），流的生命周期由本方法内 use 关闭。
     */
    suspend fun publishFile(
        code: String,
        kindCode: Int,
        fileName: String,
        size: Long,
        content: () -> InputStream?,
    ): DeliveryItemVo

    /** 取走条目（文本=认领+焚毁一步完成；文件=仅原子认领）；已被取走抛 DeliveryException(409) */
    suspend fun takeItem(code: String, itemId: Long): DeliveryTakeVo

    /** 文本条目内容（未取走返回正文；已焚毁 textContent=null，不视为失败） */
    suspend fun fetchTextContent(code: String, itemId: Long): DeliveryTextContentVo

    /**
     * 打开文件条目内容流（仅图片/文件条目；须先 [takeItem] 认领，未认领后端 409）。
     * 二进制流式转发不走响应信封，故用 :common:network 的 download OkHttp 客户端直连
     * （主 Retrofit 的 ResponseInterceptor 会把二进制体误当 JSON 解析）。
     * 返回后流保持打开，由调用方关闭；服务端在流正常完成后焚毁对象，流中断自动释放认领。
     */
    suspend fun openFileContent(code: String, itemId: Long): DeliveryFileContent
}

/** 文件条目内容流（焚毁语义：调用方读毕关闭，服务端随流完成删对象） */
class DeliveryFileContent(
    /** 条目 mime_type（缺省 application/octet-stream 时可能为 null） */
    val contentType: String?,
    /** Content-Length，服务端未回传时为 null */
    val contentLength: Long?,
    val stream: InputStream,
)

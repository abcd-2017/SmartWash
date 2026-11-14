package com.smartwash.feature.toolbox.repository

import com.smartwash.common.network.R
import com.smartwash.common.network.TokenProvider
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.HttpStatusCode
import com.smartwash.feature.toolbox.R as ToolboxR
import com.smartwash.feature.toolbox.network.AddDeliverySessionRequest
import com.smartwash.feature.toolbox.network.AddDeliveryTextRequest
import com.smartwash.feature.toolbox.network.DeliveryApi
import com.smartwash.feature.toolbox.network.DeliveryException
import com.smartwash.feature.toolbox.network.DeliveryItemVo
import com.smartwash.feature.toolbox.network.DeliverySessionDetailVo
import com.smartwash.feature.toolbox.network.DeliverySessionVo
import com.smartwash.feature.toolbox.network.DeliveryTakeVo
import com.smartwash.feature.toolbox.network.DeliveryTextContentVo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject
import retrofit2.Retrofit
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 设备投递数据实现：薄封装 DeliveryApi，统一「信封非 200 即按业务码抛 DeliveryException」
 * （403/404/409/413 原样穿过 ResponseInterceptor，在这里转译，**不触发全局 401 登出**）；
 * 信封 201 已被拦截器转译为 NetworkException，传输层失败同为 NetworkException。
 *
 * 文件内容下载走 @Named("download") OkHttp 客户端直连（该客户端不挂 ResponseInterceptor，
 * 避免二进制体被当 JSON 解析），token 经 TokenProvider 手动注入 Bearer；
 * 错误分支（未认领 409 / 会话失效 404）后端仍回信封 JSON，按 Content-Type 识别后转译。
 */
@Singleton
class DeliveryRepositoryImpl @Inject constructor(
    private val deliveryApi: DeliveryApi,
    private val retrofit: Retrofit,
    @Named("download") private val downloadClient: OkHttpClient,
    private val tokenProvider: TokenProvider,
) : DeliveryRepository {

    override suspend fun createSession(durationMinutes: Int?, password: String?): DeliverySessionVo {
        return deliveryApi.createSession(
            AddDeliverySessionRequest(durationMinutes = durationMinutes, password = password)
        ).requireDeliveryData("投递会话数据为空")
    }

    override suspend fun closeSession(code: String) {
        // 后端 Result.ok(null)：data 为 null 属正常不消费；信封码必须显式校验——
        // 403/404/409/413 原样穿过拦截器，不校验会把「会话不存在或已失效」判成假成功
        deliveryApi.closeSession(code).requireDeliverySuccess()
    }

    override suspend fun fetchSessionDetail(code: String): DeliverySessionDetailVo {
        return deliveryApi.fetchSessionDetail(code).requireDeliveryData("投递条目流为空")
    }

    override suspend fun publishText(code: String, textContent: String, title: String?): DeliveryItemVo {
        return deliveryApi.publishText(code, AddDeliveryTextRequest(textContent = textContent, title = title))
            .requireDeliveryData("投递条目数据为空")
    }

    override suspend fun publishFile(
        code: String,
        kindCode: Int,
        fileName: String,
        size: Long,
        content: () -> InputStream?,
    ): DeliveryItemVo = withContext(Dispatchers.IO) {
        // 流式请求体：不把 20MB 读进内存，writeTo 时按工厂开流（重试重开流，工厂幂等）
        val fileBody = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
            override fun contentLength(): Long = size
            override fun writeTo(sink: BufferedSink) {
                val input = content() ?: run {
                    AppLogger.warn(TAG, "publishFile: 内容流工厂返回空，所选内容已失效 fileName=$fileName size=$size")
                    throw NetworkException("所选内容已失效", R.string.error_network_fail)
                }
                input.use {
                    // 实际写出与声明长度不符时主动失败并给真实原因——provider SIZE 是快照值，
                    // 两次开流之间内容仍可能被改写；不拦会被 OkHttp 判 ProtocolException，
                    // 再被拦截器误转成「网络连接失败」（audit P1：错误文案按真实失败类型）
                    var written = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = it.read(buffer)
                        if (read == -1) break
                        if (written + read > size) {
                            // message 留空使用户可见文案走 resId（规范 §9 禁硬编码中文）
                            throw NetworkException(
                                "",
                                ToolboxR.string.toolbox_delivery_error_content_changed,
                            )
                        }
                        sink.write(buffer, 0, read)
                        written += read
                    }
                    if (written != size) {
                        // message 留空使用户可见文案走 resId（规范 §9 禁硬编码中文）
                        throw NetworkException(
                            "",
                            ToolboxR.string.toolbox_delivery_error_content_changed,
                        )
                    }
                }
            }
        }
        val filePart = MultipartBody.Part.createFormData("file", fileName, fileBody)
        val kindPart = kindCode.toString().toRequestBody("text/plain".toMediaType())
        deliveryApi.publishFile(code, kindPart, filePart).requireDeliveryData("投递条目数据为空")
    }

    override suspend fun takeItem(code: String, itemId: Long): DeliveryTakeVo {
        return deliveryApi.takeItem(code, itemId).requireDeliveryData("取走结果为空")
    }

    override suspend fun fetchTextContent(code: String, itemId: Long): DeliveryTextContentVo {
        return deliveryApi.fetchTextContent(code, itemId).requireDeliveryData("条目内容为空")
    }

    override suspend fun openFileContent(code: String, itemId: Long): DeliveryFileContent =
        withContext(Dispatchers.IO) {
            val url = retrofit.baseUrl().resolve("web/auth/toolbox/delivery/sessions/$code/items/$itemId/content")
                ?: throw NetworkException("投递地址构造失败", R.string.error_network_fail)
            val request = Request.Builder()
                .url(url)
                // download 客户端无 RequestInterceptor，token 手动注入（对齐 RequestInterceptor 的 Bearer 格式）
                .header("Authorization", "Bearer ${tokenProvider.currentToken().orEmpty()}")
                .build()
            val response = downloadClient.newCall(request).execute()
            try {
                if (!response.isSuccessful) {
                    AppLogger.error(TAG, "openFileContent: 内容下载传输层失败 http=${response.code} itemId=$itemId")
                    throw NetworkException("请求失败", R.string.error_network_fail)
                }
                val contentType = response.header("Content-Type")
                if (contentType?.contains("json", ignoreCase = true) == true) {
                    // 错误信封（未认领 409 / 会话失效 404 等）：读出 JSON 按业务码转译，绝不当作二进制流消费
                    val body = response.body?.string().orEmpty()
                    val code2 = runCatching { JSONObject(body).optInt("code") }.getOrDefault(HttpStatusCode.Error.code)
                    val message = runCatching { JSONObject(body).optString("message") }.getOrDefault("")
                    AppLogger.warn(TAG, "openFileContent: 内容下载返回错误信封 code=$code2 itemId=$itemId")
                    throw DeliveryException(code2, message.ifBlank { "投递内容获取失败" })
                }
                val body = response.body ?: run {
                    AppLogger.warn(TAG, "openFileContent: 内容下载响应体为空 itemId=$itemId")
                    throw NetworkException("投递内容为空", R.string.error_network_fail)
                }
                DeliveryFileContent(
                    contentType = contentType?.substringBefore(';')?.trim(),
                    contentLength = body.contentLength().takeIf { it >= 0 },
                    // 返回后 response 保持打开，流关闭即释放连接（此函数内禁止 use 包裹）
                    stream = body.byteStream(),
                )
            } catch (e: CancellationException) {
                response.close()
                throw e
            } catch (e: Throwable) {
                response.close()
                throw e
            }
        }

    /** 信封校验：code=200 取 data，否则按业务码抛 [DeliveryException]（403/404/409/413…不触发 401 语义） */
    private fun <T> ApiResult<T>.requireDeliveryData(emptyMessage: String): T {
        if (code != HttpStatusCode.Success.code) {
            AppLogger.warn(TAG, "requireDeliveryData: 投递业务拒绝 code=$code")
            throw DeliveryException(code, message)
        }
        return data ?: run {
            AppLogger.warn(TAG, "requireDeliveryData: 投递响应 data 为空（$emptyMessage），按失败上抛")
            throw NetworkException(emptyMessage, R.string.error_network_fail)
        }
    }

    /** 信封码校验（data 允许为 null，closeSession 场景）：非 200 按业务码抛 [DeliveryException] */
    private fun ApiResult<*>.requireDeliverySuccess() {
        if (code != HttpStatusCode.Success.code) {
            AppLogger.warn(TAG, "requireDeliverySuccess: 投递业务拒绝 code=$code")
            throw DeliveryException(code, message)
        }
    }

    private companion object {
        const val TAG = "DeliveryRepository"
    }
}

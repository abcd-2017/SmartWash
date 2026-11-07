package com.smartwash.feature.readlater.repository

import com.smartwash.common.network.R
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.readlater.network.AddReadLaterRequest
import com.smartwash.feature.readlater.network.ReadLaterApi
import com.smartwash.feature.readlater.network.ReadLaterVo
import com.smartwash.feature.readlater.network.UpdateReadLaterRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 稍后读数据实现：薄封装 ReadLaterApi，统一「data 为 null 即失败」语义
 * （业务失败已被 ResponseInterceptor 转译为 NetworkException 抛出，
 * 这里只兜住 code=200 但 data 缺失的分支），成功以 code=200 为准。
 */
@Singleton
class ReadLaterRepositoryImpl @Inject constructor(
    private val readLaterApi: ReadLaterApi,
) : ReadLaterRepository {

    override suspend fun add(url: String, tags: List<String>): Long {
        return readLaterApi.addReadLater(AddReadLaterRequest(url = url, tags = tags)).data ?: run {
            AppLogger.warn(TAG, "add: 收藏响应 data 为空，按失败上抛")
            throw NetworkException("收藏响应为空", R.string.error_network_fail)
        }
    }

    override suspend fun page(
        page: Int,
        size: Int,
        status: Int?,
        tag: String?,
        keyword: String?,
    ): PageData<ReadLaterVo> {
        // 空页 data 为空 records 是合法成功；data 缺失（null）才按失败处理
        return readLaterApi.listReadLater(
            page = page,
            size = size,
            status = status,
            tag = tag,
            keyword = keyword,
        ).data ?: run {
            AppLogger.warn(TAG, "page: 列表响应 data 为空（page=$page status=$status），按失败上抛")
            throw NetworkException("列表数据为空", R.string.error_network_fail)
        }
    }

    override suspend fun generateDigest(id: Long): String {
        return readLaterApi.generateDigest(id).data ?: run {
            AppLogger.warn(TAG, "generateDigest: 摘要响应 data 为空（id=$id），按失败上抛")
            throw NetworkException("摘要响应为空", R.string.error_network_fail)
        }
    }

    override suspend fun update(id: Long, tags: List<String>?, status: Int?): String {
        return readLaterApi.updateReadLater(id, UpdateReadLaterRequest(tags = tags, status = status)).data
            ?: run {
                AppLogger.warn(TAG, "update: 更新响应 data 为空（id=$id），按失败上抛")
                throw NetworkException("更新响应为空", R.string.error_network_fail)
            }
    }

    override suspend fun delete(id: Long): String {
        return readLaterApi.deleteReadLater(id).data ?: run {
            AppLogger.warn(TAG, "delete: 删除响应 data 为空（id=$id），按失败上抛")
            throw NetworkException("删除响应为空", R.string.error_network_fail)
        }
    }

    override suspend fun shorten(id: Long): String {
        return readLaterApi.shorten(id).data ?: run {
            AppLogger.warn(TAG, "shorten: 短链响应 data 为空（id=$id），按失败上抛")
            throw NetworkException("短链响应为空", R.string.error_network_fail)
        }
    }

    private companion object {
        const val TAG = "ReadLaterRepository"
    }
}

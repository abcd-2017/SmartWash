package com.smartwash.feature.toolbox.repository

import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.toolbox.api.network.AssetTagApi
import com.smartwash.feature.toolbox.api.network.AssetTagVo
import com.smartwash.feature.toolbox.api.network.CreateAssetTagRequest
import com.smartwash.feature.toolbox.api.network.UpdateAssetTagRequest
import javax.inject.Inject

/**
 * 物品标签仓库实现：解包 ApiResult.data（业务失败由 :common:network 拦截器转译为 NetworkException）。
 */
class AssetTagRepositoryImpl @Inject constructor(
    private val api: AssetTagApi,
) : AssetTagRepository {

    override suspend fun createAssetTag(body: CreateAssetTagRequest): AssetTagVo =
        api.createAssetTag(body).data ?: throw IllegalStateException("创建标签响应缺失数据")

    override suspend fun pageAssetTags(page: Int, size: Int): PageData<AssetTagVo> =
        api.pageAssetTags(page, size).data ?: PageData(emptyList(), 0, size, page)

    override suspend fun updateAssetTag(id: Long, body: UpdateAssetTagRequest): AssetTagVo =
        api.updateAssetTag(id, body).data ?: throw IllegalStateException("更新标签响应缺失数据")

    override suspend fun deleteAssetTag(id: Long) {
        api.deleteAssetTag(id)
    }
}

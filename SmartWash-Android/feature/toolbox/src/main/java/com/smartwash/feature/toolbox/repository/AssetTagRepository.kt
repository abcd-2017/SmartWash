package com.smartwash.feature.toolbox.repository

import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.toolbox.network.AssetTagVo
import com.smartwash.feature.toolbox.network.CreateAssetTagRequest
import com.smartwash.feature.toolbox.network.UpdateAssetTagRequest

/**
 * 物品标签（一物一码 M2）仓库契约（JVM 单测可替换 Fake）。
 */
interface AssetTagRepository {

    suspend fun createAssetTag(body: CreateAssetTagRequest): AssetTagVo

    suspend fun pageAssetTags(page: Int, size: Int): PageData<AssetTagVo>

    suspend fun updateAssetTag(id: Long, body: UpdateAssetTagRequest): AssetTagVo

    suspend fun deleteAssetTag(id: Long)
}

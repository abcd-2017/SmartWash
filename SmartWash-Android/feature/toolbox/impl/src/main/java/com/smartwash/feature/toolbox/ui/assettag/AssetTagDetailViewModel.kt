package com.smartwash.feature.toolbox.ui.assettag

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.api.network.AssetTagVo
import com.smartwash.feature.toolbox.api.network.UpdateAssetTagRequest
import com.smartwash.feature.toolbox.repository.AssetTagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 物品标签详情 ViewModel：基础字段由列表路由携带；
 * 寻物状态流转（PUT status）/ 删除（DELETE）成功后回列表并触发刷新。
 */
@HiltViewModel
class AssetTagDetailViewModel @Inject constructor(
    private val repository: AssetTagRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** 路由入参还原（缺省防御值与 AssetTagVo 默认一致） */
    val assetTag = AssetTagVo(
        id = savedStateHandle.get<Long>(ARG_ID) ?: -1L,
        code = savedStateHandle.get<String>(ARG_CODE).orEmpty(),
        itemName = savedStateHandle.get<String>(ARG_ITEM_NAME).orEmpty(),
        itemDesc = savedStateHandle.get<String>(ARG_ITEM_DESC).orEmpty(),
        status = savedStateHandle.get<Int>(ARG_STATUS) ?: 0,
        hasContact = savedStateHandle.get<Boolean>(ARG_HAS_CONTACT) ?: false,
        tagUrl = savedStateHandle.get<String>(ARG_TAG_URL).orEmpty(),
        scanCount = savedStateHandle.get<Long>(ARG_SCAN_COUNT) ?: 0L,
        lastScannedAt = savedStateHandle.get<String>(ARG_LAST_SCANNED)?.ifBlank { null },
    )

    /** 状态流转提交状态 */
    private val _updateState = MutableStateFlow<RequestState>(RequestState.Idle)
    val updateState = _updateState.asStateFlow()

    /** 删除提交状态 */
    private val _deleteState = MutableStateFlow<RequestState>(RequestState.Idle)
    val deleteState = _deleteState.asStateFlow()

    /** 寻物状态流转（0 正常 / 1 寻物中 / 2 已寻回，裁定在后端） */
    fun changeStatus(status: Int) {
        viewModelScope.launch {
            _updateState.value = RequestState.Loading
            try {
                repository.updateAssetTag(assetTag.id, UpdateAssetTagRequest(status = status))
                _updateState.value = RequestState.Success
                AppLogger.info(TAG, "标签状态流转成功 id=${assetTag.id} status=$status")
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "changeStatus: ${e.message}", e)
                _updateState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "changeStatus: ${e.message}", e)
                _updateState.value = RequestState.Error(R.string.asset_tag_error_submit)
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            _deleteState.value = RequestState.Loading
            try {
                repository.deleteAssetTag(assetTag.id)
                _deleteState.value = RequestState.Success
                AppLogger.info(TAG, "标签删除成功 id=${assetTag.id}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "delete: ${e.message}", e)
                _deleteState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "delete: ${e.message}", e)
                _deleteState.value = RequestState.Error(R.string.asset_tag_error_submit)
            }
        }
    }

    fun resetUpdateState() {
        _updateState.value = RequestState.Idle
    }

    fun resetDeleteState() {
        _deleteState.value = RequestState.Idle
    }

    companion object {
        const val ARG_ID = "id"
        const val ARG_CODE = "code"
        const val ARG_ITEM_NAME = "itemName"
        const val ARG_ITEM_DESC = "itemDesc"
        const val ARG_STATUS = "status"
        const val ARG_HAS_CONTACT = "hasContact"
        const val ARG_TAG_URL = "tagUrl"
        const val ARG_SCAN_COUNT = "scanCount"
        const val ARG_LAST_SCANNED = "lastScannedAt"

        private const val TAG = "AssetTagDetailViewModel"
    }
}

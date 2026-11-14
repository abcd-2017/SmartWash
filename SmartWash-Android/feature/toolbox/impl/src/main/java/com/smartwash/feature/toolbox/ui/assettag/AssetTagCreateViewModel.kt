package com.smartwash.feature.toolbox.ui.assettag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.logic.AssetTagRules
import com.smartwash.feature.toolbox.logic.ShortCodeRules
import com.smartwash.feature.toolbox.api.network.CreateAssetTagRequest
import com.smartwash.feature.toolbox.repository.AssetTagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 新建物品标签 ViewModel：表单单向数据流；提交前置校验失败不触网；
 * 成功后置 ASSET_TAG_CHANGED_KEY 刷新信号并回列表。
 */
@HiltViewModel
class AssetTagCreateViewModel @Inject constructor(
    private val repository: AssetTagRepository,
) : ViewModel() {

    /** 表单状态（联系方式默认关闭输入 = 不留联系方式） */
    data class FormState(
        val itemName: String = "",
        val itemDesc: String = "",
        val contact: String = "",
        val customCode: String = "",
    )

    private val _form = MutableStateFlow(FormState())
    val form = _form.asStateFlow()

    private val _submitState = MutableStateFlow<RequestState>(RequestState.Idle)
    val submitState = _submitState.asStateFlow()

    fun updateItemName(value: String) {
        _form.value = _form.value.copy(
            itemName = value.take(AssetTagRules.ITEM_NAME_MAX),
        )
    }

    fun updateItemDesc(value: String) {
        _form.value = _form.value.copy(
            itemDesc = value.take(AssetTagRules.ITEM_DESC_MAX),
        )
    }

    fun updateContact(value: String) {
        _form.value = _form.value.copy(
            contact = value.take(AssetTagRules.CONTACT_MAX),
        )
    }

    fun updateCustomCode(value: String) {
        _form.value = _form.value.copy(customCode = value)
    }

    fun submit() {
        val f = _form.value
        if (!AssetTagRules.isValidItemName(f.itemName)) {
            _submitState.value = RequestState.Error(R.string.asset_tag_error_item_name)
            return
        }
        if (!AssetTagRules.isValidContact(f.contact)) {
            _submitState.value = RequestState.Error(R.string.asset_tag_error_contact)
            return
        }
        if (f.customCode.isNotBlank() && !ShortCodeRules.isValidCustomCode(f.customCode.trim())) {
            _submitState.value = RequestState.Error(R.string.toolbox_error_invalid_custom_code)
            return
        }
        viewModelScope.launch {
            _submitState.value = RequestState.Loading
            try {
                repository.createAssetTag(
                    CreateAssetTagRequest(
                        itemName = f.itemName.trim(),
                        itemDesc = f.itemDesc.trim().ifEmpty { null },
                        // 联系方式明文仅 HTTPS 上行，服务端加密落库
                        contact = f.contact.trim().ifEmpty { null },
                        customCode = f.customCode.trim().ifEmpty { null },
                    )
                )
                _submitState.value = RequestState.Success
                AppLogger.info(TAG, "物品标签创建成功 itemName=${f.itemName.trim()}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "submit: ${e.message}", e)
                _submitState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "submit: ${e.message}", e)
                _submitState.value = RequestState.Error(R.string.asset_tag_error_submit)
            }
        }
    }

    fun resetSubmitState() {
        _submitState.value = RequestState.Idle
    }

    private companion object {
        const val TAG = "AssetTagCreateViewModel"
    }
}

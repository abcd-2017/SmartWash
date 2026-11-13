package com.smartwash.feature.dining.ui.create

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.api.model.AddDiningRecipeRequest
import com.smartwash.feature.dining.api.model.DiningCategoryVo
import com.smartwash.feature.dining.logic.DiningRecipeFormValidator
import com.smartwash.feature.dining.logic.DiningRecipeFormValidator.DiningRecipeFormError
import com.smartwash.feature.dining.repository.DiningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/**
 * 新增菜谱页 ViewModel：表单状态 + 端上校验（DiningRecipeFormValidator，JVM 单测锚点）+
 * 封面 PhotoPicker 选图上传（头像上传先例模式：contentResolver 读 bytes → Multipart "file"）
 * + 提交（source=USER 直上架由后端写入）。
 *
 * 状态划分：[coverUploadState]（封面独立态，失败不影响表单继续编辑）与 [submitState]
 * （提交动作态，Loading 期间防重）独立；[formError] 为端上校验首错（提交时设置，随输入清除）。
 */
@HiltViewModel
class DiningRecipeCreateViewModel @Inject constructor(
    private val diningRepository: DiningRepository,
    private val application: Application,
) : ViewModel() {

    /** 菜名（输入经 onNameChange 回写，单向下发） */
    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()

    /** 分类目录（加载失败仅留痕——分类可空，未分类也能提交，不拖垮表单主流程） */
    private val _categories = MutableStateFlow<List<DiningCategoryVo>>(emptyList())
    val categories = _categories.asStateFlow()

    /** 选中分类（null=未分类，对应后端哨兵 0/null） */
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId = _selectedCategoryId.asStateFlow()

    /** 食材名列表（输入框「添加」入列，chips 可移除） */
    private val _ingredients = MutableStateFlow<List<String>>(emptyList())
    val ingredients = _ingredients.asStateFlow()

    /** 步骤（多行，输入经 onStepsChange 回写，单向下发） */
    private val _steps = MutableStateFlow("")
    val steps = _steps.asStateFlow()

    /** 已选封面本地 Uri（null=未选；仅用于提交前预览，回显走上传后的直链） */
    private val _coverUri = MutableStateFlow<Uri?>(null)
    val coverUri = _coverUri.asStateFlow()

    /** 封面上传成功后的对象键（提交时随表单回传；null=未上传/未成功） */
    private var coverObjectKey: String? = null

    private val _coverUploadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val coverUploadState = _coverUploadState.asStateFlow()

    private val _submitState = MutableStateFlow<RequestState>(RequestState.Idle)
    val submitState = _submitState.asStateFlow()

    /** 端上校验首错（提交时设置；对应字段输入变化即清除） */
    private val _formError = MutableStateFlow<DiningRecipeFormError?>(null)
    val formError = _formError.asStateFlow()

    /** 表单错误 → 文案 res（UI 层映射，纯逻辑枚举不持有文案） */
    fun errorRes(error: DiningRecipeFormError): Int = when (error) {
        DiningRecipeFormError.NAME_REQUIRED -> R.string.dining_form_error_name_required
        DiningRecipeFormError.NAME_TOO_LONG -> R.string.dining_form_error_name_too_long
        DiningRecipeFormError.INGREDIENT_EMPTY -> R.string.dining_form_error_ingredient_empty
        DiningRecipeFormError.INGREDIENT_TOO_LONG -> R.string.dining_form_error_ingredient_too_long
        DiningRecipeFormError.INGREDIENT_TOO_MANY -> R.string.dining_form_error_ingredient_too_many
        DiningRecipeFormError.STEPS_TOO_LONG -> R.string.dining_form_error_steps_too_long
    }

    /** 分类目录独立拉取（失败仅留痕，未分类仍可提交） */
    fun loadCategories() {
        if (_categories.value.isNotEmpty()) return
        viewModelScope.launch {
            try {
                _categories.value = diningRepository.getCategories()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "分类目录拉取失败（可未分类提交）：${e.message}")
            }
        }
    }

    fun onNameChange(value: String) {
        _name.value = value
        clearFormError()
    }

    fun onStepsChange(value: String) {
        _steps.value = value
        clearFormError()
    }

    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun addIngredient(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        // 输入框入列即清洗；上限校验在提交时统一做（列表 chips 可视化自检）
        _ingredients.value = _ingredients.value + trimmed
    }

    fun removeIngredient(index: Int) {
        if (index !in _ingredients.value.indices) return
        _ingredients.value = _ingredients.value.toMutableList().apply { removeAt(index) }
    }

    /** PhotoPicker 选图回调 → 上传（独立态，失败仅 Toast 不阻断表单） */
    fun uploadCover(uri: Uri) {
        if (_coverUploadState.value is RequestState.Loading) return
        _coverUri.value = uri
        _coverUploadState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                // 头像上传先例模式：contentResolver 读 bytes + 按 MIME 推扩展名
                // （后端校验魔数与扩展名一致性，文件名不能伪造——image/png 统一配 .png）
                val bytes = application.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException(application.getString(R.string.dining_create_cover_failed))
                val mimeType = application.contentResolver.getType(uri) ?: "image/jpeg"
                val extension = when (mimeType) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
                val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "cover.$extension", requestBody)
                val objectKey = diningRepository.uploadCover(part)
                coverObjectKey = objectKey
                // 直链不落端上（对象键由后端 MinioConfig.urlOf 拼装）：本页预览用本地 Uri，
                // 提交成功回菜谱库后由列表 SmartWashAsyncImage 按 coverUrl 回显（票验收第 4 条）
                AppLogger.info(TAG, "菜谱封面上传成功 size=${bytes.size}B key=$objectKey")
                _coverUploadState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "uploadCover: ${e.message}", e)
                _coverUploadState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "uploadCover: ${e.message}", e)
                _coverUploadState.value = RequestState.Error(R.string.dining_create_cover_failed, e.message)
            }
        }
    }

    /** 提交（端上校验先行；Loading 期间防重）。成功由页面消费后返回菜谱库 */
    fun submit() {
        if (_submitState.value is RequestState.Loading) return
        val nameValidation = DiningRecipeFormValidator.validateName(_name.value)
        if (!nameValidation.valid) {
            _formError.value = nameValidation.error
            return
        }
        val ingredientsValidation = DiningRecipeFormValidator.validateIngredients(_ingredients.value)
        if (!ingredientsValidation.valid) {
            _formError.value = ingredientsValidation.error
            return
        }
        val stepsValidation = DiningRecipeFormValidator.validateSteps(_steps.value)
        if (!stepsValidation.valid) {
            _formError.value = stepsValidation.error
            return
        }
        _formError.value = null
        _submitState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                diningRepository.addRecipe(
                    AddDiningRecipeRequest(
                        name = nameValidation.normalized,
                        categoryId = _selectedCategoryId.value,
                        ingredients = ingredientsValidation.normalized.ifEmpty { null },
                        steps = stepsValidation.normalized,
                        coverObjectKey = coverObjectKey,
                    )
                )
                // 低频关键动作成功（内容上架）
                AppLogger.info(TAG, "菜谱新增成功 name=${nameValidation.normalized}")
                _submitState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "submit: ${e.message}", e)
                _submitState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 对应字段输入变化即清除校验错误（就近提示，不打扰其他字段） */
    fun clearFormError() {
        _formError.value = null
    }

    fun resetSubmitState() {
        _submitState.value = RequestState.Idle
    }

    fun resetCoverUploadState() {
        _coverUploadState.value = RequestState.Idle
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "DiningRecipeCreateViewModel"
    }
}

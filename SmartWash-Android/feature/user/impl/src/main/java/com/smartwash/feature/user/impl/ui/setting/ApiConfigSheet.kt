package com.smartwash.feature.user.impl.ui.setting

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.user.impl.R

/**
 * 「解读模型」BYOK 编辑弹层（issue #79，设置页观象台分组入口）。
 *
 * 交互形态对齐 PaymentPage 优惠券弹层（项目唯一 ModalBottomSheet 先例）：surface 令牌底、
 * tonalElevation 归零、顶部 radiusXl 圆角、自绘拖拽指示条。表单三字段端上均必填
 * （服务端 PUT 三字段必填，编辑态占位引导重输 key）；「测试连接」走 POST test 即时反馈，
 * 「保存」走 PUT（服务端真实试呼，失败回业务失败信封，文案经 ResponseInterceptor
 * 解析为用户可读 message）；已配置时提供「删除配置」二次确认（AppConfirmDialog）。
 *
 * 隐私红线：明文 API Key 只存在于输入框内存态（组合内 remember），不落 DataStore、
 * 不进日志；列表回显一律掩码。
 *
 * 动效：仅按钮 loading spinner（ButtonSpinner 内置 reduced motion 降级）与展开/收起
 * 高度变化（直接显隐，无自定义动效需降级分支）；无新增无限动画。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConfigSheet(
    settingViewModel: SettingViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val view = currentView()

    val config by settingViewModel.apiConfig.collectAsState()
    val configState by settingViewModel.apiConfigState.collectAsState()
    val platformModels by settingViewModel.platformModels.collectAsState()
    val testState by settingViewModel.apiTestState.collectAsState()
    val saveState by settingViewModel.apiSaveState.collectAsState()
    val deleteState by settingViewModel.apiDeleteState.collectAsState()
    val fieldError by settingViewModel.apiFieldError.collectAsState()

    // 表单内存态（仅编辑期存在；明文 key 不持久化）
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var catalogExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 首次配置回填标记：回填只发生在首次拉取完成且用户尚未被覆盖输入之前
    var formInitialized by remember { mutableStateOf(false) }

    // 关闭统一收口：重置弹层瞬态（测试/保存/删除/校验反馈），再交给页面收起
    val closeSheet = {
        settingViewModel.resetApiEditState()
        onDismiss()
    }

    // 弹层打开即拉取最新配置与平台模型目录（行内数据可能滞后）
    LaunchedEffect(Unit) {
        settingViewModel.refreshApiConfig()
        settingViewModel.loadPlatformModels()
    }

    // 编辑态预填：GET 返回后回填 baseUrl/model；apiKey 始终留空（占位「重新输入 API Key」）
    LaunchedEffect(config, configState) {
        if (!formInitialized && (config != null || configState is RequestState.Success)) {
            formInitialized = true
            config?.let { cfg ->
                baseUrl = cfg.customBaseUrl.orEmpty()
                model = cfg.customModel.orEmpty()
            }
        }
    }

    // 副作用统一放 LaunchedEffect（Compose 硬规则：禁止写在渲染分支）
    LaunchedEffect(testState) {
        if (testState is RequestState.Success) {
            // 测试通过是因果明确的时刻，给一次确认触感（规范 §7.7）
            view.performHaptic(HapticEffect.LIGHT)
        }
    }
    LaunchedEffect(saveState) {
        val state = saveState
        if (state is RequestState.Success) {
            Toast.makeText(
                context, context.getString(R.string.api_config_save_success), Toast.LENGTH_SHORT
            ).show()
            closeSheet()
        }
        // Error 不 Toast：服务端原因文案在表单反馈区 inline 展示（保存按钮旁，语境更强）
    }
    LaunchedEffect(deleteState) {
        val state = deleteState
        if (state is RequestState.Success) {
            Toast.makeText(
                context, context.getString(R.string.api_config_delete_success), Toast.LENGTH_SHORT
            ).show()
            closeSheet()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { closeSheet() },
        sheetState = sheetState,
        // 对齐 PaymentPage 弹层画法：surface 令牌底（tonalElevation 归零防 M3 默认叠色），
        // 顶部 radiusXl 20dp 圆角，自绘拖拽指示条（M3 默认样式不受令牌控制）
        containerColor = AppColors.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = AppDimens.radiusXl, topEnd = AppDimens.radiusXl),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = AppDimens.spaceSm)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(AppDimens.radiusFull))
                    .background(AppColors.colorScheme.outline)
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = AppDimens.pagePadding),
        ) {
            Text(
                text = stringResource(R.string.api_config_title),
                style = MaterialTheme.typography.headlineSmall,
                color = AppColors.colorScheme.textPrimary,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceXs))
            // 说明文案：与「隐私与免责声明」口径一致（issue #79 第 3 点）
            Text(
                text = stringResource(R.string.api_config_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))

            // 已配置摘要（仅掩码回显，帮助理解为何编辑需重输 key）
            config?.let { cfg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppDimens.radiusMd))
                        .background(AppColors.colorScheme.surfaceVariant)
                        .padding(horizontal = AppDimens.spaceMd, vertical = 10.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = cfg.customModel.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = AppColors.colorScheme.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (cfg.verified == 1) {
                                Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                                VerifiedBadge()
                            }
                        }
                        if (!cfg.customBaseUrl.isNullOrBlank()) {
                            Text(
                                text = cfg.customBaseUrl.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.colorScheme.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!cfg.apiKeyMask.isNullOrBlank()) {
                            Text(
                                text = cfg.apiKeyMask.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.colorScheme.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            }

            // 配置加载失败：给重试（说明发生了什么 + 怎么解决，规范 §3.6）
            if (configState is RequestState.Error) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.api_config_load_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.api_config_retry),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = AppColors.colorScheme.primary,
                        modifier = Modifier
                            .pressable(onClick = { settingViewModel.refreshApiConfig() })
                            .padding(horizontal = AppDimens.spaceSm, vertical = 8.dp),
                    )
                }
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            }

            // 接入点地址（服务端校验 https 且非内网；端上先拦 https 前缀给即时文案）
            ApiConfigTextField(
                label = stringResource(R.string.api_config_base_url_label),
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    settingViewModel.clearFieldError(ApiConfigField.BASE_URL)
                },
                placeholder = stringResource(R.string.api_config_base_url_hint),
                isError = fieldError?.field == ApiConfigField.BASE_URL,
                errorMessage = fieldError?.takeIf { it.field == ApiConfigField.BASE_URL }
                    ?.let { stringResource(it.messageRes) },
                keyboardType = KeyboardType.Uri,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))

            // 模型名称（BYOK 不限平台目录，输入框独立填写；目录仅作建议）
            ApiConfigTextField(
                label = stringResource(R.string.api_config_model_label),
                value = model,
                onValueChange = {
                    model = it
                    settingViewModel.clearFieldError(ApiConfigField.MODEL)
                },
                placeholder = stringResource(R.string.api_config_model_hint),
                isError = fieldError?.field == ApiConfigField.MODEL,
                errorMessage = fieldError?.takeIf { it.field == ApiConfigField.MODEL }
                    ?.let { stringResource(it.messageRes) },
                keyboardType = KeyboardType.Text,
            )

            // 平台模型目录建议（可选）：展开列表点选回填 modelId；自绘列表（无 DropdownMenu，
            // 避免键盘遮挡与 M3 交互组件 state layer）
            Text(
                text = stringResource(
                    if (catalogExpanded) R.string.api_config_model_catalog_collapse
                    else R.string.api_config_model_catalog_expand
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = AppColors.colorScheme.primary,
                modifier = Modifier
                    .pressable(onClick = { catalogExpanded = !catalogExpanded }, debounce = false)
                    .padding(vertical = 8.dp),
            )
            if (catalogExpanded) {
                if (platformModels.isEmpty()) {
                    Text(
                        text = stringResource(R.string.api_config_model_catalog_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textTertiary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                            .clip(RoundedCornerShape(AppDimens.radiusMd))
                            .background(AppColors.colorScheme.surfaceVariant)
                            .padding(horizontal = AppDimens.spaceMd),
                    ) {
                        platformModels.forEach { item ->
                            val modelId = item.modelId.orEmpty()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressable(onClick = {
                                        if (modelId.isNotBlank()) {
                                            model = modelId
                                            settingViewModel.clearFieldError(ApiConfigField.MODEL)
                                        }
                                        catalogExpanded = false
                                    })
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AppColors.colorScheme.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = modelId,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppColors.colorScheme.textTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = AppColors.colorScheme.textTertiary,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))

            // API Key（密码框可切换明文；编辑态占位引导重输——服务端三字段必填，端上简化为必填）
            ApiConfigTextField(
                label = stringResource(R.string.api_config_key_label),
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    settingViewModel.clearFieldError(ApiConfigField.API_KEY)
                },
                placeholder = stringResource(
                    if (config != null) R.string.api_config_key_hint_edit
                    else R.string.api_config_key_hint_new
                ),
                isError = fieldError?.field == ApiConfigField.API_KEY,
                errorMessage = fieldError?.takeIf { it.field == ApiConfigField.API_KEY }
                    ?.let { stringResource(it.messageRes) },
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    // §3.7 尾缀一律图标化（Visibility/VisibilityOff），热区 44dp+
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .pressable(onClick = { showKey = !showKey }, debounce = false),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(
                                if (showKey) R.string.api_config_key_hide else R.string.api_config_key_show
                            ),
                            modifier = Modifier.size(20.dp),
                            tint = AppColors.colorScheme.textSecondary,
                        )
                    }
                },
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))

            // 测试连接（POST test，不保存；失败后端回 200 信封 data=false，端上转失败文案）
            AppButton(
                text = stringResource(R.string.api_config_test),
                onClick = {
                    settingViewModel.testApiConnection(baseUrl, model, apiKey)
                },
                variant = AppButtonVariant.SECONDARY,
                loading = testState is RequestState.Loading,
            )
            // 测试即时反馈（成功/失败文案行，成功给触感见上方 LaunchedEffect）
            if (testState is RequestState.Success || testState is RequestState.Error) {
                val result = testState
                Text(
                    text = when (result) {
                        is RequestState.Error -> result.getMessage(context)
                        else -> stringResource(R.string.api_config_test_ok)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result is RequestState.Error) {
                        AppColors.colorScheme.error
                    } else {
                        AppColors.colorScheme.success
                    },
                    modifier = Modifier.padding(top = AppDimens.spaceXs),
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.spaceMd))

            // 服务端拒绝 / 网络异常反馈（保存或删除）：inline 展示后端原因文案
            val saveError = saveState as? RequestState.Error
            val deleteError = deleteState as? RequestState.Error
            val submitErrorText = saveError?.getMessage(context)
                ?: deleteError?.getMessage(context)
            if (submitErrorText != null) {
                Text(
                    text = submitErrorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            }

            // 保存（PUT：服务端真实试呼，失败信封文案经 ResponseInterceptor 解析）
            AppButton(
                text = stringResource(R.string.api_config_save),
                onClick = { settingViewModel.saveApiConfig(baseUrl, model, apiKey) },
                loading = saveState is RequestState.Loading,
            )

            // 删除配置（已配置时）：二次确认 → DELETE，删除后入口行回「未配置」态
            if (config != null) {
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                AppButton(
                    text = stringResource(R.string.api_config_delete),
                    onClick = { showDeleteConfirm = true },
                    variant = AppButtonVariant.TEXT,
                    contentColor = AppColors.colorScheme.dangerInk,
                    loading = deleteState is RequestState.Loading,
                )
            }

            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(AppDimens.spaceLg)
            )
        }
    }

    // 删除二次确认（危险语义 isDanger → dangerInk 确认键，与登出/解绑同款）
    if (showDeleteConfirm) {
        AppConfirmDialog(
            title = stringResource(R.string.api_config_confirm_delete_title),
            message = stringResource(R.string.api_config_confirm_delete_message),
            confirmText = stringResource(R.string.api_config_delete),
            isDanger = true,
            onConfirm = {
                showDeleteConfirm = false
                settingViewModel.deleteApiConfig()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/**
 * BYOK 表单输入框 — AppInputDialog（common/ui）同款 M3 OutlinedTextField 画法：
 * 16sp 正文（§3.7 输入高频读写场景禁 14sp）、inputRadius 14、
 * focused primary / unfocused outline / error 描边；错误文案 13sp error 仅提交时展示。
 */
@Composable
private fun ApiConfigTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean,
    errorMessage: String?,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        placeholder = {
            Text(
                text = placeholder,
                // §3.7：占位符字号与正文一致（16sp）
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.colorScheme.textSecondary,
            )
        },
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = AppColors.colorScheme.textPrimary,
        ),
        singleLine = true,
        isError = isError,
        supportingText = if (isError && errorMessage != null) {
            {
                Text(
                    text = errorMessage,
                    // §3.7：错误态 13sp
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.colorScheme.error,
                )
            }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(AppDimens.inputRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.colorScheme.primary,
            unfocusedBorderColor = AppColors.colorScheme.outline,
            errorBorderColor = AppColors.colorScheme.error,
        ),
    )
}

/** 「已验证」徽标 — primarySoft 底 + onPrimarySoft 字的胶囊小标（radiusFull）；入口行尾注共用 */
@Composable
internal fun VerifiedBadge() {
    Text(
        text = stringResource(R.string.api_config_verified),
        style = MaterialTheme.typography.labelSmall,
        color = AppColors.colorScheme.onPrimarySoft,
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimens.radiusFull))
            .background(AppColors.colorScheme.primarySoft)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

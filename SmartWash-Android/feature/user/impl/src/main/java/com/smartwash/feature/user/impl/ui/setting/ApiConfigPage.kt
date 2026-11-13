package com.smartwash.feature.user.impl.ui.setting

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.user.impl.R
import com.smartwash.feature.user.impl.network.vo.div.PlatformVendorGroupVo

/**
 * 「解读模型」配置编辑页（issue #79，设置页观象台分组入口；2026-11-11 双模式契约）。
 *
 * 由 ApiConfigSheet 弹层改为独立页面（2026-11-12）：表单含模式切换 + 双联动下拉 + key 输入 +
 * 测试/保存/删除，内容长度超出弹层合理承载范围，半屏态在小屏拥挤；与设置页其他编辑入口
 * （修改密码 / 权限管理）统一走独立页面。观象台 428 拦截直达本页。
 *
 * 表单双模式二选一：
 * - 目录模式（默认）：GET /models 一次返回「厂商 → 模型」目录树，两个联动下拉选择——
 *   第一个下拉选厂商，第二个下拉联动列该厂商的模型条目点选回填 modelConfigId，
 *   接入点/模型名由服务端从目录取（不显示 baseUrl 输入）；
 * - 自定义模式：baseUrl + 模型名 + apiKey 三输入（OpenAI 兼容接入点）。
 * apiKey 两模式均必填（编辑态恒不回显，占位引导重输）；「测试连接」走 POST test 即时反馈，
 * 「保存」走 PUT（服务端真实试呼，失败回业务失败信封，文案经 ResponseInterceptor
 * 解析为用户可读 message）；已配置时提供「删除配置」二次确认（AppConfirmDialog）。
 *
 * 本页使用独立的 [SettingViewModel] 实例（绑定本页 NavBackStackEntry），保存/删除成功后
 * [NavController.navigateUp] 返回；设置页在回到前台时重新拉取配置刷新行内回显。
 *
 * 隐私红线：明文 API Key 只存在于输入框内存态（组合内 remember），不落 DataStore、
 * 不进日志；列表回显一律掩码。
 *
 * 动效：仅按钮 loading spinner（ButtonSpinner 内置 reduced motion 降级）；
 * 下拉为 M3 内建（箭头展开旋转），无新增自定义动效；无新增无限动画。
 */
@Composable
fun ApiConfigPage(
    navController: NavController,
    settingViewModel: SettingViewModel = hiltViewModel(),
) {
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
    var mode by remember { mutableStateOf(ApiConfigMode.CATALOG) }
    var selectedVendorIndex by remember { mutableStateOf<Int?>(null) }
    var selectedModelConfigId by remember { mutableStateOf<Long?>(null) }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 首次配置回填标记：回填只发生在首次拉取完成且用户尚未被覆盖输入之前
    var formInitialized by remember { mutableStateOf(false) }

    // 目录树已由服务端按厂商分组；端上只按选中厂商本地过滤（模型下拉无二次请求）。
    // 选中标识用列表索引（显示名可能同名，索引稳定唯一）；厂商显示名 vendor 优先、provider 兜底
    val vendorFallback = stringResource(R.string.other)
    val selectedGroup = remember(platformModels, selectedVendorIndex) {
        platformModels.getOrNull(selectedVendorIndex ?: -1)
    }
    val selectedEntryGroup = remember(platformModels, selectedModelConfigId) {
        platformModels.firstOrNull { group ->
            group.models.any { entry -> entry.id == selectedModelConfigId }
        }
    }
    val selectedEntry = selectedEntryGroup?.models?.firstOrNull { it.id == selectedModelConfigId }

    // 进入页面即拉取最新配置与平台模型目录（行内数据可能滞后）
    LaunchedEffect(Unit) {
        settingViewModel.refreshApiConfig()
        settingViewModel.loadPlatformModels()
    }
    // 平台总开关关闭（divUseEnabled=0）兜底拦截（2026-11-07 双轴审查 B2）：设置页行内置灰
    // 依赖已加载的 apiConfig，首拉失败/未返回时页面仍可编辑——拉到 divUseEnabled=0
    // 即返回设置页（设置页行已置灰），不再展示编辑表单
    LaunchedEffect(config) {
        if (config?.divUseEnabled == 0) navController.navigateUp()
    }

    // 编辑态预填：GET 返回后按配置回显模式与字段；apiKey 始终留空（占位「重新输入 API Key」）
    LaunchedEffect(config, configState) {
        if (!formInitialized && (config != null || configState is RequestState.Success)) {
            formInitialized = true
            config?.let { cfg ->
                mode = if (cfg.modelConfigId != null) ApiConfigMode.CATALOG else ApiConfigMode.CUSTOM
                selectedModelConfigId = cfg.modelConfigId
                baseUrl = cfg.customBaseUrl.orEmpty()
                model = cfg.customModel.orEmpty()
            }
        }
    }
    // 编辑态回填补位：目录模式下按 modelConfigId 在目录树中定位所属厂商（平台目录异步到达后补位，
    // 不覆盖用户后续手动选择）；两个下拉框经 selectedGroup/selectedEntry 直接显示已选名称
    LaunchedEffect(platformModels, selectedModelConfigId) {
        if (selectedVendorIndex == null && selectedModelConfigId != null) {
            selectedVendorIndex = platformModels
                .indexOfFirst { group -> group.models.any { it.id == selectedModelConfigId } }
                .takeIf { it >= 0 }
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
            navController.navigateUp()
        }
        // Error 不 Toast：服务端原因文案在表单反馈区 inline 展示（保存按钮旁，语境更强）
    }
    LaunchedEffect(deleteState) {
        val state = deleteState
        if (state is RequestState.Success) {
            Toast.makeText(
                context, context.getString(R.string.api_config_delete_success), Toast.LENGTH_SHORT
            ).show()
            navController.navigateUp()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
            // 键盘弹出时内容上收，CTA 可滚动到视口内
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            PageHeader(
                title = stringResource(R.string.api_config_title),
                onBack = { navController.navigateUp() },
            )

            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.pagePadding),
            ) {
                // 说明文案：与「隐私与免责声明」口径一致（issue #79 第 3 点；零兜底表述）
                Text(
                    text = stringResource(R.string.api_config_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceMd))

                // 已配置摘要（仅掩码回显，帮助理解为何编辑需重输 key）：
                // 目录模式「vendor · modelName」，自定义模式 customModel + customBaseUrl
                config?.let { cfg ->
                    val isCatalog = cfg.modelConfigId != null
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
                                    text = if (isCatalog) {
                                        listOfNotNull(cfg.vendor, cfg.modelName)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · ")
                                    } else {
                                        cfg.customModel.orEmpty()
                                    },
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
                            if (!isCatalog && !cfg.customBaseUrl.isNullOrBlank()) {
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

                // 选择模式（2026-11-11 双模式契约）：目录模式默认；切换清两模式不兼容的字段报错
                Text(
                    text = stringResource(R.string.api_config_mode_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.colorScheme.textSecondary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(
                        selected = mode == ApiConfigMode.CATALOG,
                        label = stringResource(R.string.api_config_mode_catalog),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (mode != ApiConfigMode.CATALOG) {
                            mode = ApiConfigMode.CATALOG
                            // 切换模式清空已选状态（两模式字段集不同，旧选择不再适用）
                            selectedVendorIndex = null
                            selectedModelConfigId = null
                            settingViewModel.clearFieldErrors()
                        }
                    }
                    ModeChip(
                        selected = mode == ApiConfigMode.CUSTOM,
                        label = stringResource(R.string.api_config_mode_custom),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (mode != ApiConfigMode.CUSTOM) {
                            mode = ApiConfigMode.CUSTOM
                            // 切换模式清空已选状态（两模式字段集不同，旧选择不再适用）
                            selectedVendorIndex = null
                            selectedModelConfigId = null
                            settingViewModel.clearFieldErrors()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(AppDimens.spaceMd))

                if (mode == ApiConfigMode.CATALOG) {
                    // 目录模式双下拉联动（用户指定交互形态）：第一框选厂商 → 第二框联动列该厂商的模型；
                    // 目录树由服务端一次返回，第二框仅本地过滤，无二次请求；接入点由服务端从目录取
                    if (platformModels.isEmpty()) {
                        Text(
                            text = stringResource(R.string.api_config_model_catalog_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textTertiary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        val modelFieldError = fieldError?.field == ApiConfigField.MODEL_CONFIG
                        // 下拉框一「厂商」：条目 = 全部厂商（displayVendor），副文本 baseUrl
                        val vendorOptions = remember(platformModels, vendorFallback) {
                            platformModels.mapIndexed { index, group ->
                                ApiConfigDropdownOption(
                                    key = index,
                                    title = group.displayVendor(vendorFallback),
                                    subtitle = group.baseUrl?.takeIf { it.isNotBlank() },
                                )
                            }
                        }
                        ApiConfigDropdownField(
                            label = stringResource(R.string.api_config_catalog_vendor_label),
                            value = selectedGroup?.displayVendor(vendorFallback).orEmpty(),
                            placeholder = stringResource(R.string.api_config_catalog_vendor_placeholder),
                            enabled = true,
                            isError = modelFieldError,
                            options = vendorOptions,
                            selectedKey = selectedVendorIndex,
                            onSelect = { option ->
                                val index = option.key as Int
                                // 换厂商即清空旧模型选择（模型归属旧厂商）
                                if (selectedVendorIndex != index) {
                                    selectedModelConfigId = null
                                }
                                selectedVendorIndex = index
                                settingViewModel.clearFieldError(ApiConfigField.MODEL_CONFIG)
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // 下拉框二「模型」：未选厂商时 disabled 占位引导；选中后条目 = 该厂商 models
                        val modelOptions = remember(platformModels, selectedVendorIndex) {
                            selectedGroup?.models?.map { entry ->
                                ApiConfigDropdownOption(
                                    key = entry.id,
                                    title = entry.name.orEmpty(),
                                    subtitle = entry.modelId,
                                )
                            }.orEmpty()
                        }
                        ApiConfigDropdownField(
                            label = stringResource(R.string.api_config_catalog_model_label),
                            value = selectedEntry?.name.orEmpty(),
                            placeholder = if (selectedGroup == null) {
                                stringResource(R.string.api_config_catalog_model_disabled_hint)
                            } else {
                                stringResource(R.string.api_config_catalog_model_placeholder)
                            },
                            enabled = selectedGroup != null,
                            isError = modelFieldError,
                            options = modelOptions,
                            selectedKey = selectedModelConfigId,
                            onSelect = { option ->
                                selectedModelConfigId = option.key as Long
                                settingViewModel.clearFieldError(ApiConfigField.MODEL_CONFIG)
                            },
                        )
                        // 已选回显（点选即时反馈；未选时留空由提交校验给「请选择解读模型」）
                        selectedEntry?.let { entry ->
                            Text(
                                text = stringResource(
                                    R.string.api_config_selected_format,
                                    selectedEntryGroup?.displayVendor(vendorFallback) ?: vendorFallback,
                                    entry.name.orEmpty(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.colorScheme.primary,
                                modifier = Modifier.padding(top = AppDimens.spaceXs),
                            )
                        }
                    }
                    // 目录模式字段级错误（未选中条目）
                    if (fieldError?.field == ApiConfigField.MODEL_CONFIG) {
                        Text(
                            text = stringResource(R.string.api_config_error_model_config_empty),
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.colorScheme.error,
                            modifier = Modifier.padding(top = AppDimens.spaceXs),
                        )
                    }
                } else {
                    // 自定义模式：接入点地址（服务端校验 https 且非内网；端上先拦 https 前缀给即时文案）
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

                    // 模型名称（自定义接入不限平台目录，独立填写）
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
                }
                Spacer(modifier = Modifier.height(AppDimens.spaceMd))

                // API Key（两模式均必填；密码框可切换明文；编辑态占位引导重输）
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

                // 测试连接（POST test，不保存；双模式同请求体；失败后端回 200 信封 data=false，端上转失败文案）
                AppButton(
                    text = stringResource(R.string.api_config_test),
                    onClick = {
                        settingViewModel.testApiConnection(mode, selectedModelConfigId, baseUrl, model, apiKey)
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

                // 保存（PUT 双模式：服务端真实试呼，失败信封文案经 ResponseInterceptor 解析）
                AppButton(
                    text = stringResource(R.string.api_config_save),
                    onClick = {
                        settingViewModel.saveApiConfig(mode, selectedModelConfigId, baseUrl, model, apiKey)
                    },
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
 * 模式切换胶囊（目录 / 自定义）：selected=primarySoft 底 + primary 描边与文字，
 * 未选=surfaceVariant 底 + outline 描边；pressable 自绘（§7 禁 M3 交互组件 state layer）。
 */
@Composable
private fun ModeChip(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(AppDimens.radiusMd)
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) AppColors.colorScheme.primarySoft else AppColors.colorScheme.surfaceVariant)
            .border(
                BorderStroke(1.dp, if (selected) AppColors.colorScheme.primary else AppColors.colorScheme.outline),
                shape,
            )
            .pressable(onClick = onClick, debounce = false),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) AppColors.colorScheme.primary else AppColors.colorScheme.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

/** 目录模式下拉条目（厂商/模型共用）：主文本 + 可空副文本；key 即选中标识（厂商=索引、模型=id） */
private data class ApiConfigDropdownOption(
    val key: Any,
    val title: String,
    val subtitle: String? = null,
)

/**
 * 目录模式联动下拉框 — M3 ExposedDropdownMenuBox：readOnly [OutlinedTextField] 作锚点
 * （样式对齐 [ApiConfigTextField]：inputRadius 圆角、focused primary / unfocused outline /
 * error 描边、16sp 正文），disabled 时描边/文字/占位与常规态一致仅不可展开；
 * 菜单条目自绘 Row + pressable（§7 禁 M3 交互组件 state layer），选中项 primarySoft 底 +
 * 尾部对勾（复用原列表画法）；菜单高度 260dp 内滚动（厂商 11 个、单厂商模型最多 13 个）。
 * 展开箭头用 M3 内建 TrailingIcon（自带展开旋转，无新增自定义动效）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiConfigDropdownField(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    isError: Boolean,
    options: List<ApiConfigDropdownOption>,
    selectedKey: Any?,
    onSelect: (ApiConfigDropdownOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(text = label) },
            placeholder = {
                Text(
                    text = placeholder,
                    // §3.7：占位符字号与正文一致（16sp）
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.colorScheme.textSecondary,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = AppColors.colorScheme.textPrimary,
            ),
            singleLine = true,
            isError = isError,
            shape = RoundedCornerShape(AppDimens.inputRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.colorScheme.primary,
                unfocusedBorderColor = AppColors.colorScheme.outline,
                disabledBorderColor = AppColors.colorScheme.outline,
                errorBorderColor = AppColors.colorScheme.error,
                disabledTextColor = AppColors.colorScheme.textPrimary,
                disabledLabelColor = AppColors.colorScheme.textSecondary,
                disabledPlaceholderColor = AppColors.colorScheme.textSecondary,
            ),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .exposedDropdownSize(matchTextFieldWidth = true)
                .heightIn(max = 260.dp),
        ) {
            options.forEachIndexed { index, option ->
                val selected = option.key == selectedKey
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppDimens.radiusSmall))
                        .background(
                            if (selected) AppColors.colorScheme.primarySoft
                            else Color.Transparent
                        )
                        .pressable(
                            onClick = {
                                expanded = false
                                onSelect(option)
                            },
                            debounce = false,
                        )
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.colorScheme.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        option.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.colorScheme.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = AppColors.colorScheme.primary,
                        )
                    }
                }
                if (index < options.lastIndex) {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
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

/**
 * 厂商显示名：vendor 优先、provider 兜底、再降「其他」。
 * 目录模式厂商下拉框条目与已选回显共用，保证同一分组键唯一稳定。
 */
private fun PlatformVendorGroupVo.displayVendor(fallback: String): String =
    vendor?.takeIf { it.isNotBlank() } ?: provider?.takeIf { it.isNotBlank() } ?: fallback

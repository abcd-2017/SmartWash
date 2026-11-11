package com.smartwash.feature.toolbox.ui.delivery

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonBlock
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.generateQrCodeBitmap
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.logic.DeliveryRules
import com.smartwash.feature.toolbox.network.DeliverySessionVo
import androidx.compose.foundation.layout.Arrangement
import com.smartwash.feature.toolbox.ui.ToolboxSelectableChip
import com.smartwash.feature.toolbox.ui.copyToClipboard
import com.smartwash.feature.toolbox.ui.ToolboxTagChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.Crossfade

/**
 * 设备投递发起页：表单态（有效期预设 + 可选进入密码）→ 会话态
 * （投递码大字锚点 + enterUrl 二维码（Dispatchers.Default 生成，不卡主线程）+
 * 剩余倒计时 + 手动关闭）；倒计时归零提示已失效并可重新发起。
 * 入口由宿主自行分配（本模块不加导航入口，路由见 DeliveryGraph）。
 */
@Composable
fun DeliveryCreatePage(
    navController: NavHostController,
    viewModel: DeliveryCreateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val form by viewModel.form.collectAsState()
    val createState by viewModel.createState.collectAsState()
    val passwordError by viewModel.passwordError.collectAsState()
    val session by viewModel.session.collectAsState()
    val closeState by viewModel.closeState.collectAsState()

    // 手动关闭：成功（VM 已回表单态）提示；失败提示并复位
    LaunchedEffect(closeState) {
        when (val state = closeState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.toolbox_delivery_toast_close_success), Toast.LENGTH_SHORT).show()
                viewModel.resetCloseState()
            }
            is RequestState.Error -> {
                // 错误类提示用 LONG（≈3.5s，规范 §8 Toast 3-5s；成功提示保持 SHORT）
                Toast.makeText(context, state.getMessage(context), Toast.LENGTH_LONG).show()
                viewModel.resetCloseState()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
            .imePadding()
            // 规范 §4：底部固定 CTA 必须避开手势条——滚动到底时提交按钮不再贴手势条
            // （audit-07 P1-3，与 OrderDetailPage/PickupPage 底栏同款）
            .navigationBarsPadding()
    ) {
        PageHeader(
            title = stringResource(R.string.toolbox_delivery_title),
            onBack = { navController.navigateUp() },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.pagePadding)
                .padding(top = AppDimens.spaceSm, bottom = AppDimens.spaceLg)
        ) {
            val created = session
            if (created == null) {
                DeliveryCreateForm(
                    form = form,
                    createState = createState,
                    passwordError = passwordError,
                    onDurationChange = viewModel::updateDuration,
                    onPasswordChange = viewModel::updatePassword,
                    onSubmit = viewModel::createSession,
                )
            } else {
                CreatedSessionCard(
                    session = created,
                    closeLoading = closeState is RequestState.Loading,
                    onOpenSession = { navController.navigateToDeliverySession(created.code) },
                    onClose = viewModel::closeSession,
                    onCopyLink = {
                        created.enterUrl?.let { url ->
                            copyToClipboard(context, url)
                            Toast.makeText(context, context.getString(R.string.toolbox_delivery_toast_link_copied), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRecreate = viewModel::resetToForm,
                )
            }
        }
    }
}

// ---------- 表单态 ----------

@Composable
private fun DeliveryCreateForm(
    form: DeliveryCreateViewModel.FormState,
    createState: RequestState,
    passwordError: Boolean,
    onDurationChange: (Int) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }

    GroupCard {
        // ---------- 有效期 ----------
        FormLabel(stringResource(R.string.toolbox_delivery_duration_label))
        // 横向可滚动：窄屏（≈360dp）下 chips 超宽时不挤压末位 chip——固定 Row 会把
        // 「2 小时」压到文字竖排折行；配合 chip 文本 maxLines=1 任何宽度都单行完整显示
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
        ) {
            DeliveryCreateViewModel.DURATION_PRESETS.forEach { minutes ->
                ToolboxSelectableChip(
                    text = durationChipText(minutes),
                    selected = form.durationMinutes == minutes,
                    onClick = { onDurationChange(minutes) },
                )
            }
        }
        Text(
            text = stringResource(R.string.toolbox_delivery_duration_desc, form.durationMinutes),
            style = MaterialTheme.typography.labelSmall,
            // 可读说明文字走 textSecondary（audit-07 P0-4：textTertiary 白卡 2.26:1 违反 §10）
            color = AppColors.colorScheme.textSecondary,
            modifier = Modifier.padding(top = AppDimens.spaceXs),
        )

        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

        // ---------- 进入密码（可选） ----------
        FormLabel(stringResource(R.string.toolbox_delivery_password_label))
        OutlinedTextField(
            value = form.password,
            onValueChange = onPasswordChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.toolbox_delivery_password_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.colorScheme.textSecondary,
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            // P1-4：密码端侧校验错误挂到字段本身（isError 红描边 §3.1），错误文案显示在字段
            // 正下方（§7）。不用 supportingText：它与固定 48dp 行高（§3.7）冲突（文本会画到
            // 输入框外与下方内容重叠），错误行复用下方 helper 槽位；网络类错误仍走表单底行
            isError = passwordError,
            trailingIcon = {
                // §3.7：尾缀可见性切换图标化，48dp 热区，禁止文字替代
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .pressable(onClick = { showPassword = !showPassword }, debounce = false),
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(
                        targetState = showPassword,
                        // 常态走 micro 档 enterTween（audit-07 P2-5：CROSSFADE_ALT_MS 语义是
                        // reduced motion 降级专用）；reduced motion 经 motionSpec 自动落 180ms
                        animationSpec = motionSpec(enterTween(DURATION_MICRO_MS)),
                        label = "deliveryPasswordVisibility",
                    ) { visible ->
                        // 无障碍：图标动作必须有语义描述，随可见态切换（§3.7 尾缀图标化）
                        Icon(
                            imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(
                                if (visible) {
                                    R.string.toolbox_delivery_password_hide
                                } else {
                                    R.string.toolbox_delivery_password_show
                                }
                            ),
                            modifier = Modifier.size(20.dp),
                            tint = AppColors.colorScheme.textSecondary,
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(AppDimens.inputRadius),
            colors = tokenTextFieldColors(),
        )
        Text(
            text = stringResource(
                if (passwordError) {
                    R.string.toolbox_delivery_error_invalid_password
                } else {
                    R.string.toolbox_delivery_password_helper
                }
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (passwordError) AppColors.colorScheme.error else AppColors.colorScheme.textTertiary,
            modifier = Modifier.padding(top = AppDimens.spaceXs),
        )

        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

        // ---------- 隐私明示（契约要求：服务器中转可见明文 + 取走即焚） ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppDimens.radiusMd))
                .background(AppColors.colorScheme.surfaceVariant)
                .padding(AppDimens.spaceMd)
        ) {
            Text(
                text = stringResource(R.string.toolbox_delivery_privacy_notice),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary,
            )
        }
    }

    // ---------- 提交错误（仅提交后展示，error 档） ----------
    val errorMessage = (createState as? RequestState.Error)?.getMessage(LocalContext.current)
    if (errorMessage != null) {
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.error,
            modifier = Modifier.padding(top = AppDimens.spaceSm),
        )
    }

    Spacer(modifier = Modifier.height(AppDimens.spaceLg))
    AppButton(
        text = stringResource(R.string.toolbox_delivery_submit_create),
        onClick = onSubmit,
        loading = createState is RequestState.Loading,
    )
}

// ---------- 会话态 ----------

/**
 * 已创建会话卡：投递码数据大字（本页唯一锚点，tnum）+ 二维码（enterUrl 本地生成）+
 * 剩余倒计时（≤60s 切紧迫色）+ 复制链接/进入会话/关闭会话；过期切换为已失效 + 重新发起。
 */
@Composable
private fun CreatedSessionCard(
    session: DeliverySessionVo,
    closeLoading: Boolean,
    onOpenSession: () -> Unit,
    onClose: () -> Unit,
    onCopyLink: () -> Unit,
    onRecreate: () -> Unit,
) {
    val remaining = rememberRemainingSeconds(session.expireAt)
    val expired = remaining != null && remaining <= 0L

    GroupCard {
        Text(
            text = stringResource(R.string.toolbox_delivery_code_label),
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colorScheme.textSecondary,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
        Text(
            text = session.code,
            style = AppTextStyles.DataLarge,
            color = AppColors.colorScheme.textPrimary,
        )

        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        // 二维码为次级信息，尺寸收敛到 160dp，避免与上方 36sp 投递码争抢本页唯一锚点（规范 §1.2）
        QrImage(enterUrl = session.enterUrl)

        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        Text(
            text = session.enterUrl.orEmpty(),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = AppColors.colorScheme.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (expired) {
                Text(
                    text = stringResource(R.string.toolbox_delivery_session_expired),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.colorScheme.dangerInk,
                )
            } else {
                DeliveryCountdownText(
                    expireAt = session.expireAt,
                    style = MaterialTheme.typography.bodyLarge,
                    // 常态色不用 ongoingInk（白卡 2.76:1 违反 §10 红线，audit-07 P0-1）
                    color = AppColors.colorScheme.onWarningContainer,
                )
            }
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            if (session.hasPassword) {
                ToolboxTagChip(
                    text = stringResource(R.string.toolbox_delivery_has_password),
                    containerColor = AppColors.colorScheme.iconContainerTeal,
                    // 12sp 文字不用图标前景令牌（#3F9D78 on #F0FBF4 仅 3.14:1，audit-07 P0-3）
                    contentColor = AppColors.colorScheme.textGreenOnTint,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (session.clickCount > 0) {
                Text(
                    text = stringResource(R.string.toolbox_delivery_clicks_format, session.clickCount),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = AppColors.colorScheme.textSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.spaceLg))
        if (expired) {
            AppButton(
                text = stringResource(R.string.toolbox_delivery_action_recreate),
                onClick = onRecreate,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)) {
                AppButton(
                    text = stringResource(R.string.toolbox_delivery_action_copy_link),
                    onClick = onCopyLink,
                    variant = AppButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    text = stringResource(R.string.toolbox_delivery_action_open_session),
                    onClick = onOpenSession,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            AppButton(
                text = stringResource(R.string.toolbox_delivery_action_close),
                onClick = onClose,
                variant = AppButtonVariant.SECONDARY,
                contentColor = AppColors.colorScheme.dangerInk,
                enabled = !closeLoading,
                loading = closeLoading,
            )
        }
    }
}

/** 二维码：enterUrl 经 generateQrCodeBitmap 在 Default 线程生成（不占主线程，ShortCodeDetailPage 同款模式） */
@Composable
private fun QrImage(enterUrl: String?) {
    val qrBitmap by produceState<Bitmap?>(initialValue = null, key1 = enterUrl) {
        value = withContext(Dispatchers.Default) {
            enterUrl?.takeIf { it.isNotBlank() }?.let { generateQrCodeBitmap(it, 512) }
        }
    }
    val bitmap = qrBitmap
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            SkeletonBlock(
                modifier = Modifier.size(160.dp),
                shape = RoundedCornerShape(AppDimens.radiusMd),
            )
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.toolbox_delivery_qr_desc),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(AppDimens.radiusMd))
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun durationChipText(minutes: Int): String = when (minutes) {
    60 -> stringResource(R.string.toolbox_delivery_duration_60m)
    120 -> stringResource(R.string.toolbox_delivery_duration_120m)
    else -> stringResource(R.string.toolbox_delivery_duration_minutes_format, minutes)
}

/** 表单字段标签（13→12sp captionStrong 档，规范 §3.4 副文案层级） */
@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = AppColors.colorScheme.textSecondary,
        modifier = Modifier.padding(bottom = AppDimens.spaceXs),
    )
}

/** 输入框令牌配色（规范 §3.7：inputRadius 14dp + surfaceVariant 底 + outline 描边） */
@Composable
private fun tokenTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.colorScheme.primary,
    unfocusedBorderColor = AppColors.colorScheme.outline,
    errorBorderColor = AppColors.colorScheme.error,
    focusedContainerColor = AppColors.colorScheme.surfaceVariant,
    unfocusedContainerColor = AppColors.colorScheme.surfaceVariant,
)

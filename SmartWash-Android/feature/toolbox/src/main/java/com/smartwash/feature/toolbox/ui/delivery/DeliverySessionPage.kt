package com.smartwash.feature.toolbox.ui.delivery

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonList
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.logic.DeliveryRules
import com.smartwash.feature.toolbox.network.DeliveryItemVo
import com.smartwash.feature.toolbox.network.DeliverySessionVo
import com.smartwash.feature.toolbox.ui.ToolboxTagChip
import com.smartwash.feature.toolbox.ui.copyToClipboard

/**
 * 设备投递会话页（双向）：会话信息条（投递码/进入地址/手动关闭）+ 双向条目流
 * （方向角标、kind 图标、取走状态，已焚毁条目灰态占位）+ 发送区（选图片/选文件/文本）。
 * 条目流由 ViewModel 2-3s 轮询驱动；点条目出预览/取走弹窗（见 [DeliveryPreviewDialog]）。
 */
@Composable
fun DeliverySessionPage(
    navController: NavHostController,
    viewModel: DeliverySessionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsState()
    val items by viewModel.items.collectAsState()
    val loadState by viewModel.loadState.collectAsState()
    val pollFailed by viewModel.pollFailed.collectAsState()
    val sessionGone by viewModel.sessionGone.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val sendState by viewModel.sendState.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val previewAction by viewModel.previewAction.collectAsState()
    val closeState by viewModel.closeState.collectAsState()
    val toast by viewModel.toast.collectAsState()

    var showCloseConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.start() }

    // 一次性提示（后端原文优先）
    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it.message ?: context.getString(it.resId), Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    // 会话失效（过期/关闭/占用达上限）：提示后立即退出
    LaunchedEffect(sessionGone) {
        sessionGone?.let {
            // 失效类提示用 LONG（≈3.5s，规范 §8 Toast 3-5s；audit-07 P2-1）
            Toast.makeText(context, it.message ?: context.getString(it.resId), Toast.LENGTH_LONG).show()
            viewModel.consumeSessionGone()
            navController.popBackStack()
        }
    }

    // 关闭会话：成功提示并退出；失败提示后复位
    LaunchedEffect(closeState) {
        when (val state = closeState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.toolbox_delivery_toast_close_success), Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
            is RequestState.Error -> {
                // 错误类提示用 LONG（≈3.5s，规范 §8 Toast 3-5s；成功提示保持 SHORT）
                Toast.makeText(context, state.getMessage(context), Toast.LENGTH_LONG).show()
                viewModel.resetCloseState()
            }
            else -> {}
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::publishImage)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::publishPickedFile)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
            .imePadding()
    ) {
        PageHeader(
            title = stringResource(R.string.toolbox_delivery_session_title),
            onBack = { navController.navigateUp() },
            actions = {
                DeliveryCountdownText(
                    expireAt = session?.expireAt,
                    style = MaterialTheme.typography.bodyMedium,
                    // 常态色不用 ongoingInk（页面底 2.54:1 / 白卡 2.76:1 违反 §10 红线，audit-07 P0-1）
                    color = AppColors.colorScheme.onWarningContainer,
                    modifier = Modifier.padding(horizontal = AppDimens.spaceXs),
                )
            },
        )

        SessionStrip(
            session = session,
            onCopyCode = { code ->
                copyToClipboard(context, code)
                Toast.makeText(context, context.getString(R.string.toolbox_delivery_toast_code_copied), Toast.LENGTH_SHORT).show()
            },
            onClose = { showCloseConfirm = true },
        )

        // 轮询失败横幅（列表非空时）：说明发生了什么 + 自动重试语义（规范 §3.6）
        if (pollFailed && items.isNotEmpty()) {
            Text(
                text = stringResource(R.string.toolbox_delivery_poll_error),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.colorScheme.dangerInk,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceXxs),
            )
        }

        val listModifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        when {
            loadState is RequestState.Loading && items.isEmpty() -> {
                SkeletonList(modifier = listModifier, rows = 4)
            }
            loadState is RequestState.Error && items.isEmpty() -> {
                // 首刷失败：说明 + 重试（轮询在后台自愈，重试可立即补拉）
                EmptyState(
                    icon = Icons.Rounded.Link,
                    message = (loadState as RequestState.Error).getMessage(context),
                    modifier = listModifier,
                    action = {
                        AppButton(
                            small = true,
                            text = stringResource(R.string.toolbox_delivery_action_retry),
                            onClick = viewModel::refreshNow,
                        )
                    },
                )
            }
            items.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.Devices,
                    message = stringResource(R.string.toolbox_delivery_empty_message),
                    modifier = listModifier,
                )
            }
            else -> {
                DeliveryItemList(
                    items = items,
                    onItem = viewModel::openItem,
                    modifier = listModifier,
                )
            }
        }

        DeliverySendArea(
            draft = draft,
            sendState = sendState,
            onDraftChange = viewModel::updateDraft,
            onSend = viewModel::sendText,
            onPickImage = { imagePicker.launch("image/*") },
            onPickFile = { filePicker.launch("*/*") },
        )
    }

    preview?.let { state ->
        DeliveryPreviewDialog(
            state = state,
            actionLoading = previewAction is RequestState.Loading,
            onDismiss = viewModel::dismissPreview,
            onConfirmTakeImage = viewModel::takeImageForPreview,
            onConfirmTakeFile = viewModel::takeFileAndSave,
            onCopyText = viewModel::copyTextAndTake,
            onSaveImage = viewModel::saveImageToGallery,
        )
    }

    if (showCloseConfirm) {
        AppConfirmDialog(
            title = stringResource(R.string.toolbox_delivery_confirm_close_title),
            message = stringResource(R.string.toolbox_delivery_confirm_close_message),
            isDanger = true,
            confirmText = stringResource(R.string.toolbox_delivery_action_close),
            onConfirm = {
                showCloseConfirm = false
                viewModel.closeSession()
            },
            onDismiss = { showCloseConfirm = false },
        )
    }
}

/** 会话信息条：投递码（点按复制）+ 进入地址 + 手动关闭（危险文字动作） */
@Composable
private fun SessionStrip(
    session: DeliverySessionVo?,
    onCopyCode: (String) -> Unit,
    onClose: () -> Unit,
) {
    if (session == null) return
    // 卡片阴影与描边统一走 GroupCard（softCardShadow），与创建页同一画法（规范 §2.4 / §3.1）
    GroupCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceXs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(
                icon = Icons.Rounded.Devices,
                size = 36.dp,
                iconSize = 20.dp,
                containerColor = AppColors.colorScheme.iconContainerBlue,
                iconTint = AppColors.colorScheme.iconForegroundBlue,
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .pressable(onClick = { onCopyCode(session.code) }, debounce = false),
            ) {
                Text(
                    text = session.code,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = AppColors.colorScheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = session.enterUrl.orEmpty().ifEmpty {
                        stringResource(R.string.toolbox_delivery_url_fallback)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    // URL 是二维码扫不出来时的唯一手动入口，可读内容不用 textTertiary（audit-07 P0-4）
                    color = AppColors.colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 关闭会话 — 危险动作文字按钮（dangerInk；44dp 最小热区，规范 §3.3）
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .pressable(onClick = onClose)
                    .padding(horizontal = AppDimens.spaceSm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.toolbox_delivery_action_close_session),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.colorScheme.dangerInk,
                )
            }
        }
    }
}

/** 双向条目流：单卡容器 + 行间发丝线（规范 §1.3 只在卡内），正序展示，新条目且已到底部时跟随滚动 */
@Composable
private fun DeliveryItemList(
    items: List<DeliveryItemVo>,
    onItem: (DeliveryItemVo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(items.size) {
        // 只在用户已阅读到底部时跟随新条目，不打断回看
        if (items.isNotEmpty() && !listState.canScrollForward) {
            if (reduceMotion) listState.scrollToItem(items.lastIndex) else listState.animateScrollToItem(items.lastIndex)
        }
    }
    // 卡片阴影与描边统一走 GroupCard（softCardShadow），与创建页同一画法（规范 §2.4 / §3.1）；
    // 列表行卡按 GroupCard 约定只给水平 20dp（行自带高度铺满，纵向不再加内边距）
    GroupCard(
        modifier = modifier
            .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceXs),
        contentPadding = PaddingValues(horizontal = AppDimens.cardPadding),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items.size, key = { items[it].id }) { index ->
                val item = items[index]
                DeliveryItemRow(item = item, onClick = { onItem(item) })
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = AppColors.colorScheme.hairline,
                        // 44dp = 32dp 图标容器 + 12dp 间距（无独立令牌，保留字面量并注释）
                        modifier = Modifier.padding(start = 44.dp),
                    )
                }
            }
        }
    }
}

/** 条目行：kind 图标容器（蓝·物流语义）+ 标题 + 时间·大小 + 方向/取走角标；已焚毁整行灰态不可点 */
@Composable
private fun DeliveryItemRow(
    item: DeliveryItemVo,
    onClick: () -> Unit,
) {
    val kind = DeliveryRules.ItemKind.fromCode(item.kind)
    val direction = DeliveryRules.Direction.fromCode(item.direction)
    val taken = item.taken != 0
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppDimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBox(
                icon = kindIcon(kind),
                size = 32.dp,
                iconSize = 20.dp,
                containerColor = if (taken) {
                    AppColors.colorScheme.surfaceVariant
                } else {
                    AppColors.colorScheme.iconContainerBlue
                },
                iconTint = if (taken) {
                    AppColors.colorScheme.textTertiary
                } else {
                    AppColors.colorScheme.iconForegroundBlue
                },
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title ?: stringResource(kindNameRes(kind)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (taken) AppColors.colorScheme.textTertiary else AppColors.colorScheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                val time = DeliveryRules.formatItemTime(item.createdAt)
                Text(
                    text = item.objectSize?.let { size ->
                        stringResource(
                            R.string.toolbox_delivery_meta_format,
                            time,
                            DeliveryRules.formatFileSize(size),
                        )
                    } ?: time,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    // 时间·大小为可读元信息，不用 textTertiary（audit-07 P0-4）
                    color = AppColors.colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(AppDimens.spaceXs))
            Column(horizontalAlignment = Alignment.End) {
                // 方向角标：发出 = 中性；收到（对端发来，待我取走）= 蓝·物流语义强调
                ToolboxTagChip(
                    text = stringResource(
                        if (direction == DeliveryRules.Direction.PHONE_TO_COMPUTER) {
                            R.string.toolbox_delivery_direction_out
                        } else {
                            R.string.toolbox_delivery_direction_in
                        }
                    ),
                    containerColor = if (direction == DeliveryRules.Direction.COMPUTER_TO_PHONE && !taken) {
                        AppColors.colorScheme.iconContainerBlue
                    } else {
                        AppColors.colorScheme.surfaceVariant
                    },
                    contentColor = if (direction == DeliveryRules.Direction.COMPUTER_TO_PHONE && !taken) {
                        AppColors.colorScheme.iconForegroundBlue
                    } else {
                        AppColors.colorScheme.textSecondary
                    },
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                // 取走状态：待取走 = 橙·待办语义；已取走 = 灰（焚毁占位）
                ToolboxTagChip(
                    text = stringResource(if (taken) R.string.toolbox_delivery_state_taken else R.string.toolbox_delivery_state_pending),
                    containerColor = if (taken) {
                        AppColors.colorScheme.surfaceVariant
                    } else {
                        AppColors.colorScheme.warningContainer
                    },
                    // 功能色浅底 + 成对文字色（规范 §5）：#D98A26 on warningContainer 仅 2.51:1
                    // 违反 §10 红线（audit-07 P0-2），改用成对的 onWarningContainer（两主题 ≥6.4:1）
                    contentColor = if (taken) {
                        AppColors.colorScheme.textTertiary
                    } else {
                        AppColors.colorScheme.onWarningContainer
                    },
                )
            }
        }
    }
    if (taken) {
        // 焚毁占位：灰态（半透明弱化）且不可再点——内容已焚毁，仅保留对话流占位
        Box(modifier = Modifier.alpha(0.45f)) { rowContent() }
    } else {
        Box(modifier = Modifier.pressable(onClick = onClick, debounce = false)) { rowContent() }
    }
}

/** 发送区：选图片/选文件图标入口 + 文本输入 + 发送（浮层与滚动内容间 0.5dp 分隔线，规范 §2.4 豁免） */
@Composable
private fun DeliverySendArea(
    draft: String,
    sendState: RequestState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.colorScheme.surface,
        shadowElevation = AppElevation.level1,
    ) {
        // 规范 §4：底部固定输入条必须避开手势条（audit-07 P1-3，与 OrderDetailPage/PickupPage
        // 底栏同款：Surface 延伸到导航栏后方，内容整体上移）；键盘弹出时 ime 已在根布局消费，
        // 导航栏内边距按轴钳制不会叠加
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceSm)
            ) {
                when (val state = sendState) {
                    is RequestState.Loading -> {
                        Text(
                            text = stringResource(R.string.toolbox_delivery_sending),
                            style = MaterialTheme.typography.labelSmall,
                            // 传输状态为可读内容，不用 textTertiary（audit-07 P0-4）
                            color = AppColors.colorScheme.textSecondary,
                        )
                    }
                    is RequestState.Error -> {
                        // 发布错误内联展示（规范 §3.7：仅提交后展示，13→12sp error 档）
                        Text(
                            text = state.getMessage(context),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.error,
                            modifier = Modifier.padding(bottom = AppDimens.spaceXxs),
                        )
                    }
                    else -> {}
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                // 选图片 / 选文件 — 44dp 热区图标入口（次级 36 容器内 20 图标，蓝·物流语义）
                PickerIconButton(
                    icon = Icons.Rounded.AddPhotoAlternate,
                    contentDescription = stringResource(R.string.toolbox_delivery_pick_image),
                    onClick = onPickImage,
                )
                // 相邻触控目标间距 ≥8dp（规范 §3，audit-07 P1-1：4dp 拇指误触率高）
                Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                PickerIconButton(
                    icon = Icons.Rounded.AttachFile,
                    contentDescription = stringResource(R.string.toolbox_delivery_pick_file),
                    onClick = onPickFile,
                )
                Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.toolbox_delivery_send_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.colorScheme.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    minLines = 1,
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(AppDimens.inputRadius),
                    colors = tokenTextFieldColors(),
                )
                Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                // 发送 — 44dp 圆形主色按钮（热区 ≥44dp，规范 §3.3；draft 空则禁用降透明度 0.5）
                val canSend = draft.isNotBlank() && sendState !is RequestState.Loading
                Box(
                    modifier = Modifier
                        .padding(bottom = AppDimens.spaceXxs)
                        .size(44.dp)
                        .alpha(if (canSend) 1f else 0.5f)
                        .then(if (canSend) Modifier.pressable(onClick = onSend) else Modifier)
                        .background(AppColors.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.toolbox_delivery_action_send),
                        tint = AppColors.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** 44dp 图标选取入口（次级图标容器规格 36/20，外扩至 44 热区） */
@Composable
private fun PickerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .pressable(onClick = onClick, debounce = false),
        contentAlignment = Alignment.Center,
    ) {
        IconBox(
            icon = icon,
            size = 36.dp,
            iconSize = 20.dp,
            containerColor = AppColors.colorScheme.iconContainerBlue,
            iconTint = AppColors.colorScheme.iconForegroundBlue,
        )
    }
}

@Composable
private fun kindIcon(kind: DeliveryRules.ItemKind): ImageVector = when (kind) {
    DeliveryRules.ItemKind.TEXT -> Icons.Rounded.Notes
    DeliveryRules.ItemKind.IMAGE -> Icons.Rounded.Image
    DeliveryRules.ItemKind.FILE -> Icons.Rounded.InsertDriveFile
}

/** 输入框令牌配色（规范 §3.7：inputRadius 14dp + surfaceVariant 底 + outline 描边） */
@Composable
private fun tokenTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.colorScheme.primary,
    unfocusedBorderColor = AppColors.colorScheme.outline,
    focusedContainerColor = AppColors.colorScheme.surfaceVariant,
    unfocusedContainerColor = AppColors.colorScheme.surfaceVariant,
)

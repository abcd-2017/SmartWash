package com.smartwash.feature.dining.ui.order

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SmartWashAsyncImage
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.generateQrCodeBitmap
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.api.model.DiningCollaboratorVo
import com.smartwash.feature.dining.api.model.DiningInviteVo
import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import com.smartwash.feature.dining.api.model.DiningOrderStatus
import com.smartwash.feature.dining.logic.DiningOrderShareDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 订单分享视图（07 号票发起链路终点）：订单基本信息卡（标题锚点/状态/菜品数/参与人数，
 * 详情接口全量拉取——列表页进入也无 token 空窗）+ 加入二维码卡（内容 = spec 约定的
 * {origin}/dining/join/{orderId}?t={join_token}）+ 复制加入链接。
 *
 * token 仅创建链路携带（后端详情/列表接口不回传）：从列表进入时二维码区降级提示，
 * 重置凭证入口已接线（发起者限定，二次确认 → reset → 重建二维码）。二维码生成对齐取件码/短链详情先例（produceState +
 * Dispatchers.Default，生成期占位，组合期零阻塞）。
 */
@Composable
fun DiningOrderSharePage(
    navController: NavHostController,
    viewModel: DiningOrderShareViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val detailState by viewModel.detailState.collectAsState()
    val detail by viewModel.detail.collectAsState()

    // 详情加载（失败重试显式触发）
    LaunchedEffect(Unit) { viewModel.load() }

    // 详情失败反馈（Toast 收进 effect——组合期禁副作用）
    LaunchedEffect(detailState) {
        val state = detailState
        if (state is RequestState.Error) {
            Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
        }
    }

    // 重置凭证结果反馈（成功=新码已生效；失败=旧码继续可用）
    LaunchedEffect(Unit) {
        viewModel.resetState.collect { state ->
            when (state) {
                is RequestState.Success -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.dining_order_share_reset_done),
                        Toast.LENGTH_SHORT,
                    ).show()
                    viewModel.consumeResetState()
                }

                is RequestState.Error -> {
                    Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
                    viewModel.consumeResetState()
                }

                else -> Unit
            }
        }
    }

    // 定向邀请结果反馈（成功=邀请已发出；失败=后端文案）
    LaunchedEffect(Unit) {
        viewModel.inviteState.collect { state ->
            when (state) {
                is RequestState.Success -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.dining_order_invite_done),
                        Toast.LENGTH_SHORT,
                    ).show()
                    viewModel.consumeInviteState()
                }

                is RequestState.Error -> {
                    Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
                    viewModel.consumeInviteState()
                }

                else -> Unit
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.surface)
    ) {
        PageHeader(
            // 邀请与分享合一：编辑中（DRAFT）显示「邀请加入」，已结束显示「订单分享」
            title = stringResource(
                if (detail?.status == DiningOrderStatus.FINISHED ||
                    detail?.status == DiningOrderStatus.CANCELLED
                ) {
                    R.string.dining_order_share_title
                } else {
                    R.string.dining_order_invite_title
                }
            ),
            onBack = { navController.navigateUp() },
        )

        Crossfade(
            targetState = detailState,
            animationSpec = motionSpec(enterTween(DURATION_CARD_MS)),
            label = "diningOrderShareContent",
        ) { state ->
            when {
                state is RequestState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())

                state is RequestState.Error && detail == null -> EmptyState(
                    // EmptyState 只渲染滚筒圆母题（§3.2 禁裸图标着色）：icon 不渲染，仅占位
                    icon = Icons.Default.Restaurant,
                    message = state.getMessage(context),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppDimens.pagePadding),
                    action = {
                        AppButton(
                            small = true,
                            text = stringResource(R.string.dining_recipe_action_retry),
                            onClick = { viewModel.load() },
                            variant = AppButtonVariant.SECONDARY,
                        )
                    },
                )

                // 详情成功或失败但首帧降级信息在（路由 title）——信息卡 + 二维码卡
                else -> ShareContent(viewModel, detail)
            }
        }
    }
}

/** 分享内容：订单信息卡（锚点标题）+ 加入二维码卡（发起者可重置凭证）+ 复制链接 */
@Composable
private fun ShareContent(
    viewModel: DiningOrderShareViewModel,
    detail: DiningOrderShareDetail?,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val collaborators by viewModel.collaborators.collectAsState()
    val invites by viewModel.invites.collectAsState()
    val inviteState by viewModel.inviteState.collectAsState()
    // 重置凭证二次确认（不可逆动作弹窗；重置进行中不重复弹出）
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    if (showResetConfirm && viewModel.resetState.value !is RequestState.Loading) {
        AppConfirmDialog(
            title = stringResource(R.string.dining_order_share_reset_confirm_title),
            message = stringResource(R.string.dining_order_share_reset_confirm_message),
            confirmText = stringResource(R.string.dining_order_share_reset_confirm),
            onConfirm = {
                showResetConfirm = false
                viewModel.resetJoinToken()
            },
            onDismiss = { showResetConfirm = false },
        )
    }

    // 展示标题：详情成功用详情值；失败/加载中降级用路由携带标题（入口均已下发，无空窗）；
    // 草稿未命名时空串 → 占位锚点（次要色），标题行不留白只剩状态点
    val title = detail?.title?.takeIf { it.isNotBlank() } ?: viewModel.routeTitle
    // 详情未到按「邀请中」降级（本页主要由创建页邀请入口进入）
    val status = detail?.status ?: DiningOrderStatus.DRAFT
    val menuCount = detail?.menuCount ?: 0
    val memberCount = detail?.memberCount ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.pagePadding),
    ) {
        // 订单信息（视觉锚点：标题）—— 平铺无卡，对齐点单页标准
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDotSmall(status)
                Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                val untitled = title.isBlank()
                Text(
                    text = if (untitled) {
                        stringResource(R.string.dining_order_title_placeholder)
                    } else {
                        title
                    },
                    style = AppTextStyles.CardTitle,
                    color = if (untitled) AppColors.colorScheme.textTertiary else AppColors.colorScheme.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            Text(
                text = listOf(
                    stringResource(R.string.dining_order_share_menu_count_format, menuCount),
                    stringResource(R.string.dining_order_share_member_count_format, memberCount),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.textSecondary,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            OnlineMemberStack(members = detail?.members.orEmpty())
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.dining_order_share_qr_section),
                style = AppTextStyles.SectionTitle,
                color = AppColors.colorScheme.textPrimary,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            JoinQrContent(
                joinUrl = viewModel.joinUrl,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            Text(
                text = stringResource(R.string.dining_order_share_qr_hint),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.colorScheme.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            viewModel.joinUrl?.let { url ->
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                AppButton(
                    small = true,
                    text = stringResource(R.string.dining_order_share_copy_link),
                    onClick = {
                        clipboard.setText(AnnotatedString(url))
                        Toast.makeText(
                            context,
                            context.getString(R.string.dining_order_share_copy_done),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    variant = AppButtonVariant.SECONDARY,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                // 重置凭证入口（发起者限定：joinUrl 非空即 token 可得=发起者；参与者本就走降级提示）
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                AppButton(
                    small = true,
                    text = stringResource(R.string.dining_order_share_reset_action),
                    onClick = { showResetConfirm = true },
                    variant = AppButtonVariant.TEXT,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }

        // 邀请面板：仅草稿态可邀请（后端 invite 仅 DRAFT）；已结束/已取消不展示，避免无效操作
        if (status == DiningOrderStatus.DRAFT) {
            InvitePanel(
                collaborators = collaborators,
                invites = invites,
                inviteState = inviteState,
                onInvite = { viewModel.invite(listOf(it)) },
            )
        }

        // 底部避让手势条/三键导航（不硬编码高度）+ 末卡呼吸空间
        Spacer(
            modifier = Modifier.height(
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                    AppDimens.spaceLg,
            ),
        )
    }
}

/** 小状态色点 + 状态字（草稿 secondary 灰 / 已结束 success 绿 / 已取消 error 红；分享页只作信息展示） */
@Composable
private fun StatusDotSmall(status: DiningOrderStatus) {
    val (dotColor, label) = when (status) {
        DiningOrderStatus.DRAFT -> MaterialTheme.colorScheme.secondary to stringResource(R.string.dining_order_status_draft)
        DiningOrderStatus.FINISHED -> AppColors.colorScheme.success to stringResource(R.string.dining_order_status_finished)
        DiningOrderStatus.CANCELLED -> AppColors.colorScheme.error to stringResource(R.string.dining_order_status_cancelled)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(AppDimens.spaceXxs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.colorScheme.textSecondary,
        )
    }
}

/**
 * 圆形头像（优先后端下发头像直链，缺失回落手机号尾字符）+ 在线点；离线灰点，在线绿点。
 * 头像 URL 由后端 users.avatar 下发（脱敏资料），经 [SmartWashAsyncImage] 统一加载。
 */
@Composable
private fun MemberAvatar(
    label: String,
    online: Boolean,
    avatarUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(AppColors.colorScheme.primarySoft)
            .border(1.5.dp, AppColors.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val url = avatarUrl?.takeIf { it.isNotBlank() }
        if (url == null) {
            Text(
                text = label.takeLast(1).ifBlank { "友" },
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.colorScheme.onPrimarySoft,
            )
        } else {
            SmartWashAsyncImage(
                url = url,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(9.dp)
                .clip(CircleShape)
                .background(
                    if (online) AppColors.colorScheme.primary
                    else AppColors.colorScheme.textTertiary
                )
                .border(1.5.dp, AppColors.colorScheme.surface, CircleShape),
        )
    }
}

/**
 * 在线成员堆叠：最多展示 5 个头像（后绘制者覆盖前一个，形成堆叠），右侧「在线 N 人」。
 * 在线状态来自详情 members（后端 Redis presence 心跳，端上每 8s 刷新）。
 */
@Composable
private fun OnlineMemberStack(members: List<DiningOrderDetailVo.OrderMember>) {
    if (members.isEmpty()) return
    val onlineCount = members.count { it.isOnline }
    val shown = members.take(5)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.width((32 + 24 * (shown.size - 1)).dp)) {
            shown.forEachIndexed { index, member ->
                key(member.safeUserId) {
                    MemberAvatar(
                        label = member.safePhoneMasked,
                        online = member.isOnline,
                        avatarUrl = member.safeAvatar,
                        modifier = Modifier.offset(x = (index * 24).dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        Text(
            text = stringResource(R.string.dining_order_invite_online_format, onlineCount),
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.colorScheme.textSecondary,
        )
    }
}

/**
 * 邀请面板：协作过的用户（可逐个邀请）+ 本单已邀请列表（状态：已邀请 / 已加入）。
 * 邀请动作经 [onInvite] 上抛（ViewModel 写邀请记录 + 发站内信）；邀请中按钮 loading 防连点。
 */
@Composable
private fun InvitePanel(
    collaborators: List<DiningCollaboratorVo>,
    invites: List<DiningInviteVo>,
    inviteState: RequestState,
    onInvite: (Long) -> Unit,
) {
    val invitedIds = remember(invites) { invites.map { it.safeInviteeId }.toSet() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.dining_order_invite_collaborators_section),
            style = AppTextStyles.SectionTitle,
            color = AppColors.colorScheme.textPrimary,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        if (collaborators.isEmpty()) {
            Text(
                text = stringResource(R.string.dining_order_invite_empty),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.colorScheme.textTertiary,
                modifier = Modifier.padding(vertical = AppDimens.spaceXs),
            )
        } else {
            collaborators.forEach { collaborator ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppDimens.spaceXs),
                ) {
                    MemberAvatar(
                        label = collaborator.safePhoneMasked,
                        online = false,
                        avatarUrl = collaborator.safeAvatar,
                    )
                    Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = collaborator.safePhoneMasked,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.colorScheme.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (collaborator.safeLastCollaboratedAt.isNotBlank()) {
                            Text(
                                text = collaborator.safeLastCollaboratedAt.substringBefore("T"),
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.colorScheme.textTertiary,
                            )
                        }
                    }
                    if (collaborator.safeUserId in invitedIds) {
                        Text(
                            text = stringResource(R.string.dining_order_invite_status_invited),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.colorScheme.textSecondary,
                        )
                    } else {
                        AppButton(
                            small = true,
                            text = stringResource(R.string.dining_order_invite_action),
                            onClick = { onInvite(collaborator.safeUserId) },
                            variant = AppButtonVariant.SECONDARY,
                            loading = inviteState is RequestState.Loading,
                            enabled = inviteState !is RequestState.Loading,
                        )
                    }
                }
            }
        }

        if (invites.isNotEmpty()) {
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            Text(
                text = stringResource(R.string.dining_order_invite_invited_section),
                style = AppTextStyles.SectionTitle,
                color = AppColors.colorScheme.textPrimary,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            invites.forEach { invite ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppDimens.spaceXs),
                ) {
                    MemberAvatar(
                        label = invite.safeInviteePhoneMasked,
                        online = false,
                        avatarUrl = invite.safeInviteeAvatar,
                    )
                    Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                    Text(
                        text = invite.safeInviteePhoneMasked,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colorScheme.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            if (invite.isJoined) R.string.dining_order_invite_status_joined
                            else R.string.dining_order_invite_status_invited
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.colorScheme.textSecondary,
                    )
                }
            }
        }
    }
}

package com.smartwash.feature.dining.ui.order

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.produceState
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
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.generateQrCodeBitmap
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.feature.dining.R
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
 * 重置凭证入口为后续票。二维码生成对齐取件码/短链详情先例（produceState +
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        PageHeader(
            title = stringResource(R.string.dining_order_share_title),
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

/** 分享内容：订单信息卡（锚点标题）+ 加入二维码卡 + 复制链接 */
@Composable
private fun ShareContent(
    viewModel: DiningOrderShareViewModel,
    detail: DiningOrderShareDetail?,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // 展示标题：详情成功用详情值；失败/加载中降级用路由携带标题（入口均已下发，无空窗）
    val title = detail?.title?.takeIf { it.isNotBlank() } ?: viewModel.routeTitle
    val status = detail?.status ?: DiningOrderStatus.ONGOING
    val menuCount = detail?.menuCount ?: 0
    val memberCount = detail?.memberCount ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.pagePadding),
    ) {
        // 订单信息卡（视觉锚点：标题）
        GroupCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDotSmall(status)
                Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = AppColors.colorScheme.textPrimary,
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
        }
        Spacer(modifier = Modifier.height(AppDimens.cardSpacing))

        // 加入二维码卡
        GroupCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.dining_order_share_qr_section),
                style = MaterialTheme.typography.titleSmall,
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
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.spaceXl))
    }
}

/** 二维码内容区：joinUrl 可构造 → 生成/展示；不可构造 → 凭证不可得降级提示 */
@Composable
private fun JoinQrContent(
    joinUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (joinUrl == null) {
            Text(
                text = stringResource(R.string.dining_order_share_qr_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppDimens.spaceLg, horizontal = AppDimens.spaceMd),
            )
        } else {
            // 二维码异步生成（先例模式）：produceState 以 url 为 key，Default 线程生成不占主线程
            val qrBitmap by produceState<Bitmap?>(initialValue = null, key1 = joinUrl) {
                value = withContext(Dispatchers.Default) { generateQrCodeBitmap(joinUrl, 512) }
            }
            val imageBitmap = qrBitmap?.asImageBitmap()
            if (imageBitmap == null) {
                // 生成期占位（对齐取件页「加载中」盒子语义）
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(AppDimens.radiusMd))
                        .background(AppColors.colorScheme.surfaceVariant),
                )
            } else {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = stringResource(R.string.dining_order_share_qr_section),
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(AppDimens.radiusMd)),
                )
            }
        }
    }
}

/** 小状态色点 + 状态字（复用列表页语义：进行中橙 / 已结束绿；分享页只作信息展示） */
@Composable
private fun StatusDotSmall(status: DiningOrderStatus) {
    val (dotColor, label) = when (status) {
        DiningOrderStatus.ONGOING -> AppColors.colorScheme.ongoing to stringResource(R.string.dining_order_status_ongoing)
        DiningOrderStatus.FINISHED -> AppColors.colorScheme.success to stringResource(R.string.dining_order_status_finished)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.colorScheme.textSecondary,
        )
    }
}

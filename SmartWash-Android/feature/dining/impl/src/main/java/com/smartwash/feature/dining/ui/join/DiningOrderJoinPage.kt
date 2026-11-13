package com.smartwash.feature.dining.ui.join

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.api.DiningRoute
import com.smartwash.feature.dining.logic.DiningJoinEntry
import com.smartwash.feature.dining.logic.DiningJoinEntryDecision
import com.smartwash.feature.dining.logic.DiningOrderBriefUi

/**
 * 点单加入入口页（12 号票）：站内信 dining 深链 `smartwash://dining/order/{orderId}?t={token}` 的落点。
 *
 * 成员经 brief 判重后直达点单页；非成员展示标题预览 + 「加入」按钮，确认加入后再进详情。
 * 未登录由壳层深链登录守卫兜底（dining host 非公开），本页不新造会话语义。
 *
 * 布局（规范 §1 每屏一个锚点）：本页无 hero 大字，视觉锚点为标题卡片与底部主按钮；
 * 卡片统一 GroupCard 画法，加载/错误态复用共享组件。
 */
@Composable
fun DiningOrderJoinPage(
    navController: NavHostController,
    viewModel: DiningOrderJoinViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val briefState by viewModel.briefState.collectAsState()
    val brief by viewModel.brief.collectAsState()
    val joinState by viewModel.joinState.collectAsState()
    val navigateToDetail by viewModel.navigateToDetail.collectAsState()

    // 首载（返回本页经 LaunchedEffect(Unit) 不重放；失败重试显式触发）
    LaunchedEffect(Unit) { viewModel.load() }

    // 成员判重/加入成功 → 直达点单页；弹掉加入入口页（返回键不回此中间页）
    LaunchedEffect(navigateToDetail) {
        val orderId = navigateToDetail ?: return@LaunchedEffect
        if (orderId <= 0L) return@LaunchedEffect
        navController.navigate(DiningRoute.OrderDetail(orderId)) {
            popUpTo<DiningRoute.OrderJoin> { inclusive = true }
            launchSingleTop = true
        }
        viewModel.consumeNavigateToDetail()
    }

    // 加入失败 Toast（组合期禁副作用，统一收进 effect）
    LaunchedEffect(joinState) {
        val state = joinState
        if (state is RequestState.Error) {
            Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
            viewModel.consumeJoinState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background),
    ) {
        PageHeader(
            title = stringResource(R.string.dining_order_join_title),
            onBack = { navController.navigateUp() },
        )

        val currentBrief = brief
        val decision = currentBrief?.let(DiningJoinEntry::decide)
        when {
            // 非成员且进行中：标题预览 + 加入按钮
            currentBrief != null && decision == DiningJoinEntryDecision.CONFIRM_JOIN ->
                JoinConfirmContent(
                    brief = currentBrief,
                    joining = joinState is RequestState.Loading,
                    onJoin = viewModel::join,
                )

            // 非成员且已结束：不引导加入
            decision == DiningJoinEntryDecision.ORDER_FINISHED ->
                EmptyState(
                    icon = Icons.Default.Restaurant,
                    message = stringResource(R.string.dining_order_join_order_finished),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppDimens.pagePadding),
                )

            // brief 拉取失败：说明发生了什么 + 重试
            briefState is RequestState.Error -> EmptyState(
                icon = Icons.Default.Restaurant,
                message = (briefState as? RequestState.Error)?.getMessage(context)
                    ?: stringResource(R.string.dining_order_join_load_failed),
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

            // 首载/重试中（含成员判重中）
            else -> LoadingState(modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * 确认加入内容：订单标题预览（标题/发起者/菜品数）+ 主按钮「加入」。
 * 卡片居中陈列，按钮固定主色胶囊（规范 §3.3）。
 */
@Composable
private fun JoinConfirmContent(
    brief: DiningOrderBriefUi,
    joining: Boolean,
    onJoin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GroupCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = brief.title.ifBlank { stringResource(R.string.dining_scan_confirm_unknown) },
                    style = MaterialTheme.typography.titleLarge,
                    color = AppColors.colorScheme.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (brief.creatorName.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.dining_order_join_creator_format, brief.creatorName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colorScheme.textSecondary,
                        modifier = Modifier.padding(top = AppDimens.spaceXs),
                    )
                }
                Text(
                    text = stringResource(R.string.dining_order_join_recipe_count_format, brief.recipeCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colorScheme.textSecondary,
                    modifier = Modifier.padding(top = AppDimens.spaceXxs),
                )
            }
        }
        AppButton(
            text = stringResource(R.string.dining_order_join_action),
            onClick = onJoin,
            loading = joining,
            modifier = Modifier.padding(top = AppDimens.spaceLg),
        )
    }
}

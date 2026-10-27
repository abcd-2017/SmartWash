package com.smartwash.ui.page.pickup

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartwash.common.utils.pressable
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.smartwash.R
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.feature.order.api.PickupDeliveryType
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.performHaptic

/** 取件列表三态：骨架（首载）/ 空态 / 内容 —— 禁止用 itemCount==0 一刀切（首载会先闪空态） */
private enum class PickupListState { LOADING, EMPTY, CONTENT }

@Composable
fun PickupPage(
    navController: NavHostController,
    pickupViewModel: PickupViewModel = hiltViewModel(),
) {
    val orderList = pickupViewModel.pagingFlow.collectAsLazyPagingItems()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // 首载 Loading 期间 itemCount==0，若只看 itemCount 会先闪"暂无取件订单"再被整屏替换；
    // 按 refresh 显式分态（paging-compose 的初始 refresh 即 Loading，不会先落空态）
    val pickupListState = when {
        orderList.loadState.refresh is LoadState.Loading && orderList.itemCount == 0 -> PickupListState.LOADING
        orderList.loadState.refresh is LoadState.NotLoading && orderList.itemCount == 0 -> PickupListState.EMPTY
        else -> PickupListState.CONTENT
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.pickup), onBack = { navController.navigateUp() })

            // 三态统一进同一容器：进入 200ms 减速淡入、退出 140ms 加速淡出。
            // transitionSpec 不是 @Composable 上下文（不能在其中调 motionSpec()），
            // 故按规范 §8 用 LocalReduceMotion 显式分支：降级统一 180ms 纯淡入。
            val reduceMotion = LocalReduceMotion.current
            val listTransition: ContentTransform =
                if (reduceMotion) {
                    fadeIn(tween(CROSSFADE_ALT_MS)) togetherWith fadeOut(tween(CROSSFADE_ALT_MS))
                } else {
                    fadeIn(enterTween(DURATION_MICRO_MS)) togetherWith fadeOut(exitTween(140))
                }
            AnimatedContent(
                targetState = pickupListState,
                transitionSpec = { listTransition },
                label = "pickupListState",
                modifier = Modifier.weight(1f)
            ) { state ->
                when (state) {
                    PickupListState.LOADING -> LoadingState(modifier = Modifier.fillMaxSize())

                    PickupListState.EMPTY -> EmptyState(
                        icon = Icons.Default.LocalLaundryService,
                        message = stringResource(R.string.no_pickup_orders)
                    )

                    PickupListState.CONTENT -> Column(modifier = Modifier.fillMaxSize()) {
                        // 学校信息
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppDimens.pagePadding, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconBox(
                                icon = Icons.Default.School,
                                size = 32.dp,
                                iconSize = 20.dp,
                                containerColor = AppColors.colorScheme.iconContainerBlue,
                                iconTint = AppColors.colorScheme.iconForegroundBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = orderList[0]?.schoolsVo?.schoolName ?: "",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
                        ) {
                            items(orderList.itemCount) { i ->
                                orderList[i]?.let {
                                    PickupOrderCard(
                                        order = it,
                                        onClick = { navController.navigate("${OrderRoute.PickupDelivery.text}/${orderList[i]?.orderId ?: -1}/${PickupDeliveryType.PICKUP.type}") }
                                    )
                                }
                            }
                        }
                    }
                }

                PickupActionBar(
                    onContactServiceClick = {
                        // 客服功能未立项，先以 Toast 兜底（与 OrderDetailPage D-OD12 约定一致）
                        Toast.makeText(context, context.getString(R.string.contact_service_unavailable), Toast.LENGTH_SHORT).show()
                    },
                    onCopyPickupCodeClick = {
                        // 主行动取列表首单的取件码；无码时给原因提示
                        val code = orderList.peek(0)?.pickupCode?.split(":")?.getOrNull(2) ?: ""
                        if (code.isNotEmpty()) {
                            clipboardManager.setText(AnnotatedString(code))
                            Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.no_pickup_code), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } else {
                EmptyState(
                    icon = Icons.Default.LocalLaundryService,
                    message = stringResource(R.string.no_pickup_orders)
                )
            }
        }
    }
}

@Composable
private fun PickupOrderCard(
    order: OrderInfo,
    onClick: () -> Unit,
) {
    val pickupCode = order.pickupCode?.split(":")?.getOrNull(2) ?: ""
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val view = currentView()
    AppCard(onClick = onClick) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.order_no_format, order.orderNo),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = AppColors.colorScheme.textSecondary
                    )
                    Text(
                        text = stringResource(R.string.locker_label_format, "${order.lockersVo.lockerNumber}"),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = AppColors.colorScheme.textSecondary
                    )
                }
                // 金额 18/24/700 + primaryDark（画板实测 #1E8C5C·浅 / #5BC894·深）
                Text(
                    text = stringResource(R.string.currency_format, "${order.totalPrice}"),
                    style = AppTextStyles.AmountMedium.copy(fontWeight = FontWeight.Bold),
                    color = AppColors.colorScheme.primaryDark
                )
            }

            // 画板实测节奏：说明区 → 20 → 1dp hairline 分隔 → 12 → 取件码区
            Spacer(modifier = Modifier.height(AppDimens.spaceLg))
            HorizontalDivider(thickness = 1.dp, color = AppColors.colorScheme.hairline)
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.pickup_code),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
                // 取件码大字 = font.display 36/44/700 + tnum（AppTextStyles.DataLarge）
                Text(
                    text = pickupCode,
                    style = AppTextStyles.DataLarge,
                    color = AppColors.colorScheme.primaryDark
                )
            }

            if (pickupCode.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    // 复制按钮（画板实测：80×48 胶囊 radiusFull、surfaceVariant 底、文字 12/400 textPrimary）
                    Box(
                        modifier = Modifier
                            .pressable(onClick = {
                                clipboardManager.setText(AnnotatedString(pickupCode))
                                view.performHaptic(HapticEffect.SELECTION)
                                Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                            })
                            .background(AppColors.colorScheme.surfaceVariant, RoundedCornerShape(AppDimens.radiusFull))
                            .defaultMinSize(minWidth = 80.dp, minHeight = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.copy_pickup_code),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 底部操作栏（模板 E 变体）— 画板实测：无 surface 底/顶线，按钮直接落在页面底上；
 * 高 48 / radiusFull 胶囊 / 按钮间距 12 / 底部留白 16 / 文字 font.title 18/600；
 * 主按钮纯色 primary + onPrimary 双主题字；次按钮透明底 + 1dp outline 描边 + primaryDark 字
 */
@Composable
private fun PickupActionBar(
    onContactServiceClick: () -> Unit,
    onCopyPickupCodeClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceMd),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)
    ) {
        // 次按钮 — 透明底 + 1dp outline 描边（画板实测无填充）
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .pressable(onClick = onContactServiceClick, debounce = false)
                .border(1.dp, AppColors.colorScheme.outline, RoundedCornerShape(AppDimens.radiusFull)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.contact_service),
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.colorScheme.primaryDark
            )
        }
        // 主按钮 — 纯色 primary 胶囊，onPrimary 双主题成对
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .pressable(onClick = onCopyPickupCodeClick, debounce = false)
                .background(AppColors.colorScheme.primary, RoundedCornerShape(AppDimens.radiusFull)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.copy_pickup_code),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

package com.smartwash.ui.page.pickup

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartwash.common.utils.pressable
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.smartwash.R
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.common.ui.components.AppCard
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.ui.page.PageConstant
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.Background
import com.smartwash.common.ui.theme.Divider
import com.smartwash.common.ui.theme.Primary
import com.smartwash.common.ui.theme.PrimaryLight
import com.smartwash.common.ui.theme.TextSecondary
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
                                // v0.4 图标容器小档 = 32 容器 / 12 圆角 / 20 图标
                                iconSize = 20.dp,
                                containerColor = AppColors.colorScheme.iconContainerBlue,
                                iconTint = AppColors.colorScheme.iconForegroundBlue
                            )
                            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
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
                    }
                }
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
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = AppColors.colorScheme.textSecondary
                    )
                    Text(
                        text = stringResource(R.string.locker_label_format, "${order.lockersVo.lockerNumber}"),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = AppColors.colorScheme.textSecondary
                    )
                }
                Text(
                    text = stringResource(R.string.currency_format, "${order.totalPrice}"),
                    style = AppTextStyles.AmountMedium,
                    color = AppColors.colorScheme.primaryDark
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(AppColors.colorScheme.divider))
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.pickup_code),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = AppColors.colorScheme.textSecondary
                    )
                )
                Text(
                    text = pickupCode,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.colorScheme.primaryDark,
                        fontFeatureSettings = "tnum",
                        letterSpacing = 0.sp
                    )
                )
            }

            if (pickupCode.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AppColors.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .pressable(onClick = {
                                clipboardManager.setText(AnnotatedString(pickupCode))
                                view.performHaptic(HapticEffect.SELECTION)
                                Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                            })
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = stringResource(R.string.copy_pickup_code),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.colorScheme.textPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 底部操作栏 — 次按钮（联系客服）+ 主按钮（复制取件码），双 48dp 胶囊落在页面底上 */
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

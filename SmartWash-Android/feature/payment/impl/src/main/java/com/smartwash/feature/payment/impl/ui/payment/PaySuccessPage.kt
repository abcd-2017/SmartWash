package com.smartwash.feature.payment.impl.ui.payment

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.feature.payment.impl.R
import com.smartwash.feature.payment.api.PaymentRoute
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.LocalCoverArt
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.InfoRow
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.CircleIconBox
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.model.RequestState

@Composable
fun PaySuccessPage(
    navController: NavHostController,
    orderId: Long,
    paySuccessViewModel: PaySuccessViewModel = hiltViewModel(),
) {
    val getOrderDetailState by paySuccessViewModel.getOrderInfoDetail.collectAsState()
    val orderInfo by paySuccessViewModel.orderInfo.collectAsState()
    val context = LocalContext.current

    // 成功对勾入场：本页只由真实支付成功导航抵达，动画由真实事件驱动。
    // reduced motion 时由 motionSpec 降级为 180ms 纯淡入（缩放归零，规范 §8）。
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val reduceMotion = LocalReduceMotion.current
    val checkScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.6f,
        animationSpec = motionSpec(defaultSpring()),
        label = "paySuccessCheckScale"
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = motionSpec(enterTween(DURATION_MICRO_MS)),
        label = "paySuccessCheckAlpha"
    )

    if (orderId != -1L) {
        LaunchedEffect(Unit) {
            paySuccessViewModel.getOrderDetail(orderId)
        }
    }

    // 错误提示放 LaunchedEffect，禁止组合期直接弹 Toast；
    // 失败态不再立刻复位（保留 Error 供内联重试入口呈现）
    LaunchedEffect(getOrderDetailState) {
        if (getOrderDetailState is RequestState.Error) {
            Toast.makeText(
                context,
                (getOrderDetailState as RequestState.Error).getMessage(context),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        // 骨架 ↔ 内容 ↔ 失败态：交叉淡变（reduced motion 由 motionSpec 降级为 180ms）
        Crossfade(
            targetState = getOrderDetailState,
            animationSpec = motionSpec(enterTween(DURATION_MICRO_MS)),
            label = "paySuccessContent"
        ) { state ->
            when (state) {
                is RequestState.Loading, is RequestState.Idle ->
                    LoadingState(modifier = Modifier.fillMaxSize())

                is RequestState.Error -> EmptyState(
                    // DrumMark 母题为默认呈现，icon 仅占位（不新增 Icons 使用）
                    icon = Icons.Default.Check,
                    message = stringResource(R.string.pay_success_load_failed),
                    modifier = Modifier.fillMaxSize(),
                    action = {
                        AppButton(
                            small = true,
                            text = stringResource(R.string.pay_success_retry),
                            onClick = {
                                if (orderId != -1L) paySuccessViewModel.getOrderDetail(orderId)
                            },
                        )
                    },
                )

                is RequestState.Success -> Column(modifier = Modifier.fillMaxSize()) {
                    PageHeader(title = stringResource(R.string.pay_success), onBack = { navController.navigateUp() })

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            // scroll 在前、padding 在后：卡外阴影需要视口内的 24dp 容身空间
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = AppDimens.pagePadding),
                    ) {
                        // 庆祝卡（DESIGN-SPEC v0.4 §3.1 唯一卡片画法：surface 白底 + 1px outline 描边 +
                        // softCardShadow = CSS 0 2px 12px rgba(20,40,30,.05)；上 40 / 下 44 内边距；
                        // 成功圆 44 = primaryLight 容器 + 勾 28 = primary；结果大标题 28/700，绿字取
                        // primaryDark（28sp 结果大标题，≥18sp 绿字豁免，2026-11-06 评审 R17 保留）
                        Surface(
                            modifier = Modifier.fillMaxWidth().softCardShadow(),
                            shape = RoundedCornerShape(AppDimens.radiusLg),
                            color = AppColors.colorScheme.surface,
                            border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                        ) {
                            Box {
                                // 庆祝背景装饰层：同心涟漪+水花（PS 自绘透明 PNG），铺满卡底、内容置顶
                                Image(
                                    painter = painterResource(LocalCoverArt.paySuccessCelebrationBg),
                                    contentDescription = null,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.FillBounds
                                )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp, bottom = 44.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 对勾：同一 entered state 驱动 scale + alpha；reduced motion 时缩放归零只留淡入
                                Box(
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = if (reduceMotion) 1f else checkScale
                                        scaleY = if (reduceMotion) 1f else checkScale
                                        alpha = checkAlpha
                                    }
                                ) {
                                    CircleIconBox(
                                        icon = Icons.Default.Check,
                                        size = 44.dp,
                                        iconSize = 24.dp
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = stringResource(R.string.pay_success),
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = AppColors.colorScheme.primaryDark
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.thanks_for_using),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppColors.colorScheme.textSecondary
                                )
                            }
                            }
                        }

                        Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

                        // 订单信息卡（DESIGN-SPEC v0.4 §3.1：surface 白底 + 1px outline 描边 +
                        // level1(1dp) 阴影；标题 18/600，卡内边距横 20 / 纵 24，行距 16，行字 12/400）
                        Text(
                            text = stringResource(R.string.order_info),
                            style = AppTextStyles.SectionTitle,
                            color = AppColors.colorScheme.textPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().softCardShadow(),
                            shape = RoundedCornerShape(AppDimens.radiusLg),
                            color = AppColors.colorScheme.surface,
                            border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppDimens.cardPadding, vertical = AppDimens.spaceXl),
                                verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMd)
                            ) {
                                // 金额/单号/时间启用等宽数字（规范 §5 tnum），值色 textPrimary（画板实测非品牌绿）
                                val infoValueStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontFeatureSettings = "tnum"
                                )
                                InfoRow(
                                    stringResource(R.string.order_number),
                                    orderInfo?.orderNo ?: "",
                                    valueStyle = infoValueStyle,
                                    rowHeight = 16.dp
                                )
                                InfoRow(
                                    stringResource(R.string.payment_amount),
                                    // 金额两位小数：Float.toString() 会把 29.9f 直出成「¥29.9」，与他页不一致
                                    stringResource(R.string.currency_format, String.format("%.2f", orderInfo?.payPrice ?: 0f)),
                                    valueStyle = infoValueStyle,
                                    rowHeight = 16.dp
                                )
                                InfoRow(
                                    stringResource(R.string.order_time_display),
                                    orderInfo?.createdAt ?: "",
                                    valueStyle = infoValueStyle,
                                    rowHeight = 16.dp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // 寄存柜提示卡（DESIGN-SPEC v0.4 §3.1：surface 白底 + 1px outline 描边 +
                        // level1(1dp) 阴影；图标盒 36/r12 + 首行随图标盒垂直居中；行间距 8，
                        // 说明行 12/400 textSecondary）
                        Text(
                            text = stringResource(R.string.locker_info_section),
                            style = AppTextStyles.SectionTitle,
                            color = AppColors.colorScheme.textPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().softCardShadow(),
                            shape = RoundedCornerShape(AppDimens.radiusLg),
                            color = AppColors.colorScheme.surface,
                            border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    start = AppDimens.cardPadding,
                                    end = AppDimens.cardPadding,
                                    top = AppDimens.spaceXl,
                                    // P14 裁定：卡底距随画板改 24（原 16 系旧版画板读数）
                                    bottom = AppDimens.spaceXl
                                ),
                                verticalAlignment = Alignment.Top
                            ) {
                                IconBox(
                                    icon = Icons.Default.LocalShipping,
                                    size = 36.dp,
                                    iconSize = 20.dp
                                )
                                Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                                // 下沉 8 使首行与 36 图标盒中心对齐（画板实测图标盒中心 = 首行中心）；
                                // 行文字画板实测 14/400（bodyMedium 档），非 16sp；
                                // P14 裁定：删除卡内首行「寄存柜信息」（与卡外区块标题完全重复，
                                // 画板卡内仅 3 行）
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(AppDimens.spaceXs)
                                ) {
                                    Text(
                                        stringResource(R.string.locker_deposited_format, orderInfo?.lockersVo?.lockerNumber?.toString() ?: "-"),
                                        // 柜号是编号类数字，开 tnum（规范 §2.2）
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                                    )
                                    Text(
                                        // 柜号空值兜底显示 "-"，不再误显示 "-1柜"（对齐鸿蒙 PaymentSuccess.ets 的空值语义）
                                        stringResource(R.string.go_to_locker_format, orderInfo?.lockersVo?.lockerNumber?.toString() ?: "-"),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                                    )
                                    Text(
                                        stringResource(R.string.locker_process_tip),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppColors.colorScheme.textSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
                    }

                    // 底部操作区（P14 裁定补底条：surface 令牌底 + 顶部 0.5dp outline 分隔线，
                    // 与支付/充值页底部操作条同画法；主按钮 48 高 / 胶囊 / 纯色 primary / 字 18·600 onPrimary；
                    // 下附「返回首页」文字链 12/400 textSecondary）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.colorScheme.surface)
                    ) {
                        HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.outline)
                        Column(modifier = Modifier.navigationBarsPadding()) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = AppDimens.pagePadding)
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .pressable(onClick = {
                                        navController.navigate(
                                            OrderRoute.PickupDelivery(orderInfo?.orderId ?: -1, OrderRoute.PICKUP_TYPE_DELIVERY)
                                        ) {
                                            popUpTo<PaymentRoute.PaySuccess> { inclusive = true }
                                        }
                                    })
                                    .background(AppColors.colorScheme.primary, RoundedCornerShape(AppDimens.radiusFull)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.go_ship_now),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            // 触控 ≥48dp 红线：文字链垂直内边距 16（画板视觉间距 8）
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressable(onClick = { navController.popBackStack<ShellRoute.Home>(inclusive = false) })
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.back_home),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.colorScheme.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

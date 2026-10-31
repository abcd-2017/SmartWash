package com.smartwash.ui.page.index

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.LocalOffer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.feature.order.api.model.OrderBrief
import com.smartwash.common.ui.components.AppInfoDialog
import com.smartwash.common.ui.components.DrumMark
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.ui.page.PageConstant
import com.smartwash.feature.coupon.api.CouponRoute
import com.smartwash.feature.laundry.api.LaundryRoute
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.feature.payment.api.PaymentRoute
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.feature.order.api.model.OrderStatus
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.rememberDebouncedClick
import com.smartwash.common.utils.selectedColor
import androidx.compose.ui.text.style.TextAlign

@Composable
fun IndexPage(
    isActive: Boolean,
    navController: NavHostController,
    indexViewModel: IndexViewModel = hiltViewModel(),
) {
    var showAlertDialog by remember { mutableStateOf(false) }
    val userInfoStatus by indexViewModel.userInfoStatus.collectAsState()
    val userInfo by indexViewModel.userInfo.collectAsState()
    val orderList by indexViewModel.orderList.collectAsState()

    // 每次 settled 落到本 tab 时刷新（原为监听 tab 栈 currentBackStackEntry，等价迁移）
    LaunchedEffect(isActive) {
        if (isActive) indexViewModel.getInfoData()
    }

    val errorContext = LocalContext.current
    LaunchedEffect(userInfoStatus) {
        if (userInfoStatus is RequestState.Error) {
            Toast.makeText(
                errorContext,
                (userInfoStatus as RequestState.Error).getMessage(errorContext),
                Toast.LENGTH_SHORT
            ).show()
            indexViewModel.resetState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        // 骨架屏只在「首载且无任何数据」时出现：刷新（VM 置 Loading）时 userInfo 已有值，
        // 保持已展示内容不被整屏骨架替换；骨架 ↔ 内容交叉淡变（reduced motion 自动 180ms）
        val showSkeleton =
            (userInfoStatus is RequestState.Idle || userInfoStatus is RequestState.Loading) && userInfo == null
        Crossfade(
            targetState = showSkeleton,
            animationSpec = motionSpec(enterTween(DURATION_CARD_MS)),
            label = "indexSkeleton"
        ) { skeleton ->
            if (skeleton) {
                LoadingState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                item {
                    StatusHeroCard(
                        orderList = orderList,
                        onOrderClick = { orderId ->
                            navController.navigate("${OrderRoute.OrderDetail.text}/$orderId")
                        }
                    )
                }

                item {
                    AccountDataCard(
                        balance = userInfo?.balance ?: 0f,
                        couponCount = userInfo?.couponCount ?: 0,
                        orderCount = userInfo?.orderCount ?: 0,
                        onRechargeClick = {
                            navController.navigate(PaymentRoute.Recharge.text)
                        }
                    )
                }

                item {
                    // 设计稿实测：账户卡底（y=328）→「洗衣服务」标题（y=344）间距 16
                    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                    Text(
                        text = stringResource(R.string.laundry_service),
                        style = AppTextStyles.SectionTitle,
                        color = AppColors.colorScheme.textPrimary,
                        modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ServiceGrid(
                        onBookingClick = {
                            navController.navigate(LaundryRoute.Laundry.text) {
                                launchSingleTop = true
                            }
                        },
                        onPickupClick = {
                            navController.navigate(PageConstant.Pickup.text)
                        },
                        onRechargeClick = {
                            navController.navigate(PaymentRoute.Recharge.text)
                        },
                        onCouponClick = {
                            navController.navigate(CouponRoute.Coupon.text)
                        }
                    )
                }

                item {
                    // 设计稿实测：服务标签底（y=416）→「进行中订单」标题（y=440）间距 24
                    Spacer(modifier = Modifier.height(AppDimens.spaceXl))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppDimens.pagePadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.in_progress_orders),
                            style = AppTextStyles.SectionTitle,
                            color = AppColors.colorScheme.textPrimary
                        )
                        // 行内「查看全部」— 16sp SemiBold primaryDark（green.700，规范 §10 绿色文字红线），
                        // 无 ripple，48dp 热区（D-I7）；文字右缘对齐页面 24 边距（设计稿实测 y=444 右缘 336）
                        Text(
                            text = stringResource(R.string.view_all),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = AppColors.colorScheme.primaryDark,
                            modifier = Modifier
                                .pressable(
                                    onClick = rememberDebouncedClick {
                                        navController.navigate("${OrderRoute.Order.text}/${OrderStatus.WASHING.status}")
                                    },
                                    debounce = false,
                                )
                                .padding(start = 12.dp, top = 16.dp, bottom = 16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 进行中订单列表 ↔ 空态：合并为单 item 交叉淡变，不再整块硬切
                item {
                    Crossfade(
                        targetState = orderList.isNotEmpty(),
                        animationSpec = motionSpec(enterTween(DURATION_CARD_MS)),
                        label = "indexOrderList"
                    ) { hasOrders ->
                        if (hasOrders) {
                            OrderListCard(
                                orderList = orderList,
                                onOrderClick = { orderId ->
                                    navController.navigate("${OrderRoute.OrderDetail.text}/$orderId")
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.no_ongoing_orders),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppColors.colorScheme.textSecondary
                                )
                            }
                        }
                    }
                }

                item {
                    // 设计稿实测：订单卡底（y=656）→ 服务须知（y=700）间距 44
                    Spacer(modifier = Modifier.height(44.dp))
                    ServiceTips()
                    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                }
                }
            }
        }
    }

    if (showAlertDialog) {
        AppInfoDialog(
            message = stringResource(R.string.change_school_contact_service),
            onDismiss = { showAlertDialog = false }
        )
    }
}

/**
 * 订单状态 → hero 滚筒进度（0..1），8 态映射自旧版恢复。
 */
private fun orderProgress(status: String): Float = when (status) {
    "0" -> 0.10f
    "1" -> 0.25f
    "2" -> 0.35f
    "3" -> 0.55f
    "4" -> 0.70f
    "5" -> 0.85f
    "6" -> 0.95f
    "7" -> 1.00f
    else -> 0f
}

/**
 * 首页 Hero 卡 — 品牌渐变底 + 真实订单状态驱动（规范 v3 §3.1）。
 * 显示当前订单状态、进度条，全页唯一视觉锚点。
 */
@Composable
private fun StatusHeroCard(
    orderList: List<OrderBrief>,
    onOrderClick: (Long) -> Unit,
) {
    val currentOrder = orderList.firstOrNull()
    val orderStatus = currentOrder?.let { OrderStatus.fromStatus(it.status) }

    // 进度弧跟随真实订单状态平滑推进（reduced motion 由 motionSpec 降级为 180ms 无回弹过渡）
    val heroProgress = currentOrder?.let { orderProgress(it.status) } ?: 0f
    val animatedHeroProgress by animateFloatAsState(
        targetValue = heroProgress,
        animationSpec = motionSpec(defaultSpring()),
        label = "heroProgress"
    )

    // 品牌渐变 135°：浅色 #2D9B6A→#1E8C5C、深色 #2D9B6A→#5BC894（规范 §1.3 gradient.hero，设计稿首页画板实测）
    val gradient = Brush.linearGradient(
        colors = listOf(AppColors.colorScheme.heroGradientStart, AppColors.colorScheme.heroGradientEnd),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding)
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(AppDimens.radiusXl))
            .background(gradient)
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 滚筒圆 — 规范 §5 母题：同心环 + 进度弧 + 单条水波（D-I1）
                DrumMark(
                    size = 64.dp,
                    progress = if (currentOrder != null) animatedHeroProgress else null,
                    tint = Color.White,
                    trackTint = Color.White.copy(alpha = 0.32f),
                    innerRingTint = Color.White.copy(alpha = 0.45f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = orderStatus?.descriptionRes?.let { stringResource(it) }
                            ?: stringResource(R.string.home_hero_no_order),
                        style = AppTextStyles.StatusLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (currentOrder != null) {
                        Text(
                            text = stringResource(R.string.home_hero_order_no, currentOrder.orderNo),
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.home_hero_no_order_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.spaceXl))

            // 步骤标签 — stringResource 资源化，进度点行与标签行共用（D-I2 硬编码清理）
            val steps = listOf(
                stringResource(R.string.step_received),
                stringResource(R.string.step_washing),
                stringResource(R.string.step_dried),
                stringResource(R.string.step_pickup)
            )

            // 进度条
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentStep = when (orderStatus) {
                    OrderStatus.WASHING -> 1
                    OrderStatus.DRIED -> 2
                    OrderStatus.READY_FOR_PICKUP -> 3
                    else -> 0
                }
                steps.forEachIndexed { index, _ ->
                    val isActive = index <= currentStep
                    // 设计稿首页画板实测：hero 渐变底上激活点/已走过连线=白（#FFFFFF），
                    // 未完成点/未走过连线=outline；primary 绿在绿渐变上不可见，禁用
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                selectedColor(if (isActive) Color.White else AppColors.colorScheme.outline)
                            )
                    )
                    if (index < steps.lastIndex) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(
                                    selectedColor(
                                        if (index < currentStep) Color.White else AppColors.colorScheme.outline
                                    )
                                )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                steps.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        textAlign = when (index) {
                            0 -> androidx.compose.ui.text.style.TextAlign.Start
                            3 -> androidx.compose.ui.text.style.TextAlign.End
                            else -> androidx.compose.ui.text.style.TextAlign.Center
                        }
                    )
                }
            }
        }
    }
}

/**
 * 账户数据卡 — 白底 + 1px 描边 + 轻阴影（规范 v0.4 §3.1 / §2.4）。
 * 余额是主角（大字），优惠券和累计订单是小字。
 */
@Composable
private fun AccountDataCard(
    balance: Float,
    couponCount: Int,
    orderCount: Int,
    onRechargeClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding)
            .padding(top = 16.dp),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        // 规范 §2.4：卡片 = 1px 描边 + 0 2px 12px rgba(20,40,30,.05) → level1
        shadowElevation = AppElevation.level1,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.colorScheme.outline)
    ) {
        // 设计稿首页画板实测：余额通栏在上（上16 + label 16 + 4 + 大字 44），下方券/单
        // 两半格各居中、中间一根 1×40 发丝竖线——不是「余额 | 券 | 单」三列
        Column {
            // 余额（主角）——按可用宽度分档缩字号，窄屏不折行不溢出
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable(onClick = rememberDebouncedClick(onClick = onRechargeClick), debounce = false)
                    .padding(top = 16.dp, start = 20.dp, end = 20.dp)
            ) {
                val availableWidth = maxWidth
                Column {
                    Text(
                        text = stringResource(R.string.account_balance_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.currency_format, String.format("%.2f", balance)),
                        // 数据大字恒为 36sp（v0.4 §2.2 硬规则：一屏一个锚点级大字）。
                        // 窄屏降级只允许落到阶梯内的档（headline 28 / titleLarge 22），不造夹档。
                        style = when {
                            availableWidth >= 160.dp -> AppTextStyles.DataLarge
                            availableWidth >= 130.dp -> AppTextStyles.Headline
                            else -> MaterialTheme.typography.titleLarge
                        },
                        color = AppColors.colorScheme.primaryDark,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                AccountStatCell(
                    value = "$couponCount",
                    label = stringResource(R.string.home_available_coupons)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(AppColors.colorScheme.hairline)
                )
                AccountStatCell(
                    value = "$orderCount",
                    label = stringResource(R.string.home_total_orders)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** 账户卡底部半格：数值 18/600 tnum + 标签 12/400，水平居中；行盒紧邻无间距（设计稿实测 40 高） */
@Composable
private fun RowScope.AccountStatCell(value: String, label: String) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = AppTextStyles.AmountMedium,
            color = AppColors.colorScheme.textPrimary,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.textSecondary,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun ServiceGrid(
    onBookingClick: () -> Unit,
    onPickupClick: () -> Unit,
    onRechargeClick: () -> Unit,
    onCouponClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding)
            // 设计稿实测：服务图标盒首列 x=44（页边距 24 + 卡内缩进 20），整组内收 20
            .padding(horizontal = AppDimens.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalLaundryService,
            label = stringResource(R.string.service_booking),
            containerColor = AppColors.colorScheme.iconContainerGreen,
            iconTint = AppColors.colorScheme.iconForegroundGreen,
            onClick = onBookingClick
        )
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalMall,
            label = stringResource(R.string.service_pickup),
            containerColor = AppColors.colorScheme.iconContainerOrange,
            iconTint = AppColors.colorScheme.iconForegroundOrange,
            onClick = onPickupClick
        )
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.AccountBalanceWallet,
            label = stringResource(R.string.service_recharge),
            // 设计稿首页画板实测：充值入口为绿色系（与预约洗衣同 primaryContainer 底）
            containerColor = AppColors.colorScheme.iconContainerGreen,
            iconTint = AppColors.colorScheme.iconForegroundGreen,
            onClick = onRechargeClick
        )
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalOffer,
            label = stringResource(R.string.service_coupon),
            containerColor = AppColors.colorScheme.iconContainerBlue,
            iconTint = AppColors.colorScheme.iconForegroundBlue,
            onClick = onCouponClick
        )
    }
}

@Composable
private fun ServiceEntry(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    containerColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .pressable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconBox(
            icon = icon,
            size = 44.dp,
            iconSize = 24.dp,
            containerColor = containerColor,
            iconTint = iconTint
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.textSecondary
        )
    }
}

/**
 * 进行中订单列表 — 白底卡片 + 1px 描边 + 轻阴影（规范 v0.4 §3.1 / §2.4）。
 * 36dp 图标容器 + 等宽价格。
 */
@Composable
private fun OrderListCard(
    orderList: List<OrderBrief>,
    onOrderClick: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        // 规范 §2.4：卡片 = 1px 描边 + 0 2px 12px rgba(20,40,30,.05) → level1
        shadowElevation = AppElevation.level1,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.colorScheme.outline)
    ) {
        Column {
            orderList.forEachIndexed { index, order ->
                OrderListItem(
                    orderVo = order,
                    onClick = { onOrderClick(order.orderId) }
                )
                if (index < orderList.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(1.dp)
                            .background(AppColors.colorScheme.hairline)
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderListItem(
    orderVo: OrderBrief,
    onClick: () -> Unit,
) {
    val orderStatus = OrderStatus.fromStatus(orderVo.status)
    val statusText = orderStatus?.descriptionRes?.let { stringResource(it) } ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            // §3.4 列表行高 56dp：图标容器 36 + 上下各 10dp（原 16dp 会得 68dp）
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标容器色按状态语义（设计稿首页画板实测）：清洗中→water 水蓝（icon.md 20），其余→橙（进行中待办语义）
        IconBox(
            icon = Icons.Default.LocalLaundryService,
            size = 36.dp,
            iconSize = 20.dp,
            containerColor = if (orderStatus == OrderStatus.WASHING) {
                AppColors.colorScheme.iconContainerBlue
            } else {
                AppColors.colorScheme.iconContainerOrange
            },
            iconTint = if (orderStatus == OrderStatus.WASHING) {
                AppColors.colorScheme.water
            } else {
                AppColors.colorScheme.iconForegroundOrange
            }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.order_no_format, orderVo.orderNo),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.textSecondary
            )
        }
        Text(
            text = stringResource(R.string.currency_format, orderVo.payPrice.toString()),
            style = AppTextStyles.AmountMedium,
            color = AppColors.colorScheme.primaryDark
        )
    }
}

@Composable
private fun ServiceTips() {
    // 服务须知 — 居中纯文字（D-I9）
    Text(
        text = stringResource(R.string.service_tips),
        style = MaterialTheme.typography.bodySmall,
        color = AppColors.colorScheme.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding),
        textAlign = TextAlign.Center
    )
}

package com.smartwash.ui.page.detail

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.common.ui.components.AppInfoDialog
import com.smartwash.common.ui.components.InfoRow
import com.smartwash.common.ui.components.InfoSection
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.feature.order.api.model.OrderStatus
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable

@Composable
fun OrderDetailPage(
    navController: NavHostController,
    orderId: Long,
    orderDetailViewModel: OrderDetailViewModel = hiltViewModel(),
) {
    val getOrderDetailState by orderDetailViewModel.getOrderInfoDetail.collectAsState()
    val orderInfo by orderDetailViewModel.orderInfo.collectAsState()

    if (orderId != -1L) {
        LaunchedEffect(Unit) {
            orderDetailViewModel.getOrderDetail(orderId)
        }
    }

    // 错误提示放 LaunchedEffect，禁止组合期直接弹 Toast
    val context = LocalContext.current
    LaunchedEffect(getOrderDetailState) {
        if (getOrderDetailState is RequestState.Error) {
            Toast.makeText(
                context,
                (getOrderDetailState as RequestState.Error).getMessage(context),
                Toast.LENGTH_SHORT
            ).show()
            orderDetailViewModel.resetState()
        }
    }

    // 取件码弹窗开关（D-OD1，恢复旧版寄存柜取件码功能链）
    var showPickupCodeDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        if (getOrderDetailState is RequestState.Loading) {
            LoadingState(modifier = Modifier.fillMaxSize())
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 底部固定操作条让位 88dp（D-OD5），防止内容被遮挡
                .padding(bottom = 88.dp)
        ) {
            PageHeader(title = stringResource(R.string.order_detail), onBack = { navController.navigateUp() })

            Column(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                Spacer(modifier = Modifier.height(8.dp))

                // 状态区 — 无卡片：居中滚筒插画 + 状态字（设计稿屏 6，D-OD8）
                OrderStatusHeader(status = OrderStatus.fromStatus(orderInfo?.status ?: "-1"))

                // 进度时间线（规范 §3.4）
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppDimens.radiusLg),
                    color = AppColors.colorScheme.surface,
                    shadowElevation = AppElevation.level1,
                    border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.progress_timeline),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.colorScheme.textPrimary,
                            modifier = Modifier.padding(bottom = 18.dp)
                        )
                        ProgressTimeline(
                            currentStatus = OrderStatus.fromStatus(orderInfo?.status ?: "-1"),
                            // 已接单节点副行：真实下单时间 HH:mm（格式异常则不显示，D-OD2）
                            firstNodeTime = orderInfo?.createdAt?.drop(11)?.take(5)
                                ?.takeIf { it.length == 5 && it.contains(':') }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 订单信息（c3：物流/订单语义蓝图标，D-OD11）
                InfoSection(
                    title = stringResource(R.string.order_info),
                    icon = Icons.Rounded.Receipt,
                    iconContainerColor = AppColors.colorScheme.iconContainerBlue,
                    iconTint = AppColors.colorScheme.iconForegroundBlue
                ) {
                    InfoRow(
                        stringResource(R.string.order_number),
                        orderInfo?.orderNo ?: "",
                        valueStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
                    )
                    InfoRow(stringResource(R.string.package_type), orderInfo?.laundryPackageVo?.itemName ?: "")
                    // 价格行（D-OD3，恢复旧版套餐信息卡的 totalPrice 数据）
                    InfoRow(
                        stringResource(R.string.price),
                        stringResource(R.string.currency_format, (orderInfo?.totalPrice ?: 0f).toString())
                    )
                    InfoRow(
                        stringResource(R.string.actual_payment),
                        stringResource(R.string.currency_format, (orderInfo?.payPrice ?: 0f).toString()),
                        valueColor = AppColors.colorScheme.primary,
                        valueStyle = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        )
                    )
                    InfoRow(stringResource(R.string.school_name), orderInfo?.schoolsVo?.schoolName ?: "")
                    InfoRow(stringResource(R.string.address), orderInfo?.schoolsVo?.location ?: "")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // 底部固定操作条（设计稿屏 6：border-top + surface 底 + padding 16/24，D-OD5/6/7）
        OrderDetailActionBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            onContactServiceClick = {
                // 待客服功能立项后接入（D-OD12，用户已确认：先以 Toast 兜底）
                Toast.makeText(context, context.getString(R.string.contact_service_unavailable), Toast.LENGTH_SHORT).show()
            },
            onPickupCodeClick = {
                // D-OD1：待寄件/待取件且取件码非空可弹窗（实时取 orderInfo 当前值），否则提示
                val status = OrderStatus.fromStatus(orderInfo?.status ?: "-1")
                val pickupCode = parsePickupCode(orderInfo)
                if ((status == OrderStatus.PENDING_SHIPMENT || status == OrderStatus.READY_FOR_PICKUP) &&
                    pickupCode.isNotEmpty()
                ) {
                    showPickupCodeDialog = true
                } else {
                    Toast.makeText(context, context.getString(R.string.no_pickup_code), Toast.LENGTH_SHORT).show()
                }
            }
        )
        }

        // 取件码弹窗（D-OD1）：柜号 + 取件码，承载旧版寄存柜信息卡的全部信息
        if (showPickupCodeDialog) {
            AppInfoDialog(
                message = stringResource(
                    R.string.pickup_code_info,
                    parsePickupCode(orderInfo),
                    orderInfo?.lockersVo?.lockerNumber ?: 0
                ),
                onDismiss = { showPickupCodeDialog = false }
            )
        }
    }
}

/**
 * 取件码解析 — 复刻旧版逻辑（53bf92c OrderDetailPage.kt:141）：
 * 后端 pickupCode 为冒号分段格式，取第 3 段为实际取件码
 */
private fun parsePickupCode(orderInfo: OrderInfo?): String =
    orderInfo?.pickupCode?.split(":")?.getOrNull(2) ?: ""

/**
 * 进度时间线 — 规范 §3.4 竖向时间线
 * 当前状态节点高亮（now），已完成节点品牌色（done），未完成节点灰色
 * 节点标题=状态名；副行仅首节点显示真实下单时间（D-OD2：原 5 条硬编码时间文案已移除）
 */
@Composable
fun ProgressTimeline(currentStatus: OrderStatus?, firstNodeTime: String? = null) {
    // 时间线节点（按订单流程顺序，保持 5 个状态不变）
    val nodes = listOf(
        OrderStatus.PENDING_PAYMENT,
        OrderStatus.PENDING_SHIPMENT,
        OrderStatus.WASHING,
        OrderStatus.READY_FOR_PICKUP,
        OrderStatus.COMPLETED,
    )

    // 确定当前节点索引
    val currentIndex = nodes.indexOfFirst { it == currentStatus }.coerceAtLeast(0)

    Column(modifier = Modifier.padding(start = 26.dp)) {
        nodes.forEachIndexed { index, node ->
            val isDone = index < currentIndex
            val isCurrent = index == currentIndex
            val isLast = index == nodes.lastIndex

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 左侧竖列：节点圆点 + 竖 rail（tl3 规格，D-OD9）
                Column(
                    modifier = Modifier
                        .width(20.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isCurrent) {
                        // now 节点：20dp 光环底（primarySoft #E8F6EF，M3 primaryContainer 令牌对应）+ 内层 12dp primary 实心
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.colorScheme.primary)
                            )
                        }
                    } else {
                        // done → primary 实心；未完成 → outline 实心
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isDone) AppColors.colorScheme.primary else AppColors.colorScheme.outline
                                )
                        )
                    }
                    if (!isLast) {
                        // rail 2dp：已完成段 primary、未完成 hairline；fillMaxHeight 衔接下一节点保证连续
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(
                                    if (isDone) AppColors.colorScheme.primary else AppColors.colorScheme.hairline
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(
                    modifier = Modifier.padding(bottom = if (isLast) 0.dp else 18.dp)
                ) {
                    Text(
                        text = stringResource(node.descriptionRes),
                        fontSize = 13.sp,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isCurrent) AppColors.colorScheme.primaryDark
                        else if (isDone) AppColors.colorScheme.textPrimary
                        else AppColors.colorScheme.textTertiary
                    )
                    // 副行仅首节点（已接单）显示真实下单时间 HH:mm（D-OD2），其余节点无副行
                    if (index == 0 && !firstNodeTime.isNullOrEmpty()) {
                        Text(
                            text = firstNodeTime,
                            fontSize = 11.sp,
                            color = AppColors.colorScheme.textTertiary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 状态区 — 设计稿屏 6：无卡片，居中 120dp 滚筒插画 + 24sp 状态字 + 12sp 副行提示（D-OD8）
 * 本页插画为静态复刻（清洗中动效由首页滚筒承担，DrumIcon.kt 保留不动）；数据流不变
 */
@Composable
private fun OrderStatusHeader(status: OrderStatus?) {
    data class OrderStatusInfo(
        @androidx.annotation.StringRes val statusTextRes: Int,
        val statusColor: Color,
        @androidx.annotation.StringRes val estimatedTimeRes: Int,
    )

    val info: OrderStatusInfo = when (status) {
        OrderStatus.PENDING_PAYMENT -> OrderStatusInfo(
            R.string.pending_payment, AppColors.colorScheme.warning, R.string.please_pay_soon
        )
        OrderStatus.WASHING -> OrderStatusInfo(
            OrderStatus.WASHING.descriptionRes, AppColors.colorScheme.primary, R.string.please_pay_soon
        )
        OrderStatus.PENDING_SHIPMENT -> OrderStatusInfo(
            R.string.pending_shipment, AppColors.colorScheme.primary, R.string.please_ship_soon
        )
        OrderStatus.READY_FOR_PICKUP -> OrderStatusInfo(
            R.string.pending_pickup, AppColors.colorScheme.primary, R.string.clothes_in_locker
        )
        OrderStatus.COMPLETED -> OrderStatusInfo(
            R.string.completed, AppColors.colorScheme.success, R.string.completion_time
        )
        // CANCELED 语义色 error → danger（对齐 D-O5 同款语义）
        OrderStatus.CANCELED -> OrderStatusInfo(
            R.string.cancelled, AppColors.colorScheme.danger, R.string.cancelled
        )
        else -> OrderStatusInfo(R.string.dash, MaterialTheme.colorScheme.onBackground, R.string.dash)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DrumIllustration()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(info.statusTextRes),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (status == OrderStatus.WASHING) {
                // 清洗中 → brand-deep（primaryDark），余态保留语义色
                AppColors.colorScheme.primaryDark
            } else {
                info.statusColor
            },
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(info.estimatedTimeRes),
            fontSize = 12.sp,
            color = AppColors.colorScheme.textSecondary
        )
    }
}

/**
 * 滚筒插画 — 120dp Canvas，逐属性复刻设计稿屏 6 SVG（D-OD8）。
 * 深色组取自设计稿 .v3.dark remap；#2D9B6A 品牌绿与白色高光两主题不变。
 */
@Composable
private fun DrumIllustration() {
    val isDark = isSystemInDarkTheme()
    val bodyFill = if (isDark) Color(0xFF2A2F2C) else Color(0xFFFFFFFF)
    val topLine = if (isDark) Color(0xFF2C4A39) else Color(0xFFE8F6EF)
    val dot1 = Color(0xFF2D9B6A)
    val dot2 = if (isDark) Color(0xFF2E6E51) else Color(0xFF8FD4B4)
    val dot3 = if (isDark) Color(0xFF25473A) else Color(0xFFC9E8D8)
    val drumFill = if (isDark) Color(0xFF182C22) else Color(0xFFF0FAF5)
    val drumInner = if (isDark) Color(0xFF1F3D2E) else Color(0xFFD8F0E4)
    val brand = Color(0xFF2D9B6A)   // 与 Primary 令牌同值，设计稿固定色
    val highlight = Color(0xFFFFFFFF)

    Canvas(modifier = Modifier.size(120.dp)) {
        // 设计稿 120×120px 坐标直绘：unit = 单个设计像素
        val unit = size.minDimension / 120f

        // 机身 roundRect(10,10,100×100, rx26)：fill + 2.6 描边
        drawRoundRect(
            color = bodyFill,
            topLeft = Offset(10f * unit, 10f * unit),
            size = Size(100f * unit, 100f * unit),
            cornerRadius = CornerRadius(26f * unit, 26f * unit)
        )
        drawRoundRect(
            color = brand,
            topLeft = Offset(10f * unit, 10f * unit),
            size = Size(100f * unit, 100f * unit),
            cornerRadius = CornerRadius(26f * unit, 26f * unit),
            style = Stroke(width = 2.6f * unit)
        )
        // 顶线 (10,40)-(110,40) w2
        drawLine(
            color = topLine,
            start = Offset(10f * unit, 40f * unit),
            end = Offset(110f * unit, 40f * unit),
            strokeWidth = 2f * unit
        )
        // 控制面板三点 r4
        drawCircle(dot1, radius = 4f * unit, center = Offset(28f * unit, 25f * unit))
        drawCircle(dot2, radius = 4f * unit, center = Offset(42f * unit, 25f * unit))
        drawCircle(dot3, radius = 4f * unit, center = Offset(56f * unit, 25f * unit))
        // 滚筒外圆 r30 fill + 2.6 描边
        drawCircle(drumFill, radius = 30f * unit, center = Offset(60f * unit, 76f * unit))
        drawCircle(
            brand, radius = 30f * unit, center = Offset(60f * unit, 76f * unit),
            style = Stroke(width = 2.6f * unit)
        )
        // 滚筒内圆 r21
        drawCircle(drumInner, radius = 21f * unit, center = Offset(60f * unit, 76f * unit))
        // 中心轴 r7
        drawCircle(brand, radius = 7f * unit, center = Offset(60f * unit, 76f * unit))
        // 水位波线 w2.2 round
        val wave = Path().apply {
            moveTo(42f * unit, 76f * unit)
            cubicTo(46f * unit, 71.5f * unit, 50f * unit, 78.5f * unit, 54f * unit, 75.5f * unit)
            cubicTo(58f * unit, 72.5f * unit, 62f * unit, 77f * unit, 66f * unit, 74f * unit)
        }
        drawPath(wave, color = brand, style = Stroke(width = 2.2f * unit, cap = StrokeCap.Round))
        // 高光两点
        drawCircle(highlight, radius = 2.6f * unit, center = Offset(51f * unit, 66f * unit))
        drawCircle(highlight, radius = 2.2f * unit, center = Offset(70f * unit, 87f * unit))
    }
}

/**
 * 底部固定操作条 — 设计稿屏 6：0.5dp border-top + surface 底 + padding 16/24 + gap 12（D-OD5/6/7）
 * 按钮规格 btn3：高 52 / r14 / 15sp / 600 / letterSpacing 1；点击行为由页面回调承接（D-OD1/D-OD12）
 */
@Composable
private fun OrderDetailActionBar(
    modifier: Modifier = Modifier,
    onContactServiceClick: () -> Unit = {},
    onPickupCodeClick: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppColors.colorScheme.surface
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(AppColors.colorScheme.outline)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 次按钮 — surfaceVariant 底 + 主文本色（§3.3），自绘无 state layer
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .pressable(onClick = onContactServiceClick, debounce = false)
                        .background(
                            AppColors.colorScheme.surfaceVariant,
                            RoundedCornerShape(AppDimens.buttonRadius)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.contact_service),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = AppColors.colorScheme.textPrimary
                    )
                }
                // 主按钮 btn3-p：135° 对角绿渐变（primary → primaryDark），白字
                val pickupGradient = Brush.linearGradient(
                    colors = listOf(AppColors.colorScheme.primary, AppColors.colorScheme.primaryDark),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .pressable(onClick = onPickupCodeClick, debounce = false)
                        .background(pickupGradient, RoundedCornerShape(AppDimens.buttonRadius)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.view_pickup_code),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

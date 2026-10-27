package com.smartwash.ui.page.detail

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.smartwash.common.ui.theme.AppTextStyles
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
        // 内容列 weight(1f) + 操作条顺序排布：内容不被固定条遮挡，让位高度动态（D-OD5）
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            PageHeader(title = stringResource(R.string.order_detail), onBack = { navController.navigateUp() })

            Column(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                // 状态区 — 无卡片：居中滚筒插画 + 状态字（设计稿屏 6，D-OD8）
                // 屏 6 实测 页头文字底→插画顶 14dp：PageHeader 自带内边距已占 18dp，不再追加 Spacer
                OrderStatusHeader(status = OrderStatus.fromStatus(orderInfo?.status ?: "-1"))

                // 进度时间线（规范 §3.4）；卡片规格对齐同页组画板实测：radiusLg 16 + 1px outline 描边 + 无阴影
                // （规范 §3：卡片 elevation 0 用描边代替）
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppDimens.radiusLg),
                    color = AppColors.colorScheme.surface,
                    border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                ) {
                    // 屏 6 实测卡内边距：左右 20 / 上 10 / 下 4，标题→节点带 6
                    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp)) {
                        Text(
                            text = stringResource(R.string.progress_timeline),
                            style = AppTextStyles.SectionTitle,
                            letterSpacing = 0.3.sp,
                            color = AppColors.colorScheme.textPrimary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        ProgressTimeline(
                            currentStatus = OrderStatus.fromStatus(orderInfo?.status ?: "-1"),
                            // 已接单节点副行：真实下单时间 HH:mm（格式异常则不显示，D-OD2）
                            firstNodeTime = orderInfo?.createdAt?.drop(11)?.take(5)
                                ?.takeIf { it.length == 5 && it.contains(':') }
                        )
                    }
                }

                // 屏 6 实测：时间线卡底 → 信息卡顶 12
                Spacer(modifier = Modifier.height(12.dp))

                // 订单信息（c3：物流/订单语义蓝图标，D-OD11）
                InfoSection(
                    title = stringResource(R.string.order_info),
                    icon = Icons.Rounded.Receipt,
                    iconContainerColor = AppColors.colorScheme.iconContainerBlue,
                    iconTint = AppColors.colorScheme.iconForegroundBlue
                ) {
                    // 行距节奏：屏 6 实测 48（行高 34 + 行间距 14）；值列 12/500 = labelMedium，订单号启用 tnum
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        InfoRow(
                            stringResource(R.string.order_number),
                            orderInfo?.orderNo ?: "",
                            valueStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
                        )
                        InfoRow(stringResource(R.string.package_type), orderInfo?.laundryPackageVo?.itemName ?: "")
                        // 价格行（D-OD3，恢复旧版套餐信息卡的 totalPrice 数据；设计稿仅 5 行无此项，保留为信息增强）
                        InfoRow(
                            stringResource(R.string.price),
                            stringResource(R.string.currency_format, (orderInfo?.totalPrice ?: 0f).toString())
                        )
                        // 实付款：AmountMedium 18/24/700 + primaryDark（屏 6 实测 18/700，#1E8C5C·浅 / #5BC894·深）
                        InfoRow(
                            stringResource(R.string.actual_payment),
                            stringResource(R.string.currency_format, (orderInfo?.payPrice ?: 0f).toString()),
                            valueColor = AppColors.colorScheme.primaryDark,
                            valueStyle = AppTextStyles.AmountMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        InfoRow(stringResource(R.string.school_name), orderInfo?.schoolsVo?.schoolName ?: "")
                        InfoRow(stringResource(R.string.address), orderInfo?.schoolsVo?.location ?: "")
                    }
                }

                // 屏 6 实测：信息卡底 → 操作条顶 8
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // 底部操作条（设计稿屏 6：border-top + surface 底 + padding 16/24，D-OD5/6/7）
        OrderDetailActionBar(
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
 * 进度时间线 — 设计稿屏 6 水平 stepper（实测：rail 2dp 节点连线、当前节点 water 蓝 20dp 光环 +
 * 12dp 实心、节点下方 caption 标签、首节点下时间副行；卡内节点带 20 + 标签 16 + 副行 16）
 * 当前状态节点高亮（now），已完成节点品牌色（done），未完成节点 outline/装饰灰
 * 节点标题=状态名；副行仅首节点显示真实下单时间（D-OD2：原 5 条硬编码时间文案已移除）
 * 注：设计稿为 4 节点（已接单/清洗中/烘干中/待取件）；代码保留 5 个业务状态节点
 * （烘干中非 OrderStatus 枚举值，待支付/已完成为真实流程端点，删减属业务语义变更，不在视觉对齐范围）
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
    val isDark = isSystemInDarkTheme()

    Column {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val step = maxWidth / nodes.size
            // rail 层（先绘，节点覆盖其上）：相邻节点圆心连线，2dp，完成段 primary / 未完成段 hairline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = step / 2 - 6.dp)
                    .offset(y = 9.dp)
            ) {
                for (i in 0 until nodes.size - 1) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                            .height(2.dp)
                            .background(
                                if (i < currentIndex) AppColors.colorScheme.primary
                                else AppColors.colorScheme.hairline
                            )
                    )
                }
            }
            // 节点 + 标签层
            Row(modifier = Modifier.fillMaxWidth()) {
                nodes.forEachIndexed { index, node ->
                    val isDone = index < currentIndex
                    val isCurrent = index == currentIndex

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 20dp 节点带：当前节点 water 光环 + 12dp 实心；余态 12dp 圆点居中（屏 6 实测）
                        Box(
                            modifier = Modifier.size(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCurrent) {
                                // now 节点：20dp 光环 water 12%（深色 15%）+ 内层 12dp water 实心
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(
                                            AppColors.colorScheme.water.copy(
                                                alpha = if (isDark) 0.15f else 0.12f
                                            )
                                        )
                                )
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(AppColors.colorScheme.water)
                                )
                            } else {
                                // done → primary 实心；未完成 → outline 实心
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isDone) AppColors.colorScheme.primary
                                            else AppColors.colorScheme.outline
                                        )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(node.descriptionRes),
                            // 节点标签 = caption 档 bodySmall 12/16（屏 6 实测 12/400，当前节点 600）
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                // 当前节点：#17322B·浅（onPrimaryContainer）/ #5BC894·深（primary）
                                isCurrent -> if (isDark) {
                                    AppColors.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                }
                                isDone -> AppColors.colorScheme.textPrimary
                                else -> AppColors.colorScheme.textTertiary
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        // 副行仅首节点（已接单）显示真实下单时间 HH:mm（D-OD2），其余节点无副行
                        if (index == 0 && !firstNodeTime.isNullOrEmpty()) {
                            Text(
                                text = firstNodeTime,
                                // 时间戳数字启用 tnum（规范 §5 金额与数字）
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                color = AppColors.colorScheme.textSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
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
            OrderStatus.WASHING.descriptionRes, AppColors.colorScheme.primary, R.string.washing_in_progress_hint
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
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DrumIllustration()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(info.statusTextRes),
            // 状态大字 = DataLarge 36/44/700（屏 6 实测 36/700/44，色 #1E8C5C·浅 / #5BC894·深 = primaryDark）
            style = AppTextStyles.DataLarge,
            color = if (status == OrderStatus.WASHING) {
                // 清洗中 → brand-deep（primaryDark），余态保留语义色
                AppColors.colorScheme.primaryDark
            } else {
                info.statusColor
            }
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(info.estimatedTimeRes),
            // 副行提示 caption 档（屏 6 实测 12/16/400；插画→状态字 12 / 状态字→副行 6）
            style = MaterialTheme.typography.bodySmall,
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
 * 底部固定操作条 — 设计稿屏 6 实测：1dp 顶线（outline）+ surface 底 + padding 16/24 + 按钮间距 12（D-OD5/6/7）
 * 按钮规格（实测）：高 48 / radiusFull 胶囊 / 文字 font.title 18/600；
 * 主按钮纯色 primary + onPrimary 白/墨绿字（ls 1）；次按钮透明底 + 1dp outline 描边 + primaryDark 字（§5 次级按钮描边样式，§10 绿字红线 green.700）
 * 点击行为由页面回调承接（D-OD1/D-OD12）
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
                    .height(1.dp)
                    .background(AppColors.colorScheme.outline)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 次按钮 — 透明底 + 1dp outline 描边（屏 6 实测无填充，规范 §5 次级按钮描边样式）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .pressable(onClick = onContactServiceClick, debounce = false)
                        .border(
                            1.dp,
                            AppColors.colorScheme.outline,
                            RoundedCornerShape(AppDimens.radiusFull)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.contact_service),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.colorScheme.primaryDark
                    )
                }
                // 主按钮 — 纯色 primary 胶囊（屏 6 实测无渐变），onPrimary 双主题成对
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .pressable(onClick = onPickupCodeClick, debounce = false)
                        .background(AppColors.colorScheme.primary, RoundedCornerShape(AppDimens.radiusFull)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.view_pickup_code),
                        style = MaterialTheme.typography.titleMedium,
                        // 屏 6 实测主按钮字距 ls=1
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

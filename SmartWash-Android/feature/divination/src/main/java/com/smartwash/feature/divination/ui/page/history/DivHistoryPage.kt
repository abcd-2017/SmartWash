package com.smartwash.feature.divination.ui.page.history

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.feature.divination.R
import com.smartwash.feature.divination.core.DivMethod
import com.smartwash.feature.divination.ui.DivinationScreen
import com.smartwash.feature.divination.ui.components.DivCard
import com.smartwash.feature.divination.ui.components.DivCategoryChip
import com.smartwash.feature.divination.ui.components.DivFooterNote
import com.smartwash.feature.divination.ui.components.DivPageHeader
import com.smartwash.feature.divination.ui.components.DivSeal
import com.smartwash.feature.divination.DivRoute
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 卦历案卷（原型 History）：术数筛选 chips + 卦历卡列表；点击进只读复盘（原盘原时刻）。
 */
@Composable
fun DivHistoryPage(
    navController: NavHostController,
    viewModel: DivHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val records by viewModel.records.collectAsState()
    val view = currentView()

    DivinationScreen(scrollable = false) {
        // 颜色必须在 DivinationScreen 之内读取（默认玄墨，包根外读会让空态文案在浅色系统下不可见）
        val c = DivColors.current
        // 画板（玄墨/宣纸双板一致）：无返回页头标题左对齐 x20，标题下 10 进筛选行
        DivPageHeader(title = stringResource(R.string.div_history_title), alignStart = true)

        // 术数筛选（2026-11-01 缺陷 7d：五枚 chip 总宽 302dp+，360dp 屏 34dp 缩进下末枚
        // 「六壬」被剩余约束压窄换行——回归 a1c845c；改回 20 缩进 + 横向滚动 + 单行限高，
        // 与问卦页领域 chips 行同款（DivAskPage），窄屏滚动可见不折行）
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            DivCategoryChip(
                text = stringResource(R.string.div_filter_all),
                selected = uiState.filter == null,
                onClick = { viewModel.onFilter(null) },
                // 画板：卦历筛选 chip 宽 52（2 字 12 号 + 左右 14），与问卦领域 chip 48 区分
                horizontalPadding = 14.dp,
            )
            DivMethod.entries.forEach { method ->
                DivCategoryChip(
                    text = methodLabel(method),
                    selected = uiState.filter == method,
                    onClick = { viewModel.onFilter(method) },
                    horizontalPadding = 14.dp,
                )
            }
        }

        // 画板：筛选行到首卡间距 20（本 Spacer 16 + 列表 contentPadding 顶 4）
        Spacer(Modifier.height(16.dp))

        // 三态判定（audit-08 #3）：null=Room 首帧未到（加载中，渲染同形骨架）、empty=数据库真空（空态）、
        // 非空=列表——VM 初值已三态化，加载期不再以空态冒充首帧
        val current = records
        val listState = when {
            current == null -> HistoryListState.LOADING
            current.isEmpty() -> HistoryListState.EMPTY
            else -> HistoryListState.READY
        }
        // 空态与列表互相替换走 Crossfade，避免 0ms 硬切
        // （常态走 micro 档 enterTween；CROSSFADE_ALT_MS 是 reduced motion 降级专用档，
        // 经 motionSpec 自动落 180ms——口径与 DivAskPage/DivReadingPage 的 Crossfade 一致）
        Crossfade(
            targetState = listState,
            animationSpec = motionSpec(enterTween(DURATION_MICRO_MS)),
            label = "historyState",
        ) { state ->
            when (state) {
                HistoryListState.LOADING -> Column(Modifier.weight(1f)) {
                    // 骨架行 ×3 与 HistoryCard 同形；直接取静态浅块形态
                    // （规范 §8 骨架微光的 reduced-motion 降级形态，无动效即免降级分支）
                    repeat(3) {
                        HistorySkeletonRow()
                        Spacer(Modifier.height(8.dp))
                    }
                }
                HistoryListState.EMPTY -> Box(
                    Modifier.fillMaxWidth().padding(top = 80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // 卦历画板 = flat 平涂深朱口径（DivSeal 组件契约），同时印文对比 3.4→5.31:1 达标（audit-08 #1）
                        DivSeal(stringResource(R.string.div_seal_empty), size = 44.dp, fontSize = 22.sp, flat = true)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.div_history_empty),
                            fontSize = 12.sp,
                            color = c.textTertiary,
                        )
                        // 空态行动按钮（规范 §3.6 空态三要素，2026-11-14 检查 report P2）：
                        // 深链直达本页的空账号用户无路径引导——补 CTA 跳观象台首页选术起卦
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .pressable(onClick = {
                                    view.performHaptic(HapticEffect.LIGHT)
                                    navController.navigate(DivRoute.Home) {
                                        launchSingleTop = true
                                    }
                                }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.div_history_go_home),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 2.sp,
                                color = c.goldText,
                            )
                        }
                    }
                }
                HistoryListState.READY -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    items(current.orEmpty(), key = { it.id }) { record ->
                        HistoryCard(
                            record = record,
                            onClick = {
                                // launchSingleTop 防重推栈：pressable 防抖窗口仅 500ms，跨窗口连点
                                // 仍会重复入栈详情页、返回链耗尽直接退出应用（2026-11-14 检查 report P1）
                                navController.navigate(DivRoute.Chart(record.id, animate = false)) {
                                    launchSingleTop = true
                                }
                            },
                            // 筛选切换的位移补间：reduced motion 取消（规范 §8）
                            // （animateItemPlacement 已废弃且需 OptIn 实验 API，改用其官方替代 animateItem）
                            modifier = if (LocalReduceMotion.current) {
                                Modifier
                            } else {
                                Modifier.animateItem(placementSpec = motionSpec(defaultSpring()))
                            },
                        )
                        // 画板：卡间距 8（卡高 60）
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        DivFooterNote(text = stringResource(R.string.div_history_note))
    }
}

@Composable
private fun HistoryCard(
    record: com.smartwash.feature.divination.database.DivRecordEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DivColors.current
    val timeText = remember(record.castAt) {
        // 案卷时间统一东八区口径（2026-11-14 检查 report P2）：与天象头四柱（lunar 库东八区）
        // 对齐，不随设备时区漂移——跨时区设备上同一「今天」不再呈现两个日期
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }.format(Date(record.castAt))
    }
    // @Composable 的 methodLabel 在字符串模板外先取值（Composable 调用须在组合上下文直呼）
    val methodText = methodLabel(DivMethod.fromId(record.method))
    // 摘要行 Gson 解析缓存：与 timeText 同策略 remember，筛选切换/animateItem 位移/滚动
    // 复用重组时不重复 decode chartJson（2026-11-14 检查 report P2）
    val summaryText = remember(record.id) { record.summaryLine() }
    DivCard(modifier = modifier, onClick = onClick) {
        // 画板：卡高 60，印章距左 14；问句 14/20 偏上 12、元信息 12/14 间隔 2；› 距右 8
        Row(
            Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 卦历画板 = flat 平涂深朱口径（DivSeal 组件契约），同时印文对比 3.4→5.31:1 达标（audit-08 #1）
            DivSeal(stringResource(methodSealRes(record.method)), size = 32.dp, fontSize = 14.sp, cornerRadius = 8.dp, flat = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = record.question.ifEmpty { "—" },
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$timeText · $methodText · $summaryText",
                    fontSize = 12.sp,
                    // 行高维持画板 12/14 口径（观象台域既有画板豁免；v0.4 caption 12/16 留画板复核）
                    lineHeight = 14.sp,
                    color = c.textSecondary,
                    // 与主文案同策略单行截断（audit-08 #6）：长摘要不换行撑破「卡高 60」画板口径
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text("›", color = c.textSecondary, fontSize = 14.sp)
        }
    }
}

/** 列表摘要行：卦名/局数/课体 */
private fun com.smartwash.feature.divination.database.DivRecordEntity.summaryLine(): String =
    com.smartwash.feature.divination.data.DivChartCodec.decode(chartJson)?.let { bundle ->
        when {
            bundle.liuyao != null -> bundle.liuyao.name + (bundle.liuyao.bianName?.let { " 之 $it" } ?: "")
            bundle.meihua != null -> bundle.meihua.benName
            bundle.qimen != null -> "阴遁${bundle.qimen.juNumber}局"
            bundle.liuren != null -> "月将${bundle.liuren.yueJiang}"
            else -> ""
        }
    } ?: ""

/** 术数显示名（与首页玉牌/案卷行同源 div_method_*_name 资源，audit-11 #1：禁止硬编码/枚举直出） */
@Composable
private fun methodLabel(method: DivMethod): String = when (method) {
    DivMethod.LIU_YAO -> stringResource(R.string.div_method_liuyao_name)
    DivMethod.MEI_HUA -> stringResource(R.string.div_method_meihua_name)
    DivMethod.QI_MEN -> stringResource(R.string.div_method_qimen_name)
    DivMethod.LIU_REN -> stringResource(R.string.div_method_liuren_name)
}

/** 术数印章字资源（画板：六爻印「六」、梅花「梅」、奇门「奇」、六壬「壬」；2026-11-05 卦历画板六爻印取「六」） */
private fun methodSealRes(methodId: String): Int = when (DivMethod.fromId(methodId)) {
    DivMethod.LIU_YAO -> R.string.div_seal_liuyao
    DivMethod.MEI_HUA -> R.string.div_seal_meihua
    DivMethod.QI_MEN -> R.string.div_seal_qimen
    DivMethod.LIU_REN -> R.string.div_seal_liuren
}

/** 列表三态（audit-08 #3）：区分「Room 首帧未到（加载中）」与「数据库真空（空态）」，消除进页闪空态 */
private enum class HistoryListState {
    LOADING, EMPTY, READY,
}

/** 加载骨架行（audit-08 #3）：与 [HistoryCard] 同形（卡高 60 画板：印章位 32 方块 r8 + 问句/元信息两条浅块，
 *  取值全部镜像 HistoryCard 既有画板值），直接取静态浅块形态——规范 §8 骨架微光的
 *  reduced-motion 降级形态即静态浅块，无动效即免降级分支 */
@Composable
private fun HistorySkeletonRow() {
    val c = DivColors.current
    DivCard {
        Row(
            Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.hair),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .background(c.hair),
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .background(c.hair),
                )
            }
        }
    }
}

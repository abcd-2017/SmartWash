package com.smartwash.feature.divination.ui.page.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.feature.divination.DivRoute
import com.smartwash.feature.divination.R
import com.smartwash.feature.divination.core.DivMethod
import com.smartwash.feature.divination.ui.DivinationScreen
import com.smartwash.feature.divination.ui.components.CompassDial
import com.smartwash.feature.divination.ui.components.DivCard
import com.smartwash.feature.divination.ui.components.DivFooterNote
import com.smartwash.feature.divination.ui.components.DivSectionTitle
import com.smartwash.feature.divination.ui.components.DivSeal
import com.smartwash.feature.divination.ui.components.TianXiangHeader
import com.smartwash.feature.divination.ui.components.divSerif
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** 玉牌条目辅助类（method + 名称 + 定位 + 适用场景 + 推荐 tag 资源 id） */
private data class JadeEntry(
    val method: DivMethod,
    val nameRes: Int,
    val descRes: Int,
    val useForRes: Int,
    val tagRes: Int?,
)

/**
 * 观象台首页（原型 DivHome）：干支天象头 + 缓转罗盘 + 四术玉牌（竖排名）+ 今日一签 + 案卷。
 */
@Composable
fun DivHomePage(
    navController: NavHostController,
    viewModel: DivHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val records by viewModel.recentRecords.collectAsState()
    val view = currentView()

    // 2026-11-01 缺陷 7a：本页是观象台唯一未包 DivinationScreen 的页面——状态栏裸顶（时辰被挡）、
    // LocalDivinationColors 落到默认玄墨（浅色系统下米白字画浅底）、导航栏/IME 也无避让。
    // 统一包根：主题覆盖 + 宣纸/玄墨渐变背景 + 底部安全区，顶部 statusBarsPadding 对齐 DivPageHeader 惯例
    //
    // 颜色必须在 DivinationScreen 之内读取：LocalDivinationColors 是 staticCompositionLocalOf，
    // 默认值恒为玄墨（DarkDivinationColors）。在包根之外读会拿到米白主文本画在宣纸底上
    // （对比度 1.09:1，等于不可见）——观象台标题/今日一签标题「看不见」即此因。
    DivinationScreen {
        val c = DivColors.current
        TianXiangHeader(
            text = uiState.tianXiang.ifEmpty { "—" },
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 12.dp),
        )
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                // 画板 .lg-title：衬线 600 / 字距 4px（原 0 字距下标题过密、辨识度低）
                text = stringResource(R.string.div_title),
                style = divSerif(22.sp, FontWeight.SemiBold, 4.sp),
                color = c.textPrimary,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = stringResource(R.string.div_home_xunkong_format, uiState.xunKong.ifEmpty { "—" }),
                fontSize = 12.sp,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        // 罗盘
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompassDial(modifier = Modifier.size(104.dp))
        }

        // 四术玉牌（2026-11-01 缺陷 7b）：原型 .jade-grid 口径 = 2×2 等分网格（gap 14）、
        // 每术定制线描 glyph + 竖排术名 + 一句话定位 + 鎏金渐变描边，六爻居首（左上）+ 推荐 tag + 加大 glyph。
        //
        // 上一版把六爻拉成通栏主牌、其余三牌挤在一行：360dp 屏次牌仅 ~101dp 宽，
        // 12sp 定位文案（约 108dp）必然折成 3–4 行溢出 128dp 定高 → 文字互相叠压，观感很脏。
        // 现回归原型 2×2：单牌 ~154dp、内容宽 ~122dp，文案两行刚好放下，四牌同构同行高一致。
        val jadeEntries = listOf(
            JadeEntry(DivMethod.LIU_YAO, R.string.div_method_liuyao_name, R.string.div_method_liuyao_desc, R.string.div_method_liuyao_use_for, R.string.div_method_liuyao_tag),
            JadeEntry(DivMethod.MEI_HUA, R.string.div_method_meihua_name, R.string.div_method_meihua_desc, R.string.div_method_meihua_use_for, null),
            JadeEntry(DivMethod.QI_MEN, R.string.div_method_qimen_name, R.string.div_method_qimen_desc, R.string.div_method_qimen_use_for, null),
            JadeEntry(DivMethod.LIU_REN, R.string.div_method_liuren_name, R.string.div_method_liuren_desc, R.string.div_method_liuren_use_for, null),
        )
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            jadeEntries.chunked(2).forEach { rowEntries ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowEntries.forEach { entry ->
                        JadeEntryTile(
                            method = entry.method,
                            name = stringResource(entry.nameRes),
                            desc = stringResource(entry.descRes),
                            useFor = stringResource(entry.useForRes),
                            tag = entry.tagRes?.let { stringResource(it) },
                            featured = entry.tagRes != null,
                            onClick = {
                                view.performHaptic(HapticEffect.LIGHT)
                                navController.navigate(DivRoute.Ask(entry.method.id))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // 今日一签
        val sign = uiState.todaySign
        if (sign != null) {
            DivCard(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                onClick = { viewModel.ensureTodaySignSaved { id -> navController.navigate(DivRoute.Chart(id)) } },
            ) {
                Row(
                    Modifier.padding(start = 18.dp, top = 10.dp, end = 10.dp, bottom = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DivSeal(stringResource(R.string.div_sign_seal), size = 44.dp, fontSize = 22.sp, cornerRadius = 8.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            // 画板 .sign-card .t .name：衬线 18px / 字距 4px
                            text = stringResource(R.string.div_sign_card_title, sign.hexagramName),
                            style = divSerif(18.sp, FontWeight.SemiBold, 4.sp),
                            color = c.textPrimary,
                        )
                        Text(
                            text = sign.subLine + " —— " + stringResource(R.string.div_sign_card_source),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = c.textSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text("›", color = c.goldText.copy(alpha = 0.6f), fontSize = 16.sp)
                }
            }
        }

        // 案卷
        DivSectionTitle(
            title = stringResource(R.string.div_home_ledger),
            tail = stringResource(R.string.div_home_ledger_more),
            onTailClick = { navController.navigate(DivRoute.History) },
        )
        DivCard(Modifier.padding(horizontal = 20.dp)) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                // 空态与记录列表互相替换时做交叉淡入淡出，避免 0ms 硬切
                Crossfade(
                    targetState = records.isEmpty(),
                    animationSpec = tween(CROSSFADE_ALT_MS),
                    label = "ledgerEmpty",
                ) { empty ->
                    if (empty) {
                        Text(
                            text = stringResource(R.string.div_history_empty),
                            fontSize = 12.sp,
                            color = c.textTertiary,
                            modifier = Modifier.padding(vertical = 18.dp),
                        )
                    } else {
                        records.take(3).forEachIndexed { index, record ->
                            LedgerRow(record)
                            if (index != 2 && index != records.take(3).lastIndex) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(c.hair)
                                )
                            }
                        }
                    }
                }
            }
        }

        DivFooterNote(text = stringResource(R.string.div_disclaimer_footer))
    }
}

/** 案卷行：日期 + 术数色点 + 问题摘要 + 卦名（衬线金） */
@Composable
private fun LedgerRow(record: com.smartwash.feature.divination.database.DivRecordEntity) {
    val c = DivColors.current
    val dateText = remember(record.castAt) {
        SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(record.castAt))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dateText, fontSize = 12.sp, color = c.textTertiary, modifier = Modifier.width(44.dp))
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .size(7.dp)
                .background(c.gold, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = record.question,
            fontSize = 12.sp,
            lineHeight = 20.sp,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = DivMethod.fromId(record.method).name,
            style = divSerif(12.sp, FontWeight.Medium, 0.sp),
            color = c.goldText.copy(alpha = 0.75f),
        )
    }
}

/**
 * 四术玉牌（原型 .jade / .jade-grid）：2×2 等分网格中的单牌 ——
 * 定制线描 glyph（左上）+ 竖排术名（右上）+ 一句话定位（两行）+ 适用场景（第三行灰字，画板 12/15）
 * + 推荐 tag（紧随适用场景之下，仅六爻）。
 *
 * 层级：六爻为首选术，用「鎏金渐变描边 + 推荐 tag + glyph 40dp」加大；
 * 其余三牌用 12% 金发丝描边 + glyph 34dp，同一网格内主次分明而不靠尺寸割裂。
 *
 * 结构约定：tag 位四牌同构预留固定高度（无 tag 留空），牌高由内容撑开（heightIn min 兜底），
 * 同一行两牌行高必然一致；窄屏/大字号下文案折行时牌高随之增长，不裁切、不叠压。
 */
@Composable
private fun JadeEntryTile(
    method: DivMethod,
    name: String,
    desc: String,
    useFor: String,
    tag: String?,
    featured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DivColors.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 148.dp)
            // 规范 §2.4 卡片唯一画法 = 1px 描边 + 柔和投影；玄墨深色卡按 §2.4 豁免投影
            .then(if (c.isDark) Modifier else Modifier.softCardShadow(cornerRadius = 16.dp))
            .clip(shape)
            .background(c.surface)
            // 鎏金渐变描边：Compose border 支持 Brush（135° 对角 = 默认 linearGradient 方向）；
            // 次牌降为 12% 金发丝线，避免四牌金框并列过吵
            .then(
                if (featured) {
                    Modifier.border(1.dp, Brush.linearGradient(listOf(c.goldLine, c.goldHair)), shape)
                } else {
                    Modifier.border(1.dp, c.hair, shape)
                }
            )
            .pressable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            MethodGlyph(method = method, size = if (featured) 40.dp else 34.dp, tint = c.gold)
            Spacer(Modifier.weight(1f))
            // 竖排术名：writing-mode 语义用逐字换行实现（画板：18/600、行距 26、右上内缩 2）
            Column(horizontalAlignment = Alignment.End) {
                name.forEach { ch ->
                    Text(
                        text = ch.toString(),
                        style = divSerif(18.sp, FontWeight.SemiBold, 0.sp).copy(lineHeight = 26.sp),
                        color = c.textPrimary,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = desc,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        // 适用场景第三行 — 画板 use_for 行 12/15 次要灰（desc 之下、tag 之上）；
        // 弱化色取 textSecondary（宣纸 textTertiary 为全墨，作灰字反而更重）
        Text(
            text = useFor,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(6.dp))
        // tag 位固定高度：四牌同构，无 tag 留空（同一行两牌行高必然一致）
        Box(Modifier.height(16.dp)) {
            if (tag != null) {
                Text(
                    text = tag,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = c.goldText.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/**
 * 四术定制线描 glyph（2026-11-01 缺陷 7b，原型 DivHome 屏 40×40 线描图标的 Canvas 等价）：
 * 六爻=方孔铜钱 / 梅花=五瓣梅花环 / 奇门=九宫格 / 六壬=三层盘环。
 * 造型规范 §4.2：24 栅格、2dp 描边、round 端点与转角；纯几何线描，无位图。
 */
@Composable
private fun MethodGlyph(
    method: DivMethod,
    size: androidx.compose.ui.unit.Dp,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val cx = this.size.width / 2
        val cy = this.size.height / 2
        // 统一换算：24 栅格 → 实际画布（glyph 以中心 12,12 / 半径 10 基准设计）
        val unit = this.size.width / 24f
        fun p(v: Float) = v * unit
        when (method) {
            DivMethod.LIU_YAO -> {
                // 方孔铜钱：外圆 + 方孔（round 转角小方）
                drawCircle(tint, radius = p(10f), center = Offset(cx, cy), style = stroke)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(cx - p(3.5f), cy - p(3.5f)),
                    size = Size(p(7f), p(7f)),
                    cornerRadius = CornerRadius(p(1f), p(1f)),
                    style = stroke,
                )
            }
            DivMethod.MEI_HUA -> {
                // 五瓣梅花环：5 小圆 + 中心点
                repeat(5) { i ->
                    val angle = Math.toRadians((i * 72 - 90).toDouble())
                    val px = cx + p(5.5f) * cos(angle).toFloat()
                    val py = cy + p(5.5f) * sin(angle).toFloat()
                    drawCircle(tint, radius = p(3.2f), center = Offset(px, py), style = stroke)
                }
                drawCircle(tint, radius = p(1.2f), center = Offset(cx, cy))
            }
            DivMethod.QI_MEN -> {
                // 九宫格：外方（圆角）+ 十字分割
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(cx - p(9f), cy - p(9f)),
                    size = Size(p(18f), p(18f)),
                    cornerRadius = CornerRadius(p(2f), p(2f)),
                    style = stroke,
                )
                listOf(-3f, 3f).forEach { v ->
                    drawLine(tint, Offset(cx + p(v), cy - p(9f)), Offset(cx + p(v), cy + p(9f)), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(cx - p(9f), cy + p(v)), Offset(cx + p(9f), cy + p(v)), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            DivMethod.LIU_REN -> {
                // 三层盘环：三重同心圆 + 中环四向刻度点
                drawCircle(tint, radius = p(10f), center = Offset(cx, cy), style = stroke)
                drawCircle(tint, radius = p(6.5f), center = Offset(cx, cy), style = stroke)
                drawCircle(tint, radius = p(3f), center = Offset(cx, cy), style = stroke)
                listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f).forEach { (dx, dy) ->
                    drawCircle(
                        tint,
                        radius = p(0.9f),
                        center = Offset(cx + dx * p(4.75f), cy + dy * p(4.75f)),
                    )
                }
            }
        }
    }
}

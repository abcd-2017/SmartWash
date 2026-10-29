package com.smartwash.feature.divination.ui.page.followup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.feature.divination.R
import com.smartwash.feature.divination.data.DivFollowUpTurn
import com.smartwash.common.ui.components.WaveIcon
import com.smartwash.feature.divination.ui.components.DivPageHeader
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressScale
import com.smartwash.common.utils.rememberDebouncedClick

/** 新气泡首帧淡入时长 */
private const val BUBBLE_FADE_MS = 120

/**
 * 追问页（画板「追问 · 玄墨 / 追问 · 宣纸」）：页头（居中标题 + 居中副题）+ 聊天流
 * （用户实金气泡右对齐、回复卡面气泡左对齐）+ 流尾脚注 + 底部输入条（发丝顶线 + 金线输入框 + 48 圆形鎏金发送钮）。
 * 沿用原 castAt 与原盘面（从 Room 读），不重算。
 */
@Composable
fun DivFollowUpPage(
    navController: NavHostController,
    recordId: Long,
    viewModel: DivFollowUpViewModel = hiltViewModel(),
) {
    val c = DivColors.current
    val state by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 新消息到底部
    LaunchedEffect(state.history.size) {
        if (state.history.isNotEmpty()) {
            listState.animateScrollToItem(state.history.size - 1)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        DivPageHeader(
            title = stringResource(R.string.div_followup_title),
            onBack = { navController.popBackStack() },
            subtitle = stringResource(R.string.div_followup_subtitle),
        )

        if (state.record == null && !state.loadingRecord) {
            Spacer(Modifier.height(80.dp))
            Text(
                text = stringResource(R.string.div_chart_missing),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = c.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // 聊天流（画板：气泡左右缘 20、项距 16）
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.history.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.div_followup_empty),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = c.textTertiary,
                            )
                        }
                    }
                }
                items(state.history, key = { it.content + it.role }) { turn ->
                    ChatBubble(turn)
                }
                if (state.followUpState is RequestState.Loading) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 推演中的水波指示（§4.5 附：观象台金色；自带 reduced motion 静止分支）
                            WaveIcon(isLoading = true, tint = c.gold, size = 16.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.div_followup_thinking),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = c.textTertiary,
                            )
                        }
                    }
                }
                // 流尾常驻脚注（画板：—— 继续追问将沿用本卦盘推演 ——）
                item {
                    Text(
                        text = stringResource(R.string.div_followup_footer),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = c.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 底部输入框
            val pending = state.followUpState is RequestState.Loading
            FollowUpInputBar(
                value = input,
                onValueChange = { input = it },
                pending = pending,
                onSend = {
                    viewModel.send(input)
                    input = ""
                },
            )
        }
    }
}

/** 聊天气泡（画板双板）：用户实金底右对齐（玄墨 gold.500 / 宣纸 gold.300、墨字、r16 无描边），
 *  回复卡面左对齐（surface + 1px 12% 金发丝描边、r16）；文字 12/16，宽上限 220/240；新气泡首帧淡入。 */
@Composable
private fun ChatBubble(turn: DivFollowUpTurn) {
    val c = DivColors.current
    val isUser = turn.role == "user"
    // 新气泡首帧 alpha 0→1（reduced motion 取消，直接呈现）
    val reduceMotion = LocalReduceMotion.current
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            alpha.snapTo(1f)
        } else {
            alpha.animateTo(1f, tween(BUBBLE_FADE_MS))
        }
    }
    val appear = Modifier.graphicsLayer { this.alpha = alpha.value }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = if (isUser) 220.dp else 240.dp)
                // 回复气泡即卡面（surface + 1px 描边 + r16，§3.1 卡片画法）：宣纸侧补 level1 轻阴影；
                // 用户金底气泡按 §2.4「按钮/hero 类实色块不加投影」豁免
                .then(
                    if (isUser || c.isDark) Modifier else Modifier.shadow(AppElevation.level1, shape)
                )
                .then(appear)
                .clip(shape)
                .background(if (isUser) c.goldGradient.first() else c.surface)
                .then(
                    if (isUser) {
                        Modifier
                    } else {
                        Modifier.border(BorderStroke(1.dp, c.hair), shape)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = turn.content,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = if (isUser) c.onGold else c.textPrimary,
            )
        }
    }
}

/** 底部输入条（画板）：surface 底 + 1px 12% 金发丝顶线；输入框 48 高 r12 金线描边（玄墨墨面/宣纸纸面底）；
 *  发送钮 48 圆形鎏金 + send 墨色图标（禁用退回 14% 金底）。 */
@Composable
private fun FollowUpInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    pending: Boolean,
    onSend: () -> Unit,
) {
    val c = DivColors.current
    val view = currentView()
    val canSend = !pending && value.isNotBlank()
    val interactionSource = remember { MutableInteractionSource() }
    val debouncedSend = rememberDebouncedClick(enabled = canSend) {
        view.performHaptic(HapticEffect.LIGHT)
        onSend()
    }
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        // 顶线（画板：1px 12% 金发丝）
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(c.hair)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fieldShape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(fieldShape)
                    // 画板输入框底：玄墨=墨面顶 #201D18 / 宣纸=纸面底 #EBE3D1（主题 bgColors 极值）
                    .background(c.bgColors[if (c.isDark) 0 else 2])
                    .border(BorderStroke(1.dp, c.line), fieldShape)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(color = c.textPrimary, fontSize = 14.sp, lineHeight = 20.sp),
                    cursorBrush = SolidColor(c.gold),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(
                                text = stringResource(R.string.div_followup_input_hint),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = c.textTertiary,
                            )
                        }
                        innerTextField()
                    },
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pressScale(interactionSource, 0.97f)
                    .clip(CircleShape)
                    .background(if (canSend) c.gold else c.gold14)
                    .clickable(
                        interactionSource = interactionSource,
                        // 规范 §7：交互反馈不使用 Material ripple —— 涟漪形状不受令牌控制，
                        // 会浮出与容器不贴合的圆形色块；按压反馈由 pressScale(0.97) 承载
                        indication = null,
                        enabled = canSend,
                        onClick = debouncedSend,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.div_followup_send),
                    tint = if (canSend) c.onGold else c.goldText.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

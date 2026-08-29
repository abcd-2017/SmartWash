package com.smartwash.divination.ui.page.followup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.divination.core.DivMethod
import com.smartwash.divination.data.DivChartCodec
import com.smartwash.divination.data.DivFollowUpTurn
import com.smartwash.divination.ui.components.DivGoldButton
import com.smartwash.divination.ui.components.DivPageHeader
import com.smartwash.divination.ui.components.DivSeal
import com.smartwash.divination.ui.components.divSerif
import com.smartwash.ui.theme.DivColors
import com.smartwash.utils.RequestState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 追问页（原型 FollowUp）：聊天流；沿用原 castAt 与原盘面（从 Room 读），不重算。
 * 用户消息金底右对齐，回复素底左对齐；底部输入框 + 发送。
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
            tail = stringResource(R.string.div_followup_subtitle),
        )

        if (state.record == null && !state.loadingRecord) {
            Spacer(Modifier.height(80.dp))
            Text(
                text = stringResource(R.string.div_chart_missing),
                fontSize = 13.sp,
                color = c.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // 盘面摘要条
            state.record?.let { record ->
                FollowUpSummary(record)
            }

            // 聊天流
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                            Text(
                                text = stringResource(R.string.div_followup_thinking),
                                fontSize = 11.sp,
                                color = c.textTertiary,
                            )
                        }
                    }
                }
            }

            // 底部输入框
            FollowUpInputBar(
                value = input,
                onValueChange = { input = it },
                enabled = state.followUpState !is RequestState.Loading,
                onSend = {
                    viewModel.send(input)
                    input = ""
                },
            )
        }
    }
}

/** 盘面摘要条 */
@Composable
private fun FollowUpSummary(record: com.smartwash.divination.data.DivRecordEntity) {
    val c = DivColors.current
    val bundle = remember(record.chartJson) { DivChartCodec.decode(record.chartJson) }
    val method = DivMethod.fromId(record.method)
    val chartName = bundle?.let {
        when {
            it.liuyao != null -> it.liuyao.name + (it.liuyao.bianName?.let { b -> " 之 $b" } ?: "")
            it.meihua != null -> it.meihua.benName
            it.qimen != null -> "奇门遁甲"
            it.liuren != null -> "大六壬"
            else -> null
        }
    } ?: methodLabel(method)
    val sealText = when (method) {
        DivMethod.LIU_YAO -> chartName.take(1)
        DivMethod.MEI_HUA -> "梅"
        DivMethod.QI_MEN -> "奇"
        DivMethod.LIU_REN -> "壬"
    }
    val timeText = remember(record.castAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(record.castAt))
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DivSeal(sealText, size = 24.dp, fontSize = 12.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = chartName,
            style = divSerif(11.5.sp, FontWeight.Medium, 2.sp),
            color = c.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = timeText,
            fontSize = 9.5.sp,
            color = c.textTertiary,
        )
    }
}

/** 聊天气泡：user 金底右对齐，assistant 素底左对齐 */
@Composable
private fun ChatBubble(turn: DivFollowUpTurn) {
    val c = DivColors.current
    val isUser = turn.role == "user"
    if (isUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = 260.dp)
                    .clip(RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp))
                    .background(c.goldSoft)
                    .border(BorderStroke(1.dp, c.goldHair), RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp))
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            ) {
                Text(
                    text = turn.content,
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = c.textPrimary,
                )
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Box(
                Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp))
                    .background(c.surface)
                    .border(BorderStroke(1.dp, c.goldHair), RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
            ) {
                Text(
                    text = turn.content,
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = c.textPrimary,
                )
            }
        }
    }
}

/** 底部输入框 + 发送按钮 */
@Composable
private fun FollowUpInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    val c = DivColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(c.surface2)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = divSerif(13.sp, FontWeight.Normal, 0.sp).copy(color = c.textPrimary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.div_followup_input_hint),
                            fontSize = 13.sp,
                            color = c.textTertiary,
                        )
                    }
                    innerTextField()
                },
            )
        }
        Spacer(Modifier.width(10.dp))
        DivGoldButton(
            text = stringResource(R.string.div_followup_send),
            enabled = enabled && value.isNotBlank(),
            height = 38.dp,
            fontSize = 13.sp,
            modifier = Modifier.width(72.dp),
            onClick = onSend,
        )
    }
}

private fun methodLabel(method: DivMethod): String = when (method) {
    DivMethod.LIU_YAO -> "六爻"
    DivMethod.MEI_HUA -> "梅花"
    DivMethod.QI_MEN -> "奇门"
    DivMethod.LIU_REN -> "六壬"
}

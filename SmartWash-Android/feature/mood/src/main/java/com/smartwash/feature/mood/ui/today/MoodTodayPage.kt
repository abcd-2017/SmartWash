package com.smartwash.feature.mood.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonList
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.performHaptic
import com.smartwash.feature.mood.R
import com.smartwash.feature.mood.ui.MoodErrorState
import com.smartwash.feature.mood.ui.MoodLevelRow
import com.smartwash.feature.mood.ui.MoodSmallAction
import com.smartwash.feature.mood.ui.MoodWordChip
import com.smartwash.feature.mood.ui.navigateToMoodCalendar
import com.smartwash.feature.mood.ui.navigateToMoodTrend
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 今日记录页：五个心情色块一次点按即存（pixy 式低负担底线）+ 可选词 chips + 可选一句话。
 * 已记录当日进入回填态（level/word 回填，月历契约无 note 故备注不回填）；保存成功轻反馈
 * （成功触感 + 色块选中态，页内状态行提示覆盖语义，不弹 Toast 打断）。
 */
@Composable
fun MoodTodayPage(
    navController: NavHostController,
    viewModel: MoodTodayViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val view = currentView()
    val draft by viewModel.draft.collectAsState()
    val loadState by viewModel.loadState.collectAsState()
    val saveState by viewModel.saveState.collectAsState()

    // 保存结果副作用（规范：组合期禁止副作用，一律 LaunchedEffect）
    LaunchedEffect(saveState) {
        when (saveState) {
            is RequestState.Success -> {
                view.performHaptic(HapticEffect.SUCCESS)
                viewModel.resetSaveState()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        PageHeader(
            title = stringResource(R.string.mood_title),
            onBack = { navController.navigateUp() },
            actions = {
                MoodSmallAction(
                    text = stringResource(R.string.mood_action_calendar),
                    onClick = { navController.navigateToMoodCalendar() },
                )
                MoodSmallAction(
                    text = stringResource(R.string.mood_action_trend),
                    onClick = { navController.navigateToMoodTrend() },
                )
            },
        )
        when (loadState) {
            RequestState.Loading -> SkeletonList(rows = 3)
            is RequestState.Error -> MoodErrorState(
                message = (loadState as RequestState.Error).getMessage(context),
                onRetry = { viewModel.load() },
                modifier = Modifier.fillMaxSize(),
            )
            else -> MoodTodayContent(
                draft = draft,
                saveState = saveState,
                onSelectLevel = viewModel::selectLevel,
                onToggleWord = viewModel::toggleWord,
                onNoteChange = viewModel::updateNote,
                onNoteCommit = viewModel::commitNote,
            )
        }
    }
}

/** 加载成功的内容体 */
@Composable
private fun MoodTodayContent(
    draft: MoodTodayViewModel.Draft,
    saveState: RequestState,
    onSelectLevel: (Int) -> Unit,
    onToggleWord: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onNoteCommit: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.pagePadding)
            .padding(top = AppDimens.spaceSm, bottom = AppDimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
    ) {
        // ---------- 日期 + 问句 ----------
        Column {
            Text(
                text = stringResource(R.string.mood_today_question),
                style = AppTextStyles.SectionTitle,
                color = AppColors.colorScheme.textPrimary,
            )
            Text(
                text = LocalDate.now().format(
                    DateTimeFormatter.ofPattern(
                        stringResource(R.string.mood_today_date_pattern),
                        Locale.CHINESE,
                    )
                ),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // ---------- 五个心情色块（本页锚点，一次点按即存） ----------
        GroupCard {
            MoodLevelRow(
                selectedLevel = draft.level,
                onSelect = onSelectLevel,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            Text(
                text = if (draft.hasRecord) {
                    stringResource(R.string.mood_today_recorded)
                } else {
                    stringResource(R.string.mood_today_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textTertiary,
            )
            // 保存失败：内联错误（13sp error 色，保留后端 message——含 code=201 业务提示）
            if (saveState is RequestState.Error) {
                Text(
                    text = saveState.getMessage(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.error,
                    modifier = Modifier.padding(top = AppDimens.spaceXs),
                )
            }
        }

        // ---------- 词 chips（可选，选中一个，再点取消） ----------
        GroupCard {
            Text(
                text = stringResource(R.string.mood_word_section_title),
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.colorScheme.textPrimary,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppDimens.spaceSm),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
            ) {
                moodWordOptions().forEach { wordRes ->
                    val word = stringResource(wordRes)
                    MoodWordChip(
                        text = word,
                        selected = draft.word == word,
                        onClick = { onToggleWord(word) },
                    )
                }
            }
        }

        // ---------- 一句话（可选） ----------
        GroupCard {
            Text(
                text = stringResource(R.string.mood_note_section_title),
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.colorScheme.textPrimary,
            )
            OutlinedTextField(
                value = draft.note,
                onValueChange = onNoteChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.mood_note_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colorScheme.textTertiary,
                    )
                },
                // §3.7：输入正文 16sp，禁 14sp 及以下
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onNoteCommit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppDimens.spaceSm)
                    .height(48.dp),
                // 规范 §3 radius.input = 12
                shape = RoundedCornerShape(AppDimens.radiusMd),
                colors = moodTextFieldColors(),
            )
        }
    }
}

/** 词候选（"累/顺/静/燥/燃"等天气词，资源单一事实来源） */
private fun moodWordOptions() = listOf(
    R.string.mood_word_tired,
    R.string.mood_word_smooth,
    R.string.mood_word_quiet,
    R.string.mood_word_restless,
    R.string.mood_word_burning,
)

/** 输入框令牌配色（模式对齐 toolbox 的 tokenTextFieldColors，模块内最小复制） */
@Composable
private fun moodTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.colorScheme.primary,
    unfocusedBorderColor = AppColors.colorScheme.outline,
)

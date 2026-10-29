package com.smartwash.feature.user.impl.ui.update_userinfo

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.feature.user.impl.R
import com.smartwash.common.ui.components.AppCard
import com.smartwash.common.ui.components.AuthCtaButton
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.feature.laundry.api.model.SchoolOption
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.Primary
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.selectedColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 退出时长 ≈ 进入（DURATION_MICRO_MS）的 70%（动效语言 v0.4）
private const val EXIT_MICRO_MS = 140

/** 选中高亮至少可见的时长——颜色过渡走完再收焦点收起下拉 */
private const val SELECTION_SETTLE_MS = 180L

@Composable
fun UpdateUserInfoPage(
    navController: NavController,
    userInfoViewModel: UpdateUserInfoViewModel = hiltViewModel()
) {
    var selectedSchoolId by remember { mutableLongStateOf(-1) }
    var studentId by remember { mutableStateOf("") }
    var isSchoolError by remember { mutableStateOf(false) }
    var isStudentIdError by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 选中后延迟收起下拉的协程句柄——连点不同学校时取消上一次，避免提前收起
    var dismissJob by remember { mutableStateOf<Job?>(null) }

    val schoolList by userInfoViewModel.schools.collectAsState()
    val interactionSource = remember { MutableInteractionSource() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isSearchFocused by remember { mutableStateOf(false) }
    val updateState by userInfoViewModel.updateState.collectAsState()

    // 信息锁定：已填写学校/学号的用户进入本页即为只读态（只能由管理员修改）
    var isInfoLocked by remember { mutableStateOf(false) }
    val currentInfo by userInfoViewModel.currentInfo.collectAsState()

    LaunchedEffect(updateState) {
        when (updateState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.modify_success), Toast.LENGTH_SHORT).show()
                userInfoViewModel.setStateIdle()
                // 提交成功回主页并清栈——不能停留在本页：锁定态下输入与按钮均禁用，停留即被困
                navController.navigate(ShellRoute.HOME) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is RequestState.Error -> {
                Toast.makeText(context, (updateState as RequestState.Error).getMessage(context), Toast.LENGTH_SHORT).show()
                userInfoViewModel.setStateIdle()
            }
            else -> {}
        }
    }

    // 已填写用户进入本页：回填学校/学号并进入只读态，防止反复变更
    LaunchedEffect(currentInfo) {
        val info = currentInfo ?: return@LaunchedEffect
        val schoolId = info.schoolVo.schoolId
        if (schoolId != null && schoolId > 0L) {
            selectedSchoolId = schoolId
            query = info.schoolVo.schoolName.orEmpty()
            studentId = info.studentId
            isSchoolError = false
            isStudentIdError = false
            isInfoLocked = true
        }
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            if (it is FocusInteraction.Focus) isSearchFocused = true
            else if (it is FocusInteraction.Unfocus) isSearchFocused = false
        }
    }
    LaunchedEffect(Unit) { userInfoViewModel.searchSchool() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { focusManager.clearFocus() }
            .background(AppColors.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = AppDimens.pagePadding),
            // 底部预留固定 CTA 栏高度（提示 16 + 间距 16 + 按钮 48 + 底距 16 + 余量），滚动内容不被按钮遮挡
            contentPadding = PaddingValues(bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "hero") {
                Spacer(modifier = Modifier.height(AppDimens.spaceXxl))

                // 品牌区域 — Hero 卡（规范 §1.3 gradient.hero：浅色 #2D9B6A→#1E8C5C / 深色 #2D9B6A→#5BC894）
                val brandGradient = if (isSystemInDarkTheme()) {
                    Brush.linearGradient(listOf(Primary, AppColors.colorScheme.primary))
                } else {
                    Brush.linearGradient(listOf(AppColors.colorScheme.primary, AppColors.colorScheme.primaryDark))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppDimens.radiusLg))
                        .background(brandGradient)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 圆形图标容器（白色16%透明度 + 1px 白.22 描边，浅深两板同值）
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.School,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Text(
                            text = stringResource(R.string.complete_info),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = stringResource(R.string.fill_school_info),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            }

            item(key = "form") {
                // 字段分组卡 — DESIGN-SPEC v0.4 §3.1 唯一卡片画法：surface 白底 + 1px outline
                // 描边 + 1dp 阴影（level1 ≈ CSS 0 2px 12px rgba(20,40,30,.05)）+ 圆角 16 + 内边距 20
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppDimens.radiusLg),
                    color = AppColors.colorScheme.surface,
                    shadowElevation = AppElevation.level1,
                    border = BorderStroke(1.dp, AppColors.colorScheme.outline),
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMd)
                ) {
                    // 字段标签 — captionStrong（12/16/500）
                    Text(
                        text = stringResource(R.string.search_school),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.colorScheme.textSecondary
                    )

                    // 学校搜索输入框 — 与标签间距 24（spacedBy 16 + 上内边距 8）— 信息锁定后禁用编辑
                    SearchSchoolInput(
                        query = query,
                        interactionSource = interactionSource,
                        isSchoolError = isSchoolError,
                        isSearchFocused = isSearchFocused,
                        schoolList = schoolList,
                        selectedSchoolId = selectedSchoolId,
                        enabled = !isInfoLocked,
                        modifier = Modifier.padding(top = 8.dp),
                        itemClick = { school ->
                            selectedSchoolId = school.schoolId
                            query = school.schoolName
                            isSchoolError = false
                            // 先让选中高亮落地，再收焦点收起下拉（收起走 exit 动画）
                            dismissJob?.cancel()
                            dismissJob = scope.launch {
                                delay(SELECTION_SETTLE_MS)
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                isSearchFocused = false
                            }
                        },
                        clearOnClick = {
                            query = ""
                            userInfoViewModel.updateSearchName(query)
                        }
                    ) {
                        query = it
                        userInfoViewModel.updateSearchName(query)
                    }

                    // 学号标签 — 与上一输入框间距 24（spacedBy 16 + 上内边距 8）
                    Text(
                        text = stringResource(R.string.student_id),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.colorScheme.textSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    // 学号输入框 — 信息锁定后禁用编辑
                    OutlinedTextField(
                        value = studentId,
                        onValueChange = { studentId = it; isStudentIdError = false },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.student_id),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.colorScheme.textTertiary
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isInfoLocked,
                        isError = isStudentIdError,
                        supportingText = if (isStudentIdError) {
                            { Text(stringResource(R.string.invalid_student_id)) }
                        } else null,
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Badge,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = AppColors.colorScheme.primary
                            )
                        },
                        shape = RoundedCornerShape(AppDimens.radiusMd),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.colorScheme.primary,
                            unfocusedBorderColor = AppColors.colorScheme.outline,
                            errorBorderColor = AppColors.colorScheme.error,
                            focusedContainerColor = AppColors.colorScheme.surfaceVariant,
                            unfocusedContainerColor = AppColors.colorScheme.surfaceVariant,
                            disabledBorderColor = AppColors.colorScheme.outline,
                            disabledContainerColor = AppColors.colorScheme.surfaceVariant
                        )
                    )
                }
                }
            }
        }

        // 底部固定 CTA 栏（模板 C：提示文案 + 主按钮；键盘弹出经 imePadding 上移，不遮挡输入框）
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(AppColors.colorScheme.background)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = AppDimens.pagePadding)
        ) {
            Text(
                text = stringResource(R.string.school_info_locked_hint),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            AuthCtaButton(
                text = stringResource(R.string.confirm),
                onClick = {
                    isSchoolError = selectedSchoolId == -1L
                    isStudentIdError = studentId.isEmpty()
                    if (!isSchoolError && !isStudentIdError) {
                        keyboardController?.hide()
                        userInfoViewModel.updateUserInfo(selectedSchoolId, studentId)
                    }
                },
                loading = updateState is RequestState.Loading,
                enabled = !isInfoLocked
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        }
    }
}

@Composable
fun SearchSchoolInput(
    query: String,
    interactionSource: MutableInteractionSource,
    isSchoolError: Boolean,
    isSearchFocused: Boolean,
    schoolList: List<SchoolOption>,
    selectedSchoolId: Long,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    itemClick: (SchoolOption) -> Unit,
    clearOnClick: () -> Unit,
    onValueChange: (String) -> Unit
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_school),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textTertiary
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = enabled,
            isError = isSchoolError,
            supportingText = if (isSchoolError) {
                { Text(stringResource(R.string.please_select_school)) }
            } else null,
            leadingIcon = {
                Icon(
                    Icons.Rounded.School,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = AppColors.colorScheme.primary
                )
            },
            trailingIcon = {
                // 40dp 自绘清空按钮（M3 IconButton 自带 state layer/涟漪，规范 §7 禁用于可点击场景）
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .then(
                            if (enabled) Modifier.pressable(onClick = clearOnClick, debounce = false)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.clear),
                        modifier = Modifier.size(20.dp),
                        tint = AppColors.colorScheme.textSecondary
                    )
                }
            },
            shape = RoundedCornerShape(AppDimens.radiusMd),
            interactionSource = interactionSource,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.colorScheme.primary,
                unfocusedBorderColor = AppColors.colorScheme.outline,
                errorBorderColor = AppColors.colorScheme.error,
                focusedContainerColor = AppColors.colorScheme.surfaceVariant,
                unfocusedContainerColor = AppColors.colorScheme.surfaceVariant,
                disabledBorderColor = AppColors.colorScheme.outline,
                disabledContainerColor = AppColors.colorScheme.surfaceVariant
            )
        )

        // 下拉列表：展开/收起走弹簧 + 淡入，替代 0ms 出现/消失
        AnimatedVisibility(
            visible = isSearchFocused && schoolList.isNotEmpty(),
            enter = fadeIn(motionSpec(defaultSpring())) +
                expandVertically(motionSpec(defaultSpring())),
            exit = fadeOut(motionSpec(exitTween(EXIT_MICRO_MS))) +
                shrinkVertically(motionSpec(exitTween(EXIT_MICRO_MS))),
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                SchoolItem(schoolList, selectedSchoolId, itemClick)
            }
        }
    }
}

@Composable
fun SchoolItem(
    schoolList: List<SchoolOption>,
    selectedSchoolId: Long,
    onClick: (SchoolOption) -> Unit
) {
    // 规范 §3.1 标准卡片 + §3.4 列表行（56dp 行高、图标容器、发丝线）；选中项 brand-soft 高亮
    AppCard {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
        ) {
            items(schoolList, key = { it.schoolId }) { school ->
                val isSelected = school.schoolId == selectedSchoolId
                // 选中态背景/文字色统一走 selectedColor 过渡（reduced motion 降级为 180ms 纯颜色变化）
                val rowBackground = selectedColor(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
                val rowTextColor = selectedColor(
                    if (isSelected) AppColors.colorScheme.primaryDark else AppColors.colorScheme.textPrimary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(rowBackground)
                        .pressable(onClick = { onClick(school) })
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 字重固定为 Normal：选中只靠背景+文字色区分，避免字重跳变导致行内重排
                    Text(
                        text = school.schoolName,
                        fontSize = 12.sp,
                        color = rowTextColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = AppColors.colorScheme.hairline,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }
    }
}

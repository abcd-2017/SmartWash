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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.feature.user.impl.R
import com.smartwash.common.ui.components.AppCard
import com.smartwash.common.ui.components.AppTextField
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.components.AuthCtaButton
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.feature.laundry.api.model.SchoolOption
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.Primary
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable

// 退出时长 ≈ 进入（DURATION_MICRO_MS）的 70%（动效语言 v0.4）
private const val EXIT_MICRO_MS = 140

@Composable
fun UpdateUserInfoPage(
    navController: NavController,
    userInfoViewModel: UpdateUserInfoViewModel = hiltViewModel()
) {
    // 2026-11-19：saveable——搜索词/已选学校/学号构成多步中间态，返回后不得清空
    var selectedSchoolId by rememberSaveable { mutableLongStateOf(-1) }
    // 展示用校名（只读选择行的回填值）；搜索词只在 Sheet 内临时存在，选中才落回本字段
    var query by rememberSaveable { mutableStateOf("") }
    var studentId by rememberSaveable { mutableStateOf("") }
    var isSchoolError by remember { mutableStateOf(false) }
    var isStudentIdError by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    // 学校选择改为「只读选择行 + 底部 Sheet」：进页不再有可聚焦文本框，从源头消除
    // 自动拉起输入法与输入框/下拉同屏的问题（搜索能力收进 Sheet 内）
    var showSchoolSheet by rememberSaveable { mutableStateOf(false) }
    var sheetQuery by rememberSaveable { mutableStateOf("") }

    val schoolList by userInfoViewModel.schools.collectAsState()
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
                navController.navigate(ShellRoute.Home) {
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
            studentId = info.studentId.orEmpty()
            isSchoolError = false
            isStudentIdError = false
            isInfoLocked = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // IME 避让：adjustResize 不 resize（manifest 注释），输入框所在 LazyColumn 的视口
            // 必须由根容器消费 ime insets 才能整体抬到键盘上方——否则聚焦表单卡底部的学号框时
            // 输入内容被键盘遮挡且无法滚到键盘上方（对齐 ChangePasswordPage/ApiConfigPage 根
            // 容器写法；insets 经此消费后底部 CTA 栏自身的 imePadding 自动落为 0，不会叠加）
            .imePadding()
            // 规范 §7：裸 clickable 必须配套按压反馈——统一走 pressable（内置 indication=null + scale 0.97）。
            // 学号框聚焦时点空白处收键盘（学校行已不是文本框，不再需要为其清焦点）
            .pressable(onClick = {
                focusManager.clearFocus()
                keyboardController?.hide()
            })
            .background(AppColors.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            // 底部预留固定 CTA 栏高度（提示 16 + 间距 16 + 按钮 48 + 底距 16 + 余量），滚动内容不被按钮遮挡
            // 2026-11-01 阴影裁剪修复：水平边距并入 contentPadding
            contentPadding = PaddingValues(start = AppDimens.pagePadding, end = AppDimens.pagePadding, bottom = 120.dp),
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
                            // 页面标题跟随内页页头档 18/24/600（2026-11-18 统一，原 22sp 作废）
                            style = AppTextStyles.PageHeader,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = stringResource(R.string.fill_school_info),
                            // 画板实测副标题 16/24（bodyLarge 档），非 18/600
                            style = MaterialTheme.typography.bodyLarge,
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
                    modifier = Modifier.fillMaxWidth().softCardShadow(),
                    shape = RoundedCornerShape(AppDimens.radiusLg),
                    color = AppColors.colorScheme.surface,
                    border = BorderStroke(1.dp, AppColors.colorScheme.outline),
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMd)
                ) {
                    // 字段标签 — 画板实测 12/400（caption 档 bodySmall），非 captionStrong 500
                    Text(
                        text = stringResource(R.string.search_school),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary
                    )

                    // 学校选择行 — 与标签间距 24（spacedBy 16 + 上内边距 8）— 信息锁定后禁用
                    SchoolSelectorField(
                        query = query,
                        isError = isSchoolError,
                        enabled = !isInfoLocked,
                        onClick = { showSchoolSheet = true },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    AnimatedVisibility(
                        visible = isSchoolError,
                        enter = fadeIn(motionSpec(enterTween(DURATION_MICRO_MS))) +
                            expandVertically(motionSpec(enterTween(DURATION_MICRO_MS))),
                        exit = fadeOut(motionSpec(exitTween(EXIT_MICRO_MS))) +
                            shrinkVertically(motionSpec(exitTween(EXIT_MICRO_MS))),
                    ) {
                        Text(stringResource(R.string.please_select_school))
                    }

                    // 学号标签 — 与上一输入框间距 24（spacedBy 16 + 上内边距 8）
                    Text(
                        text = stringResource(R.string.student_id),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    // 学号输入框 — 信息锁定后禁用编辑
                    AppTextField(
                        value = studentId,
                        onValueChange = { studentId = it; isStudentIdError = false },
                        placeholder = stringResource(R.string.student_id),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isInfoLocked,
                        isError = isStudentIdError,
                        supportingText = {
                            AnimatedVisibility(
                                visible = isStudentIdError,
                                enter = fadeIn(motionSpec(enterTween(DURATION_MICRO_MS))) +
                                    expandVertically(motionSpec(enterTween(DURATION_MICRO_MS))),
                                exit = fadeOut(motionSpec(exitTween(EXIT_MICRO_MS))) +
                                    shrinkVertically(motionSpec(exitTween(EXIT_MICRO_MS))),
                            ) {
                                Text(stringResource(R.string.invalid_student_id))
                            }
                        },
                        leadingIcon = Icons.Rounded.Badge
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

    if (showSchoolSheet) {
        SchoolPickerSheet(
            sheetQuery = sheetQuery,
            schoolList = schoolList,
            selectedSchoolId = selectedSchoolId,
            onQueryChange = { sheetQuery = it; userInfoViewModel.updateSearchName(it) },
            onSelect = { school ->
                selectedSchoolId = school.schoolId
                query = school.schoolName
                isSchoolError = false
                // 与关闭 Sheet 同路径清空搜索词：下次打开回到全量列表，不残留上次的过滤条件
                sheetQuery = ""
                userInfoViewModel.updateSearchName("")
                showSchoolSheet = false
            },
            onDismiss = {
                // 关闭即丢弃搜索词，下次打开回到全量列表
                sheetQuery = ""
                userInfoViewModel.updateSearchName("")
                showSchoolSheet = false
            },
        )
    }
}

/**
 * 学校只读选择行 — 视觉与 [AppTextField] STANDARD 皮肤同款（surfaceVariant 底 + outline 描边 +
 * 14dp 圆角 + 前缀 16dp/.5 + 56dp 行高），但不是文本框：点击整行开 Sheet。
 *
 * 为什么不直接用 `AppTextField(readOnly=true)`：readOnly 仍可聚焦、会唤起输入法并显示光标，
 * 与「点击展开下拉」的语义相反；自绘只读行从根上没有输入连接，进页不会弹键盘。
 */
@Composable
private fun SchoolSelectorField(
    query: String,
    isError: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .then(if (enabled) Modifier.pressable(onClick = onClick, debounce = false) else Modifier),
        shape = RoundedCornerShape(AppDimens.inputRadius),
        color = AppColors.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (isError) AppColors.colorScheme.error else AppColors.colorScheme.outline
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.School,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = AppColors.colorScheme.textSecondary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            Text(
                text = query.ifBlank { stringResource(R.string.select_school) },
                style = MaterialTheme.typography.bodyLarge,
                color = if (query.isBlank()) {
                    AppColors.colorScheme.textSecondary
                } else {
                    AppColors.colorScheme.textPrimary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = AppColors.colorScheme.textSecondary
            )
        }
    }
}

/**
 * 学校选择底部 Sheet（对齐 PaymentPage 先例：surface 容器 + 自绘拖拽条 + 顶部圆角）。
 *
 * 搜索框在 Sheet 内部：只有用户主动点击选择行后才可能出键盘，且此时下拉列表本来就该出现，
 * 不存在「进页自动拉起输入法」与「输入框/下拉同屏非预期弹起」的冲突。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchoolPickerSheet(
    sheetQuery: String,
    schoolList: List<SchoolOption>,
    selectedSchoolId: Long,
    onQueryChange: (String) -> Unit,
    onSelect: (SchoolOption) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = AppDimens.radiusXl, topEnd = AppDimens.radiusXl),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = AppDimens.spaceSm)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(AppDimens.radiusFull))
                    .background(AppColors.colorScheme.outline)
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.pagePadding)
                .padding(bottom = AppDimens.pagePadding)
        ) {
            Text(
                text = stringResource(R.string.select_school),
                style = MaterialTheme.typography.headlineSmall,
                color = AppColors.colorScheme.textPrimary
            )

            Spacer(modifier = Modifier.height(AppDimens.spaceMd))

            AppTextField(
                value = sheetQuery,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.search_school),
                leadingIcon = Icons.Rounded.Search,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(AppDimens.spaceSm))

            if (schoolList.isEmpty()) {
                EmptyState(icon = Icons.Rounded.School, message = stringResource(R.string.school_not_found))
            } else {
                // 单卡承载整份列表（规范 §3.1：卡是容器，行不各自套卡）
                AppCard {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(schoolList, key = { it.schoolId }) { school ->
                            SchoolItem(school, selectedSchoolId == school.schoolId) { onSelect(school) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SchoolItem(
    school: SchoolOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // 规范 §3.4 列表行（56dp 行高、发丝线）；选中项 brand-soft 高亮。
    // 整份列表共用外层一张 AppCard（§3.1：卡是容器，行不各自套卡）
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
                .pressable(onClick = onClick, debounce = false)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 列表行主文案按规范 §3.4 取 14sp/500（titleSmall 档，原 12sp 低于规范）；
            // 字重固定，选中只靠背景+文字色区分，避免字重跳变导致行内重排
            Text(
                text = school.schoolName,
                style = MaterialTheme.typography.titleSmall,
                color = if (isSelected) {
                    AppColors.colorScheme.textGreenOnTint
                } else {
                    AppColors.colorScheme.textPrimary
                },
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = AppColors.colorScheme.textGreenOnTint
                )
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = AppColors.colorScheme.hairline,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
    }
}

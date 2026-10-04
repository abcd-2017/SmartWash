package com.smartwash.feature.user.impl.ui.update_userinfo

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppCard
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.feature.laundry.api.model.SchoolOption
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.model.RequestState

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

    val schoolList by userInfoViewModel.schools.collectAsState()
    val interactionSource = remember { MutableInteractionSource() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isSearchFocused by remember { mutableStateOf(false) }
    val updateState by userInfoViewModel.updateState.collectAsState()

    when (updateState) {
        is RequestState.Success -> {
            LaunchedEffect(Unit) {
                Toast.makeText(context, context.getString(R.string.modify_success), Toast.LENGTH_SHORT).show()
                userInfoViewModel.setStateIdle()
                navController.popBackStack()
                navController.navigate(ShellRoute.HOME)
            }
        }
        is RequestState.Error -> {
            Toast.makeText(context, (updateState as RequestState.Error).getMessage(context), Toast.LENGTH_SHORT).show()
            userInfoViewModel.setStateIdle()
        }
        else -> {}
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(40.dp))

                // 品牌区域 — Hero 卡（规范 §3.1 变体：品牌渐变底 + 白字）
                val brandGradient = Brush.linearGradient(
                    colors = listOf(AppColors.colorScheme.primary, AppColors.colorScheme.primaryDark)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppDimens.radiusXl))
                        .background(brandGradient)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 圆形图标容器（白色16%透明度）
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.School,
                                contentDescription = null,
                                modifier = Modifier.size(30.dp),
                                tint = Color.White
                            )
                        }
                        Text(
                            text = stringResource(R.string.complete_info),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.fill_school_info),
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                AppCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 学校标签
                        Text(
                            text = stringResource(R.string.search_school),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                            color = AppColors.colorScheme.textTertiary
                        )

                        // field3 样式输入框
                        SearchSchoolInput(
                            query = query,
                            interactionSource = interactionSource,
                            isSchoolError = isSchoolError,
                            isSearchFocused = isSearchFocused,
                            schoolList = schoolList,
                            itemClick = { school ->
                                selectedSchoolId = school.schoolId
                                query = school.schoolName
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                isSchoolError = false
                                isSearchFocused = false
                            },
                            clearOnClick = {
                                query = ""
                                userInfoViewModel.updateSearchName(query)
                            }
                        ) {
                            query = it
                            userInfoViewModel.updateSearchName(query)
                        }

                        // 学号标签
                        Text(
                            text = stringResource(R.string.student_id),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                            color = AppColors.colorScheme.textTertiary,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        // field3 样式输入框
                        OutlinedTextField(
                            value = studentId,
                            onValueChange = { studentId = it; isStudentIdError = false },
                            placeholder = { Text(stringResource(R.string.student_id)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = isStudentIdError,
                            supportingText = if (isStudentIdError) {
                                { Text(stringResource(R.string.invalid_student_id)) }
                            } else null,
                            leadingIcon = {
                                Icon(Icons.Rounded.Badge, contentDescription = null, tint = AppColors.colorScheme.primary)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.colorScheme.primary,
                                unfocusedBorderColor = AppColors.colorScheme.outline,
                                errorBorderColor = AppColors.colorScheme.error,
                                focusedContainerColor = AppColors.colorScheme.surfaceVariant,
                                unfocusedContainerColor = AppColors.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                AppButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        isSchoolError = selectedSchoolId == -1L
                        isStudentIdError = studentId.isEmpty()
                        if (!isSchoolError && !isStudentIdError) {
                            keyboardController?.hide()
                            userInfoViewModel.updateUserInfo(selectedSchoolId, studentId)
                        }
                    },
                    loading = updateState is RequestState.Loading
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "学校信息决定可用的柜机与配送网点",
                    fontSize = 11.sp,
                    color = AppColors.colorScheme.textTertiary
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
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
    itemClick: (SchoolOption) -> Unit,
    clearOnClick: () -> Unit,
    onValueChange: (String) -> Unit
) {
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onValueChange,
            placeholder = { Text(stringResource(R.string.search_school)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            isError = isSchoolError,
            supportingText = if (isSchoolError) {
                { Text(stringResource(R.string.please_select_school)) }
            } else null,
            leadingIcon = {
                Icon(Icons.Rounded.School, contentDescription = null, tint = AppColors.colorScheme.primary)
            },
            trailingIcon = {
                IconButton(onClick = clearOnClick) {
                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.clear), tint = AppColors.colorScheme.textSecondary)
                }
            },
            shape = RoundedCornerShape(14.dp),
            interactionSource = interactionSource,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.colorScheme.primary,
                unfocusedBorderColor = AppColors.colorScheme.outline,
                errorBorderColor = AppColors.colorScheme.error,
                focusedContainerColor = AppColors.colorScheme.surfaceVariant,
                unfocusedContainerColor = AppColors.colorScheme.surfaceVariant
            )
        )

        if (isSearchFocused && schoolList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            SchoolItem(schoolList, itemClick)
        }
    }
}

@Composable
fun SchoolItem(
    schoolList: List<SchoolOption>,
    onClick: (SchoolOption) -> Unit
) {
    // 规范 §3.1 标准卡片 + §3.4 列表行（56dp 行高、图标容器、发丝线）
    AppCard {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
        ) {
            items(schoolList, key = { it.schoolId }) { school ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { onClick(school) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = school.schoolName,
                        fontSize = 13.sp,
                        color = AppColors.colorScheme.textPrimary,
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

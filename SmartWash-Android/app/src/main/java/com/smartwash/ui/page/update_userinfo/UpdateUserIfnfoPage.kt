package com.smartwash.ui.page.userinfo

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.network.vo.SchoolName
import com.smartwash.ui.page.PageConstant
import com.smartwash.utils.RequestState

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
                Toast.makeText(context, "修改成功", Toast.LENGTH_SHORT).show()
                userInfoViewModel.setStateIdle()
                navController.popBackStack()
                navController.navigate(PageConstant.Home.text)
            }
        }

        is RequestState.Error -> {
            Toast.makeText(
                context,
                (updateState as RequestState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
            userInfoViewModel.setStateIdle()
        }

        else -> {}
    }

    //判断输入框是否获取焦点
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            if (it is FocusInteraction.Focus) {
                isSearchFocused = true
            } else if (it is FocusInteraction.Unfocus) {
                isSearchFocused = false
            }
        }
    }
    LaunchedEffect(Unit) {
        userInfoViewModel.searchSchool()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { focusManager.clearFocus() }
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(30.dp))

                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.School,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "完善信息",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "请填写您的学校信息",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(34.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
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

                        Spacer(modifier = Modifier.height(16.dp))

                        // 学号输入框
                        OutlinedTextField(
                            value = studentId,
                            onValueChange = {
                                studentId = it
                                isStudentIdError = false
                            },
                            label = { Text("学号") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = isStudentIdError,
                            supportingText = if (isStudentIdError) {
                                { Text("请输入正确的学号") }
                            } else null,
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Badge,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))

                UpdateUserInfoButton(updateState) {
                    isSchoolError = selectedSchoolId == -1L
                    isStudentIdError = studentId.isEmpty()

                    if (!isSchoolError && !isStudentIdError) {
                        keyboardController?.hide()
                        userInfoViewModel.updateUserInfo(selectedSchoolId, studentId)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

//学校名搜索框
@Composable
fun SearchSchoolInput(
    query: String,
    interactionSource: MutableInteractionSource,
    isSchoolError: Boolean,
    isSearchFocused: Boolean,
    schoolList: List<SchoolName>,
    itemClick: (SchoolName) -> Unit,
    clearOnClick: () -> Unit,
    onValueChange: (String) -> Unit
) {
    // 学校搜索框
    OutlinedTextField(
        value = query,
        onValueChange = onValueChange,
        label = { Text("搜索学校") },
        modifier = Modifier
            .fillMaxWidth(),
        isError = isSchoolError,
        supportingText = if (isSchoolError) {
            { Text("请选择学校") }
        } else null,
        leadingIcon = {
            Icon(
                Icons.Rounded.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingIcon = {
            IconButton(onClick = clearOnClick) {
                Icon(
                    Icons.Rounded.Clear,
                    contentDescription = "清除",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        shape = RoundedCornerShape(12.dp),
        interactionSource = interactionSource
    )

    // 学校列表
    if (isSearchFocused && schoolList.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        SchoolItem(schoolList, itemClick)
    }
}

@Composable
fun SchoolItem(
    schoolList: List<SchoolName>,
    onClick: (SchoolName) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            items(schoolList) { school ->
                ListItem(
                    headlineContent = { Text(text = school.schoolName) },
                    modifier = Modifier.clickable {
                        onClick(school)
                    }
                )
            }
        }
    }
}

@Composable
fun UpdateUserInfoButton(
    updateState: RequestState,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        when (updateState) {
            is RequestState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            else -> {
                Text(
                    "确认",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
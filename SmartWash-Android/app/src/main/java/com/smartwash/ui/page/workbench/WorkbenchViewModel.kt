package com.smartwash.ui.page.workbench

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.lifecycle.ViewModel
import com.smartwash.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 工作台页 ViewModel。
 * 工具列表当前为内置假数据（设计稿屏 15 定稿六项）；后端工作台接口设计完成后，
 * 接入点就在此处——把 [tools] 的数据源从内置列表换成接口返回，Page 层零改动。
 */
@HiltViewModel
class WorkbenchViewModel @Inject constructor() : ViewModel() {

    private val _tools = MutableStateFlow(defaultTools())
    val tools: StateFlow<List<WorkbenchTool>> = _tools.asStateFlow()

    companion object {
        // TODO(工作台接口): 接口就绪后移除内置列表，改为请求 /web 工作台配置（顺序、文案、启停由服务端下发）
        fun defaultTools(): List<WorkbenchTool> = listOf(
            WorkbenchTool(
                key = "laundry_calendar",
                titleRes = R.string.workbench_tool_calendar,
                descriptionRes = R.string.workbench_tool_calendar_desc,
                icon = Icons.Default.CalendarMonth,
                tint = WorkbenchTint.GREEN,
                action = WorkbenchAction.COMING_SOON,
            ),
            WorkbenchTool(
                key = "stain_recognition",
                titleRes = R.string.workbench_tool_stain,
                descriptionRes = R.string.workbench_tool_stain_desc,
                icon = Icons.Default.Search,
                tint = WorkbenchTint.BLUE,
                action = WorkbenchAction.COMING_SOON,
            ),
            WorkbenchTool(
                key = "fabric_encyclopedia",
                titleRes = R.string.workbench_tool_fabric,
                descriptionRes = R.string.workbench_tool_fabric_desc,
                icon = Icons.Default.MenuBook,
                tint = WorkbenchTint.TEAL,
                action = WorkbenchAction.COMING_SOON,
            ),
            WorkbenchTool(
                key = "cost_estimate",
                titleRes = R.string.workbench_tool_estimate,
                descriptionRes = R.string.workbench_tool_estimate_desc,
                icon = Icons.Default.Calculate,
                tint = WorkbenchTint.PURPLE,
                action = WorkbenchAction.COMING_SOON,
            ),
            WorkbenchTool(
                key = "my_history",
                titleRes = R.string.workbench_tool_history,
                descriptionRes = R.string.workbench_tool_history_desc,
                icon = Icons.Default.History,
                tint = WorkbenchTint.PURPLE,
                action = WorkbenchAction.DIV_HISTORY,
            ),
            WorkbenchTool(
                key = "ai_assistant",
                titleRes = R.string.workbench_tool_ai,
                descriptionRes = R.string.workbench_tool_ai_desc,
                icon = Icons.Default.ChatBubbleOutline,
                tint = WorkbenchTint.GREEN,
                action = WorkbenchAction.AI_ASSISTANT,
            ),
        )
    }
}

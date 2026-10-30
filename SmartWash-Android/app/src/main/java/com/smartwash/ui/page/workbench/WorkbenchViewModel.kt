package com.smartwash.ui.page.workbench

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.deeplink.DeepLinkAction
import com.smartwash.feature.deeplink.DeepLinkHosts
import com.smartwash.feature.deeplink.DeepLinkParser
import com.smartwash.feature.deeplink.handler.DeepLinkHandler
import com.smartwash.repository.WorkbenchEntryRepository
import com.smartwash.network.api.WorkbenchEntryVo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 工作台页 ViewModel。
 *
 * 数据流：init 拉取 `GET /web/auth/workbench/entries`（经 [WorkbenchEntryRepository]）——
 * Success 且非空 → 服务端下发列表映射 [WorkbenchTool]（icon/color 白名单枚举映射，
 * 未知值兜底）；失败或空列表 → 本地兜底列表（设计稿屏 15 六项 + 短链工具箱）。
 * 数据源状态用 [RequestState] 暴露给页面做骨架屏。
 *
 * 点击分发：[onToolClick] 经 [DeepLinkHandler.resolve] 解析 target（挂起，可能查后端
 * 字典兜底），解析结果经 [navigateAction] 一次性回流给页面调 [navigate] 出栈跳转。
 * target 为空的兜底项由页面直接提示「功能建设中」，不进深链链路。
 */
@HiltViewModel
class WorkbenchViewModel @Inject constructor(
    // 兜底列表标题/描述经 application.getString 解析（规则：ViewModel 内不持 res id 进 UI 层）
    @ApplicationContext private val appContext: Context,
    private val workbenchEntryRepository: WorkbenchEntryRepository,
    private val deepLinkHandler: DeepLinkHandler,
) : ViewModel() {

    private val _tools = MutableStateFlow<List<WorkbenchTool>>(emptyList())
    val tools: StateFlow<List<WorkbenchTool>> = _tools.asStateFlow()

    /** 数据源状态（Idle/Loading/Success/Error）：Loading 且列表为空时页面展示骨架屏 */
    private val _entriesState = MutableStateFlow<RequestState>(RequestState.Idle)
    val entriesState: StateFlow<RequestState> = _entriesState.asStateFlow()

    /** 深链解析结果一次性回流（点击 → resolve → 页面 navigate），extraBuffer 兜连点 */
    private val _navigateAction = MutableSharedFlow<DeepLinkAction>(extraBufferCapacity = 4)
    val navigateAction: SharedFlow<DeepLinkAction> = _navigateAction.asSharedFlow()

    init {
        loadEntries()
    }

    /** 拉取服务端入口配置；失败或空列表走本地兜底（静默降级，tab 根页不打断用户） */
    fun loadEntries() {
        if (_entriesState.value is RequestState.Loading) return
        viewModelScope.launch {
            _entriesState.value = RequestState.Loading
            try {
                val entries = workbenchEntryRepository.getEntries()
                if (entries.isEmpty()) {
                    // 服务端未配置：视为成功但走兜底列表（不显示错误态）
                    _tools.value = defaultTools()
                    _entriesState.value = RequestState.Success
                } else {
                    _tools.value = entries.mapIndexed { index, vo -> vo.toTool(index) }
                        .withUniqueKeys()
                    _entriesState.value = RequestState.Success
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 失败：本地兜底 + Error 态留痕（页面静默消费，不弹错——兜底数据已可用）
                _tools.value = defaultTools()
                _entriesState.value = RequestState.Error(R.string.workbench_entries_fallback)
            }
        }
    }

    /**
     * 工具点击分发：resolve 为挂起调用（未命中本地注册表时会查后端字典），统一进
     * viewModelScope；结果经 [navigateAction] 回流页面，导航动作由页面出栈执行。
     */
    fun onToolClick(tool: WorkbenchTool) {
        if (tool.target.isBlank()) return // 功能建设中：页面侧直接提示，不进深链链路
        viewModelScope.launch {
            try {
                _navigateAction.emit(deepLinkHandler.resolve(tool.target))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _navigateAction.emit(DeepLinkAction.Unknown(tool.target))
            }
        }
    }

    /** 导航委托（DeepLinkHandler.navigate：NativeRoute/WebViewUrl/Unknown 三态分发） */
    fun navigate(navController: NavHostController, action: DeepLinkAction) {
        deepLinkHandler.navigate(navController, action)
    }

    // ---------- 映射与兜底 ----------

    /**
     * entryKey 稳定性防御：服务端配置理论上唯一（业务唯一标识），但网格按 entryKey
     * 作 LazyColumn key，重复会直接崩溃——重复时追加序号去重，宁可展示也别崩。
     */
    private fun List<WorkbenchTool>.withUniqueKeys(): List<WorkbenchTool> {
        val seen = mutableMapOf<String, Int>()
        return map { tool ->
            val count = seen.merge(tool.entryKey, 1, Int::plus) ?: 1
            if (count == 1) tool else tool.copy(entryKey = "${tool.entryKey}_$count")
        }
    }

    /** 后端入口 VO → 工具卡模型：entryKey 兼作列表稳定 key（空值防御回退 index key） */
    private fun WorkbenchEntryVo.toTool(index: Int): WorkbenchTool = WorkbenchTool(
        entryKey = entryKey.takeIf { it.isNotBlank() } ?: "entry_$index",
        title = title,
        description = description,
        icon = workbenchIconFromKey(icon),
        tint = WorkbenchTint.fromKey(color),
        target = target.orEmpty(),
        badge = badge?.takeIf { it.isNotBlank() },
    )

    /**
     * 本地兜底列表（设计稿屏 15 六项 + 短链工具箱）。
     * 服务端入口配置与端上兜底按 entryKey 对齐（后端 WorkbenchEntryVo 注释约定）；
     * 跳转目标全部走深链（host 清单见 :feature:deeplink 的 DeepLinkHosts），
     * target 空 = 功能建设中。
     */
    private fun defaultTools(): List<WorkbenchTool> {
        val context = appContext
        val scheme = DeepLinkParser.SCHEME
        return listOf(
            WorkbenchTool(
                entryKey = "laundry_calendar",
                title = context.getString(R.string.workbench_tool_calendar),
                description = context.getString(R.string.workbench_tool_calendar_desc),
                icon = workbenchIconFromKey("calendar"),
                tint = WorkbenchTint.GREEN,
                target = "", // 洗衣黄历：功能建设中
            ),
            WorkbenchTool(
                entryKey = "stain_recognition",
                title = context.getString(R.string.workbench_tool_stain),
                description = context.getString(R.string.workbench_tool_stain_desc),
                icon = workbenchIconFromKey("search"),
                tint = WorkbenchTint.BLUE,
                target = "", // 渍迹识别：功能建设中
            ),
            WorkbenchTool(
                entryKey = "fabric_encyclopedia",
                title = context.getString(R.string.workbench_tool_fabric),
                description = context.getString(R.string.workbench_tool_fabric_desc),
                icon = workbenchIconFromKey("book"),
                tint = WorkbenchTint.GREEN,
                target = "", // 面料百科：功能建设中
            ),
            WorkbenchTool(
                entryKey = "cost_estimate",
                title = context.getString(R.string.workbench_tool_estimate),
                description = context.getString(R.string.workbench_tool_estimate_desc),
                icon = workbenchIconFromKey("calculate"),
                tint = WorkbenchTint.PURPLE,
                target = "", // 费用估算：功能建设中
            ),
            WorkbenchTool(
                entryKey = "my_history",
                title = context.getString(R.string.workbench_tool_history),
                description = context.getString(R.string.workbench_tool_history_desc),
                icon = workbenchIconFromKey("history"),
                tint = WorkbenchTint.PURPLE,
                target = "$scheme://${DeepLinkHosts.DIVINATION_HISTORY}",
            ),
            WorkbenchTool(
                entryKey = "ai_assistant",
                title = context.getString(R.string.workbench_tool_ai),
                description = context.getString(R.string.workbench_tool_ai_desc),
                icon = workbenchIconFromKey("chat"),
                tint = WorkbenchTint.BLUE,
                target = "$scheme://${DeepLinkHosts.AI_WORK}",
            ),
            WorkbenchTool(
                entryKey = "toolbox_short_code",
                title = context.getString(R.string.workbench_tool_short_code),
                description = context.getString(R.string.workbench_tool_short_code_desc),
                icon = workbenchIconFromKey("link"),
                tint = WorkbenchTint.BLUE,
                target = "$scheme://${DeepLinkHosts.TOOLBOX_SHORT_CODE}",
            ),
        )
    }
}

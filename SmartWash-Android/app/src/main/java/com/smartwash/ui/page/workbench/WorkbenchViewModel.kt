package com.smartwash.ui.page.workbench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.common.utils.deeplink.DeepLinkAction
import com.smartwash.common.utils.model.RequestState
import com.smartwash.deeplink.DeepLinkHandler
import com.smartwash.repository.WorkbenchEntryRepository
import com.smartwash.network.api.WorkbenchEntryVo
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * Success → 服务端下发列表映射 [WorkbenchTool]（icon/color 白名单枚举映射，未知值兜底）。
 * 2026-11-01 缺陷 6：移除本地兜底假列表（洗衣黄历等 7 项静态工具），失败给真实错误态 +
 * 重试、空配置给真实空态，工具清单完全由后端 workbench_entry 表下发。
 *
 * 点击分发：[onToolClick] 经 [DeepLinkHandler.resolve] 解析 target（挂起，可能查后端
 * 字典兜底），解析结果经 [navigateAction] 一次性回流给页面调 [navigate] 出栈跳转。
 * target 为空的占位项由页面直接提示「功能建设中」，不进深链链路。
 */
@HiltViewModel
class WorkbenchViewModel @Inject constructor(
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

    /** 拉取服务端入口配置：成功（含空配置）→ Success；失败 → 真实 Error 态，页面出重试 */
    fun loadEntries() {
        if (_entriesState.value is RequestState.Loading) return
        viewModelScope.launch {
            _entriesState.value = RequestState.Loading
            try {
                val entries = workbenchEntryRepository.getEntries()
                _tools.value = entries.mapIndexed { index, vo -> vo.toTool(index) }
                    .withUniqueKeys()
                _entriesState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _tools.value = emptyList()
                _entriesState.value = RequestState.Error(R.string.workbench_entries_error)
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
}

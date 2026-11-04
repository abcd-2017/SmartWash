package com.smartwash.ui.page.workbench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.deeplink.DeepLinkAction
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.SharePreferenceUtils
import com.smartwash.deeplink.DeepLinkHandler
import com.smartwash.repository.WorkbenchEntryRepository
import com.smartwash.network.api.WorkbenchEntryVo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 工作台页 ViewModel。
 *
 * 数据流：init 拉取 `GET /web/auth/workbench/entries`（经 [WorkbenchEntryRepository]）——
 * Success → 服务端下发列表映射 [WorkbenchTool]（icon/color/type 白名单枚举映射，未知值兜底）。
 * 2026-11-01 缺陷 6：移除本地兜底假列表（洗衣黄历等 7 项静态工具），失败给真实错误态 +
 * 重试、空配置给真实空态，工具清单完全由后端 workbench_entry 表下发。
 *
 * 端上自闭环能力（2026-11-04 类型化组件批，零契约改动）：
 * - 搜索：[onSearchChange] 本地过滤 visibleTools（title/description 包含匹配，大小写不敏感）；
 * - 最近使用：点击时经 [recordRecent] 记入 DataStore（新的在前，去重封顶 [RECENT_MAX] 条），
 *   与当次下发列表求交集后供页面渲染「最近使用」区；
 * - 公告关闭：type=notice 条目可关闭，关闭记录落 DataStore，visibleTools 永久过滤该条。
 *
 * 点击分发：[onToolClick] 经 [DeepLinkHandler.resolve] 解析 target（挂起，可能查后端
 * 字典兜底），解析结果经 [navigateAction] 一次性回流给页面调 [navigate] 出栈跳转。
 * target 为空的占位项由页面直接提示「功能建设中」，不进深链链路。
 */
@HiltViewModel
class WorkbenchViewModel @Inject constructor(
    private val workbenchEntryRepository: WorkbenchEntryRepository,
    private val deepLinkHandler: DeepLinkHandler,
    private val prefs: SharePreferenceUtils,
) : ViewModel() {

    private val _tools = MutableStateFlow<List<WorkbenchTool>>(emptyList())
    val tools: StateFlow<List<WorkbenchTool>> = _tools.asStateFlow()

    /** 数据源状态（Idle/Loading/Success/Error）：Loading 且列表为空时页面展示骨架屏 */
    private val _entriesState = MutableStateFlow<RequestState>(RequestState.Idle)
    val entriesState: StateFlow<RequestState> = _entriesState.asStateFlow()

    /** 搜索关键词（空 = 非搜索态，页面回到分组/类型排版） */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 最近使用 entryKey（本地记忆，新的在前，仅存 key——展示与当次下发列表求交集） */
    private val _recentKeys = MutableStateFlow<List<String>>(emptyList())

    /** 已关闭公告 entryKey 集合（关闭即永久过滤，下拉刷新/重装恢复） */
    private val _dismissedNotices = MutableStateFlow<Set<String>>(emptySet())

    /** 展示列表 = 下发列表 − 已关闭公告 − 搜索过滤（搜索态下页面退化为平铺网格） */
    val visibleTools: StateFlow<List<WorkbenchTool>> =
        combine(_tools, _searchQuery, _dismissedNotices) { tools, query, dismissed ->
            val base = tools.filterNot { it.layout == WorkbenchLayout.NOTICE && it.entryKey in dismissed }
            if (query.isBlank()) {
                base
            } else {
                base.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 最近使用 = 本地记忆 key 按序映射回当次下发列表（已下线的入口自动消失） */
    val recentTools: StateFlow<List<WorkbenchTool>> =
        combine(_recentKeys, _tools) { keys, tools ->
            keys.mapNotNull { key -> tools.firstOrNull { it.entryKey == key } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 深链解析结果一次性回流（点击 → resolve → 页面 navigate），extraBuffer 兜连点 */
    private val _navigateAction = MutableSharedFlow<DeepLinkAction>(extraBufferCapacity = 4)
    val navigateAction: SharedFlow<DeepLinkAction> = _navigateAction.asSharedFlow()

    init {
        loadEntries()
        // 最近使用/公告关闭记录异步回读（suspend 读 DataStore，禁 runBlocking）
        viewModelScope.launch {
            _recentKeys.value = prefs.getData(KEY_RECENT_ENTRIES, "")
                .split(',').filter { it.isNotBlank() }
            _dismissedNotices.value = prefs.getData(KEY_DISMISSED_NOTICES, "")
                .split(',').filter { it.isNotBlank() }.toSet()
        }
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
                if (_tools.value.isEmpty()) {
                    // 空态预警：服务端 workbench_entry 未下发任何条目（新端上线/配置清空的可见信号）
                    AppLogger.warn(TAG, "工作台入口配置为空（服务端未下发条目），页面展示空态")
                }
                _entriesState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "WorkbenchViewModel.loadEntries 失败，工作台降级错误态", e)
                _tools.value = emptyList()
                _entriesState.value = RequestState.Error(R.string.workbench_entries_error)
            }
        }
    }

    /** 搜索关键词回写（页面搜索框直连） */
    fun onSearchChange(query: String) {
        _searchQuery.value = query
    }

    /**
     * 工具点击分发：先记最近使用，再 resolve 为挂起调用（未命中本地注册表时会查后端
     * 字典兜底），统一进 viewModelScope；结果经 [navigateAction] 回流页面，导航动作由
     * 页面出栈执行。
     */
    fun onToolClick(tool: WorkbenchTool) {
        if (tool.target.isBlank()) return // 功能建设中：页面侧直接提示，不进深链链路
        recordRecent(tool.entryKey)
        viewModelScope.launch {
            try {
                _navigateAction.emit(deepLinkHandler.resolve(tool.target))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "WorkbenchViewModel.onToolClick 深链解析异常，降级 Unknown：entryKey=${tool.entryKey}", e)
                _navigateAction.emit(DeepLinkAction.Unknown(tool.target))
            }
        }
    }

    /** 关闭公告条：内存集合即时生效 + DataStore 持久化（下次启动仍过滤） */
    fun dismissNotice(tool: WorkbenchTool) {
        viewModelScope.launch {
            _dismissedNotices.value = _dismissedNotices.value + tool.entryKey
            prefs.saveData(KEY_DISMISSED_NOTICES, _dismissedNotices.value.joinToString(","))
        }
    }

    /** 导航委托（DeepLinkHandler.navigate：NativeRoute/WebViewUrl/Unknown 三态分发） */
    fun navigate(navController: NavHostController, action: DeepLinkAction) {
        deepLinkHandler.navigate(navController, action)
    }

    /** 记录最近使用：新的在前、去重、封顶 [RECENT_MAX]，异步落 DataStore */
    private fun recordRecent(entryKey: String) {
        viewModelScope.launch {
            val updated = (listOf(entryKey) + _recentKeys.value.filter { it != entryKey })
                .take(RECENT_MAX)
            _recentKeys.value = updated
            prefs.saveData(KEY_RECENT_ENTRIES, updated.joinToString(","))
        }
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
            if (count == 1) {
                tool
            } else {
                // 数据异常预警：entryKey 本应服务端唯一，重复即配置错误
                AppLogger.warn(TAG, "工作台入口 entryKey 重复，追加序号去重防崩溃：entryKey=${tool.entryKey} 出现 $count 次")
                tool.copy(entryKey = "${tool.entryKey}_$count")
            }
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
        layout = WorkbenchLayout.fromKey(type),
        sectionTitle = sectionTitle?.takeIf { it.isNotBlank() },
        imageUrl = imageUrl?.takeIf { it.isNotBlank() },
    )

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "WorkbenchViewModel"

        /** DataStore key：最近使用入口（entryKey 逗号连接，新的在前） */
        private const val KEY_RECENT_ENTRIES = "workbench_recent_entries"

        /** DataStore key：已关闭公告（entryKey 逗号连接） */
        private const val KEY_DISMISSED_NOTICES = "workbench_dismissed_notices"

        /** 最近使用保留条数（一屏 LazyRow 可见量级，防无限增长） */
        private const val RECENT_MAX = 6
    }
}

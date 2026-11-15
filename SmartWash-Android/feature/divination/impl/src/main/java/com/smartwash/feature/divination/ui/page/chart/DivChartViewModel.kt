package com.smartwash.feature.divination.ui.page.chart

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.divination.core.liuyao.LiuYaoChart
import com.smartwash.feature.divination.core.liuyao.LiuYaoFacts
import com.smartwash.feature.divination.data.DivChartBundle
import com.smartwash.feature.divination.data.DivChartCodec
import com.smartwash.feature.divination.database.DivRecordEntity
import com.smartwash.feature.divination.data.DivRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DivChartUiState(
    val record: DivRecordEntity? = null,
    val bundle: DivChartBundle? = null,
    val liuYaoFacts: LiuYaoFacts? = null,
    val loading: Boolean = true,
)

/**
 * 「解 读」入口拦截结果（2026-11-11 平台零兜底契约）：点击先查用户解读模型配置。
 */
enum class InterpretGate {
    /** 已配置且平台开关开启 → 正常进入解读页 */
    PROCEED,

    /** 未配置（data=null 或 apiKeyMask 空）且开关开启 → Toast 引导 + 跳设置页直达弹层 */
    NEED_CONFIG,

    /** 平台总开关关闭（divUseEnabled=0）→ Toast 提示暂未开放，不跳转 */
    PLATFORM_DISABLED,
}

/**
 * 卦盘页 VM：从 Room 卦历读原盘原时刻（复盘/追问沿用首卦 chart JSON，不重算）。
 *
 * 2026-11-11 两处加固：
 * - init 的 core 算法调用（LiuYaoFacts.of）加异常兜底——内核对异常盘面抛错时不让
 *   异常逃逸 viewModelScope 线程裸崩，降级为事实卡空态（盘面本体照常渲染）；
 * - 「解 读」入口前置拦截（[resolveInterpretGate]）：先查用户模型配置，未配置或
 *   平台总开关关闭时引导/提示，不再盲进解读页吃 428/403 错误卡。
 */
@HiltViewModel
class DivChartViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DivRecordRepository,
) : ViewModel() {

    private val recordId: Long = savedStateHandle.get<Long>("recordId") ?: -1L

    private val _uiState = MutableStateFlow(DivChartUiState())
    val uiState: StateFlow<DivChartUiState> = _uiState.asStateFlow()

    /** 拦截查询防重入标记（双击/连点只放行首个在途查询） */
    private val gateInFlight = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            val record = repository.getById(recordId)
            if (record == null) {
                AppLogger.warn(TAG, "卦盘记录不存在 recordId=$recordId，页面按空态兜底")
            }
            val bundle = record?.let { DivChartCodec.decode(it.chartJson) }
            if (record != null && bundle == null) {
                AppLogger.warn(TAG, "卦盘 chart JSON 解码失败 recordId=$recordId，页面按空态兜底")
            }
            val liuYao = bundle?.liuyao
            _uiState.value = DivChartUiState(
                record = record,
                bundle = bundle,
                // 内核异常兜底（crash 修复）：LiuYaoFacts.of 对异常盘面会抛错，降级为事实卡
                // 空态（用神/规则事实显示「—」），不让异常逃逸协程裸崩；算法逻辑本身不动
                liuYaoFacts = liuYao?.let { chart ->
                    runCatching {
                        LiuYaoFacts.of(
                            chart,
                            com.smartwash.feature.divination.core.DivCategory.fromId(record.category),
                        )
                    }.onFailure {
                        AppLogger.warn(TAG, "六爻规则事实计算失败 recordId=$recordId，按空态兜底", it)
                    }.getOrNull()
                },
                loading = false,
            )
        }
    }

    /**
     * 「解 读」入口拦截（挂起，页面点击回调内调用）：先查 GET /web/auth/div/settings/api，
     * 未配置且开关开启 → NEED_CONFIG；开关关闭（divUseEnabled=0，缺失视为开启）→
     * PLATFORM_DISABLED；配置查询失败放行（宁见 SSE 428 错误卡，不误拦已配置用户）。
     *
     * @return 拦截结果；null = 上一次查询仍在途（防重入，忽略本次点击）
     */
    suspend fun resolveInterpretGate(): InterpretGate? {
        if (!gateInFlight.compareAndSet(false, true)) return null
        try {
            val result = repository.getUserApiConfig()
            // 查询失败（网络抖动等）：放行走解读页 SSE 兜底文案
            val config = result.getOrNull()
            if (result.isFailure) return InterpretGate.PROCEED
            // 平台总开关关闭优先（无论是否已配置，解读均不可用）
            if (config?.divUseEnabled == 0) return InterpretGate.PLATFORM_DISABLED
            // 未配置（data=null 或 apiKeyMask 空）→ 引导配置
            return if (config == null || config.apiKeyMask.isNullOrBlank()) {
                InterpretGate.NEED_CONFIG
            } else {
                InterpretGate.PROCEED
            }
        } finally {
            gateInFlight.set(false)
        }
    }

    /** 爻值文本（解读/追问 packet 复用） */
    fun linesText(): String = _uiState.value.record?.lines ?: "[]"

    companion object {
        private const val TAG = "DivChartViewModel"

        fun decode(json: String): DivChartBundle? = DivChartCodec.decode(json)
        fun extractLiuYao(bundle: DivChartBundle): LiuYaoChart? = bundle.liuyao
    }
}

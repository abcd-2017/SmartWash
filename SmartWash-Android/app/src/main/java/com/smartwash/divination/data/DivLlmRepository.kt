package com.smartwash.divination.data

import com.smartwash.divination.core.DivCategory
import com.smartwash.divination.core.DivMethod
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 解读结果（UI 层模型）。本期 Mock 文本，后端就绪后换 DivLlmRepositoryImpl 真连。
 */
data class DivReading(
    val summary: String,
    val sections: List<DivReadingSection>,
    val action: String,
)

data class DivReadingSection(
    val title: String,
    val content: String,
)

/** 追问单轮 */
data class DivFollowUpTurn(
    val role: String,   // "user" / "assistant"
    val content: String,
)

/**
 * 观象台解读仓库（Mock 实现）。
 *
 * 当前返回内置 Mock 解读文本（后端 LLM 网关就绪后，替换为 DivinationApi 真连实现）。
 * 本期方法直接返回数据，异常即失败；VM 侧统一用 [com.smartwash.utils.RequestState] 管理网络态。
 */
@Singleton
class DivLlmRepository @Inject constructor() {

    /** 模拟解读延迟（让 loading 态可感知） */
    private val mockDelayMs = 600L

    /**
     * 解读（Mock）：按术数与问题领域返回内置解读文本。
     * 后端就绪后切到 DivinationApi.interpret 真连。
     */
    suspend fun interpret(
        method: DivMethod,
        category: DivCategory,
        question: String,
        chartLine: String,
    ): DivReading {
        delay(mockDelayMs)
        return buildMockReading(method, category, question, chartLine)
    }

    /**
     * 追问（Mock）：沿用原盘，返回内置追问回复。
     * 后端就绪后切到 DivinationApi.followUp 真连。
     */
    suspend fun followUp(
        method: DivMethod,
        question: String,
        history: List<DivFollowUpTurn>,
    ): String {
        delay(mockDelayMs)
        return buildMockFollowUp(method, question, history)
    }

    // ---- Mock 文本生成 ----

    private fun buildMockReading(
        method: DivMethod,
        category: DivCategory,
        question: String,
        chartLine: String,
    ): DivReading {
        val categoryLabel = categoryLabel(category)
        val q = question.ifEmpty { "所问之事" }

        val summary = when (method) {
            DivMethod.LIU_YAO -> "本卦六爻排成，世应分明；所问「$q」落在${categoryLabel}，用神可取，动象已见。"
            DivMethod.MEI_HUA -> "梅花以时起卦，体用已分；所问「$q」属${categoryLabel}，卦象所示，吉凶可见端倪。"
            DivMethod.QI_MEN -> "奇门起局完成，九宫排定；所问「$q」在${categoryLabel}之域，方位时令皆有端倪。"
            DivMethod.LIU_REN -> "六壬课成三传，人事占验最精；所问「$q」系${categoryLabel}，四课三传已示机微。"
        }

        val sections = when (method) {
            DivMethod.LIU_YAO -> listOf(
                DivReadingSection(
                    "直接判断",
                    "所问${categoryLabel}之事，倾向有利，但成于出空之后，眼下仍需蓄力等待，非立见分晓之象。",
                ),
                DivReadingSection(
                    "用神与世应",
                    "取用神对照世爻，世持官鬼，官星旺相，主此求于你为正路；应爻相生，外部环境尚可。",
                ),
                DivReadingSection(
                    "关键动变",
                    "动爻发动，化出回头生——先行者先耗而后散，动爻落定之期，正是转机。",
                ),
                DivReadingSection(
                    "应期",
                    "逢值逢合，关注旬末与出空前后两周窗口，届时事态明朗。",
                ),
            )

            DivMethod.MEI_HUA -> listOf(
                DivReadingSection(
                    "直接判断",
                    "体用生克已分，用克体则事稍阻，体生用则耗心力；所问「$q」需以静待时，勿急进。",
                ),
                DivReadingSection(
                    "本互变卦",
                    "本卦示现状，互卦示过程，变卦之象为归结——三卦合参，脉络自明。",
                ),
                DivReadingSection(
                    "体用生克",
                    "体卦静而用卦动，体用相克之处即事之关隘；调其失衡，方得其用。",
                ),
                DivReadingSection(
                    "应期",
                    "卦数合时，关注动爻对应之期，近则数日，远则数周。",
                ),
            )

            DivMethod.QI_MEN -> listOf(
                DivReadingSection(
                    "直接判断",
                    "局象已成，值符值使落宫既定；所问${categoryLabel}之事，择时择方皆有法度。",
                ),
                DivReadingSection(
                    "用神宫位",
                    "用神落宫得令则吉，失令则宜静待；宫位旺衰为断事根本。",
                ),
                DivReadingSection(
                    "空亡驿马",
                    "空亡落宫事宜缓，驿马动处利出行——趋避有道，不拘一时。",
                ),
                DivReadingSection(
                    "应期",
                    "值使落宫之数应期，近则当日，远则合数之期。",
                ),
            )

            DivMethod.LIU_REN -> listOf(
                DivReadingSection(
                    "直接判断",
                    "四课三传已定，课体九宗门可辨；所问${categoryLabel}，机微尽在初传之中。",
                ),
                DivReadingSection(
                    "四课辨析",
                    "日干支上神为四课，阴阳相配之处即事之发端；四课无克则审三传。",
                ),
                DivReadingSection(
                    "三传走势",
                    "初传为事之中，中传为事之转，末传为事之归——三传连珠，脉络自现。",
                ),
                DivReadingSection(
                    "应期",
                    "初传落支应期，逢合逢冲为验，关注传中之支对应时日。",
                ),
            )
        }

        val action = when (method) {
            DivMethod.LIU_YAO -> "此问偏吉，成事在出空之期。近两周集中打磨准备，窗口一到即刻行动。"
            DivMethod.MEI_HUA -> "以静待时为上，勿强求近利。关注动爻应期，届时顺势而为。"
            DivMethod.QI_MEN -> "择时择方，趋吉避凶。用神得令则动，失令则守，不拘于一时一地。"
            DivMethod.LIU_REN -> "人事占验，机在三传。初传落定后顺势而为，勿逆课体而行。"
        }

        return DivReading(summary = summary, sections = sections, action = action)
    }

    private fun buildMockFollowUp(
        method: DivMethod,
        question: String,
        history: List<DivFollowUpTurn>,
    ): String {
        val q = question.ifEmpty { "追问" }
        val turnCount = history.count { it.role == "user" }
        return when {
            turnCount <= 1 -> "仍以原盘为准：所问「$q」，用神得令则可行，旬空未出则宜缓。建议先观后动，待出空窗口再做定夺。"
            else -> "综合本卦与前两轮追问，「$q」之机象渐明——体用不争则事可成，三传不逆则行无碍。时机到时，当断则断。"
        }
    }

    private fun categoryLabel(category: DivCategory): String = when (category) {
        DivCategory.CAREER -> "事业"
        DivCategory.WEALTH -> "财运"
        DivCategory.LOVE -> "感情"
        DivCategory.STUDY -> "学业"
        DivCategory.TRAVEL -> "出行"
        DivCategory.OTHER -> "综合"
    }
}

/**
 * 观象台解读/追问仓库（#80 真连）—— 对齐安卓同票 #78 的 DivLlmRepository 定位。
 *
 * 职责：
 * - 调度 SSE 通道（DivinationApi.streamInterpret / streamFollowUp），
 *   delta 帧在此累积，向 UI 回调「累积后的全文」（流式逐帧刷新 / 攒流一次性整段）；
 * - done 帧存 interpretationId（供追问与反馈）；
 * - error 帧映射 UI 文案（428 未配置模型 / 403 安全分流·平台关闭双语义 / 429 / 503 / 404）；
 * - Mock 回退：USE_MOCK_LLM 常量开关切回本地模板文本（分小段定时回调模拟流式），
 *   与真连同构（同回调、同 Markdown 结构），页面零改动。
 *
 * 铁律（沿袭 DivinationStore 文件头）：沿用原盘原 castAt，不重算；
 * 追问只发当前问题，历史解读上下文由服务端按 recordId 拼接。
 */

import { streamFollowUp, streamInterpret } from '../network/DivinationApi'
import { DivSseCallbacks } from '../network/DivSseClient'
import { logger } from '../../../utils/Logger'

/**
 * ★ Mock 回退开关：true = 不发网络，返回内置模板解读（回归 #80 之前的行为）；
 *   false = 真连后端 SSE。默认真连。
 */
const USE_MOCK_LLM: boolean = false

/** Mock 模式查询（页面据此跳过服务端建档，保证 Mock 回归完全离线、行为与 #80 之前一致） */
export function isLlmMock(): boolean {
  return USE_MOCK_LLM
}

/** Mock 模式模拟流式的分段间隔（毫秒） */
const MOCK_STEP_MS: number = 220

const TAG: string = 'divLlmRepository'

/** 解读/追问仓库回调（onAccumulated 携带累积全文；done/error 终态） */
export interface DivLlmCallbacks {
  /** 累积全文回调：流式逐帧触发，攒流时一次性触发（UI 直接渲染该文本） */
  onAccumulated: (fullMarkdown: string) => void
  /** 完成帧：interpretationId 供追问/反馈（Mock 为 0）；cached 为服务端缓存命中 */
  onDone: (interpretationId: number, cached: boolean) => void
  /** 业务 error 帧（文案映射后的用户可见文案 + 原始码） */
  onSseError: (code: number, message: string) => void
  /** 传输层失败（含 401 已处理登录态后的收尾） */
  onFail: (message: string) => void
}

/** 428 固定文案（用户未配置解读模型；观星台零兜底，无平台默认模型可回落） */
export const SSE_ERROR_UNCONFIGURED: string = '尚未配置解读模型，请先到设置中配置'

/** error 帧业务码 → 用户可见文案（对齐后端 LlmErrorMapping 的下发语义） */
function sseErrorText(code: number, serverMessage: string): string {
  if (code === 428) {
    // 用户未配置解读模型：观星台零兜底（无平台默认模型），固定文案 + 页面「去配置」入口
    return SSE_ERROR_UNCONFIGURED
  }
  if (code === 403) {
    // 双语义：安全分流拒绝（如医疗/安全类问题引导）或平台总开关关闭（「观星台 AI 解读
    // 暂未开放，请联系管理员」）——两种语义服务端 message 均已明确，原样透传展示
    return serverMessage.length > 0 ? serverMessage : '该问题不适合以卜筮作答，请寻求现实帮助'
  }
  if (code === 429) {
    return '今日解读次数已达上限，请明日再来'
  }
  if (code === 503) {
    return '解读服务暂不可用，请稍后再试'
  }
  if (code === 404) {
    return '卦例不存在，请返回重新起卦'
  }
  return serverMessage.length > 0 ? serverMessage : '解读暂不可用，请稍后重试'
}

/** delta 累积器（SSE 会话内全文累积） */
interface TextAccumulator {
  text: string
}

/** SSE 会话终态标记（done/error 帧任一已收即置位；置位后传输层 onFail 不再下发） */
interface SseTerminalFlag {
  reached: boolean
}

/** 仓库内部回调适配：把 DivSseCallbacks 原始帧翻译为 DivLlmCallbacks（累积 + 文案映射） */
function buildSseCallbacks(cb: DivLlmCallbacks, acc: TextAccumulator): DivSseCallbacks {
  // 终态标记随每次会话新建（对齐安卓 failureSent 守卫）
  const terminal: SseTerminalFlag = { reached: false }
  return {
    onDelta: (text: string): void => {
      acc.text += text
      cb.onAccumulated(acc.text)
    },
    onDone: (interpretationId: number, cached: boolean): void => {
      terminal.reached = true
      cb.onDone(interpretationId, cached)
    },
    onSseError: (code: number, message: string): void => {
      // error 帧后服务端 emitter.complete() 是正常关闭——先置位防终态重复补发，
      // 避免 403/429/503 已映射文案被传输层中断/兜底文案覆盖（对齐安卓 failureSent）
      terminal.reached = true
      logger.warn(TAG, `解读 SSE error 帧 code=${code}`)
      cb.onSseError(code, sseErrorText(code, message))
    },
    onFail: (message: string): void => {
      if (terminal.reached) {
        // 终态帧（error/done）已收：UI 已呈现映射后的终态文案，传输层失败不再覆盖
        logger.debug(TAG, '终态帧已收，忽略传输层失败回调')
        return
      }
      cb.onFail(message)
    }
  }
}

function delay(ms: number): Promise<void> {
  return new Promise<void>((resolve: (value: void) => void) => {
    setTimeout(resolve, ms)
  })
}

// ========== Mock 文本（回归开关用；与 #80 之前同模板，重排为四段 Markdown） ==========

const MOCK_DISCLAIMER: string =
  '观象台为传统术数排盘与文化解读工具，结果仅供娱乐与文化研究参考，不构成任何决策依据。'

/** Mock 解读正文（四段固定标题 + 免责行，与真连同构） */
function buildMockReadingMarkdown(method: string, question: string): string {
  const methodName = method === 'liuyao' ? '六爻'
    : method === 'meihua' ? '梅花易数'
      : method === 'qimen' ? '奇门遁甲' : '大六壬'
  const q = question.trim().length > 0 ? question.trim() : '所问之事'
  return `## 一句话断语\n` +
    `${methodName}起卦，问「${q}」。盘面已分体用，所问之事**有可为之机**，唯节奏待时。\n\n` +
    `## 盘面要点\n` +
    `- 用神已依所问领域取定，旺相休囚看月令日辰\n` +
    `- 动爻为变化之机，**变爻所在即转机所在**\n` +
    `- 旬空月破之处，须待出空填实之期\n\n` +
    `## 分项解读\n` +
    `就事体而言，盘面呈现的是事情当前的状态与推力，而非定局。用神得力则事有根基，` +
    `休囚则推进乏力；体用比和则顺，体克用则事可成而费力，用克体则事多阻。\n\n` +
    `## 结论与建议\n` +
    `建议以盘面提示的方向调整节奏：**用神得力则积极推进**，用神休囚则先固本再图进。` +
    `重要决策请回到现实信息核对。\n\n` +
    `> 免责声明：${MOCK_DISCLAIMER}`
}

/** Mock 追问回复（沿用 #80 之前模板） */
function buildMockFollowUpReply(turn: number): string {
  return turn <= 2
    ? '已沿用你所起的这一卦继续推演。盘面未变，变化的只是你追问的切口——说得更具体些，我可以就那一处细论。'
    : '这一卦的推演已趋完整。若想换一个角度，可以重新起卦；同一卦象反复追问，信息增量有限。'
}

/** Mock 模式：把整文按自然段切成小段，逐段回调累积全文，模拟流式节奏 */
async function emitMockStream(markdown: string, cb: DivLlmCallbacks): Promise<void> {
  const parts: string[] = []
  const sections = markdown.split('\n\n')
  for (let i = 0; i < sections.length; i++) {
    parts.push(i === 0 ? sections[i] : `\n\n${sections[i]}`)
  }
  let acc = ''
  for (let i = 0; i < parts.length; i++) {
    await delay(MOCK_STEP_MS)
    acc += parts[i]
    cb.onAccumulated(acc)
  }
  cb.onDone(0, false)
}

// ========== 对外入口 ==========

/**
 * 解读：对服务端卦例 recordId 发起 SSE 解读。
 * @param question 补充问题（可空串：服务端按盘面直出解读）
 */
export async function interpretMarkdown(recordId: number, method: string, question: string,
  cb: DivLlmCallbacks): Promise<void> {
  if (USE_MOCK_LLM) {
    logger.debug(TAG, `解读 Mock 模式 recordId=${recordId} method=${method}`)
    await emitMockStream(buildMockReadingMarkdown(method, question), cb)
    return
  }
  const acc: TextAccumulator = { text: '' }
  await streamInterpret(recordId, question, buildSseCallbacks(cb, acc))
}

/**
 * 同卦追问：只发当前问题（历史由服务端拼）。
 * @param turn 当前轮次（Mock 模式控制回复语气；真连不使用）
 */
export async function followUpMarkdown(recordId: number, question: string, turn: number,
  cb: DivLlmCallbacks): Promise<void> {
  if (USE_MOCK_LLM) {
    logger.debug(TAG, `追问 Mock 模式 recordId=${recordId} turn=${turn}`)
    await emitMockStream(buildMockFollowUpReply(turn), cb)
    return
  }
  const acc: TextAccumulator = { text: '' }
  await streamFollowUp(recordId, question, buildSseCallbacks(cb, acc))
}

/**
 * 观象台解读 / 追问接口契约 —— 当前为 Mock 实现。
 *
 * 蓝本：安卓 feature/divination/.../network/DivinationApi.kt + data/DivLlmRepository.kt。
 *
 * 后端现状：`SmartWash/src/main/java/com/smartwash/divination/` 已有服务层（管理端接口 + RAG + 记录），
 * 但**没有用户端 controller**——`web/auth/divination/interpret|followup` 尚未对外暴露。
 * 因此本文件先返回与安卓同结构的 Mock 文本；后端就绪后只需替换本文件的实现，
 * 调用方（解读页 / 追问页）零改动。
 *
 * 注意：本模块的 `/web/auth/` 前缀会被 Axios 请求拦截器自动注入 Bearer token。
 */

import { BaseResponse } from '../../../network/BaseResponse'
import { httpUtil } from '../../../network/HttpUtil'

/** 解读请求体（字段与安卓 DivinationApi.interpret 一致） */
export interface DivInterpretForm {
  recordId: number
  method: string
  category: string
  question: string
  castAt: number
  chartJson: string
  stream: boolean
}

/** 追问请求体 */
export interface DivFollowUpForm {
  recordId: number
  question: string
  history: Array<DivMessage>
}

export interface DivMessage {
  role: string
  content: string
}

export interface DivReadingSection {
  title: string
  content: string
}

export interface DivReadingVo {
  summary: string
  sections: Array<DivReadingSection>
  action: string
  disclaimer: string
}

export interface DivFollowUpVo {
  reply: string
}

const METHOD_NAMES: Record<string, string> = {
  'liuyao': '六爻',
  'meihua': '梅花易数',
  'qimen': '奇门遁甲',
  'liuren': '大六壬'
}

const CATEGORY_NAMES: Record<string, string> = {
  'career': '事业',
  'wealth': '财运',
  'love': '感情',
  'study': '学业',
  'travel': '出行',
  'other': '综合'
}

const DISCLAIMER = '观象台为传统术数排盘与文化解读工具，结果仅供娱乐与文化研究参考，不构成任何决策依据。'

function delay(ms: number): Promise<void> {
  return new Promise<void>((resolve: (value: void) => void) => {
    setTimeout(resolve, ms)
  })
}

function buildMockReading(form: DivInterpretForm): DivReadingVo {
  const methodName = METHOD_NAMES[form.method] ?? form.method
  const categoryName = CATEGORY_NAMES[form.category] ?? form.category
  const question = form.question.trim()

  const summary = `${methodName}起卦，问「${question}」。所问属${categoryName}领域，以下为盘面推演结果。`

  const sections: Array<DivReadingSection> = []
  sections.push({
    title: '直接判断',
    content: `${methodName}本卦成象，问事之体与用已分。就${categoryName}而言，盘面呈现的是事情当前的状态与推力，' +
      '而非定局——变爻所在之处即转机所在。`
  })
  sections.push({
    title: form.method === 'liuyao' ? '用神与旺衰' : (form.method === 'meihua' ? '体用生克' : '格局要点'),
    content: form.method === 'liuyao'
      ? '用神已依所问领域取定，旺相休囚死看月令日辰。用神旺相则事有根基，休囚则推进乏力；若用神落旬空月破，须待出空填实之期。'
      : (form.method === 'meihua'
        ? '体卦为己、用卦为事。体用比和则顺，体克用则事可成而费力，用克体则事多阻，体生用则耗散，用生体则有人相助。'
        : '以日干为体、时干为用，看值符值使之落宫与空亡驿马。落宫得生则事顺，落空亡则须待时日。')
  })
  sections.push({
    title: form.method === 'liuren' ? '三传与天将' : '关键动变',
    content: form.method === 'liuren'
      ? '四课三传已成。初传为事之始、末传为事之终，中传为过程；天将加临处即助力与阻碍的来源。'
      : '动爻为变化之机，静爻为既定之势。动而化进则事进，化退则事退；回头生克决定这一变化对用神是助是损。'
  })
  sections.push({
    title: '应期',
    content: '应期取用神逢值逢合之期，或出空填实之时。盘面所示为时机倾向，具体时日仍需结合现实节奏判断。'
  })

  return {
    summary: summary,
    sections: sections,
    action: '建议以盘面提示的方向调整节奏：用神得力则积极推进，用神休囚则先固本再图进。重要决策请回到现实信息核对。',
    disclaimer: DISCLAIMER
  }
}

/** 解读（Mock）：后端就绪后替换为 httpUtil.post 真连 */
export async function interpret(form: DivInterpretForm): Promise<BaseResponse<DivReadingVo>> {
  await delay(600)
  return { code: 200, message: 'ok', data: buildMockReading(form) }
}

/** 追问（Mock）：沿用原盘原 castAt，不重算（见 Repository 铁律） */
export async function followUp(form: DivFollowUpForm): Promise<BaseResponse<DivFollowUpVo>> {
  await delay(600)
  const turn = form.history.length
  const reply = turn <= 2
    ? '已沿用你所起的这一卦继续推演。盘面未变，变化的只是你追问的切口——说得更具体些，我可以就那一处细论。'
    : '这一卦的推演已趋完整。若想换一个角度，可以重新起卦；同一卦象反复追问，信息增量有限。'
  return { code: 200, message: 'ok', data: { reply: reply } }
}


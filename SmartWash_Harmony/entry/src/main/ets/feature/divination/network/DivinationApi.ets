/**
 * 观象台用户端接口封装（#80 真连）—— 唯一事实标准：后端 DivinationWebController
 * （SmartWash/src/main/java/com/smartwash/divination/controller/DivinationWebController.java，
 * 前缀 /web/auth/div，需 ROLE_USER，JWT Bearer，响应信封 {code,message,data}）。
 *
 * 接口清单（与 Android 端同票 #78 契约 100% 对齐）：
 * - POST   /web/auth/div/records                建档 → data=服务端 recordId(Long)
 * - GET    /web/auth/div/records?page&size       卦历分页 → Page<RecordVo>
 * - GET    /web/auth/div/records/{id}            卦例详情（含最近一次解读）
 * - GET    /web/auth/div/today                   今日一签（服务端按当日时间卦生成/复用，幂等）
 * - POST   /web/auth/div/records/{id}/feedback   反馈与应验回填
 * - GET    /web/auth/div/methods                 四术元信息与免责声明文案
 * - POST   /web/auth/div/records/{id}/interpret  SSE 流式解读（body 为纯文本 question，可空）
 * - POST   /web/auth/div/records/{id}/followup   同卦追问 SSE（body 为纯文本 question，必填）
 *
 * 通道划分：
 * - JSON 信封接口走既有 httpUtil（Axios 拦截器自动注入 Bearer / 统一 401 处理）；
 * - interpret / followup 走 DivSseClient（@ohos.net.http 流式 + 攒流兜底，手动注入 token，
 *   401 与 Axios 同语义）。
 *
 * 爻值口径（六爻/梅花 lines 映射，服务端 core 重算权威盘面的输入）：
 * - 六爻：后端 6/7/8/9（老阴6/少阳7/少阴8/老阳9），端上 1/2/3/4（少阳1/少阴2/老阳3/老阴4）
 *   → 提交前映射 1→7、2→8、3→9、4→6（奇偶阴阳一致，动爻 6/9 对齐）；
 * - 梅花：后端 lines 为 [上卦先天数, 下卦先天数, 动爻]（1..8 与端上 Trigram.number 同源），
 *   端上 chartJson 的 lines 是 6 爻值编码 → 从 chart 的 upper/lower/movingYao 重构三元组；
 * - 奇门/六壬：无 lines（服务端按 castAt 重算），castMethod 仅为元数据。
 */

import { BaseResponse } from '../../../network/BaseResponse'
import { httpUtil } from '../../../network/HttpUtil'
import { logger } from '../../../utils/Logger'
import { decodeBundle, decodeBundleFromJson, DivRecordRow } from '../data/DivinationStore'
import { DivSseCallbacks, divSsePost } from './DivSseClient'
// 平台模型目录与 BYOK 设置同属 /web/auth/div 域，已由 #81 在 DivSettingsApi 实现，此处转发复用（勿重复定义）
export { getDivModels, DivModelVo, DivVendorGroupVo } from './DivSettingsApi'

// ========== 请求体 ==========

/** 追问页聊天单轮（user/assistant 本地聊天流渲染用；追问请求只发当前问题，历史由服务端拼） */
export interface DivMessage {
  role: string
  content: string
}

/** 建档请求体（对齐后端 CreateRecordFrom，method/question/castMethod/castAt 必填） */
export interface DivCreateRecordForm {
  /** 术数：liuyao/meihua/qimen/liuren */
  method: string
  /** 所问领域 id；后端空值落 general */
  category: string
  question: string
  /** 起卦方式：auto/manual/time/number（引擎仅作元数据，盘面由 lines/castAt 决定） */
  castMethod: string
  /** 起卦时刻（epoch 毫秒） */
  castAt: number
  /** 时区偏移分钟（东八=480；按 castAt 时刻的设备时区计算，对齐安卓 TimeZone.getOffset） */
  tzOffset: number
  /** 起卦原始输入（六爻爻值数组 / 梅花三元组；奇门六壬不传） */
  lines?: Array<number>
  /** 端上排盘结果 JSON 字符串（chartJson 原文 = bundle 全文，对齐安卓 DivChartCodec.encode(bundle)） */
  clientChart: string
}

/** 反馈 + 应验回填请求体（对齐后端 FeedbackFrom） */
export interface DivFeedbackForm {
  interpretationId: number
  /** 1-5 */
  rating: number
  /** 0未回填/1应验/2未验/3难说 */
  outcome: number
  outcomeNote?: string
}

// ========== 响应 VO（字段名与后端逐一勿改；LocalDateTime 为字符串） ==========

/** 卦例视图（卦历列表返回，对齐后端 RecordVo） */
export interface DivRecordVo {
  id: number
  method: string
  category: string
  question: string
  castMethod: string
  /** 后端 LocalDateTime 序列化字符串（yyyy-MM-dd HH:mm:ss） */
  castAt: string
  tzOffset: number
  source: string
  /** 1 端上/服务端盘面一致，0 不一致（仅标记，不影响建档与解读） */
  chartVerified: number
  latestInterpretation: string | null
  createdAt: string
}

/** 解读记录视图（对齐后端 InterpretationVo） */
export interface DivInterpretationVo {
  id: number
  recordId: number
  /** first / followup */
  kind: string
  question: string | null
  provider: string
  model: string
  keySource: string
  /** 解读正文（Markdown 单文） */
  contentMd: string
  tokensIn: number
  tokensOut: number
  latencyMs: number
  cacheHit: number
  auditStatus: number
  createdAt: string
}

/** 卦例详情视图（对齐后端 RecordDetailVo） */
export interface DivRecordDetailVo {
  id: number
  method: string
  category: string
  question: string
  castMethod: string
  castAt: string
  tzOffset: number
  source: string
  lines: string | null
  clientChart: string | null
  serverChart: string | null
  chartVerified: number
  latestInterpretation: DivInterpretationVo | null
  createdAt: string
}

/** 卦历分页（对齐后端 Page<RecordVo> 的关键分页字段） */
export interface DivRecordPageVo {
  records: Array<DivRecordVo>
  total: number
  size: number
  current: number
}

/** 四术元信息（对齐后端 MethodVo） */
export interface DivMethodVo {
  code: string
  name: string
  description: string
  disclaimer: string
}

/** 卦历分页查询参数（对齐后端 BaseSearchFrom 的 page/size） */
interface DivRecordPageQuery {
  page: number
  size: number
}

// ========== 爻值口径映射（建档 lines / clientChart 输入） ==========

/** chartJson 局部视图：六爻爻值数组 */
interface ChartLinesView {
  lines?: number[]
}

/** chartJson 局部视图：梅花先天卦数三元组（Trigram.number 1..8 与后端同源） */
interface ChartTrigramView {
  number?: number
}

interface ChartMeiHuaView {
  upper?: ChartTrigramView
  lower?: ChartTrigramView
  /** 动爻 1..6（MeiHuaChart 序列化键即 movingYao；勿读 moving——恒 undefined 会丢三元组） */
  movingYao?: number
}

/** 端上爻值 → 后端六爻爻值（1少阳→7 / 2少阴→8 / 3老阳→9 / 4老阴→6；其余原样兜底） */
function toServerYaoCode(localCode: number): number {
  if (localCode === 1) {
    return 7
  }
  if (localCode === 2) {
    return 8
  }
  if (localCode === 3) {
    return 9
  }
  if (localCode === 4) {
    return 6
  }
  return localCode
}

/**
 * 由本地卦盘记录构建服务端建档 lines：
 * - liuyao：chartJson.lines 逐爻转后端码；
 * - meihua：从 upper/lower/movingYao 重构 [上卦数, 下卦数, 动爻]；
 * - qimen/liuren：undefined（服务端按 castAt 重算）。
 */
function buildServerLines(row: DivRecordRow): Array<number> | undefined {
  try {
    const chartJson = decodeBundle(decodeBundleFromJson(row.chartJson))
    if (row.method === 'liuyao') {
      const chart = JSON.parse(chartJson) as ChartLinesView
      if (Array.isArray(chart.lines)) {
        return chart.lines.map((code: number): number => toServerYaoCode(code))
      }
      return undefined
    }
    if (row.method === 'meihua') {
      const chart = JSON.parse(chartJson) as ChartMeiHuaView
      const upper = chart.upper?.number
      const lower = chart.lower?.number
      const movingYao = chart.movingYao
      if (typeof upper === 'number' && typeof lower === 'number' && typeof movingYao === 'number') {
        return [upper, lower, movingYao]
      }
      return undefined
    }
  } catch (err) {
    // 解码失败按无原始输入建档（服务端按 castAt 时间卦重算），warn 留痕不打盘面内容
    logger.warn('divinationApi', `建档 lines 构建失败 method=${row.method}, ` +
      `${err instanceof Error ? err.message : ''}`)
  }
  return undefined
}

/** 起卦方式元数据（引擎无分支依赖；梅花恒传 number——lines 三元组即数字卦输入口径） */
function inferCastMethod(method: string): string {
  if (method === 'liuyao') {
    return 'auto'
  }
  if (method === 'meihua') {
    return 'number'
  }
  return 'time'
}

/** 建档问题防御：trim + 空白兜底「未记所问」（后端 @NotBlank，本地历史脏数据防建档 400，对齐安卓） */
function normalizeQuestion(question: string): string {
  const trimmed = question.trim()
  return trimmed.length > 0 ? trimmed : '未记所问'
}

/**
 * 由本地卦盘记录（RDB divination_db 行）构建服务端建档表单。
 * clientChart 传 row.chartJson 原文（bundle 全文，对齐安卓 DivChartCodec.encode(bundle)）：
 * 解包后的裸 chart 丢 method 信封且 __map 包裹形态与安卓不一致，服务端 chartVerified 比对会误判。
 */
export function buildCreateRecordForm(row: DivRecordRow): DivCreateRecordForm {
  const form: DivCreateRecordForm = {
    method: row.method,
    category: row.category,
    question: normalizeQuestion(row.question),
    castMethod: inferCastMethod(row.method),
    castAt: row.castAt,
    // 起卦时刻的设备时区偏移（分钟，东八=480）：按 castAt 计算而非当前时刻，跨时区回填不漂移
    tzOffset: -new Date(row.castAt).getTimezoneOffset(),
    lines: buildServerLines(row),
    clientChart: row.chartJson
  }
  return form
}

// ========== JSON 信封接口（走 httpUtil，Axios 拦截器自动注入 token） ==========

/** 建档：返回服务端 recordId（Long） */
export async function createDivRecord(form: DivCreateRecordForm): Promise<BaseResponse<number>> {
  logger.debug('divinationApi', `建档请求 method=${form.method} castAt=${form.castAt} ` +
    `lines=${form.lines === undefined ? 0 : form.lines.length}`)
  return httpUtil.post<number>('/web/auth/div/records', form)
}

/** 今日一签（服务端按当日时间卦生成/复用，幂等）；未登录/网络失败由调用方兜底 */
export async function getDivToday(): Promise<BaseResponse<DivRecordDetailVo>> {
  return httpUtil.get<DivRecordDetailVo>('/web/auth/div/today')
}

/** 卦历分页（当前用户卦例历史） */
export async function getDivRecords(page: number, size: number): Promise<BaseResponse<DivRecordPageVo>> {
  const query: DivRecordPageQuery = { page: page, size: size }
  return httpUtil.get<DivRecordPageVo>('/web/auth/div/records', query)
}

/** 四术元信息与免责声明文案 */
export async function getDivMethods(): Promise<BaseResponse<Array<DivMethodVo>>> {
  return httpUtil.get<Array<DivMethodVo>>('/web/auth/div/methods')
}

/** 反馈与应验回填（interpretationId 来自解读 done 帧） */
export async function submitDivFeedback(recordId: number,
  form: DivFeedbackForm): Promise<BaseResponse<string>> {
  logger.debug('divinationApi', `反馈提交 recordId=${recordId} interpretationId=${form.interpretationId} ` +
    `rating=${form.rating} outcome=${form.outcome}`)
  return httpUtil.post<string>(`/web/auth/div/records/${recordId}/feedback`, form)
}

// ========== SSE 流式接口（走 DivSseClient：流式优先 + 攒流兜底） ==========

/**
 * 解读（SSE）：POST /web/auth/div/records/{id}/interpret。
 * body 为纯文本 question（后端 @RequestBody(required=false) String 直取原始 body；
 * 空串 = 无补充问题，服务端按盘面直出解读），trim 对齐安卓。
 */
export async function streamInterpret(recordId: number, question: string,
  cb: DivSseCallbacks): Promise<void> {
  logger.debug('divinationApi', `解读 SSE 请求 recordId=${recordId} questionLen=${question.trim().length}`)
  return divSsePost(`/web/auth/div/records/${recordId}/interpret`, question.trim(), cb)
}

/**
 * 同卦追问（SSE）：POST /web/auth/div/records/{id}/followup。
 * 只发当前问题（历史解读上下文由服务端按 recordId 拼接），body 为纯文本（必填）。
 */
export async function streamFollowUp(recordId: number, question: string,
  cb: DivSseCallbacks): Promise<void> {
  logger.debug('divinationApi', `追问 SSE 请求 recordId=${recordId} questionLen=${question.length}`)
  return divSsePost(`/web/auth/div/records/${recordId}/followup`, question, cb)
}

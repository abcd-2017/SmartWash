/**
 * 观象台 SSE 客户端 —— 解读 / 追问的真连流式通道（#80）。
 *
 * 后端契约（DivinationWebController，前缀 /web/auth/div，SseEmitter）：
 *   event: delta   data: {"text":"…流式片段…"}
 *   event: done    data: {"interpretationId":123,"cached":false}
 *   event: error   data: {"code":428|404|403|429|503|500,"message":"…"}
 *                  （428=用户未配置解读模型；403=安全分流或平台总开关关闭，message 原样透传）
 *
 * 两种接收模式（流式优先）：
 * - 流式：@ohos.net.http requestInStream + on('dataReceive') 逐块到手后按 SSE 帧解析
 *   （按 \n 分行、event:/data: 字段、空行分发），delta 逐帧回调；
 * - 兜底攒流：流式链路异常（requestInStream 失败/逐块处理异常）且尚未产出任何帧时，
 *   同一端点改用整段接收（http.request 等请求结束一次性解析全部 SSE 帧后统一回调），
 *   UI 呈现 loading → 整段出。已产出帧后中断不再重试（避免内容重复）。
 *
 * 鉴权与 401：手动从 StorageUtil 读取 USER_TOKEN 注入 Bearer（SSE 不走 @ohos/axios 通道，
 * 拦截器不生效）；HTTP 401 与 Axios.ets 同语义——清 token → 清内存登录态 → 切登录根页
 * （复用 clearLoginState + tryClaimUnauthorizedDebounce，防抖时间戳与 axios 通道同源共享，
 * 同刻 401 只弹一次；勿在此重写）。
 *
 * 终态兜底：done/error 帧为会话终态；流正常结束但两者皆未收到（后端 SseEmitter 120s 超时
 * onTimeout 只断流不发终态帧，必现场景）时补发 onFail，避免调用方 UI 永久停留 loading。
 *
 * 地址红线：BASE_URL 经 hvigor BuildProfile 注入（与 Axios.ets 同源），源码零硬编码。
 */

import { http } from '@kit.NetworkKit'
import { util } from '@kit.ArkTS'
import BuildProfile from 'entry/BuildProfile'
import { getValue } from '../../../utils/StorageUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { CommonConstants } from '../../../constant/CommonConstants'
import { logger } from '../../../utils/Logger'
import { clearLoginState, tryClaimUnauthorizedDebounce } from '../../../network/Axios'
import { resString } from '../../../utils/ResourceString'

/** 服务端地址（构建期注入，与 Axios.ets 同一事实源） */
const BASE_URL: string = BuildProfile.BASE_URL

/** SSE 连接建立超时（毫秒） */
const CONNECT_TIMEOUT_MS: number = 15000
/** SSE 读超时（毫秒）：对齐后端 SseEmitter 120s 推流上限 */
const READ_TIMEOUT_MS: number = 120000

const TAG: string = 'divSseClient'

// ========== 传输层失败文案（DivText.* 同风格常量化；network 层不反向依赖 ui 组件，故本地定义） ==========

/** 登录失效（401：handleUnauthorizedLikeAxios 已同步清登录态并 toast） */
function FAIL_UNAUTHORIZED(): string { return resString('div_sse_client_1') }
/** 流已产出内容后中断 / 正常结束但未收到 done/error 终态帧 */
function FAIL_STREAM_INTERRUPTED(): string { return resString('div_sse_client_2') }
/** 流式与攒流通道均不可达 */
function FAIL_SERVICE_UNAVAILABLE(): string { return resString('div_sse_client_3') }

/** SSE 回调集合（onDelta 逐帧；onSseError 为业务 error 帧；onFail 为传输层失败） */
export interface DivSseCallbacks {
  onDelta: (text: string) => void
  onDone: (interpretationId: number, cached: boolean) => void
  onSseError: (code: number, message: string) => void
  onFail: (message: string) => void
}

/** delta 帧 data 载荷（后端 SseBridge.sendDelta） */
interface DeltaPayload {
  text?: string
}

/** done 帧 data 载荷（后端 SseBridge.sendDone） */
interface DonePayload {
  interpretationId?: number
  cached?: boolean
}

/** error 帧 data 载荷（后端 SseBridge.sendError） */
interface ErrorPayload {
  code?: number
  message?: string
}

/** 帧产出标记（跨流式/攒流两次尝试共享）：
 * any=任一内容帧已回调（攒流降级判定用）；terminated=终态帧（done/error）已回调（终态兜底判定用） */
interface SseProducedFlag {
  any: boolean
  terminated: boolean
}

/** 内部传输失败（流式模式抛出，由外层决定是否攒流重试） */
class SseTransportError extends Error {
  /** 已触发 401 语义时不做攒流重试 */
  readonly unauthorized: boolean

  constructor(message: string, unauthorized: boolean) {
    super(message)
    this.unauthorized = unauthorized
  }
}

/**
 * 401 统一处理：与 Axios.ets handleUnauthorized 完全同语义——
 * 已确认未登录静默返回；1.5s 窗口防抖（tryClaimUnauthorizedDebounce，与 axios 通道共享
 * 同一时间戳，SSE 与普通请求同刻 401 只弹一次）；toast 文案一致；
 * 复用 clearLoginState（清 token → 清内存登录态 → Index 经 isLoggedIn 切登录根页）。
 */
function handleUnauthorizedLikeAxios(): void {
  if (AppStorage.get<boolean>("isLoggedIn") === false) {
    return
  }
  if (!tryClaimUnauthorizedDebounce()) {
    return
  }
  showToastSafety(FAIL_UNAUTHORIZED())
  clearLoginState()
}

/**
 * SSE 帧解析器：把字节流切分为「event: + data:」帧，空行分发。
 * 单帧分发规则（对齐后端 SseBridge 契约）：
 * - delta → cb.onDelta(payload.text)
 * - done  → cb.onDone(payload.interpretationId, payload.cached)
 * - error → cb.onSseError(payload.code, payload.message)
 */
class SseFrameParser {
  private readonly onFrame: (event: string, data: string) => void
  private buffer: string = ''
  private eventName: string = ''
  private dataLines: string[] = []

  constructor(onFrame: (event: string, data: string) => void) {
    this.onFrame = onFrame
  }

  /** 追加新到文本块并解析出完整帧 */
  feed(chunk: string): void {
    this.buffer += chunk
    // 处理 buffer 中所有完整行（\n 结尾；兼容 \r\n，行尾 \r 在分发前剥离）
    for (; ; ) {
      const idx = this.buffer.indexOf('\n')
      if (idx < 0) {
        break
      }
      const line = this.buffer.substring(0, idx)
      this.buffer = this.buffer.substring(idx + 1)
      this.handleLine(line.endsWith('\r') ? line.substring(0, line.length - 1) : line)
    }
  }

  /** 请求结束时 flush：处理残余未换行行 + 分发未完成帧（服务端帧以空行结尾，正常无残余） */
  flush(): void {
    if (this.buffer.length > 0) {
      const tail = this.buffer.endsWith('\r') ? this.buffer.substring(0, this.buffer.length - 1) : this.buffer
      this.buffer = ''
      this.handleLine(tail)
    }
    this.dispatchPending()
  }

  private handleLine(line: string): void {
    if (line === '') {
      // 空行 = 帧边界，分发当前事件
      this.dispatchPending()
      return
    }
    if (line.startsWith(':')) {
      // SSE 注释行，忽略
      return
    }
    if (line.startsWith('event:')) {
      this.eventName = line.substring('event:'.length).trim()
      return
    }
    if (line.startsWith('data:')) {
      // 规范允许 "data: x" / "data:x"，仅剥一个前导空格
      let data = line.substring('data:'.length)
      if (data.startsWith(' ')) {
        data = data.substring(1)
      }
      this.dataLines.push(data)
      return
    }
    // event:/data:/注释 之外的行（如 id:/retry:）当前契约不存在，忽略
  }

  private dispatchPending(): void {
    if (this.eventName === '' && this.dataLines.length === 0) {
      return
    }
    const event = this.eventName
    const data = this.dataLines.join('\n')
    this.eventName = ''
    this.dataLines = []
    if (event !== '') {
      this.onFrame(event, data)
    }
  }
}

/** 解析 delta 帧数据（字段类型校验防御，禁裸 as 直取） */
function parseDeltaPayload(data: string): string | null {
  try {
    const payload = JSON.parse(data) as DeltaPayload
    if (typeof payload.text === 'string' && payload.text.length > 0) {
      return payload.text
    }
    return null
  } catch (err) {
    logger.warn(TAG, `delta 帧解析失败（忽略该帧）: ${err instanceof Error ? err.message : ''}`)
    return null
  }
}

/** 解析 done 帧数据（interpretationId 缺失视为 0，cached 缺省 false） */
function parseDonePayload(data: string): [number, boolean] | null {
  try {
    const payload = JSON.parse(data) as DonePayload
    const id = typeof payload.interpretationId === 'number' ? payload.interpretationId : 0
    const cached = payload.cached === true
    return [id, cached]
  } catch (err) {
    logger.warn(TAG, `done 帧解析失败: ${err instanceof Error ? err.message : ''}`)
    return null
  }
}

/** 解析 error 帧数据（code 缺省按 500 处理，message 兜底固定文案） */
function parseErrorPayload(data: string): [number, string] | null {
  try {
    const payload = JSON.parse(data) as ErrorPayload
    const code = typeof payload.code === 'number' ? payload.code : 500
    const message = typeof payload.message === 'string' && payload.message.length > 0
      ? payload.message : resString('div_sse_client_4')
    return [code, message]
  } catch (err) {
    logger.warn(TAG, `error 帧解析失败: ${err instanceof Error ? err.message : ''}`)
    return null
  }
}

/** 构造帧分发器（把 SSE 帧映射到回调，并记录是否已产出内容帧） */
function buildFrameHandler(cb: DivSseCallbacks, produced: SseProducedFlag): (event: string, data: string) => void {
  return (event: string, data: string): void => {
    if (event === 'delta') {
      const text = parseDeltaPayload(data)
      if (text !== null) {
        produced.any = true
        cb.onDelta(text)
      }
      return
    }
    if (event === 'done') {
      // done 为会话终态：无论载荷是否可解析都先置位（畸形 done 仍以完成信号兜底回调）
      produced.terminated = true
      const done = parseDonePayload(data)
      if (done !== null) {
        produced.any = true
        cb.onDone(done[0], done[1])
      } else {
        // done 帧畸形：以完成信号兜底（内容已全部到达）
        produced.any = true
        cb.onDone(0, false)
      }
      return
    }
    if (event === 'error') {
      // error 为会话终态：先置位防传输层兜底文案覆盖已映射的业务错误语义
      produced.terminated = true
      const err = parseErrorPayload(data)
      if (err !== null) {
        produced.any = true
        cb.onSseError(err[0], err[1])
      } else {
        produced.any = true
        cb.onSseError(500, resString('div_sse_client_4'))
      }
      return
    }
    // 未知事件类型忽略（向前兼容）
  }
}

/** UTF-8 分块解码器（stream:true 保多字节字符跨块完整） */
class ChunkDecoder {
  private readonly decoder: util.TextDecoder = util.TextDecoder.create('utf-8')

  decode(ab: ArrayBuffer): string {
    return this.decoder.decodeToString(new Uint8Array(ab), { stream: true })
  }

  decodeTail(): string {
    return this.decoder.decodeToString(new Uint8Array(0), { stream: false })
  }
}

/** 请求头（含手动注入的 Bearer token） */
async function buildHeaders(): Promise<Record<string, string>> {
  const headers: Record<string, string> = {
    'Content-Type': 'text/plain;charset=UTF-8',
    'Accept': 'text/event-stream'
  }
  const cached = await getValue(CommonConstants.USER_TOKEN, "")
  if (typeof cached === 'string' && cached.length > 0) {
    headers['Authorization'] = `Bearer ${cached}`
  }
  return headers
}

/**
 * 流式模式：requestInStream + on('dataReceive') 逐块解析。
 * 失败抛 SseTransportError（401 时 unauthorized=true，不做攒流重试）。
 */
async function runStreamed(url: string, body: string, cb: DivSseCallbacks, produced: SseProducedFlag): Promise<void> {
  const headers = await buildHeaders()
  const decoder = new ChunkDecoder()
  const parser = new SseFrameParser(buildFrameHandler(cb, produced))

  const req = http.createHttp()
  // dataEnd 兑现器：requestInStream 的 Promise 在响应头即 resolve，流结束以 dataEnd 为准
  let resolveEnd: () => void = (): void => {
  }
  let rejectEnd: (err: Error) => void = (_err: Error): void => {
  }
  const ended = new Promise<void>((resolve: () => void, reject: (err: Error) => void) => {
    resolveEnd = resolve
    rejectEnd = reject
  })

  req.on('dataReceive', (ab: ArrayBuffer) => {
    try {
      parser.feed(decoder.decode(ab))
    } catch (err) {
      // 逐块处理异常：中断等待并抛错（外层视帧产出情况决定攒流重试）
      rejectEnd(err instanceof Error ? err : new Error(resString('div_sse_client_5')))
    }
  })
  req.on('dataEnd', () => {
    resolveEnd()
  })

  let code: number
  try {
    code = await req.requestInStream(url, {
      method: http.RequestMethod.POST,
      extraData: body,
      header: headers,
      usingCache: false,
      connectTimeout: CONNECT_TIMEOUT_MS,
      readTimeout: READ_TIMEOUT_MS
    })
  } catch (err) {
    req.destroy()
    throw new SseTransportError(`SSE 流式请求失败: ${err instanceof Error ? err.message : ''}`, false)
  }

  if (code === 401) {
    req.destroy()
    handleUnauthorizedLikeAxios()
    throw new SseTransportError(resString('div_sse_client_6'), true)
  }
  if (code !== 200) {
    req.destroy()
    throw new SseTransportError(`服务不可用（HTTP ${code}）`, false)
  }

  try {
    await ended
  } finally {
    // 流尾残余字节：先 flush 解码器尾部（stream:false 兑现跨块截断的多字节字符）再分发解析器残余帧
    parser.feed(decoder.decodeTail())
    parser.flush()
    req.off('dataReceive')
    req.off('dataEnd')
    req.destroy()
  }
}

/** 非业务帧行提取整段 body 字符串（HTTP 层返回） */
function extractBody(result: Object | string): string {
  if (typeof result === 'string') {
    return result
  }
  // ArrayBuffer 形态（expectDataType 未生效时的兜底）
  try {
    const ab = result as ArrayBuffer
    return util.TextDecoder.create('utf-8').decodeToString(new Uint8Array(ab))
  } catch (err) {
    logger.warn(TAG, `攒流 body 非字符串: ${err instanceof Error ? err.message : ''}`)
    return ''
  }
}

/**
 * 攒流兜底：同一端点整段接收（等请求结束一次性解析全部 SSE 帧）。
 * 回调时序：所有 delta 一次性回调 → done/error 回调（UI 呈现 loading → 整段出）。
 */
async function runBuffered(url: string, body: string, cb: DivSseCallbacks, produced: SseProducedFlag): Promise<void> {
  const headers = await buildHeaders()
  const req = http.createHttp()
  let code: number = 0
  let result: Object | string
  try {
    const response = await req.request(url, {
      method: http.RequestMethod.POST,
      extraData: body,
      header: headers,
      usingCache: false,
      connectTimeout: CONNECT_TIMEOUT_MS,
      readTimeout: READ_TIMEOUT_MS,
      expectDataType: http.HttpDataType.STRING
    })
    code = response.responseCode
    result = response.result
  } catch (err) {
    req.destroy()
    throw new SseTransportError(`请求失败: ${err instanceof Error ? err.message : ''}`, false)
  }
  req.destroy()

  if (code === 401) {
    handleUnauthorizedLikeAxios()
    throw new SseTransportError(resString('div_sse_client_6'), true)
  }
  if (code !== 200) {
    throw new SseTransportError(`服务不可用（HTTP ${code}）`, false)
  }

  // 一次性解析全部 SSE 帧（帧回调经 buildFrameHandler 统一分发）
  const parser = new SseFrameParser(buildFrameHandler(cb, produced))
  parser.feed(extractBody(result))
  parser.flush()
}

/**
 * 发起 SSE 请求（POST，body 为纯文本 question）。
 *
 * 切换策略：先流式；流式链路失败且尚未产出任何帧、且非 401 时，
 * 自动降级攒流重试一次；攒流也失败 → onFail。
 * 已产出帧后中断不重试（避免内容重复），直接 onFail。
 * 终态兜底：尝试正常结束但未收到 done/error 终态帧（后端 SseEmitter 120s 超时 onTimeout
 * 只断流不发终态帧，必现场景）时补发 onFail，避免调用方 UI 永久停留 loading / 无错误态。
 *
 * @param path 相对路径（如 /web/auth/div/records/1/interpret）
 */
export async function divSsePost(path: string, question: string, cb: DivSseCallbacks): Promise<void> {
  const url = `${BASE_URL}${path}`
  // 产出标记：any=任一内容帧已回调（不再攒流重试）；terminated=终态帧（done/error）已回调
  const produced: SseProducedFlag = { any: false, terminated: false }
  // 流终态兜底：全程结束仍未收到终态帧时补发失败回调（追问页 sending 复位 / 解读页出错误态可重试）
  const ensureTerminalFrame = (): void => {
    if (!produced.terminated) {
      logger.warn(TAG, 'SSE 流结束但未收到 done/error 终态帧，按推流中断兜底')
      cb.onFail(FAIL_STREAM_INTERRUPTED())
    }
  }
  try {
    await runStreamed(url, question, cb, produced)
    // 流式正常结束即会话收尾（已产出内容不重试，避免重复），终态帧缺失直接兜底
    ensureTerminalFrame()
    return
  } catch (err) {
    const unauthorized = err instanceof SseTransportError && err.unauthorized
    if (unauthorized) {
      cb.onFail(FAIL_UNAUTHORIZED())
      return
    }
    if (produced.any) {
      logger.warn(TAG, `流式接收中断（已产出内容，不重试）: ${err instanceof Error ? err.message : ''}`)
      cb.onFail(FAIL_STREAM_INTERRUPTED())
      return
    }
    logger.warn(TAG, `流式通道失败，降级攒流重试: ${err instanceof Error ? err.message : ''}`)
  }
  try {
    await runBuffered(url, question, cb, produced)
    ensureTerminalFrame()
  } catch (err) {
    const unauthorized = err instanceof SseTransportError && err.unauthorized
    if (unauthorized) {
      cb.onFail(FAIL_UNAUTHORIZED())
      return
    }
    logger.error(TAG, `攒流通道亦失败: ${err instanceof Error ? err.message : ''}`)
    cb.onFail(FAIL_SERVICE_UNAVAILABLE())
  }
}

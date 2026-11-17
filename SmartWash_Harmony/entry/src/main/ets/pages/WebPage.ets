// 内置浏览器页（壳层留守页面，对齐安卓 app ui/page/webview/WebViewPage.kt）：
// 深链 web 分支与运营位 H5 承载，路由名 WebPage（router_map + RouteNames.WEB_PAGE）。
// - 仅接受 http/https，否则 EmptyState 错误态，杜绝 javascript: 等伪协议进 WebView；
// - 原生 Web 组件（@kit.ArkWeb webview.WebviewController，零新依赖），JavaScript 与 DOM 存储开启
//   （常规 H5 含登录态 localStorage 依赖 DOM 存储，关闭会导致大量页面异常）；
// - 外部 scheme（tel/mailto 等）经 onLoadIntercept 交系统处理，不在应用内加载
//   （对齐安卓 WebViewClient.shouldOverrideUrlLoading）；
// - 返回语义：网页历史可回退则回退，历史为空才出栈本页（对齐安卓 BackHandler(canGoBack)）；
// - 加载进度为真实 onProgressChange 回调驱动的清氧细进度条（2vp 主色 + 页面底色轨道，
//   非伪造动画，无自主动效，项目无 reduced motion 机制，本页也不新增长时间动画）；
// - 标题取路由参数，缺省「详情页」。
//
// 导航条避让（安卓 navigationBarsPadding）：本端窗口未开启 setWindowLayoutFullScreen，
// 窗口内容默认已按系统安全区布局（NavDestination 内容不伸进系统导航条），
// 安卓该 padding 是 edge-to-edge 的补偿项，此处无对应内边距可消费，加 pad 反而双重避让
// （同 recap G1/G2 真机复核结论）。

import { webview } from '@kit.ArkWeb'
import { common, Want } from '@kit.AbilityKit'
import { window } from '@kit.ArkUI'
import { RouteNames } from '../constant/RouteNames'
import { DesignFontSize } from '../constant/DesignSystem'
import { AppButton, AppButtonVariant, EmptyState, PageHeader } from '../view/common/AppComponents'
import { getObjectParam, pathStack } from '../utils/PathStackUtil'
import { logger } from '../utils/Logger'
import { safeBottomVp, safeTopVp } from '../utils/SafeArea'
import BuildProfile from 'entry/BuildProfile'
import { ServerConfig } from '../config/ServerConfig'
import { resColor } from '../utils/ResourceColor'
import { resString } from '../utils/ResourceString'
import { BaseResponse } from '../network/BaseResponse'
import { BizCode } from '../network/HttpUtil'
import { jsonNum, jsonStr } from '../network/vo/JsonParse'
import {
  GameScoreReportBody,
  GameStateUpdateBody,
  getGameLeaderboard,
  getGameLevels,
  getGameState,
  reportGameScore,
  saveGameState
} from '../feature/game/network/GameApi'

/** WebView 页路由入参（对齐安卓 ShellRoute.WEB_VIEW 的 url/title/chrome 三个 query 参数） */
export class WebPageParam {
  /** 网页地址（调用方保证 http/https，WebPage 内仍做白名单校验兜底） */
  url: string = ''
  /** 页面标题，空串 = 兜底「详情页」 */
  title: string = ''
  /** chrome 初值（对齐安卓 URL 参数 chrome：full/immersive/none，避免先闪一帧原生栏再隐藏） */
  chrome: string = 'full'
}

// 标题兜底文案（对齐安卓 webview_default_title「详情页」）
function WEB_DEFAULT_TITLE(): string { return resString('webview_default_title') }

// 非法链接错误态文案（对齐安卓 webview_error_url_unsupported）
function WEB_ERROR_URL_UNSUPPORTED(): string { return resString('webview_error_url_unsupported') }

// 加载失败错误态文案（对齐安卓 webview_error_load）
function WEB_ERROR_LOAD(): string { return resString('webview_error_load') }
// 加载失败重试按钮文案（对齐安卓 webview_action_retry）
function WEB_ACTION_RETRY(): string { return resString('retry') }

// ========== 游戏 H5 桥（对齐安卓 GameThemeBridge + GameChrome，问题16） ==========

// chrome 合法取值（对齐安卓 GameChrome：full=标题栏+状态栏 / immersive=保标题栏隐状态栏 / none=全隐）
const CHROME_FULL: string = 'full'
const CHROME_IMMERSIVE: string = 'immersive'
const CHROME_NONE: string = 'none'
const CHROME_MODES: Array<string> = [CHROME_FULL, CHROME_IMMERSIVE, CHROME_NONE]

// 排行榜 limit 非法（<= 0）时的缺省 TOP N（对齐安卓 GameThemeBridgePolicy.DEFAULT_LEADERBOARD_LIMIT）
const DEFAULT_LEADERBOARD_LIMIT: number = 20
// 桥数据调用超时（毫秒）：H5 GB 适配层的 pending 表无超时会永久挂起，原生侧兜底回传失败
const GAME_BRIDGE_TIMEOUT_MS: number = 15000
// 空载荷占位（ok=true 但 data 为 null 时回传 {}，对齐安卓 gson.toJson(payload ?: emptyMap)）
const EMPTY_GAME_PAYLOAD: Record<string, Object> = {}

// JS 桥注入对象名（H5 侧 window.SmartWashGameBridge，协议与安卓一致）
const GAME_BRIDGE_NAME: string = 'SmartWashGameBridge'
/**
 * 主题推送脚本（H5 未定义钩子时为空操作）。
 * App 深浅色跟随系统（AppStorage 'darkMode'），推送携带当前解析值，
 * 对齐安卓 onPageFinished 推 resolveBridgeThemeMode(uiMode) 的语义。
 */
function gameThemePushScript(): string {
  const dark: boolean = AppStorage.get<boolean>('darkMode') === true
  const mode: string = dark ? 'dark' : 'light'
  return `window.__applySmartWashTheme&&__applySmartWashTheme('${mode}')`
}

/** chrome 归一化：非法/空值回落 full（fail-safe，对齐安卓 GameChrome.normalize） */
function normalizeChrome(mode: string): string {
  for (const candidate of CHROME_MODES) {
    if (candidate === mode) {
      return mode
    }
  }
  return CHROME_FULL
}

/** 存档写入载荷解析结果（对齐安卓 GameThemeBridgePolicy.SaveStatePayload） */
interface SaveStatePayload {
  stateJson: string
  schemaVer: number | null
}

/** 成绩上报载荷解析结果（对齐安卓 GameBridgeScoreReportBody 的映射前置） */
interface ReportScorePayload {
  metricValue: number
  metricExtra: number | null
  metricJson: string | null
}

/**
 * 解析 H5 传入的 JSON 对象（非法/数组/标量返回 null，由调用方按缺省处理）。
 * 对齐安卓 GameThemeBridgePolicy.parseJsonObject：Gson.fromJson(JsonObject) 对非对象失败。
 */
function parseJsonObject(json: string | null): Record<string, Object> | null {
  if (json === null || json.length === 0) {
    return null
  }
  try {
    const parsed: Object = JSON.parse(json)
    if (parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, Object>
    }
  } catch (err) {
    // 非法 JSON：按缺省处理（存档场景回退原始字符串，不丢内容）
  }
  return null
}

/**
 * saveState 载荷解析，兼容两种 H5 载荷（对齐安卓 Policy.parseSaveStatePayload）：
 * - 标准 {"stateJson":"...","schemaVer":1}：提取 stateJson / schemaVer；
 * - 裸 JSON（无 stateJson 键或 stateJson 为 null）：整个原始字符串作为存档内容落库，
 *   避免「存档内容不能为空」静默失败；空/非法输入解析为空串。
 */
function parseSaveStatePayload(payloadJson: string | null): SaveStatePayload {
  const payload: Record<string, Object> | null = parseJsonObject(payloadJson)
  if (payload === null) {
    return { stateJson: payloadJson ?? '', schemaVer: null }
  }
  const stateJson: string | null = jsonStr(payload['stateJson'])
  return {
    stateJson: stateJson ?? payloadJson ?? '',
    schemaVer: jsonNum(payload['schemaVer'])
  }
}

/**
 * reportScore 载荷解析（对齐安卓 Policy.parseJsonObject + GameBridgeScoreReportBody 映射）：
 * 缺失字段按缺省（metricValue=0，其余 null），不阻断上报。
 */
function parseReportScorePayload(payloadJson: string | null): ReportScorePayload {
  const payload: Record<string, Object> | null = parseJsonObject(payloadJson)
  if (payload === null) {
    return { metricValue: 0, metricExtra: null, metricJson: null }
  }
  return {
    metricValue: jsonNum(payload['metricValue']) ?? 0,
    metricExtra: jsonNum(payload['metricExtra']),
    metricJson: jsonStr(payload['metricJson'])
  }
}

/** 排行榜 limit 归一化：H5 传 0 / 负数按缺省 20 处理（对齐安卓 normalizeLeaderboardLimit） */
function normalizeLeaderboardLimit(limit: number): number {
  return limit <= 0 ? DEFAULT_LEADERBOARD_LIMIT : limit
}

/**
 * cbId 经 JS 单引号字符串字面量转义（回调脚本以 'cbId' 内嵌）：
 * 先转反斜杠再转单引号——顺序保证已转义序列不被二次转义，防止 cbId 注入破坏回调脚本
 * （对齐安卓 GameThemeBridgePolicy.escapeJsSingleQuoted）。
 */
function escapeJsSingleQuoted(raw: string): string {
  return raw.replace(/\\/g, '\\\\').replace(/'/g, "\\'")
}

/**
 * 从 URL 提取 host（归一化：小写、去尾部 FQDN 点）。
 * 仅 http/https 放行；解析失败返回 null（fail-closed，对齐安卓 GameThemeBridgePolicy.hostOf）。
 */
function hostOf(url: string): string | null {
  if (url === null || url === undefined || url.length === 0) {
    return null
  }
  const schemeEnd = url.indexOf('://')
  if (schemeEnd <= 0) {
    return null
  }
  const scheme = url.substring(0, schemeEnd).toLowerCase()
  if (scheme !== 'http' && scheme !== 'https') {
    return null
  }
  const rest = url.substring(schemeEnd + 3)
  let host = ''
  for (let i = 0; i < rest.length; i++) {
    const ch = rest.charAt(i)
    if (ch === '/' || ch === ':' || ch === '?' || ch === '#') {
      break
    }
    host += ch
  }
  const lower = host.toLowerCase()
  const trimmed = lower.charAt(lower.length - 1) === '.' ? lower.substring(0, lower.length - 1) : lower
  return trimmed.length > 0 ? trimmed : null
}

/**
 * 桥白名单 host 集（服务端同源判定；对齐安卓 whitelistHostsOf(BASE_URL, minioEndpoint)）。
 * MinIO host 来自运行期 /web/config 预热（ServerConfig），游戏直链与封面域名变更无需发版；
 * 配置未就绪时仅含 API host，属可接受降级（游戏页由用户操作打开，此时配置早已就绪）。
 */
function bridgeWhitelist(): Set<string> {
  const hosts: Set<string> = new Set()
  const base = hostOf(BuildProfile.BASE_URL)
  if (base !== null) {
    hosts.add(base)
  }
  const minio = hostOf(ServerConfig.minioEndpoint)
  if (minio !== null) {
    hosts.add(minio)
  }
  return hosts
}

/**
 * 游戏 H5 主题/chrome 桥（注册名固定 SmartWashGameBridge，协议对齐安卓）：
 * - getTheme()：白名单内返回当前主题实值（实读 AppStorage('darkMode')，App 已跟随系统深浅色，
 *   语义对齐安卓 resolveBridgeThemeMode；「恒 light」为旧固定亮色时代的注释，已过时）；
 *   白名单外返回空串（H5 端空串走 matchMedia 跟随系统，不报错）；
 * - setChrome(mode)：白名单内切换原生顶部 chrome（标题栏显隐 + 状态栏显隐），
 *   白名单外静默忽略——通用容器不得让第三方 H5 改掉 App 的标题栏。
 */
class SmartWashGameBridge {
  /** 当前页 host 快照（onPageBegin 先于页面任何 JS 执行时刷新，覆盖首帧与站内跳转） */
  private currentPageHost: string | null = null
  private whitelist: Set<string>
  private onChromeChange: (mode: string) => void
  /** Web 控制器（异步结果经 runJavaScript 回传 H5） */
  private controller: webview.WebviewController
  /** 页面存活标记：销毁后禁止 evaluateJavascript（防 destroy 竞态，对齐安卓 G2.1） */
  private alive: boolean = false
  /** 待回传 cbId → 超时定时器（全路径回调保障，H5 pending 表无超时会永久挂起） */
  private pending: Map<string, number> = new Map()

  constructor(whitelist: Set<string>, onChromeChange: (mode: string) => void,
    controller: webview.WebviewController) {
    this.whitelist = whitelist
    this.onChromeChange = onChromeChange
    this.controller = controller
  }

  /** 同步页面存活标记（aboutToAppear 置 true / aboutToDisappear 置 false） */
  setAlive(alive: boolean): void {
    this.alive = alive
    if (!alive) {
      this.clearPending()
    }
  }

  /** onPageBegin 刷新 host 快照（供桥调用时的来源判定） */
  refreshHost(url: string): void {
    this.currentPageHost = hostOf(url)
  }

  private isAllowedHost(): boolean {
    const host = this.currentPageHost
    if (host === null || this.whitelist.size === 0) {
      return false
    }
    return this.whitelist.has(host)
  }

  getTheme(): string {
    if (!this.isAllowedHost()) {
      return ''
    }
    const dark: boolean = AppStorage.get<boolean>('darkMode') === true
    return dark ? 'dark' : 'light'
  }

  setChrome(mode: string): void {
    if (!this.isAllowedHost()) {
      return
    }
    this.onChromeChange(normalizeChrome(mode))
  }

  /** 读存档：H5 传 gameKey + 回调 id，原生带 token 请求后回传 */
  loadState(gameKey: string, cbId: string): void {
    this.dispatchData(cbId, 'loadState', (): Promise<BaseResponse<Object>> => getGameState(gameKey))
  }

  /** 写存档：payloadJson = {"stateJson":"...","schemaVer":1}（兼容裸 JSON 兜底） */
  saveState(gameKey: string, payloadJson: string, cbId: string): void {
    const payload: SaveStatePayload = parseSaveStatePayload(payloadJson)
    const body: GameStateUpdateBody = { gameKey: gameKey, stateJson: payload.stateJson }
    if (payload.schemaVer !== null) {
      body.schemaVer = payload.schemaVer
    }
    this.dispatchData(cbId, 'saveState', (): Promise<BaseResponse<Object>> => saveGameState(body))
  }

  /** 上报成绩：payloadJson = {"metricValue":100,"metricExtra":null,"metricJson":null} */
  reportScore(gameKey: string, payloadJson: string, cbId: string): void {
    const payload: ReportScorePayload = parseReportScorePayload(payloadJson)
    const body: GameScoreReportBody = { gameKey: gameKey, metricValue: payload.metricValue }
    if (payload.metricExtra !== null) {
      body.metricExtra = payload.metricExtra
    }
    if (payload.metricJson !== null) {
      body.metricJson = payload.metricJson
    }
    this.dispatchData(cbId, 'reportScore', (): Promise<BaseResponse<Object>> => reportGameScore(body))
  }

  /** 排行榜：limit 非法（<= 0）时按缺省 20 处理 */
  getLeaderboard(gameKey: string, limit: number, cbId: string): void {
    const normalized: number = normalizeLeaderboardLimit(limit)
    this.dispatchData(cbId, 'getLeaderboard',
      (): Promise<BaseResponse<Object>> => getGameLeaderboard(gameKey, normalized))
  }

  /** 关卡下发 */
  getLevels(gameKey: string, cbId: string): void {
    this.dispatchData(cbId, 'getLevels', (): Promise<BaseResponse<Object>> => getGameLevels(gameKey))
  }

  /**
   * 统一数据请求分发：白名单校验 → 超时计时 → 请求 → 结果回调 H5。
   * 白名单外 / 请求异常 / 超时一律回调失败（ok=false），H5 侧据此降级 localStorage。
   */
  private dispatchData(cbId: string, method: string,
    request: () => Promise<BaseResponse<Object>>): void {
    if (!this.isAllowedHost()) {
      logger.warn('webPage', `桥数据调用拒绝非白名单来源 method=${method}`)
      this.emitResult(cbId, false, '非白名单来源')
      return
    }
    // cbId 重入防御：H5 异常重复调用同一 id 时先了结旧回调，避免 pending 互相覆盖悬挂
    this.cancelPending(cbId)
    const timerId: number = setTimeout((): void => {
      this.pending.delete(cbId)
      logger.warn('webPage', `桥数据调用超时 method=${method}`)
      this.emitResult(cbId, false, '网络异常')
    }, GAME_BRIDGE_TIMEOUT_MS)
    this.pending.set(cbId, timerId)
    request().then((res: BaseResponse<Object>): void => {
      this.cancelPending(cbId)
      if (res.code === BizCode.SUCCESS) {
        this.emitResult(cbId, true, res.data ?? EMPTY_GAME_PAYLOAD)
      } else {
        this.emitResult(cbId, false, res.message ?? '网络异常')
      }
    }).catch((err: Error): void => {
      this.cancelPending(cbId)
      // 仅记 method + message 摘要，不序列化 error（可能携带请求上下文）
      logger.warn('webPage', `桥数据请求失败 method=${method} err=${err.message}`)
      this.emitResult(cbId, false, '网络异常')
    })
  }

  /**
   * 结果回调：window.__gOnGameBridgeResult(cbId, ok, payload)；
   * payload 为 JSON 对象字面量（字符串载荷为 JSON 字符串字面量）。
   * 页面已销毁 / 控制器未就绪时静默跳过（回调丢失无副作用，H5 侧超时兜底）。
   */
  private emitResult(cbId: string, ok: boolean, payload: Object | null): void {
    if (!this.alive) {
      return
    }
    const json: string = JSON.stringify(payload !== null ? payload : EMPTY_GAME_PAYLOAD)
    const safeCbId: string = escapeJsSingleQuoted(cbId)
    const script: string =
      `window.__gOnGameBridgeResult&&window.__gOnGameBridgeResult('${safeCbId}', ${ok}, ${json})`
    try {
      this.controller.runJavaScript(script).catch((): void => {
        // controller 未附加 / 页面已销毁：静默
      })
    } catch (err) {
      logger.debug('webPage', '桥结果回调时 Web 控制器未就绪，跳过')
    }
  }

  /** 了结指定 cbId 的待回传项（存在则清定时器），供完成回调与重入防御复用 */
  private cancelPending(cbId: string): void {
    const timerId: number | undefined = this.pending.get(cbId)
    if (timerId !== undefined) {
      clearTimeout(timerId)
      this.pending.delete(cbId)
    }
  }

  /** 清空全部待回传项（页面销毁时调用，防定时器泄漏） */
  private clearPending(): void {
    this.pending.forEach((timerId: number): void => {
      clearTimeout(timerId)
    })
    this.pending.clear()
  }
}

@Builder
export function WebPageBuilder() {
  WebPage()
}

@Component
struct WebPage {
  @State url: string = ''
  @State title: string = WEB_DEFAULT_TITLE()
  // 0-100 真实加载进度（onProgressChange 回调驱动；1-99 显示细进度条）
  @State progress: number = 0
  // 主框架加载失败标记（仅主框架错误置位，子资源失败不打断页面）
  @State loadError: boolean = false
  // 网页历史回退可用性（onPageEnd 后刷新，返回键语义跟随用户浏览预期）
  @State canGoBack: boolean = false
  // chrome 模式（游戏 H5 经桥/路由参数控制，对齐安卓 chromeMode）：控制标题栏显隐与状态栏显隐
  @State chromeMode: string = CHROME_FULL
  // 深浅色实时跟随（深色模式审计）：本页不套 ThemeRefresh（子树重建会销毁重建 Web 组件、
  // 丢失 H5 浏览状态，代价过高），改用 @Watch 感知色模式变化后主动向 H5 推送主题，
  // 对齐安卓 GameThemeBridge 的主动推送语义（原生薄层进度条/错误态颜色以重进页面刷新兜底）
  @StorageProp('darkMode') @Watch('onDarkModeChange') darkMode: boolean = false
  private controller: webview.WebviewController = new webview.WebviewController()
  // 游戏桥：白名单 host 内才生效（构造即绑定 chrome 回调与 Web 控制器，供异步数据回传）
  private gameBridge: SmartWashGameBridge = new SmartWashGameBridge(bridgeWhitelist(), (mode: string) => {
    this.applyChrome(mode)
  }, this.controller)

  aboutToAppear(): void {
    // 桥存活标记：页面销毁前允许数据回调（销毁即置 false，回调侧据此跳过 evaluateJavascript）
    this.gameBridge.setAlive(true)
    // 安全取参（评审 #31 约定）：参数缺失/非法时不崩溃，保持错误态
    const param = getObjectParam<WebPageParam>(RouteNames.WEB_PAGE)
    if (param !== null) {
      this.url = param.url
      this.title = param.title.length > 0 ? param.title : WEB_DEFAULT_TITLE()
      // chrome 初值经路由参数（避免先闪一帧原生标题栏再隐藏，对齐安卓）
      this.chromeMode = normalizeChrome(param.chrome)
    }
  }

  aboutToDisappear(): void {
    // 桥存活标记置 false 并清空待回传定时器（防页面销毁后 evaluateJavascript 与定时器泄漏）
    this.gameBridge.setAlive(false)
    // 离页复原 chrome（对齐安卓 DisposableEffect onDispose → apply(FULL)）
    this.applyChrome(CHROME_FULL)
  }

  /**
   * 系统深浅色切换（@Watch 回调）：主动向当前 H5 推送新主题（对齐安卓 onPageFinished
   * pushGameTheme 的推送语义 + 拉取 getTheme 的双保险，跨端一致）。
   * controller 未附加/页面未加载时静默（runJavaScript 失败无副作用）。
   */
  onDarkModeChange(): void {
    try {
      this.controller.runJavaScript(gameThemePushScript()).catch(() => {
      })
    } catch (err) {
      // controller 未附加（主题切换时页面尚未加载）静默
      logger.debug('webPage', '主题推送时 Web 控制器未就绪，跳过')
    }
  }

  /** 是否显示原生标题返回栏（对齐安卓 GameChrome.shouldShowHeader） */
  private shouldShowHeader(): boolean {
    return this.chromeMode !== CHROME_NONE
  }

  /**
   * 应用 chrome 到窗口（对齐安卓 GameChrome.apply）：
   * full → 显示状态栏；immersive/none → 隐藏状态栏（H5 多得一行可视高度）。
   * 系统栏 API 失败静默（部分设备/窗口态不支持），标题栏显隐不受影响。
   */
  private applyChrome(mode: string): void {
    this.chromeMode = mode
    try {
      window.getLastWindow(getContext(this)).then((win: window.Window) => {
        win.setSpecificSystemBarEnabled('status', mode === CHROME_FULL).catch(() => {
          // 状态栏控制失败静默：标题栏显隐是主要诉求
        })
      }).catch(() => {
      })
    } catch (err) {
      // 无窗口上下文时静默（理论不可达：页面已挂载）
      logger.debug('webPage', `WebView 页挂载，标题=${this.title}`)
      logger.warn('webPage', '路由参数缺失或非法，展示仅支持网页链接错误态')
    }
  }

  /** url 白名单：仅 http/https 放行进 Web 容器（错误态兜底，不渲染 Web） */
  private isHttpUrl(): boolean {
    return this.isHttpScheme(this.url)
  }

  /** scheme 判定（大小写不敏感，对齐安卓 request.url.scheme?.lowercase()） */
  private isHttpScheme(target: string): boolean {
    const colonIndex: number = target.indexOf(':')
    if (colonIndex < 0) {
      return false
    }
    const scheme: string = target.substring(0, colonIndex).toLowerCase()
    return scheme === 'http' || scheme === 'https'
  }

  /**
   * 外部 scheme 分发（对齐安卓 shouldOverrideUrlLoading）：tel/mailto 等非 http(s) 链接
   * 不在应用内加载，交系统 viewData 处理；无应用可处理时静默（与安卓 runCatching{...}.isSuccess 一致）。
   */
  private openExternal(url: string): void {
    try {
      const context = getContext(this) as common.UIAbilityContext
      const want: Want = {
        action: 'ohos.want.action.viewData',
        uri: url,
        entities: ['entity.system.browsable']
      }
      // startAbility 返回 Promise：异步 reject（无应用可处理）try/catch 抓不到，必须 .catch 兜底
      context.startAbility(want).catch(() => {
        // 无系统应用可处理该 scheme：静默返回
      })
    } catch (err) {
      // 无系统应用可处理该 scheme：静默返回（对齐安卓 runCatching 静默，仅补 debug 留痕）
      logger.debug('webPage', '系统无应用可处理外部 scheme，静默忽略')
    }
  }

  /**
   * 返回语义（PageHeader 返回键与系统返回键共用）：
   * 网页历史可回退则回退并消费本次返回（true），历史为空交还导航默认出栈（false）。
   */
  private handleBack(): boolean {
    if (!this.isHttpUrl() || !this.canGoBack) {
      return false
    }
    try {
      this.controller.backward()
      return true
    } catch (err) {
      // controller 未附加等异常（理论不可达：canGoBack 仅在页面加载后置位），降级出栈
      logger.debug('webPage', '网页回退控制器异常，降级直接出栈')
      return false
    }
  }

  /** 重试：清错误态复位进度后重新加载当前页（对齐安卓 webView.reload()；ArkWeb 侧方法名为 refresh） */
  private retryLoad(): void {
    this.loadError = false
    this.progress = 0
    try {
      this.controller.refresh()
    } catch (err) {
      // controller 异常时按当前 url 重新装载兜底（重试动作降级留 warn）
      logger.warn('webPage', '刷新重试失败，改为重新装载当前页')
      this.controller.loadUrl(this.url)
    }
  }

  build() {
    NavDestination() {
      Column() {
        // chrome=none 时整条标题返回栏收起（游戏自绘 chrome）；full/immersive 保留返回入口
        if (this.shouldShowHeader()) {
          PageHeader({
            title: this.title,
            backClick: () => {
              if (!this.handleBack()) {
                pathStack.pop()
              }
            }
          })
        }

        if (this.isHttpUrl()) {
          // Web 容器 + 细进度条 + 加载失败错误态叠加
          Stack({ alignContent: Alignment.TopStart }) {
            Web({ src: this.url, controller: this.controller })
              .javaScriptAccess(true)
              .domStorageAccess(true)
              // 游戏 H5 桥（问题16 + HF6）：主题/chrome + 数据桥 5 方法
              //（loadState/saveState/reportScore/getLeaderboard/getLevels），
              // 仅白名单 host（BASE_URL + MinIO）生效，协议与安卓 GameThemeBridge 对齐
              .javaScriptProxy({
                object: this.gameBridge,
                name: GAME_BRIDGE_NAME,
                methodList: ['getTheme', 'setChrome', 'loadState', 'saveState',
                  'reportScore', 'getLeaderboard', 'getLevels'],
                controller: this.controller
              })
              // 外部 scheme 拦截（对齐安卓 shouldOverrideUrlLoading）：http(s) 留应用内，其余交系统
              .onLoadIntercept((event: OnLoadInterceptEvent) => {
                const requestUrl: string = event.data.getRequestUrl()
                if (this.isHttpScheme(requestUrl)) {
                  return false
                }
                this.openExternal(requestUrl)
                return true
              })
              .onPageBegin((event: OnPageBeginEvent) => {
                // 新一轮导航开始时复位错误态与进度，并刷新桥的 host 快照
                //（onPageBegin 先于页面任何 JS 执行，覆盖首帧与站内跳转，对齐安卓 onPageStarted）
                this.loadError = false
                this.progress = 0
                this.gameBridge.refreshHost(event.url)
              })
              .onPageEnd(() => {
                try {
                  this.canGoBack = this.controller.accessBackward()
                } catch (err) {
                  // controller 异常时按不可回退处理，返回键语义降级为直接出栈
                  logger.debug('webPage', '查询网页回退状态失败，按不可回退处理')
                  this.canGoBack = false
                }
                // 主题主动推送（双保险之一，对齐安卓 onPageFinished pushGameTheme；白名单校验在脚本消费侧）
                try {
                  this.controller.runJavaScript(gameThemePushScript()).catch(() => {
                  })
                } catch (err) {
                  // controller 未附加（快速返回）时静默
                }
              })
              .onProgressChange((event: OnProgressChangeEvent) => {
                this.progress = event.newProgress
              })
              .onErrorReceive((event: OnErrorReceiveEvent) => {
                // 仅主框架失败才进错误态（子资源 404/超时不应打断整页）
                if (event.request.isMainFrame()) {
                  // 外部加载失败（WebView 运行期回调，非渲染路径）：对齐安卓 WebViewPage，
                  // 只记 errorCode + errorInfo 摘要，不记 URL（深链/运营链接 query 不可控）
                  logger.error('webPage',
                    `WebView 主框架加载失败 errorCode=${event.error.getErrorCode()} errorInfo=${event.error.getErrorInfo()}`)
                  this.loadError = true
                }
              })
              .width('100%')
              .height('100%')

            // 清氧细进度条：真实进度驱动（隐藏 0 与 100 两端，避免空载/完成残留）。
            // 轨道 = 页面底色 2vp（对齐安卓 LinearProgressIndicator 的 trackColor = background），
            // 原实现只有主色填充、无底轨，深色网页下进度条会「消失」。
            if (this.progress >= 1 && this.progress <= 99) {
              Stack() {
                Row()
                  .width('100%')
                  .height(2)
                  .backgroundColor($r('app.color.BACKGROUND'))
                Row()
                  .width(`${this.progress}%`)
                  .height(2)
                  .backgroundColor($r('app.color.PRIMARY'))
              }
              .width('100%')
              .height(2)
              .alignContent(Alignment.TopStart)
            }

            // 加载失败错误态：说明发生了什么 + 给重试（规范 §3.6）
            if (this.loadError) {
              Column() {
                EmptyState({ message: WEB_ERROR_LOAD() }) {
                  AppButton({
                    small: true,
                    text: WEB_ACTION_RETRY(),
                    variant: AppButtonVariant.TEXT,
                    click: () => {
                      this.retryLoad()
                    }
                  })
                }
              }
              .width('100%')
              .height('100%')
              .backgroundColor($r('app.color.BACKGROUND'))
              .justifyContent(FlexAlign.Center)
            }
          }
          .width('100%')
          .layoutWeight(1)
        } else {
          // 非法链接错误态：说明发生了什么（规范 §3.6）
          Column() {
            EmptyState({ message: WEB_ERROR_URL_UNSUPPORTED() })
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        }
      }
      .width('100%')
      .height('100%')
      // 沉浸式：标题栏/底部手势条避让（背景延伸；chrome=none 时 H5 全屏自绘，不避让）
      .padding({
        top: this.shouldShowHeader() ? safeTopVp() : 0,
        bottom: this.shouldShowHeader() ? safeBottomVp() : 0
      })
      .backgroundColor($r('app.color.BACKGROUND'))
    }
    .hideTitleBar(true)
    .onBackPressed(() => {
      // 系统返回键与 PageHeader 返回键同语义：网页历史优先，否则默认出栈
      return this.handleBack()
    })
  }
}

// 深链解析器（纯函数，零路由知识、零 UI 依赖，对齐安卓 common:utils/deeplink/DeepLinkParser.kt）
//
// 内建判定仅两类，其余 host 全部交给调用方注入的 routeLookup（host → 路由目标，
// 由 DeepLinkRegistry 提供实现）：
// 1. `http://` / `https://` → WEB（URL 原样，不改写/不解码）；
// 2. `smartwash://web`：query `url` 必填（须 http/https）、`title` 可选（均百分号解码）→ WEB；
// 3. 其余 host → routeLookup：命中 → ROUTE；未命中 → UNKNOWN（host 记入结果，供
//    DeepLinkHandler 未命中时查后端字典）。
// 非 smartwash scheme / 畸形输入 → UNKNOWN。解析全程不抛异常（对齐安卓解析契约）。

import { util } from '@kit.ArkTS'

/** 自有 scheme（大小写不敏感） */
export const DEEPLINK_SCHEME: string = 'smartwash'

/** 内建 host：WebView 分支（query 携带 url/title），不经 routeLookup */
export const DEEPLINK_HOST_WEB: string = 'web'

/** scheme 分隔符 */
const SCHEME_SEPARATOR: string = '://'

/**
 * 解析结果三态（对齐安卓 DeepLinkAction 密封类；ArkTS 无密封类，用枚举 + 全字段接口表达）：
 * - ROUTE：本地注册表命中，携带可直接 pushPathByName 的路由名与入参；
 * - WEB：http(s) 直链或 smartwash://web 分支，由 WebPage 页承载；
 * - UNKNOWN：非法或未注册，UI 侧统一提示「暂不支持该链接」。
 */
export enum DeepLinkKind {
  ROUTE,
  WEB,
  UNKNOWN
}

/**
 * 注册表命中产物：路由名（RouteNames 常量）+ 入参对象（页面经 PathStackUtil
 * 的 getXxxParam 消费，禁止裸强转）。
 */
export interface DeepLinkRouteTarget {
  routeName: string
  param: Object | null
}

/**
 * host → 路由目标查找函数（对齐安卓 DeepLinkRouteFactory 的契约位）。
 * 由 DeepLinkRegistry 实现，DeepLinkParser.parse 消费：
 * @param host 已归一化小写的 scheme host
 * @param pathSegments host 之后的剩余 path 段（保留原始大小写，参数校验由实现方自行完成）
 * @param query query 参数（value 已百分号解码；字面 + 不转空格）
 * @return 命中返回路由目标；未注册/参数非法返回 null
 */
export type DeepLinkRouteLookup = (host: string, pathSegments: string[],
  query: Map<string, string>) => DeepLinkRouteTarget | null

/**
 * 深链解析结果（全字段非空，未用到的字段取零值，规避 ArkTS 可空字段的调用方判空噪音）。
 */
export interface DeepLinkResult {
  /** 结果三态 */
  kind: DeepLinkKind
  /** WEB 分支：网页地址（http/https，调用方可直接进 WebPage） */
  url: string
  /** WEB 分支：页面标题；空串 = 页面兜底「详情页」 */
  title: string
  /** ROUTE 分支：路由名（RouteNames 常量） */
  routeName: string
  /** ROUTE 分支：路由入参对象（无参路由为 null） */
  routeParam: Object | null
  /** smartwash:// 的 host（小写）；UNKNOWN 且非空时供 Handler 查后端字典。非 smartwash 输入为空串 */
  host: string
  /** host 之外的剩余 path 段（如 t/{code} 的 code），诊断用 */
  pathSegments: string[]
  /** query 参数（value 已百分号解码），诊断用 */
  query: Map<string, string>
}

/** 空结果模板（畸形输入统一回落，避免各分支重复拼对象） */
function unknownResult(uri: string): DeepLinkResult {
  return {
    kind: DeepLinkKind.UNKNOWN,
    url: '',
    title: '',
    routeName: '',
    routeParam: null,
    host: hostOf(uri) ?? '',
    pathSegments: [],
    query: new Map<string, string>()
  }
}

/**
 * 解析入口：任何输入（含畸形 URI）都不抛异常，失败一律 UNKNOWN。
 *
 * @param uri 待解析深链
 * @param routeLookup host → 路由目标查找（由 DeepLinkRegistry.lookup 提供）
 */
export function parse(uri: string, routeLookup: DeepLinkRouteLookup): DeepLinkResult {
  if (uri.trim().length === 0) {
    return unknownResult(uri)
  }
  try {
    return parseInternal(uri.trim(), routeLookup)
  } catch (err) {
    // 兜底：解析逻辑自身的意外异常也降级为 UNKNOWN，保证调用方无需 try-catch
    return unknownResult(uri)
  }
}

/**
 * 提取 smartwash:// 的 host（供 Handler 未命中时查后端字典兜底）；
 * 非 smartwash scheme 或无 host 返回 null。
 */
export function hostOf(uri: string): string | null {
  const trimmed = uri.trim()
  const prefix = DEEPLINK_SCHEME + SCHEME_SEPARATOR
  if (!trimmed.toLowerCase().startsWith(prefix)) {
    return null
  }
  const rest = trimmed.substring(prefix.length)
  const queryStart = rest.indexOf('?')
  const pathPart = queryStart >= 0 ? rest.substring(0, queryStart) : rest
  const slashIndex = pathPart.indexOf('/')
  const host = slashIndex >= 0 ? pathPart.substring(0, slashIndex) : pathPart
  return host.length > 0 ? host.toLowerCase() : null
}

// ---------- 内部实现 ----------

function parseInternal(uri: string, routeLookup: DeepLinkRouteLookup): DeepLinkResult {
  const schemeEnd = uri.indexOf(SCHEME_SEPARATOR)
  if (schemeEnd <= 0) {
    return unknownResult(uri)
  }
  const scheme = uri.substring(0, schemeEnd).toLowerCase()
  const rest = uri.substring(schemeEnd + SCHEME_SEPARATOR.length)
  if (rest.length === 0) {
    return unknownResult(uri)
  }
  if (scheme === 'http' || scheme === 'https') {
    // http(s) 直链：URL 原样进 WebView（无标题，页面兜底「详情页」）
    return webResult(uri, '')
  }
  if (scheme === DEEPLINK_SCHEME) {
    return parseSmartwash(rest, uri, routeLookup)
  }
  return unknownResult(uri)
}

/** smartwash://host/path?query：web 为内建分支，其余 host 全部交给 routeLookup */
function parseSmartwash(rest: string, rawUri: string,
  routeLookup: DeepLinkRouteLookup): DeepLinkResult {
  const queryStart = rest.indexOf('?')
  const pathPart = queryStart >= 0 ? rest.substring(0, queryStart) : rest
  const queryPart = queryStart >= 0 ? rest.substring(queryStart + 1) : null
  // 过滤空段：容忍 smartwash://host/ 形式的尾斜杠
  const segments = pathPart.split('/').filter((segment: string) => segment.length > 0)
  if (segments.length === 0) {
    return unknownResult(rawUri)
  }
  const host = segments[0].toLowerCase()
  const params = parseQuery(queryPart)
  if (host === DEEPLINK_HOST_WEB) {
    return parseWeb(params, rawUri)
  }
  // host 之外剩余 path 段原样下传（t/{code} 的 code 大小写敏感，由注册表自行校验）
  const target = routeLookup(host, segments.slice(1), params)
  if (target === null) {
    return unknownResult(rawUri)
  }
  return {
    kind: DeepLinkKind.ROUTE,
    url: '',
    title: '',
    routeName: target.routeName,
    routeParam: target.param,
    host: host,
    pathSegments: segments.slice(1),
    query: params
  }
}

/** web 分支：query url 必填且必须 http/https，title 可选（值已在 parseQuery 解码） */
function parseWeb(params: Map<string, string>, rawUri: string): DeepLinkResult {
  const url = params.get('url') ?? ''
  if (url.length === 0 || (!url.startsWith('http://') && !url.startsWith('https://'))) {
    return unknownResult(rawUri)
  }
  const title = params.get('title') ?? ''
  return webResult(url, title)
}

function webResult(url: string, title: string): DeepLinkResult {
  return {
    kind: DeepLinkKind.WEB,
    url: url,
    title: title,
    routeName: '',
    routeParam: null,
    host: '',
    pathSegments: [],
    query: new Map<string, string>()
  }
}

/**
 * query 串解析：key 原样（自有 scheme 的 key 均为 ASCII），value 百分号解码，
 * 畸形参数（% 序列非法）整体丢弃（对齐安卓 URLDecoder 抛异常→丢弃的行为）。
 */
function parseQuery(query: string | null): Map<string, string> {
  const params = new Map<string, string>()
  if (query === null || query.length === 0) {
    return params
  }
  const pairs = query.split('&')
  pairs.forEach((pair: string) => {
    if (pair.length === 0) {
      return
    }
    const eq = pair.indexOf('=')
    const key = eq >= 0 ? pair.substring(0, eq) : pair
    const rawValue = eq >= 0 ? pair.substring(eq + 1) : ''
    const decoded = percentDecode(rawValue)
    if (decoded !== null) {
      params.set(key, decoded)
    }
  })
  return params
}

const HEX_CHARS: string = '0123456789abcdefABCDEF'

/** 十六进制字符 → 数值（0-15）；非十六进制字符返回 -1 */
function hexValue(char: string): number {
  const index = HEX_CHARS.indexOf(char)
  if (index < 0) {
    return -1
  }
  return index < 16 ? index : index - 6
}

/**
 * 百分号解码（UTF-8，自实现，对齐安卓 URLDecoder 语义）：
 * - 字面 `+` 不转空格——URI query 语义中 + 是普通字符，与表单编码区分
 *   （安卓实现为先把 + 保护为 %2B 再 URLDecoder，本实现天然不处理 +，结果等价）；
 * - % 序列非法（缺位/非十六进制）返回 null，由调用方丢弃该参数；
 * - 字面字符（含非 ASCII）经 TextEncoder 转 UTF-8 字节后与 %XX 字节统一解码，
 *   与 decodeURIComponent 语义一致但不依赖平台对 +/空格的差异化处理。
 */
function percentDecode(input: string): string | null {
  if (input.indexOf('%') < 0) {
    return input
  }
  try {
    const bytes: number[] = []
    const encoder = new util.TextEncoder()
    let literalStart = 0
    let i = 0
    while (i < input.length) {
      if (input.charAt(i) !== '%') {
        i += 1
        continue
      }
      // % 后必须跟两位十六进制，否则整段参数非法
      if (i + 2 >= input.length) {
        return null
      }
      const hi = hexValue(input.charAt(i + 1))
      const lo = hexValue(input.charAt(i + 2))
      if (hi < 0 || lo < 0) {
        return null
      }
      // 冲刷 % 之前的字面字符段（UTF-8 编码入字节流）
      if (i > literalStart) {
        appendUtf8Bytes(bytes, encoder.encodeInto(input.substring(literalStart, i)))
      }
      bytes.push(hi * 16 + lo)
      i += 3
      literalStart = i
    }
    if (literalStart < input.length) {
      appendUtf8Bytes(bytes, encoder.encodeInto(input.substring(literalStart)))
    }
    const out = new Uint8Array(bytes.length)
    for (let j = 0; j < bytes.length; j++) {
      out[j] = bytes[j]
    }
    const decoder = util.TextDecoder.create('utf-8')
    return decoder.decodeToString(out)
  } catch (err) {
    return null
  }
}

/** TextEncoder 产物追加进字节收集数组（手写拷贝，规避 Uint8Array.from 的兼容疑点） */
function appendUtf8Bytes(bytes: number[], chunk: Uint8Array): void {
  for (let i = 0; i < chunk.length; i++) {
    bytes.push(chunk[i])
  }
}

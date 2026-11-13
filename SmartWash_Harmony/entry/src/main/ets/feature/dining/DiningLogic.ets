// 吃啥（dining）域纯函数集 — 无 UI 依赖、无网络依赖（JVM 单测锚点的 ArkTS 对应物，
// 对齐 Android impl logic/* 纯函数：DiningJoinUrlBuilder / DiningJoinUrlParser /
// DiningJoinEntry / DiningOrderFormValidator / DiningRecipeFormValidator /
// DiningShoppingListMappers / DiningMenuNavigator）。
//
// 全部函数确定性输入输出，供 entry/src/test/DiningLogic.test.ets 断言。

import { DiningOrderBriefVo } from '../../network/vo/DiningVo'
import { diningShoppingListFromJSON, DiningShoppingListSnapshot } from '../../network/vo/DiningVo'

// ========== 点单订单状态 ==========

/** 点单订单状态全集（后端 dining_order.status 白名单；文案见 DiningText） */
export enum DiningOrderStatus {
  /** 进行中（可加入、可勾选/改备注/取消） */
  ONGOING,
  /** 已结束（发起者锁定并生成购买清单快照，拒绝一切选菜写入） */
  FINISHED
}

/**
 * 状态码 → 枚举；null/空白/白名单外脏数据按保守降级返回 FINISHED 档语义
 * （未知状态按「不可写」处理，交互与展示同 FINISHED——不引导用户对未知态下单）。
 */
export function diningOrderStatusFromCode(code: string | null): DiningOrderStatus {
  if (code === 'ONGOING') {
    return DiningOrderStatus.ONGOING
  }
  return DiningOrderStatus.FINISHED
}

// ========== 加入二维码内容构造 ==========

/** 二维码路径前缀（与深链 host "dining" 对齐；端内扫码/解析侧按此 path 识别加入链路） */
export const DINING_JOIN_PATH_PREFIX: string = '/dining/join/'

/**
 * 构造二维码内容 URL：`{origin}/dining/join/{orderId}?t={joinToken}`。
 *
 * origin 取主网关 BASE_URL（scheme://host[:port]，去尾斜杠与路径）——BASE_URL 是环境唯一
 * 真源（构建期注入），二维码 host 与 App 实际访问后端同源，不新增硬编码地址（环境红线）。
 *
 * @returns 二维码内容；token 空白或 baseUrl 畸形/非 http(s) 返回 null（页面降级提示）
 */
export function buildDiningJoinUrl(baseUrl: string, orderId: number, joinToken: string): string | null {
  const token = joinToken.trim()
  if (token.length === 0) {
    return null
  }
  const origin = originOf(baseUrl)
  if (origin === null) {
    return null
  }
  return origin + DINING_JOIN_PATH_PREFIX + orderId + '?t=' + token
}

/**
 * 从 baseUrl 提取 origin（scheme://host[:port]，去尾斜杠与路径）。
 * 仅接受 http/https（二维码内容必须是浏览器/端内扫码可识别的 http 族地址）；
 * host 缺失/畸形返回 null。手写解析（ArkTS 无 java.net.URI；对齐 Android originOf 语义）。
 */
export function originOf(baseUrl: string): string | null {
  const trimmed = baseUrl.trim()
  const lower = trimmed.toLowerCase()
  let scheme = ''
  let rest = ''
  if (lower.startsWith('http://')) {
    scheme = 'http'
    rest = trimmed.substring('http://'.length)
  } else if (lower.startsWith('https://')) {
    scheme = 'https'
    rest = trimmed.substring('https://'.length)
  } else {
    return null
  }
  // authority 到 / ? # 或串尾为止
  let end = rest.length
  const marks = ['/', '?', '#']
  for (let i = 0; i < marks.length; i++) {
    const idx = rest.indexOf(marks[i])
    if (idx >= 0 && idx < end) {
      end = idx
    }
  }
  const authority = rest.substring(0, end)
  if (authority.length === 0) {
    return null
  }
  // 剥离 userinfo（user:pass@host:port 的 @ 前段）
  const atIndex = authority.lastIndexOf('@')
  const hostPort = atIndex >= 0 ? authority.substring(atIndex + 1) : authority
  if (hostPort.length === 0) {
    return null
  }
  return scheme + '://' + hostPort
}

// ========== 加入链接解析（扫码内容 / 外部带入的 http 族加入地址） ==========

/** 加入凭证查询键（大小写不敏感匹配） */
const TOKEN_QUERY_KEY: string = 't'

/**
 * 加入链接解析结果（对齐 Android DiningJoinUrlParseResult 三分语义）：
 * - Join：合法加入链接（orderId + token）；
 * - NotDining：非本应用链接（空串/纯文本/非 http 族 scheme/路径不符）；
 * - Malformed：命中加入路径但结构非法（orderId 缺失/非正整数/多余路径、token 缺失）。
 */
export enum DiningJoinUrlKind {
  JOIN,
  NOT_DINING,
  MALFORMED
}

/** 解析产物（kind=JOIN 时 orderId/token 有值，其余为 0/空串） */
export interface DiningJoinUrlParseResult {
  kind: DiningJoinUrlKind
  orderId: number
  token: string
}

/**
 * 解析加入链接原文（`{origin}/dining/join/{orderId}?t={token}`）。
 * 大小写策略：scheme 大小写不敏感；路径段大小写敏感（spec 固定 /dining/join/）；
 * 查询键 t 大小写不敏感。解析全程不抛异常。
 */
export function parseDiningJoinUrl(raw: string | null): DiningJoinUrlParseResult {
  const malformed: DiningJoinUrlParseResult = { kind: DiningJoinUrlKind.MALFORMED, orderId: 0, token: '' }
  const notDining: DiningJoinUrlParseResult = { kind: DiningJoinUrlKind.NOT_DINING, orderId: 0, token: '' }
  const value = raw === null ? '' : raw.trim()
  if (value.length === 0) {
    return notDining
  }
  const schemeSep = value.indexOf('://')
  if (schemeSep <= 0) {
    return notDining
  }
  const scheme = value.substring(0, schemeSep).toLowerCase()
  if (scheme !== 'http' && scheme !== 'https') {
    return notDining
  }
  const rest = value.substring(schemeSep + 3)
  const queryStart = rest.indexOf('?')
  const pathPart = queryStart >= 0 ? rest.substring(0, queryStart) : rest
  const queryPart = queryStart >= 0 ? rest.substring(queryStart + 1) : ''
  // 去掉 authority：path 自第一个 / 起（若无 / 则无 path）
  const slashIndex = pathPart.indexOf('/')
  if (slashIndex < 0) {
    return notDining
  }
  const path = pathPart.substring(slashIndex)
  if (!path.startsWith(DINING_JOIN_PATH_PREFIX)) {
    return notDining
  }
  const orderIdText = path.substring(DINING_JOIN_PATH_PREFIX.length)
  if (orderIdText.length === 0 || orderIdText.indexOf('/') >= 0) {
    return malformed
  }
  if (!/^\d+$/.test(orderIdText)) {
    return malformed
  }
  const orderId = Number(orderIdText)
  if (!Number.isInteger(orderId) || orderId <= 0) {
    return malformed
  }
  // token：第一个 t 键（大小写不敏感），值原样（构造侧 token 均为 URL 安全字符）
  let token = ''
  if (queryPart.length > 0) {
    const pairs = queryPart.split('&')
    for (let i = 0; i < pairs.length; i++) {
      const eq = pairs[i].indexOf('=')
      if (eq <= 0) {
        continue
      }
      const key = pairs[i].substring(0, eq)
      if (key.toLowerCase() === TOKEN_QUERY_KEY) {
        token = pairs[i].substring(eq + 1).trim()
        break
      }
    }
  }
  if (token.length === 0) {
    return malformed
  }
  return { kind: DiningJoinUrlKind.JOIN, orderId: orderId, token: token }
}

// ========== 加入入口决策（深链 brief 判重） ==========

/** 加入入口决策四态（对齐 Android DiningJoinEntryDecision） */
export enum DiningJoinEntryDecision {
  /** 已是成员：直达点单详情页 */
  ENTER_DETAIL,
  /** 非成员且订单进行中：展示确认加入 */
  CONFIRM_JOIN,
  /** 订单已结束/状态未知：不引导加入 */
  ORDER_FINISHED,
  /** brief 缺失：页面按错误态处理 */
  BRIEF_UNAVAILABLE
}

/**
 * 深链 `smartwash://dining/order/{orderId}?t={token}` 落地后的 brief 判重决策。
 * 状态保守降级：brief.status 未知按 FINISHED 档（不可加入）处理。
 */
export function decideDiningJoinEntry(brief: DiningOrderBriefVo | null): DiningJoinEntryDecision {
  if (brief === null) {
    return DiningJoinEntryDecision.BRIEF_UNAVAILABLE
  }
  if (brief.joined) {
    return DiningJoinEntryDecision.ENTER_DETAIL
  }
  if (diningOrderStatusFromCode(brief.status) === DiningOrderStatus.ONGOING) {
    return DiningJoinEntryDecision.CONFIRM_JOIN
  }
  return DiningJoinEntryDecision.ORDER_FINISHED
}

// ========== 创建订单表单校验 ==========

/** 标题长度上限（对齐后端 @Size(max = 64)） */
export const DINING_TITLE_MAX_LENGTH: number = 64
/** 本单菜单菜品数上限（对齐后端 @Size(max = 100)） */
export const DINING_MENU_MAX_COUNT: number = 100

/** 创建订单表单校验错误（纯逻辑不持有文案，文案由 DiningText 映射） */
export enum DiningOrderFormError {
  TITLE_REQUIRED,
  TITLE_TOO_LONG,
  MENU_EMPTY,
  MENU_TOO_MANY
}

/** 校验结果（valid=false 时 error 有值；normalized 仅合法时携带） */
export interface DiningOrderFormValidation {
  valid: boolean
  error: DiningOrderFormError | null
  normalizedTitle: string
  normalizedRecipeIds: Array<number>
}

/**
 * 创建订单组合校验（显式 recipeIds 链路）：标题先行、菜单随后，逐项短路返回首个错误。
 * 规则对齐后端 Bean Validation + service 二选一裁决（端上先拦，后端兜底）。
 */
export function validateDiningOrderForm(title: string, recipeIds: Array<number>): DiningOrderFormValidation {
  const trimmedTitle = title.trim()
  if (trimmedTitle.length === 0) {
    return invalidDiningOrderForm(DiningOrderFormError.TITLE_REQUIRED)
  }
  if (trimmedTitle.length > DINING_TITLE_MAX_LENGTH) {
    return invalidDiningOrderForm(DiningOrderFormError.TITLE_TOO_LONG)
  }
  if (recipeIds.length === 0) {
    return invalidDiningOrderForm(DiningOrderFormError.MENU_EMPTY)
  }
  if (recipeIds.length > DINING_MENU_MAX_COUNT) {
    return invalidDiningOrderForm(DiningOrderFormError.MENU_TOO_MANY)
  }
  return {
    valid: true,
    error: null,
    normalizedTitle: trimmedTitle,
    normalizedRecipeIds: recipeIds
  }
}

/** 复用历史菜单模式的提交校验：只校验标题（菜单由后端按 fromOrderId 整份复制） */
export function validateDiningOrderReuse(title: string): DiningOrderFormValidation {
  const trimmedTitle = title.trim()
  if (trimmedTitle.length === 0) {
    return invalidDiningOrderForm(DiningOrderFormError.TITLE_REQUIRED)
  }
  if (trimmedTitle.length > DINING_TITLE_MAX_LENGTH) {
    return invalidDiningOrderForm(DiningOrderFormError.TITLE_TOO_LONG)
  }
  return { valid: true, error: null, normalizedTitle: trimmedTitle, normalizedRecipeIds: [] }
}

function invalidDiningOrderForm(error: DiningOrderFormError): DiningOrderFormValidation {
  return { valid: false, error: error, normalizedTitle: '', normalizedRecipeIds: [] }
}

// ========== 新增菜谱表单校验 ==========

/** 菜名长度上限（对齐后端 @Size(max = 64)） */
export const DINING_RECIPE_NAME_MAX_LENGTH: number = 64
/** 单个食材名长度上限（对齐后端 @Size(max = 32)） */
export const DINING_INGREDIENT_MAX_LENGTH: number = 32
/** 食材数量上限（对齐后端 @Size(max = 100)） */
export const DINING_INGREDIENT_MAX_COUNT: number = 100
/** 步骤长度上限（对齐后端 @Size(max = 20000)） */
export const DINING_STEPS_MAX_LENGTH: number = 20000

export enum DiningRecipeFormError {
  NAME_REQUIRED,
  NAME_TOO_LONG,
  INGREDIENT_EMPTY,
  INGREDIENT_TOO_LONG,
  INGREDIENT_TOO_MANY,
  STEPS_TOO_LONG
}

export interface DiningRecipeFormValidation {
  valid: boolean
  error: DiningRecipeFormError | null
  normalizedError: DiningOrderFormError | null
  name: string
  ingredients: Array<string>
  steps: string | null
}

/**
 * 新增菜谱组合校验：菜名必填 ≤64；食材逐项 trim 后非空 ≤32、总数 ≤100；步骤可空 ≤20000。
 * 分类不做端上校验（0/null=未分类，>0 存在性由后端裁决）。
 */
export function validateDiningRecipeForm(name: string, ingredients: Array<string>,
  steps: string): DiningRecipeFormValidation {
  const failRecipe = (error: DiningRecipeFormError): DiningRecipeFormValidation => ({
    valid: false, error: error, normalizedError: null, name: '', ingredients: [], steps: null
  })
  const trimmedName = name.trim()
  if (trimmedName.length === 0) {
    return failRecipe(DiningRecipeFormError.NAME_REQUIRED)
  }
  if (trimmedName.length > DINING_RECIPE_NAME_MAX_LENGTH) {
    return failRecipe(DiningRecipeFormError.NAME_TOO_LONG)
  }
  if (ingredients.length > DINING_INGREDIENT_MAX_COUNT) {
    return failRecipe(DiningRecipeFormError.INGREDIENT_TOO_MANY)
  }
  const normalized: Array<string> = []
  for (let i = 0; i < ingredients.length; i++) {
    const item = ingredients[i].trim()
    if (item.length === 0) {
      return failRecipe(DiningRecipeFormError.INGREDIENT_EMPTY)
    }
    if (item.length > DINING_INGREDIENT_MAX_LENGTH) {
      return failRecipe(DiningRecipeFormError.INGREDIENT_TOO_LONG)
    }
    normalized.push(item)
  }
  const trimmedSteps = steps.trim()
  if (trimmedSteps.length > DINING_STEPS_MAX_LENGTH) {
    return failRecipe(DiningRecipeFormError.STEPS_TOO_LONG)
  }
  return {
    valid: true,
    error: null,
    normalizedError: null,
    name: trimmedName,
    ingredients: normalized,
    steps: trimmedSteps.length > 0 ? trimmedSteps : null
  }
}

// ========== 手机号脱敏与「我的选菜」识别 ==========

/**
 * 手机号脱敏（镜像后端 DiningOrderServiceImpl.maskPhone 规则，纯函数便于单测）：
 * 11 位按 3+4+4 掩中间；长度 ≤5 原样返回；其余保留前 3 后 2；空值返回空串。
 */
export function maskDiningPhone(phone: string | null): string {
  const value = (phone ?? '').trim()
  if (value.length === 0) {
    return ''
  }
  if (value.length === 11) {
    return value.substring(0, 3) + '****' + value.substring(7)
  }
  if (value.length <= 5) {
    return value
  }
  return value.substring(0, 3) + '****' + value.substring(value.length - 2)
}

/**
 * 由当前登录手机号在参与者中定位当前用户 ID（端上无 userId 契约，详情成员仅回传脱敏手机号，
 * 故按同源脱敏规则比对；手机号缺失/未命中返回 0——降级为不识别「我的选菜」，不误判）。
 *
 * @param memberIds  成员 userId 列表（与 phoneMaskedList 一一对应）
 * @param phoneMaskedList 成员脱敏手机号列表
 * @param currentPhone 当前登录手机号（getUserInfo.phoneNumber）
 */
export function resolveMyUserId(memberIds: Array<number>, phoneMaskedList: Array<string>,
  currentPhone: string | null): number {
  const masked = maskDiningPhone(currentPhone)
  if (masked.length === 0 || memberIds.length !== phoneMaskedList.length) {
    return 0
  }
  for (let i = 0; i < phoneMaskedList.length; i++) {
    if (phoneMaskedList[i] === masked) {
      return memberIds[i]
    }
  }
  return 0
}

// ========== 点单菜单分组（美团式左分类右菜品派生） ==========

/** 分类锚点（左侧分类栏条目：点击滚动到右侧对应分组） */
export interface DiningMenuSection {
  /** 分类 ID；0 = 未分类分组 */
  categoryId: number
  /** 分组标题（已回填，未分类给展示层传入的兜底文案） */
  categoryName: string
  /** 该分类下的本单菜单菜谱（保序） */
  recipes: Array<DiningMenuRecipeRow>
}

/** 本单菜单行（点单页右侧菜品行） */
export interface DiningMenuRecipeRow {
  recipeId: number
  name: string
  categoryId: number
}

/**
 * 本单菜单按分类分段（对齐 Android groupMenuByCategory）：
 * - 分类 ID 为 0 视为「未分类」段（收尾渲染）；
 * - 段顺序 = 菜单中分类首次出现顺序（保持发起者编排顺序）；
 * - 段名取分类目录 [categories]，目录缺失时按「分类 {id}」兜底、未分类用 [uncategorizedLabel]；
 * - 段内菜品保持菜单顺序。
 */
export function groupDiningMenu(menu: Array<DiningMenuRecipeRow>,
  categories: Array<DiningCategoryName>, uncategorizedLabel: string): Array<DiningMenuSection> {
  if (menu.length === 0) {
    return []
  }
  const sections: Array<DiningMenuSection> = []
  const indexByCategory = new Map<number, number>()
  let uncategorized: DiningMenuSection | null = null
  menu.forEach((row: DiningMenuRecipeRow): void => {
    if (row.categoryId <= 0) {
      // 未分类统一收尾（首次遇到即建段，段内保序）
      if (uncategorized === null) {
        uncategorized = {
          categoryId: 0,
          categoryName: uncategorizedLabel,
          recipes: []
        }
      }
      uncategorized.recipes.push(row)
      return
    }
    let sectionIndex = indexByCategory.get(row.categoryId)
    if (sectionIndex === undefined) {
      const fallbackName = `分类 ${row.categoryId}`
      const section: DiningMenuSection = {
        categoryId: row.categoryId,
        categoryName: categoryNameOf(categories, row.categoryId, fallbackName),
        recipes: []
      }
      sections.push(section)
      sectionIndex = sections.length - 1
      indexByCategory.set(row.categoryId, sectionIndex)
    }
    sections[sectionIndex].recipes.push(row)
  })
  if (uncategorized !== null) {
    sections.push(uncategorized)
  }
  return sections
}

/** 分类名回填源（categoryId → name） */
export interface DiningCategoryName {
  id: number
  name: string
}

/** 分类名查找：未命中给 fallback（「分类 {id}」兜底） */
function categoryNameOf(categories: Array<DiningCategoryName>, categoryId: number, fallback: string): string {
  for (let i = 0; i < categories.length; i++) {
    if (categories[i].id === categoryId) {
      return categories[i].name
    }
  }
  return fallback
}

// ========== 购买清单快照解析 ==========

/** 空快照（解析失败兜底；展示层据此给空态） */
export function emptyDiningShoppingList(): DiningShoppingListSnapshot {
  return { ingredientNames: [], perRecipe: [] }
}

/**
 * 购买清单快照 JSON 文本 → 结构化快照。
 * shoppingList 为后端 ShoppingListAssembler 序列化的 JSON（ingredientNames + perRecipe）；
 * 进行中订单为空串、脏数据/结构漂移统一兜底空快照（结果页不重复聚合，按需重新拉详情）。
 */
export function parseDiningShoppingList(json: string | null): DiningShoppingListSnapshot {
  if (json === null || json.trim().length === 0) {
    return emptyDiningShoppingList()
  }
  try {
    const parsed: Object = JSON.parse(json)
    return diningShoppingListFromJSON(parsed)
  } catch (err) {
    return emptyDiningShoppingList()
  }
}

// ========== 购买清单结果页模型映射 ==========

/** 选菜人展示条目：谁选的（脱敏手机号）+ 备注 + 是否当前用户 */
export interface DiningSelectorShopping {
  /** 选菜人标识（脱敏手机号；缺失兜底「用户 {id}」） */
  label: string
  /** 个人备注（空串 = 未写备注） */
  note: string
  /** 是否当前用户 */
  isMe: boolean
}

/** 按菜备菜视图条目：一道菜及其全部选菜人 */
export interface DiningRecipeShopping {
  recipeName: string
  selectors: Array<DiningSelectorShopping>
}

/** 结果页双视图模型（快照 + 参与者目录 → 展示结构，对齐 Android toShoppingListResult） */
export interface DiningShoppingResult {
  ingredients: Array<string>
  perRecipe: Array<DiningRecipeShopping>
}

/**
 * 快照 → 结果页双视图模型（纯函数）：
 * - 选菜人标识取参与者脱敏手机号，缺失按「用户 {id}」兜底；
 * - [myUserId] 用于标记「我」的选菜（0 = 未取到，不误判）。
 */
export function toDiningShoppingResult(snapshot: DiningShoppingListSnapshot,
  memberIds: Array<number>, memberPhones: Array<string>, myUserId: number): DiningShoppingResult {
  const labelOf = (userId: number): string => {
    for (let i = 0; i < memberIds.length; i++) {
      if (memberIds[i] === userId) {
        const phone = memberPhones[i]
        return phone.length > 0 ? phone : `用户 ${userId}`
      }
    }
    return `用户 ${userId}`
  }
  const perRecipe: Array<DiningRecipeShopping> = []
  snapshot.perRecipe.forEach((view): void => {
    const selectors: Array<DiningSelectorShopping> = []
    view.selectors.forEach((selector): void => {
      selectors.push({
        label: labelOf(selector.userId),
        note: selector.note,
        isMe: myUserId !== 0 && selector.userId === myUserId
      })
    })
    perRecipe.push({ recipeName: view.recipeName, selectors: selectors })
  })
  return { ingredients: snapshot.ingredientNames, perRecipe: perRecipe }
}

// ========== 展示辅助 ==========

/**
 * ISO 时间（yyyy-MM-dd'T'HH:mm:ss / yyyy-MM-dd HH:mm:ss 均兼容）→ 展示串：
 * 同天「今天 HH:mm」、前一天「昨天 HH:mm」、其余「M月d日 HH:mm」；解析失败兜底原文。
 */
export function diningTimeLabel(iso: string | null): string {
  const raw = iso ?? ''
  if (raw.length < 16) {
    return raw
  }
  const dateText = raw.substring(0, 10)
  const timeText = raw.substring(11, 16)
  const parsed = new Date(`${dateText}T00:00:00`)
  if (Number.isNaN(parsed.getTime())) {
    return raw
  }
  const startOfDay = (d: Date): number => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const dayMs = 24 * 60 * 60 * 1000
  const diffDays = Math.round((startOfDay(new Date()) - startOfDay(parsed)) / dayMs)
  if (diffDays === 0) {
    return `今天 ${timeText}`
  }
  if (diffDays === 1) {
    return `昨天 ${timeText}`
  }
  return `${parsed.getMonth() + 1}月${parsed.getDate()}日 ${timeText}`
}

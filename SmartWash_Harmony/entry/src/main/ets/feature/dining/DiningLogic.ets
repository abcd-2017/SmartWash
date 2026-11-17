// 吃啥（dining）域纯函数集 — 无 UI 依赖、无网络依赖（JVM 单测锚点的 ArkTS 对应物，
// 对齐 Android impl logic/* 纯函数：DiningJoinUrlBuilder / DiningJoinUrlParser /
// DiningJoinEntry / DiningOrderFormValidator / DiningRecipeFormValidator /
// DiningShoppingListMappers / DiningMenuNavigator）。
//
// 全部函数确定性输入输出，供 entry/src/test/DiningLogic.test.ets 断言。

import {
  DINING_DEFAULT_CALORIE_TARGET,
  DiningIngredientTotal,
  DiningOrderBriefVo,
  DiningRecipeVo
} from '../../network/vo/DiningVo'
import { diningShoppingListFromJSON, DiningShoppingListSnapshot } from '../../network/vo/DiningVo'


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

/** 扫码内容经本域归一化的三分结果（对齐 Android DiningScanResult） */
export enum DiningScanKind {
  /** 命中本域加入链接：deepLinkUri 可直接交壳层深链体系分发 */
  DEEP_LINK,
  /** 非本域二维码：交还通用分发（深链/内置浏览器/复制） */
  NOT_DINING,
  /** 命中本域加入路径但参数非法：提示后不落通用分发（防坏 URL 打开 WebView 404） */
  MALFORMED
}

/** 扫码归一化结果（kind=DEEP_LINK 时 deepLinkUri 有值） */
export interface DiningScanResult {
  kind: DiningScanKind
  deepLinkUri: string
}

/**
 * 扫码内容归一化（壳层全局扫一扫消费，08 号票能力对齐 Android DiningApi.normalizeScanToDeepLink）：
 * 识别本域加入链接（parseDiningJoinUrl 同源解析），命中返回对应
 * `smartwash://dining/order/{orderId}?t={token}` 深链串（dining 深链注册表同协议单源），
 * 由壳层深链体系分发进加入入口页。加入 URL 的识别知识留在本域，壳层扫码零业务感知。
 */
export function normalizeDiningScan(raw: string | null): DiningScanResult {
  const parsed = parseDiningJoinUrl(raw)
  if (parsed.kind === DiningJoinUrlKind.JOIN) {
    const deepLinkUri = `smartwash://dining/order/${parsed.orderId}?t=${parsed.token}`
    return { kind: DiningScanKind.DEEP_LINK, deepLinkUri }
  }
  if (parsed.kind === DiningJoinUrlKind.MALFORMED) {
    return { kind: DiningScanKind.MALFORMED, deepLinkUri: '' }
  }
  return { kind: DiningScanKind.NOT_DINING, deepLinkUri: '' }
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


/** 空快照（解析失败兜底；展示层据此给空态，v2 字段按「无 v2 数据」降级） */
export function emptyDiningShoppingList(): DiningShoppingListSnapshot {
  return {
    ingredientNames: [],
    perRecipe: [],
    ingredientTotals: null,
    totalCalories: null,
    calorieKnownDishCount: null,
    calorieUnknownDishCount: null,
    perDishCalories: null
  }
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

/** 配料聚合行 UI 模型（快照 v2「按食材」渲染单元：名称 + 总量单位 + 菜数徽标） */
export interface DiningIngredientTotalUi {
  name: string
  /** 聚合总量（null=来源未给出，仅列名不显数量） */
  totalValue: number | null
  /** 归一后单位（g/ml/个/…；空串=未给出） */
  unit: string
  /** 该配料出现的菜数（0=未给出，隐藏「N 道菜」徽标） */
  dishCount: number
}

/**
 * 结果页双视图模型（快照 + 参与者目录 → 展示结构，对齐 Android toShoppingListResult）。
 * v2 字段（契约 §3.3 additive）：老快照为 null → 消费方按「无 v2 数据」降级
 * （热量汇总隐藏、「按食材」tab 回落 ingredients 逐名渲染）。
 */
export interface DiningShoppingResult {
  ingredients: Array<string>
  perRecipe: Array<DiningRecipeShopping>
  /** v2：配料聚合行；null=老快照无 v2 数据 */
  ingredientTotals: Array<DiningIngredientTotalUi> | null
  /** v2：已知热量菜的份热量之和（Kcal）；null=老快照无 v2 数据或无一菜已知 */
  totalCalories: number | null
  /** v2：热量未知菜数（未计入总热量）；null=老快照无 v2 数据 */
  calorieUnknownDishCount: number | null
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
  // v2 字段透传（老快照 ingredientTotals 为 null → 结果模型同为 null，消费方降级渲染）
  const totals: Array<DiningIngredientTotalUi> | null = snapshot.ingredientTotals === null
    ? null
    : snapshot.ingredientTotals.map((total: DiningIngredientTotal): DiningIngredientTotalUi => ({
      name: total.name,
      totalValue: total.totalValue,
      unit: total.unit,
      dishCount: total.dishCount
    }))
  return {
    ingredients: snapshot.ingredientNames,
    perRecipe: perRecipe,
    ingredientTotals: totals,
    totalCalories: snapshot.totalCalories,
    calorieUnknownDishCount: snapshot.calorieUnknownDishCount
  }
}

/**
 * 购买清单文本导出（纯函数）：订单标题 + 「按食材买菜」勾选态（[x] 已买 / [ ] 未买）
 * + 「按菜备菜」选菜人（含备注）。跨端同口径（对齐 Android DiningShoppingListText.kt），
 * 供「复制清单」发给家人/自己对着买。
 */
export function buildShoppingListText(orderTitle: string, result: DiningShoppingResult,
  checked: Array<string>): string {
  const lines: string[] = []
  lines.push('吃啥 · 购买清单')
  if (orderTitle.trim().length > 0) {
    lines.push(`订单：${orderTitle}`)
  }
  lines.push('')
  lines.push('【按食材买菜】')
  if (result.ingredients.length === 0) {
    lines.push('（无）')
  } else {
    result.ingredients.forEach((name: string): void => {
      lines.push(`${checked.indexOf(name) >= 0 ? '[x] ' : '[ ] '}${name}`)
    })
  }
  lines.push('')
  lines.push('【按菜备菜】')
  if (result.perRecipe.length === 0) {
    lines.push('（无）')
  } else {
    result.perRecipe.forEach((recipe: DiningRecipeShopping): void => {
      let line = `- ${recipe.recipeName}`
      if (recipe.selectors.length > 0) {
        const who = recipe.selectors.map((selector: DiningSelectorShopping): string =>
          selector.note.length > 0 ? `${selector.label}（${selector.note}）` : selector.label
        ).join('、')
        line = `${line}：${who}`
      }
      lines.push(line)
    })
  }
  return lines.join('\n')
}


// ========== 营养计算（对齐 Android DiningNutritionCalculator，四端同口径） ==========

/**
 * 份量假设常量（g）：一份菜按 250g 估算（契约 §二拍板，四端一致写死并注释）。
 * 每 100g 口径的营养折算到份 = 值 × 250 ÷ 100；与后端 DiningRecipeGate.PORTION_GRAMS、
 * Android DiningNutritionCalculator.PORTION_GRAMS、Web dict.js DINING_PORTION_GRAMS 同一常量。
 */
export const DINING_PORTION_GRAMS: number = 250

/**
 * 份热量口径（契约 §二，四端一致）：caloriesTotal（HowToCook 整份预估）优先；
 * 否则 caloriesPer100g（CookLikeHOC 营养表）× 250g ÷ 100；两者皆缺返回 null
 * （热量未知，不参与热量聚合，UI 显示「热量未知」次级样式）。
 */
export function diningServingCalories(caloriesTotal: number | null,
  caloriesPer100g: number | null): number | null {
  if (caloriesTotal !== null) {
    return caloriesTotal
  }
  if (caloriesPer100g === null) {
    return null
  }
  return caloriesPer100g * DINING_PORTION_GRAMS / 100
}

/**
 * 数量展示格式化（对齐 Android DiningNutritionCalculator.formatAmount，跨端一致格式）：
 * 整数去尾零（1830.0 → "1830"），非整数最多保留 1 位小数（666.66 → "666.7"）；
 * null/非有限值返回 null（热量未知态由调用方渲染「热量未知」）。
 */
export function formatDiningAmount(value: number | null): string | null {
  if (value === null || !Number.isFinite(value)) {
    return null
  }
  const rounded = Math.round(value * 10) / 10
  if (Number.isInteger(rounded)) {
    return `${rounded}`
  }
  return rounded.toFixed(1)
}


// ========== 忌口警示（对齐 Android DiningAllergenGuard，四端同口径） ==========

/** 忌口标签白名单（与后端 Pattern、设置页 7 个多选 chip 同源） */
export const DINING_ALLERGEN_CODES: Array<string> =
  ['SEAFOOD', 'NUT', 'GLUTEN', 'DAIRY', 'EGG', 'SOY', 'SPICY']

/** 选中菜的过敏原事实（页面由选中 VO + 详情缓存的 allergens 组装） */
export interface DiningSelectedDish {
  recipeId: number
  recipeName: string
  /** 该菜过敏原码集合（null=未下发/未拉取，按无过敏原参与） */
  allergens: Array<string> | null
}

/** 单条命中：哪道菜命中了哪个忌口（allergen 为归一化后的枚举码，UI 映射中文文案） */
export interface DiningAllergenHit {
  recipeId: number
  recipeName: string
  allergen: string
}

/** 单个码归一化：null/空白返回 null，其余去空白 + 大写（脏数据不抛出） */
export function normalizeDiningAllergen(code: string | null): string | null {
  if (code === null) {
    return null
  }
  const trimmed = code.trim()
  return trimmed.length > 0 ? trimmed.toUpperCase() : null
}

/** 归一化 + 去重（保序）；入参 null 兜底空数组 */
export function normalizeDiningAllergens(codes: Array<string> | null): Array<string> {
  if (codes === null) {
    return []
  }
  const result: Array<string> = []
  for (let i = 0; i < codes.length; i++) {
    const normalized = normalizeDiningAllergen(codes[i])
    if (normalized !== null && result.indexOf(normalized) < 0) {
      result.push(normalized)
    }
  }
  return result
}

/**
 * 选中菜过敏原 ∩ 用户忌口求交（保选中菜顺序；同一道菜内按归一化去重；用户忌口为空返回空表）。
 * 端上只负责「已登记过敏原」的交集，生僻配料后端为 NULL 的不参与（覆盖率风险见契约 §五.3）。
 */
export function diningAllergenHits(dishes: Array<DiningSelectedDish>,
  userAllergens: Array<string> | null): Array<DiningAllergenHit> {
  const user = normalizeDiningAllergens(userAllergens)
  if (user.length === 0) {
    return []
  }
  const hits: Array<DiningAllergenHit> = []
  for (let i = 0; i < dishes.length; i++) {
    const dish = dishes[i]
    const dishAllergens = normalizeDiningAllergens(dish.allergens)
    for (let j = 0; j < dishAllergens.length; j++) {
      if (user.indexOf(dishAllergens[j]) >= 0) {
        hits.push({ recipeId: dish.recipeId, recipeName: dish.recipeName, allergen: dishAllergens[j] })
      }
    }
  }
  return hits
}


// ========== 份量建议（对齐 Android DiningPortionAdvisor，四端同口径） ==========

/** 人数下限（stepper 与人均共用） */
export const DINING_PEOPLE_MIN: number = 1
/** 人数上限（stepper 与人均共用） */
export const DINING_PEOPLE_MAX: number = 20
/** 每日餐次（参考值 = 日目标 ÷ 3，与营养超标警示同一口径） */
export const DINING_MEALS_PER_DAY: number = 3
/** 够一餐下限系数（人均 ≥ 参考 × 0.9） */
export const DINING_PORTION_LOWER_RATIO: number = 0.9
/** 够一餐上限系数（人均 ≤ 参考 × 1.1） */
export const DINING_PORTION_UPPER_RATIO: number = 1.1

/** 份量判定 */
export enum DiningPortionVerdict {
  /** 人均在参考 0.9-1.1 倍区间：够一餐 */
  ENOUGH,
  /** 人均高于参考 1.1 倍：偏多 */
  MORE,
  /** 人均低于参考 0.9 倍：偏少 */
  LESS
}

/** 建议结果（页面文案与本地态人数回显共用） */
export interface DiningPortionAdvice {
  /** 收敛后的人数（1-20） */
  peopleCount: number
  /** 人均热量（kcal） */
  perCapita: number
  /** 一餐参考热量（kcal）= 目标 ÷ 3 */
  reference: number
  verdict: DiningPortionVerdict
}

/** 人数收敛到契约区间（1-20）：越界钳制、异常值兜底，页面 stepper 与人均共用 */
export function coerceDiningPeopleCount(value: number): number {
  if (!Number.isFinite(value)) {
    return DINING_PEOPLE_MIN
  }
  const rounded = Math.round(value)
  if (rounded < DINING_PEOPLE_MIN) {
    return DINING_PEOPLE_MIN
  }
  if (rounded > DINING_PEOPLE_MAX) {
    return DINING_PEOPLE_MAX
  }
  return rounded
}

/**
 * 已选菜热量合计（份热量口径）：仅统计已知热量的菜；全部未知/空列表返回 null
 * （页面不渲染建议，不谎报）。对齐 Android DiningNutritionCalculator.summarize.totalCalories。
 */
export function diningSelectedTotalCalories(recipes: Array<DiningRecipeVo>): number | null {
  let total = 0
  let knownCount = 0
  for (let i = 0; i < recipes.length; i++) {
    const calories = diningServingCalories(recipes[i].caloriesTotal, recipes[i].caloriesPer100g)
    if (calories !== null) {
      total += calories
      knownCount++
    }
  }
  return knownCount > 0 ? total : null
}

/**
 * 计算份量建议（契约 §2.5）。
 * @param totalCalories 本单已知热量菜之和（未知为 null → 返回 null）
 * @param peopleCount 人数（越界收敛到 1-20）
 * @param calorieTargetPerDay 用户每日热量目标（≤0 时用默认 2000）
 */
export function diningPortionAdvice(totalCalories: number | null, peopleCount: number,
  calorieTargetPerDay: number): DiningPortionAdvice | null {
  if (totalCalories === null) {
    return null
  }
  const people = coerceDiningPeopleCount(peopleCount)
  const target = calorieTargetPerDay > 0 ? calorieTargetPerDay : DINING_DEFAULT_CALORIE_TARGET
  const reference = target / DINING_MEALS_PER_DAY
  const perCapita = totalCalories / people
  let verdict: DiningPortionVerdict = DiningPortionVerdict.ENOUGH
  if (perCapita > reference * DINING_PORTION_UPPER_RATIO) {
    verdict = DiningPortionVerdict.MORE
  } else if (perCapita < reference * DINING_PORTION_LOWER_RATIO) {
    verdict = DiningPortionVerdict.LESS
  }
  return { peopleCount: people, perCapita: perCapita, reference: reference, verdict: verdict }
}

/**
 * 今日剩余热量（契约 §三.3）：目标 - 本单总热量；总热量未知返回 null（页面不渲染）。
 * 负值 = 本单超目标，页面用 danger 色渲染。
 */
export function diningCalorieRemaining(totalCalories: number | null,
  calorieTargetPerDay: number): number | null {
  if (totalCalories === null) {
    return null
  }
  const target = calorieTargetPerDay > 0 ? calorieTargetPerDay : DINING_DEFAULT_CALORIE_TARGET
  return target - totalCalories
}


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

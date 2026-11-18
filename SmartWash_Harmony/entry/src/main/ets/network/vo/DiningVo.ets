/**
 * 吃啥（dining）域视图对象 — 与后端 dining/vo/*.java 一一对应（契约源：
 * WebDiningCategoryController / WebDiningRecipeController / WebDiningOrderController /
 * WebDiningOrderItemController，需 ROLE_USER）。
 *
 * 字段容错（对齐 NoticeVo 约定）：Jackson 可能省略 null 字段，interface 运行时不填充，
 * 一律经 fromJSON 显式映射兜底，消费点不读可空原始字段。
 * 时间字段保留后端 ISO 原文（如 2026-11-13T20:30:00），格式化由展示层处理
 * （utils/TimeUtil.formatOrderTime 对 T 分隔符同样兼容：日期取 0-10 位、时分取 11-16 位）。
 */
import { jsonBool, jsonNum, jsonStr, jsonStrArray, mapArray } from './JsonParse'

/** 泛型分页信封（后端 MyBatis-Plus IPage 序列化：records/total/size/current） */
export interface DiningPageData<T> {
  records: Array<T>
  total: number
  size: number
  current: number
}

/** JSON → 泛型分页信封；raw 缺失兜底空页 */
export function diningPageDataFromJSON<T>(raw: Object | null | undefined,
  mapper: (item: Object) => T): DiningPageData<T> {
  if (raw === null || raw === undefined) {
    return { records: [], total: 0, size: 0, current: 0 }
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    records: mapArray(obj['records'], mapper),
    total: jsonNum(obj['total']) ?? 0,
    size: jsonNum(obj['size']) ?? 0,
    current: jsonNum(obj['current']) ?? 0
  }
}


/** 菜谱分类（GET /web/auth/dining/categories/all 载荷，sort 升序） */
export interface DiningCategoryVo {
  id: number
  name: string
  sort: number
}

/** JSON → DiningCategoryVo；缺失字段回落 0/空串 */
export function diningCategoryVoFromJSON(raw: Object): DiningCategoryVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    id: jsonNum(obj['id']) ?? 0,
    name: jsonStr(obj['name']) ?? '',
    sort: jsonNum(obj['sort']) ?? 0
  }
}


/** 菜谱（GET /web/auth/dining/recipes/all 与 /{id} 载荷；用户端仅返回上架） */
export interface DiningRecipeVo {
  id: number
  /** 所属分类 ID；0 = 未分类（后端 null 归一） */
  categoryId: number
  /** 所属分类名称（后端联查回填，未分类为空串） */
  categoryName: string
  name: string
  /** 封面对外直链（MinIO dining/covers/ 白名单匿名读），空串 = 未上传 */
  coverUrl: string
  /** 食材名数组（缺失兜底空数组） */
  ingredients: Array<string>
  /** 做法步骤纯文本（可含换行） */
  steps: string
  /** 来源：ADMIN=运营录入 / USER=用户新增 */
  source: string
  /** 上下架：1=上架 0=下架（用户端恒为 1，防御性兜底） */
  status: number
  // ---- 营养字段（契约 §3.4，四端对齐后端 DiningRecipeVo Jackson 驼峰序列化）----
  // 可空语义：后端 DECIMAL 列可空（来源未给出即 null），Jackson 反序列化后可能整字段省略；
  // 映射层缺失/类型漂移一律兜底 null，消费端经 DiningLogic.diningServingCalories 份热量口径派生，
  // 不直接对原始可空字段做算术。
  /** 热量 Kcal/100g（CookLikeHOC 营养表口径；null=未录入） */
  caloriesPer100g: number | null
  /** 整份估算热量 Kcal（HowToCook 预估卡路里口径；null=未录入） */
  caloriesTotal: number | null
  /** 蛋白质 g/100g（null=未录入） */
  proteinG: number | null
  /** 脂肪 g/100g（null=未录入） */
  fatG: number | null
  /** 碳水化合物 g/100g（null=未录入） */
  carbsG: number | null
  /** 钠 mg/100g（null=未录入） */
  sodiumMg: number | null
  // ---- 收藏 / 忌口（契约 §2.2/§2.3，四端对齐后端 DiningRecipeVo）----
  // 可空语义：Jackson 可能省略 null 字段，映射层缺失兜底 null，消费点不读可空原始字段。
  /** 当前用户是否已收藏（列表批量填充 / 详情同字段；null=未下发，按未收藏降级） */
  favorited: boolean | null
  /** 该菜全部配料的过敏原去重集合（仅详情接口拼装；null=未下发/无过敏原） */
  allergens: Array<string> | null
}

/** JSON → DiningRecipeVo */
export function diningRecipeVoFromJSON(raw: Object): DiningRecipeVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  const rawCategoryId = jsonNum(obj['categoryId'])
  return {
    id: jsonNum(obj['id']) ?? 0,
    categoryId: rawCategoryId === null ? 0 : rawCategoryId,
    categoryName: jsonStr(obj['categoryName']) ?? '',
    name: jsonStr(obj['name']) ?? '',
    coverUrl: jsonStr(obj['coverUrl']) ?? '',
    ingredients: jsonStrArray(obj['ingredients']).filter((item: string): boolean => item.length > 0),
    steps: jsonStr(obj['steps']) ?? '',
    source: jsonStr(obj['source']) ?? '',
    status: jsonNum(obj['status']) ?? 1,
    caloriesPer100g: jsonNum(obj['caloriesPer100g']),
    caloriesTotal: jsonNum(obj['caloriesTotal']),
    proteinG: jsonNum(obj['proteinG']),
    fatG: jsonNum(obj['fatG']),
    carbsG: jsonNum(obj['carbsG']),
    sodiumMg: jsonNum(obj['sodiumMg']),
    favorited: jsonBool(obj['favorited']),
    allergens: jsonStrArray(obj['allergens']).filter((item: string): boolean => item.length > 0)
  }
}


/**
 * 用户饮食偏好（GET/PUT /web/auth/dining/dietary-preference 载荷，契约 §2.1）。
 *
 * 字段 null = 默认值语义：端上兜底热量 2000 kcal / 钠 2000 mg；allergens 空列表 = 无忌口。
 * 同一接口兼作 PUT 请求体（后端同名字段）：任一字段 null = 恢复默认（allergens null=清空忌口）。
 * 忌口标签枚举：SEAFOOD/NUT/GLUTEN/DAIRY/EGG/SOY/SPICY（后端白名单）。
 */
export interface DietaryPreferenceVo {
  /** 忌口标签集合，null/缺失按空列表兜底（无忌口） */
  allergens: Array<string> | null
  /** 每日热量目标 kcal，null=默认 2000（有效区间 1200-4000） */
  calorieTarget: number | null
  /** 每日钠目标 mg，null=默认 2000（有效区间 1000-4000） */
  sodiumTarget: number | null
}

/** JSON → DietaryPreferenceVo；缺失字段兜底 null（默认值语义） */
export function dietaryPreferenceVoFromJSON(raw: Object): DietaryPreferenceVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    allergens: jsonStrArray(obj['allergens']).filter((item: string): boolean => item.length > 0),
    calorieTarget: jsonNum(obj['calorieTarget']),
    sodiumTarget: jsonNum(obj['sodiumTarget'])
  }
}

/** 默认每日热量目标（kcal）：后端 null 语义的端上兜底值（契约 §2.1） */
export const DINING_DEFAULT_CALORIE_TARGET: number = 2000
/** 默认每日钠目标（mg）：后端 null 语义的端上兜底值（契约 §2.1） */
export const DINING_DEFAULT_SODIUM_TARGET: number = 2000
/** 热量目标有效区间（与后端 @Min/@Max、设置页 stepper 一致） */
export const DINING_CALORIE_MIN: number = 1200
export const DINING_CALORIE_MAX: number = 4000
/** 钠目标有效区间（与后端 @Min/@Max、设置页 stepper 一致） */
export const DINING_SODIUM_MIN: number = 1000
export const DINING_SODIUM_MAX: number = 4000


/** 我的订单列表条目（GET /web/auth/dining/orders/my 载荷：我发起的 + 我参与的，创建时间倒序） */
export interface DiningOrderListVo {
  id: number
  title: string
  /** DRAFT / ONGOING / FINISHED（白名单外脏数据由映射层保守降级为 FINISHED） */
  status: string
  /** 是否我发起的 */
  isCreator: boolean
  creatorUserId: number
  /** 创建时间（ISO 原文） */
  createdAt: string
  /** 结束时间（进行中为空串） */
  finishedAt: string
}

/** JSON → DiningOrderListVo */
export function diningOrderListVoFromJSON(raw: Object): DiningOrderListVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    id: jsonNum(obj['id']) ?? 0,
    title: jsonStr(obj['title']) ?? '',
    status: jsonStr(obj['status']) ?? '',
    isCreator: jsonBool(obj['isCreator']) ?? false,
    creatorUserId: jsonNum(obj['creatorUserId']) ?? 0,
    createdAt: jsonStr(obj['createdAt']) ?? '',
    finishedAt: jsonStr(obj['finishedAt']) ?? ''
  }
}


/** 创建点单订单返回（POST /web/auth/dining/orders/create 载荷；joinToken 是拼二维码唯一来源） */
export interface DiningOrderCreatedVo {
  orderId: number
  joinToken: string
}

/** JSON → DiningOrderCreatedVo */
export function diningOrderCreatedVoFromJSON(raw: Object): DiningOrderCreatedVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    orderId: jsonNum(obj['orderId']) ?? 0,
    joinToken: jsonStr(obj['joinToken']) ?? ''
  }
}

// ========== 订单详情（轮询主接口） ==========

/** 本单菜单条目（发起者编排的选菜范围，按加入菜单顺序） */
export interface DiningMenuRecipe {
  recipeId: number
  name: string
  /** 分类 ID；0 = 未分类（端上按分类组织左侧分类栏） */
  categoryId: number
}

/** 选菜记录条目（共享购物车明细：谁选了哪道菜 + 备注） */
export interface DiningOrderItem {
  recipeId: number
  userId: number
  note: string
}

/** 参与者条目 */
export interface DiningOrderMember {
  userId: number
  /** 是否发起者 */
  isCreator: boolean
  /** 脱敏手机号（如 139****9000），作选菜人标识 */
  phoneMasked: string
}

/**
 * 点单订单详情（GET /web/auth/dining/orders/{id} 载荷，轮询主接口）。
 *
 * version 轮询协议：knownVersion 与服务端一致 → unchanged=true 轻量响应（仅
 * id/version/unchanged，其余字段空）；不一致或未带 → unchanged=false 全量响应。
 * joinToken 仅发起者的全量响应下发（参与者与轻量响应为空串，端上沿用首次全量值）。
 */
export interface DiningOrderDetailVo {
  id: number
  /** 服务端当前 version（轮询协议基准） */
  version: number
  /** true=与已知 version 一致（轻量响应）；缺失按 false 兜底 */
  unchanged: boolean
  title: string
  /** DRAFT / ONGOING / FINISHED（DRAFT 菜单可加/移除，全部成员可加入） */
  status: string
  creatorUserId: number
  menu: Array<DiningMenuRecipe>
  items: Array<DiningOrderItem>
  members: Array<DiningOrderMember>
  /** 结束时间（进行中为空串，ISO 原文） */
  finishedAt: string
  /** 购买清单快照 JSON 文本（结束时写入，进行中为空串） */
  shoppingList: string
  /** 加入凭证（仅发起者全量响应下发） */
  joinToken: string
}

/** JSON → DiningMenuRecipe */
function diningMenuRecipeFromJSON(raw: Object): DiningMenuRecipe {
  const obj: Record<string, Object> = raw as Record<string, Object>
  const rawCategoryId = jsonNum(obj['categoryId'])
  return {
    recipeId: jsonNum(obj['recipeId']) ?? 0,
    name: jsonStr(obj['name']) ?? '',
    categoryId: rawCategoryId === null ? 0 : rawCategoryId
  }
}

/** JSON → DiningOrderItem */
function diningOrderItemFromJSON(raw: Object): DiningOrderItem {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    recipeId: jsonNum(obj['recipeId']) ?? 0,
    userId: jsonNum(obj['userId']) ?? 0,
    note: jsonStr(obj['note']) ?? ''
  }
}

/** JSON → DiningOrderMember */
function diningOrderMemberFromJSON(raw: Object): DiningOrderMember {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    userId: jsonNum(obj['userId']) ?? 0,
    isCreator: jsonBool(obj['isCreator']) ?? false,
    phoneMasked: jsonStr(obj['phoneMasked']) ?? ''
  }
}

/** JSON → DiningOrderDetailVo */
export function diningOrderDetailVoFromJSON(raw: Object): DiningOrderDetailVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    id: jsonNum(obj['id']) ?? 0,
    version: jsonNum(obj['version']) ?? 0,
    unchanged: jsonBool(obj['unchanged']) ?? false,
    title: jsonStr(obj['title']) ?? '',
    status: jsonStr(obj['status']) ?? '',
    creatorUserId: jsonNum(obj['creatorUserId']) ?? 0,
    menu: mapArray(obj['menu'], diningMenuRecipeFromJSON),
    items: mapArray(obj['items'], diningOrderItemFromJSON),
    members: mapArray(obj['members'], diningOrderMemberFromJSON),
    finishedAt: jsonStr(obj['finishedAt']) ?? '',
    shoppingList: jsonStr(obj['shoppingList']) ?? '',
    joinToken: jsonStr(obj['joinToken']) ?? ''
  }
}


/** 加入前预览 brief（GET /web/auth/dining/orders/brief/{token} 载荷，确认弹窗数据源） */
export interface DiningOrderBriefVo {
  orderId: number
  title: string
  /** DRAFT / ONGOING / FINISHED（DRAFT 亦可加入） */
  status: string
  /** 发起者标识（脱敏手机号） */
  creatorName: string
  /** 本单菜单菜品数 */
  recipeCount: number
  /** 当前用户是否已在单中（已加入时端上可直接进详情页） */
  joined: boolean
}

/** JSON → DiningOrderBriefVo */
export function diningOrderBriefVoFromJSON(raw: Object): DiningOrderBriefVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    orderId: jsonNum(obj['orderId']) ?? 0,
    title: jsonStr(obj['title']) ?? '',
    status: jsonStr(obj['status']) ?? '',
    creatorName: jsonStr(obj['creatorName']) ?? '',
    recipeCount: jsonNum(obj['recipeCount']) ?? 0,
    joined: jsonBool(obj['joined']) ?? false
  }
}


/** 结束点单响应（POST /web/auth/dining/orders/{id}/finish 载荷；幂等返回库内原快照） */
export interface DiningOrderFinishVo {
  id: number
  title: string
  status: string
  finishedAt: string
  /** 购买清单快照 JSON 文本 */
  shoppingList: string
}

/** JSON → DiningOrderFinishVo */
export function diningOrderFinishVoFromJSON(raw: Object): DiningOrderFinishVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    id: jsonNum(obj['id']) ?? 0,
    title: jsonStr(obj['title']) ?? '',
    status: jsonStr(obj['status']) ?? '',
    finishedAt: jsonStr(obj['finishedAt']) ?? '',
    shoppingList: jsonStr(obj['shoppingList']) ?? ''
  }
}


/** 选菜人条目：谁选的 + 个人备注 */
export interface DiningShoppingSelector {
  userId: number
  note: string
}

/** 按菜视图条目：一道菜及其全部选菜人 */
export interface DiningShoppingRecipeView {
  recipeName: string
  selectors: Array<DiningShoppingSelector>
}

/**
 * 配料聚合条目（快照 v2：按 (配料名, 归一单位) 分组的可加总量，kg/L 已由后端归一为 g/ml）。
 * 可空字段 = 后端来源未给出（Jackson 驼峰序列化可能省略），消费端兜底展示。
 */
export interface DiningIngredientTotal {
  name: string
  /** 聚合总量（null=来源未给出，仅列名不显数量） */
  totalValue: number | null
  /** 归一后单位（g/ml/个/…；空串=未给出） */
  unit: string
  /** 该配料出现的菜数（0=未给出，隐藏「N 道菜」徽标） */
  dishCount: number
  /** 组内单菜贡献（哪道菜贡献了多少量） */
  perDish: Array<DiningDishAmount>
}

/** 聚合组内单菜贡献：哪道菜贡献了多少量（数值已按单位归一） */
export interface DiningDishAmount {
  recipeName: string
  /** 数量数值（null=来源未给出） */
  amountValue: number | null
  /** 单位（空串=未给出） */
  amountUnit: string
}

/** 逐菜热量条目（仅已知热量的菜；calories null=来源未给出） */
export interface DiningDishCalories {
  recipeName: string
  /** 份热量 Kcal（口径见 DiningRecipeVo 营养字段注释） */
  calories: number | null
}

/**
 * 购买清单快照载荷（dining_order.shopping_list JSON 文本的结构化结果，
 * 与后端 ShoppingListAssembler.ShoppingList 序列化一一对应）：
 * - ingredientNames：按菜去重后所选菜谱食材名并集去重（保序）——v1 字段，行为不变；
 * - perRecipe：按菜视图（仅含被勾选过的菜），顺序跟随本单菜单编排序——v1 字段，行为不变；
 * - v2 additive（契约 §3.3）：ingredientTotals/totalCalories/calorieKnownDishCount/
 *   calorieUnknownDishCount/perDishCalories。老快照无 v2 键 → 映射为 null，
 *   消费方按「无 v2 数据」降级（热量汇总隐藏、按食材 tab 回落 ingredientNames 逐名渲染）。
 */
export interface DiningShoppingListSnapshot {
  ingredientNames: Array<string>
  perRecipe: Array<DiningShoppingRecipeView>
  /** v2：配料聚合行；null=老快照无 v2 数据 */
  ingredientTotals: Array<DiningIngredientTotal> | null
  /** v2：已知热量菜的份热量之和（Kcal）；null=老快照无 v2 数据或无一菜已知 */
  totalCalories: number | null
  /** v2：热量已知菜数；null=老快照无 v2 数据 */
  calorieKnownDishCount: number | null
  /** v2：热量未知菜数（未计入总热量）；null=老快照无 v2 数据 */
  calorieUnknownDishCount: number | null
  /** v2：逐菜热量（仅已知热量菜）；null=老快照无 v2 数据 */
  perDishCalories: Array<DiningDishCalories> | null
}

/** JSON → DiningIngredientTotal */
function diningIngredientTotalFromJSON(raw: Object): DiningIngredientTotal {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    name: jsonStr(obj['name']) ?? '',
    totalValue: jsonNum(obj['totalValue']),
    unit: jsonStr(obj['unit']) ?? '',
    dishCount: jsonNum(obj['dishCount']) ?? 0,
    perDish: mapArray(obj['perDish'], diningDishAmountFromJSON)
  }
}

/** JSON → DiningDishAmount */
function diningDishAmountFromJSON(raw: Object): DiningDishAmount {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    recipeName: jsonStr(obj['recipeName']) ?? '',
    amountValue: jsonNum(obj['amountValue']),
    amountUnit: jsonStr(obj['amountUnit']) ?? ''
  }
}

/** JSON → DiningDishCalories */
function diningDishCaloriesFromJSON(raw: Object): DiningDishCalories {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    recipeName: jsonStr(obj['recipeName']) ?? '',
    calories: jsonNum(obj['calories'])
  }
}

/** 空快照（解析失败兜底；v2 字段一律 null，消费方按「无 v2 数据」降级） */
function emptyShoppingSnapshot(): DiningShoppingListSnapshot {
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

/** JSON → DiningShoppingListSnapshot；解析失败/结构漂移兜底空快照（展示层给空态） */
export function diningShoppingListFromJSON(raw: Object | null): DiningShoppingListSnapshot {
  if (raw === null || typeof raw !== 'object') {
    return emptyShoppingSnapshot()
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  // v2 字段按存在性映射：键缺失/null → null（对齐契约 §3.3 additive 向后兼容语义）
  const rawTotals: Object | null | undefined = obj['ingredientTotals']
  const rawDishCalories: Object | null | undefined = obj['perDishCalories']
  return {
    ingredientNames: jsonStrArray(obj['ingredientNames']).filter((item: string): boolean => item.length > 0),
    perRecipe: mapArray(obj['perRecipe'], (item: Object): DiningShoppingRecipeView => {
      const recipe: Record<string, Object> = item as Record<string, Object>
      return {
        recipeName: jsonStr(recipe['recipeName']) ?? '',
        selectors: mapArray(recipe['selectors'], (sel: Object): DiningShoppingSelector => {
          const selObj: Record<string, Object> = sel as Record<string, Object>
          return {
            userId: jsonNum(selObj['userId']) ?? 0,
            note: jsonStr(selObj['note']) ?? ''
          }
        })
      }
    }),
    ingredientTotals: rawTotals === undefined || rawTotals === null
      ? null : mapArray(rawTotals, diningIngredientTotalFromJSON),
    totalCalories: jsonNum(obj['totalCalories']),
    calorieKnownDishCount: jsonNum(obj['calorieKnownDishCount']),
    calorieUnknownDishCount: jsonNum(obj['calorieUnknownDishCount']),
    perDishCalories: rawDishCalories === undefined || rawDishCalories === null
      ? null : mapArray(rawDishCalories, diningDishCaloriesFromJSON)
  }
}

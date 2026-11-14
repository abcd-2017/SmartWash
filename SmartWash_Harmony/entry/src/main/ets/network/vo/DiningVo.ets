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

// ========== 分类 ==========

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

// ========== 菜谱 ==========

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
    status: jsonNum(obj['status']) ?? 1
  }
}

// ========== 订单列表 ==========

/** 我的订单列表条目（GET /web/auth/dining/orders/my 载荷：我发起的 + 我参与的，创建时间倒序） */
export interface DiningOrderListVo {
  id: number
  title: string
  /** ONGOING / FINISHED（白名单外脏数据由映射层保守降级为 FINISHED） */
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

// ========== 创建响应 ==========

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
  /** ONGOING / FINISHED */
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

// ========== 加入前预览 ==========

/** 加入前预览 brief（GET /web/auth/dining/orders/brief/{token} 载荷，确认弹窗数据源） */
export interface DiningOrderBriefVo {
  orderId: number
  title: string
  /** ONGOING / FINISHED */
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

// ========== 结束点单响应 ==========

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

// ========== 购买清单快照 ==========

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
 * 购买清单快照载荷（dining_order.shopping_list JSON 文本的结构化结果，
 * 与后端 ShoppingListAssembler.ShoppingList 序列化一一对应）：
 * - ingredientNames：按菜去重后所选菜谱食材名并集去重（保序）；
 * - perRecipe：按菜视图（仅含被勾选过的菜），顺序跟随本单菜单编排序。
 */
export interface DiningShoppingListSnapshot {
  ingredientNames: Array<string>
  perRecipe: Array<DiningShoppingRecipeView>
}

/** JSON → DiningShoppingListSnapshot；解析失败/结构漂移兜底空快照（展示层给空态） */
export function diningShoppingListFromJSON(raw: Object | null): DiningShoppingListSnapshot {
  if (raw === null || typeof raw !== 'object') {
    return { ingredientNames: [], perRecipe: [] }
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
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
    })
  }
}

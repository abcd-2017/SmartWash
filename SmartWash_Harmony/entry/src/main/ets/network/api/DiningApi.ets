// 吃啥（dining）域用户端接口（契约源：后端 dining/controller/WebDiningCategoryController /
// WebDiningRecipeController / WebDiningOrderController / WebDiningOrderItemController，需 ROLE_USER）
//
// 23 个端点与 Android feature/dining/api/DiningServiceApi.kt 逐接口对齐：
//   分类   GET  /web/auth/dining/categories/all
//   菜谱   GET  /web/auth/dining/recipes/all          （上架分页：categoryId/keyword 筛选）
//          GET  /web/auth/dining/recipes/{id}         （详情，仅上架）
//          POST /web/auth/dining/recipes/add          （source=USER 直上架）
//          POST /web/auth/dining/recipes/cover        （multipart 封面上传，返回对象键）
//          POST /web/auth/dining/recipes/{id}/favorite（收藏，幂等）
//          DELETE /web/auth/dining/recipes/{id}/favorite（取消收藏，幂等）
//          GET  /web/auth/dining/recipes/favorites     （我的收藏分页，字段同列表）
//   偏好   GET/PUT /web/auth/dining/dietary-preference（忌口 + 每日热量/钠目标）
//   订单   POST /web/auth/dining/orders/create        （创建 DRAFT：title/recipeIds 均可空或 fromOrderId 复用）
//          POST /web/auth/dining/orders/{id}/submit   （DRAFT→FINISHED；仅发起者；标题必填+菜单非空；version；
//                                                     聚合本单菜单生成 shopping_list，返回 DiningOrderFinishVo）
//          POST /web/auth/dining/orders/{id}/dishes   （草稿加菜；全部成员；幂等；version）
//          DELETE /web/auth/dining/orders/{id}/dishes/{recipeId}?version=（草稿移除菜；全部成员；幂等；version）
//          DELETE /web/auth/dining/orders/{id}         （放弃草稿；仅创建者 + DRAFT）
//          GET  /web/auth/dining/orders/my            （我发起的 + 我参与的，分页 page/size，含 status）
//          GET  /web/auth/dining/orders/{id}          （轮询主接口，knownVersion 轻量协议）
//          GET  /web/auth/dining/orders/brief/{token} （加入前预览）
//          POST /web/auth/dining/orders/{id}/join     （仅 DRAFT 可加入）
//          POST /web/auth/dining/orders/{id}/join-token/reset（仅发起者 + 仅 DRAFT）
//   选菜   POST /web/auth/dining/orders/{orderId}/items          （勾选，version 乐观并发）
//          PUT  /web/auth/dining/orders/{orderId}/items          （改备注，version）
//          DELETE /web/auth/dining/orders/{orderId}/items/{recipeId}（取消，version）
//
// 乐观并发契约：写请求携带客户端已知 version；与服务端不一致返回业务码 409
//（message「订单已被他人更新，请刷新后重试」），端上拉取最新详情后重放/回滚。
// 鸿蒙端消费约定：
// - 选菜/备注/取消：写失败（含 409）回滚本地意图并全量刷新（knownVersion=null），不逐操作重放；
// - 草稿菜单（加菜/移除/submit）：经 DiningDraftMenuEditor 净差写，409 拉取最新后重放未决差分。
//
// BASE_URL 不在本文件出现：请求走 httpUtil（baseURL 由 Axios.ets 构建期注入），
// 二维码内容构造见 feature/dining/DiningLogic.ets 的 buildDiningJoinUrl。

import { FormData, Type } from '@ohos/axios'
import { httpUtil } from '../HttpUtil'
import { BaseResponse } from '../BaseResponse'
import {
  DietaryPreferenceVo,
  DiningCategoryVo,
  DiningOrderBriefVo,
  DiningOrderCreatedVo,
  DiningOrderDetailVo,
  DiningOrderFinishVo,
  DiningOrderListVo,
  DiningPageData,
  DiningRecipeVo,
  dietaryPreferenceVoFromJSON,
  diningCategoryVoFromJSON,
  diningOrderBriefVoFromJSON,
  diningOrderCreatedVoFromJSON,
  diningOrderDetailVoFromJSON,
  diningOrderFinishVoFromJSON,
  diningOrderListVoFromJSON,
  diningPageDataFromJSON,
  diningRecipeVoFromJSON
} from '../vo/DiningVo'

// VO 类型再导出（页面统一从本域 API 文件取契约类型）
export {
  DietaryPreferenceVo,
  DiningCategoryVo,
  DiningMenuRecipe,
  DiningOrderBriefVo,
  DiningOrderCreatedVo,
  DiningOrderDetailVo,
  DiningOrderItem,
  DiningOrderListVo,
  DiningOrderMember,
  DiningPageData,
  DiningRecipeVo,
  DiningShoppingListSnapshot,
  DiningShoppingRecipeView,
  DiningShoppingSelector
} from '../vo/DiningVo'

/** 上架菜谱分页大小（菜谱库/创建页列表复用；Android 端每页 100 取满同量级） */
export const DINING_PAGE_SIZE: number = 50


/** 全部分类（sort 升序、id 升序；量级小不分页） */
export async function getDiningCategories(): Promise<BaseResponse<Array<DiningCategoryVo>>> {
  const res = await httpUtil.get<Object>('/web/auth/dining/categories/all')
  return {
    data: res.data === null ? null : diningPageDataLikeToList(res.data, diningCategoryVoFromJSON),
    code: res.code,
    message: res.message
  }
}

/** data 为数组的逐项映射（缺失/非数组兜底空数组） */
function diningPageDataLikeToList<T>(raw: Object, mapper: (item: Object) => T): Array<T> {
  if (!Array.isArray(raw)) {
    return []
  }
  const arr: Array<Object | null> = raw as Array<Object | null>
  const result: Array<T> = []
  for (let i = 0; i < arr.length; i++) {
    const item: Object | null = arr[i]
    if (item !== null && typeof item === 'object') {
      result.push(mapper(item))
    }
  }
  return result
}


/**
 * 上架菜谱分页（categoryId 精确，0/null=不过滤；keyword 菜名模糊，空串=不过滤；
 * ingredient 配料名模糊反查，空串=不过滤；多参数 AND）。
 * 列表按当前用户批量填充 favorited（后端一次 IN 查询，无 N+1）。
 */
export async function getDiningRecipes(page: number, size: number, categoryId: number,
  keyword: string, ingredient: string = ''): Promise<BaseResponse<DiningPageData<DiningRecipeVo>>> {
  const params: Record<string, Object> = {}
  params['page'] = page
  params['size'] = size
  if (categoryId > 0) {
    params['categoryId'] = categoryId
  }
  if (keyword.trim().length > 0) {
    params['keyword'] = keyword.trim()
  }
  if (ingredient.trim().length > 0) {
    params['ingredient'] = ingredient.trim()
  }
  const res = await httpUtil.get<Object>('/web/auth/dining/recipes/all', params)
  return {
    data: res.data === null ? null : diningPageDataFromJSON<DiningRecipeVo>(res.data, diningRecipeVoFromJSON),
    code: res.code,
    message: res.message
  }
}

/**
 * 我的收藏分页（GET /web/auth/dining/recipes/favorites；按收藏时间倒序，字段同上架列表，
 * favorited 恒 true）。
 */
export async function getDiningFavoriteRecipes(page: number, size: number)
  : Promise<BaseResponse<DiningPageData<DiningRecipeVo>>> {
  const params: Record<string, Object> = {}
  params['page'] = page
  params['size'] = size
  const res = await httpUtil.get<Object>('/web/auth/dining/recipes/favorites', params)
  return {
    data: res.data === null ? null : diningPageDataFromJSON<DiningRecipeVo>(res.data, diningRecipeVoFromJSON),
    code: res.code,
    message: res.message
  }
}

/**
 * 收藏菜谱（POST；幂等：已收藏返回当前态 true；菜谱不存在业务失败）。
 * 返回 data 为当前收藏态 boolean。
 */
export async function favoriteDiningRecipe(id: number): Promise<BaseResponse<boolean>> {
  return httpUtil.post<boolean>(`/web/auth/dining/recipes/${id}/favorite`)
}

/**
 * 取消收藏（DELETE；幂等：未收藏时无操作，返回当前态 false）。
 * 返回 data 为当前收藏态 boolean。
 */
export async function unfavoriteDiningRecipe(id: number): Promise<BaseResponse<boolean>> {
  return httpUtil.delete<boolean>(`/web/auth/dining/recipes/${id}/favorite`)
}

/**
 * 获取饮食偏好（GET /web/auth/dining/dietary-preference；字段 null=默认值语义，端上兜底 2000/2000）。
 */
export async function getDiningDietaryPreference(): Promise<BaseResponse<DietaryPreferenceVo>> {
  const res = await httpUtil.get<Object>('/web/auth/dining/dietary-preference')
  return {
    data: res.data === null ? null : dietaryPreferenceVoFromJSON(res.data),
    code: res.code,
    message: res.message
  }
}

/**
 * 保存饮食偏好（PUT；整行 upsert；任一字段 null=恢复默认，allergens null=清空忌口）。
 * 成功 data 为「保存成功」文案（与后端 Result<String> 对应）。
 */
export async function saveDiningDietaryPreference(preference: DietaryPreferenceVo)
  : Promise<BaseResponse<string>> {
  const body: Record<string, Object> = {}
  // null 字段不序列化（Gson 默认省略 null，后端 absent 即 null），与 Android 端语义一致
  if (preference.allergens !== null) {
    body['allergens'] = preference.allergens
  }
  if (preference.calorieTarget !== null) {
    body['calorieTarget'] = preference.calorieTarget
  }
  if (preference.sodiumTarget !== null) {
    body['sodiumTarget'] = preference.sodiumTarget
  }
  return httpUtil.put<string>('/web/auth/dining/dietary-preference', body)
}

/** 菜谱详情（仅上架可见，下架/不存在返回同一业务失败） */
export async function getDiningRecipeDetail(id: number): Promise<BaseResponse<DiningRecipeVo>> {
  const res = await httpUtil.get<Object>(`/web/auth/dining/recipes/${id}`)
  return {
    data: res.data === null ? null : diningRecipeVoFromJSON(res.data),
    code: res.code,
    message: res.message
  }
}

/** 新增菜谱请求体（与后端 from/AddDiningRecipeFrom 对应；categoryId 0/null=未分类） */
export interface AddDiningRecipeForm {
  name: string
  categoryId: number
  ingredients: Array<string> | null
  steps: string | null
  /** 封面对象键（封面上传接口返回），空串 = 暂不设置 */
  coverObjectKey: string | null
}

/** 新增菜谱（source=USER 直上架，无审核流；成功 data 为「添加成功」文案） */
export async function addDiningRecipe(form: AddDiningRecipeForm): Promise<BaseResponse<string>> {
  const body: Record<string, Object> = {}
  body['name'] = form.name
  if (form.categoryId > 0) {
    body['categoryId'] = form.categoryId
  }
  if (form.ingredients !== null && form.ingredients.length > 0) {
    body['ingredients'] = form.ingredients
  }
  if (form.steps !== null && form.steps.length > 0) {
    body['steps'] = form.steps
  }
  if (form.coverObjectKey !== null && form.coverObjectKey.length > 0) {
    body['coverObjectKey'] = form.coverObjectKey
  }
  return httpUtil.post<string>('/web/auth/dining/recipes/add', body)
}

/** 上传菜谱封面（multipart 图片，JPG/PNG/WebP + 魔数 + ≤5MB；返回 dining/covers/ 对象键） */
export async function uploadDiningCover(fileName: string, mimeType: string,
  buffer: ArrayBuffer): Promise<BaseResponse<string>> {
  const formData: FormData = new FormData()
  const option: Type = { filename: fileName, type: mimeType }
  formData.append('file', buffer, option)
  return httpUtil.upload<string>('/web/auth/dining/recipes/cover', formData)
}


/** 创建点单订单请求体（与后端 from/CreateDiningOrderFrom 对应；recipeIds 与 fromOrderId 二选一） */
export interface CreateDiningOrderForm {
  title: string
  /** 本单菜单菜谱 ID 集合（显式链路；复用链路传 null） */
  recipeIds: Array<number> | null
  /** 要复用本单菜单的历史订单 ID（复用链路；显式链路传 null） */
  fromOrderId: number | null
}

/**
 * 创建点单草稿（返回 orderId + joinToken 供拼二维码/深链）。
 * 草稿协同：title/recipeIds 均可空（或 fromOrderId 复用历史菜单），创建即 DRAFT；
 * 发起者 submit 提交后直接 FINISHED。
 */
export async function createDiningOrder(form: CreateDiningOrderForm)
  : Promise<BaseResponse<DiningOrderCreatedVo>> {
  const body: Record<string, Object> = {}
  body['title'] = form.title
  if (form.recipeIds !== null) {
    body['recipeIds'] = form.recipeIds
  }
  if (form.fromOrderId !== null) {
    body['fromOrderId'] = form.fromOrderId
  }
  const res = await httpUtil.post<Object>('/web/auth/dining/orders/create', body)
  return {
    data: res.data === null ? null : diningOrderCreatedVoFromJSON(res.data),
    code: res.code,
    message: res.message
  }
}

/** 提交点单请求体（DRAFT→FINISHED；title 为草稿页编辑后的最终标题，version 乐观锁） */
export interface SubmitDiningOrderForm {
  title: string
  version: number
}

/**
 * 提交点单（DRAFT→FINISHED；仅发起者；标题必填 + 菜单非空；version 乐观锁，冲突返回业务码 409）。
 * 提交即结束：后端聚合本单菜单生成 shopping_list 快照、通知全体成员；返回 DiningOrderFinishVo。
 */
export async function submitDiningOrder(orderId: number, form: SubmitDiningOrderForm)
  : Promise<BaseResponse<DiningOrderFinishVo>> {
  const body: Record<string, Object> = {}
  body['title'] = form.title
  body['version'] = form.version
  const res = await httpUtil.post<Object>(`/web/auth/dining/orders/${orderId}/submit`, body)
  return {
    data: res.data === null ? null : diningOrderFinishVoFromJSON(res.data),
    code: res.code,
    message: res.message
  }
}

/** 草稿加菜请求体（recipeId + 客户端已知 version，乐观锁） */
export interface AddDiningOrderDishForm {
  recipeId: number
  version: number
}

/**
 * 草稿加菜（仅 DRAFT 态、全部成员可加；幂等：已存在返回成功；version 冲突返回 409）。
 * 成功 data 恒为 null。
 */
export async function addDiningOrderDish(orderId: number, form: AddDiningOrderDishForm)
  : Promise<BaseResponse<null>> {
  const body: Record<string, Object> = {}
  body['recipeId'] = form.recipeId
  body['version'] = form.version
  return httpUtil.post<null>(`/web/auth/dining/orders/${orderId}/dishes`, body)
}

/**
 * 草稿移除菜（仅 DRAFT 态、全部成员可移除任意菜；幂等：不存在返回成功；version 冲突返回 409）。
 * 成功 data 恒为 null。
 */
export async function removeDiningOrderDish(orderId: number, recipeId: number, version: number)
  : Promise<BaseResponse<null>> {
  return httpUtil.delete<null>(`/web/auth/dining/orders/${orderId}/dishes/${recipeId}?version=${version}`)
}

/**
 * 放弃草稿（仅创建者 + 仅 DRAFT 态；物理删订单与菜单/成员/选菜子行）。
 * 成功 data 恒为 null。
 */
export async function discardDiningOrder(orderId: number): Promise<BaseResponse<null>> {
  return httpUtil.delete<null>(`/web/auth/dining/orders/${orderId}`)
}

/**
 * 我的订单明细分页（我发起的 + 我参与的，按创建时间倒序，带 isCreator）。
 * 后端 BaseSearchFrom：page 默认 1、size 默认 10 上限 100。
 */
export async function pageMyDiningOrders(page: number, size: number)
  : Promise<BaseResponse<DiningPageData<DiningOrderListVo>>> {
  const params: Record<string, Object> = {}
  params['page'] = page
  params['size'] = size
  const res = await httpUtil.get<Object>('/web/auth/dining/orders/my', params)
  return {
    data: res.data === null ? null : diningPageDataFromJSON<DiningOrderListVo>(res.data, diningOrderListVoFromJSON),
    code: res.code,
    message: res.message
  }
}

/**
 * 订单详情（轮询主接口）。
 * @param knownVersion 客户端已知 version；null=首次全量拉取（写失败回滚刷新也走 null）
 */
export async function getDiningOrderDetail(orderId: number, knownVersion: number | null)
  : Promise<BaseResponse<DiningOrderDetailVo>> {
  const params: Record<string, Object> = {}
  if (knownVersion !== null) {
    params['knownVersion'] = knownVersion
  }
  const res = await httpUtil.get<Object>(`/web/auth/dining/orders/${orderId}`, params)
  return {
    data: res.data === null ? null : diningOrderDetailVoFromJSON(res.data),
    code: res.code,
    message: res.message
  }
}

/** 加入前预览 brief（凭二维码 token 换标题/发起者/菜品数/是否已在单中） */
export async function getDiningOrderBrief(token: string): Promise<BaseResponse<DiningOrderBriefVo>> {
  const res = await httpUtil.get<Object>(`/web/auth/dining/orders/brief/${encodeURIComponent(token)}`)
  return {
    data: res.data === null ? null : diningOrderBriefVoFromJSON(res.data),
    code: res.code,
    message: res.message
  }
}

/** 加入订单（校验 token 匹配 + 订单状态为 DRAFT + 未重复加入；成功 data 恒为 null） */
export async function joinDiningOrder(orderId: number, joinToken: string): Promise<BaseResponse<null>> {
  const body: Record<string, Object> = {}
  body['joinToken'] = joinToken
  return httpUtil.post<null>(`/web/auth/dining/orders/${orderId}/join`, body)
}

/** 重置加入凭证（仅发起者；旧二维码全部作废，返回新 joinToken） */
export async function resetDiningJoinToken(orderId: number): Promise<BaseResponse<string>> {
  return httpUtil.post<string>(`/web/auth/dining/orders/${orderId}/join-token/reset`)
}

// ========== 选菜（共享购物车，version 乐观并发；契约保留但停止写入） ==========

/** 勾选菜品（菜须在本单菜单内；一人一菜一条，重复勾选拒绝；version 冲突返回业务码 409） */
export async function addDiningOrderItem(orderId: number, recipeId: number, note: string,
  version: number): Promise<BaseResponse<null>> {
  const body: Record<string, Object> = {}
  body['recipeId'] = recipeId
  body['note'] = note
  body['version'] = version
  return httpUtil.post<null>(`/web/auth/dining/orders/${orderId}/items`, body)
}

/** 修改选菜备注（仅本人选菜记录；note 空串 = 清空备注；version 冲突返回业务码 409） */
export async function updateDiningOrderItemNote(orderId: number, recipeId: number, note: string,
  version: number): Promise<BaseResponse<null>> {
  const body: Record<string, Object> = {}
  body['recipeId'] = recipeId
  body['note'] = note
  body['version'] = version
  return httpUtil.put<null>(`/web/auth/dining/orders/${orderId}/items`, body)
}

/** 取消选菜（删除本人该菜的选菜记录；version 缺失按业务失败返回） */
export async function removeDiningOrderItem(orderId: number, recipeId: number,
  version: number): Promise<BaseResponse<null>> {
  return httpUtil.delete<null>(`/web/auth/dining/orders/${orderId}/items/${recipeId}?version=${version}`)
}

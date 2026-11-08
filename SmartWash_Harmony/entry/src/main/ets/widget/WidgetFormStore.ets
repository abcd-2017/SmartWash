/**
 * 洗衣状态卡片 — formId 与卡片尺寸登记（卡片提供方进程内持久化）。
 *
 * 为什么需要登记：卡片刷新链路里只有 onAddForm 的 want 带卡片尺寸
 * （FormParam.DIMENSION_KEY，仅添加时回调一次）；后续系统按 form_config 的 updateDuration
 * 定时回调 onUpdateForm(formId) 时拿不到尺寸。而 updateForm 推送以键为粒度整体写入
 * LocalStorage，binding 里的版式标记（cardWide）若在宽卡刷新时被窄值覆盖，宽卡会错切成
 * 窄版式 —— 所以必须在 onAddForm 时把 formId→尺寸 落盘，onUpdateForm 时读回装配。
 * （参考 WisdomDaily WidgetFormStore 同款取舍；本实现用独立 preferences 文件替代其 RDB。）
 *
 * 存储选型：独立文件 smartwash_widget_forms，不经 utils/StorageUtil（其单例硬编码 App 的
 * smartWash_pref）。Preferences 官方声明为进程内缓存模型，跨进程共用同一文件有写覆盖风险 ——
 * 卡片登记的读写只发生在卡片提供方进程（onAddForm/onRemoveForm/onUpdateForm），
 * 独立文件保证与 App 进程（写 token/登录态）完全隔离，只读场景不存在冲突。
 *
 * 失败兜底（hvigor "Function may throw" 警告治理）：preferences 同步 API 可能抛异常，
 * 全部 try-catch 静默兜底 —— 登记失败按未登记处理（装配层回退窄卡默认形态）、
 * 读回失败同理、注销失败不影响删除卡片的系统流程；对齐 StorageUtil
 * 「Ability 早期生命周期静默兜底优于崩溃」的既有风格。
 */

import { preferences } from '@kit.ArkData'
import { logger } from '../utils/Logger'

const TAG: string = 'widgetFormStore'

/** 登记文件名（与 App 进程 smartWash_pref 隔离，见类注释） */
const WIDGET_FORM_STORE_NAME: string = 'smartwash_widget_forms'

/** 键前缀：dim_{formId} → number（formInfo.FormDimension 枚举值） */
const KEY_DIMENSION_PREFIX: string = 'dim_'

/** 单次登记操作失败的日志口径（低频关键路径失败，info 留痕） */
function logFailure(action: string, err: Object): void {
  logger.error(TAG, `卡片尺寸登记${action}失败`,
    err instanceof Error ? err.message : '')
}

/**
 * 登记 formId 的卡片尺寸（onAddForm 同步调用；覆盖式写入，重复添加不产生脏数据）。
 * @param dimension formInfo.FormDimension 枚举值（2=2x2，3=2x4）
 */
export function saveFormDimension(context: Context, formId: string, dimension: number): void {
  if (formId.length === 0) {
    return
  }
  try {
    const store = preferences.getPreferencesSync(context, { name: WIDGET_FORM_STORE_NAME })
    store.putSync(`${KEY_DIMENSION_PREFIX}${formId}`, dimension)
    store.flushSync()
  } catch (err) {
    // 登记失败：定时刷新读不到尺寸会按窄卡兜底，卡片仍有正确默认形态
    logFailure('写入', err as Object)
  }
}

/** 读取登记尺寸；未登记/非法 formId/读取失败返回 0（装配层按窄版式兜底，保证有默认形态） */
export function dimensionOf(context: Context, formId: string): number {
  if (formId.length === 0) {
    return 0
  }
  try {
    const store = preferences.getPreferencesSync(context, { name: WIDGET_FORM_STORE_NAME })
    const value = store.getSync(`${KEY_DIMENSION_PREFIX}${formId}`, 0)
    return typeof value === 'number' ? value : 0
  } catch (err) {
    logFailure('读取', err as Object)
    return 0
  }
}

/** 注销登记（onRemoveForm）：卡片被用户删除后清理，防登记表无限增长；失败仅留痕 */
export function removeForm(context: Context, formId: string): void {
  if (formId.length === 0) {
    return
  }
  try {
    const store = preferences.getPreferencesSync(context, { name: WIDGET_FORM_STORE_NAME })
    store.deleteSync(`${KEY_DIMENSION_PREFIX}${formId}`)
    store.flushSync()
  } catch (err) {
    logFailure('注销', err as Object)
  }
}

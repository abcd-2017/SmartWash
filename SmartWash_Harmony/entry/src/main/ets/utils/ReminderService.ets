/**
 * 取件提醒服务层：reminderAgentManager（@kit.BackgroundTaskKit）系统提醒封装。
 *
 * API 签名查证来源（红线：禁止凭记忆落码）：
 * - SDK d.ts：/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/ets/api/@ohos.reminderAgentManager.d.ts
 *   · publishReminder(reminderReq: ReminderRequest): Promise<number>（返回 reminderId，since 9）
 *   · cancelReminder(reminderId: number): Promise<void>（since 9）
 *   · ReminderType.REMINDER_TYPE_TIMER = 0（倒计时型），ReminderRequestTimer.triggerTimeInSeconds 单位秒
 *   · ReminderRequest 必填仅 reminderType；title/content/wantAgent/tapDismissed 均可选
 *   · 调用需 ohos.permission.PUBLISH_AGENT_REMINDER（d.ts @permission 注释；system_grant 静态声明，无弹窗）
 * - Kit 导出：ets/kits/@kit.BackgroundTasksKit.d.ts（本 SDK 5.0.5(17) Kit 名为复数 BackgroundTasksKit）
 *   导出名 reminderAgentManager
 *
 * 容错红线（对齐参考实现 arkts_startup 的解耦原则）：登记/撤销失败一律静默留痕，
 * 绝不向页面抛出——提醒是增强能力，任何失败不得影响订单主流程。
 * 防重：preferences 登记表（orderId → reminderId，见 ReminderLogic）为唯一事实源，同订单至多一条。
 */
import { reminderAgentManager } from '@kit.BackgroundTasksKit'
import { preferences } from '@kit.ArkData'
import BuildProfile from 'entry/BuildProfile'
import { getValue, setValue } from './StorageUtil'
import { logger } from './Logger'
import {
  ReminderAction,
  ReminderRegistryEntry,
  applyReminderStrings,
  buildReminderContent,
  buildReminderTitle,
  computeTriggerSeconds,
  decideReminderAction,
  findRegisteredReminderId,
  parseRegistry,
  removeRegistryEntry,
  serializeRegistry,
  upsertRegistryEntry
} from './ReminderLogic'
import { REMINDER_DELAY_MINUTES_DEFAULT } from './ReminderLogic'

const TAG: string = 'reminderService'

// preferences key（开关与登记表，同经 StorageUtil 封装，不裸操作 preferences）
const PREF_KEY_ENABLED: string = 'pickup_reminder_enabled'
const PREF_KEY_REGISTRY: string = 'pickup_reminder_registry'

// 点击提醒回到的 Ability（Stage 模型主 UIAbility，与 module.json5 mainElement 一致）
const ENTRY_ABILITY_NAME: string = 'EntryAbility'

/**
 * ValueType → string 收窄：登记表在 preferences 中恒为序列化 JSON 串，
 * 非字符串（类型漂移/键误用）按空串处理（空串在 parseRegistry 内容错为空表）。
 */
function storedString(value: preferences.ValueType | null): string {
  return typeof value === 'string' ? value : ''
}

/**
 * 同步入参（结构化接口，与页面 VO 解耦）：OrderInfo（class）/ OrderVo 均结构兼容可直接传入；
 * lockerNumber 可选——详情页有柜号上下文时带上，文案落到具体柜位。
 */
export interface ReminderSyncOrder {
  orderId: number | null
  status: string | null
  lockerNumber?: number | null
}

/**
 * 应用启动初始化：注入资源文案（失败回落 ReminderLogic 内置兜底，不影响后续提醒发布）。
 * EntryAbility.onWindowStageCreate 与 initPreferences 同点位调用，不 await（失败自有兜底）。
 */
export async function initPickupReminder(context: Context): Promise<void> {
  try {
    const title: string = context.resourceManager.getStringSync($r('app.string.pickup_reminder_title').id)
    const prefix: string = context.resourceManager.getStringSync($r('app.string.pickup_reminder_content_prefix').id)
    const suffix: string = context.resourceManager.getStringSync($r('app.string.pickup_reminder_content_suffix').id)
    applyReminderStrings(title, prefix, suffix)
    logger.debug(TAG, '取件提醒文案已从资源注入')
  } catch (err) {
    logger.debug(TAG, `取件提醒文案资源读取失败，使用内置兜底 ${err instanceof Error ? err.message : ''}`)
  }
}

/**
 * 开关读取：默认开启。preferences 未初始化 / 读取失败均按开启——
 * 提醒是用户可见的兜底能力，读侧异常时保持出厂行为比静默禁用更符合预期。
 */
export async function isPickupReminderEnabled(): Promise<boolean> {
  try {
    const stored = await getValue(PREF_KEY_ENABLED, true)
    return stored === null ? true : stored === true
  } catch (err) {
    logger.debug(TAG, `取件提醒开关读取失败，按默认开启 ${err instanceof Error ? err.message : ''}`)
    return true
  }
}

/**
 * 开关写入。关闭时全量撤销未触发提醒（登记表清空）；
 * 撤销逐条容错，失败条目保留（用户重新开启后列表同步可再尝试清理）。
 */
export async function setPickupReminderEnabled(enabled: boolean): Promise<void> {
  await setValue(PREF_KEY_ENABLED, enabled)
  logger.debug(TAG, `取件提醒开关写入 ${enabled ? '开' : '关'}`)
  if (!enabled) {
    await cancelAllPickupReminders()
  }
}

/** 单订单同步（详情页）：按状态决策登记/撤销 */
export async function syncPickupReminderForOrder(order: ReminderSyncOrder): Promise<void> {
  await syncPickupReminderOrders([order])
}

/**
 * 批量同步（列表页）：逐单独立决策（登记/撤销/跳过），任何单点失败不中断其余订单。
 * 全函数容错收口——调用方 fire-and-forget，无需 catch（内部不会向外部抛出）。
 */
export async function syncPickupReminderOrders(orders: Array<ReminderSyncOrder>): Promise<void> {
  try {
    if (!(await isPickupReminderEnabled())) {
      // 开关关闭：跳过登记。已登记提醒在开关关闭路径（setPickupReminderEnabled）已全量撤销，
      // 此处不重复撤销（列表刷新高频路径，避免反复全量 cancel 打系统接口）。
      logger.debug(TAG, '取件提醒开关关闭，跳过同步')
      return
    }
    let registry: Array<ReminderRegistryEntry> = parseRegistry(storedString(await getValue(PREF_KEY_REGISTRY, '')))
    let mutated: boolean = false
    for (const order of orders) {
      const orderId: number | null = order.orderId
      if (orderId === null || orderId === undefined) {
        continue
      }
      const registeredId: number | null = findRegisteredReminderId(registry, orderId)
      const action: ReminderAction = decideReminderAction(order.status, registeredId !== null)
      if (action === ReminderAction.REGISTER) {
        const reminderId: number | null = await publishPickupReminder(orderId, order.lockerNumber ?? null)
        if (reminderId !== null) {
          const entry: ReminderRegistryEntry = {
            orderId: orderId,
            reminderId: reminderId,
            registeredAt: Date.now()
          }
          registry = upsertRegistryEntry(registry, entry)
          mutated = true
        }
      } else if (action === ReminderAction.CANCEL && registeredId !== null) {
        const cancelled: boolean = await cancelSystemReminder(registeredId)
        if (cancelled) {
          registry = removeRegistryEntry(registry, orderId)
          mutated = true
        }
        // 撤销失败保留登记：下次同步还会重试（自愈）；仅当 reminder 已不存在时才可能持续失败，
        // 由「设置页开关关闭再开启」或后续成功同步兜底清理
      }
    }
    if (mutated) {
      await setValue(PREF_KEY_REGISTRY, serializeRegistry(registry))
    }
  } catch (err) {
    logger.debug(TAG, `取件提醒同步异常（已忽略，不影响主流程）${err instanceof Error ? err.message : ''}`)
  }
}

/**
 * 单订单撤销（取消订单 / 取件成功路径）：无登记静默返回；撤销失败保留登记待下次重试。
 */
export async function cancelPickupReminderForOrder(orderId: number): Promise<void> {
  try {
    const registry: Array<ReminderRegistryEntry> =
      parseRegistry(storedString(await getValue(PREF_KEY_REGISTRY, '')))
    const registeredId: number | null = findRegisteredReminderId(registry, orderId)
    if (registeredId === null) {
      return
    }
    const cancelled: boolean = await cancelSystemReminder(registeredId)
    if (cancelled) {
      await setValue(PREF_KEY_REGISTRY, serializeRegistry(removeRegistryEntry(registry, orderId)))
      logger.info(TAG, `取件提醒已撤销 orderId=${orderId}`)
    }
  } catch (err) {
    logger.debug(TAG, `取件提醒撤销异常（已忽略，不影响主流程）${err instanceof Error ? err.message : ''}`)
  }
}

/** 全量撤销未触发提醒（设置页开关关闭时调用）：逐条尝试，失败条目保留登记 */
export async function cancelAllPickupReminders(): Promise<void> {
  try {
    const registry: Array<ReminderRegistryEntry> =
      parseRegistry(storedString(await getValue(PREF_KEY_REGISTRY, '')))
    if (registry.length === 0) {
      return
    }
    const remaining: Array<ReminderRegistryEntry> = []
    for (const entry of registry) {
      const cancelled: boolean = await cancelSystemReminder(entry.reminderId)
      if (!cancelled) {
        remaining.push(entry)
      }
    }
    await setValue(PREF_KEY_REGISTRY, serializeRegistry(remaining))
    logger.info(TAG, `取件提醒全量撤销完成 剩余未撤销=${remaining.length}`)
  } catch (err) {
    logger.debug(TAG, `取件提醒全量撤销异常（已忽略）${err instanceof Error ? err.message : ''}`)
  }
}

/**
 * 发布待取件倒计时提醒（默认 30 分钟）。失败返回 null（不抛出）：
 * - 1700001 通知未开启 / 1700002 提醒数量超限 / 权限被系统回收等均静默降级；
 * - 发布失败不写登记表，下次同步（列表刷新/详情进入）自动重试。
 */
async function publishPickupReminder(orderId: number,
  lockerNumber: number | null | undefined): Promise<number | null> {
  try {
    const agent: reminderAgentManager.WantAgent = {
      pkgName: BuildProfile.BUNDLE_NAME,
      abilityName: ENTRY_ABILITY_NAME
    }
    const req: reminderAgentManager.ReminderRequestTimer = {
      reminderType: reminderAgentManager.ReminderType.REMINDER_TYPE_TIMER,
      triggerTimeInSeconds: computeTriggerSeconds(REMINDER_DELAY_MINUTES_DEFAULT),
      title: buildReminderTitle(),
      content: buildReminderContent(lockerNumber),
      wantAgent: agent,
      // 点击通知即清除（API 10+）：待取件提醒被点开后没有再留在提醒中心的必要
      tapDismissed: true
    }
    const reminderId: number = await reminderAgentManager.publishReminder(req)
    logger.info(TAG, `取件提醒已登记 orderId=${orderId} reminderId=${reminderId} 柜号=${lockerNumber ?? '-'}`)
    return reminderId
  } catch (err) {
    logger.warn(TAG, `取件提醒登记失败 orderId=${orderId}（不影响主流程）${err instanceof Error ? err.message : ''}`)
    return null
  }
}

/** 撤销系统提醒；失败返回 false（不抛出，调用方决定登记去留） */
async function cancelSystemReminder(reminderId: number): Promise<boolean> {
  try {
    await reminderAgentManager.cancelReminder(reminderId)
    return true
  } catch (err) {
    logger.warn(TAG, `系统提醒撤销失败 reminderId=${reminderId}${err instanceof Error ? err.message : ''}`)
    return false
  }
}

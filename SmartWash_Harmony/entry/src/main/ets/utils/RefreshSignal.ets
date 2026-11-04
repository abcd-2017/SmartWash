/**
 * 跨页刷新信号（问题 3 修复）：自增 tick 替代布尔标记。
 *
 * 布尔标记的缺陷：AppStorage 里已是 true 时再 setOrCreate(true) 不产生变更，
 * 消费方的 @Watch 不触发——充值返回后首页余额不刷新正是这个原因。
 * tick 每次自增必然产生变更，所有监听方都会被唤醒；消费方无需复位。
 *
 * 约定：写侧只调用 bump*()，读侧用 @StorageLink(key) + @Watch 消费。
 */

/** 账户数据（余额/券数/累计订单）需要刷新 */
export function bumpUserInfoRefresh(): void {
  const current = AppStorage.get<number>(USER_INFO_TICK_KEY) ?? 0
  AppStorage.setOrCreate(USER_INFO_TICK_KEY, current + 1)
}

/** 订单计数/订单列表需要刷新 */
export function bumpOrderRefresh(): void {
  const current = AppStorage.get<number>(ORDER_TICK_KEY) ?? 0
  AppStorage.setOrCreate(ORDER_TICK_KEY, current + 1)
}

/** AppStorage key（写读双方共用，避免字符串漂移） */
export const USER_INFO_TICK_KEY: string = 'refreshUserInfoTick'
export const ORDER_TICK_KEY: string = 'refreshOrderCountTick'

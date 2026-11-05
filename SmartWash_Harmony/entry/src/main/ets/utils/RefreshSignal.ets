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

/**
 * 「非首页 tab 按系统返回键切回首页 tab」信号。
 * Home 是 Navigation 根内容而非页面栈成员，其 NavDestination.onBackPressed 不会触发
 * （API 17 实测按返回直接退后台）；由 @Entry Index.onBackPress 拦截后 bump 本 tick，
 * Home 经 @Watch 切回 tab 0（对齐安卓 HomePage.kt BackHandler）。
 */
export function bumpHomeTabBack(): void {
  const current = AppStorage.get<number>(HOME_TAB_BACK_TICK_KEY) ?? 0
  AppStorage.setOrCreate(HOME_TAB_BACK_TICK_KEY, current + 1)
}

/**
 * 情绪气象站数据（今日/月历/趋势）需要刷新。
 * 今日页保存成功后 bump，月历/趋势页经 @StorageLink + @Watch 消费重拉。
 */
export function bumpMoodRefresh(): void {
  const current = AppStorage.get<number>(MOOD_TICK_KEY) ?? 0
  AppStorage.setOrCreate(MOOD_TICK_KEY, current + 1)
}

/**
 * 记忆复习数据（今日队列）需要刷新。
 * 资料页建资料/建卡、复习页每次评分成功后 bump，队列页经 @StorageLink + @Watch 消费重拉
 * （对齐安卓 ReviewRoute.CHANGED_KEY）。
 */
export function bumpReviewRefresh(): void {
  const current = AppStorage.get<number>(REVIEW_TICK_KEY) ?? 0
  AppStorage.setOrCreate(REVIEW_TICK_KEY, current + 1)
}

/**
 * 订阅逃生舱数据（列表/详情/提醒红点）需要刷新。
 * 新建/编辑/删除/状态流转/打卡/撤销/标记已读成功后 bump，
 * 列表页与详情页经 @StorageLink + @Watch 消费重拉（对齐安卓 SubscriptionRoute.CHANGED_KEY）。
 */
export function bumpSubscriptionRefresh(): void {
  const current = AppStorage.get<number>(SUBSCRIPTION_TICK_KEY) ?? 0
  AppStorage.setOrCreate(SUBSCRIPTION_TICK_KEY, current + 1)
}

/** AppStorage key（写读双方共用，避免字符串漂移） */
export const USER_INFO_TICK_KEY: string = 'refreshUserInfoTick'
export const ORDER_TICK_KEY: string = 'refreshOrderCountTick'
export const HOME_TAB_BACK_TICK_KEY: string = 'homeTabBackTick'
export const MOOD_TICK_KEY: string = 'refreshMoodTick'
export const REVIEW_TICK_KEY: string = 'refreshReviewTick'
export const SUBSCRIPTION_TICK_KEY: string = 'refreshSubscriptionTick'

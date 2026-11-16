/**
 * 轻量分页"加载更多"封装（差异分析 01 §3.3，鸿蒙无 Paging 3 等价物，做轻量封装）。
 *
 * 收敛 OrderList / RechargeRecord / PickUp 等列表页各自手写的
 * "onScrollFrameBegin 预解锁 → onScrollIndex 触底 → 上锁 → page++ → 拉取 → 解锁"模板逻辑，
 * 并取代各页 setTimeout 1200/2000 的人工延时（加载完成即解锁，行为对齐安卓无人工延时）。
 *
 * 页面 @State 负责驱动 UI（LoadingFooter），本控制器经 onLoadingChange 回调同步加载态；
 * 拉取结果两种终态（对齐安卓 Paging append LoadState）：
 * - resolve(false)：本页无数据 → 封底，后续触底不再发请求（页面可提示"没有更多"）；
 * - reject：请求/业务失败 → **不封底**，记录失败页，footer 可展示"加载失败，点击重试"
 *   并经 [retry] 重拉同一失败页；再次触底也会重试。既有消费页全部 resolve，
 *   仅失败时抛错的页面（工具箱列表）获得重试能力，其余页面行为不变。
 */
export type FetchPageFn = (page: number) => Promise<boolean>
export type LoadingChangeFn = (loading: boolean) => void

/**
 * LazyForEach 数据源（通用）：以「provider 闭包读取的数组」为唯一数据源，实现 IDataSource。
 *
 * 背景：LazyForEach 必须作用于 List/Grid/WaterFlow/Swiper 等懒加载容器，且需要 IDataSource
 * 提供 totalCount/getData 与变化通知；页面侧的 @State 数组负责驱动空态/计数等 UI 状态，
 * 本数据源经闭包读取同一数组，避免「状态数组」与「列表数据」两份数据不一致。
 *
 * 用法：
 * - 页面 private 字段持有稳定实例（不可每次 build 重建，否则 LazyForEach 会整表重建）；
 * - 数组整体替换后调 notifyReload()（第 1 页/刷新）；
 * - 追加后调 notifyAppend(startIndex, count)（触底加载）。
 * @param provider 读取页面 @State 数组的闭包
 */
export class LazyListDataSource<T> implements IDataSource {
  private readonly provider: () => Array<T>
  private listeners: Array<DataChangeListener> = []

  constructor(provider: () => Array<T>) {
    this.provider = provider
  }

  totalCount(): number {
    return this.provider().length
  }

  getData(index: number): T {
    return this.provider()[index]
  }

  registerDataChangeListener(listener: DataChangeListener): void {
    if (this.listeners.indexOf(listener) < 0) {
      this.listeners.push(listener)
    }
  }

  unregisterDataChangeListener(listener: DataChangeListener): void {
    const index: number = this.listeners.indexOf(listener)
    if (index >= 0) {
      this.listeners.splice(index, 1)
    }
  }

  /** 全量替换后通知（对应 ForEach 整体换数组，LazyForEach 重建全部可见项） */
  notifyReload(): void {
    this.listeners.forEach((listener: DataChangeListener): void => listener.onDataReloaded())
  }

  /** 追加后按新索引逐项通知（触底 append，避免整表重建） */
  notifyAppend(startIndex: number, count: number): void {
    for (let i = 0; i < count; i++) {
      const index: number = startIndex + i
      this.listeners.forEach((listener: DataChangeListener): void => listener.onDataAdd(index))
    }
  }
}

/** onScrollFrameBegin 判定为"向下滑动"的偏移阈值（vp），沿用各页原实现 */
const SCROLL_OFFSET_THRESHOLD: number = 5

export class PagingController {
  private page: number = 1
  private canLoad: boolean = false
  private loading: boolean = false
  private hasMore: boolean = true
  /** 最近一次加载失败的目标页（null = 无失败）；失败不封底，retry 重拉同一页 */
  private failedPage: number | null = null
  private readonly fetchPage: FetchPageFn
  private readonly onLoadingChange: LoadingChangeFn

  constructor(fetchPage: FetchPageFn, onLoadingChange: LoadingChangeFn) {
    this.fetchPage = fetchPage
    this.onLoadingChange = onLoadingChange
  }

  /** 当前页码（供页面查询参数等场景读取） */
  currentPage(): number {
    return this.page
  }

  /** 追加加载是否处于失败态（footer 据此展示"加载失败，点击重试"） */
  hasAppendError(): boolean {
    return this.failedPage !== null
  }

  /**
   * 重置到第 1 页（切页签/下拉刷新/页面重显场景）。
   * 注意：重置不会自动拉取，由页面在重置后自行调用首屏加载。
   */
  reset(): void {
    this.page = 1
    this.canLoad = false
    this.loading = false
    this.hasMore = true
    this.failedPage = null
    this.onLoadingChange(false)
  }

  /** 对接 List 的 onScrollFrameBegin：向下滑动且未在加载时，预解锁触底加载 */
  onScrollFrameBegin(offset: number): void {
    if (offset > SCROLL_OFFSET_THRESHOLD && !this.loading) {
      this.canLoad = true
    }
  }

  /**
   * 对接 List 的 onScrollIndex：已解锁且滚动到列表末尾时触发一次下一页加载。
   * @param endIndex 本次滚动后末尾可见项下标
   * @param listLength 列表数据长度（不含 LoadingFooter 等占位项）
   */
  onScrollIndex(endIndex: number, listLength: number): void {
    if (!this.canLoad || this.loading || !this.hasMore) {
      return
    }
    if (listLength <= 0 || endIndex < listLength - 1) {
      return
    }
    this.canLoad = false
    this.load(this.page + 1)
  }

  /**
   * 失败重试 — 重拉同一失败页（对齐安卓 append Error → retry()，页码不推进）。
   * loading 中忽略；成功后 failedPage 清零、页码推进到该页，自动加载恢复。
   */
  retry(): void {
    if (this.failedPage === null || this.loading) {
      return
    }
    this.load(this.failedPage)
  }

  /** 统一加载一页：resolve(false) 封底，reject 记失败页不封底 */
  private load(targetPage: number): void {
    this.loading = true
    this.onLoadingChange(true)
    this.fetchPage(targetPage)
      .then((hasData: boolean) => {
        this.failedPage = null
        if (hasData) {
          this.page = targetPage
        } else {
          // 本页无数据：封底，后续触底不再发请求
          this.hasMore = false
        }
      })
      .catch(() => {
        // 失败不封底：保留 hasMore，footer 重试或再次触底重拉同一页
        this.failedPage = targetPage
      })
      .finally(() => {
        this.loading = false
        this.onLoadingChange(false)
      })
  }
}

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

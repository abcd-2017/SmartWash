/**
 * 轻量分页"加载更多"封装（差异分析 01 §3.3，鸿蒙无 Paging 3 等价物，做轻量封装）。
 *
 * 收敛 OrderList / RechargeRecord / PickUp 等列表页各自手写的
 * "onScrollFrameBegin 预解锁 → onScrollIndex 触底 → 上锁 → page++ → 拉取 → 解锁"模板逻辑，
 * 并取代各页 setTimeout 1200/2000 的人工延时（加载完成即解锁，行为对齐安卓无人工延时）。
 *
 * 页面 @State 负责驱动 UI（LoadingFooter），本控制器经 onLoadingChange 回调同步加载态；
 * 拉取函数返回本页是否有数据：无数据时控制器不再放行下一页，页面可在此提示"没有更多数据了"。
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

  /**
   * 重置到第 1 页（切页签/下拉刷新/页面重显场景）。
   * 注意：重置不会自动拉取，由页面在重置后自行调用首屏加载。
   */
  reset(): void {
    this.page = 1
    this.canLoad = false
    this.loading = false
    this.hasMore = true
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
    this.loading = true
    this.onLoadingChange(true)
    this.fetchPage(this.page + 1)
      .then((hasData: boolean) => {
        if (hasData) {
          this.page++
        } else {
          // 本页无数据：封底，后续触底不再发请求
          this.hasMore = false
        }
      })
      .finally(() => {
        this.loading = false
        this.onLoadingChange(false)
      })
  }
}

// 吃啥订单列表页（域首页）— 对齐 Android DiningOrderListPage.kt
//
// 「我发起的 / 我参与的」分区（isCreator 派生）+ 订单卡（标题/状态/时间/卡内「再来一单」）
// + FAB 发起点单 + 右上角菜谱库入口。深链 smartwash://dining 落点、工作台入口 target 同款。
// 卡片点击：ONGOING → 点单页；FINISHED → 购买清单结果页。
// 返回本页（离栈再回）经 onShown 重拉（对齐 Android 列表返回不刷新遗留的修法）。

import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight, MotionAlpha, MotionScale, MotionTiming } from '../../../constant/DesignSystem'
import { ReduceMotion } from '../../../utils/ReduceMotion'
import { BizCode } from '../../../network/HttpUtil'
import { DiningOrderListVo, pageMyDiningOrders } from '../../../network/api/DiningApi'
import { diningOrderStatusFromCode, DiningOrderStatus, diningTimeLabel } from '../DiningLogic'
import { DiningOrderCreateParam } from '../api'
import { DiningText } from '../DiningText'
import { DiningStatusTag } from './DiningUiCommon'
import { AppButton, AppButtonVariant, EmptyState, SkeletonList } from '../../../view/common/AppComponents'
import { LoadingFooter } from '../../../view/LoadingFooter'
import { logger } from '../../../utils/Logger'
import { pathStack } from '../../../utils/PathStackUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'
import { LazyListDataSource, PagingController } from '../../../utils/PagingUtil'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'diningOrderList'

/** FAB 尺寸（vp）：对齐 Android dining FAB 56dp 圆形自绘 */
const FAB_SIZE: number = 56

/** 订单列表分页大小（后端 BaseSearchFrom 默认 10，上限 100） */
const PAGE_SIZE: number = 10

/**
 * LazyForEach 数据源条目：分区标题或订单卡。
 * 单一数据源承载「我发起的 / 我参与的」分组，避免在 List 中混排多个 LazyForEach。
 */
interface DiningOrderListEntry {
  /** 稳定唯一 key（框架按 key 复用子组件） */
  key: string
  isHeader: boolean
  title: string
  order: DiningOrderListVo | null
}

@Builder
export function DiningOrderListBuilder() {
  DiningOrderListPage()
}

@ComponentV2
struct DiningOrderListPage {
  /** 我发起的订单（创建时间倒序，接口原序） */
  @Local createdOrders: Array<DiningOrderListVo> = []
  /** 我参与的订单 */
  @Local joinedOrders: Array<DiningOrderListVo> = []
  /** LazyForEach 数据源条目（分区标题 + 订单卡，由分区数组派生） */
  @Local entries: Array<DiningOrderListEntry> = []
  @Local isLoading: boolean = true
  @Local isError: boolean = false
  // 触底加载更多态（驱动 LoadingFooter）
  @Local isLoadingMore: boolean = false
  // 按压反馈 key（对齐项目口径：onTouch Down/Up + scale 0.97）
  @Local pressedKey: string = ''
  // reduced motion：按压取消缩放改 alpha 0.86（规范 §8）
  @Local reduceMotion: boolean = false
  /** LazyForEach 数据源（读取 entries，实例稳定不可重建） */
  private dataSource: LazyListDataSource<DiningOrderListEntry> =
    new LazyListDataSource<DiningOrderListEntry>(() => this.entries)
  /** 轻量分页封装：触底加载/上锁防重/封底判定（复用 OrderList 同款） */
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchOrderPage(page)
    },
    (loading: boolean): void => {
      this.isLoadingMore = loading
    }
  )
  private hasShown: boolean = false

  aboutToAppear(): void {
    this.reduceMotion = ReduceMotion.isEnabled()
    logger.debug(TAG, '订单列表页挂载，开始首载')
    this.reloadFirstPage()
  }

  build() {
    NavDestination() {
      ThemeRefresh() {
        Stack({ alignContent: Alignment.BottomEnd }) {
          Column() {
            // 页头：返回 + 标题 + 右上角「菜谱库」（对齐 ToolboxShortCodeList 页头画法）
            Row() {
              Column() {
                SymbolGlyph($r('sys.symbol.chevron_left'))
                  .fontSize(24)
                  .fontColor([$r('app.color.TEXT_PRIMARY')])
              }
              .width(DesignDimens.BACK_HIT_AREA)
              .height(DesignDimens.BACK_HIT_AREA)
              .justifyContent(FlexAlign.Center)
              .alignItems(HorizontalAlign.Center)
              .onClick(() => {
                pathStack.pop()
              })

              Text(DiningText.dining_title)
                .fontSize(DesignFontSize.ROOT_TITLE)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor($r('app.color.TEXT_PRIMARY'))

              Blank()

              // 我的收藏入口（契约 §三.4）
              Text(DiningText.dining_order_action_favorites)
                .fontSize(DesignFontSize.PAGE_HEADER)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor($r('app.color.PRIMARY_DARK'))
                .padding({ left: 12, right: 12, top: 12, bottom: 12 })
                .onClick(() => {
                  pathStack.pushPathByName(RouteNames.DINING_FAVORITE_LIST, null)
                })

              Text(DiningText.dining_order_action_recipe_library)
                .fontSize(DesignFontSize.PAGE_HEADER)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor($r('app.color.PRIMARY_DARK'))
                .padding({ left: 12, right: 12, top: 12, bottom: 12 })
                // 入口按压 100ms scale 0.97（对齐安卓 pressable）；reduced 改 alpha 0.86
                .scale({
                  x: !this.reduceMotion && this.pressedKey === 'library' ? MotionScale.Press : 1,
                  y: !this.reduceMotion && this.pressedKey === 'library' ? MotionScale.Press : 1
                })
                .opacity(this.reduceMotion && this.pressedKey === 'library' ? MotionAlpha.PressReduced : 1)
                .animation(MotionTiming.PRESS)
                .onTouch((event: TouchEvent) => {
                  if (event.type === TouchType.Down) {
                    this.pressedKey = 'library'
                  } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
                    this.pressedKey = ''
                  }
                })
                .onClick(() => {
                  pathStack.pushPathByName(RouteNames.DINING_LIBRARY, null)
                })
            }
            .width('100%')
            .height(56)
            .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
            .alignItems(VerticalAlign.Center)

            // 主体
            if (this.isLoading) {
              Column({ space: DesignDimens.SPACE_MD }) {
                SkeletonList({ rows: 3 })
                SkeletonList({ rows: 2 })
              }
              .width('100%')
              .layoutWeight(1)
              // 内容三态交叉淡变 300ms LinearOutSlowIn（对齐安卓 Crossfade(300)），reduced 降 180ms
              .transition(TransitionEffect.OPACITY)
              .animation(ReduceMotion.spec(MotionTiming.ENTER_CARD))
            } else if (this.isError) {
              Column() {
                EmptyState({ message: DiningText.dining_order_list_load_failed }) {
                  Column() {
                    AppButton({
                      small: true,
                      text: DiningText.dining_action_retry,
                      variant: AppButtonVariant.SECONDARY,
                      click: () => {
                        this.reloadFirstPage()
                      }
                    })
                  }
                  .width('100%')
                  .padding({ top: DesignDimens.SPACE_MD })
                }
              }
              .width('100%')
              .layoutWeight(1)
              // 内容三态交叉淡变 300ms LinearOutSlowIn（对齐安卓 Crossfade(300)），reduced 降 180ms
              .transition(TransitionEffect.OPACITY)
              .animation(ReduceMotion.spec(MotionTiming.ENTER_CARD))
            } else if (this.createdOrders.length === 0 && this.joinedOrders.length === 0) {
              Column() {
                EmptyState({ message: DiningText.dining_order_list_empty })
              }
              .width('100%')
              .layoutWeight(1)
              // 内容三态交叉淡变 300ms LinearOutSlowIn（对齐安卓 Crossfade(300)），reduced 降 180ms
              .transition(TransitionEffect.OPACITY)
              .animation(ReduceMotion.spec(MotionTiming.ENTER_CARD))
            } else {
              // 真数据列表改 LazyForEach 懒加载（分区标题 + 订单卡统一走数据源）
              List() {
                LazyForEach(this.dataSource, (entry: DiningOrderListEntry) => {
                  ListItem() {
                    if (entry.isHeader) {
                      this.SectionHeader(entry.title)
                    } else if (entry.order !== null) {
                      this.OrderCard(entry.order)
                    }
                  }
                }, (entry: DiningOrderListEntry) => entry.key)
                ListItem() {
                  LoadingFooter({ isLoading: this.isLoadingMore })
                }
              }
              .scrollBar(BarState.Off)
              .width('100%')
              .height('100%')
              .align(Alignment.TopStart)
              .edgeEffect(EdgeEffect.Spring)
              // 底部留白避开 FAB 遮挡（原 Scroll 内占位 Column 的等价迁移）
              .padding({ bottom: Math.max(safeBottomVp() + FAB_SIZE, DesignDimens.SPACE_XXL) })
              .onScrollIndex((start: number, end: number) => {
                // listLength 为 ListItem 真实数量（数据源条目 + LoadingFooter）
                this.paging.onScrollIndex(end, this.dataSource.totalCount() + 1)
              })
              .onScrollFrameBegin((offset: number, state: ScrollState) => {
                this.paging.onScrollFrameBegin(offset)
                return { offsetRemain: offset }
              })
              // 内容三态交叉淡变 300ms LinearOutSlowIn（对齐安卓 Crossfade(300)），reduced 降 180ms
              .transition(TransitionEffect.OPACITY)
              .animation(ReduceMotion.spec(MotionTiming.ENTER_CARD))
            }
          }
          .width('100%')
          .height('100%')
          .padding({ top: safeTopVp(), bottom: safeBottomVp() })

          // FAB 发起点单（自绘圆形 + 品牌渐变 + 十字，对齐 Android dining FAB 自绘）
          Column() {
            SymbolGlyph($r('sys.symbol.plus'))
              .fontSize(24)
              .fontColor([$r('app.color.ON_PRIMARY')])
          }
          .width(FAB_SIZE)
          .height(FAB_SIZE)
          .borderRadius(FAB_SIZE / 2)
          .linearGradient({
            angle: 135,
            colors: [[$r('app.color.PRIMARY'), 0], [$r('app.color.PRIMARY_DARK'), 1]]
          })
          .shadow({
            radius: DesignElevation.CARD_SHADOW_RADIUS,
            color: $r('app.color.SHADOW_COLOR_CARD'),
            offsetX: 0,
            offsetY: DesignElevation.CARD_SHADOW_OFFSET
          })
          .margin({
            right: DesignDimens.PAGE_PADDING,
            bottom: Math.max(safeBottomVp(), DesignDimens.SPACE_LG) + DesignDimens.SPACE_MD
          })
          // FAB 按压 100ms FastOutSlowIn scale 0.97（对齐安卓 pressable）；reduced 改 alpha 0.86
          .scale({
            x: !this.reduceMotion && this.pressedKey === 'fab' ? MotionScale.Press : 1,
            y: !this.reduceMotion && this.pressedKey === 'fab' ? MotionScale.Press : 1
          })
          .opacity(this.reduceMotion && this.pressedKey === 'fab' ? MotionAlpha.PressReduced : 1)
          .animation(MotionTiming.PRESS)
          .onTouch((event: TouchEvent) => {
            if (event.type === TouchType.Down) {
              this.pressedKey = 'fab'
            } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
              this.pressedKey = ''
            }
          })
          .onClick(() => {
            pathStack.pushPathByName(RouteNames.DINING_ORDER_CREATE, new DiningOrderCreateParam())
          })
        }
        .width('100%')
        .height('100%')
        .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
    .onShown(() => {
      // 离栈再回（创建/加入/结束点单后）重拉列表；首显与 aboutToAppear 去重
      if (!this.hasShown) {
        this.hasShown = true
        return
      }
      logger.debug(TAG, '列表页重回可见，重拉订单')
      this.reloadFirstPage()
    })
  }

  /** 「我发起的 / 我参与的」分区标题（区块标题档 18/600） */
  @Builder
  SectionHeader(title: string) {
    Text(title)
      .fontSize(DesignFontSize.SECTION_TITLE)
      .fontWeight(DesignFontWeight.SEMI_BOLD)
      .fontColor($r('app.color.TEXT_PRIMARY'))
      .width('100%')
      .padding({
        left: DesignDimens.PAGE_PADDING,
        right: DesignDimens.PAGE_PADDING,
        top: DesignDimens.SPACE_MD,
        bottom: DesignDimens.SPACE_XS
      })
  }

  /** 订单卡：标题 + 状态标签 + 时间 + 「再来一单」（AppCard 唯一画法 + pressable） */
  @Builder
  OrderCard(order: DiningOrderListVo) {
    Column() {
      Row({ space: DesignDimens.SPACE_SM }) {
        Text(order.title.length > 0 ? order.title : DiningText.dining_order_join_unknown_title)
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .layoutWeight(1)

        DiningStatusTag({ status: diningOrderStatusFromCode(order.status) })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      Row({ space: DesignDimens.SPACE_SM }) {
        if (order.isCreator) {
          Text(DiningText.dining_order_creator_badge)
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor($r('app.color.PRIMARY'))
            .backgroundColor($r('app.color.PRIMARY_LIGHT'))
            .borderRadius(DesignDimens.RADIUS_FULL)
            .padding({ left: DesignDimens.SPACE_XS, right: DesignDimens.SPACE_XS, top: 2, bottom: 2 })
        }
        Text(diningTimeLabel(order.createdAt))
          .fontSize(DesignFontSize.CAPTION)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .layoutWeight(1)

        // 草稿不提供「再来一单」：点卡片进详情继续编辑（复用仅对已定稿单有意义）
        if (diningOrderStatusFromCode(order.status) !== DiningOrderStatus.DRAFT) {
          Text(DiningText.dining_order_action_reuse)
          .fontSize(DesignFontSize.BUTTON_TEXT)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.TEXT_GREEN'))
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM, top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
          // 「再来一单」按压 100ms FastOutSlowIn scale 0.97（对齐安卓 pressable）；reduced 改 alpha 0.86
          .scale({
            x: !this.reduceMotion && this.pressedKey === `reuse_${order.id}` ? MotionScale.Press : 1,
            y: !this.reduceMotion && this.pressedKey === `reuse_${order.id}` ? MotionScale.Press : 1
          })
          .opacity(this.reduceMotion && this.pressedKey === `reuse_${order.id}` ? MotionAlpha.PressReduced : 1)
          .animation(MotionTiming.PRESS)
          .onTouch((event: TouchEvent) => {
            if (event.type === TouchType.Down) {
              this.pressedKey = `reuse_${order.id}`
            } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
              this.pressedKey = ''
            }
          })
          .onClick(() => {
            this.goCreateForReuse(order)
          })
        }
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
      .margin({ top: DesignDimens.SPACE_XS })
    }
    .width('100%')
    .backgroundColor($r('app.color.SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor($r('app.color.OUTLINE'))
    .padding(DesignDimens.CARD_PADDING)
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: $r('app.color.SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
    .margin({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, bottom: DesignDimens.CARD_SPACING })
    // 订单卡按压 100ms FastOutSlowIn scale 0.97（对齐安卓 pressable）；reduced 改 alpha 0.86
    .scale({
      x: !this.reduceMotion && this.pressedKey === `card_${order.id}` ? MotionScale.Press : 1,
      y: !this.reduceMotion && this.pressedKey === `card_${order.id}` ? MotionScale.Press : 1
    })
    .opacity(this.reduceMotion && this.pressedKey === `card_${order.id}` ? MotionAlpha.PressReduced : 1)
    .animation(MotionTiming.PRESS)
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = `card_${order.id}`
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = ''
      }
    })
    .onClick(() => {
      this.goOrderBody(order)
    })
  }

  /** 重置分页并重拉第 1 页（首载 / 离栈再回 / 失败重试） */
  private reloadFirstPage(): void {
    this.paging.reset()
    this.fetchOrderPage(1)
  }

  /**
   * 按页拉取我的订单并按 isCreator 派生分区。
   * @returns 本页是否有数据（供 PagingController 判断是否封底）
   */
  private async fetchOrderPage(page: number): Promise<boolean> {
    this.isLoading = page === 1 && this.createdOrders.length === 0 && this.joinedOrders.length === 0
    this.isError = false
    try {
      const res = await pageMyDiningOrders(page, PAGE_SIZE)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const records: Array<DiningOrderListVo> = res.data.records
        if (page === 1) {
          this.createdOrders = []
          this.joinedOrders = []
        }
        this.appendOrders(records)
        this.syncDataSource()
        logger.debug(TAG, `订单列表加载成功 page=${page} 本页=${records.length} ` +
          `发起=${this.createdOrders.length} 参与=${this.joinedOrders.length}`)
        return records.length > 0
      }
      logger.warn(TAG, `订单列表未生效 page=${page} code=${res.code} message=${res.message ?? ''}`)
      if (page === 1) {
        this.isError = true
      }
      showToastSafety(res.message, DiningText.dining_order_list_load_failed)
      return false
    } catch (err) {
      logger.error(TAG, '订单列表加载异常', err instanceof Error ? err.message : '')
      if (page === 1) {
        this.isError = true
      }
      showToastSafety('', DiningText.dining_order_list_load_failed)
      return false
    } finally {
      if (page === 1) {
        this.isLoading = false
      }
    }
  }

  /**
   * 追加分页记录到对应分区：按 id 去重（防触底/重拉并发重复），
   * 并按 createdAt 倒序重排（后端分页原序为倒序，合并后保持稳定顺序）。
   */
  private appendOrders(records: Array<DiningOrderListVo>): void {
    const seen = new Set<number>()
    for (const order of this.createdOrders) {
      seen.add(order.id)
    }
    for (const order of this.joinedOrders) {
      seen.add(order.id)
    }
    for (const order of records) {
      if (seen.has(order.id)) {
        continue
      }
      seen.add(order.id)
      if (order.isCreator) {
        this.createdOrders.push(order)
      } else {
        this.joinedOrders.push(order)
      }
    }
    this.createdOrders.sort((a: DiningOrderListVo, b: DiningOrderListVo) =>
      (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
    this.joinedOrders.sort((a: DiningOrderListVo, b: DiningOrderListVo) =>
      (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
  }

  /** 由分区数组重建 LazyForEach 数据源（分区标题 + 订单卡，键稳定） */
  private syncDataSource(): void {
    const listEntries: Array<DiningOrderListEntry> = []
    if (this.createdOrders.length > 0) {
      listEntries.push({
        key: 'header_created',
        isHeader: true,
        title: DiningText.dining_order_section_created,
        order: null
      })
      for (const order of this.createdOrders) {
        listEntries.push({ key: `created_${order.id}`, isHeader: false, title: '', order: order })
      }
    }
    if (this.joinedOrders.length > 0) {
      listEntries.push({
        key: 'header_joined',
        isHeader: true,
        title: DiningText.dining_order_section_joined,
        order: null
      })
      for (const order of this.joinedOrders) {
        listEntries.push({ key: `joined_${order.id}`, isHeader: false, title: '', order: order })
      }
    }
    this.entries = listEntries
    this.dataSource.notifyReload()
  }

  /**
   * 卡片点击：草稿/进行中 → 点单页（草稿继续编辑、进行中勾选）；
   * 已结束/未知 → 购买清单结果页（未知态保守降级同 FINISHED）。
   */
  private goOrderBody(order: DiningOrderListVo): void {
    const status = diningOrderStatusFromCode(order.status)
    if (status === DiningOrderStatus.DRAFT || status === DiningOrderStatus.ONGOING) {
      pathStack.pushPathByName(RouteNames.DINING_ORDER_DETAIL, order.id)
    } else {
      pathStack.pushPathByName(RouteNames.DINING_SHOPPING_LIST, order.id)
    }
  }

  /** 「再来一单」：创建页复用模式（携带源单 ID 与标题） */
  private goCreateForReuse(order: DiningOrderListVo): void {
    const param = new DiningOrderCreateParam()
    param.fromOrderId = order.id
    param.sourceTitle = order.title
    pathStack.pushPathByName(RouteNames.DINING_ORDER_CREATE, param)
  }
}

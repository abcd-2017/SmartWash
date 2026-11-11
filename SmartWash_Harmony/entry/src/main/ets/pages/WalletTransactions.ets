import { AppButton, AppButtonVariant, EmptyState, IconBox, LoadingState, PageHeader } from "../view/common/AppComponents"
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from "../constant/DesignSystem"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import { getTransactions } from "../network/api/UserApi"
import { TransactionVo } from "../network/vo/TransactionVo"
import { HttpStatusCode } from "@ohos/axios"
import { LoadingFooter } from "../view/LoadingFooter"
import { showToastSafety } from "../utils/ToastUtil"
import { logger } from "../utils/Logger"
import { PagingController } from "../utils/PagingUtil"
import { pathStack } from "../utils/PathStackUtil"
import { RouteNames } from "../constant/RouteNames"
import { ThemeRefresh } from '../view/common/ThemeRefresh'
import { resColor } from '../utils/ResourceColor'
import { resString } from '../utils/ResourceString'

// 分页每页条数（后端 pageSize 上限 50，常规列表沿用 10）
const PAGE_SIZE: number = 10

// 流水类型标识（对齐后端 TransactionVo.type 取值）
const TYPE_RECHARGE: string = "recharge"
const TYPE_PAYMENT: string = "payment"
// 成功状态标识（对齐后端 TransactionVo.status 取值）
const STATUS_SUCCESS: string = "success"

@Builder
export function WalletTransactionsBuilder() {
  WalletTransactions()
}

@Component
struct WalletTransactions {
  @State recordList: Array<TransactionVo> = []
  @State isLoadingMore: boolean = false
  // 首屏加载态：数据返回前展示占位，避免页面空白（对齐充值记录页 LoadingState 模式）
  @State isLoadingFirst: boolean = true
  // 首载失败态（对齐安卓 WalletListState.Error，audit-21 #1 同口径）：page 1 失败且
  // 无内容时置位，错误态与空态严格分离——原实现失败仅 toast 落入空态，伪装「暂无交易记录」
  @State isFirstError: boolean = false
  // 追加加载失败态（对齐安卓 append Error → footer 重试行）；重试经 paging.retry() 重拉同一页
  @State appendError: boolean = false
  // 轻量分页封装：触底加载/上锁防重/封底判定统一收敛
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchTransactionPage(page)
    },
    (loading: boolean) => {
      this.isLoadingMore = loading
    }
  )

  async aboutToAppear(): Promise<void> {
    logger.debug('walletTransactions', '钱包流水页挂载，拉取首页数据')
    await this.fetchTransactionPage(1)
    this.isLoadingFirst = false
  }

  /**
   * 流水时间展示格式化（防御性兼容两种入参，与充值记录页 formatRechargeTime 同策略）：
   * 已是空格分隔串则原样展示，ISO（含 T）则转为 `yyyy-MM-dd HH:mm`；解析失败原样返回，不吞数据。
   */
  private formatTransactionTime(raw: string): string {
    if (raw === "") {
      return raw
    }
    if (!raw.includes("T")) {
      return raw
    }
    // ISO 取前 16 位（yyyy-MM-ddTHH:mm）并把 T 替换为空格
    const head: string = raw.length >= 16 ? raw.substring(0, 16) : raw
    return head.replace("T", " ")
  }

  /** 状态文案：后端当前仅产出 success，未知值原样兜底展示不吞数据 */
  private statusText(status: string): string {
    return status === STATUS_SUCCESS ? resString('transaction_status_success') : status
  }

  /**
   * 金额展示：充值 +¥ / 消费 -¥；消费流水后端已下发负数金额，
   * 统一按类型重算符号（对 amount 取绝对值），避免依赖后端符号约定。
   */
  private amountText(item: TransactionVo): string {
    const value = Math.abs(item.amount ?? 0).toFixed(2)
    return item.type === TYPE_RECHARGE ? `+¥${value}` : `-¥${value}`
  }

  /** 金额色：充值 = primaryDark（22 大字豁免内取更优档 4.23:1）/ 消费 = textPrimary
   *  （金额正负已有 +/− 符号双通道，负金额非「错误」警示语义；均对齐安卓 audit-21 修后值） */
  private amountColor(item: TransactionVo): number {
    return item.type === TYPE_RECHARGE ? resColor('PRIMARY_DARK') : resColor('TEXT_PRIMARY')
  }

  /**
   * 首载重试：回骨架拉首页，结束（成功/失败）后复位加载态（page 1 失败路径不 throw）。
   */
  private async retryFirstLoad(): Promise<void> {
    this.isLoadingFirst = true
    try {
      await this.fetchTransactionPage(1)
    } finally {
      this.isLoadingFirst = false
    }
  }

  /**
   * 按页拉取钱包流水（对齐安卓 walletListStateOf 四态推导）：
   * 首载失败置 isFirstError（错误态优先于空态，给重试入口，不伪装「暂无交易记录」；
   * 列表非空时保留旧内容仅 toast，stale-while-revalidate 语义）；
   * 追加失败置 appendError 并 throw——分页器 catch 记失败页不封底（页码不推进），
   * 尾部重试行 / 再次触底重拉同一页，对齐安卓 Paging append Error。
   * @returns 本页是否有数据（供 PagingController 判断是否封底）
   */
  async fetchTransactionPage(page: number): Promise<boolean> {
    try {
      let res = await getTransactions(page, PAGE_SIZE)
      if (res.code === HttpStatusCode.Ok && res.data !== null) {
        let records = res.data!.records
        if (records === null || records.length === 0) {
          // 末页封底（不再弹「没有更多数据了」，对齐安卓 Paging 静默停止；跨页先例 fix-04/08 已删）
          if (page === 1) {
            this.isFirstError = false
          }
          this.appendError = false
          return false
        }
        if (page === 1) {
          // stale-while-revalidate（对齐充值记录页）：刷新成功才整体替换，刷新期间旧内容保持可读
          this.recordList = records
          this.isFirstError = false
          logger.debug('walletTransactions', `钱包流水首页加载成功 count=${records.length}`)
          return true
        }
        this.recordList.push(...records)
        this.appendError = false
        logger.debug('walletTransactions', `钱包流水分页加载成功 page=${page} count=${records.length}`)
        return true
      }
      logger.warn('walletTransactions', `钱包流水加载失败 page=${page} ` +
        `code=${res.code} message=${res.message ?? ""}`)
      showToastSafety(res.message, resString('wallet_transactions_1'))
      if (page === 1) {
        if (this.recordList.length === 0) {
          this.isFirstError = true
        }
        return false
      }
      // 追加失败置失败态并 reject（throw 落入下方 catch 重抛，toast 不重复）
      this.appendError = true
      throw new Error(resString('wallet_transactions_2'))
    } catch (err) {
      if (page === 1) {
        // 网络异常/401 拒绝时 promise 会 throw：兜住进错误态，避免 isLoadingFirst 卡死永久骨架
        showToastSafety(resString('wallet_transactions_3'))
        if (this.recordList.length === 0) {
          this.isFirstError = true
        }
        return false
      }
      this.appendError = true
      throw new Error(resString('wallet_transactions_2'))
    }
  }

  @Builder
  TransactionCard(item: TransactionVo) {
    Row() {
      // 左侧：类型图标盒（§3.2 容器规格 44 容器 / 12 圆角 / 24 图标，对齐安卓 TransactionCard
      // 图标盒形态）。原「文字标签盒 + 消费粉红 ERROR_CONTAINER」为鸿蒙自创——「消费」非错误
      // 语义。充值 = 账户青（iconContainerTeal + iconForegroundTeal，符号取 creditcard 钱包卡类
      // 近似档，对齐安卓 AccountBalanceWallet）；消费 = 洗护绿（iconContainerGreen +
      // iconForegroundGreen，laundry 洗衣机符号对齐安卓 ic_washing_machine）
      IconBox({
        icon: item.type === TYPE_RECHARGE ? $r('sys.symbol.creditcard') : $r('sys.symbol.laundry'),
        containerColor: item.type === TYPE_RECHARGE
          ? resColor('ICON_CONTAINER_TEAL')
          : resColor('ICON_CONTAINER_GREEN'),
        iconColor: item.type === TYPE_RECHARGE
          ? resColor('ICON_FOREGROUND_TEAL')
          : resColor('ICON_FOREGROUND_GREEN')
      })

      // 中间：描述 + 时间
      Column() {
        Text(item.description ?? "")
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontWeight(DesignFontWeight.NORMAL)
          .fontColor(resColor('TEXT_PRIMARY'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })

        Text(this.formatTransactionTime(item.transactionTime ?? ""))
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(resColor('TEXT_SECONDARY'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .margin({ top: 4 })
      }
      .alignItems(HorizontalAlign.Start)
      .margin({ left: 12 })
      .layoutWeight(1)

      // 右侧：金额（充值 primaryDark / 消费 textPrimary，22/600 + tnum 对齐安卓
      // titleLarge 金额档）+ 状态文案
      Column() {
        Text(this.amountText(item))
          .fontSize(DesignFontSize.STATUS_LARGE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontFeature(DesignFontFeature.TNUM)
          .fontColor(this.amountColor(item))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Clip })

        Text(this.statusText(item.status ?? ""))
          .fontSize(DesignFontSize.CAPTION)
          // 状态是决策所需可读信息（textTertiary 浅色 2.25:1 击穿 §10 红线，audit-21 #1）
          .fontColor(resColor('TEXT_SECONDARY'))
          .margin({ top: 4 })
      }
      .alignItems(HorizontalAlign.End)
      .margin({ left: 12 })
    }
    .width("100%")
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor(resColor('SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    // DESIGN-SPEC §3.1 唯一卡片画法：1px outline 描边 + 卡片档阴影（对齐充值记录页）
    .border({ width: 1, color: resColor('OUTLINE'), radius: DesignDimens.CARD_RADIUS })
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: resColor('SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        // 页头（公共 PageHeader：22/600 标题 + 48dp 返回热区）
        PageHeader({ title: resString('wallet_transactions') })

        if (this.isLoadingFirst) {
          // 首屏加载态（骨架屏，§3.6 禁转圈）
          LoadingState()
            .width("100%")
            .layoutWeight(1)
            .transition(TransitionEffect.OPACITY)
            .animation({ duration: 200, curve: Curve.EaseOut })
        } else if (this.recordList.length > 0) {
          // 与充值记录页一致：LazyColumn 触底分页，无下拉刷新入口
          List({ space: DesignDimens.CARD_SPACING }) {
            ForEach(this.recordList, (item: TransactionVo, index: number) => {
              ListItem() {
                this.TransactionCard(item)
              }
            }, (item: TransactionVo, index: number) => `${item.transactionTime ?? ""}-${item.type ?? ""}-${index}`)
            ListItem() {
              if (this.appendError) {
                // 追加失败：整行可点重试（主文案 DANGER_INK 14，热区 ≥48，
                // 对齐安卓 AppendErrorFooter / audit-21 #3；重试重拉同一失败页）
                Column() {
                  Text($r('app.string.record_append_failed'))
                    .fontSize(DesignFontSize.BODY_SMALL)
                    .fontColor(resColor('DANGER_INK'))
                }
                .width("100%")
                .constraintSize({ minHeight: 48 })
                .justifyContent(FlexAlign.Center)
                .alignItems(HorizontalAlign.Center)
                .onClick(() => {
                  this.paging.retry()
                })
              } else {
                LoadingFooter({ isLoading: !!this.isLoadingMore })
              }
            }
          }
          .width("100%")
          .height("100%")
          // top 8 / bottom 16+导航条（对齐安卓 contentPadding spaceXs / spaceMd + navBars，
          // 无导航条设备末卡与 footer 不贴屏幕底）
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_XS,
            bottom: safeBottomVp() + DesignDimens.SPACE_MD
          })
          .onScrollIndex((start: number, end: number) => {
            this.paging.onScrollIndex(end, this.recordList?.length ?? 0)
          })
          .onScrollFrameBegin((offset: number, state: ScrollState) => {
            this.paging.onScrollFrameBegin(offset)
            return { offsetRemain: offset }
          })
          .transition(TransitionEffect.OPACITY)
          .animation({ duration: 200, curve: Curve.EaseOut })
        } else if (this.isFirstError) {
          // 首载失败：说明 + 重试（对齐安卓 WalletListState.Error；错误态优先于空态判定，
          // 不得伪装成「暂无交易记录」）。trailing builder 后不可续链式属性，
          // 尺寸/过渡挂在包裹 Column 上
          Column() {
            EmptyState({ message: resString('wallet_load_failed') }) {
              Column() {
                AppButton({
                  small: true,
                  text: resString('retry'),
                  variant: AppButtonVariant.SECONDARY,
                  click: (): void => {
                    this.retryFirstLoad()
                  }
                })
              }
              .margin({ top: DesignDimens.SPACE_MD })
            }
          }
          .width("100%")
          .layoutWeight(1)
          .transition(TransitionEffect.OPACITY)
          .animation({ duration: 200, curve: Curve.EaseOut })
        } else {
          // 空状态（规范 §3.6 空态三件套：母题 + 引导文案 + 行动按钮「去充值」，
          // 文案对齐安卓 no_wallet_transactions 修后值）
          Column() {
            EmptyState({ message: resString('no_wallet_transactions') }) {
              Column() {
                AppButton({
                  small: true,
                  text: resString('go_recharge'),
                  click: (): void => {
                    pathStack.pushPathByName(RouteNames.RECHARGE, null)
                  }
                })
              }
              .margin({ top: DesignDimens.SPACE_MD })
            }
          }
          .width("100%")
          .layoutWeight(1)
        }
      }
      .width("100%")
      .height("100%")
      // 沉浸式：内容避让状态栏（背景延伸）
      .padding({ top: safeTopVp() })
      }
    }
    .width("100%")
    .height("100%")
    .align(Alignment.Top)
    .backgroundColor(resColor('BACKGROUND'))
    .hideTitleBar(true)
  }
}

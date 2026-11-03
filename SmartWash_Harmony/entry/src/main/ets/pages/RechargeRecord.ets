import { EmptyState, LoadingState } from "../view/common/AppComponents"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from "../constant/DesignSystem"
import { RechargeType } from "../constant/RechargeType"
import { getRechargeRecordList } from "../network/api/ReChargeApi"
import { RechargeRecordVo } from "../network/vo/RechargeRecordVo"
import { HttpStatusCode } from "@ohos/axios"
import { pathStack } from "../utils/PathStackUtil"
import { LoadingFooter } from "../view/LoadingFooter"
import { showToastSafety } from "../utils/ToastUtil"
import { PagingController } from "../utils/PagingUtil"

// 分页每页条数
const PAGE_SIZE: number = 10

@Builder
export function RechargeRecordBuilder() {
  RechargeRecord()
}

@Component
struct RechargeRecord {
  @State recordList: Array<RechargeRecordVo> = []
  @State isRefreshing: boolean = false
  @State isLoadingMore: boolean = false
  // 首屏加载态：数据返回前展示占位，避免页面空白（对齐安卓 RechargeRecordPage.kt:111-120）
  @State isLoadingFirst: boolean = true
  // 轻量分页封装：触底加载/上锁防重/封底判定统一收敛（差异分析 01 §3.3）
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchRecordPage(page)
    },
    (loading: boolean) => {
      this.isLoadingMore = loading
    }
  )

  async aboutToAppear(): Promise<void> {
    await this.fetchRecordPage(1)
    this.isLoadingFirst = false
  }

  /**
   * 充值时间展示格式化（对齐安卓 formatRechargeTime）：
   * 后端已补 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`，端上防御性兼容两种入参——
   * 已是空格分隔串则原样展示，ISO（含 T）则转为 `yyyy-MM-dd HH:mm`；解析失败原样返回，不吞数据。
   */
  private formatRechargeTime(raw: string): string {
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

  /**
   * 按页拉取充值记录。
   * @returns 本页是否有数据（供 PagingController 判断是否封底）
   */
  async fetchRecordPage(page: number): Promise<boolean> {
    // ArkTS 禁止裸对象字面量作参数（arkts-no-untyped-obj-literals），查询参数用 Record 并按索引赋值
    const param: Record<string, Object> = {}
    param['page'] = page
    param['size'] = PAGE_SIZE
    let res = await getRechargeRecordList(param)
    if (res.code === HttpStatusCode.Ok && res.data !== null) {
      let records = res.data!.records
      if (records === null || records.length === 0) {
        if (this.recordList.length > 0) {
          showToastSafety("没有更多数据了")
        }
        return false
      }
      if (page === 1) {
        // stale-while-revalidate（对齐安卓）：刷新成功才整体替换，刷新期间旧内容保持可读
        this.recordList = records
        return true
      }
      this.recordList.push(...records)
      return true
    }
    showToastSafety(res.message, "充值记录加载失败")
    return false
  }

  refreshRecords() {
    this.isRefreshing = false
    this.paging.reset()
    this.fetchRecordPage(1)
  }

  @Builder
  RecordCard(record: RechargeRecordVo) {
    Row() {
      // 左侧：支付方式图标容器（对齐安卓 44 容器 / 12 圆角 / 24 图标 + 六色浅底）
      Row() {
        if (record.rechargeType === RechargeType.WECHAT) {
          Image($r('app.media.wechat'))
            .width(24)
            .height(24)
        } else if (record.rechargeType === RechargeType.ALIPAY) {
          Image($r('app.media.alipay'))
            .width(24)
            .height(24)
        } else {
          SymbolGlyph($r('sys.symbol.creditcard'))
            .fontSize(24)
            .fontColor([DesignColor.PRIMARY])
        }
      }
      .width(44)
      .height(44)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      // 微信/余额 = primaryContainer(#F0FBF4)、支付宝 = iconContainerBlue(#EEF2FB)（对齐安卓 RechargeRecordCard）
      .backgroundColor(record.rechargeType === RechargeType.WECHAT ? DesignColor.ICON_CONTAINER_GREEN :
        record.rechargeType === RechargeType.ALIPAY ? DesignColor.ICON_CONTAINER_BLUE : DesignColor.ICON_CONTAINER_GREEN)
      .justifyContent(FlexAlign.Center)
      .alignItems(VerticalAlign.Center)

      // 中间：支付方式名称 + 充值时间
      Column() {
        Text(record.rechargeType === RechargeType.WECHAT ? "微信支付" :
          record.rechargeType === RechargeType.ALIPAY ? "支付宝" : "充值")
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontWeight(DesignFontWeight.NORMAL)
          .fontColor(DesignColor.TEXT_PRIMARY)

        Text(this.formatRechargeTime(record.rechargeTime ?? ""))
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .margin({ top: 4 })
      }
      .alignItems(HorizontalAlign.Start)
      .margin({ left: 12 })
      .layoutWeight(1)

      // 右侧：充值金额（对齐安卓 AmountMedium 18/600 + primaryDark + tnum + 单行 Clip）
      Text(`+¥${record.amount?.toFixed(2) ?? "0.00"}`)
        .fontSize(DesignFontSize.AMOUNT_MEDIUM)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontFeature(DesignFontFeature.TNUM)
        .fontColor(DesignColor.PRIMARY_DARK)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Clip })
    }
    .width("100%")
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    // DESIGN-SPEC v0.4 §3.1 / §2.4 唯一卡片画法：1px outline 描边 + level1 阴影
    .border({ width: 1, color: DesignColor.OUTLINE, radius: DesignDimens.CARD_RADIUS })
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: DesignColor.SHADOW_COLOR_CARD,
      offsetX: 0,
      offsetY: 2
    })
  }

  build() {
    NavDestination() {
      Column() {
        // 页面标题行
        Row() {
          SymbolGlyph($r('sys.symbol.chevron_left'))
            .fontSize(24)
            .fontColor([DesignColor.ON_BACKGROUND])
            .onClick(() => {
              pathStack.pop()
            })
            .padding(8)

          Text("充值记录")
            .fontSize(DesignFontSize.ROOT_TITLE)
            .fontWeight(DesignFontWeight.BOLD)
            .fontColor(DesignColor.ON_BACKGROUND)
            .margin({ left: 4 })
        }
        .width("100%")
        .height(56)
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        .alignItems(VerticalAlign.Center)

        if (this.isLoadingFirst) {
          // 首屏加载态（对齐安卓 LoadingState：骨架屏，§3.6 禁转圈）
          LoadingState()
          .width("100%")
          .layoutWeight(1)
          .transition(TransitionEffect.OPACITY)
          .animation({ duration: 200, curve: Curve.EaseOut })
        } else if (this.recordList.length > 0) {
          Refresh({ refreshing: $$this.isRefreshing }) {
            List({ space: DesignDimens.CARD_SPACING }) {
              ForEach(this.recordList, (record: RechargeRecordVo) => {
                ListItem() {
                  this.RecordCard(record)
                }
              }, (record: RechargeRecordVo, index: number) => `${record.recordId ?? index}`)
              ListItem() {
                LoadingFooter({ isLoading: !!this.isLoadingMore })
              }
            }
            .width("100%")
            .height("100%")
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
            .divider({ strokeWidth: 0, color: Color.Transparent })
            .onScrollIndex((start: number, end: number) => {
              this.paging.onScrollIndex(end, this.recordList?.length ?? 0)
            })
            .onScrollFrameBegin((offset: number, state: ScrollState) => {
              this.paging.onScrollFrameBegin(offset)
              return { offsetRemain: offset }
            })
          }
          .onRefreshing(() => {
            this.refreshRecords()
          })
          .transition(TransitionEffect.OPACITY)
          .animation({ duration: 200, curve: Curve.EaseOut })
        } else {
          // 空状态（对齐安卓 EmptyState：滚筒圆母题 + 引导文案，§3.6 禁灰色 Material 图标）
          EmptyState({ message: "暂无充值记录" })
          .width("100%")
          .layoutWeight(1)
        }
      }
      .width("100%")
      .height("100%")
    }
    .width("100%")
    .height("100%")
    .align(Alignment.Top)
    .backgroundColor(DesignColor.BACKGROUND)
    .hideTitleBar(true)
  }
}

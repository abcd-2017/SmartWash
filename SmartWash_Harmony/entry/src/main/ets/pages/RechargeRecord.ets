import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { RechargeType } from "../constant/RechargeType"
import { getRechargeRecordList } from "../network/api/ReChargeApi"
import { RechargeRecordVo } from "../network/vo/RechargeRecordVo"
import { HttpStatusCode } from "@ohos/axios"
import { pathStack } from "../utils/PathStackUtil"
import { LoadingFooter } from "../view/LoadingFooter"
import { showToastSafety } from "../utils/ToastUtil"

@Builder
export function RechargeRecordBuilder() {
  RechargeRecord()
}

@Component
struct RechargeRecord {
  @State recordList: Array<RechargeRecordVo> = []
  @State isRefreshing: boolean = false
  @State isLoadingMore: boolean = false
  @State canLoad: boolean = false
  @State page: number = 1
  @State hasMore: boolean = true

  aboutToAppear(): void {
    this.loadRecords()
  }

  async loadRecords() {
    let res = await getRechargeRecordList({ page: this.page, size: 10 })
    if (res.code == HttpStatusCode.Ok && res.data != null) {
      let records = res.data!.records
      if (records == null || records.length == 0) {
        if (this.recordList.length > 0) {
          showToastSafety("没有更多数据了")
        }
        this.hasMore = false
      } else {
        if (this.page == 1) {
          this.recordList = []
        }
        this.recordList.push(...records)
      }
    } else {
      showToastSafety(res.message, "充值记录加载失败")
    }
  }

  refreshRecords() {
    this.isRefreshing = false
    this.isLoadingMore = false
    this.canLoad = false
    this.page = 1
    this.hasMore = true
    this.recordList = []
    this.loadRecords()
  }

  @Builder
  RecordCard(record: RechargeRecordVo) {
    Row() {
      // 左侧：支付方式图标容器
      Row() {
        if (record.rechargeType == RechargeType.WECHAT) {
          Image($r('app.media.wechat'))
            .width(22)
            .height(22)
        } else if (record.rechargeType == RechargeType.ALIPAY) {
          Image($r('app.media.alipay'))
            .width(22)
            .height(22)
        } else {
          SymbolGlyph($r('sys.symbol.creditcard'))
            .fontSize(22)
            .fontColor([DesignColor.PRIMARY])
        }
      }
      .width(44)
      .height(44)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      // 品牌浅色底收敛到 DesignSystem 常量（评审 #18），视觉不变
      .backgroundColor(record.rechargeType == RechargeType.WECHAT ? DesignColor.WECHAT_GREEN_LIGHT :
        record.rechargeType == RechargeType.ALIPAY ? DesignColor.ALIPAY_BLUE_LIGHT : DesignColor.PRIMARY_LIGHT)
      .justifyContent(FlexAlign.Center)
      .alignItems(VerticalAlign.Center)

      // 中间：支付方式名称 + 充值时间
      Column() {
        Text(record.rechargeType == RechargeType.WECHAT ? "微信支付" :
          record.rechargeType == RechargeType.ALIPAY ? "支付宝" : "充值")
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.TEXT_PRIMARY)

        Text(record.rechargeTime ?? "")
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: 2 })
      }
      .alignItems(HorizontalAlign.Start)
      .margin({ left: 12 })
      .layoutWeight(1)

      // 右侧：充值金额
      Text(`+¥${record.amount?.toFixed(2) ?? "0.00"}`)
        .fontSize(DesignFontSize.SECTION_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.PRIMARY)
    }
    .width("100%")
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .border({ width: 0.5, color: DesignColor.OUTLINE, radius: DesignDimens.CARD_RADIUS })
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
            .fontSize(DesignFontSize.PAGE_TITLE)
            .fontWeight(DesignFontWeight.BOLD)
            .fontColor(DesignColor.ON_BACKGROUND)
            .margin({ left: 4 })
        }
        .width("100%")
        .height(56)
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        .alignItems(VerticalAlign.Center)

        if (this.recordList.length > 0) {
          Refresh({ refreshing: $$this.isRefreshing }) {
            List() {
              ForEach(this.recordList, (record: RechargeRecordVo) => {
                ListItem() {
                  this.RecordCard(record)
                }
              })
              ListItem() {
                LoadingFooter({ isLoading: !!this.isLoadingMore })
              }
            }
            .width("100%")
            .height("100%")
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
            .divider({ strokeWidth: 0, color: Color.Transparent })
            .onScrollIndex((start: number, end: number) => {
              if (this.canLoad && this.hasMore && end >= (this.recordList?.length ?? 0) - 1) {
                this.canLoad = false
                this.isLoadingMore = true
                setTimeout(() => {
                  this.page++
                  this.loadRecords()
                  this.isLoadingMore = false
                }, 1200)
              }
            })
            .onScrollFrameBegin((offset: number, state: ScrollState) => {
              if (offset > 5 && !this.isLoadingMore) {
                this.canLoad = true
              }
              return { offsetRemain: offset }
            })
          }
          .onRefreshing(() => {
            setTimeout(() => {
              this.refreshRecords()
              this.isRefreshing = false
            }, 2000)
          })
        } else {
          // 空状态
          Column() {
            SymbolGlyph($r('sys.symbol.creditcard'))
              .fontSize(64)
              .fontColor([DesignColor.TEXT_TERTIARY])
              .margin({ bottom: 16 })

            Text("暂无充值记录")
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.TEXT_SECONDARY)
          }
          .width("100%")
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
          .alignItems(HorizontalAlign.Center)
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

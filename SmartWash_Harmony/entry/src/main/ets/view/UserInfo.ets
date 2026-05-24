import { HttpStatusCode } from "@ohos/axios"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { getOrderItemCount } from "../network/api/OrderApi"
import { getUserInfo, uploadAvatar } from "../network/api/UserApi"
import { photoAccessHelper } from '@kit.MediaLibraryKit'
import { OrderItemCountVo } from "../network/vo/OrderItemCountVo"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { pathStack } from "../utils/PathStackUtil"
import { promptAction } from "@kit.ArkUI"
import { ShowOrderStatusEnum } from "../constant/ShowOrderStatus"

@Component
export struct UserInfo {
  private scroller: Scroller = new Scroller()
  @State userInfo: UserInfoVo | null = null
  @StorageLink('refreshUserInfo') @Watch("onUserInfoChange") refreshFlag: boolean = false
  @State orderItemCount: OrderItemCountVo | null = null
  @StorageLink('refreshOrderCount') @Watch("onOrderCountChange") orderCountFlag: boolean = false
  @State avatarUrl: string | null = null
  @State isUploading: boolean = false

  //监听用户信息刷新状态变化
  onUserInfoChange() {
    if (this.refreshFlag) {
      this.refreshUserInfo()
    }
    this.refreshFlag = false
  }

  //监听订单数量刷新状态变化
  onOrderCountChange() {
    if (this.orderCountFlag) {
      this.getOrderCount()
    }
    this.orderCountFlag = false
  }

  aboutToAppear() {
    Promise.all([this.refreshUserInfo(), this.getOrderCount()])
  }

  async refreshUserInfo() {
    let res = await getUserInfo()
    if (res.code == HttpStatusCode.Ok) {
      this.userInfo = res.data
      this.avatarUrl = this.userInfo?.avatar ?? null
    } else {
      promptAction.showToast({ message: res.message })
    }
  }

  // 选择图片并上传头像
  async selectAndUploadAvatar() {
    try {
      let picker = new photoAccessHelper.PhotoViewPicker()
      let result = await picker.select({
        MIMEType: photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE,
        maxSelectNumber: 1
      })
      if (result.photoUris && result.photoUris.length > 0) {
        let uri = result.photoUris[0]
        this.isUploading = true
        let res = await uploadAvatar(uri)
        if (res.code == HttpStatusCode.Ok) {
          this.avatarUrl = res.data
          promptAction.showToast({ message: "头像上传成功" })
        } else {
          promptAction.showToast({ message: res.message ?? "头像上传失败" })
        }
        this.isUploading = false
      }
    } catch (err) {
      this.isUploading = false
      promptAction.showToast({ message: "头像上传失败" })
    }
  }

  async getOrderCount() {
    let res = await getOrderItemCount({
      pendingPaymentStatus: ShowOrderStatusEnum.PENDING_PAYMENT,
      processingStatus: ShowOrderStatusEnum.WASHING,
      pendingPickupStatus: ShowOrderStatusEnum.READY_FOR_PICKUP,
      shippedStatus: ShowOrderStatusEnum.PENDING_SHIPMENT,
    })
    if (res.code == HttpStatusCode.Ok) {
      this.orderItemCount = res.data
    } else {
      promptAction.showToast({ message: res.message })
    }
  }

  //跳转到订单列表页
  jumpToOrderList(index: number) {
    pathStack.pushPath({
      name: "OrderList", param: index
    })
  }

  // 渲染订单快捷入口
  @Builder
  orderQuickEntry(icon: Resource, label: string, count: number, onClick: () => void) {
    Column() {
      // 图标容器
      Column() {
        SymbolGlyph(icon)
          .fontSize(20)
          .fontColor([Color(DesignColor.PRIMARY)])
      }
      .width(40)
      .height(40)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      .backgroundColor(Color(DesignColor.PRIMARY_LIGHT))
      .justifyContent(FlexAlign.Center)

      Text(label)
        .fontSize(DesignFontSize.BODY)
        .fontColor(Color(DesignColor.TEXT_PRIMARY))
        .margin({ top: 8 })

      if (count > 0) {
        Text(`${count}单`)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(Color(DesignColor.PRIMARY))
          .margin({ top: 4 })
      } else {
        Text(" ")
          .fontSize(DesignFontSize.CAPTION)
          .margin({ top: 4 })
      }
    }
    .layoutWeight(1)
    .alignItems(HorizontalAlign.Center)
    .padding({ top: 16, bottom: 16 })
    .onClick(onClick)
  }

  // 渲染功能列表项
  @Builder
  functionItem(icon: Resource, title: string, subtitle: string, onClick: () => void) {
    Row() {
      // 左侧图标容器
      Column() {
        SymbolGlyph(icon)
          .fontSize(18)
          .fontColor([Color(DesignColor.PRIMARY)])
      }
      .width(36)
      .height(36)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      .backgroundColor(Color(DesignColor.PRIMARY_LIGHT))
      .justifyContent(FlexAlign.Center)

      // 标题 + 副标题
      Column() {
        Text(title)
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontColor(Color(DesignColor.TEXT_PRIMARY))
        Text(subtitle)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(Color(DesignColor.TEXT_SECONDARY))
          .margin({ top: 2 })
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
      .margin({ left: 12 })

      // 右侧箭头
      SymbolGlyph($r('sys.symbol.chevron_right'))
        .fontSize(16)
        .fontColor([Color(DesignColor.TEXT_SECONDARY)])
    }
    .width("100%")
    .padding({ left: 16, right: 16, top: 14, bottom: 14 })
    .backgroundColor(Color(DesignColor.SURFACE))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .border({ width: 0.5, color: Color(DesignColor.OUTLINE) })
    .onClick(onClick)
  }

  build() {
    Scroll(this.scroller) {
      Column() {
        // ========== 标题行 ==========
        Row() {
          Text("个人中心")
            .fontSize(DesignFontSize.PAGE_TITLE)
            .fontWeight(DesignFontWeight.BOLD)
            .fontColor(Color(DesignColor.ON_BACKGROUND))

          SymbolGlyph($r('sys.symbol.gearshape'))
            .fontSize(24)
            .fontColor([Color(DesignColor.TEXT_SECONDARY)])
            .padding(12)
            .onClick(() => {
              pathStack.pushPathByName("Setting", null)
            })
        }
        .justifyContent(FlexAlign.SpaceBetween)
        .alignItems(VerticalAlign.Center)
        .width("100%")
        .padding({ top: 8 })

        // ========== 用户信息区 ==========
        Column() {
          // 头像区域 - 点击可上传
          Stack({ alignContent: Alignment.BottomEnd }) {
            if (this.avatarUrl != null && this.avatarUrl !== "") {
              // 有头像时显示头像图片
              Image(this.avatarUrl)
                .width(72)
                .height(72)
                .borderRadius(36)
                .objectFit(ImageFit.Crop)
            } else {
              // 无头像时显示默认图标
              Column() {
                if (this.isUploading) {
                  LoadingProgress()
                    .width(28)
                    .height(28)
                    .color(DesignColor.PRIMARY)
                } else {
                  SymbolGlyph($r('sys.symbol.person_crop_circle_fill'))
                    .fontSize(32)
                    .fontColor([Color(DesignColor.PRIMARY)])
                }
              }
              .width(72)
              .height(72)
              .borderRadius(36)
              .backgroundColor(Color(DesignColor.PRIMARY_LIGHT))
              .justifyContent(FlexAlign.Center)
            }

            // 相机角标
            Column() {
              SymbolGlyph($r('sys.symbol.camera_fill'))
                .fontSize(13)
                .fontColor([Color.White])
            }
            .width(22)
            .height(22)
            .borderRadius(11)
            .backgroundColor(Color(DesignColor.PRIMARY))
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)
          }
          .onClick(() => {
            this.selectAndUploadAvatar()
          })

          // 手机号
          Text(this.userInfo?.phoneNumber ?? "未登录")
            .fontSize(18)
            .fontWeight(DesignFontWeight.BOLD)
            .fontColor(Color(DesignColor.TEXT_PRIMARY))
            .margin({ top: 12 })

          // 学号
          Text(`学号：${this.userInfo?.studentId ?? ""}`)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(Color(DesignColor.TEXT_SECONDARY))
            .margin({ top: 4 })
        }
        .width("100%")
        .alignItems(HorizontalAlign.Center)
        .padding({ top: 24, bottom: 24 })

        // ========== 余额信息卡（渐变绿底） ==========
        Column() {
          Row() {
            // 左侧：账户余额
            Column() {
              Text("账户余额")
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(Color.White)
                .opacity(0.7)
              Text(`￥${this.userInfo?.balance ?? "0.00"}`)
                .fontSize(28)
                .fontWeight(DesignFontWeight.BOLD)
                .fontColor(Color.White)
                .margin({ top: 4 })
            }
            .alignItems(HorizontalAlign.Start)

            // 右侧：校园卡状态 + 去充值
            Column() {
              Text(`校园卡：${this.userInfo?.campusCard == null ? "未绑定" : "已绑定"}`)
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(Color.White)
                .opacity(0.7)
                .onClick(() => {
                  pathStack.pushPathByName("BingCampusCardAlert", this.userInfo, popInfo => {
                    this.refreshUserInfo()
                  }, false)
                })

              Row() {
                Text("去充值")
                  .fontSize(DesignFontSize.CARD_TITLE)
                  .fontColor(Color.White)
                  .fontWeight(DesignFontWeight.MEDIUM)

                SymbolGlyph($r('sys.symbol.chevron_right'))
                  .fontSize(18)
                  .fontColor([Color.White])
              }
              .margin({ top: 4 })
              .onClick(() => {
                pathStack.pushPathByName("Recharge", "")
              })
            }
            .alignItems(HorizontalAlign.End)
          }
          .width("100%")
          .justifyContent(FlexAlign.SpaceBetween)
          .alignItems(VerticalAlign.Center)
          .padding(20)
        }
        .width("100%")
        .borderRadius(DesignDimens.CARD_RADIUS)
        .linearGradient({
          direction: GradientDirection.Right,
          colors: [[Color(DesignColor.PRIMARY), 0.0], [Color(DesignColor.PRIMARY_DARK), 1.0]]
        })

        // ========== 订单快捷入口 ==========
        Text("订单管理")
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(Color(DesignColor.ON_BACKGROUND))
          .margin({ top: DesignDimens.SECTION_SPACING })
          .width("100%")

        Row() {
          this.orderQuickEntry($r('sys.symbol.clock'), "待支付",
            this.orderItemCount?.pendingPaymentCount ?? 0, () => {
              this.jumpToOrderList(1)
            })

          Divider()
            .strokeWidth(0.5)
            .vertical(true)
            .height(72)
            .color(Color(DesignColor.DIVIDER))

          this.orderQuickEntry($r('sys.symbol.laundry'), "清洗中",
            this.orderItemCount?.processingCount ?? 0, () => {
              this.jumpToOrderList(3)
            })

          Divider()
            .strokeWidth(0.5)
            .vertical(true)
            .height(72)
            .color(Color(DesignColor.DIVIDER))

          this.orderQuickEntry($r('sys.symbol.envelope_open'), "待取件",
            this.orderItemCount?.pendingPickupCount ?? 0, () => {
              this.jumpToOrderList(4)
            })

          Divider()
            .strokeWidth(0.5)
            .vertical(true)
            .height(72)
            .color(Color(DesignColor.DIVIDER))

          this.orderQuickEntry($r('sys.symbol.arrow_counterclockwise_clock'), "历史订单",
            0, () => {
              this.jumpToOrderList(0)
            })
        }
        .width("100%")
        .margin({ top: 12 })
        .backgroundColor(Color(DesignColor.SURFACE))
        .borderRadius(DesignDimens.CARD_RADIUS)
        .border({ width: 0.5, color: Color(DesignColor.OUTLINE) })

        // ========== 功能列表 ==========
        Column() {
          this.functionItem($r('sys.symbol.label'), "优惠券", "可领取优惠券", () => {
            pathStack.pushPathByName("Coupon", null)
          })

          this.functionItem($r('sys.symbol.service'), "联系客服", "在线咨询", () => {
            // 联系客服逻辑
          })

          this.functionItem($r('sys.symbol.questionmark_circle'), "常见问题", "使用指南", () => {
            // 常见问题逻辑
          })
        }
        .width("100%")
        .margin({ top: DesignDimens.SECTION_SPACING })
        .space({ space: DesignDimens.CARD_SPACING, direction: FlexDirection.Column })

        // 底部安全距离
        Row().height(72)
      }
    }
    .scrollable(ScrollDirection.Vertical)
    .scrollBar(BarState.Off)
    .width("100%")
    .height("100%")
    .align(Alignment.TopStart)
    .backgroundColor(Color(DesignColor.BACKGROUND))
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
  }
}

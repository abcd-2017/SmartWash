import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontFeature, DesignFontWeight, DesignElevation } from "../constant/DesignSystem"
import { getUserInfo, uploadAvatar } from "../network/api/UserApi"
import { photoAccessHelper } from '@kit.MediaLibraryKit'
import { promptAction } from '@kit.ArkUI'
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { getUnreadCount } from "../network/api/NoticeApi"
import { NoticeText, formatUnreadBadge } from "../utils/NoticeLogic"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { logger } from "../utils/Logger"
import { AppButton, AppButtonVariant, EmptyState, LoadingState } from "./common/AppComponents"
import { safeTopVp } from "../utils/SafeArea"
import { NOTICE_TICK_KEY, USER_INFO_TICK_KEY } from "../utils/RefreshSignal"

/**
 * 手机号掩码（3+****+4，对齐安卓 UserInfoPage.kt:623-624 maskPhone）：
 * 隐私要求不在页面明文展示完整手机号。
 */
function maskPhone(phone: string | null): string {
  const value = phone ?? ""
  if (value.length === 11) {
    return `${value.substring(0, 3)}****${value.substring(value.length - 4)}`
  }
  return value
}

@Component
export struct UserInfo {
  private scroller: Scroller = new Scroller()
  @State userInfo: UserInfoVo | null = null
  // 首载失败标记：网络异常/401 拒绝时 promise 会 throw，兜住后进错误态，避免「我的」tab 永久骨架
  @State loadFailed: boolean = false
  // 自增 tick（问题3）：布尔标记重复 setOrCreate(true) 不产生变更事件，@Watch 不触发
  @StorageLink(USER_INFO_TICK_KEY) @Watch("onUserInfoChange") userInfoTick: number = 0
  @State avatarUrl: string | null = null
  @State isUploading: boolean = false
  // 下拉刷新进行中标记（对齐安卓 PullToRefreshBox；与首载骨架/错误重试互补）
  @State refreshing: boolean = false
  // 消息中心未读数（独立链路：失败仅留痕，角标隐藏，不影响用户信息主流程）
  @State unreadCount: number = 0
  // 跨页刷新信号（自增 tick）：消息中心标记已读 / 全部已读成功后 bump
  @StorageLink(NOTICE_TICK_KEY) @Watch("onNoticeTick") noticeTick: number = 0

  //监听用户信息刷新状态变化
  onUserInfoChange() {
    if (this.userInfoTick > 0) {
      this.refreshUserInfo()
    }
  }

  /** 消费站内信变更 tick：重拉未读数（自增保证每次变更都触发） */
  onNoticeTick() {
    if (this.noticeTick > 0) {
      this.loadUnreadCount()
    }
  }

  aboutToAppear() {
    this.refreshUserInfo()
    this.loadUnreadCount()
  }

  /**
   * 未读数独立拉取（对齐安卓 UserInfoViewModel.loadUnreadCount）：
   * 失败仅留痕（角标隐藏），不影响我的页主流程。
   */
  async loadUnreadCount() {
    try {
      const res = await getUnreadCount()
      if (res.code === HttpStatusCode.Ok) {
        this.unreadCount = res.data ?? 0
      } else {
        logger.warn('userInfo', `未读数拉取失败 code=${res.code ?? 0}`)
      }
    } catch (err) {
      logger.warn('userInfo', '未读数拉取异常（角标隐藏）')
    }
  }

  /** 行尾未读角标文案（0/缺失/负数返回空串，UI 隐藏角标；>99 封顶 99+） */
  private unreadBadgeText(): string {
    return formatUnreadBadge(this.unreadCount) ?? ''
  }

  async refreshUserInfo() {
    try {
      let res = await getUserInfo()
      if (res.code === HttpStatusCode.Ok) {
        this.userInfo = res.data
        this.avatarUrl = this.userInfo?.avatar ?? null
        logger.debug('userInfo', '用户信息加载成功')
        this.loadFailed = false
      } else {
        logger.warn('userInfo', `用户信息加载失败 code=${res.code} message=${res.message ?? ""}`)
        this.loadFailed = true
        showToastSafety(res.message, "用户信息加载失败")
      }
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：置失败态由 UI 出重试，不再让骨架常驻
      this.loadFailed = true
    }
  }

  /** 下拉刷新（对齐安卓 PullToRefreshBox）：复用 refreshUserInfo + 未读数，结束后复位 refreshing */
  private async onRefresh(): Promise<void> {
    this.refreshing = true
    await this.refreshUserInfo()
    await this.loadUnreadCount()
    this.refreshing = false
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
        if (res.code === HttpStatusCode.Ok) {
          this.avatarUrl = res.data
          // 上传成功后全量重拉用户信息：余额等其余字段由服务端在该响应后变更，
          // 直接用响应 data 更新头像会导致页面其余字段陈旧（对齐安卓 UserInfoViewModel.kt:117）
          await this.refreshUserInfo()
          // 低频关键动作成功：头像上传成功
          logger.info('userInfo', '头像上传成功')
          showToastSafety("头像上传成功")
        } else {
          // 上传被服务端拒绝
          logger.warn('userInfo', `头像上传被拒绝 code=${res.code} message=${res.message ?? ""}`)
          showToastSafety(res.message, "头像上传失败")
        }
        this.isUploading = false
      }
    } catch (err) {
      this.isUploading = false
      // 外部调用失败（选图/上传异常）：error 留痕，只记 message 摘要，不序列化完整 error
      logger.error('userInfo', '头像上传流程异常', err instanceof Error ? err.message : "")
      showToastSafety("头像上传失败")
    }
  }

  //跳转到订单列表页
  jumpToOrderList(index: number) {
    pathStack.pushPath({
      name: RouteNames.ORDER_LIST, param: index
    })
  }

  /** 余额两位小数（对齐安卓 String.format("%.2f", balance)） */
  private formatBalance(): string {
    const value = this.userInfo?.balance ?? 0
    return value.toFixed(2)
  }

  /** 头像占位首字（对齐安卓 phoneNumber.firstOrNull() ?: "U"） */
  private avatarInitial(): string {
    const phone = this.userInfo?.phoneNumber ?? ""
    if (phone.length > 0) {
      return phone.substring(0, 1)
    }
    return "U"
  }

  /** 校园卡绑定态：campusCard 空串=未绑（与绑卡页同语义，差异分析 02 §9.3） */
  private isCampusCardBound(): boolean {
    return (this.userInfo?.campusCard ?? "") !== ""
  }

  /**
   * 头像三态（对齐安卓 UserInfoPage.kt:264-329）：
   * 上传中 = primaryLight 底 + spinner；占位 = hero 渐变底 + 手机号首字；远程 = 网络图。
   */
  @Builder
  avatarContent() {
    if (this.isUploading) {
      Column() {
        LoadingProgress()
          .width(24)
          .height(24)
          .color(DesignColor.PRIMARY)
      }
      .width(56)
      .height(56)
      .borderRadius(28)
      .backgroundColor(DesignColor.PRIMARY_LIGHT)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
    } else if (this.avatarUrl !== null && this.avatarUrl !== "") {
      Image(this.avatarUrl)
        .width(56)
        .height(56)
        .borderRadius(28)
        .objectFit(ImageFit.Cover)
    } else {
      Column() {
        Text(this.avatarInitial())
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.ON_PRIMARY)
      }
      .width(56)
      .height(56)
      .borderRadius(28)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .linearGradient({
        angle: 135,
        colors: [[DesignColor.PRIMARY, 0], [DesignColor.PRIMARY_DARK, 1]]
      })
    }
  }

  /**
   * GroupCard 行卡（对齐安卓 UserInfoPage.kt:573-621 SettingRow）：
   * 64 行 + ib32/20 图标盒 + 14sp 主文案 + 行尾值（tnum）+ chevron 20。
   * 行间 0.5dp 发丝线由调用方在卡内补充。
   */
  @Builder
  settingRow(icon: Resource, title: string, containerColor: number, iconColor: number,
             trailingText: string, trailingColor: number, onClick: () => void) {
    Row() {
      Column() {
        SymbolGlyph(icon)
          .fontSize(20)
          .fontColor([iconColor])
      }
      .width(32)
      .height(32)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      .backgroundColor(containerColor)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)

      Text(title)
        .fontSize(DesignFontSize.BODY_SMALL)
        .fontWeight(DesignFontWeight.NORMAL)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .layoutWeight(1)
        .margin({ left: 12 })

      // 仅行尾值存在时与 chevron 保持 4dp 间隔（对齐安卓 SettingRow：无 trailing 的行不加间隔）
      if (trailingText !== "") {
        Text(trailingText)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(trailingColor)
          .fontFeature(DesignFontFeature.TNUM)
          .margin({ right: DesignDimens.SPACE_XXS })
      }

      SymbolGlyph($r('sys.symbol.chevron_right'))
        .fontSize(20)
        .fontColor([DesignColor.TEXT_TERTIARY])
    }
    .width("100%")
    .height(64)
    .alignItems(VerticalAlign.Center)
    .onClick(onClick)
  }

  build() {
    // 下拉刷新（对齐安卓 PullToRefreshBox）：包裹滚动区，下拉触发 onRefresh 重拉用户信息
    Refresh({ refreshing: $$this.refreshing }) {
    Scroll(this.scroller) {
      Column() {
        if (this.userInfo === null) {
          if (this.loadFailed) {
            // 首载失败（网络异常/401）：EmptyState + 重试，替代无限骨架（对齐规范 §3.6）
            Column() {
              EmptyState({ message: "用户信息加载失败" }) {
                AppButton({
                  text: "重试",
                  variant: AppButtonVariant.SECONDARY,
                  click: () => {
                    this.refreshUserInfo()
                  }
                })
                .margin({ top: DesignDimens.SPACE_MD })
              }
            }
            .width("100%")
            .layoutWeight(1)
            .justifyContent(FlexAlign.Center)
          } else {
            // 首载骨架（对齐安卓 UserInfoPage.kt:180-195 LoadingState；回 tab/绑定/换头像后的刷新保留现有内容）
            LoadingState()
          }
        } else {
          // ========== 标题行（对齐安卓 UserInfoPage.kt:202-240） ==========
          Row() {
            Text("我的")
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_PRIMARY)

            // 设置入口：48dp 圆形青底容器 + 齿轮 20（对齐安卓 UserInfoPage.kt:220-239，PR #47 由 surfaceVariant 改青）
            Column() {
              SymbolGlyph($r('sys.symbol.gearshape'))
                .fontSize(20)
                .fontColor([DesignColor.ICON_FOREGROUND_TEAL])
            }
            .width(48)
            .height(48)
            .borderRadius(DesignDimens.RADIUS_FULL)
            .backgroundColor(DesignColor.ICON_CONTAINER_TEAL)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)
            // 无障碍：设置齿轮补语义（原仅图形，读屏无标签；accessibilityRole 需 SDK 18+，目标 API 17 不用）
            .accessibilityDescription('设置')
            .onClick(() => {
              pathStack.pushPathByName(RouteNames.SETTING, null)
            })
          }
          .justifyContent(FlexAlign.SpaceBetween)
          .alignItems(VerticalAlign.Center)
          .width("100%")
          .padding({ top: 16, bottom: 8 })

          // ========== 横排头部（对齐安卓 UserInfoPage.kt:242-357） ==========
          Row() {
            Stack({ alignContent: Alignment.Center }) {
              this.avatarContent()
            }
            .width(56)
            .height(56)
            .onClick(() => {
              this.selectAndUploadAvatar()
            })

            Column() {
              Text(maskPhone(this.userInfo?.phoneNumber ?? null))
                .fontSize(DesignFontSize.CARD_TITLE)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor(DesignColor.TEXT_PRIMARY)

              // 学校名（对齐安卓 UserInfoPage.kt:343-348；无学校时显示空串）
              Text(this.userInfo?.schoolVo?.schoolName ?? "")
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(DesignColor.TEXT_SECONDARY)
                .margin({ top: 4 })
            }
            .layoutWeight(1)
            .alignItems(HorizontalAlign.Start)
            .margin({ left: 16 })

            SymbolGlyph($r('sys.symbol.chevron_right'))
              .fontSize(20)
              .fontColor([DesignColor.TEXT_TERTIARY])
          }
          .width("100%")
          .alignItems(VerticalAlign.Center)
          .padding({ top: 8, bottom: 16 })

          // ========== 数据卡（白底 GroupCard，对齐安卓 UserInfoPage.kt:359-436） ==========
          Column() {
            // 余额通栏在上（点击去充值）
            Column() {
              // 显式行盒对齐安卓 Type.kt（D4）：bodySmall 12/16
              Text("钱包余额")
                .fontSize(DesignFontSize.CAPTION)
                .lineHeight(DesignFontSize.CAPTION + 4)
                .fontColor(DesignColor.TEXT_SECONDARY)
              // 显式行盒对齐安卓 DataLarge 36/44；字重保持 BOLD（font.display 档，规范允许 700）
              Text(`¥${this.formatBalance()}`)
                .fontSize(DesignFontSize.DATA_LARGE)
                .lineHeight(DesignFontSize.DATA_LARGE + 8)
                .fontWeight(DesignFontWeight.BOLD)
                .fontFeature(DesignFontFeature.TNUM)
                .fontColor(DesignColor.PRIMARY_DARK)
                .margin({ top: 4 })
            }
            .alignItems(HorizontalAlign.Start)
            .width("100%")
            .onClick(() => {
              pathStack.pushPathByName(RouteNames.RECHARGE, "")
            })

            // 下方券/单两半格各居中，中间 1×40 发丝线
            Row() {
              Column() {
                // 行盒对齐安卓 Type.kt（D4）：AmountMedium 18/24、bodySmall 12/16
                Text(`${this.userInfo?.couponCount ?? 0}`)
                  .fontSize(DesignFontSize.AMOUNT_MEDIUM)
                  .lineHeight(DesignFontSize.AMOUNT_MEDIUM + 6)
                  .fontWeight(DesignFontWeight.SEMI_BOLD)
                  .fontFeature(DesignFontFeature.TNUM)
                  .fontColor(DesignColor.TEXT_PRIMARY)
                Text("优惠券")
                  .fontSize(DesignFontSize.CAPTION)
                  .lineHeight(DesignFontSize.CAPTION + 4)
                  .fontColor(DesignColor.TEXT_SECONDARY)
                  .margin({ top: 4 })
              }
              .layoutWeight(1)
              .height(48)
              .alignItems(HorizontalAlign.Center)
              .justifyContent(FlexAlign.Center)
              .onClick(() => {
                pathStack.pushPathByName(RouteNames.COUPON, null)
              })

              Column()
                .width(1)
                .height(40)
                .backgroundColor(DesignColor.HAIRLINE)

              Column() {
                // 行盒对齐安卓 Type.kt（D4）：AmountMedium 18/24、bodySmall 12/16
                Text(`${this.userInfo?.orderCount ?? 0}`)
                  .fontSize(DesignFontSize.AMOUNT_MEDIUM)
                  .lineHeight(DesignFontSize.AMOUNT_MEDIUM + 6)
                  .fontWeight(DesignFontWeight.SEMI_BOLD)
                  .fontFeature(DesignFontFeature.TNUM)
                  .fontColor(DesignColor.TEXT_PRIMARY)
                Text("累计订单")
                  .fontSize(DesignFontSize.CAPTION)
                  .lineHeight(DesignFontSize.CAPTION + 4)
                  .fontColor(DesignColor.TEXT_SECONDARY)
                  .margin({ top: 4 })
              }
              .layoutWeight(1)
              .height(48)
              .alignItems(HorizontalAlign.Center)
              .justifyContent(FlexAlign.Center)
            }
            .width("100%")
            .margin({ top: 8 })
          }
          .width("100%")
          .padding(DesignDimens.CARD_PADDING)
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.CARD_RADIUS)
          .border({ width: 1, color: DesignColor.OUTLINE })
          .shadow({
            radius: DesignElevation.CARD_SHADOW_RADIUS,
            color: DesignColor.SHADOW_COLOR_CARD,
            offsetX: 0,
            offsetY: DesignElevation.CARD_SHADOW_OFFSET
          })

          // ========== 账户卡 GroupCard（对齐安卓 UserInfoPage.kt:441-487） ==========
          Column() {
            // 校园卡行尾状态不能走 @Builder 按值参数（已知坑：参数首帧定格，绑定/解绑后不刷新）；
            // 用 if 分支包住使 userInfo 变更时分支重跑、以新字面量重新调用 settingRow
            if (this.isCampusCardBound()) {
              this.settingRow($r('sys.symbol.creditcard'), "校园卡",
                DesignColor.ICON_CONTAINER_GREEN, DesignColor.ICON_FOREGROUND_GREEN,
                // U-103：「已绑定」绿字 on 白仅 3.50:1（绿字红线禁小号文字）→ 统一 textSecondary
                "已绑定", DesignColor.TEXT_SECONDARY,
                () => {
                  pathStack.pushPathByName(RouteNames.BING_CAMPUS_CARD_ALERT, this.userInfo, popInfo => {
                    this.refreshUserInfo()
                  }, false)
                })
            } else {
              this.settingRow($r('sys.symbol.creditcard'), "校园卡",
                DesignColor.ICON_CONTAINER_GREEN, DesignColor.ICON_FOREGROUND_GREEN,
                "未绑定", DesignColor.TEXT_SECONDARY,
                () => {
                  pathStack.pushPathByName(RouteNames.BING_CAMPUS_CARD_ALERT, this.userInfo, popInfo => {
                    this.refreshUserInfo()
                  }, false)
                })
            }

            Divider()
              .strokeWidth(0.5)
              .color(DesignColor.HAIRLINE)

            this.settingRow($r('sys.symbol.pin_fill'), "收货地址",
              DesignColor.ICON_CONTAINER_ORANGE, DesignColor.ICON_FOREGROUND_ORANGE,
              "", DesignColor.TEXT_SECONDARY,
              () => {
                // 文案对齐安卓 shipping_address_unavailable
                promptAction.showToast({ message: "收货地址功能暂未开放" })
              })

            Divider()
              .strokeWidth(0.5)
              .color(DesignColor.HAIRLINE)

            // 钱包流水入口（充值/消费合并明细，对齐安卓 WalletTransactionsPage 语义）
            this.settingRow($r('sys.symbol.doc_text'), "钱包流水",
              DesignColor.ICON_CONTAINER_TEAL, DesignColor.ICON_FOREGROUND_TEAL,
              "", DesignColor.TEXT_SECONDARY,
              () => {
                pathStack.pushPathByName(RouteNames.WALLET_TRANSACTIONS, null)
              })
          }
          .width("100%")
          .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.CARD_RADIUS)
          .border({ width: 1, color: DesignColor.OUTLINE })
          .shadow({
            radius: DesignElevation.CARD_SHADOW_RADIUS,
            color: DesignColor.SHADOW_COLOR_CARD,
            offsetX: 0,
            offsetY: DesignElevation.CARD_SHADOW_OFFSET
          })
          .margin({ top: DesignDimens.CARD_SPACING })

          // ========== 功能卡 GroupCard（对齐安卓 UserInfoPage.kt:492-535） ==========
          Column() {
            this.settingRow($r('sys.symbol.list_square_bill'), "我的订单",
              DesignColor.ICON_CONTAINER_BLUE, DesignColor.ICON_FOREGROUND_BLUE,
              `${this.userInfo?.orderCount ?? 0} 单`, DesignColor.TEXT_SECONDARY,
              () => {
                this.jumpToOrderList(0)
              })

            Divider()
              .strokeWidth(0.5)
              .color(DesignColor.HAIRLINE)

            // 消息中心入口（扩展提案 #4；对齐安卓 UserInfoPage 功能卡「消息中心」行）：
            // 橙容器 = 通知/提醒语义（规范 §3.2 六色浅底）。
            // 行尾未读角标依赖 @State，不能走 @Builder 按值参数（已知坑：参数首帧定格），
            // 故本行不走 settingRow，直接内联渲染以随 unreadCount 变更重绘。
            Row() {
              Column() {
                SymbolGlyph($r('sys.symbol.bell'))
                  .fontSize(20)
                  .fontColor([DesignColor.ICON_FOREGROUND_ORANGE])
              }
              .width(32)
              .height(32)
              .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
              .backgroundColor(DesignColor.ICON_CONTAINER_ORANGE)
              .justifyContent(FlexAlign.Center)
              .alignItems(HorizontalAlign.Center)

              Text(NoticeText.notice_title)
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontWeight(DesignFontWeight.NORMAL)
                .fontColor(DesignColor.TEXT_PRIMARY)
                .layoutWeight(1)
                .margin({ left: 12 })

              // 未读数字角标（0/缺失隐藏；>99 封顶 99+）
              if (this.unreadBadgeText().length > 0) {
                Text(this.unreadBadgeText())
                  .fontSize(DesignFontSize.CAPTION)
                  .fontWeight(DesignFontWeight.MEDIUM)
                  .fontColor(DesignColor.ICON_FOREGROUND_PINK)
                  .fontFeature(DesignFontFeature.TNUM)
                  .padding({ left: 5, right: 5, top: 1, bottom: 1 })
                  .backgroundColor(DesignColor.ICON_CONTAINER_PINK)
                  .borderRadius(DesignDimens.RADIUS_FULL)
                  .margin({ right: DesignDimens.SPACE_XXS })
              }

              SymbolGlyph($r('sys.symbol.chevron_right'))
                .fontSize(20)
                .fontColor([DesignColor.TEXT_TERTIARY])
            }
            .width("100%")
            .height(64)
            .alignItems(VerticalAlign.Center)
            .onClick(() => {
              pathStack.pushPathByName(RouteNames.MESSAGES, null)
            })

            Divider()
              .strokeWidth(0.5)
              .color(DesignColor.HAIRLINE)

            this.settingRow($r('sys.symbol.gearshape'), "设置",
              DesignColor.ICON_CONTAINER_GREEN, DesignColor.ICON_FOREGROUND_GREEN,
              "", DesignColor.TEXT_SECONDARY,
              () => {
                pathStack.pushPathByName(RouteNames.SETTING, null)
              })
          }
          .width("100%")
          .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.CARD_RADIUS)
          .border({ width: 1, color: DesignColor.OUTLINE })
          .shadow({
            radius: DesignElevation.CARD_SHADOW_RADIUS,
            color: DesignColor.SHADOW_COLOR_CARD,
            offsetX: 0,
            offsetY: DesignElevation.CARD_SHADOW_OFFSET
          })
          .margin({ top: DesignDimens.CARD_SPACING })
        }

        // 底部留白（对齐安卓 Spacer(24)）
        Row().height(DesignDimens.SPACE_XL)
      }
    }
    .scrollable(ScrollDirection.Vertical)
    .scrollBar(BarState.Off)
    .width("100%")
    .height("100%")
    .align(Alignment.TopStart)
    // 沉浸式：内容避让状态栏（背景延伸）
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: safeTopVp() })
    }
    .onRefreshing(() => {
      this.onRefresh()
    })
    .width("100%")
    .height("100%")
    .backgroundColor(DesignColor.BACKGROUND)
  }
}

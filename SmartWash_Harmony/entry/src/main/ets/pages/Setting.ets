import { CommonConstants } from "../constant/CommonConstants"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { promptAction } from "@kit.ArkUI"
import { setValue } from "../utils/StorageUtil"
import { pathStack } from "../utils/PathStackUtil"

// ========== 退出登录确认弹窗 ==========
@CustomDialog
struct LogoutConfirmDialog {
  controller: CustomDialogController
  onConfirm: () => void = () => {}

  build() {
    Column() {
      // 弹窗内容区
      Column() {
        Text("提示")
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.TEXT_PRIMARY)

        Text("是否要退出登录？")
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: 12 })
      }
      .padding({ left: 24, right: 24, top: 20, bottom: 20 })
      .alignItems(HorizontalAlign.Start)

      // 分隔线
      Divider()
        .strokeWidth(0.5)
        .color(DesignColor.DIVIDER)

      // 按钮行
      Row() {
        Button("取消")
          .layoutWeight(1)
          .height(48)
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .backgroundColor(Color.Transparent)
          .borderRadius(0)
          .onClick(() => {
            this.controller.close()
          })

        Divider()
          .vertical(true)
          .strokeWidth(0.5)
          .color(DesignColor.DIVIDER)
          .height(48)

        Button("确认")
          .layoutWeight(1)
          .height(48)
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.ERROR)
          .backgroundColor(Color.Transparent)
          .borderRadius(0)
          .onClick(() => {
            this.controller.close()
            this.onConfirm()
          })
      }
      .width("100%")
    }
    .width("85%")
    .borderRadius(DesignDimens.CARD_RADIUS)
    .backgroundColor(Color.White)
  }
}

// ========== 关于我们弹窗 ==========
@CustomDialog
struct AboutDialog {
  controller: CustomDialogController

  build() {
    Column() {
      // 弹窗内容区
      Column() {
        Text("关于我们")
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.TEXT_PRIMARY)

        Text("清氧洗衣 v1.0.0")
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: 12 })
      }
      .padding({ left: 24, right: 24, top: 20, bottom: 20 })
      .alignItems(HorizontalAlign.Start)

      // 分隔线
      Divider()
        .strokeWidth(0.5)
        .color(DesignColor.DIVIDER)

      // 确认按钮
      Button("确认")
        .width("100%")
        .height(48)
        .fontSize(DesignFontSize.BODY)
        .fontColor(DesignColor.PRIMARY)
        .backgroundColor(Color.Transparent)
        .borderRadius(0)
        .onClick(() => {
          this.controller.close()
        })
    }
    .width("85%")
    .borderRadius(DesignDimens.CARD_RADIUS)
    .backgroundColor(Color.White)
  }
}

// ========== 设置页面 ==========
@Builder
export function SettingBuilder() {
  Setting()
}

@Component
struct Setting {
  private scroller = new Scroller()

  // 退出登录弹窗控制器
  private logoutDialogController: CustomDialogController = new CustomDialogController({
    builder: LogoutConfirmDialog({
      onConfirm: () => {
        // 与网络层 401 处理同语义（评审 #23，参照 Axios.ets clearLoginState）：
        // 清本地 token + 复位内存登录态，只清 token 不复位 AppStorage 标志，
        // 重启后 Index 会按残留登录态直接进首页并循环 401
        setValue(CommonConstants.USER_TOKEN, "")
        AppStorage.setOrCreate("userLoginFlag", false)
        pathStack.clear()
        pathStack.pushPathByName("Login", null)
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  // 关于我们弹窗控制器
  private aboutDialogController: CustomDialogController = new CustomDialogController({
    builder: AboutDialog(),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  // 自定义设置行
  @Builder
  SettingRowBuilder(icon: Resource, title: string, description: string, onClick: () => void) {
    Row() {
      // 图标容器 — 36vp，12vp 圆角，浅绿底+绿色图标
      Column() {
        SymbolGlyph(icon)
          .fontSize(18)
          .fontColor([DesignColor.PRIMARY])
      }
      .width(36)
      .height(36)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      .backgroundColor(DesignColor.PRIMARY_LIGHT)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)

      // 标题 + 副标题
      Column() {
        Text(title)
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.TEXT_PRIMARY)

        Text(description)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: 2 })
      }
      .layoutWeight(1)
      .margin({ left: 12 })
      .alignItems(HorizontalAlign.Start)

      // 右箭头
      SymbolGlyph($r('sys.symbol.chevron_right'))
        .fontSize(18)
        .fontColor([DesignColor.TEXT_TERTIARY])
    }
    .width("100%")
    .height(56)
    .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
    .onClick(() => {
      onClick()
    })
  }

  // 分组卡片
  @Builder
  SectionCard(sectionTitle: string, children: () => void) {
    Column() {
      // 分组标题
      Text(sectionTitle)
        .fontSize(DesignFontSize.SECTION_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .margin({ bottom: 12 })

      // 卡片
      Column() {
        children()
      }
      .width("100%")
      .borderRadius(DesignDimens.CARD_RADIUS)
      .backgroundColor(Color.White)
      .border({ width: 0.5, color: DesignColor.OUTLINE })
    }
    .width("100%")
  }

  // 分隔线
  @Builder
  CardDivider() {
    Divider()
      .strokeWidth(0.5)
      .color(DesignColor.DIVIDER)
      .margin({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
  }

  build() {
    NavDestination() {
      Column() {
        // 自定义页面头部 — 返回按钮 + 标题"设置"
        Row() {
          SymbolGlyph($r('sys.symbol.chevron_left'))
            .fontSize(24)
            .fontColor([DesignColor.TEXT_PRIMARY])
            .onClick(() => {
              pathStack.pop()
            })

          Text("设置")
            .fontSize(DesignFontSize.PAGE_TITLE)
            .fontWeight(DesignFontWeight.BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .margin({ left: 12 })
        }
        .width("100%")
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 12, bottom: 12 })
        .alignItems(VerticalAlign.Center)

        // 滚动内容
        Scroll(this.scroller) {
          Column() {
            // 通知设置
            this.SectionCard("通知设置") {
              this.SettingRowBuilder(
                $r('sys.symbol.bell'),
                "消息通知",
                "订单状态、活动提醒等",
                () => {
                  promptAction.showToast({ message: "功能开发中" })
                }
              )
              this.CardDivider()
              this.SettingRowBuilder(
                $r('sys.symbol.envelope'),
                "邮件通知",
                "账单、优惠信息等",
                () => {
                  promptAction.showToast({ message: "功能开发中" })
                }
              )
            }

            // 隐私设置
            this.SectionCard("隐私设置") {
              this.SettingRowBuilder(
                $r('sys.symbol.key_shield'),
                "隐私设置",
                "个人信息、数据收集等",
                () => {
                  promptAction.showToast({ message: "功能开发中" })
                }
              )
              this.CardDivider()
              this.SettingRowBuilder(
                $r('sys.symbol.lock_fill'),
                "账号安全",
                "密码、登录设备等",
                () => {
                  promptAction.showToast({ message: "功能开发中" })
                }
              )
            }
            .margin({ top: DesignDimens.SECTION_SPACING })

            // 其他
            this.SectionCard("其他") {
              this.SettingRowBuilder(
                $r('sys.symbol.info_circle'),
                "关于我们",
                "版本信息、用户协议等",
                () => {
                  this.aboutDialogController.open()
                }
              )
              this.CardDivider()
              this.SettingRowBuilder(
                $r('sys.symbol.questionmark_circle'),
                "帮助与反馈",
                "常见问题、意见反馈",
                () => {
                  promptAction.showToast({ message: "功能开发中" })
                }
              )
            }
            .margin({ top: DesignDimens.SECTION_SPACING })

            // 退出登录按钮 — 白底 + 红色边框 + 红色文字
            Button("退出登录")
              .width("100%")
              .height(52)
              .fontSize(DesignFontSize.CARD_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.ERROR)
              .backgroundColor(Color.White)
              .borderRadius(DesignDimens.BUTTON_RADIUS)
              .border({ width: 1, color: DesignColor.ERROR })
              .margin({ top: 36, bottom: 24 })
              .onClick(() => {
                this.logoutDialogController.open()
              })
          }
          .width("100%")
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        }
        .layoutWeight(1)
        .scrollBar(BarState.Off)
      }
      .width("100%")
      .height("100%")
    }
    .backgroundColor(DesignColor.BACKGROUND)
    .height("100%")
    .width("100%")
  }
}

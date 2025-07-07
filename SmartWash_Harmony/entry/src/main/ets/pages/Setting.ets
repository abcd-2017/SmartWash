import { CommonConstants } from "../constant/CommonConstants"
import { ItemTitle } from "../view/ItemTitle"
import { SettingRow } from "../view/SettingRow"
import { PromptAction } from "@ohos.arkui.UIContext"
import { promptAction } from "@kit.ArkUI"
import { setValue } from "../utils/StoreageUtil"
import { pathStack } from "../utils/PathStackUtil"

@Builder
export function SettingBuilder() {
  Setting()
}

@Component
struct Setting {
  private scroller = new Scroller()

  build() {
    NavDestination() {
      Scroll(this.scroller) {
        Column() {
          ItemTitle({ title: "通知设置" })
            .margin({ top: 8, bottom: 8 })
          Column() {
            SettingRow({
              icon: $r('sys.symbol.bell'),
              title: "消息通知",
              description: "订单状态、活动提醒等"
            })
            Divider().strokeWidth(1).width("70%")
            SettingRow({
              icon: $r('sys.symbol.envelope'),
              title: "邮件通知",
              description: "账单、优惠信息等"
            })
          }.width("100%")
          .borderRadius(CommonConstants.BORDER_RADIUS)
          .shadow(ShadowStyle.OUTER_DEFAULT_SM)
          .backgroundColor($r('sys.color.comp_background_primary'))

          RowSplit().height("14")
          ItemTitle({ title: "隐私设置" })
            .margin({ top: 8, bottom: 8 })
          Column() {
            SettingRow({
              icon: $r('sys.symbol.key_shield'),
              title: "隐私设置",
              description: "个人信息、数据收集等"
            })
            Divider().strokeWidth(1).width("70%")
            SettingRow({
              icon: $r('sys.symbol.lock_fill'),
              title: "账号安全",
              description: "密码、登录设备等"
            })
          }.width("100%")
          .borderRadius(CommonConstants.BORDER_RADIUS)
          .shadow(ShadowStyle.OUTER_DEFAULT_SM)
          .backgroundColor($r('sys.color.comp_background_primary'))

          RowSplit().height("14")
          ItemTitle({ title: "其他" })
            .margin({ top: 8, bottom: 8 })
          Column() {
            SettingRow({
              icon: $r('sys.symbol.info_circle'),
              title: "关于我们",
              description: "版本信息、用户协议等"
            })
            Divider().strokeWidth(1).width("70%")
            SettingRow({
              icon: $r('sys.symbol.questionmark_circle'),
              title: "帮助与反馈",
              description: "常见问题、意见反馈"
            })
          }.width("100%")
          .borderRadius(CommonConstants.BORDER_RADIUS)
          .shadow(ShadowStyle.OUTER_DEFAULT_SM)
          .backgroundColor($r('sys.color.comp_background_primary'))

          RowSplit().height("48")

          Button("退出登录").onClick((event: ClickEvent) => {
            promptAction.showDialog({
              title: "提示",
              message: "是否要退出登录？",
              buttons: [{ text: "取消", color: $r('sys.color.font_primary') },
                { text: "确认", color: $r('sys.color.font_primary') }],
            }, async (err, data) => {
              if (data.index == 1) {
                setValue(CommonConstants.USER_TOKEN, "")
                pathStack.clear()
                pathStack.pushPathByName("Login", null)
              }
            })
          }).width("100%")
            .backgroundColor($r('sys.color.warning'))
        }
      }.padding({ left: 16, right: 16 })
    }
    .title("设置")
    .width("100%")
    .height("100%")
    .align(Alignment.Top)
    .backgroundColor($r('sys.color.background_primary'))
  }
}
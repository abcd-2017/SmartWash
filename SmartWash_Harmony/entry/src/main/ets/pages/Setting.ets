import { CommonConstants } from "../constant/CommonConstants"
import { RouteNames } from "../constant/RouteNames"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { promptAction } from "@kit.ArkUI"
import { setValue } from "../utils/StorageUtil"
import { pathStack } from "../utils/PathStackUtil"
import { AppConfirmDialog, AppInfoDialog } from "../view/common/AppComponents"
import { getAppVersionName } from "../utils/AppInfoUtil"

// 品牌名单一事实来源：与安卓 brand_name 一致（feature/user/impl strings.xml:17）
const APP_NAME: string = "智能洗衣"

// ========== 设置页面 ==========
@Builder
export function SettingBuilder() {
  Setting()
}

@Component
struct Setting {
  private scroller = new Scroller()

  // 退出登录弹窗（对齐安卓 AppConfirmDialog isDanger，统一使用 #23 产出的通用组件）
  private logoutDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: "提示",
      message: "是否要退出登录？",
      confirmText: "确认",
      cancelText: "取消",
      onConfirm: () => {
        // 登出链路（对齐安卓 SettingViewModel.logout）：清本地 token + 清 role + 复位内存登录态，
        // 再清栈跳登录页。userRole 属登录态脏数据，必须一并清理，
        // 否则下次登录前的 AppStorage 仍残留上一位用户的角色
        setValue(CommonConstants.USER_TOKEN, "")
        AppStorage.setOrCreate("userRole", "")
        AppStorage.setOrCreate("userLoginFlag", false)
        pathStack.clear()
        pathStack.pushPathByName(RouteNames.LOGIN, null)
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  // 关于我们弹窗（对齐安卓 AppInfoDialog，版本号动态读取）
  private aboutDialogController: CustomDialogController = new CustomDialogController({
    builder: AppInfoDialog({
      title: "关于我们",
      message: `${APP_NAME} v${getAppVersionName()}`
    }),
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

  // 分组卡片（原 @Builder 接收 children 并链式 .margin 的写法不满足 ArkTS UI 语法，改为子组件 SectionCard）

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
            .fontSize(DesignFontSize.ROOT_TITLE)
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
            // 通知设置（ArkTS 不支持尾随闭包传 @Builder 参数，改为显式箭头函数）
            SectionCard({
              sectionTitle: "通知设置",
              content: () => {
                this.SettingRowBuilder(
                  $r('sys.symbol.bell'),
                  "消息通知",
                  "订单状态、活动提醒等",
                  () => {
                    promptAction.showToast({ message: "功能开发中" })
                  }
                )
              }
            })

            // 其他
            SectionCard({
              sectionTitle: "其他",
              content: () => {
                this.SettingRowBuilder(
                  $r('sys.symbol.info_circle'),
                  "关于我们",
                  `v${getAppVersionName()}`,
                  () => {
                    this.aboutDialogController.open()
                  }
                )
              }
            })
            .margin({ top: DesignDimens.SECTION_SPACING })

            // 退出登录 — 白卡内 dangerInk 文字行、无红色边框（对齐安卓 SettingPage.kt:217-232）
            Column() {
              Text("退出登录")
                .width("100%")
                .height(52)
                .fontSize(DesignFontSize.BODY)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor(DesignColor.DANGER_INK)
                .textAlign(TextAlign.Center)
                .onClick(() => {
                  this.logoutDialogController.open()
                })
            }
            .width("100%")
            .backgroundColor(DesignColor.SURFACE)
            .borderRadius(DesignDimens.CARD_RADIUS)
            .border({ width: 0.5, color: DesignColor.OUTLINE })
            .margin({ top: 16, bottom: 24 })
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

/**
 * 通用分组卡片（组标题 + 内容插槽）。
 * 原 @Builder 方法接收 `children: () => void` 参数：@Builder 调用返回 void 无法链式设置外边距、
 * 且 children() 不满足 UI 组件语法，故改为子组件 + @BuilderParam（ArkTS 标准插槽模式）。
 */
@Component
struct SectionCard {
  @Prop sectionTitle: string = ""
  @BuilderParam content: () => void = emptyContent

  build() {
    Column() {
      // 分组标题
      Text(this.sectionTitle)
        .fontSize(DesignFontSize.SECTION_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .margin({ bottom: 12 })

      // 卡片
      Column() {
        this.content()
      }
      .width("100%")
      .borderRadius(DesignDimens.CARD_RADIUS)
      .backgroundColor(Color.White)
      .border({ width: 0.5, color: DesignColor.OUTLINE })
    }
    .width("100%")
  }
}

// @BuilderParam 的默认空插槽（ArkTS 要求默认值必须是 @Builder 函数，不能用内联箭头函数）
@Builder
function emptyContent() {
}

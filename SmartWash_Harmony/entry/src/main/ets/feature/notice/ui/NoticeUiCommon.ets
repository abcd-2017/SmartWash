// 站内信模块共享件 — 对齐安卓 feature/notice/ui/inbox/NoticeInboxPage.kt 的私有 composable。
//
// 内容：
// 1) 页面头（返回 + 标题 + 未读数 + 「全部已读」文字动作，按钮自带 loading 防重）；
// 2) 类型 tag 胶囊 chip；
// 3) 首载失败错误态（滚筒圆母题 + 重试）。
//
// 已知坑遵守：内容依赖状态的组件一律 @ComponentV2 + @Param/@Event；颜色一律取 Design* 令牌。

import { DesignColor, DesignDimens, DesignFontSize, DesignFontFeature, DesignFontWeight } from '../../../constant/DesignSystem'
import { NoticeText } from '../../../utils/NoticeLogic'
import { AppButton, AppButtonVariant, EmptyState } from '../../../view/common/AppComponents'
import { pathStack } from '../../../utils/PathStackUtil'

/**
 * 页面头（对齐安卓 NoticeInboxPage 的 PageHeader）：返回箭头 48 热区 + 标题 22/600 +
 * 右侧未读数（tnum）+「全部已读」文字按钮（loading 态禁点，后端幂等为最终兜底）。
 */
@ComponentV2
export struct NoticePageHeader {
  @Param title: string = ''
  @Param unreadText: string = ''
  @Param readAllLoading: boolean = false
  @Event onBack: () => void = () => {
    pathStack.pop()
  }
  @Event onReadAll: () => void = () => {
  }

  build() {
    Row() {
      Column() {
        SymbolGlyph($r('sys.symbol.chevron_left'))
          .fontSize(24)
          .fontColor([DesignColor.TEXT_PRIMARY])
      }
      .width(DesignDimens.BACK_HIT_AREA)
      .height(DesignDimens.BACK_HIT_AREA)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .accessibilityDescription('返回')
      .onClick(() => {
        this.onBack()
      })

      Text(this.title)
        .fontSize(DesignFontSize.ROOT_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)

      Blank()

      // 未读数是数量，开 tabular numbers（规范 §2.2 硬规则，避免数字变化左右跳）
      Text(this.unreadText)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.TEXT_SECONDARY)
        .fontFeature(DesignFontFeature.TNUM)

      AppButton({
        text: NoticeText.notice_read_all,
        variant: AppButtonVariant.TEXT,
        loading: this.readAllLoading,
        click: (): void => {
          this.onReadAll()
        }
      })
        .margin({ left: DesignDimens.SPACE_XS })
    }
    .width('100%')
    .height(56)
    // 行首让位 12：48dp 热区盒居中后图形左缘落在 24（对齐 Android PageHeader）
    .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING })
    .alignItems(VerticalAlign.Center)
  }
}

/** 类型 tag 胶囊（caption 12/500，配色由调用方按类型语义给定） */
@ComponentV2
export struct NoticeTagChip {
  @Param text: string = ''
  @Param containerColor: number = DesignColor.SURFACE_VARIANT
  @Param contentColor: number = DesignColor.TEXT_SECONDARY

  build() {
    Text(this.text)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(this.contentColor)
      .padding({ left: 8, right: 8, top: 2, bottom: 2 })
      .backgroundColor(this.containerColor)
      .borderRadius(DesignDimens.RADIUS_FULL)
  }
}

/**
 * 首载失败错误态（规范 §3.6）— EmptyState 滚筒圆母题 + 说明文案 + 重试按钮。
 * 用于收件箱首刷失败（网络异常/401）。
 */
@ComponentV2
export struct NoticeErrorState {
  @Param message: string = ''
  @Event onRetry: () => void = () => {
  }

  build() {
    Column() {
      EmptyState({ message: this.message }) {
        Column() {
          AppButton({
            small: true,
            text: NoticeText.notice_action_retry,
            variant: AppButtonVariant.SECONDARY,
            click: (): void => {
              this.onRetry()
            }
          })
        }
        .margin({ top: DesignDimens.SPACE_MD })
      }
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }
}

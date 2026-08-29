// 清氧设计系统 — 通用 UI 组件
// 参考 Android 端 AppComponents.kt

import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from '../../constant/DesignSystem'
import { pathStack } from '../../utils/PathStackUtil'
import vibrator from '@ohos.vibrator'

// @BuilderParam 的默认空插槽（ArkTS 要求默认值必须是 @Builder 函数，不能用内联箭头函数）
@Builder
function emptyContent() {
}

// ========== PageHeader 页面头部 ==========

@ComponentV2
export struct PageHeader {
  @Param title: string = ''
  @Param showBack: boolean = true
  @Event backClick: () => void = () => {
    pathStack.pop()
  }

  build() {
    Row() {
      // 左侧返回按钮
      if (this.showBack) {
        SymbolGlyph($r('sys.symbol.chevron_left'))
          .fontSize(24)
          .fontColor([DesignColor.ON_BACKGROUND])
          .onClick(() => {
            this.backClick()
          })
          .padding(8)
      }

      // 标题
      Text(this.title)
        .fontSize(DesignFontSize.PAGE_TITLE)
        .fontWeight(DesignFontWeight.BOLD)
        .fontColor(DesignColor.ON_BACKGROUND)
        .margin({ left: this.showBack ? 4 : 0 })

      Blank()

      // 右侧插槽（通过外部 Row 容器扩展）
    }
    .width('100%')
    .height(56)
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
    .alignItems(VerticalAlign.Center)
  }
}

// ========== AppCard 统一卡片容器 ==========

/**
 * 统一卡片容器
 * 白底、20vp 圆角、0.5vp 边框（替代 shadow）
 *
 * 使用方式：
 * AppCard() {
 *   Text("卡片内容")
 * }
 */
@ComponentV2
export struct AppCard {
  // 命名注意：不能叫 borderRadius（与 CustomComponent 基类的同名属性方法冲突导致编译错误）
  @Param cardRadius: number = DesignDimens.CARD_RADIUS
  @Param paddingValue: number = DesignDimens.CARD_PADDING
  @BuilderParam content: () => void = emptyContent

  build() {
    Column() {
      this.content()
    }
    .width('100%')
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(this.cardRadius)
    .borderWidth(0.5)
    .borderColor(DesignColor.OUTLINE)
    .padding(this.paddingValue)
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: '#1A000000',
      offsetX: 0,
      offsetY: 1
    })
  }
}

// ========== AppButton 统一主按钮 ==========

/**
 * 统一主按钮
 * 绿底(#2D9B6A)、14vp 圆角、52vp 高度
 * 支持 loading 状态显示 LoadingProgress
 */
@ComponentV2
export struct AppButton {
  @Param text: string = ''
  // 命名注意：不能叫 enabled（与 CustomComponent 基类的同名属性方法冲突导致编译错误）
  @Param isEnabled: boolean = true
  @Param loading: boolean = false
  @Event click: () => void = () => {
  }

  build() {
    Button() {
      if (this.loading) {
        LoadingProgress()
          .width(22)
          .height(22)
          .color(Color.White)
      } else {
        Text(this.text)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.ON_PRIMARY)
      }
    }
    .type(ButtonType.Capsule)
    .width('100%')
    .height(52)
    .borderRadius(DesignDimens.BUTTON_RADIUS)
    .backgroundColor(this.isEnabled && !this.loading ? DesignColor.PRIMARY : DesignColor.TEXT_TERTIARY)
    .stateEffect(this.isEnabled && !this.loading)
    .enabled(this.isEnabled && !this.loading)
    .onClick(() => {
      if (this.isEnabled && !this.loading) {
        // 触觉反馈
        try {
          vibrator.startVibration({ type: 'time', duration: 10 }, { id: 0, usage: 'unknown' })
        } catch (err) {
          // 忽略不支持的设备
        }
        this.click()
      }
    })
  }
}

// ========== AppTabBar 标签栏 ==========

/**
 * 标签栏
 * 文字标签 + 底部短横线指示器
 * 选中项绿色加粗，未选中灰色
 */
@ComponentV2
export struct AppTabBar {
  @Param tabs: string[] = []
  @Param selectedIndex: number = 0
  @Event tabSelected: (index: number) => void = (_: number) => {
  }

  build() {
    Row() {
      ForEach(this.tabs, (title: string, index: number) => {
        Column() {
          Text(title)
            .fontSize(this.selectedIndex === index ? DesignFontSize.CARD_TITLE : DesignFontSize.BODY)
            .fontWeight(this.selectedIndex === index ? DesignFontWeight.SEMI_BOLD : DesignFontWeight.NORMAL)
            .fontColor(this.selectedIndex === index ? DesignColor.PRIMARY : DesignColor.TEXT_SECONDARY)
            .padding({ left: 12, right: 12, top: 8, bottom: 8 })

          // 底部指示器
          if (this.selectedIndex === index) {
            Row()
              .width(20)
              .height(3)
              .borderRadius(1.5)
              .backgroundColor(DesignColor.PRIMARY)
          } else {
            Row()
              .width(20)
              .height(3)
          }
        }
        .alignItems(HorizontalAlign.Center)
        .onClick(() => {
          this.tabSelected(index)
        })
      })
    }
    .width('100%')
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
  }
}

// ========== EmptyState 空状态占位 ==========

/**
 * 空状态占位
 * 居中图标 + 提示文字
 */
@ComponentV2
export struct EmptyState {
  @Param message: string = '暂无数据'
  @Param icon: Resource = $r('sys.symbol.square_slash')

  build() {
    Column() {
      // 图标 — 圆形底色背景
      Column() {
        SymbolGlyph(this.icon)
          .fontSize(40)
          .fontColor([DesignColor.TEXT_SECONDARY])
      }
      .width(96)
      .height(96)
      .borderRadius(48)
      .backgroundColor(DesignColor.SURFACE_VARIANT)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .margin({ bottom: 16 })

      Text(this.message)
        .fontSize(DesignFontSize.BODY)
        .fontColor(DesignColor.TEXT_SECONDARY)
    }
    .width('100%')
    .padding({ top: 64, bottom: 64 })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
  }
}

// ========== AppConfirmDialog 确认弹窗 ==========

/**
 * 确认弹窗
 * 用于：取消订单、确认支付、解绑校园卡、退出登录等
 *
 * 使用方式：
 * private dialogController: CustomDialogController = new CustomDialogController({
 *   builder: AppConfirmDialog({
 *     title: '提示',
 *     message: '确定要取消订单吗？',
 *     onConfirm: () => { ... },
 *     onDismiss: () => { ... }
 *   }),
 *   autoCancel: true,
 *   alignment: DialogAlignment.Center,
 *   customStyle: true
 * })
 */
@CustomDialog
export struct AppConfirmDialog {
  // 由父组件注入控制器（与 Setting.ets 的弹窗写法一致；空 builder 的自建控制器不合法）
  controller: CustomDialogController
  @Prop title: string = ''
  @Prop message: string = ''
  @Prop confirmText: string = '确认'
  @Prop cancelText: string = '取消'
  onConfirm: () => void = () => {
  }
  onDismiss: () => void = () => {
  }

  build() {
    // build 只允许单根节点：内容 Column / 分隔线 / 按钮行统一收进一个 Column（原多根节点不满足 UI 语法）
    Column() {
      // 内容区
      Column() {
        // 标题
        if (this.title !== '') {
          Text(this.title)
            .fontSize(DesignFontSize.SECTION_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .margin({ bottom: 12 })
        }

        // 内容
        Text(this.message)
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ bottom: 20 })
      }
      .width('100%')
      .padding({
        left: DesignDimens.PAGE_PADDING,
        right: DesignDimens.PAGE_PADDING,
        top: 20,
        bottom: 0
      })
      .shadow({
        radius: DesignElevation.LEVEL_4,
        color: '#33000000',
        offsetX: 0,
        offsetY: 4
      })

      // 分隔线
      Divider()
        .color(DesignColor.DIVIDER)
        .strokeWidth(0.5)

      // 按钮行
      Row() {
        Button(this.cancelText)
          .type(ButtonType.Normal)
          .layoutWeight(1)
          .height(48)
          .backgroundColor(Color.Transparent)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .fontSize(DesignFontSize.BODY)
          .borderRadius(0)
          .onClick(() => {
            this.onDismiss()
            this.controller.close()
          })

        Divider()
          .vertical(true)
          .color(DesignColor.DIVIDER)
          .strokeWidth(0.5)
          .height(48)

        Button(this.confirmText)
          .type(ButtonType.Normal)
          .layoutWeight(1)
          .height(48)
          .backgroundColor(Color.Transparent)
          .fontColor(DesignColor.PRIMARY)
          .fontSize(DesignFontSize.BODY)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .borderRadius(0)
          .onClick(() => {
            this.onConfirm()
            this.controller.close()
          })
      }
      .width('100%')
    }
  }
}

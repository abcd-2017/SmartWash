// 吃啥（dining）域共享 UI 组件 — 状态标签 / 勾选框 / 备注弹窗。
// 设计语言对齐清氧规范与既有域先例（AppCard / AppConfirmDialog / SymbolGlyph 令牌）。

import { DesignDimens, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { resString } from '../../../utils/ResourceString'
import { DiningOrderStatus } from '../DiningLogic'
import { DiningText } from '../DiningText'

/**
 * 点单订单状态标签（进行中 = primary 浅底主色字 / 已结束 = surfaceVariant 底次级字）。
 * 对齐清氧小胶囊画法（radiusFull + caption 档）。
 */
@ComponentV2
export struct DiningStatusTag {
  @Param @Require status: DiningOrderStatus = DiningOrderStatus.FINISHED

  build() {
    Text(this.status === DiningOrderStatus.ONGOING
      ? DiningText.dining_order_status_ongoing
      : DiningText.dining_order_status_finished)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(this.status === DiningOrderStatus.ONGOING
        ? $r('app.color.PRIMARY')
        : $r('app.color.TEXT_SECONDARY'))
      .backgroundColor(this.status === DiningOrderStatus.ONGOING
        ? $r('app.color.PRIMARY_LIGHT')
        : $r('app.color.SURFACE_VARIANT'))
      .borderRadius(DesignDimens.RADIUS_FULL)
      .padding({
        left: DesignDimens.SPACE_SM,
        right: DesignDimens.SPACE_SM,
        top: 2,
        bottom: 2
      })
  }
}

/**
 * 自绘勾选框（对齐 Android dining 域勾选框：20vp 外环 + primary 实底 + 白色对勾）。
 * 无点击逻辑：勾选切换由调用方行级手势消费，本组件只做状态表达。
 */
@ComponentV2
export struct DiningCheckbox {
  @Param checked: boolean = false
  @Param boxSize: number = 20

  build() {
    Stack({ alignContent: Alignment.Center }) {
      Column()
        .width(this.boxSize)
        .height(this.boxSize)
        .borderRadius(6)
        .border({
          width: 1,
          color: this.checked ? $r('app.color.PRIMARY') : $r('app.color.OUTLINE'),
          style: BorderStyle.Solid
        })
        .backgroundColor(this.checked ? $r('app.color.PRIMARY') : Color.Transparent)
        .animation({ duration: 120, curve: Curve.EaseOut })

      if (this.checked) {
        SymbolGlyph($r('sys.symbol.checkmark'))
          .fontSize(this.boxSize * 0.7)
          .fontColor([$r('app.color.ON_PRIMARY')])
          .animation({ duration: 120, curve: Curve.EaseOut })
      }
    }
    .width(this.boxSize)
    .height(this.boxSize)
  }
}

/**
 * 备注编辑弹窗（点单页「备注」入口；保存回调携带清洗后的备注，空串 = 清空备注）。
 * 独立于 AppConfirmDialog：需要文本输入，自定义卡片内嵌 TextArea。
 */
@CustomDialog
export struct DiningNoteDialog {
  controller: CustomDialogController
  /** 初始备注（编辑回显；空串 = 未填写） */
  @Prop initialNote: string = ''
  /** 弹窗标题 */
  @Prop title: string = ''
  /** 输入占位 */
  @Prop hint: string = ''
  onConfirm: (note: string) => void = (_: string) => {
  }
  @State note: string = ''

  aboutToAppear(): void {
    this.note = this.initialNote
  }

  build() {
    Column() {
      Column() {
        Text(this.title)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .margin({ bottom: DesignDimens.SPACE_SM })

        TextArea({ text: this.note, placeholder: this.hint })
          .fontSize(DesignFontSize.BODY)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .placeholderColor($r('app.color.TEXT_TERTIARY'))
          .placeholderFont({ size: DesignFontSize.BODY })
          .caretColor($r('app.color.PRIMARY'))
          .backgroundColor($r('app.color.SURFACE_VARIANT'))
          .borderRadius(DesignDimens.INPUT_RADIUS)
          .width('100%')
          .height(96)
          .maxLength(200)
          .onChange((value: string) => {
            this.note = value
          })

        Row() {
          Button(resString('cancel'))
            .type(ButtonType.Normal)
            .layoutWeight(1)
            .height(48)
            .backgroundColor(Color.Transparent)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .fontSize(DesignFontSize.BODY)
            .borderRadius(0)
            .onClick(() => {
              this.controller.close()
            })

          Divider()
            .vertical(true)
            .color($r('app.color.DIVIDER'))
            .strokeWidth(0.5)
            .height(48)

          Button(this.title)
            .type(ButtonType.Normal)
            .layoutWeight(1)
            .height(48)
            .backgroundColor(Color.Transparent)
            .fontColor($r('app.color.PRIMARY'))
            .fontSize(DesignFontSize.BODY)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .borderRadius(0)
            .onClick(() => {
              // 先关窗再回调（回调内常触发网络请求与状态刷新，与 AppConfirmDialog 同序）
              const trimmed = this.note.trim()
              this.controller.close()
              this.onConfirm(trimmed)
            })
        }
        .width('100%')
        .margin({ top: DesignDimens.SPACE_MD })
      }
      .width('100%')
      .padding({
        left: DesignDimens.PAGE_PADDING,
        right: DesignDimens.PAGE_PADDING,
        top: DesignDimens.SPACE_LG,
        bottom: DesignDimens.SPACE_XS
      })
    }
    .width('100%')
    .backgroundColor($r('app.color.SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .margin({ left: DesignDimens.SPACE_XL, right: DesignDimens.SPACE_XL })
  }
}

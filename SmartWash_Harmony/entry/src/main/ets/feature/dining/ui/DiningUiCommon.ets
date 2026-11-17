// 吃啥（dining）域共享 UI 组件 — 状态标签 / 勾选框 / 备注弹窗。
// 设计语言对齐清氧规范与既有域先例（AppCard / AppConfirmDialog / SymbolGlyph 令牌）。

import { DesignDimens, DesignFontSize, DesignFontWeight, MotionTiming } from '../../../constant/DesignSystem'
import { resString } from '../../../utils/ResourceString'
import { DiningOrderStatus, formatDiningAmount } from '../DiningLogic'
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
 * 热量徽标（契约 §二：一份热量 = caloriesTotal ?: caloriesPer100g × 250 ÷ 100，四端同口径，
 * 估算口径展示、不做超标提醒）。对齐 Android DiningCalorieBadge 画法：
 * 已知热量 = primary 浅底主色字「≈N kcal/份」；两者皆缺 = surfaceVariant 底次级字「热量未知」。
 */
@ComponentV2
export struct DiningCalorieBadge {
  /** 份热量 Kcal（null = 热量未知；调用方经 DiningLogic.diningServingCalories 派生） */
  @Param calories: number | null = null

  /** 已知热量判定（非有限值同样按未知渲染，与文案同源） */
  private isKnown(): boolean {
    return formatDiningAmount(this.calories) !== null
  }

  private badgeText(): string {
    const known = DiningText.calorieBadge(this.calories)
    return known !== null ? known : DiningText.dining_nutrition_calorie_unknown
  }

  build() {
    Text(this.badgeText())
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(this.isKnown() ? $r('app.color.PRIMARY') : $r('app.color.TEXT_SECONDARY'))
      .backgroundColor(this.isKnown() ? $r('app.color.PRIMARY_LIGHT') : $r('app.color.SURFACE_VARIANT'))
      .borderRadius(DesignDimens.RADIUS_SMALL)
      .padding({ left: DesignDimens.SPACE_XS, right: DesignDimens.SPACE_XS, top: 2, bottom: 2 })
      .maxLines(1)
      .textOverflow({ overflow: TextOverflow.Ellipsis })
  }
}

/**
 * 自绘勾选框（对齐 Android dining 域勾选框：20vp 外环 + primary 实底 + 白色对勾）。
 * 选中态整体恒定放大 1.12（TD19/D11 对齐安卓加选按钮 spring 弹跳：勾选瞬间放大过冲回落，
 * 取消时回弹），缩放过渡走弹跳弹簧；无点击逻辑：勾选切换由调用方行级手势消费。
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
    .scale({
      x: this.checked ? 1.12 : 1,
      y: this.checked ? 1.12 : 1
    })
    .animation({ curve: MotionTiming.BOUNCY })
  }
}

/**
 * 菜谱封面缩略（TD18 对齐安卓定案）：dining/covers/category_*.png 分类占位键视为无封面
 * （转换器新数据已不再生成占位键，渲染层兼容存量数据）；无封面/占位键回落浅绿底 +
 * 菜名首字占位（不依赖网络，红白色跳出清氧绿系的旧占位图不再出现）。
 * 无点击逻辑：点击跳转由调用方行级手势消费，本组件只做视觉表达。
 */
@ComponentV2
export struct DiningRecipeThumb {
  /** 封面直链（空串 = 无封面） */
  @Param coverUrl: string = ''
  /** 菜名（无封面回落首字占位） */
  @Param name: string = ''
  /** 缩略边长（vp） */
  @Param thumbSize: number = 72

  /** 真实封面判定（TD18）：分类占位键（dining/covers/category_*）视为无封面 */
  private hasRealCover(): boolean {
    return this.coverUrl.length > 0 && !this.coverUrl.includes('dining/covers/category_')
  }

  build() {
    Stack() {
      if (this.hasRealCover()) {
        Image(this.coverUrl)
          .width(this.thumbSize)
          .height(this.thumbSize)
          .borderRadius(DesignDimens.RADIUS_MD)
          .objectFit(ImageFit.Cover)
          .draggable(false)
      } else {
        Column() {
          Text(this.name.length > 0 ? this.name.charAt(0) : '')
            .fontSize(Math.round(this.thumbSize * 0.33))
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor($r('app.color.PRIMARY_DARK'))
        }
        .width(this.thumbSize)
        .height(this.thumbSize)
        .borderRadius(DesignDimens.RADIUS_MD)
        .backgroundColor($r('app.color.ICON_CONTAINER_GREEN'))
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
      }
    }
    .width(this.thumbSize)
    .height(this.thumbSize)
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

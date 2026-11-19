// 吃啥（dining）域共享 UI 组件 — 状态标签 / 勾选框 / 备注弹窗。
// 设计语言对齐清氧规范与既有域先例（AppCard / AppConfirmDialog / SymbolGlyph 令牌）。

import { DesignDimens, DesignFontSize, DesignFontWeight, MotionTiming } from '../../../constant/DesignSystem'
import { ReduceMotion } from '../../../utils/ReduceMotion'
import { resString } from '../../../utils/ResourceString'
import { DINING_DEFAULT_CALORIE_TARGET, DiningRecipeVo } from '../../../network/vo/DiningVo'
import {
  DINING_PEOPLE_MAX,
  DINING_PEOPLE_MIN,
  DiningOrderStatus,
  DiningPortionVerdict,
  coerceDiningPeopleCount,
  diningPortionAdvice,
  diningSelectedTotalCalories,
  formatDiningAmount
} from '../DiningLogic'
import { DiningText } from '../DiningText'

/**
 * 点单订单状态标签（草稿 = secondary 档 / 已结束 = surfaceVariant 底次级字）。
 * 对齐清氧小胶囊画法（radiusFull + caption 档）。契约移除 ONGOING，仅两态。
 */
@ComponentV2
export struct DiningStatusTag {
  @Param @Require status: DiningOrderStatus = DiningOrderStatus.FINISHED

  build() {
    Text(this.status === DiningOrderStatus.DRAFT
      ? DiningText.dining_order_status_draft
      : DiningText.dining_order_status_finished)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(this.status === DiningOrderStatus.DRAFT
        ? $r('app.color.ON_SECONDARY_CONTAINER')
        : $r('app.color.TEXT_SECONDARY'))
      .backgroundColor(this.status === DiningOrderStatus.DRAFT
        ? $r('app.color.SECONDARY_CONTAINER')
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
        // 选中态颜色走临界阻尼弹簧（对齐安卓 selectedColor = motionSpec(defaultSpring())），reduced 降 180ms
        .animation(ReduceMotion.specCurve(MotionTiming.SELECTED))

      if (this.checked) {
        SymbolGlyph($r('sys.symbol.checkmark'))
          .fontSize(this.boxSize * 0.7)
          .fontColor([$r('app.color.ON_PRIMARY')])
          .animation(ReduceMotion.specCurve(MotionTiming.SELECTED))
      }
    }
    .width(this.boxSize)
    .height(this.boxSize)
    .scale({
      x: this.checked ? 1.12 : 1,
      y: this.checked ? 1.12 : 1
    })
    // 加选弹跳 spring(0.5,1500)（对齐安卓 MediumBouncy），reduced 降级 180ms 无回弹
    .animation(ReduceMotion.specCurve(MotionTiming.BOUNCY))
  }
}

/**
 * 菜谱封面缩略（TD18 对齐安卓定案）：dining/covers/category_*.png 分类占位键视为无封面
 * （转换器新数据已不再生成占位键，渲染层兼容存量数据）；无封面/占位键回落浅绿底 +
 * 品牌碗筷线稿占位图（D24，替代菜名首字——首字在小图上可读性差且无品牌感，
 * 四端一致复用 Android feature/dining dining_recipe_placeholder 同款图形）。
 * 无点击逻辑：点击跳转由调用方行级手势消费，本组件只做视觉表达。
 */
@ComponentV2
export struct DiningRecipeThumb {
  /** 封面直链（空串 = 无封面） */
  @Param coverUrl: string = ''
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
          // 无封面占位：品牌碗筷线稿（D24），Contain + 内边距防止线稿顶满浅绿底
          Image($r('app.media.dining_recipe_placeholder'))
            .width('100%')
            .height('100%')
            .objectFit(ImageFit.Contain)
            .padding(10)
            .draggable(false)
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

/**
 * 购物车弹窗（创建/编辑页底栏左侧信息区点击唤起，契约 §五.2）：
 * 已选菜明细（名称 + 食材）+ 逐行移除 + 人数 stepper（1-20）+ 份量建议。
 * 行内口径同契约 §五.1：文字右内边距 16dp、移除按钮右内边距 12dp。
 *
 * 本地 @State 承载弹窗内编辑态（打开时以 @Prop 快照初始化），移除/人数变化即时回抛父页，
 * 保证弹窗内 reactive 刷新（CustomDialog 内容不随 @Prop 自动重建）。
 */
@CustomDialog
export struct DiningCartPopup {
  controller: CustomDialogController
  /** 打开时的已选菜快照（保序） */
  @Prop selectedRecipes: Array<DiningRecipeVo> = []
  /** 打开时的人数快照 */
  @Prop peopleCount: number = 1
  /** 每日热量目标（份量建议用） */
  @Prop calorieTarget: number = DINING_DEFAULT_CALORIE_TARGET
  /** 移除一道菜（回抛父页写 dishes 差分） */
  onRemove: (recipeId: number) => void = (_: number) => {
  }
  /** 人数变化（回抛父页同步本地态） */
  onPeopleChange: (count: number) => void = (_: number) => {
  }

  @State private selected: Array<DiningRecipeVo> = []
  @State private people: number = 1

  aboutToAppear(): void {
    this.selected = this.selectedRecipes
    this.people = coerceDiningPeopleCount(this.peopleCount)
  }

  /** 移除：本地即时收缩 + 回抛父页（父页写草稿差分） */
  private remove(recipeId: number): void {
    this.selected = this.selected.filter((recipe: DiningRecipeVo): boolean => recipe.id !== recipeId)
    this.onRemove(recipeId)
  }

  /** 人数变化：本地即时刷新 + 回抛父页 */
  private changePeople(delta: number): void {
    const next = coerceDiningPeopleCount(this.people + delta)
    if (next === this.people) {
      return
    }
    this.people = next
    this.onPeopleChange(next)
  }

  /** 份量建议文案（总热量未知返回 null，不渲染） */
  private portionText(): string | null {
    const advice = diningPortionAdvice(
      diningSelectedTotalCalories(this.selected), this.people, this.calorieTarget)
    if (advice === null) {
      return null
    }
    const perCapita = formatDiningAmount(advice.perCapita) ?? ''
    if (advice.verdict === DiningPortionVerdict.ENOUGH) {
      return DiningText.portionAdviceEnough(advice.peopleCount, perCapita)
    }
    if (advice.verdict === DiningPortionVerdict.MORE) {
      return DiningText.portionAdviceMore(advice.peopleCount, perCapita)
    }
    return DiningText.portionAdviceLess(advice.peopleCount, perCapita)
  }

  @Builder
  DishRow(recipe: DiningRecipeVo) {
    Row() {
      Column({ space: 2 }) {
        Text(recipe.name)
          .fontSize(DesignFontSize.BODY)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        if (recipe.ingredients.length > 0) {
          Text(recipe.ingredients.join('、'))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
        }
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
      .padding({ right: 16 })

      Text(DiningText.dining_order_cart_remove)
        .fontSize(DesignFontSize.BUTTON_TEXT)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor($r('app.color.DANGER_INK'))
        .onClick(() => {
          this.remove(recipe.id)
        })
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
    .padding({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS, right: 12 })
  }

  build() {
    Column() {
      // 标题行：购物车 + 关闭
      Row() {
        Text(DiningText.dining_order_cart_title)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .layoutWeight(1)
        SymbolGlyph($r('sys.symbol.xmark'))
          .fontSize(20)
          .fontColor([$r('app.color.TEXT_SECONDARY')])
          .width(48)
          .height(48)
          .onClick(() => {
            this.controller.close()
          })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      // 已选明细（空态提示）
      if (this.selected.length === 0) {
        Text(DiningText.dining_order_cart_empty)
          .fontSize(DesignFontSize.BODY)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .width('100%')
          .textAlign(TextAlign.Center)
          .padding({ top: DesignDimens.SPACE_LG, bottom: DesignDimens.SPACE_LG })
      } else {
        List() {
          ForEach(this.selected, (recipe: DiningRecipeVo) => {
            ListItem() {
              this.DishRow(recipe)
            }
          }, (recipe: DiningRecipeVo): string => `cart_${recipe.id}`)
        }
        .scrollBar(BarState.Off)
        .width('100%')
        .constraintSize({ maxHeight: 280 })
        .padding({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
        .divider({ strokeWidth: 0.5, color: $r('app.color.DIVIDER') })
      }

      // 人数 stepper（1-20，本地态）
      Row({ space: DesignDimens.SPACE_SM }) {
        Text(DiningText.dining_portion_people_label)
          .fontSize(DesignFontSize.BODY)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .layoutWeight(1)
        SymbolGlyph($r('sys.symbol.minus'))
          .fontSize(20)
          .fontColor([this.people > DINING_PEOPLE_MIN ? $r('app.color.TEXT_PRIMARY') : $r('app.color.TEXT_TERTIARY')])
          .width(48)
          .height(48)
          .onClick(() => {
            this.changePeople(-1)
          })
        Text(`${this.people}`)
          .fontSize(DesignFontSize.BODY)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .textAlign(TextAlign.Center)
        SymbolGlyph($r('sys.symbol.plus'))
          .fontSize(20)
          .fontColor([this.people < DINING_PEOPLE_MAX ? $r('app.color.TEXT_PRIMARY') : $r('app.color.TEXT_TERTIARY')])
          .width(48)
          .height(48)
          .onClick(() => {
            this.changePeople(1)
          })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
      .padding({ top: DesignDimens.SPACE_SM })

      if (this.portionText() !== null) {
        Text(this.portionText() ?? '')
          .fontSize(DesignFontSize.CAPTION)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .width('100%')
          .padding({ top: DesignDimens.SPACE_XS })
      }
    }
    .width('100%')
    .backgroundColor($r('app.color.SURFACE'))
    .borderRadius({ topLeft: DesignDimens.CARD_RADIUS, topRight: DesignDimens.CARD_RADIUS })
    .padding({
      left: DesignDimens.PAGE_PADDING,
      right: DesignDimens.PAGE_PADDING,
      top: DesignDimens.SPACE_MD,
      bottom: DesignDimens.SPACE_LG
    })
  }
}

// 饮食偏好设置页 — 对齐 Android DietaryPreferencePage.kt（契约 §2.1/§三.2）
//
// 忌口 7 标签多选（SEAFOOD/NUT/GLUTEN/DAIRY/EGG/SOY/SPICY）+ 每日热量目标 stepper（1200-4000）
// + 每日钠目标 stepper（1000-4000）；进入 GET 回填（null 字段兜底 2000/2000），保存走 PUT。
// 入口：设置页「其他」组。

import { DesignDimens, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import {
  DietaryPreferenceVo,
  getDiningDietaryPreference,
  saveDiningDietaryPreference
} from '../../../network/api/DiningApi'
import {
  DINING_CALORIE_MAX,
  DINING_CALORIE_MIN,
  DINING_DEFAULT_CALORIE_TARGET,
  DINING_DEFAULT_SODIUM_TARGET,
  DINING_SODIUM_MAX,
  DINING_SODIUM_MIN
} from '../../../network/vo/DiningVo'
import { DINING_ALLERGEN_CODES } from '../DiningLogic'
import { DiningText } from '../DiningText'
import { AppButton, EmptyState, SkeletonList } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { pathStack } from '../../../utils/PathStackUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'

const TAG: string = 'diningDietaryPreference'

@Builder
export function DiningDietaryPreferenceBuilder() {
  DiningDietaryPreferencePage()
}

@ComponentV2
struct DiningDietaryPreferencePage {
  @Local allergens: Array<string> = []
  @Local calorieTarget: number = DINING_DEFAULT_CALORIE_TARGET
  @Local sodiumTarget: number = DINING_DEFAULT_SODIUM_TARGET
  @Local isLoading: boolean = true
  @Local isError: boolean = false
  @Local saving: boolean = false

  async aboutToAppear(): Promise<void> {
    await this.load()
  }

  build() {
    NavDestination() {
      ThemeRefresh() {
        Column() {
          Row() {
            Column() {
              SymbolGlyph($r('sys.symbol.chevron_left'))
                .fontSize(24)
                .fontColor([$r('app.color.TEXT_PRIMARY')])
            }
            .width(DesignDimens.BACK_HIT_AREA)
            .height(DesignDimens.BACK_HIT_AREA)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)
            .onClick(() => {
              pathStack.pop()
            })

            Text(DiningText.dietary_preference_title)
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_PRIMARY'))

            Blank()
          }
          .width('100%')
          .height(56)
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
          .alignItems(VerticalAlign.Center)

          if (this.isLoading) {
            Column() {
              SkeletonList({ rows: 5 })
            }
            .width('100%')
            .layoutWeight(1)
          } else if (this.isError) {
            Column() {
              EmptyState({ message: DiningText.dietary_preference_load_failed }) {
                Column() {
                  AppButton({
                    small: true,
                    text: DiningText.dietary_preference_retry,
                    click: (): void => {
                      this.load()
                    }
                  })
                }
                .width('100%')
                .padding({ top: DesignDimens.SPACE_MD })
              }
            }
            .width('100%')
            .layoutWeight(1)
          } else {
            Scroll() {
              Column() {
                // 忌口多选 chips
                Text(DiningText.dietary_preference_allergens_label)
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .fontWeight(DesignFontWeight.MEDIUM)
                  .fontColor($r('app.color.TEXT_PRIMARY'))
                  .width('100%')
                Text(DiningText.dietary_preference_allergens_hint)
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor($r('app.color.TEXT_TERTIARY'))
                  .width('100%')
                  .padding({ top: 2, bottom: DesignDimens.SPACE_XS })
                Flex({
                  wrap: FlexWrap.Wrap,
                  justifyContent: FlexAlign.Start
                }) {
                  ForEach(DINING_ALLERGEN_CODES, (code: string) => {
                    this.AllergenChip(code)
                  }, (code: string): string => `allergen_${code}`)
                }
                .width('100%')

                // 每日热量目标 stepper
                Text(DiningText.dietary_preference_calorie_label)
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .fontWeight(DesignFontWeight.MEDIUM)
                  .fontColor($r('app.color.TEXT_PRIMARY'))
                  .width('100%')
                  .margin({ top: DesignDimens.SECTION_SPACING })
                Row({ space: DesignDimens.SPACE_SM }) {
                  SymbolGlyph($r('sys.symbol.minus'))
                    .fontSize(20)
                    .fontColor([$r('app.color.TEXT_PRIMARY')])
                    .width(48)
                    .height(48)
                    .onClick(() => {
                      this.calorieTarget = this.coerceCalorie(this.calorieTarget - 50)
                    })
                  Text(`${this.calorieTarget}`)
                    .fontSize(DesignFontSize.BODY)
                    .fontWeight(DesignFontWeight.MEDIUM)
                    .fontColor($r('app.color.TEXT_PRIMARY'))
                    .textAlign(TextAlign.Center)
                  Text(DiningText.dietary_unit_kcal)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor($r('app.color.TEXT_SECONDARY'))
                  SymbolGlyph($r('sys.symbol.plus'))
                    .fontSize(20)
                    .fontColor([$r('app.color.TEXT_PRIMARY')])
                    .width(48)
                    .height(48)
                    .onClick(() => {
                      this.calorieTarget = this.coerceCalorie(this.calorieTarget + 50)
                    })
                }
                .width('100%')
                .alignItems(VerticalAlign.Center)
                .padding({ top: DesignDimens.SPACE_XS })

                // 每日钠目标 stepper
                Text(DiningText.dietary_preference_sodium_label)
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .fontWeight(DesignFontWeight.MEDIUM)
                  .fontColor($r('app.color.TEXT_PRIMARY'))
                  .width('100%')
                  .margin({ top: DesignDimens.SPACE_MD })
                Row({ space: DesignDimens.SPACE_SM }) {
                  SymbolGlyph($r('sys.symbol.minus'))
                    .fontSize(20)
                    .fontColor([$r('app.color.TEXT_PRIMARY')])
                    .width(48)
                    .height(48)
                    .onClick(() => {
                      this.sodiumTarget = this.coerceSodium(this.sodiumTarget - 50)
                    })
                  Text(`${this.sodiumTarget}`)
                    .fontSize(DesignFontSize.BODY)
                    .fontWeight(DesignFontWeight.MEDIUM)
                    .fontColor($r('app.color.TEXT_PRIMARY'))
                    .textAlign(TextAlign.Center)
                  Text(DiningText.dietary_unit_milligram)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor($r('app.color.TEXT_SECONDARY'))
                  SymbolGlyph($r('sys.symbol.plus'))
                    .fontSize(20)
                    .fontColor([$r('app.color.TEXT_PRIMARY')])
                    .width(48)
                    .height(48)
                    .onClick(() => {
                      this.sodiumTarget = this.coerceSodium(this.sodiumTarget + 50)
                    })
                }
                .width('100%')
                .alignItems(VerticalAlign.Center)
                .padding({ top: DesignDimens.SPACE_XS })

                Column() {
                }
                .height(Math.max(safeBottomVp(), DesignDimens.SPACE_XL))
              }
              .width('100%')
              .padding({
                left: DesignDimens.PAGE_PADDING,
                right: DesignDimens.PAGE_PADDING,
                top: DesignDimens.SPACE_SM
              })
            }
            .scrollBar(BarState.Off)
            .width('100%')
            .layoutWeight(1)
            .align(Alignment.TopStart)
            .edgeEffect(EdgeEffect.Spring)

            AppButton({
              text: DiningText.dietary_preference_save,
              loading: this.saving,
              click: () => {
                this.save()
              }
            })
              .padding({
                left: DesignDimens.PAGE_PADDING,
                right: DesignDimens.PAGE_PADDING,
                bottom: Math.max(safeBottomVp(), DesignDimens.SPACE_MD)
              })
          }
        }
        .width('100%')
        .height('100%')
        .padding({ top: safeTopVp(), bottom: safeBottomVp() })
        .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
  }

  /** 忌口 chip（多选） */
  @Builder
  AllergenChip(code: string) {
    Row() {
      Text(DiningText.dietaryAllergenLabel(code))
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(this.allergens.indexOf(code) >= 0
          ? $r('app.color.ON_PRIMARY')
          : $r('app.color.TEXT_SECONDARY'))
    }
    .justifyContent(FlexAlign.Center)
    .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: 8, bottom: 8 })
    .borderRadius(DesignDimens.RADIUS_FULL)
    .backgroundColor(this.allergens.indexOf(code) >= 0
      ? $r('app.color.PRIMARY')
      : $r('app.color.SURFACE'))
    .border({
      width: 1,
      color: this.allergens.indexOf(code) >= 0 ? $r('app.color.PRIMARY') : $r('app.color.OUTLINE')
    })
    .margin({ right: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
    .onClick(() => {
      this.toggleAllergen(code)
    })
  }

  private coerceCalorie(value: number): number {
    if (value < DINING_CALORIE_MIN) {
      return DINING_CALORIE_MIN
    }
    if (value > DINING_CALORIE_MAX) {
      return DINING_CALORIE_MAX
    }
    return value
  }

  private coerceSodium(value: number): number {
    if (value < DINING_SODIUM_MIN) {
      return DINING_SODIUM_MIN
    }
    if (value > DINING_SODIUM_MAX) {
      return DINING_SODIUM_MAX
    }
    return value
  }

  private toggleAllergen(code: string): void {
    if (DINING_ALLERGEN_CODES.indexOf(code) < 0) {
      return
    }
    const index = this.allergens.indexOf(code)
    if (index >= 0) {
      const next = this.allergens.slice()
      next.splice(index, 1)
      this.allergens = next
    } else {
      this.allergens = this.allergens.concat([code])
    }
  }

  private async load(): Promise<void> {
    this.isLoading = true
    this.isError = false
    try {
      const res = await getDiningDietaryPreference()
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const preference: DietaryPreferenceVo = res.data
        this.allergens = preference.allergens === null ? [] : preference.allergens
        this.calorieTarget = preference.calorieTarget !== null && preference.calorieTarget > 0
          ? this.coerceCalorie(preference.calorieTarget)
          : DINING_DEFAULT_CALORIE_TARGET
        this.sodiumTarget = preference.sodiumTarget !== null && preference.sodiumTarget > 0
          ? this.coerceSodium(preference.sodiumTarget)
          : DINING_DEFAULT_SODIUM_TARGET
        this.isLoading = false
      } else {
        logger.warn(TAG, `饮食偏好加载失败 code=${res.code} message=${res.message ?? ''}`)
        this.isLoading = false
        this.isError = true
        showToastSafety(res.message, DiningText.dietary_preference_load_failed)
      }
    } catch (err) {
      logger.error(TAG, '饮食偏好加载异常', err instanceof Error ? err.message : '')
      this.isLoading = false
      this.isError = true
      showToastSafety('', DiningText.dietary_preference_load_failed)
    }
  }

  private async save(): Promise<void> {
    if (this.saving) {
      return
    }
    this.saving = true
    try {
      const res = await saveDiningDietaryPreference({
        allergens: this.allergens,
        calorieTarget: this.calorieTarget,
        sodiumTarget: this.sodiumTarget
      })
      if (res.code === BizCode.SUCCESS) {
        logger.info(TAG, '饮食偏好保存成功')
        showToastSafety(DiningText.dietary_preference_save_success)
        pathStack.pop()
      } else {
        logger.warn(TAG, `饮食偏好保存失败 code=${res.code} message=${res.message ?? ''}`)
        showToastSafety(res.message, DiningText.dietary_preference_load_failed)
      }
    } catch (err) {
      logger.error(TAG, '饮食偏好保存异常', err instanceof Error ? err.message : '')
      showToastSafety('', DiningText.dietary_preference_load_failed)
    }
    this.saving = false
  }
}

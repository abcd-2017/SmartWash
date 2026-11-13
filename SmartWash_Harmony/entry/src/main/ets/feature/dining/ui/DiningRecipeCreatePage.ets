// 新增菜谱页 — 对齐 Android DiningRecipeCreatePage.kt（04 号票）
//
// 表单：菜名 / 分类（下拉选择，0=未分类）/ 食材（动态输入行 + 添加/移除）/ 步骤 TextArea
// / 封面（PhotoPicker 选择 → multipart 上传拿对象键 → 随新增回填）。source=USER 直上架。
// 校验两层：端上 DiningLogic.validateDiningRecipeForm 先拦，后端兜底。

import { fileIo as fs } from '@kit.CoreFileKit'
import { photoAccessHelper } from '@kit.MediaLibraryKit'
import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import {
  AddDiningRecipeForm,
  DiningCategoryVo,
  addDiningRecipe,
  getDiningCategories,
  uploadDiningCover
} from '../../../network/api/DiningApi'
import { DiningRecipeFormError, validateDiningRecipeForm } from '../DiningLogic'
import { DiningText } from '../DiningText'
import { AppButton, SkeletonBlock } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { pathStack } from '../../../utils/PathStackUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'

/** 日志 TAG */
const TAG: string = 'diningRecipeCreate'

/** 封面上限（与后端 RecipeImageValidator 一致：5MB） */
const COVER_MAX_BYTES: number = 5 * 1024 * 1024

/** 食材输入行 key 生成基准（动态行稳定 key） */
let ingredientKeySeed: number = 0

/** 食材动态输入行模型（value = 输入内容，key = ForEach 稳定标识） */
interface IngredientRow {
  key: number
  value: string
}

@Builder
export function DiningRecipeCreateBuilder() {
  DiningRecipeCreatePage()
}

@ComponentV2
struct DiningRecipeCreatePage {
  @Local nameInput: string = ''
  @Local categories: Array<DiningCategoryVo> = []
  @Local selectedCategoryId: number = 0
  @Local selectedCategoryLabel: string = ''
  /** 分类选择弹层展开态 */
  @Local categoryMenuOpen: boolean = false
  @Local ingredientRows: Array<IngredientRow> = []
  @Local stepsInput: string = ''
  /** 封面：对象键（上传成功回填）+ 预览直链为空（本地上传不回显远端；用本地 URI 预览） */
  @Local coverObjectKey: string = ''
  @Local coverPreviewUri: string = ''
  @Local coverUploading: boolean = false
  @Local submitting: boolean = false
  @Local isLoading: boolean = true
  @Local isError: boolean = false

  async aboutToAppear(): Promise<void> {
    logger.debug(TAG, '新增菜谱页挂载')
    try {
      const res = await getDiningCategories()
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.categories = res.data
      } else {
        logger.warn(TAG, `分类加载失败 code=${res.code} message=${res.message ?? ''}`)
        // 分类目录失败不致命：可作「未分类」新增（存在性校验由后端裁决）
      }
    } catch (err) {
      logger.warn(TAG, `分类加载异常 ${err instanceof Error ? err.message : ''}`)
    }
    this.isLoading = false
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

            Text(DiningText.dining_recipe_create_title)
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
            Column({ space: DesignDimens.CARD_SPACING }) {
              SkeletonBlock().height(48)
              SkeletonBlock().height(140)
              SkeletonBlock().height(140)
            }
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_MD })
            .layoutWeight(1)
          } else {
            Scroll() {
              Column() {
                this.FormFields()
                Column() {
                }
                .height(DesignDimens.SPACE_MD)
              }
              .width('100%')
              .padding({
                left: DesignDimens.PAGE_PADDING,
                right: DesignDimens.PAGE_PADDING,
                top: DesignDimens.SPACE_SM
              })
            }
            .scrollBar(BarState.Off)
            .layoutWeight(1)
            .align(Alignment.TopStart)
            .edgeEffect(EdgeEffect.Spring)

            AppButton({
              text: DiningText.dining_create_submit,
              loading: this.submitting,
              click: (): void => {
                this.submit()
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

  /** 表单标签（统一小标签档） */
  @Builder
  FieldLabel(text: string) {
    Text(text)
      .fontSize(DesignFontSize.BODY_SMALL)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor($r('app.color.TEXT_PRIMARY'))
      .width('100%')
      .margin({ top: DesignDimens.SPACE_MD })
  }

  @Builder
  FormFields() {
    Column() {
      // 菜名
      this.FieldLabel(DiningText.dining_create_name_label)
      TextInput({ text: this.nameInput, placeholder: DiningText.dining_create_name_hint })
        .placeholderColor($r('app.color.TEXT_TERTIARY'))
        .placeholderFont({ size: DesignFontSize.BODY })
        .caretColor($r('app.color.PRIMARY'))
        .backgroundColor($r('app.color.SURFACE_VARIANT'))
        .borderRadius(DesignDimens.INPUT_RADIUS)
        .fontSize(DesignFontSize.BODY)
        .fontColor($r('app.color.TEXT_PRIMARY'))
        .maxLength(64)
        .width('100%')
        .height(48)
        .margin({ top: DesignDimens.SPACE_XS })
        .onChange((value: string) => {
          this.nameInput = value
        })

      // 分类（0 = 未分类；下拉选择层）
      this.FieldLabel(DiningText.dining_create_category_label)
      Column() {
        Row({ space: DesignDimens.SPACE_SM }) {
          Text(this.selectedCategoryLabel.length > 0
            ? this.selectedCategoryLabel
            : DiningText.dining_create_category_uncategorized)
            .fontSize(DesignFontSize.BODY)
            .fontColor($r('app.color.TEXT_PRIMARY'))
            .layoutWeight(1)
          SymbolGlyph($r('sys.symbol.chevron_down'))
            .fontSize(16)
            .fontColor([$r('app.color.TEXT_TERTIARY')])
        }
        .width('100%')
        .height(48)
        .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD })
        .backgroundColor($r('app.color.SURFACE_VARIANT'))
        .borderRadius(DesignDimens.INPUT_RADIUS)
        .alignItems(VerticalAlign.Center)
        .onClick(() => {
          this.categoryMenuOpen = !this.categoryMenuOpen
        })

        if (this.categoryMenuOpen) {
          Column() {
            // 未分类（categoryId 0）
            this.CategoryOption(0, DiningText.dining_create_category_uncategorized)
            ForEach(this.categories, (category: DiningCategoryVo) => {
              this.CategoryOption(category.id, category.name)
            }, (category: DiningCategoryVo) => `opt_${category.id}`)
          }
          .width('100%')
          .borderRadius(DesignDimens.RADIUS_MD)
          .backgroundColor($r('app.color.SURFACE'))
          .border({ width: 1, color: $r('app.color.OUTLINE') })
          .padding(DesignDimens.SPACE_XS)
          .margin({ top: DesignDimens.SPACE_XS })
          .shadow({
            radius: DesignDimens.SPACE_SM,
            color: $r('app.color.SHADOW_COLOR_CARD'),
            offsetX: 0,
            offsetY: 2
          })
        }
      }
      .width('100%')
      .margin({ top: DesignDimens.SPACE_XS })

      // 食材（动态输入行）
      this.FieldLabel(DiningText.dining_create_ingredients_label)
      if (this.ingredientRows.length === 0) {
        Text(DiningText.dining_create_ingredients_empty)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor($r('app.color.TEXT_TERTIARY'))
          .width('100%')
          .margin({ top: DesignDimens.SPACE_XS })
      }
      ForEach(this.ingredientRows, (row: IngredientRow, index: number) => {
        Row({ space: DesignDimens.SPACE_SM }) {
          TextInput({ text: row.value, placeholder: DiningText.dining_create_ingredients_hint })
            .placeholderColor($r('app.color.TEXT_TERTIARY'))
            .placeholderFont({ size: DesignFontSize.BODY })
            .caretColor($r('app.color.PRIMARY'))
            .backgroundColor($r('app.color.SURFACE_VARIANT'))
            .borderRadius(DesignDimens.INPUT_RADIUS)
            .fontSize(DesignFontSize.BODY)
            .fontColor($r('app.color.TEXT_PRIMARY'))
            .maxLength(32)
            .height(44)
            .layoutWeight(1)
            .onChange((value: string) => {
              this.updateIngredientValue(row.key, value)
            })
          Text('×')
            .fontSize(DesignFontSize.CARD_TITLE)
            .fontColor($r('app.color.DANGER_INK'))
            .width(44)
            .height(44)
            .textAlign(TextAlign.Center)
            .accessibilityDescription('移除该食材')
            .onClick(() => {
              this.removeIngredientRow(row.key)
            })
        }
        .width('100%')
        .alignItems(VerticalAlign.Center)
        .margin({ top: index === 0 ? DesignDimens.SPACE_XS : DesignDimens.SPACE_XS })
      }, (row: IngredientRow) => `ing_row_${row.key}`)

      Row() {
        Text(`+ ${DiningText.dining_create_ingredient_add}`)
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.TEXT_GREEN'))
          .padding({
            left: DesignDimens.SPACE_MD,
            right: DesignDimens.SPACE_MD,
            top: DesignDimens.SPACE_SM,
            bottom: DesignDimens.SPACE_SM
          })
          .borderRadius(DesignDimens.RADIUS_FULL)
          .border({ width: 1, color: $r('app.color.OUTLINE') })
          .backgroundColor($r('app.color.SURFACE'))
          .onClick(() => {
            this.addIngredientRow()
          })
      }
      .width('100%')
      .margin({ top: DesignDimens.SPACE_SM })

      // 步骤
      this.FieldLabel(DiningText.dining_create_steps_label)
      TextArea({ text: this.stepsInput, placeholder: DiningText.dining_create_steps_hint })
        .placeholderColor($r('app.color.TEXT_TERTIARY'))
        .placeholderFont({ size: DesignFontSize.BODY })
        .caretColor($r('app.color.PRIMARY'))
        .backgroundColor($r('app.color.SURFACE_VARIANT'))
        .borderRadius(DesignDimens.INPUT_RADIUS)
        .fontSize(DesignFontSize.BODY)
        .fontColor($r('app.color.TEXT_PRIMARY'))
        .width('100%')
        .height(140)
        .margin({ top: DesignDimens.SPACE_XS })
        .onChange((value: string) => {
          this.stepsInput = value
        })

      // 封面
      this.FieldLabel(DiningText.dining_create_cover_label)
      Row({ space: DesignDimens.SPACE_MD }) {
        if (this.coverPreviewUri.length > 0) {
          Image(this.coverPreviewUri)
            .width(72)
            .height(72)
            .borderRadius(DesignDimens.RADIUS_MD)
            .objectFit(ImageFit.Cover)
            .draggable(false)
        }
        Text(this.coverObjectKey.length > 0 ? DiningText.dining_create_cover_replace : DiningText.dining_create_cover_pick)
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.TEXT_GREEN'))
          .padding({
            left: DesignDimens.SPACE_MD,
            right: DesignDimens.SPACE_MD,
            top: DesignDimens.SPACE_SM,
            bottom: DesignDimens.SPACE_SM
          })
          .borderRadius(DesignDimens.RADIUS_FULL)
          .border({ width: 1, color: $r('app.color.OUTLINE') })
          .backgroundColor($r('app.color.SURFACE'))
          .onClick(() => {
            this.pickAndUploadCover()
          })
        if (this.coverUploading) {
          LoadingProgress()
            .width(20)
            .height(20)
            .color($r('app.color.PRIMARY'))
        }
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
      .margin({ top: DesignDimens.SPACE_XS })
    }
    .width('100%')
  }

  /** 分类下拉选项（未分类 + 全量目录） */
  @Builder
  CategoryOption(categoryId: number, label: string) {
    Text(label)
      .fontSize(DesignFontSize.BODY)
      .fontColor(this.selectedCategoryId === categoryId ? $r('app.color.PRIMARY') : $r('app.color.TEXT_PRIMARY'))
      .width('100%')
      .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM })
      .borderRadius(DesignDimens.RADIUS_MD)
      .backgroundColor(this.selectedCategoryId === categoryId ? $r('app.color.PRIMARY_LIGHT') : Color.Transparent)
      .onClick(() => {
        this.selectedCategoryId = categoryId
        this.selectedCategoryLabel = label
        this.categoryMenuOpen = false
      })
  }

  /** 新增食材行（上限 100 项对齐后端） */
  private addIngredientRow(): void {
    if (this.ingredientRows.length >= 100) {
      showToastSafety(DiningText.dining_form_error_ingredient_too_many)
      return
    }
    ingredientKeySeed++
    this.ingredientRows = this.ingredientRows.concat([{ key: ingredientKeySeed, value: '' }])
  }

  private removeIngredientRow(key: number): void {
    this.ingredientRows = this.ingredientRows.filter((row: IngredientRow): boolean => row.key !== key)
  }

  private updateIngredientValue(key: number, value: string): void {
    this.ingredientRows = this.ingredientRows.map((row: IngredientRow): IngredientRow =>
      row.key === key ? { key: row.key, value: value } : row)
  }

  /** 选封面 → 读 ArrayBuffer → multipart 上传拿对象键（对齐头像上传先例） */
  private async pickAndUploadCover(): Promise<void> {
    if (this.coverUploading) {
      return
    }
    let uri = ''
    try {
      const picker = new photoAccessHelper.PhotoViewPicker()
      const result = await picker.select({
        MIMEType: photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE,
        maxSelectNumber: 1
      })
      if (result.photoUris && result.photoUris.length > 0) {
        uri = result.photoUris[0]
      }
    } catch (err) {
      logger.warn(TAG, `选择封面图片取消或异常 ${err instanceof Error ? err.message : ''}`)
      return
    }
    if (uri.length === 0) {
      return
    }
    this.coverUploading = true
    try {
      const stat = fs.statSync(uri)
      if (stat.size > COVER_MAX_BYTES) {
        showToastSafety(DiningText.dining_create_cover_failed)
        this.coverUploading = false
        return
      }
      const file = fs.openSync(uri, fs.OpenMode.READ_ONLY)
      let buffer: ArrayBuffer
      try {
        buffer = new ArrayBuffer(stat.size)
        fs.readSync(file.fd, buffer)
      } finally {
        fs.closeSync(file)
      }
      const fileName = `cover_${Date.now()}.jpg`
      const res = await uploadDiningCover(fileName, 'image/jpeg', buffer)
      if (res.code === BizCode.SUCCESS && res.data !== null && res.data.length > 0) {
        this.coverObjectKey = res.data
        this.coverPreviewUri = uri
        logger.info(TAG, `封面上传成功 objectKey=${res.data}`)
      } else {
        logger.warn(TAG, `封面上传被拒绝 code=${res.code} message=${res.message ?? ''}`)
        showToastSafety(res.message, DiningText.dining_create_cover_failed)
      }
    } catch (err) {
      logger.error(TAG, '封面上传流程异常', err instanceof Error ? err.message : '')
      showToastSafety('', DiningText.dining_create_cover_failed)
    }
    this.coverUploading = false
  }

  /** 提交：端上校验先行 → 新增（source=USER 直上架由后端写入） */
  private async submit(): Promise<void> {
    if (this.submitting) {
      return
    }
    const ingredients = this.ingredientRows.map((row: IngredientRow): string => row.value)
    const validation = validateDiningRecipeForm(this.nameInput, ingredients, this.stepsInput)
    if (!validation.valid) {
      showToastSafety(this.recipeErrorText(validation.error))
      return
    }
    this.submitting = true
    try {
      const form: AddDiningRecipeForm = {
        name: validation.name,
        categoryId: this.selectedCategoryId,
        ingredients: validation.ingredients,
        steps: validation.steps,
        coverObjectKey: this.coverObjectKey.length > 0 ? this.coverObjectKey : null
      }
      const res = await addDiningRecipe(form)
      if (res.code === BizCode.SUCCESS) {
        logger.info(TAG, `菜谱新增成功 name=${validation.name}`)
        showToastSafety(DiningText.dining_create_submit_success)
        pathStack.pop()
      } else {
        logger.warn(TAG, `菜谱新增未生效 code=${res.code} message=${res.message ?? ''}`)
        showToastSafety(res.message, DiningText.dining_recipe_load_failed)
      }
    } catch (err) {
      logger.error(TAG, '菜谱新增异常', err instanceof Error ? err.message : '')
      showToastSafety('', DiningText.dining_recipe_load_failed)
    }
    this.submitting = false
  }

  /** 菜谱表单错误 → 文案 */
  private recipeErrorText(error: DiningRecipeFormError | null): string {
    if (error === DiningRecipeFormError.NAME_REQUIRED) {
      return DiningText.dining_form_error_name_required
    }
    if (error === DiningRecipeFormError.NAME_TOO_LONG) {
      return DiningText.dining_form_error_name_too_long
    }
    if (error === DiningRecipeFormError.INGREDIENT_EMPTY) {
      return DiningText.dining_form_error_ingredient_empty
    }
    if (error === DiningRecipeFormError.INGREDIENT_TOO_LONG) {
      return DiningText.dining_form_error_ingredient_too_long
    }
    if (error === DiningRecipeFormError.INGREDIENT_TOO_MANY) {
      return DiningText.dining_form_error_ingredient_too_many
    }
    if (error === DiningRecipeFormError.STEPS_TOO_LONG) {
      return DiningText.dining_form_error_steps_too_long
    }
    return DiningText.dining_create_cover_failed
  }
}

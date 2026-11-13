// 新建物品标签页（一物一码 M2）— 对齐安卓 feature/toolbox/ui/assettag/AssetTagCreatePage.kt
//
// 表单：物品名称（必填）+ 物品描述/联系方式（可选）+ 自定义标签码（可选）。
// 联系方式隐私语义在 helper 说明；成功后 toast + 置变更信号 + 返回列表（列表 onShown 重拉）。

import { HttpStatusCode } from '@ohos/axios'
import { DesignDimens, DesignFontSize, DesignFontWeight } from '../../constant/DesignSystem'
import { createAssetTag, ToolboxText } from './api'
import { markAssetTagChanged } from './ToolboxChangeSignal'
import { AppButton } from '../../view/common/AppComponents'
import { logger } from '../../utils/Logger'
import { pathStack } from '../../utils/PathStackUtil'
import { showToastSafety } from '../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../utils/SafeArea'
import { ThemeRefresh } from '../../view/common/ThemeRefresh'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'toolboxAssetTagCreate'

@Builder
export function ToolboxAssetTagCreateBuilder() {
  ToolboxAssetTagCreate()
}

@Component
struct ToolboxAssetTagCreate {
  @State itemName: string = ""
  @State itemDesc: string = ""
  @State contact: string = ""
  @State customCode: string = ""
  @State submitting: boolean = false
  @State errorMessage: string = ""
  @State itemNameFocused: boolean = false
  @State itemDescFocused: boolean = false
  @State contactFocused: boolean = false
  @State customCodeFocused: boolean = false

  /** 上限常量（对齐后端 @Size） */
  private readonly itemNameMax: number = 64
  private readonly itemDescMax: number = 256
  private readonly contactMax: number = 64

  async submit(): Promise<void> {
    if (this.submitting) {
      logger.debug(TAG, '提交进行中，忽略重复触发')
      return
    }
    const name = this.itemName.trim()
    if (name.length === 0 || name.length > this.itemNameMax) {
      logger.warn(TAG, '创建校验拒绝: 物品名称非法')
      this.errorMessage = ToolboxText.asset_tag_error_item_name
      return
    }
    if (this.contact.length > this.contactMax) {
      logger.warn(TAG, '创建校验拒绝: 联系方式超长')
      this.errorMessage = ToolboxText.asset_tag_error_contact
      return
    }
    const custom = this.customCode.trim()
    if (custom.length > 0 && !/^[0-9a-zA-Z-_]{4,16}$/.test(custom)) {
      logger.warn(TAG, '创建校验拒绝: 自定义标签码非法')
      this.errorMessage = ToolboxText.toolbox_error_invalid_custom_code
      return
    }

    this.errorMessage = ""
    this.submitting = true
    try {
      const res = await createAssetTag({
        itemName: name,
        itemDesc: this.itemDesc.trim().length > 0 ? this.itemDesc.trim() : undefined,
        // 联系方式明文仅 HTTPS 上行，服务端加密落库
        contact: this.contact.trim().length > 0 ? this.contact.trim() : undefined,
        customCode: custom.length > 0 ? custom : undefined
      })
      if (res.code === HttpStatusCode.Ok) {
        logger.info(TAG, `标签创建成功 code=${res.data?.code ?? ''}`)
        showToastSafety(ToolboxText.asset_tag_toast_create_success)
        markAssetTagChanged()
        pathStack.pop()
      } else {
        logger.warn(TAG, `创建被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.errorMessage = res.message ?? ToolboxText.asset_tag_error_submit
      }
    } catch (err) {
      logger.error(TAG, '创建请求异常', err instanceof Error ? err.message : '')
      this.errorMessage = ToolboxText.asset_tag_error_submit
    } finally {
      this.submitting = false
    }
  }

  /** 表单字段标签（对齐安卓 FormLabel labelMedium 档） */
  @Builder
  FormLabel(text: string) {
    Text(text)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor($r('app.color.TEXT_SECONDARY'))
      .margin({ bottom: DesignDimens.SPACE_XS })
  }

  /** 输入框（对齐安卓 tokenTextFieldColors：surfaceVariant 底 + outline 描边） */
  @Builder
  FormInput(text: string, focused: boolean, maxLength: number, placeholder: string, onChange: (v: string) => void) {
    TextInput({ text: text, placeholder: placeholder })
      .height(48)
      .fontSize(DesignFontSize.BODY)
      .backgroundColor($r('app.color.SURFACE_VARIANT'))
      .borderRadius(DesignDimens.RADIUS_MD)
      .borderWidth(1)
      .borderColor(focused ? $r('app.color.PRIMARY') : $r('app.color.OUTLINE'))
      .placeholderColor($r('app.color.TEXT_TERTIARY'))
      .placeholderFont({ size: DesignFontSize.BODY_SMALL })
      .maxLength(maxLength)
      .onChange((value: string) => {
        onChange(value)
      })
  }

  build() {
    NavDestination() {
      ThemeRefresh() {
        Scroll() {
          Column() {
            // 页面标题（对齐安卓 PageHeader）
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

              Text(ToolboxText.asset_tag_create_title)
                .fontSize(DesignFontSize.ROOT_TITLE)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor($r('app.color.TEXT_PRIMARY'))
            }
            .width('100%')
            .height(56)
            .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
            .alignItems(VerticalAlign.Center)

            // 表单卡（对齐安卓 GroupCard）
            Column() {
              this.FormLabel(ToolboxText.asset_tag_item_name_label)
              this.FormInput(this.itemName, this.itemNameFocused, this.itemNameMax,
                ToolboxText.asset_tag_item_name_hint, (v: string) => {
                this.itemName = v
              })

              Column().height(DesignDimens.SPACE_LG)

              this.FormLabel(ToolboxText.asset_tag_item_desc_label)
              this.FormInput(this.itemDesc, this.itemDescFocused, this.itemDescMax,
                ToolboxText.asset_tag_item_desc_hint, (v: string) => {
                this.itemDesc = v
              })

              Column().height(DesignDimens.SPACE_LG)

              this.FormLabel(ToolboxText.asset_tag_contact_label)
              this.FormInput(this.contact, this.contactFocused, this.contactMax,
                ToolboxText.asset_tag_contact_hint, (v: string) => {
                this.contact = v
              })
              Text(ToolboxText.asset_tag_contact_desc)
                .fontSize(DesignFontSize.CAPTION)
                .fontWeight(DesignFontWeight.MEDIUM)
                .fontColor($r('app.color.TEXT_SECONDARY'))
                .margin({ top: DesignDimens.SPACE_XXS })

              Column().height(DesignDimens.SPACE_LG)

              this.FormLabel(ToolboxText.asset_tag_custom_code_label)
              this.FormInput(this.customCode, this.customCodeFocused, 16,
                ToolboxText.toolbox_custom_code_hint, (v: string) => {
                this.customCode = v
              })
            }
            .width('100%')
            .alignItems(HorizontalAlign.Start)
            .padding(DesignDimens.CARD_PADDING)
            .backgroundColor($r('app.color.SURFACE'))
            .borderRadius(DesignDimens.CARD_RADIUS)
            .border({ width: 1, color: $r('app.color.OUTLINE') })

            if (this.errorMessage.length > 0) {
              Text(this.errorMessage)
                .fontSize(DesignFontSize.CAPTION)
                .fontColor($r('app.color.ERROR'))
                .width('100%')
                .margin({ top: DesignDimens.SPACE_SM })
            }

            Column().height(DesignDimens.SPACE_LG)

            AppButton({
              text: ToolboxText.asset_tag_submit_create,
              loading: this.submitting,
              click: () => {
                this.submit()
              }
            })
          }
          .width('100%')
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_XS,
            bottom: DesignDimens.SPACE_XL + safeBottomVp()
          })
          .alignItems(HorizontalAlign.Start)
        }
        .scrollBar(BarState.Off)
        .width('100%')
        .height('100%')
        .align(Alignment.TopStart)
        .padding({ top: safeTopVp() })
        .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
  }
}

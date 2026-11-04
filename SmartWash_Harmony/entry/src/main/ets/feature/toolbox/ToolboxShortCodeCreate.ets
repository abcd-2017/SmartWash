// 新建短链页 — 对齐安卓 feature/toolbox/ui/create/ShortCodeCreatePage.kt
//
// 表单：目标链接（必填 http/https）+ 自定义短码（可选 4-16 位）+ 可见性（默认公开）+
// 有效期预设（永久/7天/30天/自定义日期）。
// 校验只在提交时内联展示（规范 §3.7 基线⑤）；成功后 toast + 置变更信号 + 返回列表（列表 onShown 重拉）。

import { HttpStatusCode } from '@ohos/axios'
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from '../../constant/DesignSystem'
import {
  AddShortCodeForm,
  createShortCode,
  ToolboxText,
  VISIBILITY_PRIVATE,
  VISIBILITY_PUBLIC
} from './api'
import { ExpiryPreset, expireAtFor, isValidCustomCode, isValidTarget } from './ShortCodeRules'
import { markShortCodeChanged } from './ToolboxChangeSignal'
import { ToolboxDatePickerDialog } from './ToolboxDatePickerDialog'
import { AppButton, AppButtonVariant } from '../../view/common/AppComponents'
import { logger } from '../../utils/Logger'
import { pathStack } from '../../utils/PathStackUtil'
import { showToastSafety } from '../../utils/ToastUtil'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'toolboxShortCodeCreate'

@Builder
export function ToolboxShortCodeCreateBuilder() {
  ToolboxShortCodeCreate()
}

@Component
struct ToolboxShortCodeCreate {
  @State target: string = ""
  @State customCode: string = ""
  @State isPublic: boolean = true
  @State preset: ExpiryPreset = ExpiryPreset.PERMANENT
  // 自定义过期日期「yyyy-MM-dd」本地日期串；null 表示未选
  @State customDate: string | null = null
  @State submitting: boolean = false
  // 提交错误：仅在点击提交后展示（对齐安卓 submitState.Error 内联展示）
  @State errorMessage: string = ""
  // 输入框聚焦态（对齐安卓 focusedBorderColor = primary）
  @State targetFocused: boolean = false
  @State customCodeFocused: boolean = false
  // 预设 chip 按压态（对齐安卓 pressable scale 0.97）
  @State pressedChipId: number = -1

  /**
   * 打开自定义日期弹窗（对齐安卓 ToolboxDatePickerDialog）。
   * 每次打开新建控制器：CustomDialogController 的 builder 捕获的是创建时的入参，
   * 复用同一控制器会让 initialDate 停留在首次创建时的值。
   */
  private openDatePicker(): void {
    const controller: CustomDialogController = new CustomDialogController({
      builder: ToolboxDatePickerDialog({
        initialDate: this.customDate ?? "",
        title: ToolboxText.toolbox_expiry_custom_pick,
        onConfirm: (date: string): void => {
          this.customDate = date
        },
        onDismiss: (): void => {
        }
      }),
      autoCancel: true,
      alignment: DialogAlignment.Center,
      customStyle: true
    })
    controller.open()
  }

  /**
   * 提交创建。客户端前置校验失败不触网；业务错误保留后端 message 由页面内联展示。
   * 校验顺序对齐安卓 ShortCodeCreateViewModel.submit：target → customCode → 自定义日期。
   */
  async submit(): Promise<void> {
    if (this.submitting) {
      logger.debug(TAG, '提交进行中，忽略重复触发')
      return
    }
    const code = this.customCode.trim()
    if (!isValidTarget(this.target)) {
      logger.warn(TAG, '创建校验拒绝: 目标链接格式不合法')
      this.errorMessage = ToolboxText.toolbox_error_invalid_target
      return
    }
    if (code.length > 0 && !isValidCustomCode(code)) {
      logger.warn(TAG, '创建校验拒绝: 自定义短码格式不合法')
      this.errorMessage = ToolboxText.toolbox_error_invalid_custom_code
      return
    }
    if (this.preset === ExpiryPreset.CUSTOM &&
      (this.customDate === null || this.customDate.length === 0)) {
      logger.warn(TAG, '创建校验拒绝: 自定义有效期未选日期')
      this.errorMessage = ToolboxText.toolbox_error_custom_date_required
      return
    }

    const form: AddShortCodeForm = {
      target: this.target.trim(),
      customCode: code.length > 0 ? code : undefined,
      expireAt: expireAtFor(this.preset, this.customDate, new Date()) ?? undefined,
      isPublic: this.isPublic ? VISIBILITY_PUBLIC : VISIBILITY_PRIVATE
    }

    this.errorMessage = ""
    this.submitting = true
    try {
      const res = await createShortCode(form)
      if (res.code === HttpStatusCode.Ok) {
        logger.info(TAG, `短链创建成功 code=${res.data?.code ?? ''} 公开=${this.isPublic}`)
        showToastSafety(ToolboxText.toolbox_toast_create_success)
        // 置变更信号：列表 onShown 据此重拉（对齐安卓 notifyShortCodeChanged）
        markShortCodeChanged()
        pathStack.pop()
      } else {
        logger.warn(TAG, `创建被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.errorMessage = res.message ?? ToolboxText.toolbox_error_submit
      }
    } catch (err) {
      logger.error(TAG, '创建请求异常', err instanceof Error ? err.message : '')
      this.errorMessage = ToolboxText.toolbox_error_submit
    } finally {
      this.submitting = false
    }
  }

  /** 表单字段标签（13fp 次文本，规范 §3.4 副文案层级） */
  @Builder
  FormLabel(text: string) {
    Text(text)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(DesignColor.TEXT_SECONDARY)
      .margin({ bottom: DesignDimens.SPACE_XS })
  }

  /** 可选预设 chip（选中 = 品牌底白字，未选中 = 白底描边；对齐安卓 ToolboxSelectableChip） */
  @Builder
  SelectableChip(text: string, selected: boolean, chipId: number) {
    Text(text)
      .fontSize(DesignFontSize.CAPTION)
      // 字重恒 SemiBold：字重随选中切换会让文字宽度跳变（对齐安卓注释）
      .fontWeight(DesignFontWeight.SEMI_BOLD)
      .fontColor(selected ? DesignColor.ON_PRIMARY : DesignColor.TEXT_SECONDARY)
      .padding({ left: 16, right: 16, top: 6, bottom: 6 })
      .backgroundColor(selected ? DesignColor.PRIMARY : DesignColor.SURFACE)
      .borderWidth(1)
      .borderColor(selected ? DesignColor.PRIMARY : DesignColor.OUTLINE)
      .borderRadius(DesignDimens.RADIUS_FULL)
      .scale({ x: this.pressedChipId === chipId ? 0.97 : 1, y: this.pressedChipId === chipId ? 0.97 : 1 })
      .onTouch((event: TouchEvent) => {
        if (event.type === TouchType.Down) {
          this.pressedChipId = chipId
        } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
          this.pressedChipId = -1
        }
      })
      .onClick(() => {
        this.onChipSelect(chipId)
      })
      // 200ms 颜色过渡（对齐安卓 selectedColor）
      .animation({ duration: 200, curve: Curve.EaseInOut })
  }

  /** chip 点击分流：0/1 为可见性（公开/私有），2..5 为有效期预设（永久/7天/30天/自定义） */
  private onChipSelect(chipId: number): void {
    if (chipId === 0) {
      this.isPublic = true
    } else if (chipId === 1) {
      this.isPublic = false
    } else if (chipId === 2) {
      this.preset = ExpiryPreset.PERMANENT
    } else if (chipId === 3) {
      this.preset = ExpiryPreset.DAYS_7
    } else if (chipId === 4) {
      this.preset = ExpiryPreset.DAYS_30
    } else {
      this.preset = ExpiryPreset.CUSTOM
    }
  }

  build() {
    NavDestination() {
      Scroll() {
        Column() {
          // 页面标题（对齐安卓 PageHeader：标题 22/600 + 48 热区返回）
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
            .onClick(() => {
              pathStack.pop()
            })

            Text(ToolboxText.toolbox_create_title)
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .margin({ left: 4 })
          }
          .width('100%')
          .height(56)
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
          .alignItems(VerticalAlign.Center)

          // 表单卡（对齐安卓 GroupCard）
          Column() {
            // ---------- 目标链接 ----------
            this.FormLabel(ToolboxText.toolbox_target_label)
            TextInput({ text: this.target, placeholder: ToolboxText.toolbox_target_hint })
              .height(48)
              .fontSize(DesignFontSize.BODY)
              .backgroundColor(DesignColor.SURFACE_VARIANT)
              .borderRadius(DesignDimens.INPUT_RADIUS)
              .borderWidth(1)
              .borderColor(this.targetFocused ? DesignColor.PRIMARY : DesignColor.OUTLINE)
              .placeholderColor(DesignColor.TEXT_TERTIARY)
              .placeholderFont({ size: DesignFontSize.BODY_SMALL })
              .onFocus(() => {
                this.targetFocused = true
              })
              .onBlur(() => {
                this.targetFocused = false
              })
              .onChange((value: string) => {
                this.target = value
              })
            Text(ToolboxText.toolbox_target_helper)
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(DesignFontWeight.MEDIUM)
              .fontColor(DesignColor.TEXT_TERTIARY)
              .margin({ top: 6 })

            Column()
              .height(DesignDimens.SPACE_LG)

            // ---------- 自定义短码（可选） ----------
            this.FormLabel(ToolboxText.toolbox_custom_code_label)
            TextInput({ text: this.customCode, placeholder: ToolboxText.toolbox_custom_code_hint })
              .height(48)
              .fontSize(DesignFontSize.BODY)
              .backgroundColor(DesignColor.SURFACE_VARIANT)
              .borderRadius(DesignDimens.INPUT_RADIUS)
              .borderWidth(1)
              .borderColor(this.customCodeFocused ? DesignColor.PRIMARY : DesignColor.OUTLINE)
              .placeholderColor(DesignColor.TEXT_TERTIARY)
              .placeholderFont({ size: DesignFontSize.BODY_SMALL })
              .onFocus(() => {
                this.customCodeFocused = true
              })
              .onBlur(() => {
                this.customCodeFocused = false
              })
              .onChange((value: string) => {
                this.customCode = value
              })

            Column()
              .height(DesignDimens.SPACE_LG)

            // ---------- 可见性 ----------
            this.FormLabel(ToolboxText.toolbox_visibility_label)
            Row({ space: DesignDimens.SPACE_XS }) {
              this.SelectableChip(ToolboxText.toolbox_visibility_public_value, this.isPublic, 0)
              this.SelectableChip(ToolboxText.toolbox_visibility_private_value, !this.isPublic, 1)
            }
            Text(this.isPublic ? ToolboxText.toolbox_visibility_public_desc
              : ToolboxText.toolbox_visibility_private_desc)
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(DesignFontWeight.MEDIUM)
              .fontColor(DesignColor.TEXT_TERTIARY)
              .margin({ top: 6 })

            Column()
              .height(DesignDimens.SPACE_LG)

            // ---------- 有效期预设 ----------
            this.FormLabel(ToolboxText.toolbox_expiry_label)
            Row({ space: DesignDimens.SPACE_XS }) {
              this.SelectableChip(ToolboxText.toolbox_expiry_preset_permanent,
                this.preset === ExpiryPreset.PERMANENT, 2)
              this.SelectableChip(ToolboxText.toolbox_expiry_preset_7d,
                this.preset === ExpiryPreset.DAYS_7, 3)
              this.SelectableChip(ToolboxText.toolbox_expiry_preset_30d,
                this.preset === ExpiryPreset.DAYS_30, 4)
              this.SelectableChip(ToolboxText.toolbox_expiry_preset_custom,
                this.preset === ExpiryPreset.CUSTOM, 5)
            }

            if (this.preset === ExpiryPreset.CUSTOM) {
              // 自定义日期按钮：AppButton SECONDARY 胶囊 48（对齐安卓）
              AppButton({
                text: this.customDate ?? ToolboxText.toolbox_expiry_custom_pick,
                variant: AppButtonVariant.SECONDARY,
                click: () => {
                  this.openDatePicker()
                }
              })
              .margin({ top: DesignDimens.SPACE_SM })
            }
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
          .padding(DesignDimens.CARD_PADDING)
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.CARD_RADIUS)
          // 表单卡同标准卡画法（规范 §3.1）：1px OUTLINE 描边 + level1 卡片阴影
          // （卡内输入框/预设 chip 的 1px 描边属控件自身规格，不受此规则约束）
          .border({ width: 1, color: DesignColor.OUTLINE })
          .shadow({
            radius: DesignElevation.LEVEL_1,
            color: DesignColor.SHADOW_COLOR_CARD,
            offsetX: 0,
            offsetY: 2
          })

          // ---------- 提交错误（仅提交后展示，error 色） ----------
          if (this.errorMessage.length > 0) {
            Text(this.errorMessage)
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.ERROR)
              .width('100%')
              .margin({ top: DesignDimens.SPACE_SM })
          }

          Column()
            .height(DesignDimens.SPACE_LG)

          AppButton({
            text: ToolboxText.toolbox_submit_create,
            loading: this.submitting,
            click: () => {
              this.submit()
            }
          })
        }
        .width('100%')
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 8, bottom: 24 })
        .alignItems(HorizontalAlign.Start)
      }
      .scrollBar(BarState.Off)
      .width('100%')
      .height('100%')
      .align(Alignment.TopStart)
      .backgroundColor(DesignColor.BACKGROUND)
      // 键盘避让（对齐安卓 imePadding）：输入框聚焦时页面整体上移，避免被软键盘遮挡
      .expandSafeArea([SafeAreaType.KEYBOARD], [SafeAreaEdge.BOTTOM])
    }
    .hideTitleBar(true)
  }
}

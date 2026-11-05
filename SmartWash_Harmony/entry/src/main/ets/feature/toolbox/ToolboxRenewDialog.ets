// 续期弹窗 — 对齐安卓 feature/toolbox/ui/detail/ShortCodeDetailPage.kt 的 RenewDialog。
//
// 预设仅 7 天 / 30 天 / 自定义日期（无永久选项）：已创建的码不可改回永久
// （后端 UpdateShortCodeFrom 中 expireAt 不传即不修改，无法表达「改为永久」）。
// 自定义日期由父页面另开 ToolboxDatePickerDialog 选择后回填。
//
// 规格：标题 headlineMedium 22/600；预设 chip labelMedium 12/600 + 200ms 颜色过渡 + 按压反馈；
// 自定义日期 AppButton SECONDARY 胶囊 48；底部 DialogAction 48 高（对齐安卓）。

import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight, MotionTiming } from '../../constant/DesignSystem'
import { ExpiryPreset } from './ShortCodeRules'
import { ToolboxText } from './api'
import { AppButton, AppButtonVariant } from '../../view/common/AppComponents'

@CustomDialog
export struct ToolboxRenewDialog {
  // 由父组件经 CustomDialogController 注入（框架约定，不可自赋初值）
  controller: CustomDialogController
  @Prop preset: ExpiryPreset = ExpiryPreset.DAYS_7
  /** 已选自定义日期「yyyy-MM-dd」，空串表示未选 */
  @Prop customDate: string = ""
  @Prop loading: boolean = false
  @State entered: boolean = false
  // 自定义日期段展开态（对齐安卓 AnimatedVisibility expand/fade）
  @State customExpanded: boolean = false
  // 预设 chip 按压态（对齐安卓 pressable scale 0.97）
  @State pressedPreset: ExpiryPreset | null = null
  onPresetChange: (preset: ExpiryPreset) => void = () => {
  }
  onPickDate: () => void = () => {
  }
  onConfirm: () => void = () => {
  }
  onDismiss: () => void = () => {
  }

  aboutToAppear(): void {
    this.customExpanded = this.preset === ExpiryPreset.CUSTOM
  }

  /** 弹窗统一入场（对齐安卓 dialogEnterMotion）：首帧后再触发，弹簧 scale 0.92→1 + fade */
  private playEnterMotion(): void {
    setTimeout(() => {
      animateTo(MotionTiming.DIALOG_ENTER, () => {
        this.entered = true
      })
    }, 16)
  }

  /** 预设 chip（选中 = 品牌底白字，未选中 = 白底描边；对齐安卓 ToolboxSelectableChip） */
  @Builder
  PresetChip(text: string, value: ExpiryPreset) {
    Text(text)
      .fontSize(DesignFontSize.CAPTION)
      // 字重恒 SemiBold：字重随选中切换会让文字宽度跳变（对齐安卓注释）
      .fontWeight(DesignFontWeight.SEMI_BOLD)
      .fontColor(this.preset === value ? DesignColor.ON_PRIMARY : DesignColor.TEXT_SECONDARY)
      .padding({ left: 16, right: 16, top: 6, bottom: 6 })
      .backgroundColor(this.preset === value ? DesignColor.PRIMARY : DesignColor.SURFACE)
      .borderWidth(1)
      .borderColor(this.preset === value ? DesignColor.PRIMARY : DesignColor.OUTLINE)
      .borderRadius(DesignDimens.RADIUS_FULL)
      .scale({ x: this.pressedPreset === value ? 0.97 : 1, y: this.pressedPreset === value ? 0.97 : 1 })
      .onTouch((event: TouchEvent) => {
        if (event.type === TouchType.Down) {
          this.pressedPreset = value
        } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
          this.pressedPreset = null
        }
      })
      .onClick(() => {
        this.onPresetChange(value)
      })
      // 200ms 颜色过渡（对齐安卓 selectedColor）
      .animation({ duration: 200, curve: Curve.EaseInOut })
  }

  build() {
    // 外层留白容器：customStyle 弹窗铺满屏幕宽度，卡片背景画在内层并留左右边距（问题16）
    Column() {
      Column() {
        Text(ToolboxText.toolbox_renew_title)
        .fontSize(DesignFontSize.STATUS_LARGE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .width('100%')
        .margin({ bottom: DesignDimens.SPACE_MD })

      Row({ space: DesignDimens.SPACE_XS }) {
        this.PresetChip(ToolboxText.toolbox_expiry_preset_7d, ExpiryPreset.DAYS_7)
        this.PresetChip(ToolboxText.toolbox_expiry_preset_30d, ExpiryPreset.DAYS_30)
        this.PresetChip(ToolboxText.toolbox_expiry_preset_custom, ExpiryPreset.CUSTOM)
      }
      .width('100%')
      .margin({ top: 4 })

      // 自定义日期段：展开/收起（对齐安卓 AnimatedVisibility expand + fade）
      Column() {
        AppButton({
          text: this.customDate.length > 0 ? this.customDate : ToolboxText.toolbox_expiry_custom_pick,
          variant: AppButtonVariant.SECONDARY,
          click: () => {
            this.onPickDate()
          }
        })
      }
      .width('100%')
      .height(this.customExpanded ? 48 : 0)
      .opacity(this.customExpanded ? 1 : 0)
      .clip(true)
      .margin({ top: this.customExpanded ? DesignDimens.SPACE_SM : 0 })
      .animation({ duration: 200, curve: Curve.EaseInOut })

      Divider()
        .strokeWidth(0.5)
        .color(DesignColor.DIVIDER)
        .width('100%')
        .margin({ top: DesignDimens.SPACE_MD })

      Row() {
        Button(ToolboxText.toolbox_date_cancel)
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

        Button(ToolboxText.toolbox_date_confirm)
          .type(ButtonType.Normal)
          .layoutWeight(1)
          .height(48)
          .backgroundColor(Color.Transparent)
          .fontColor(DesignColor.PRIMARY)
          .fontSize(DesignFontSize.BODY)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .borderRadius(0)
          .enabled(!this.loading)
          .opacity(this.loading ? 0.5 : 1)
          .onClick(() => {
            if (!this.loading) {
              this.onConfirm()
            }
          })
      }
      .width('100%')
      }
      .width('100%')
      // 对齐安卓 RenewDialog（vertical spaceLg 20）；横向留白由外层容器统一给出
      .padding({ top: DesignDimens.SPACE_LG, bottom: DesignDimens.SPACE_LG })
      .backgroundColor(DesignColor.SURFACE)
      // 弹窗圆角收敛到 radiusLg 16（安卓弹窗统一档；原 RADIUS_XL 20 属 hero/大容器档）
      .borderRadius(DesignDimens.CARD_RADIUS)
      .shadow({
        radius: DesignElevation.FLOAT_SHADOW_RADIUS,
        color: DesignColor.SHADOW_COLOR_FLOAT,
        offsetX: 0,
        offsetY: DesignElevation.FLOAT_SHADOW_OFFSET
      })
      .scale({ x: this.entered ? 1 : 0.92, y: this.entered ? 1 : 0.92 })
      .opacity(this.entered ? 1 : 0)
      .onAppear(() => {
        this.playEnterMotion()
      })
    }
    .width('100%')
    .justifyContent(FlexAlign.Center)
    .padding({ left: DesignDimens.SPACE_XL, right: DesignDimens.SPACE_XL })
  }
}

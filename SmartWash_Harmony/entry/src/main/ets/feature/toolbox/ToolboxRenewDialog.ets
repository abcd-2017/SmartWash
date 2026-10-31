// 续期弹窗 — 对齐安卓 feature/toolbox/ui/detail/ShortCodeDetailPage.kt 的 RenewDialog。
//
// 预设仅 7 天 / 30 天 / 自定义日期（无永久选项）：已创建的码不可改回永久
// （后端 UpdateShortCodeFrom 中 expireAt 不传即不修改，无法表达「改为永久」）。
// 自定义日期由父页面另开 ToolboxDatePickerDialog 选择后回填。

import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight, MotionTiming } from '../../constant/DesignSystem'
import { ExpiryPreset } from './ShortCodeRules'
import { ToolboxText } from './api'

@CustomDialog
export struct ToolboxRenewDialog {
  // 由父组件经 CustomDialogController 注入（框架约定，不可自赋初值）
  controller: CustomDialogController
  @Prop preset: ExpiryPreset = ExpiryPreset.DAYS_7
  /** 已选自定义日期「yyyy-MM-dd」，空串表示未选 */
  @Prop customDate: string = ""
  @Prop loading: boolean = false
  @State entered: boolean = false
  onPresetChange: (preset: ExpiryPreset) => void = () => {
  }
  onPickDate: () => void = () => {
  }
  onConfirm: () => void = () => {
  }
  onDismiss: () => void = () => {
  }

  /** 弹窗统一入场（对齐安卓 dialogEnterMotion）：首帧后再触发，弹簧 scale 0.92→1 + fade */
  private playEnterMotion(): void {
    setTimeout(() => {
      animateTo(MotionTiming.DIALOG_ENTER, () => {
        this.entered = true
      })
    }, 16)
  }

  /** 预设 chip（选中 = 品牌底白字，未选中 = 白底描边） */
  @Builder
  PresetChip(text: string, value: ExpiryPreset) {
    Text(text)
      .fontSize(DesignFontSize.BODY)
      .fontWeight(this.preset === value ? DesignFontWeight.SEMI_BOLD : DesignFontWeight.MEDIUM)
      .fontColor(this.preset === value ? DesignColor.ON_PRIMARY : DesignColor.TEXT_SECONDARY)
      .padding({ left: 16, right: 16, top: 6, bottom: 6 })
      .backgroundColor(this.preset === value ? DesignColor.PRIMARY : DesignColor.SURFACE)
      .borderWidth(this.preset === value ? 0 : 1)
      .borderColor(DesignColor.OUTLINE)
      .borderRadius(DesignDimens.RADIUS_FULL)
      .onClick(() => {
        this.onPresetChange(value)
      })
  }

  build() {
    Column() {
      Text(ToolboxText.toolbox_renew_title)
        .fontSize(DesignFontSize.SECTION_TITLE)
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

      if (this.preset === ExpiryPreset.CUSTOM) {
        Button(this.customDate.length > 0 ? this.customDate : ToolboxText.toolbox_expiry_custom_pick)
          .type(ButtonType.Capsule)
          .height(44)
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .backgroundColor(DesignColor.SURFACE)
          .borderWidth(1)
          .borderColor(DesignColor.OUTLINE)
          .width('100%')
          .margin({ top: DesignDimens.SPACE_SM })
          .onClick(() => {
            this.onPickDate()
          })
      }

      Divider()
        .strokeWidth(0.5)
        .color(DesignColor.DIVIDER)
        .width('100%')
        .margin({ top: DesignDimens.SPACE_MD })

      Row() {
        Button("取消")
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

        Button("确定")
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
    .padding(20)
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
}

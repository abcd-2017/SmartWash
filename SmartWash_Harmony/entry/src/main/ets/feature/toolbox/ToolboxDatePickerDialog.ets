// 自定义过期日期选择弹窗 — 对齐安卓 feature/toolbox/ui/ToolboxUiCommon.kt 的 ToolboxDatePickerDialog。
//
// 鸿蒙 SDK 6.1.1 已移除 DatePicker 的 bindDatePickerDialog，而 DatePickerDialog.show 在 API 18+ 标记废弃；
// 为保证在目标 API 17 上可用且不依赖被移除的静态弹窗 API，这里用 CustomDialogController + 内联 DatePicker 自绘
// （标题 + 年/月/日三列 + 确定/取消），与安卓 Material3 DatePickerDialog 交互等价。
//
// 选中结果以「yyyy-MM-dd」本地日期串回传（Android 侧 DatePicker 按 UTC 取整日、这里按本地时间取整日，
// 二者对「用户选中的那一天」语义一致；短链过期时刻由 ShortCodeRules.expireAtFor 补 23:59:59）。

import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight, MotionTiming } from '../../constant/DesignSystem'
import { formatDate } from './ShortCodeRules'

@CustomDialog
export struct ToolboxDatePickerDialog {
  // 由父组件经 CustomDialogController 注入（框架约定，不可自赋初值）
  controller: CustomDialogController
  /** 已选日期「yyyy-MM-dd」，空串表示未选（默认今天） */
  @Prop initialDate: string = ""
  @Prop title: string = "选择过期日期"
  @State selected: Date = new Date()
  @State entered: boolean = false
  onConfirm: (date: string) => void = () => {
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

  aboutToAppear(): void {
    if (this.initialDate.length >= 10) {
      const year = Number(this.initialDate.substring(0, 4))
      const month = Number(this.initialDate.substring(5, 7))
      const day = Number(this.initialDate.substring(8, 10))
      this.selected = new Date(year, month - 1, day)
    }
  }

  build() {
    Column() {
      Text(this.title)
        .fontSize(DesignFontSize.SECTION_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .width('100%')
        .margin({ bottom: 12 })

      // 年/月/日三列选择（showTime 语义由组件默认 DATE 模式保证，不选时分秒）
      DatePicker({ selected: this.selected })
        .onDateChange((value: Date) => {
          this.selected = value
        })
        .height(200)

      Row() {
        Button("取消")
          .type(ButtonType.Normal)
          .layoutWeight(1)
          .height(44)
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
          .height(44)

        Button("确定")
          .type(ButtonType.Normal)
          .layoutWeight(1)
          .height(44)
          .backgroundColor(Color.Transparent)
          .fontColor(DesignColor.PRIMARY)
          .fontSize(DesignFontSize.BODY)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .borderRadius(0)
          .onClick(() => {
            this.onConfirm(formatDate(this.selected))
            this.controller.close()
          })
      }
      .width('100%')
      .margin({ top: 8 })
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

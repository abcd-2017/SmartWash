// 续期弹窗 — 对齐安卓 feature/toolbox/ui/detail/ShortCodeDetailPage.kt 的 RenewDialog。
//
// 预设仅 7 天 / 30 天 / 自定义日期（无永久选项）：已创建的码不可改回永久
// （后端 UpdateShortCodeFrom 中 expireAt 不传即不修改，无法表达「改为永久」）。
//
// 规格：标题 headlineMedium 22/600；预设 chip labelMedium 12/600 + 200ms 颜色过渡 + 按压反馈；
// 自定义日期 AppButton SECONDARY 胶囊 48；底部 DialogAction 48 高（对齐安卓）。
//
// 响应式说明（2026-11-06）：@CustomDialog 内容实例由控制器在 open() 时构建，父组件状态变化
// 不会重建本弹窗，@Prop 与按值传参的 @Builder 都不会刷新（实测点「自定义」后 chip 选中态不动、
// 日期按钮不出现）。故弹窗内部改用 @State 承载当前预设/自定义日期：
// - 父组件经 @Prop 传入初始值（aboutToAppear 同步到 @State）；
// - chip 点击更新 @State 并经 onPresetChange 回写父组件（供 doRenew 读取）；
// - 预设 chip 用 @ComponentV2 + @Param（@Builder 按值传参在状态变化时定格，不能承载选中态）；
// - 自定义日期选择器由父组件持有（@CustomDialog 内不能再挂 CustomDialogController，实测不弹出）：
//   点日期按钮经 onPickDate 把「选中回填回调」交给父组件，父组件在日期弹窗确定时回调本弹窗，
//   更新内部 @State（可见回填）并经 onDatePicked 回写父组件（供 doRenew 读取）。

import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight, MotionTiming } from '../../constant/DesignSystem'
import { ExpiryPreset } from './ShortCodeRules'
import { ToolboxText } from './api'
import { AppButton, AppButtonVariant } from '../../view/common/AppComponents'

/**
 * 续期预设 chip（选中 = 品牌底白字，未选中 = 白底描边；对齐安卓 ToolboxSelectableChip）。
 * 用 @ComponentV2 + @Param 承载选中态：@Builder 按值传参在状态变化时定格（已知坑）。
 * 垂直内边距 6 → 17：可点区即 Text，原热区仅 27.7vp，抬到 ≥48vp
 * （与创建页 SelectableChip 一致；写法同投递页 DeliveryDurationChip）。
 */
@ComponentV2
export struct RenewPresetChip {
  @Param text: string = ''
  @Param selected: boolean = false
  @Event onSelect: () => void = () => {
  }
  @Local pressed: boolean = false

  build() {
    Text(this.text)
      .fontSize(DesignFontSize.CAPTION)
      // 字重恒 SemiBold：字重随选中切换会让文字宽度跳变（对齐安卓注释）
      .fontWeight(DesignFontWeight.SEMI_BOLD)
      .fontColor(this.selected ? DesignColor.ON_PRIMARY : DesignColor.TEXT_SECONDARY)
      // chip 内含数字（「7 天」等）：tnum 数字等宽（规范 §2.2，与创建页 chip 同款）
      .fontFeature(DesignFontFeature.TNUM)
      .padding({ left: 16, right: 16, top: 17, bottom: 17 })
      .backgroundColor(this.selected ? DesignColor.PRIMARY : DesignColor.SURFACE)
      .borderWidth(1)
      .borderColor(this.selected ? DesignColor.PRIMARY : DesignColor.OUTLINE)
      .borderRadius(DesignDimens.RADIUS_FULL)
      .scale({ x: this.pressed ? 0.97 : 1, y: this.pressed ? 0.97 : 1 })
      .onTouch((event: TouchEvent) => {
        if (event.type === TouchType.Down) {
          this.pressed = true
        } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
          this.pressed = false
        }
      })
      .onClick(() => {
        this.onSelect()
      })
      // 200ms 颜色过渡（对齐安卓 selectedColor）
      .animation({ duration: 200, curve: Curve.EaseInOut })
  }
}

@CustomDialog
export struct ToolboxRenewDialog {
  // 由父组件经 CustomDialogController 注入（框架约定，不可自赋初值）
  controller: CustomDialogController
  /** 初始预设（父组件打开时传入，aboutToAppear 同步到 currentPreset） */
  @Prop preset: ExpiryPreset = ExpiryPreset.DAYS_7
  /** 初始自定义日期「yyyy-MM-dd」，空串表示未选（aboutToAppear 同步到 currentCustomDate） */
  @Prop customDate: string = ""
  @Prop loading: boolean = false
  // 内部响应式状态（父组件状态变化不重建弹窗，故 @Prop 仅作初始值）
  @State currentPreset: ExpiryPreset = ExpiryPreset.DAYS_7
  @State currentCustomDate: string = ""
  @State entered: boolean = false
  // 自定义日期段展开态（对齐安卓 AnimatedVisibility expand/fade）
  @State customExpanded: boolean = false
  onPresetChange: (preset: ExpiryPreset) => void = () => {
  }
  /** 打开父组件持有的日期选择器；父组件在选定后回调 onPicked 以回填本弹窗 */
  onPickDate: (onPicked: (date: string) => void) => void = () => {
  }
  onDatePicked: (date: string) => void = () => {
  }
  onConfirm: () => void = () => {
  }
  onDismiss: () => void = () => {
  }

  aboutToAppear(): void {
    this.currentPreset = this.preset
    this.currentCustomDate = this.customDate
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

  /** 预设 chip 点击：更新内部选中态（驱动 chip 视觉 + 自定义段展开）并回写父组件 */
  private selectPreset(value: ExpiryPreset): void {
    this.currentPreset = value
    this.customExpanded = value === ExpiryPreset.CUSTOM
    this.onPresetChange(value)
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
          RenewPresetChip({
            text: ToolboxText.toolbox_expiry_preset_7d,
            selected: this.currentPreset === ExpiryPreset.DAYS_7,
            onSelect: (): void => {
              this.selectPreset(ExpiryPreset.DAYS_7)
            }
          })
          RenewPresetChip({
            text: ToolboxText.toolbox_expiry_preset_30d,
            selected: this.currentPreset === ExpiryPreset.DAYS_30,
            onSelect: (): void => {
              this.selectPreset(ExpiryPreset.DAYS_30)
            }
          })
          RenewPresetChip({
            text: ToolboxText.toolbox_expiry_preset_custom,
            selected: this.currentPreset === ExpiryPreset.CUSTOM,
            onSelect: (): void => {
              this.selectPreset(ExpiryPreset.CUSTOM)
            }
          })
        }
        .width('100%')
        .margin({ top: 4 })

        // 自定义日期段：展开/收起（对齐安卓 AnimatedVisibility expand + fade）
        Column() {
          AppButton({
            text: this.currentCustomDate.length > 0 ? this.currentCustomDate : ToolboxText.toolbox_expiry_custom_pick,
            variant: AppButtonVariant.SECONDARY,
            click: () => {
              // 把回填回调交给父组件的日期弹窗，选定后更新内部状态（可见回填）
              this.onPickDate((date: string): void => {
                this.currentCustomDate = date
                this.onDatePicked(date)
              })
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
            // 确认键小字绿走 TEXT_GREEN 深档（PRIMARY 3.4:1 不达标，§10，对齐安卓 DialogAction）
            .fontColor(DesignColor.TEXT_GREEN)
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

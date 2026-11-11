// AI 助手页（端上占位）— 对齐安卓 app 壳层 ui/page/ai/AiWorkPage.kt
//
// 后端现状：SmartWash 后端无 ai/chat 相关 controller（已核对 controller 目录），
// 故本页为纯 UI 占位 + 明确提示，不伪造接口：
// - 快捷任务卡 / 发送按钮只弹「AI 能力正在接入中」提示，不发起任何请求；
// - 输入内容仅在本地暂存，不上传。
// 待后端提供对话接口后，按四端联动规则补 network/api/AiApi.ets 与真实消息流。

import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from '../constant/DesignSystem'
import { DrumMark, PageHeader } from '../view/common/AppComponents'
import { logger } from '../utils/Logger'
import { pathStack } from '../utils/PathStackUtil'
import { showToastSafety } from '../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../utils/SafeArea'
import { ThemeRefresh } from '../view/common/ThemeRefresh'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'aiWork'

/**
 * AI 页文案常量表（参照 feature/toolbox/api.ets 的 ToolboxText 模式），
 * 值与安卓 app strings.xml 的 ai_* 键逐条对齐；AI 属壳层页面，暂随页面存放。
 */
export class AiText {
  static readonly ai_workbench_title: string = "智能助手"
  static readonly ai_quick_tasks: string = "快捷任务"
  static readonly ai_task_choose_package: string = "洗护咨询"
  static readonly ai_task_check_order: string = "订单查询"
  static readonly ai_task_use_coupon: string = "优惠券"
  static readonly ai_task_recharge: string = "费用估算"
  static readonly ai_recent_work: string = "最近工作"
  static readonly ai_empty_recent: string = "还没有对话记录，从上面选一个任务开始"
  static readonly ai_start_asking: string = "开始提问"
  static readonly ai_input_placeholder: string = "有什么可以帮你的？"
  // 发送钮为图标语义（安卓侧作 contentDescription），端上暂无文字位，键保留备查
  static readonly ai_send: string = "发送"
}

/** 快捷任务（对齐安卓 AiWorkPage 的 QuickTaskCard 四项） */
interface QuickTask {
  label: string
  icon: Resource
  containerColor: number
  iconColor: number
}

const QUICK_TASKS: QuickTask[] = [
  {
    label: AiText.ai_task_choose_package,
    icon: $r('sys.symbol.laundry'),
    containerColor: DesignColor.ICON_CONTAINER_GREEN,
    iconColor: DesignColor.ICON_FOREGROUND_GREEN
  },
  {
    label: AiText.ai_task_check_order,
    icon: $r('sys.symbol.list_square_bill'),
    containerColor: DesignColor.ICON_CONTAINER_BLUE,
    iconColor: DesignColor.ICON_FOREGROUND_BLUE
  },
  {
    label: AiText.ai_task_use_coupon,
    icon: $r('sys.symbol.label'),
    containerColor: DesignColor.ICON_CONTAINER_ORANGE,
    iconColor: DesignColor.ICON_FOREGROUND_ORANGE
  },
  {
    label: AiText.ai_task_recharge,
    icon: $r('sys.symbol.calculator'),
    containerColor: DesignColor.ICON_CONTAINER_PURPLE,
    iconColor: DesignColor.ICON_FOREGROUND_PURPLE
  }
]

/** 能力未接入的统一提示文案 */
const AI_PENDING_TIP: string = 'AI 能力正在接入中，当前为界面预览'

@Builder
export function AiWorkBuilder() {
  AiWorkPage()
}

@Component
struct AiWorkPage {
  @State inputText: string = ''
  @State inputFocused: boolean = false
  // 按压反馈（对齐安卓 pressable scale 0.97）
  @State pressedTask: number = -1
  // 发送钮按压反馈（对齐安卓 pressable scale 0.97，套用本页 QuickTaskCard 同款模式）
  @State pressedSend: boolean = false

  /** 快捷任务点击：占位提示（不发起请求） */
  private onQuickTask(index: number): void {
    logger.debug(TAG, `快捷任务点击 index=${index}（占位，不发起请求）`)
    showToastSafety(AI_PENDING_TIP)
  }

  /** 发送：占位提示（不发起请求） */
  private onSend(): void {
    if (this.inputText.trim().length === 0) {
      return
    }
    logger.debug(TAG, '发送点击（占位，不发起请求）')
    showToastSafety(AI_PENDING_TIP)
    this.inputText = ''
  }

  /** 快捷任务卡（对齐安卓 QuickTaskCard：图标容器 + 文案，卡样 1px 描边 + §2.4 卡片档阴影） */
  @Builder
  QuickTaskCard(task: QuickTask, index: number) {
    Row() {
      Column() {
        SymbolGlyph(task.icon)
          .fontSize(20)
          .fontColor([task.iconColor])
      }
      .width(36)
      .height(36)
      .borderRadius(DesignDimens.RADIUS_MD)
      .backgroundColor(task.containerColor)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)

      Text(task.label)
        .fontSize(DesignFontSize.BODY_SMALL)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .margin({ left: DesignDimens.SPACE_XS })
    }
    .layoutWeight(1)
    .padding({ left: 20, right: 20, top: 16, bottom: 16 })
    .alignItems(VerticalAlign.Center)
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .border({ width: 1, color: DesignColor.OUTLINE })
    // 卡片阴影唯一画法（规范 §2.4）：blur 取 CARD_SHADOW_RADIUS(12)，非海拔档位号 LEVEL_1
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: DesignColor.SHADOW_COLOR_CARD,
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
    .scale({ x: this.pressedTask === index ? 0.97 : 1, y: this.pressedTask === index ? 0.97 : 1 })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedTask = index
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedTask = -1
      }
    })
    .onClick(() => {
      this.onQuickTask(index)
    })
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内 DesignColor 静态令牌直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        PageHeader({
          title: AiText.ai_workbench_title,
          backClick: () => {
            pathStack.pop()
          }
        })

        // 安卓 AiWorkPage 无「能力接入中」提示条（结构差异，已按口径移除；
        // 提示文案仍由快捷任务/发送的 toast 承载）
        Scroll() {
          Column() {
            // ---------- 快捷任务 ----------
            // 标题上下各留 12（对齐安卓标题前后 Spacer(spaceSm)，原标题→网格 gap 为 0）
            Text(AiText.ai_quick_tasks)
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .width('100%')
              .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM })

            // 快捷任务 2×2 网格（对齐安卓 AiWorkPage 的两行两列 QuickTaskCard）
            Column({ space: 16 }) {
              Row({ space: 16 }) {
                ForEach(QUICK_TASKS.slice(0, 2), (task: QuickTask, index: number) => {
                  this.QuickTaskCard(task, index)
                }, (task: QuickTask, index: number) => `${index}`)
              }
              .width('100%')
              Row({ space: 16 }) {
                ForEach(QUICK_TASKS.slice(2, 4), (task: QuickTask, index: number) => {
                  this.QuickTaskCard(task, index + 2)
                }, (task: QuickTask, index: number) => `${index + 2}`)
              }
              .width('100%')
            }
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

            // ---------- 最近工作（空态） ----------
            // 区块间距走 SECTION_SPACING(32)（对齐安卓 LazyColumn spacedBy(sectionSpacing)，
            // 原 SPACE_LG 20 偏小）；标题下留 12（对齐安卓标题后 Spacer(spaceSm)）
            Text(AiText.ai_recent_work)
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .width('100%')
              .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SECTION_SPACING, bottom: DesignDimens.SPACE_SM })

            // 卡片本体 width('100%') 必须挂在有左右 padding 的 wrapper 内，
            // 直接 width('100%') + margin 会溢出到屏幕右缘（实测按钮越界）
            Column() {
              Column() {
                Column() {
                  DrumMark({ sizeValue: 60, tint: DesignColor.TEXT_TERTIARY })
                  Text(AiText.ai_empty_recent)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor(DesignColor.TEXT_SECONDARY)
                    .margin({ top: DesignDimens.SPACE_MD })
                  // 「开始提问」对齐安卓 AiWorkPage 的 Surface：wrap-content（约 84dp 宽，
                  // 原 AppButton 通栏 314.9vp）、radiusButton 14、高 52（[P08] 2026-11-05
                  // 画板复裁 84×52，原 48 差 4）、primaryContainer 底 + onPrimaryContainer 字
                  Button(AiText.ai_start_asking)
                    .type(ButtonType.Normal)
                    .height(52)
                    .borderRadius(DesignDimens.BUTTON_RADIUS)
                    .backgroundColor(DesignColor.PRIMARY_CONTAINER)
                    .fontColor(DesignColor.ON_PRIMARY_CONTAINER)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontWeight(DesignFontWeight.MEDIUM)
                    .padding({ left: 18, right: 18, top: 16, bottom: 16 })
                    .onClick(() => {
                      // 「开始提问」真实语义：聚焦底部输入框拉起键盘（对齐安卓
                      // pressable { focusRequester.requestFocus() }）；占位提示保留
                      // focusControl 模块 SDK 17 无独立 d.ts；UIContext.requestFocus @since 22
                      // 超出本工程 API 17——改走 FocusController.requestFocus（@since 12）
                      this.getUIContext().getFocusController().requestFocus('aiInput')
                      showToastSafety(AI_PENDING_TIP)
                    })
                    .margin({ top: DesignDimens.SPACE_SM })
                }
                .width('100%')
                .padding({ top: 32, bottom: 16 })
                .alignItems(HorizontalAlign.Center)
              }
              .width('100%')
              .backgroundColor(DesignColor.SURFACE)
              .borderRadius(DesignDimens.CARD_RADIUS)
              .border({ width: 1, color: DesignColor.OUTLINE })
              .shadow({
                radius: DesignElevation.CARD_SHADOW_RADIUS,
                color: DesignColor.SHADOW_COLOR_CARD,
                offsetX: 0,
                offsetY: DesignElevation.CARD_SHADOW_OFFSET
              })
            }
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
          }
          .width('100%')
          .padding({ bottom: DesignDimens.SPACE_MD })
          .alignItems(HorizontalAlign.Start)
        }
        .layoutWeight(1)
        .scrollBar(BarState.Off)
        .align(Alignment.TopStart)

        // ---------- 底部输入条（固定） ----------
        Column() {
          // 顶线 1px outline（对齐安卓「顶线」画法；原 HAIRLINE 0.5 更细且更浅一档，白底上近不可见）
          Divider()
            .color(DesignColor.OUTLINE)
            .strokeWidth(1)

          Row() {
            TextInput({ text: this.inputText, placeholder: AiText.ai_input_placeholder })
              .id('aiInput')
              .height(48)
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .caretColor(DesignColor.PRIMARY)
              .backgroundColor(DesignColor.SURFACE_VARIANT)
              .borderRadius(DesignDimens.INPUT_RADIUS)
              .borderWidth(1)
              .borderColor(this.inputFocused ? DesignColor.PRIMARY : DesignColor.OUTLINE)
              // 占位符 textSecondary + 与正文同字号 16（对齐安卓 audit-10 P1-4：textTertiary
              // 双主题 1.99/3.76:1 触 DESIGN-SPEC §10 可读文本红线）
              .placeholderColor(DesignColor.TEXT_SECONDARY)
              .placeholderFont({ size: DesignFontSize.BODY })
              .layoutWeight(1)
              .onFocus(() => {
                this.inputFocused = true
              })
              .onBlur(() => {
                this.inputFocused = false
              })
              .onChange((value: string) => {
                this.inputText = value
              })

            Column() {
              SymbolGlyph($r('sys.symbol.paperplane_fill'))
                .fontSize(20)
                .fontColor([DesignColor.ON_PRIMARY])
            }
            .width(48)
            .height(48)
            .borderRadius(24)
            .backgroundColor(DesignColor.PRIMARY)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)
            .opacity(this.inputText.trim().length > 0 ? 1 : 0.5)
            // 按压反馈（对齐安卓 pressable scale 0.97）
            .scale({ x: this.pressedSend ? 0.97 : 1, y: this.pressedSend ? 0.97 : 1 })
            .onTouch((event: TouchEvent) => {
              if (event.type === TouchType.Down) {
                this.pressedSend = true
              } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
                this.pressedSend = false
              }
            })
            .onClick(() => {
              this.onSend()
            })
            .margin({ left: DesignDimens.SPACE_XS })
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)
          // 上下 16（对齐安卓 vertical spaceMd：条总高 1+16+48+16 = 81 ≈ 画板 80；
          // 原 12/12 整条矮 8.5vp），bottom 保留手势条避让（等价安卓 navigationBarsPadding）
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_MD,
            bottom: DesignDimens.SPACE_MD + safeBottomVp()
          })
        }
        .width('100%')
        .backgroundColor(DesignColor.SURFACE)
        .shadow({
          radius: DesignElevation.CARD_SHADOW_RADIUS,
          color: DesignColor.SHADOW_COLOR_CARD,
          offsetX: 0,
          offsetY: DesignElevation.CARD_SHADOW_OFFSET
        })
      }
      .width('100%')
      .height('100%')
      .padding({ top: safeTopVp() })
      .backgroundColor(DesignColor.BACKGROUND)
      }
    }
    .hideTitleBar(true)
  }
}

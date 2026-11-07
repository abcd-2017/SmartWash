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

/** 快捷任务（对齐安卓 AiWorkPage 的 QuickTaskCard 四项） */
interface QuickTask {
  label: string
  icon: Resource
  containerColor: number
  iconColor: number
}

const QUICK_TASKS: QuickTask[] = [
  {
    label: '洗护咨询',
    icon: $r('sys.symbol.laundry'),
    containerColor: DesignColor.ICON_CONTAINER_GREEN,
    iconColor: DesignColor.ICON_FOREGROUND_GREEN
  },
  {
    label: '订单查询',
    icon: $r('sys.symbol.list_square_bill'),
    containerColor: DesignColor.ICON_CONTAINER_BLUE,
    iconColor: DesignColor.ICON_FOREGROUND_BLUE
  },
  {
    label: '优惠券',
    icon: $r('sys.symbol.label'),
    containerColor: DesignColor.ICON_CONTAINER_ORANGE,
    iconColor: DesignColor.ICON_FOREGROUND_ORANGE
  },
  {
    label: '费用估算',
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

  /** 快捷任务卡（对齐安卓 QuickTaskCard：图标容器 + 文案，卡样 1px 描边 + level1 阴影） */
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
        .margin({ left: DesignDimens.SPACE_SM })
    }
    .layoutWeight(1)
    .padding({ left: 20, right: 20, top: 16, bottom: 16 })
    .alignItems(VerticalAlign.Center)
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .border({ width: 1, color: DesignColor.OUTLINE })
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: DesignColor.SHADOW_COLOR_CARD,
      offsetX: 0,
      offsetY: 2
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
          title: '智能助手',
          backClick: () => {
            pathStack.pop()
          }
        })

        // 安卓 AiWorkPage 无「能力接入中」提示条（结构差异，已按口径移除；
        // 提示文案仍由快捷任务/发送的 toast 承载）
        Scroll() {
          Column() {
            // ---------- 快捷任务 ----------
            Text('快捷任务')
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .width('100%')
              .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_SM })

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
            Text('最近工作')
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .width('100%')
              .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_LG })

            // 卡片本体 width('100%') 必须挂在有左右 padding 的 wrapper 内，
            // 直接 width('100%') + margin 会溢出到屏幕右缘（实测按钮越界）
            Column() {
              Column() {
                Column() {
                  DrumMark({ sizeValue: 60, tint: DesignColor.TEXT_TERTIARY })
                  Text('还没有对话记录，从上面选一个任务开始')
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor(DesignColor.TEXT_SECONDARY)
                    .margin({ top: DesignDimens.SPACE_MD })
                  // 「开始提问」对齐安卓 AiWorkPage 的 Surface：wrap-content（约 84dp 宽，
                  // 原 AppButton 通栏 314.9vp）、radiusButton 14、高 48、
                  // primaryContainer 底 + onPrimaryContainer 字（bodySmall 12/500）
                  Button('开始提问')
                    .type(ButtonType.Normal)
                    .height(48)
                    .borderRadius(DesignDimens.BUTTON_RADIUS)
                    .backgroundColor(DesignColor.PRIMARY_CONTAINER)
                    .fontColor(DesignColor.ON_PRIMARY_CONTAINER)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontWeight(DesignFontWeight.MEDIUM)
                    .padding({ left: 18, right: 18, top: 16, bottom: 16 })
                    .onClick(() => {
                      showToastSafety(AI_PENDING_TIP)
                    })
                    .margin({ top: DesignDimens.SPACE_MD })
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
                radius: DesignElevation.LEVEL_1,
                color: DesignColor.SHADOW_COLOR_CARD,
                offsetX: 0,
                offsetY: 2
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
          Divider()
            .color(DesignColor.HAIRLINE)
            .strokeWidth(0.5)

          Row() {
            TextInput({ text: this.inputText, placeholder: '有什么可以帮你的？' })
              .height(48)
              .fontSize(DesignFontSize.BODY)
              .backgroundColor(DesignColor.SURFACE_VARIANT)
              .borderRadius(DesignDimens.INPUT_RADIUS)
              .borderWidth(1)
              .borderColor(this.inputFocused ? DesignColor.PRIMARY : DesignColor.OUTLINE)
              .placeholderColor(DesignColor.TEXT_TERTIARY)
              .placeholderFont({ size: DesignFontSize.BODY_SMALL })
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
            .onClick(() => {
              this.onSend()
            })
            .margin({ left: DesignDimens.SPACE_XS })
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_SM,
            bottom: DesignDimens.SPACE_SM + safeBottomVp()
          })
        }
        .width('100%')
        .backgroundColor(DesignColor.SURFACE)
        .shadow({
          radius: DesignElevation.LEVEL_1,
          color: DesignColor.SHADOW_COLOR_CARD,
          offsetX: 0,
          offsetY: 2
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

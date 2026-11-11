/**
 * 泡泡纸页 — 对齐安卓 feature/lab/ui/bubble/BubbleWrapPage.kt。
 *
 * Canvas 全屏泡泡网格，点击戳破（缩放+淡出短动效 + 三档触觉），全部戳完出「再铺一张」。
 * 泡泡阵列/计数会话内即弃（模块约定）；戳破动画帧经 setInterval 驱动，
 * reduced motion 下直接消失态（触觉保留）。
 */

import { DesignDimens, DesignFontSize, DesignFontFeature, DesignFontWeight, toColorString } from '../../../../constant/DesignSystem'
import { safeBottomVp, safeTopVp } from '../../../../utils/SafeArea'
import { AppButton, PageHeader } from '../../../../view/common/AppComponents'
import { ThemeRefreshV2 } from '../../../../view/common/ThemeRefresh'
import { LabHaptic, labReduceMotion } from '../../core/LabCommon'
import { resColor } from '../../../../utils/ResourceColor'
import { resString } from '../../../../utils/ResourceString'

/** 单个泡泡：网格坐标 + 大小档 */
class Bubble {
  key: string
  col: number
  row: number
  tier: number

  constructor(key: string, col: number, row: number, tier: number) {
    this.key = key
    this.col = col
    this.row = row
    this.tier = tier
  }
}

/** 页面文案 */
class BubbleText {
  static get title(): string { return resString('lab_toy_bubble_wrap') }
  static get poppedFormat(): string { return resString('bubble_text_popped_format') }
  static get reset(): string { return resString('lab_bubble_reset') }
}

/** 目标格距（vp）：屏宽自适应约 8 列 */
const CELL_TARGET: number = 52

/** 列数自适应边界 */
const MIN_COLS: number = 6
const MAX_COLS: number = 12

/** 行数自适应边界（行高按 0.92 倍格距略密于横向） */
const MIN_ROWS: number = 8
const MAX_ROWS: number = 24
const ROW_ASPECT: number = 0.92

/** 泡泡半径占格内基准半径的比例 */
const FILL_RATIO: number = 0.78

/** 命中容差（相对半径）：1.3 使常规屏最小泡有效命中直径达 ≈48dp 触控下限（对齐安卓） */
const HIT_TOLERANCE: number = 1.3

/** 高光偏移（相对半径） */
const HIGHLIGHT_SHIFT: number = 0.35

/** 戳破淡出时长（ms，对齐安卓 DURATION_MICRO_MS） */
const POP_DURATION_MS: number = 200

/** 泡泡大小档位数 */
const BUBBLE_TIERS: number = 3

/** 半径系数（小 0.62 / 中 0.81 / 大 1.0） */
function radiusFactor(tier: number): number {
  return 0.62 + Math.min(2, Math.max(0, tier)) * 0.19
}

@Builder
export function BubbleWrapBuilder() {
  BubbleWrapPage()
}

@ComponentV2
export struct BubbleWrapPage {
  @Local bubbles: Bubble[] = []
  @Local poppedKeys: string[] = []
  @Local poppedCount: number = 0

  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))
  private canvasWidth: number = 0
  private canvasHeight: number = 0
  private cols: number = 0
  private rows: number = 0
  private animatingPops: Map<string, number> = new Map<string, number>()
  private animTimerId: number = -1
  private reduceMotion: boolean = labReduceMotion()

  aboutToDisappear(): void {
    if (this.animTimerId !== -1) {
      clearInterval(this.animTimerId)
      this.animTimerId = -1
    }
  }

  /** 铺一张新泡泡纸（大小档随机三档混排） */
  private newSheet(cols: number, rows: number): void {
    if (cols <= 0 || rows <= 0) {
      return
    }
    this.cols = cols
    this.rows = rows
    this.animatingPops.clear()
    const list: Bubble[] = []
    for (let row = 0; row < rows; row++) {
      for (let col = 0; col < cols; col++) {
        list.push(new Bubble(`${row}_${col}`, col, row, Math.floor(Math.random() * BUBBLE_TIERS)))
      }
    }
    this.bubbles = list
    this.poppedKeys = []
    this.poppedCount = 0
    this.drawSheet()
  }

  /** 戳破一个泡泡：仅首次有效 */
  private pop(key: string): boolean {
    if (this.bubbles.find((bubble: Bubble): boolean => bubble.key === key) === undefined) {
      return false
    }
    if (this.poppedKeys.indexOf(key) >= 0) {
      return false
    }
    this.poppedKeys = this.poppedKeys.concat(key)
    this.poppedCount = this.poppedKeys.length
    return true
  }

  /** 画布尺寸变化 → 计算网格规格并首铺 */
  private onCanvasAreaChange(oldValue: Area, newValue: Area): void {
    const width: number = Number(newValue.width)
    const height: number = Number(newValue.height)
    if (width <= 0 || height <= 0) {
      return
    }
    this.canvasWidth = width
    this.canvasHeight = height
    const cols: number = Math.min(MAX_COLS, Math.max(MIN_COLS, Math.floor(width / CELL_TARGET)))
    const rows: number = Math.min(MAX_ROWS,
      Math.max(MIN_ROWS, Math.floor(height / (CELL_TARGET * ROW_ASPECT))))
    if (cols !== this.cols || rows !== this.rows || this.bubbles.length === 0) {
      this.newSheet(cols, rows)
    } else {
      this.drawSheet()
    }
  }

  /** 点击戳泡（event.x/y 相对画布） */
  private onCanvasClick(event: ClickEvent): void {
    if (this.cols <= 0 || this.rows <= 0 || this.canvasWidth <= 0 || this.canvasHeight <= 0) {
      return
    }
    const cellW: number = this.canvasWidth / this.cols
    const cellH: number = this.canvasHeight / this.rows
    const col: number = Math.min(this.cols - 1, Math.max(0, Math.floor(event.x / cellW)))
    const row: number = Math.min(this.rows - 1, Math.max(0, Math.floor(event.y / cellH)))
    const key: string = `${row}_${col}`
    if (this.poppedKeys.indexOf(key) >= 0) {
      return
    }
    const bubble: Bubble | undefined =
      this.bubbles.find((item: Bubble): boolean => item.key === key)
    if (bubble === undefined) {
      return
    }
    const baseR: number = Math.min(cellW, cellH) / 2 * FILL_RATIO
    const cx: number = (col + 0.5) * cellW
    const cy: number = (row + 0.5) * cellH
    const r: number = baseR * radiusFactor(bubble.tier)
    const dx: number = event.x - cx
    const dy: number = event.y - cy
    if (Math.sqrt(dx * dx + dy * dy) > r * HIT_TOLERANCE) {
      return
    }
    if (this.pop(key)) {
      if (!this.reduceMotion) {
        this.animatingPops.set(key, Date.now())
        this.startAnimLoop()
      }
      // 全部戳完给完成触觉，其余按大小档三档力度
      const allPopped: boolean = this.poppedCount >= this.bubbles.length
      if (allPopped) {
        LabHaptic.success()
      } else if (bubble.tier >= 2) {
        LabHaptic.heavy()
      } else if (bubble.tier >= 1) {
        LabHaptic.medium()
      } else {
        LabHaptic.light()
      }
    }
  }

  /** 启动戳破动画帧循环（在途动画存在时逐帧推进，播完即退出） */
  private startAnimLoop(): void {
    if (this.animTimerId !== -1) {
      return
    }
    this.animTimerId = setInterval(() => {
      this.drawSheet()
      if (this.animatingPops.size === 0) {
        clearInterval(this.animTimerId)
        this.animTimerId = -1
      }
    }, 16)
  }

  /** 绘制泡泡网格 */
  private drawSheet(): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    const width: number = this.canvasWidth
    const height: number = this.canvasHeight
    ctx.clearRect(0, 0, width, height)
    if (this.cols <= 0 || this.rows <= 0 || width <= 0 || height <= 0) {
      return
    }
    const cellW: number = width / this.cols
    const cellH: number = height / this.rows
    const baseR: number = Math.min(cellW, cellH) / 2 * FILL_RATIO
    const now: number = Date.now()
    const finished: string[] = []
    for (const bubble of this.bubbles) {
      const cx: number = (bubble.col + 0.5) * cellW
      const cy: number = (bubble.row + 0.5) * cellH
      const r: number = baseR * radiusFactor(bubble.tier)
      const popStart: number | undefined = this.animatingPops.get(bubble.key)
      if (popStart !== undefined) {
        const t: number = Math.min(1, Math.max(0, (now - popStart) / POP_DURATION_MS))
        const eased: number = t * t
        if (eased < 1) {
          this.drawBulge(cx, cy, r * (1 + 0.35 * eased), 1 - eased)
        } else {
          finished.push(bubble.key)
        }
      } else if (this.poppedKeys.indexOf(bubble.key) >= 0) {
        // 已播完：消失态不再绘制
      } else {
        this.drawBulge(cx, cy, r, 1)
      }
    }
    for (const key of finished) {
      this.animatingPops.delete(key)
    }
  }

  /** 凸起感泡泡：径向渐变圆心左上偏移 + 外缘细描边 */
  private drawBulge(cx: number, cy: number, radius: number, alpha: number): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    ctx.globalAlpha = alpha
    const highlight: string = toColorString(resColor('SURFACE'))
    const soft: string = toColorString(resColor('WATER_SOFT'))
    const edge: string = toColorString(0x99000000 | (resColor('WATER') & 0xFFFFFF))
    const gradient: CanvasGradient = ctx.createRadialGradient(
      cx - radius * HIGHLIGHT_SHIFT, cy - radius * HIGHLIGHT_SHIFT, radius * 0.1,
      cx, cy, radius * 1.7)
    gradient.addColorStop(0, highlight)
    gradient.addColorStop(0.55, soft)
    gradient.addColorStop(1, edge)
    ctx.fillStyle = gradient
    ctx.beginPath()
    ctx.arc(cx, cy, radius, 0, Math.PI * 2, false)
    ctx.fill()
    ctx.lineWidth = 1
    ctx.strokeStyle = toColorString(0x59000000 | (resColor('WATER') & 0xFFFFFF))
    ctx.beginPath()
    ctx.arc(cx, cy, radius, 0, Math.PI * 2, false)
    ctx.stroke()
    ctx.globalAlpha = 1
  }

  /** 计数文案 */
  private poppedText(): string {
    return BubbleText.poppedFormat.replace('%d', this.poppedCount.toString())
      .replace('%d', this.bubbles.length.toString())
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计补齐）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，画布 onReady 重绘与 资源色直读点
      // 重读新值（静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets；
      // 戳破动画帧循环 startAnimLoop 保持在宿主之外，页面组件本体不在子树内）
      ThemeRefreshV2() {
      Column() {
        PageHeader({ title: BubbleText.title })

        Text(this.poppedText())
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(resColor('TEXT_SECONDARY'))
          .fontFeature(DesignFontFeature.TNUM)
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

        Stack() {
          Canvas(this.ctx)
            .width('100%')
            .height('100%')
            .onAreaChange((oldValue: Area, newValue: Area) => {
              this.onCanvasAreaChange(oldValue, newValue)
            })
            .onReady(() => {
              this.drawSheet()
            })
            .onClick((event: ClickEvent) => {
              this.onCanvasClick(event)
            })

          // 全部戳完：浮出「再铺一张」
          if (this.bubbles.length > 0 && this.poppedCount >= this.bubbles.length) {
            AppButton({
              text: BubbleText.reset,
              click: () => {
                this.newSheet(this.cols, this.rows)
              }
            })
            .margin({ bottom: DesignDimens.SECTION_SPACING })
          }
        }
        .width('100%')
        .layoutWeight(1)
        .padding({ bottom: DesignDimens.SPACE_SM })
      }
      .width('100%')
      .height('100%')
      .padding({ top: safeTopVp(), bottom: safeBottomVp() })
      .backgroundColor(resColor('BACKGROUND'))
      }
    }
    .hideTitleBar(true)
  }
}

/**
 * 落沙实验室页 — 对齐安卓 feature/lab/ui/sand/SandPage.kt。
 *
 * 全屏 Canvas 沙盒（元胞自动机，内核 SandSimulator 纯逻辑）+ 底部调色板（9 元素）
 * + 清除 / 暂停播放。渲染采用按行游程合并（同色连续格合并为一次 fillRect），
 * 避免逐 cell 绘制带来的性能开销。
 * reduced motion：默认暂停（静帧）+ 用户主动「播放」（当前 SDK 无等价 API，常量 false）。
 */

import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from '../../../../constant/DesignSystem'
import { safeBottomVp, safeTopVp } from '../../../../utils/SafeArea'
import { PageHeader } from '../../../../view/common/AppComponents'
import { SandElement, SandSimulator } from '../../core/SandSimulator'
import { LabHaptic, labReduceMotion } from '../../core/LabCommon'

/** 页面文案 */
class SandText {
  static readonly title: string = '落沙实验室'
  static readonly hint: string = '拖动手指涂抹，看元素互相反应'
  static readonly currentFormat: string = '当前：%s'
  static readonly clear: string = '清除'
  static readonly play: string = '播放'
  static readonly pause: string = '暂停'
  static readonly elementEmpty: string = '橡皮'
  static readonly elementSand: string = '沙'
  static readonly elementWater: string = '水'
  static readonly elementDetergent: string = '洗衣粉'
  static readonly elementFoam: string = '泡沫'
  static readonly elementStain: string = '污渍'
  static readonly elementFabric: string = '织物'
  static readonly elementFire: string = '火'
  static readonly elementSteam: string = '蒸汽'
}

/** 目标格距（vp）：按屏宽自适应约 100 列 */
const SAND_CELL_TARGET: number = 4.5

/** 网格列数边界 */
const MIN_COLS: number = 20
const MAX_COLS: number = 200

/** 网格行数边界 */
const MIN_ROWS: number = 8
const MAX_ROWS: number = 400

/** 模拟节拍（ms）：约 30 tick/秒 */
const SIM_TICK_MS: number = 33

/** 调色板每行元素数（9 元素 → 5 + 4 两行） */
const PALETTE_COLUMNS: number = 5

@Builder
export function SandBuilder() {
  SandPage()
}

@ComponentV2
export struct SandPage {
  @Local playing: boolean = true
  @Local selected: SandElement = SandElement.SAND

  private sim: SandSimulator = new SandSimulator()
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))
  private canvasWidth: number = 0
  private canvasHeight: number = 0
  private gridCols: number = 0
  private gridRows: number = 0
  private timerId: number = -1
  private reduceMotion: boolean = labReduceMotion()
  // 调色板 ARGB 按元素 id 索引（EMPTY 透明，画布底色透出）
  private palette: number[] = [
    DesignColor.SURFACE_VARIANT,
    DesignColor.WARNING,
    DesignColor.WATER,
    DesignColor.ICON_CONTAINER_BLUE,
    DesignColor.SURFACE,
    DesignColor.ICON_FOREGROUND_PINK,
    DesignColor.PRIMARY,
    DesignColor.ERROR,
    DesignColor.TEXT_TERTIARY,
  ]

  aboutToAppear(): void {
    if (this.reduceMotion) {
      this.playing = false
    }
    if (this.playing) {
      this.startLoop()
    }
  }

  aboutToDisappear(): void {
    this.clearLoop()
  }

  private clearLoop(): void {
    if (this.timerId !== -1) {
      clearInterval(this.timerId)
      this.timerId = -1
    }
  }

  /** 帧驱动：固定节拍 tick + 重绘（暂停即停止=静帧） */
  private startLoop(): void {
    if (this.timerId !== -1) {
      return
    }
    this.timerId = setInterval(() => {
      this.sim.tick()
      this.drawSand()
    }, SIM_TICK_MS)
  }

  /** 画布尺寸变化 → 计算网格规格并重建空沙盒 */
  private onCanvasAreaChange(oldValue: Area, newValue: Area): void {
    const width: number = Number(newValue.width)
    const height: number = Number(newValue.height)
    if (width <= 0 || height <= 0) {
      return
    }
    this.canvasWidth = width
    this.canvasHeight = height
    const cols: number = Math.min(MAX_COLS, Math.max(MIN_COLS, Math.floor(width / SAND_CELL_TARGET)))
    if (cols <= 0) {
      return
    }
    const cellPx: number = width / cols
    const rows: number = Math.min(MAX_ROWS, Math.max(MIN_ROWS, Math.floor(height / cellPx)))
    if (rows <= 0) {
      return
    }
    this.gridCols = cols
    this.gridRows = rows
    this.sim.resize(cols, rows)
    this.drawSand()
  }

  /** 指针位置换算为网格坐标并涂抹（圆形概率笔刷） */
  private paintAt(x: number, y: number): void {
    if (this.gridCols <= 0 || this.gridRows <= 0 || this.canvasWidth <= 0) {
      return
    }
    const cellPx: number = this.canvasWidth / this.gridCols
    if (cellPx <= 0) {
      return
    }
    const col: number = Math.min(this.gridCols - 1, Math.max(0, Math.floor(x / cellPx)))
    const row: number = Math.min(this.gridRows - 1, Math.max(0, Math.floor(y / cellPx)))
    this.sim.paint(col, row, this.selected)
    this.drawSand()
  }

  /** 触摸涂抹（单指拖动） */
  private onCanvasTouch(event: TouchEvent): void {
    const touch: TouchObject | undefined = event.touches.length > 0 ? event.touches[0] : undefined
    if (touch === undefined) {
      return
    }
    if (event.type === TouchType.Down) {
      this.paintAt(touch.x, touch.y)
      LabHaptic.light()
    } else if (event.type === TouchType.Move) {
      this.paintAt(touch.x, touch.y)
    }
  }

  /** 按行游程合并绘制沙盒（同色连续格合并为一次 fillRect） */
  private drawSand(): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    const width: number = this.canvasWidth
    const height: number = this.canvasHeight
    if (width <= 0 || height <= 0 || this.gridCols <= 0 || this.gridRows <= 0) {
      return
    }
    ctx.clearRect(0, 0, width, height)
    ctx.fillStyle = DesignColor.SURFACE_VARIANT
    ctx.fillRect(0, 0, width, height)
    const cellW: number = width / this.gridCols
    const cellH: number = height / this.gridRows
    for (let row = 0; row < this.gridRows; row++) {
      let col: number = 0
      while (col < this.gridCols) {
        const element: SandElement = this.sim.elementAt(col, row)
        if (element === SandElement.EMPTY) {
          col++
          continue
        }
        let end: number = col + 1
        while (end < this.gridCols && this.sim.elementAt(end, row) === element) {
          end++
        }
        ctx.fillStyle = this.palette[Number(element)]
        ctx.fillRect(col * cellW, row * cellH, (end - col) * cellW + 0.5, cellH + 0.5)
        col = end
      }
    }
  }

  /** 元素名文案 */
  private elementLabel(element: SandElement): string {
    switch (element) {
      case SandElement.EMPTY:
        return SandText.elementEmpty
      case SandElement.SAND:
        return SandText.elementSand
      case SandElement.WATER:
        return SandText.elementWater
      case SandElement.DETERGENT:
        return SandText.elementDetergent
      case SandElement.FOAM:
        return SandText.elementFoam
      case SandElement.STAIN:
        return SandText.elementStain
      case SandElement.FABRIC:
        return SandText.elementFabric
      case SandElement.FIRE:
        return SandText.elementFire
      default:
        return SandText.elementSteam
    }
  }

  /** 调色板色块色 */
  private swatchColor(element: SandElement): number {
    if (element === SandElement.EMPTY) {
      return DesignColor.SURFACE_VARIANT
    }
    return this.palette[Number(element)]
  }

  /** 元素调色板（9 元素两行 5 + 4） */
  @Builder
  SandPalette() {
    Column({ space: DesignDimens.SPACE_SM }) {
      Row({ space: DesignDimens.SPACE_SM }) {
        ForEach(this.paletteElements(true), (element: SandElement) => {
          this.PaletteSwatch(element)
        }, (element: SandElement) => element.toString())
      }
      .width('100%')

      Row({ space: DesignDimens.SPACE_SM }) {
        ForEach(this.paletteElements(false), (element: SandElement) => {
          this.PaletteSwatch(element)
        }, (element: SandElement) => element.toString())
      }
      .width('100%')
    }
    .width('100%')
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor(DesignColor.OUTLINE)
    .shadow({
      radius: 12,
      color: DesignColor.SHADOW_COLOR_CARD,
      offsetX: 0,
      offsetY: 2
    })
  }

  /** 调色板元素分组（前 5 / 后 4） */
  private paletteElements(firstRow: boolean): SandElement[] {
    const all: SandElement[] = [
      SandElement.EMPTY, SandElement.SAND, SandElement.WATER, SandElement.DETERGENT,
      SandElement.FOAM, SandElement.STAIN, SandElement.FABRIC, SandElement.FIRE,
      SandElement.STEAM,
    ]
    return firstRow ? all.slice(0, PALETTE_COLUMNS) : all.slice(PALETTE_COLUMNS)
  }

  /** 单个色块：44vp 圆形，选中态 3vp 品牌色描边 */
  @Builder
  PaletteSwatch(element: SandElement) {
    Column()
      .width(44)
      .height(44)
      .borderRadius(22)
      .backgroundColor(this.swatchColor(element))
      .borderWidth(this.selected === element ? 3 : 1)
      .borderColor(this.selected === element ? DesignColor.PRIMARY : DesignColor.OUTLINE)
      .onClick(() => {
        this.selected = element
        LabHaptic.selection()
      })
  }

  /** 小按钮（文字胶囊） */
  private onTogglePlay(): void {
    this.playing = !this.playing
    if (this.playing) {
      this.startLoop()
    } else {
      this.clearLoop()
    }
    LabHaptic.selection()
  }

  build() {
    NavDestination() {
      Column() {
        PageHeader({ title: SandText.title })

        Text(SandText.hint)
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor(DesignColor.TEXT_TERTIARY)
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_XS })

        Stack() {
          Canvas(this.ctx)
            .width('100%')
            .height('100%')
            .borderRadius(DesignDimens.CARD_RADIUS)
            .clip(true)
            .onAreaChange((oldValue: Area, newValue: Area) => {
              this.onCanvasAreaChange(oldValue, newValue)
            })
            .onReady(() => {
              this.drawSand()
            })
            .onTouch((event: TouchEvent) => {
              this.onCanvasTouch(event)
            })
        }
        .width('100%')
        .layoutWeight(1)
        .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD,
          top: DesignDimens.SPACE_SM })

        Column() {
          this.SandPalette()
        }
        .width('100%')
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING,
          top: DesignDimens.SPACE_MD })

        Row() {
          Text(SandText.currentFormat.replace('%s', this.elementLabel(this.selected)))
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .layoutWeight(1)

          SandTextButton({
            text: SandText.clear,
            click: (): void => {
              this.sim.clear()
              this.drawSand()
              LabHaptic.medium()
            }
          })

          Column() {
            SandTextButton({
              // 文案读 this.playing（@Param 响应式）：原 @Builder 按值传参在暂停/播放后
              // 定格，按钮文字不随状态切换
              text: this.playing ? SandText.pause : SandText.play,
              click: (): void => {
                this.onTogglePlay()
              }
            })
          }
          .margin({ left: DesignDimens.SPACE_SM })
        }
        .width('100%')
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING,
          top: DesignDimens.SPACE_MD, bottom: DesignDimens.SPACE_LG })
        .alignItems(VerticalAlign.Center)
      }
      .width('100%')
      .height('100%')
      .padding({ top: safeTopVp(), bottom: safeBottomVp() })
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
  }
}

/**
 * 文字胶囊小按钮 —— @ComponentV2 + @Param 承载文案（已知坑：@Builder 按值传参在
 * this.playing 变更后不重建，暂停/播放按钮文案定格）。点击经 @Event 上抛。
 */
@ComponentV2
struct SandTextButton {
  @Param text: string = ''
  @Event click: () => void = () => {
  }

  build() {
    Text(this.text)
      .fontSize(DesignFontSize.BODY_SMALL)
      .fontColor(DesignColor.TEXT_PRIMARY)
      .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD,
        top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
      .borderRadius(DesignDimens.RADIUS_MD)
      .backgroundColor(DesignColor.SURFACE_VARIANT)
      .onClick(() => {
        this.click()
      })
  }
}

/**
 * 落沙实验室页 — 对齐安卓 feature/lab/ui/sand/SandPage.kt。
 *
 * 全屏 Canvas 沙盒（元胞自动机，内核 SandSimulator 纯逻辑）+ 底部调色板（9 元素）
 * + 清除 / 暂停播放。渲染采用按行游程合并（同色连续格合并为一次 fillRect），
 * 避免逐 cell 绘制带来的性能开销。
 * reduced motion：默认暂停（静帧）+ 用户主动「播放」（当前 SDK 无等价 API，常量 false）。
 */

import { DesignDimens, DesignFontSize, DesignFontWeight } from '../../../../constant/DesignSystem'
import { safeBottomVp, safeTopVp } from '../../../../utils/SafeArea'
import { PageHeader } from '../../../../view/common/AppComponents'
import { ThemeRefreshV2 } from '../../../../view/common/ThemeRefresh'
import { SandElement, SandSimulator } from '../../core/SandSimulator'
import { LabHaptic, labReduceMotion } from '../../core/LabCommon'
import { resColor } from '../../../../utils/ResourceColor'
import { resString } from '../../../../utils/ResourceString'

/** 页面文案 */
class SandText {
  static get title(): string { return resString('lab_toy_sand') }
  static get hint(): string { return resString('lab_sand_hint') }
  static get currentFormat(): string { return resString('sand_text_current_format') }
  static get clear(): string { return resString('lab_sand_clear') }
  static get play(): string { return resString('lab_fish_play') }
  static get pause(): string { return resString('lab_breath_pause') }
  static get elementEmpty(): string { return resString('lab_sand_element_empty') }
  static get elementSand(): string { return resString('lab_sand_element_sand') }
  static get elementWater(): string { return resString('lab_sand_element_water') }
  static get elementDetergent(): string { return resString('lab_sand_element_detergent') }
  static get elementFoam(): string { return resString('lab_sand_element_foam') }
  static get elementStain(): string { return resString('lab_sand_element_stain') }
  static get elementFabric(): string { return resString('lab_sand_element_fabric') }
  static get elementFire(): string { return resString('lab_sand_element_fire') }
  static get elementSteam(): string { return resString('lab_sand_element_steam') }
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
  // 元素是画在 surfaceVariant 画板上的前景图形，禁用「容器浅底」令牌：
  // DETERGENT→iconForegroundBlue、FOAM→iconForegroundPurple、STEAM→textSecondary，
  // 逐槽对底 ≥1.5:1（对齐安卓 colorOf）
  // 即时求值而非构造期快照：深浅色切换经 ThemeRefreshV2 重建子树后，
  // drawSand/swatchColor 重读新令牌（原快照导致实时切换停留旧色）
  private paletteColors(): number[] {
    return [
      resColor('SURFACE_VARIANT'),
      resColor('WARNING'),
      resColor('WATER'),
      resColor('ICON_FOREGROUND_BLUE'),
      resColor('ICON_FOREGROUND_PURPLE'),
      resColor('ICON_FOREGROUND_PINK'),
      resColor('PRIMARY'),
      resColor('ERROR'),
      resColor('TEXT_SECONDARY'),
    ]
  }

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
    // 规格未变（ThemeRefreshV2 重建子树触发的同尺寸 onAreaChange）只重绘不重建沙盒，
    // 避免实时切深浅色把用户已涂抹的沙画清空
    if (cols === this.gridCols && rows === this.gridRows) {
      this.drawSand()
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
    // 调色板每帧即时求值（深浅色切换后下一帧即取新令牌）
    const palette: number[] = this.paletteColors()
    ctx.clearRect(0, 0, width, height)
    ctx.fillStyle = resColor('SURFACE_VARIANT')
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
        ctx.fillStyle = palette[Number(element)]
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
  private swatchColor(element: SandElement): ResourceColor {
    if (element === SandElement.EMPTY) {
      return $r('app.color.SURFACE_VARIANT')
    }
    return this.paletteColors()[Number(element)]
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
    .backgroundColor($r('app.color.SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor($r('app.color.OUTLINE'))
    .shadow({
      radius: 12,
      color: $r('app.color.SHADOW_COLOR_CARD'),
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

  /** 单个色块：48vp 圆形（触控热区下限），选中态 3vp 品牌色描边 */
  @Builder
  PaletteSwatch(element: SandElement) {
    Column()
      .width(48)
      .height(48)
      .borderRadius(24)
      .backgroundColor(this.swatchColor(element))
      .borderWidth(this.selected === element ? 3 : 1)
      .borderColor(this.selected === element ? $r('app.color.PRIMARY') : $r('app.color.OUTLINE'))
      .accessibilityText(this.elementLabel(element))
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
      // 深浅色实时跟随（深色模式审计补齐）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点与调色板
      // 即时求值点重读新值（静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets；
      // 页面级引擎循环（aboutToAppear/startLoop）保持在宿主之外，页面组件本体不在子树内）
      ThemeRefreshV2() {
      Column() {
        PageHeader({ title: SandText.title })

        Text(SandText.hint)
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_XS })

        Stack() {
          Canvas(this.ctx)
            .width('100%')
            .height('100%')
            .borderRadius(DesignDimens.CARD_RADIUS)
            .clip(true)
            .borderWidth(1)
            .borderColor($r('app.color.OUTLINE'))
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
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING,
          top: DesignDimens.SPACE_SM })

        Column() {
          this.SandPalette()
        }
        .width('100%')
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING,
          top: DesignDimens.SPACE_MD })

        Row() {
          Text(SandText.currentFormat.replace('%s', this.elementLabel(this.selected)))
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor($r('app.color.TEXT_SECONDARY'))
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
      .backgroundColor($r('app.color.BACKGROUND'))
      }
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
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor($r('app.color.TEXT_PRIMARY'))
      // 视觉胶囊不变，热区抬到 48dp 下限（对齐安卓 heightIn(min=48)）
      .constraintSize({ minHeight: 48 })
      .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD,
        top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
      .borderRadius(DesignDimens.RADIUS_MD)
      .backgroundColor($r('app.color.SURFACE_VARIANT'))
      .onClick(() => {
        this.click()
      })
  }
}

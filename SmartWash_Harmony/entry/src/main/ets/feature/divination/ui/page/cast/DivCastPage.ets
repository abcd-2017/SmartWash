/**
 * 摇卦页（仅六爻）—— 对齐安卓 ui/page/cast/DivCastPage.kt + DivCastViewModel.kt。
 *
 * 时间轴：按下即缩放 → 三币起跳翻转 ~420ms → 落定【同帧】触觉 → 爻落子午线节点
 * → 第 6 掷完成按钮变为「成卦 · 进入卦盘」。
 *
 * 关键约束：
 * - 结果先于动画确定（CoinTosser 先算，动画只呈现，不改变爻值序列）。
 * - 掷爻/成卦按钮在动画播放中、保存中、待落定期间禁用（防重掷）。
 * - reduced-motion：跳过动画直接定格结果面 + 触感（对齐安卓 DivCastPage.kt:194-199）。
 * - 已掷 1..5 爻或动画中按返回键 → 弃局确认对话框（禁重摇）。
 * - 成卦：以当前时刻装卦入库 → 跳卦盘并清空栈回观象台首页。
 */

import { Solar } from '../../../core/lunar/lunar'
import { CoinTosser, YaoValue } from '../../../core/Yao'
import { LiuYaoChart } from '../../../core/liuyao/LiuYaoChart'
import { encodeChartJson } from '../../data/DivChartCodec'
import { DivRecordRepository, encodeBundle, encodeBundleToJson } from '../../data/DivinationStore'
import { DivCastParam } from '../../DivCastParam'
import {
  DivGoldButton,
  DivHaptic,
  DivPalette,
  DivSerifText,
  DivText,
  divReduceMotion
} from '../../components/DivComponents'
import { DivinationScreen } from '../../DivinationScreen'
import { AppConfirmDialog } from '../../../../view/common/AppComponents'
import { RouteNames } from '../../../../constant/RouteNames'
import { getObjectParam, pathStack } from '../../../../utils/PathStackUtil'
import { showToastSafety } from '../../../../utils/ToastUtil'

/** 铜钱尺寸 / 间距（对齐安卓 CoinAndMeridian.kt:63 的 72dp 币身 + 11dp 水平内距） */
const COIN_SIZE: number = 72
const COIN_GAP: number = 22
const COIN_ROW_WIDTH: number = COIN_SIZE * 3 + COIN_GAP * 2

/** 爻位名（自上爻顶到初爻底，对齐安卓 DivCastPage.kt:118） */
const POSITION_LABELS: string[] = ['上爻', '五爻', '四爻', '三爻', '二爻', '初爻']

/** 爻值 → 符号 / 爻名（对齐安卓 DivCastPage.kt:148-163 与 CoinAndMeridian.kt:247-269） */
const YAO_SYMBOL_YANG: string = '▅▅▅▅▅▅'
const YAO_SYMBOL_YIN: string = '▅▅  ▅▅'

/** 子午线节点展示数据（UI 语法不允许局部语句，故计算收敛为纯方法返回对象） */
interface MeridianNodeData {
  symbol: string
  nameLabel: string
  isMoving: boolean
  done: boolean
}

@Builder
export function DivCastBuilder() {
  DivCastPage()
}

@ComponentV2
struct DivCastPage {
  @Local question: string = ''
  @Local category: string = 'other'
  @Local lines: number[] = []
  @Local tossing: boolean = false
  @Local saving: boolean = false
  @Local pendingYao: YaoValue | null = null
  @Local tossId: number = 0
  @Local faces: boolean[] = [true, true, true]
  // 掷币动画进度（0→1，420ms；setInterval 驱动 Canvas 逐帧重绘）
  @Local progress: number = 0

  private tosser: CoinTosser = new CoinTosser()
  private coinCtx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))
  private coinTimerId: number = -1
  private baseRot: number[] = [0, 0, 0]
  private deltaRot: number[] = [0, 0, 0]
  private heights: number[] = [52, 52, 52]
  private tilts: number[] = [0, 0, 0]
  private quitDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: DivText.castQuitTitle,
      message: DivText.castQuitBody,
      confirmText: '丢弃',
      cancelText: '继续摇卦',
      onConfirm: () => {
        pathStack.pop()
      },
      onDismiss: () => {
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  async aboutToAppear(): Promise<void> {
    // 路由入参（question / category 由问事页打包携带，对齐安卓 DivCast 的 query 参数）
    const param = getObjectParam<DivCastParam>(RouteNames.DIV_CAST)
    if (param !== null) {
      this.question = param.question
      this.category = param.category
    }
    this.drawCoins()
  }

  aboutToDisappear(): void {
    // 定时器硬规则：页面销毁必须清理
    if (this.coinTimerId !== -1) {
      clearInterval(this.coinTimerId)
      this.coinTimerId = -1
    }
  }

  /** tossId 递增即播放一次掷币编排（结果先于动画确定，动画只呈现） */
  @Monitor('tossId')
  onTossIdChange(): void {
    if (this.tossId === 0) {
      return
    }
    for (let i = 0; i < 3; i++) {
      const target = this.faces[i] ? 0 : 180
      const current = this.baseRot[i]
      // 至少两整圈到目标净角（字 0° / 背 180°）
      this.deltaRot[i] = 720 + ((target - current) % 360 + 360) % 360
      // 随机相位：起跳高度扰动 + 随机 tiltZ ±20°（避免每次轨迹一模一样）
      this.heights[i] = 52 + Math.floor(Math.random() * 17)
      this.tilts[i] = Math.random() * 40 - 20
    }
    this.progress = 0
    this.drawCoins()

    if (this.coinTimerId !== -1) {
      clearInterval(this.coinTimerId)
    }
    const start = Date.now()
    this.coinTimerId = setInterval(() => {
      const elapsed = Date.now() - start
      this.progress = Math.min(1, elapsed / 420)
      this.drawCoins()
      if (this.progress >= 1) {
        clearInterval(this.coinTimerId)
        this.coinTimerId = -1
        for (let i = 0; i < 3; i++) {
          this.baseRot[i] = this.baseRot[i] + this.deltaRot[i]
        }
        this.onSettled()
      }
    }, 16)
  }

  /** 落定【同帧】触觉：动爻（老阴/老阳）更强反馈，随后爻落子午线节点 */
  private onSettled(): void {
    const yao = this.pendingYao
    if (yao !== null) {
      if (yao.isMoving) {
        DivHaptic.success()
      } else {
        DivHaptic.medium()
      }
      this.commitToss(yao)
    }
  }

  /** 提交本爻（逐爻累积，自初爻起） */
  private commitToss(yao: YaoValue): void {
    if (this.lines.length >= 6) {
      return
    }
    this.lines = this.lines.concat(yao.code)
    this.tossing = false
    this.pendingYao = null
  }

  private isComplete(): boolean {
    return this.lines.length === 6
  }

  private tossCount(): number {
    return this.lines.length + (this.pendingYao !== null ? 1 : 0)
  }

  /** 掷爻 / 成卦 */
  private onMainButtonClick(): void {
    if (this.isComplete()) {
      DivHaptic.success()
      this.completeChart()
      return
    }
    // t=0：先定结果（预生成），动画只呈现
    const yao = this.tosser.tossLine()
    this.pendingYao = yao
    const ziCount = this.ziCountOf(yao)
    this.faces = [0, 1, 2].map((i: number): boolean => i < ziCount)
    this.tossing = true
    if (divReduceMotion()) {
      // reduced-motion：直接定格结果面，仅保留落定触觉
      if (yao.isMoving) {
        DivHaptic.success()
      } else {
        DivHaptic.medium()
      }
      this.commitToss(yao)
      return
    }
    this.tossId = this.tossId + 1
  }

  /** 由爻值定三币结果面：字记 3、背记 2，和 6/7/8/9 = 老阴/少阳/少阴/老阳 */
  private ziCountOf(yao: YaoValue): number {
    if (yao === YaoValue.LAO_YANG) {
      return 3
    }
    if (yao === YaoValue.SHAO_YIN) {
      return 2
    }
    if (yao === YaoValue.SHAO_YANG) {
      return 1
    }
    return 0
  }

  /** 六掷完成：以当前时刻起四柱装卦入库（存入卦历），返回记录 id */
  private async completeChart(): Promise<void> {
    const lines = this.lines
    if (lines.length !== 6 || this.saving) {
      return
    }
    this.saving = true
    try {
      const castAt = Date.now()
      const chart = LiuYaoChart.compile(lines, Solar.fromDate(new Date(castAt)))
      const chartJson = encodeChartJson(chart)
      const bundleJson = encodeBundleToJson(encodeBundle('liuyao', chartJson))
      const id = await DivRecordRepository.save(
        'liuyao', this.category, this.question, JSON.stringify(lines), castAt, bundleJson)
      this.saving = false
      this.navigateChart(id)
    } catch (err) {
      this.saving = false
      showToastSafety('成卦失败，请稍后重试')
    }
  }

  private navigateChart(id: number): void {
    pathStack.clear()
    pathStack.pushPathByName(RouteNames.DIV_HOME, null)
    pathStack.pushPathByName(RouteNames.DIV_CHART, { recordId: id, animate: true })
  }

  // ========== 铜钱绘制（Canvas 拟真：径向渐变 + 回纹内圈 + 方孔 + 正背面） ==========

  private drawCoins(): void {
    const ctx = this.coinCtx
    ctx.clearRect(0, 0, COIN_ROW_WIDTH, COIN_SIZE)
    for (let i = 0; i < 3; i++) {
      const rot = this.baseRot[i] + this.deltaRot[i] * this.progress
      const lift = -this.heights[i] * Math.sin(this.progress * Math.PI)
      const tilt = this.tilts[i] * Math.sin(this.progress * Math.PI)
      ctx.save()
      ctx.translate(i * (COIN_SIZE + COIN_GAP) + COIN_SIZE / 2, COIN_SIZE / 2 + lift)
      ctx.rotate(tilt * Math.PI / 180)
      ctx.translate(-COIN_SIZE / 2, -COIN_SIZE / 2)
      this.drawCoinBody(ctx, rot)
      ctx.restore()
    }
  }

  private drawCoinBody(ctx: CanvasRenderingContext2D, rotationY: number): void {
    const size = COIN_SIZE
    const r = size / 2
    const cx = size / 2
    const cy = size / 2
    const normalized = ((rotationY % 360) + 360) % 360
    const visibleFace = normalized < 90 || normalized > 270

    // 币身：径向渐变鎏金
    const grad = ctx.createRadialGradient(cx - r * 0.3, cy - r * 0.35, 0, cx, cy, r * 1.4)
    grad.addColorStop(0, '#E8D5A4')
    grad.addColorStop(0.5, '#C9A961')
    grad.addColorStop(1, '#8F7434')
    ctx.fillStyle = grad
    ctx.beginPath()
    ctx.arc(cx, cy, r, 0, Math.PI * 2, false)
    ctx.fill()

    // 描边
    ctx.lineWidth = 1.5
    ctx.strokeStyle = '#6B5626'
    ctx.beginPath()
    ctx.arc(cx, cy, r, 0, Math.PI * 2, false)
    ctx.stroke()

    // 虚线回纹内圈
    ctx.lineWidth = 1
    ctx.strokeStyle = '#8F7434'
    ctx.setLineDash([8, 6])
    ctx.beginPath()
    ctx.arc(cx, cy, r * 0.78, 0, Math.PI * 2, false)
    ctx.stroke()
    ctx.setLineDash([])

    // 方孔
    const holeHalf = r * 0.27
    ctx.fillStyle = '#26231C'
    ctx.fillRect(cx - holeHalf, cy - holeHalf, holeHalf * 2, holeHalf * 2)
    ctx.lineWidth = 1.5
    ctx.strokeStyle = '#6B5626'
    ctx.strokeRect(cx - holeHalf, cy - holeHalf, holeHalf * 2, holeHalf * 2)

    if (visibleFace) {
      // 正面：乾隆通宝（上下字位）
      ctx.font = '10px serif'
      ctx.fillStyle = '#5F4C1E'
      this.drawCenteredText(ctx, '乾隆', cx, cy - holeHalf - r * 0.30)
      this.drawCenteredText(ctx, '通宝', cx, cy + holeHalf + r * 0.30)
    } else {
      // 背面：四角回纹斜线
      const d = r * 0.55
      const gap = holeHalf + 5
      ctx.strokeStyle = '#5F4C1E'
      ctx.lineWidth = 1.4
      ctx.lineCap = 'round'
      const corners: number[][] = [[-1, -1], [1, -1], [-1, 1], [1, 1]]
      for (let i = 0; i < corners.length; i++) {
        ctx.beginPath()
        ctx.moveTo(cx + corners[i][0] * d, cy + corners[i][1] * d)
        ctx.lineTo(cx + corners[i][0] * gap, cy + corners[i][1] * gap)
        ctx.stroke()
      }
    }
  }

  private drawCenteredText(ctx: CanvasRenderingContext2D, text: string, cx: number, cy: number): void {
    const metrics = ctx.measureText(text)
    ctx.fillText(text, cx - metrics.width / 2, cy + metrics.actualBoundingBoxAscent / 2)
  }

  // ========== 子午线节点 ==========

  @Builder
  MeridianNode(data: MeridianNodeData, positionLabel: string) {
    Row() {
      DivSerifText({
        text: positionLabel,
        size: 12,
        weight: 500,
        spacing: 2,
        color: DivPalette.textTertiary,
        align: TextAlign.End
      })
        .width(34)

      Column().width(14)

      Stack() {
        Column()
          .width(14)
          .height(14)
          .borderRadius(7)
          .border({ width: 1, color: data.done ? DivPalette.gold : DivPalette.goldHair })
        if (data.done) {
          Column()
            .width(6)
            .height(6)
            .borderRadius(3)
            .backgroundColor(DivPalette.gold)
        }
      }
      .width(14)
      .height(14)
      .alignContent(Alignment.Center)

      Column().width(14)

      if (data.done && data.symbol.length > 0) {
        DivSerifText({ text: data.symbol, size: 17, weight: 400, spacing: 0, color: DivPalette.textPrimary })
        if (data.isMoving) {
          // 老阳○ 鎏金 / 老阴× 朱砂 —— 颜色即信息
          DivSerifText({
            text: data.nameLabel.indexOf('老阳') === 0 ? '○' : '×',
            size: 13,
            weight: 400,
            spacing: 0,
            color: data.nameLabel.indexOf('老阳') === 0 ? DivPalette.gold : DivPalette.sealHi
          })
            .margin({ left: 4 })
        }
        DivSerifText({
          text: data.nameLabel,
          size: 11,
          weight: 400,
          spacing: 0,
          color: data.isMoving ? DivPalette.sealHi : DivPalette.gold
        })
          .margin({ left: 8 })
      } else {
        DivSerifText({ text: '┄┄┄', size: 15, weight: 400, spacing: 0, color: DivPalette.goldHair })
      }
    }
    .width('100%')
    .height(54)
    .alignItems(VerticalAlign.Center)
  }

  build() {
    NavDestination() {
      DivinationScreen({ scrollable: false }) {
        // 1. 标题 + 进度
        DivSerifText({
          text: DivText.castTitle,
          size: 15,
          weight: 500,
          spacing: 4,
          color: DivPalette.textSecondary,
          align: TextAlign.Center
        })
          .width('100%')
          .margin({ top: 10 })

        DivSerifText({
          text: `第 ${Math.min(this.tossCount(), 6)} 掷 · 共 6 掷`,
          size: 11,
          weight: 400,
          spacing: 3,
          color: DivPalette.gold,
          align: TextAlign.Center
        })
          .width('100%')
          .padding({ top: 4, bottom: 8 })

        // 2. 三币
        Column() {
          Canvas(this.coinCtx)
            .width(COIN_ROW_WIDTH)
            .height(COIN_SIZE)
            .onReady(() => {
              this.drawCoins()
            })
        }
        .width('100%')
        .alignItems(HorizontalAlign.Center)

        Column().height(6)

        // 3. 子午线：六节点，自上爻(顶)到初爻(底)
        Column() {
          Stack() {
            // 中线（鎏金渐隐）—— 居中置于节点列之下
            Column() {
              Column()
                .width(1)
                .height(300)
                .linearGradient({
                  direction: GradientDirection.Bottom,
                  colors: [[DivPalette.goldHair, 0.0], [DivPalette.goldLine, 0.5], [DivPalette.goldHair, 1.0]]
                })
            }
            .width('100%')
            .height('100%')
            .alignItems(HorizontalAlign.Center)
            .justifyContent(FlexAlign.Center)

            Column() {
              ForEach([0, 1, 2, 3, 4, 5], (displayIndex: number) => {
                Column() {
                  this.MeridianNode(this.nodeData(displayIndex), POSITION_LABELS[displayIndex])
                }
                .width('100%')
              }, (index: number) => index.toString())
            }
            .width('100%')
          }
          .width('100%')
        }
        .width('100%')
        .layoutWeight(1)
        .padding({ left: 48, right: 48 })

        // 4. 掷爻 / 成卦
        DivGoldButton({
          text: this.isComplete() ? DivText.castDone : DivText.castToss,
          isEnabled: !this.tossing && !this.saving && this.pendingYao === null,
          onClick: () => {
            this.onMainButtonClick()
          }
        })
          .padding({ left: 76, right: 76, top: 14, bottom: 14 })
      }
    }
    .hideTitleBar(true)
    .onBackPressed(() => {
      // 已掷 1..5 爻或动画中 → 弃局确认；否则直接返回（对齐安卓 BackHandler）
      const tossCount = this.tossCount()
      if ((tossCount >= 1 && tossCount <= 5) || this.tossing) {
        this.quitDialogController.open()
        return true
      }
      return false
    })
  }

  /** 单个子午线节点数据（displayIndex 0=上爻(index5) … 5=初爻(index0)） */
  private nodeData(displayIndex: number): MeridianNodeData {
    const lineIndex = 5 - displayIndex
    const committed = lineIndex < this.lines.length ? this.lines[lineIndex] : null
    const isPending = this.pendingYao !== null && lineIndex === this.lines.length
    const yaoCode = committed !== null ? committed : (isPending && this.pendingYao !== null ? this.pendingYao.code : null)

    let symbol = ''
    let nameLabel = ''
    if (yaoCode !== null) {
      symbol = yaoCode === 1 || yaoCode === 3 ? YAO_SYMBOL_YANG : YAO_SYMBOL_YIN
      if (yaoCode === 1) {
        nameLabel = '少阳'
      } else if (yaoCode === 2) {
        nameLabel = '少阴'
      } else if (yaoCode === 3) {
        nameLabel = '老阳 · 动'
      } else {
        nameLabel = '老阴 · 动'
      }
    }
    return {
      symbol: symbol,
      nameLabel: nameLabel,
      isMoving: yaoCode !== null && yaoCode > 2,
      done: yaoCode !== null
    }
  }
}

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
import { encodeChartJson } from '../../../data/DivChartCodec'
import { DivRecordRepository, encodeBundle, encodeBundleToJson } from '../../../data/DivinationStore'
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
import { AppConfirmDialog } from '../../../../../view/common/AppComponents'
import { RouteNames } from '../../../../../constant/RouteNames'
import { DesignDimens } from '../../../../../constant/DesignSystem'
import { getObjectParam, pathStack } from '../../../../../utils/PathStackUtil'
import { showToastSafety } from '../../../../../utils/ToastUtil'
import { logger } from '../../../../../utils/Logger'
import { DivChartParam } from '../../DivChartParam'

/** 铜钱尺寸 / 间距（对齐安卓 CoinAndMeridian.kt:214 的 72dp 币身 + 12dp 币间距） */
const COIN_SIZE: number = 72
const COIN_GAP: number = 12
const COIN_ROW_WIDTH: number = COIN_SIZE * 3 + COIN_GAP * 2

/** 爻位名（自上爻顶到初爻底，对齐安卓 DivCastPage.kt:118） */
const POSITION_LABELS: string[] = ['上爻', '五爻', '四爻', '三爻', '二爻', '初爻']

/** 进程重建防护：待落定爻 / tossId 的 AppStorage key（对齐安卓 rememberSaveable 语义） */
const KEY_PENDING_YAO: string = 'div_cast_pending_yao'
const KEY_TOSS_ID: string = 'div_cast_toss_id'
/** 无待落定爻占位码（爻值合法范围 1..4） */
const NO_PENDING_YAO: number = -1

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
      // 文案对齐安卓 AppConfirmDialog 默认「确定/取消」（鸿蒙组件默认「确认」，此处显式覆盖）
      confirmText: '确定',
      cancelText: '取消',
      isDanger: true,
      onConfirm: () => {
        // 弃局：清空掷卦进度，避免下次进入恢复出过期状态
        logger.debug('divCastPage', `弃局确认，清空掷卦进度 tossed=${this.lines.length}`)
        this.clearProgress()
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
    this.restoreProgress()
    this.drawCoins()
    logger.debug('divCastPage', `摇卦页挂载 category=${this.category} questionLen=${this.question.length}`)
  }

  aboutToDisappear(): void {
    // 定时器硬规则：页面销毁必须清理
    if (this.coinTimerId !== -1) {
      clearInterval(this.coinTimerId)
      this.coinTimerId = -1
    }
  }

  /**
   * 恢复待落定爻与 tossId（对齐安卓 rememberSaveable：进程重建后不丢状态）。
   * 仍有待落定爻时直接提交并释放掷爻按钮，避免按钮永久禁用（对齐安卓「待落定爻缺失时复位 tossing」兜底）。
   */
  private restoreProgress(): void {
    const savedCode = AppStorage.get<number>(KEY_PENDING_YAO)
    if (savedCode !== undefined && savedCode >= 1 && savedCode <= 4) {
      try {
        this.pendingYao = YaoValue.fromCode(savedCode)
      } catch (err) {
        this.pendingYao = null
        logger.warn('divCastPage', `恢复待落定爻失败，按无待落定爻处理 savedCode=${savedCode}, ` +
          `${err instanceof Error ? err.message : ''}`)
      }
    }
    const savedTossId = AppStorage.get<number>(KEY_TOSS_ID)
    if (savedTossId !== undefined && savedTossId > 0) {
      this.tossId = savedTossId
    }
    if (this.pendingYao !== null) {
      this.commitToss(this.pendingYao)
    } else {
      this.tossing = false
    }
    this.saveProgress()
  }

  /** 持久化待落定爻与 tossId（起掷 / 落定 / 恢复后同步写） */
  private saveProgress(): void {
    AppStorage.setOrCreate(KEY_PENDING_YAO, this.pendingYao === null ? NO_PENDING_YAO : this.pendingYao.code)
    AppStorage.setOrCreate(KEY_TOSS_ID, this.tossId)
  }

  /** 成卦 / 弃局时清空进度，避免下次进入恢复出过期状态 */
  private clearProgress(): void {
    AppStorage.setOrCreate(KEY_PENDING_YAO, NO_PENDING_YAO)
    AppStorage.setOrCreate(KEY_TOSS_ID, 0)
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
    this.saveProgress()
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
    this.saveProgress()
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
      // 低频关键动作：六掷完成成卦入库成功（问事内容只记长度，不打全文）
      logger.info('divCastPage', `成卦入库成功 recordId=${id} category=${this.category} castAt=${castAt}`)
      this.navigateChart(id)
    } catch (err) {
      this.saving = false
      logger.error('divCastPage', '成卦入库失败', err instanceof Error ? err.message : '')
      showToastSafety('成卦失败，请稍后重试')
    }
  }

  private navigateChart(id: number): void {
    // 成卦后清空掷卦进度，避免下次进入恢复出过期状态
    this.clearProgress()
    pathStack.clear()
    pathStack.pushPathByName(RouteNames.DIV_HOME, null)
    const chartParam: DivChartParam = { recordId: id, animate: true }
    pathStack.pushPathByName(RouteNames.DIV_CHART, chartParam)
  }

  // ========== 铜钱绘制（Canvas 拟真：径向渐变 + 回纹内圈 + 方孔 + 正背面） ==========

  private drawCoins(): void {
    const ctx = this.coinCtx
    ctx.clearRect(0, 0, COIN_ROW_WIDTH, COIN_SIZE)
    for (let i = 0; i < 3; i++) {
      const rot = this.baseRot[i] + this.deltaRot[i] * this.progress
      // 抬升：0→45% 起跳（ease-out 到最高点），45%→100% 惯性回弹（阻尼振荡，对齐安卓 momentumSpring）
      let liftRatio: number
      if (this.progress < 0.45) {
        liftRatio = -Math.sin((this.progress / 0.45) * Math.PI / 2)
      } else {
        const t = (this.progress - 0.45) / 0.55
        liftRatio = -0.18 * Math.sin(t * Math.PI * 2) * (1 - t)
      }
      const lift = this.heights[i] * liftRatio
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

    // 币身：对角线性鎏金渐变（画板双板口径 300→500，对齐安卓 goldGradient）
    const grad = ctx.createLinearGradient(0, 0, size, size)
    grad.addColorStop(0, '#D4B878')
    grad.addColorStop(1, '#C9A961')
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

    // 方孔（透出页面底色，画板口径：宣纸底 #EBE3D1）
    const holeHalf = r * 0.27
    ctx.fillStyle = '#EBE3D1'
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
        textSize: 12,
        weight: 400,
        spacing: 0,
        color: DivPalette.textSecondary,
        textAlign: TextAlign.End
      })
        .width(40)

      Column().width(32)

      // 中列（120）：已掷仅爻符号 14/600；待掷 8dp 25% 金点
      Column() {
        if (data.done && data.symbol.length > 0) {
          DivSerifText({
            text: data.symbol,
            textSize: 14,
            weight: 600,
            spacing: 1,
            color: data.isMoving ? DivPalette.goldText : DivPalette.textPrimary,
            textAlign: TextAlign.Center
          })
        } else {
          Column()
            .width(8)
            .height(8)
            .borderRadius(4)
            .backgroundColor(DivPalette.gold)
            .opacity(0.25)
        }
      }
      .width(120)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)

      Column().width(10)

      if (data.done && data.nameLabel.length > 0) {
        DivSerifText({
          text: data.nameLabel,
          textSize: 12,
          weight: 400,
          spacing: 0,
          color: data.isMoving ? DivPalette.goldText : DivPalette.textSecondary,
          maxLines: 1
        })
          .layoutWeight(1)
      } else {
        Blank()
      }
    }
    .width('100%')
    .height(50)
    .alignItems(VerticalAlign.Center)
  }

  build() {
    NavDestination() {
      // 摇卦为全屏画布页：整页扩展到系统安全区外（页头不再额外避让，避免双重避让）
      DivinationScreen({ scrollable: false, immersive: true }) {
        Column() {
          // 滚动内容区（weight 1）：矮屏/大字体可滚动不裁切（对齐安卓 weight(1f) + verticalScroll）
          Scroll() {
            Column() {
              // 1. 标题 + 进度
              DivSerifText({
                text: DivText.castTitle,
                textSize: 14,
                weight: 500,
                spacing: 4,
                color: DivPalette.textSecondary,
                textAlign: TextAlign.Center
              })
                .width('100%')
                // 标题顶距统一 8（8pt 网格档位，替代 magic number 10）
                .margin({ top: DesignDimens.SPACE_XS })

              DivSerifText({
                text: `第 ${Math.min(this.tossCount(), 6)} 掷 · 共 6 掷`,
                textSize: 12,
                weight: 400,
                spacing: 3,
                color: DivPalette.goldText,
                textAlign: TextAlign.Center
              })
                .width('100%')
                .opacity(0.75)
                // 进度区上下间距收敛到 8 的倍数（8 / 16）
                .padding({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_MD })

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

              // 画板：铜钱底到节点区顶间距 48
              Column().height(48)

              // 3. 子午线：六节点，自上爻(顶)到初爻(底)；左右让位 PAGE_PADDING（24，横向留白统一档位）
              Column() {
                Stack() {
                  // 中线：20% 金实色（画板口径）
                  Column() {
                    Column()
                      .width(1)
                      .height(300)
                      .backgroundColor(DivPalette.gold)
                      .opacity(0.2)
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
              .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

              // 节点区下留白（画板 800 基准取 24）
              Column().height(24)
            }
            .width('100%')
          }
          .width('100%')
          .layoutWeight(1)
          .align(Alignment.TopStart)
          .scrollBar(BarState.Off)

          // 4. 掷爻 / 成卦（固定贴底）
          DivGoldButton({
            text: this.isComplete() ? DivText.castDone : DivText.castToss,
            isEnabled: !this.tossing && !this.saving && this.pendingYao === null,
            onAction: () => {
              this.onMainButtonClick()
            }
          })
            // 按钮横向留白统一 PAGE_PADDING（替代 magic number 76）
            .padding({
              left: DesignDimens.PAGE_PADDING,
              right: DesignDimens.PAGE_PADDING,
              top: 14,
              bottom: 14
            })
        }
        .width('100%')
        .height('100%')
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

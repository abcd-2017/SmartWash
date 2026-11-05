/**
 * 问事页 —— 对齐安卓 ui/page/ask/DivAskPage.kt + DivAskViewModel.kt。
 *
 * 结构：问题卡（衬线输入）→ 所问领域 chips（输入时关键词自动预选，手动选择后关掉自动）
 * → 起卦方式单选卡（按 method 分支）→ 「开始」金按钮 → 页脚安全提示。
 *
 * 关键约束：
 * - 高风险问题（医疗/生死/胎儿性别/失踪等 20 词）实时匹配 → 整页替换为分流提示，不排盘不解读。
 * - 排盘结果只在起卦瞬间计算一次并落库（chartJson 经 DivChartCodec 序列化）；
 *   跳卦盘时清空栈并回到观象台首页（对齐安卓 popUpTo(DivHome)）。
 */

import { Solar } from '../../../core/lunar/lunar'
import { GanZhi } from '../../../core/GanZhi'
import { DivCategory, DivMethod } from '../../../core/DivinationCommon'
import { LiuYaoChart } from '../../../core/liuyao/LiuYaoChart'
import { MeiHuaChart } from '../../../core/meihua/MeiHuaChart'
import { QiMenChart } from '../../../core/qimen/QiMenChart'
import { LiuRenChart } from '../../../core/liuren/LiuRenChart'
import { encodeChartJson } from '../../../data/DivChartCodec'
import { DivRecordRepository, encodeBundle, encodeBundleToJson } from '../../../data/DivinationStore'
import { DivCastParam } from '../../DivCastParam'
import {
  DivCard,
  DivCategoryChip,
  DivFooterNote,
  DivGoldButton,
  DivPageHeader,
  DivPalette,
  DivRadioRow,
  DivSeal,
  DivSectionTitle,
  DivSerifText,
  DivText
} from '../../components/DivComponents'
import { DivinationScreen } from '../../DivinationScreen'
import { DesignDimens, DesignElevation, MotionTiming } from '../../../../../constant/DesignSystem'
import { RouteNames } from '../../../../../constant/RouteNames'
import { pathStack, getStringParam } from '../../../../../utils/PathStackUtil'
import { showToastSafety } from '../../../../../utils/ToastUtil'
import { logger } from '../../../../../utils/Logger'
import { DivChartParam } from '../../DivChartParam'

/** 六爻起卦方式 */
const LIU_YAO_MODE_SHAKE: string = 'shake'
const LIU_YAO_MODE_LINES: string = 'lines'

/** 梅花起卦方式 */
const MEI_HUA_MODE_TIME: string = 'time'
const MEI_HUA_MODE_NUMBERS: string = 'numbers'

/**
 * 高风险问题关键词（对齐安卓 DivAskViewModel.HIGH_RISK_KEYWORDS）：
 * 医疗 / 生死 / 胎儿性别 / 失踪 —— 直接分流现实求助，不排盘不解读。
 */
const HIGH_RISK_KEYWORDS: string[] = [
  '病', '癌', '瘤', '医', '药', '手术', '诊断', '抑郁', '怀孕', '妊',
  '死', '自杀', '轻生', '寿命', '阳寿',
  '胎儿', '性别', '男孩', '女孩',
  '失踪', '走失', '寻人',
]

/** 领域关键词自动预选映射（对齐安卓 DivAskViewModel.CATEGORY_KEYWORDS） */
const CATEGORY_KEYWORDS: Array<[string, string[]]> = [
  ['career', ['工作', '事业', 'offer', '面试', '升职', '跳槽', '岗位', '实习', '转正']],
  ['wealth', ['财', '钱', '收入', '工资', '回款', '生意', '投资', '欠', '借款']],
  ['love', ['感情', '恋', '婚', '表白', '对象', '心动', '分手', '复合']],
  ['study', ['学', '考试', '考研', '绩点', '论文', '毕业', '保研', '四六级', '考证']],
  ['travel', ['出行', '旅行', '搬家', '出差', '车票', '远行', '返校']],
]

/** 所问领域 chips（对齐安卓 DivCategory.ALL） */
const CATEGORY_LIST: Array<[string, string]> = [
  ['career', DivText.categoryCareer],
  ['wealth', DivText.categoryWealth],
  ['love', DivText.categoryLove],
  ['study', DivText.categoryStudy],
  ['travel', DivText.categoryTravel],
  ['other', DivText.categoryOther],
]

@Builder
export function DivAskBuilder() {
  DivAskPage()
}

@ComponentV2
struct DivAskPage {
  @Local method: string = DivMethod.LIU_YAO.id
  @Local question: string = ''
  @Local category: string = DivCategory.OTHER.id
  @Local categoryAuto: boolean = true
  @Local liuYaoMode: string = LIU_YAO_MODE_SHAKE
  @Local meiHuaMode: string = MEI_HUA_MODE_TIME
  @Local safetyBlocked: boolean = false
  @Local saving: boolean = false

  async aboutToAppear(): Promise<void> {
    // 路由参数 method（默认 liuyao；非法值兜底 liuyao，对齐 DivMethod.fromId 的 runCatching 兜底）
    const param = getStringParam(RouteNames.DIV_ASK)
    const methodId = param !== null && param.length > 0 ? param : DivMethod.LIU_YAO.id
    this.method = DivMethod.ALL.find((m) => m.id === methodId) !== undefined
      ? methodId
      : DivMethod.LIU_YAO.id
    logger.debug('divAskPage', `问事页挂载 method=${this.method}`)
  }

  /** 输入实时分流：高风险整页替换 + 领域关键词自动预选（手动选择后关掉自动） */
  private onQuestionChange(text: string): void {
    this.question = text
    const blocked = this.isHighRisk(text)
    // 每输入回调为高频路径，仅在拦截状态翻转瞬间留痕（不记每次输入）
    if (blocked !== this.safetyBlocked) {
      if (blocked) {
        logger.warn('divAskPage', `命中高风险关键词，切换安全分流页 len=${text.length}`)
      } else {
        logger.debug('divAskPage', '高风险状态解除，恢复问事表单')
      }
    }
    this.safetyBlocked = blocked
    if (this.categoryAuto) {
      this.category = this.classifyCategory(text)
    }
  }

  private onCategorySelect(category: string): void {
    this.category = category
    this.categoryAuto = false
  }

  private onGo(): void {
    if (this.question.trim().length === 0) {
      return
    }
    if (this.method === DivMethod.LIU_YAO.id) {
      if (this.liuYaoMode === LIU_YAO_MODE_SHAKE) {
        const param: DivCastParam = { question: this.question.trim(), category: this.category }
        pathStack.pushPathByName(RouteNames.DIV_CAST, param)
      } else {
        this.openLinesDialog()
      }
      return
    }
    if (this.method === DivMethod.MEI_HUA.id) {
      if (this.meiHuaMode === MEI_HUA_MODE_TIME) {
        this.castMeiHuaTime()
      } else {
        this.openNumbersDialog()
      }
      return
    }
    if (this.method === DivMethod.QI_MEN.id) {
      this.castQiMen()
      return
    }
    if (this.method === DivMethod.LIU_REN.id) {
      this.castLiuRen()
    }
  }

  // ========== 排盘（结果只在起卦瞬间计算一次并落库） ==========

  private castMeiHuaTime(): void {
    const chart = MeiHuaChart.timeChart(Solar.fromDate(new Date()))
    this.saved(chart.lines, DivMethod.MEI_HUA.id, chart)
  }

  private castMeiHuaNumbers(a: number, b: number): void {
    const chart = MeiHuaChart.numbersChart(a, b)
    this.saved(chart.lines, DivMethod.MEI_HUA.id, chart)
  }

  private castQiMen(): void {
    try {
      const chart = QiMenChart.compile(Solar.fromDate(new Date()))
      this.saved([], DivMethod.QI_MEN.id, chart)
    } catch (err) {
      logger.error('divAskPage', '奇门起局失败', err instanceof Error ? err.message : '')
      showToastSafety('起局失败，请稍后重试')
    }
  }

  private castLiuRen(): void {
    try {
      const solar = Solar.fromDate(new Date())
      const lunar = solar.getLunar()
      const pair = LiuRenChart.yueJiang(solar)
      const chart = LiuRenChart.compile(
        GanZhi.parse(lunar.getDayInGanZhi()),
        GanZhi.parse(lunar.getTimeInGanZhi()),
        pair[0],
        pair[1])
      this.saved([], DivMethod.LIU_REN.id, chart)
    } catch (err) {
      // 六壬九宗门未实现宗门（伏吟/返吟/遥克等）抛异常，按 UI 口径提示「暂不支持」
      const note = err instanceof Error && err.message.length > 0 ? err.message : '起课暂不支持，敬请期待'
      logger.warn('divAskPage', `六壬起课受限 note=${note}`)
      showToastSafety(note)
    }
  }

  private castManualLines(lines: number[]): void {
    if (lines.length !== 6 || lines.some((v) => v < 1 || v > 4)) {
      return
    }
    const chart = LiuYaoChart.compile(lines, Solar.fromDate(new Date()))
    this.saved(lines, DivMethod.LIU_YAO.id, chart)
  }

  /** 落库（saving 防重）→ 跳卦盘并清空栈回到观象台首页（对齐安卓 popUpTo(DivHome)） */
  private async saved(lines: number[], method: string, chart: Object): Promise<void> {
    if (this.saving) {
      logger.debug('divAskPage', '排盘入库进行中，忽略重复触发')
      return
    }
    this.saving = true
    try {
      const chartJson = encodeChartJson(chart)
      const bundleJson = encodeBundleToJson(encodeBundle(method, chartJson))
      const id = await DivRecordRepository.save(
        method, this.category, this.question.trim(), JSON.stringify(lines), Date.now(), bundleJson)
      this.saving = false
      // 低频关键动作：问事排盘入库成功（问事内容只记长度，不打全文）
      logger.info('divAskPage', `排盘入库成功 recordId=${id} method=${method} ` +
        `category=${this.category} questionLen=${this.question.trim().length}`)
      this.navigateChart(id)
    } catch (err) {
      this.saving = false
      logger.error('divAskPage', '排盘入库失败', err instanceof Error ? err.message : '')
      showToastSafety('起卦失败，请稍后重试')
    }
  }

  private navigateChart(id: number): void {
    pathStack.clear()
    pathStack.pushPathByName(RouteNames.DIV_HOME, null)
    const chartParam: DivChartParam = { recordId: id, animate: true }
    pathStack.pushPathByName(RouteNames.DIV_CHART, chartParam)
  }

  // ========== 弹窗控制器 ==========
  //
  // 控制器必须由结构体字段持有（与 Setting.ets / DivCastPage.ets 同款）：
  // 在方法内临时 new 的控制器，框架不会把 controller 注入弹窗内容实例，
  // 弹窗内 this.controller 为 undefined，点击「装卦 / 确定」即抛
  // 「Cannot read property close of undefined」jscrash（11-04 DivAskPage.ets:669）。
  // 两个弹窗入参都是稳定闭包（不随打开时机变化），故字段持有一次即可。
  private linesDialogController: CustomDialogController = new CustomDialogController({
    builder: ManualLinesDialog({
      onConfirm: (lines: number[]) => {
        this.castManualLines(lines)
      },
      onDismiss: () => {
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  private numbersDialogController: CustomDialogController = new CustomDialogController({
    builder: NumbersDialog({
      onConfirm: (a: number, b: number) => {
        this.castMeiHuaNumbers(a, b)
      },
      onDismiss: () => {
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  private openLinesDialog(): void {
    this.linesDialogController.open()
  }

  private openNumbersDialog(): void {
    this.numbersDialogController.open()
  }

  // ========== 辅助 ==========

  private methodTail(): string {
    if (this.method === DivMethod.LIU_YAO.id) {
      return '六爻 · 摇卦'
    }
    if (this.method === DivMethod.MEI_HUA.id) {
      return '梅花 · 心易'
    }
    if (this.method === DivMethod.QI_MEN.id) {
      return '奇门 · 起局'
    }
    if (this.method === DivMethod.LIU_REN.id) {
      return '六壬 · 起课'
    }
    return ''
  }

  private isHighRisk(question: string): boolean {
    const text = question.toLowerCase()
    for (let i = 0; i < HIGH_RISK_KEYWORDS.length; i++) {
      if (text.includes(HIGH_RISK_KEYWORDS[i].toLowerCase())) {
        return true
      }
    }
    return false
  }

  private classifyCategory(question: string): string {
    const text = question.toLowerCase()
    for (let i = 0; i < CATEGORY_KEYWORDS.length; i++) {
      const words = CATEGORY_KEYWORDS[i][1]
      for (let j = 0; j < words.length; j++) {
        if (text.includes(words[j].toLowerCase())) {
          return CATEGORY_KEYWORDS[i][0]
        }
      }
    }
    return DivCategory.OTHER.id
  }

  // ========== 高风险分流页（整页替换，不排盘，引导现实求助） ==========

  @Builder
  SafetyBlockingPage() {
    Column() {
      Column().height(48)

      DivSeal({ text: DivText.safetySeal, sealSize: 44, fontSize: 26 })

      Column().height(DesignDimens.SPACE_LG)

      DivSerifText({
        text: DivText.safetyTitle,
        textSize: 18,
        weight: 600,
        spacing: 3,
        color: DivPalette.textPrimary,
        textAlign: TextAlign.Center
      })

      Column().height(12)

      DivSerifText({
        text: DivText.safetyBody,
        textSize: 16,
        weight: 400,
        spacing: 0,
        color: DivPalette.textSecondary,
        textAlign: TextAlign.Center
      })
        .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM })

      Column().height(28)

      DivGoldButton({
        text: DivText.safetyBack,
        onAction: () => {
          this.onQuestionChange('')
          pathStack.pop()
        }
      })
    }
    .width('100%')
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
    .alignItems(HorizontalAlign.Center)
  }

  /** 方式卡行分隔（画板：1px 12% 金发丝线，左右缩进 16） */
  @Builder
  MethodRowDivider() {
    Row() {
      Column()
        .width('100%')
        .height(1)
        .backgroundColor(DivPalette.hair)
    }
    .width('100%')
    .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD })
  }

  build() {
    NavDestination() {
      DivinationScreen() {
        DivPageHeader({
          title: DivText.askTitle,
          tail: this.methodTail(),
          onBack: () => {
            pathStack.pop()
          }
        })

        // 高风险分流页与问事表单的整页替换：交叉淡入淡出（对齐安卓 Crossfade，避免 0ms 硬切）
        Stack() {
          // 分流页层（隐藏时禁命中）
          Column() {
            this.SafetyBlockingPage()
          }
          .width('100%')
          .opacity(this.safetyBlocked ? 1 : 0)
          .animation({ duration: 180, curve: Curve.EaseInOut })
          .hitTestBehavior(this.safetyBlocked ? HitTestMode.Default : HitTestMode.None)

          // 问事表单层
          Column() {
          // 1. 问题卡（hint 12/16 textSecondary；卡内边距 CARD_PADDING）
          // clickable=true：DivCard 默认 enabled(false) 会禁用整棵子树（问题13 根因——
          // 输入框弹不起键盘、领域 chips/方式单选全不可点），卡自身无行点击动作，仅为放行子交互
          Column() {
            DivCard({ clickable: true }) {
              Column() {
                DivSerifText({
                  text: DivText.askHint,
                  textSize: 12,
                  weight: 400,
                  spacing: 0,
                  color: DivPalette.textSecondary
                })
                TextInput({
                  text: this.question,
                  placeholder: DivText.questionExample
                })
                  .width('100%')
                  .backgroundColor(Color.Transparent)
                  .caretColor(DivPalette.gold)
                  .fontSize(14)
                  .fontColor(DivPalette.textPrimary)
                  .fontFamily('serif')
                  .placeholderColor(DivPalette.textSecondary)
                  .margin({ top: 8 })
                  .onChange((value: string) => {
                    this.onQuestionChange(value)
                  })
              }
              .width('100%')
              .padding(DesignDimens.CARD_PADDING)
            }
          }
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

          // 2. 所问领域（自动识别 / 手动选择）
          DivSectionTitle({
            title: DivText.askCategoryLabel,
            tail: this.categoryAuto ? DivText.askCategoryAuto : DivText.askCategoryManual
          })

          Scroll() {
            Row({ space: 8 }) {
              ForEach(CATEGORY_LIST, (item: [string, string]) => {
                DivCategoryChip({
                  text: item[1],
                  selected: this.category === item[0],
                  onAction: () => {
                    this.onCategorySelect(item[0])
                  }
                })
              }, (item: [string, string]) => item[0])
            }
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
          }
          .scrollable(ScrollDirection.Horizontal)
          .scrollBar(BarState.Off)
          .width('100%')
          .align(Alignment.TopStart)

          // 3. 起卦方式
          DivSectionTitle({ title: DivText.askCastMethodLabel })

          Column() {
          // clickable=true：放行内部方式单选（DivCard 默认整树禁用，见上方问题13注释）
          DivCard({ clickable: true }) {
            Column() {
              if (this.method === DivMethod.LIU_YAO.id) {
                DivRadioRow({
                  title: DivText.castMethodShake,
                  subtitle: DivText.castMethodShakeSub,
                  selected: this.liuYaoMode === LIU_YAO_MODE_SHAKE,
                  onAction: () => {
                    this.liuYaoMode = LIU_YAO_MODE_SHAKE
                  }
                })
                this.MethodRowDivider()
                DivRadioRow({
                  title: DivText.castMethodLines,
                  subtitle: DivText.castMethodLinesSub,
                  selected: this.liuYaoMode === LIU_YAO_MODE_LINES,
                  onAction: () => {
                    this.liuYaoMode = LIU_YAO_MODE_LINES
                  }
                })
              } else if (this.method === DivMethod.MEI_HUA.id) {
                DivRadioRow({
                  title: DivText.castMethodTime,
                  subtitle: DivText.castMethodTimeSub,
                  selected: this.meiHuaMode === MEI_HUA_MODE_TIME,
                  onAction: () => {
                    this.meiHuaMode = MEI_HUA_MODE_TIME
                  }
                })
                this.MethodRowDivider()
                DivRadioRow({
                  title: DivText.castMethodNumber,
                  subtitle: DivText.castMethodNumberSub,
                  selected: this.meiHuaMode === MEI_HUA_MODE_NUMBERS,
                  onAction: () => {
                    this.meiHuaMode = MEI_HUA_MODE_NUMBERS
                  }
                })
              } else if (this.method === DivMethod.QI_MEN.id) {
                DivRadioRow({
                  title: DivText.castMethodNowJu,
                  subtitle: DivText.castMethodNowJuSub,
                  selected: true,
                  onAction: () => {
                  }
                })
            } else {
              DivRadioRow({
                title: DivText.castMethodNowKe,
                subtitle: DivText.castMethodNowKeSub,
                selected: true,
                onAction: () => {
                }
              })
            }
          }
          .width('100%')
        }
          }
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

          // 卡片间距统一 CARD_SPACING（16）
          Column().height(DesignDimens.CARD_SPACING)

          // 4. 开始
          DivGoldButton({
            text: DivText.askGo,
            isEnabled: this.question.trim().length > 0,
            onAction: () => {
              this.onGo()
            }
          })
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

          // 5. 页脚安全提示
          DivFooterNote({ text: DivText.askSafetyHint })
          }
          .width('100%')
          .opacity(this.safetyBlocked ? 0 : 1)
          .animation({ duration: 180, curve: Curve.EaseInOut })
          .hitTestBehavior(this.safetyBlocked ? HitTestMode.None : HitTestMode.Default)
        }
        .width('100%')
      }
    }
    .hideTitleBar(true)
  }
}

// ========== 手动爻值选项（0.5dp 描边 + 按压 scale 0.97） ==========

@ComponentV2
struct ManualOption {
  @Param label: string = ''
  @Param selected: boolean = false
  @Event onAction: () => void = () => {
  }
  @Local pressed: boolean = false

  build() {
    Column() {
      DivSerifText({
        text: this.label,
        textSize: 10,
        weight: 400,
        spacing: 0,
        // 选中项文字取语义金（goldText：宣纸侧墨色，对齐安卓 c.goldText 口径）
        color: this.selected ? DivPalette.goldText : DivPalette.textSecondary,
        textAlign: TextAlign.Center
      })
    }
    .padding({ left: 8, right: 8, top: 4, bottom: 4 })
    .borderRadius(10)
    .backgroundColor(this.selected ? DivPalette.gold14 : Color.Transparent)
    .border({
      width: 0.5,
      color: this.selected ? DivPalette.goldLine : DivPalette.hair,
      radius: 10
    })
    .scale({ x: this.pressed ? 0.97 : 1, y: this.pressed ? 0.97 : 1 })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressed = true
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressed = false
      }
    })
    .onClick(() => {
      this.onAction()
    })
  }
}

// ========== 手动输入六爻弹窗（逐爻选 单/拆/重○动/交×动，自初爻到上爻） ==========

@CustomDialog
struct ManualLinesDialog {
  controller: CustomDialogController
  @State lines: number[] = [1, 1, 1, 1, 1, 1]
  // 与公共弹窗同一条入场动效（对齐安卓 dialogEnterMotion：scale 0.92→1 + fade）
  @State entered: boolean = false
  onConfirm: (lines: number[]) => void = () => {
  }
  onDismiss: () => void = () => {
  }

  private playEnterMotion(): void {
    setTimeout(() => {
      animateTo(MotionTiming.DIALOG_ENTER, () => {
        this.entered = true
      })
    }, 16)
  }

  build() {
    // 外层留白容器：customStyle 弹窗内容铺满屏幕宽度，卡片背景画在内层并留左右边距（问题16）
    Column() {
      Column() {
        DivSerifText({
          text: DivText.manualTitle,
          textSize: 15,
          weight: 600,
          spacing: 3,
          color: DivPalette.textPrimary
        })
          .margin({ bottom: 12 })

      ForEach([0, 1, 2, 3, 4, 5], (index: number) => {
        Row() {
          DivSerifText({
            text: DivText.lineNames[index],
            textSize: 12,
            weight: 400,
            spacing: 0,
            color: DivPalette.textSecondary
          })
            .width(40)

          Row({ space: 6 }) {
            ForEach([1, 2, 3, 4], (code: number) => {
              ManualOption({
                label: DivText.manualOptionLabels[code - 1],
                selected: this.lines[index] === code,
                onAction: () => {
                  const next = this.lines.slice()
                  next[index] = code
                  this.lines = next
                }
              })
            }, (code: number) => code.toString())
          }
        }
        .width('100%')
        .padding({ top: 4, bottom: 4 })
        .alignItems(VerticalAlign.Center)
      }, (index: number) => index.toString())

      Column().height(DesignDimens.SPACE_MD)

        DivGoldButton({
          text: DivText.manualConfirm,
          btnHeight: 42,
          fontSize: 13,
          onAction: () => {
            this.onConfirm(this.lines)
            this.controller.close()
          }
        })
      }
      .width('100%')
      .padding(DesignDimens.CARD_PADDING)
      .borderRadius(16)
      .backgroundColor(DivPalette.surface2)
      .border({ width: 1, color: DivPalette.goldLine, radius: 16 })
      // 安卓对应 DivCard（含 level1 阴影）；弹窗内容卡与遮罩的分离由窗口层承担
      .shadow({ radius: DesignElevation.LEVEL_1, color: 'rgba(20,40,30,0.05)', offsetX: 0, offsetY: 2 })
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

// ========== 梅花报数弹窗（随口报两数 1..999） ==========

@CustomDialog
struct NumbersDialog {
  controller: CustomDialogController
  @State first: string = ''
  @State second: string = ''
  // 聚焦态：下划线指示由发丝线转金线（对齐安卓 focusedIndicatorColor = goldLine）
  @State firstFocused: boolean = false
  @State secondFocused: boolean = false
  // 与公共弹窗同一条入场动效（对齐安卓 dialogEnterMotion：scale 0.92→1 + fade）
  @State entered: boolean = false
  onConfirm: (a: number, b: number) => void = () => {
  }
  onDismiss: () => void = () => {
  }

  private playEnterMotion(): void {
    setTimeout(() => {
      animateTo(MotionTiming.DIALOG_ENTER, () => {
        this.entered = true
      })
    }, 16)
  }

  private isValid(): boolean {
    if (this.first.length === 0 || this.second.length === 0) {
      return false
    }
    const a = Number(this.first)
    const b = Number(this.second)
    return a >= 1 && a <= 999 && b >= 1 && b <= 999
  }

  private filterDigits(text: string): string {
    let out = ''
    for (let i = 0; i < text.length && out.length < 3; i++) {
      const ch = text.charAt(i)
      if (ch >= '0' && ch <= '9') {
        out += ch
      }
    }
    return out
  }

  build() {
    // 外层留白容器：customStyle 弹窗内容铺满屏幕宽度，卡片背景画在内层并留左右边距（问题16）
    Column() {
      Column() {
        DivSerifText({
          text: DivText.numbersTitle,
          textSize: 15,
          weight: 600,
          spacing: 3,
          color: DivPalette.textPrimary
        })
          .margin({ bottom: 6 })

        DivSerifText({
          text: DivText.numbersHint,
          textSize: 11,
          weight: 400,
          spacing: 0,
          color: DivPalette.textTertiary
        })
          .margin({ bottom: 14 })

        Row({ space: 12 }) {
          // 下划线指示（聚焦 goldLine / 未聚焦 hair；onFocus/onBlur 驱动）
          Column() {
            TextInput({ text: this.first, placeholder: '1–999' })
              .width('100%')
              .backgroundColor(Color.Transparent)
              .caretColor(DivPalette.gold)
              .fontSize(16)
              .fontWeight(FontWeight.Medium)
              .fontFamily('serif')
              .fontColor(DivPalette.textPrimary)
              .onFocus(() => {
                this.firstFocused = true
              })
              .onBlur(() => {
                this.firstFocused = false
              })
              .onChange((value: string) => {
                this.first = this.filterDigits(value)
              })
            Column()
              .width('100%')
              .height(1)
              .backgroundColor(this.firstFocused ? DivPalette.goldLine : DivPalette.hair)
          }
          .layoutWeight(1)

          Column() {
            TextInput({ text: this.second, placeholder: '1–999' })
              .width('100%')
              .backgroundColor(Color.Transparent)
              .caretColor(DivPalette.gold)
              .fontSize(16)
              .fontWeight(FontWeight.Medium)
              .fontFamily('serif')
              .fontColor(DivPalette.textPrimary)
              .onFocus(() => {
                this.secondFocused = true
              })
              .onBlur(() => {
                this.secondFocused = false
              })
              .onChange((value: string) => {
                this.second = this.filterDigits(value)
              })
            Column()
              .width('100%')
              .height(1)
              .backgroundColor(this.secondFocused ? DivPalette.goldLine : DivPalette.hair)
          }
          .layoutWeight(1)
        }
        .width('100%')

        Column().height(DesignDimens.SPACE_MD)

        DivGoldButton({
          text: DivText.manualConfirm,
          isEnabled: this.isValid(),
          btnHeight: 42,
          fontSize: 13,
          onAction: () => {
            this.onConfirm(Number(this.first), Number(this.second))
            this.controller.close()
          }
        })
      }
      .width('100%')
      .padding(20)
      .borderRadius(16)
      .backgroundColor(DivPalette.surface2)
      .border({ width: 1, color: DivPalette.goldLine, radius: 16 })
      // 安卓对应 DivCard（含 level1 阴影）；弹窗内容卡与遮罩的分离由窗口层承担
      .shadow({ radius: DesignElevation.LEVEL_1, color: 'rgba(20,40,30,0.05)', offsetX: 0, offsetY: 2 })
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

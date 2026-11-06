/**
 * 观象台共享 UI 组件 —— 对齐安卓 feature/divination/.../ui/components/DivCommon.kt + CompassDial.kt。
 *
 * 视觉规则（安卓 DivCommon.kt:48-51）：容器只用 1px 渐变金线描边；鎏金只作图形强调不作正文色；
 * 朱砂只出现在印章与动爻标记（颜色即信息）。子主题色板取 constant/DivTheme.ets（宣纸/玄墨双子主题）。
 *
 * 说明：本文件只放「观象台三页（首页 / 问事 / 摇卦）」共用组件；卦盘 / 解读 / 追问 / 卦历页的
 * 专用组件（六爻盘、梅花三卡、奇门九宫、六壬盘）随各自批次补充。
 */

import vibrator from '@ohos.vibrator'
import { DivColor } from '../../../../constant/DivTheme'
import { DesignDimens, DesignElevation, DesignColor } from '../../../../constant/DesignSystem'

// ========== 文案（对齐安卓 res/values/strings.xml 的 div_* 键；纯中文 demo 暂用字面量） ==========

export class DivText {
  // 首页
  static readonly title: string = '观象台'
  static readonly homeXunKongFormat: string = '旬空 %s'
  static readonly homeLedger: string = '案 卷'
  static readonly homeLedgerMore: string = '卦历 ›'
  static readonly disclaimerFooter: string = '内容基于玄学体系生成，仅供文化参考 · 不构成决策建议'
  static readonly signSeal: string = '签'
  static readonly signCardTitle: string = '今日一签 · %s'
  static readonly signCardSource: string = '梅花时间卦'
  static readonly methodLiuyaoName: string = '六爻'
  static readonly methodLiuyaoDesc: string = '摇铜钱问一事\n装卦最全 · 应期可推'
  static readonly methodLiuyaoUseFor: string = '日常决疑 · 具体事件'
  static readonly methodLiuyaoTag: string = '推 荐'
  static readonly methodMeihuaName: string = '梅花'
  static readonly methodMeihuaDesc: string = '时间数字起卦\n体用生克 · 快占一事'
  static readonly methodMeihuaUseFor: string = '见物占数 · 随机应变'
  static readonly methodQimenName: string = '奇门'
  static readonly methodQimenDesc: string = '九宫排局\n择时择方 · 趋避有道'
  static readonly methodQimenUseFor: string = '趋吉避凶 · 方位选择'
  static readonly methodLiurenName: string = '六壬'
  static readonly methodLiurenDesc: string = '月将加时 · 三传定机\n人事占验最精'
  static readonly methodLiurenUseFor: string = '精细推算 · 复杂事务'
  static readonly historyEmpty: string = '案卷尚空，且去起一卦'
  static readonly historyTitle: string = '卦历 · 案卷'
  static readonly historyNote: string = '复盘沿用原盘原时刻 · 不因当前日期重算'
  static readonly filterAll: string = '全部'

  // 问事页
  static readonly askTitle: string = '心中所问'
  static readonly askHint: string = '默念所问之事，一事一占'
  static readonly questionExample: string = '例：未来三个月能否找到合适工作？'
  static readonly askCategoryLabel: string = '所问领域'
  static readonly askCategoryAuto: string = '自动识别，可修改'
  static readonly askCategoryManual: string = '已手动选择'
  static readonly categoryCareer: string = '事业'
  static readonly categoryWealth: string = '财运'
  static readonly categoryLove: string = '感情'
  static readonly categoryStudy: string = '学业'
  static readonly categoryTravel: string = '出行'
  static readonly categoryOther: string = '其他'
  static readonly askCastMethodLabel: string = '起卦方式'
  static readonly castMethodShake: string = '静心摇卦'
  static readonly castMethodShakeSub: string = '三枚铜钱 · 六掷成卦'
  static readonly castMethodLines: string = '直接输入爻值'
  static readonly castMethodLinesSub: string = '逐爻录入单拆重交'
  static readonly castMethodTime: string = '梅花时间卦'
  static readonly castMethodTimeSub: string = '以此时此刻起卦'
  static readonly castMethodNumber: string = '随口报数'
  static readonly castMethodNumberSub: string = '心中默念两数'
  static readonly castMethodNowJu: string = '立即起局'
  static readonly castMethodNowJuSub: string = '按当前时辰拆补转盘'
  static readonly castMethodNowKe: string = '立即起课'
  static readonly castMethodNowKeSub: string = '月将加时 · 四课三传'
  static readonly askGo: string = '静 心 · 起 卦'
  static readonly askSafetyHint: string = '涉及医疗 / 安全等问题请寻求现实帮助'
  static readonly manualTitle: string = '直接输入爻值'
  static readonly manualConfirm: string = '装 卦'
  static readonly numbersTitle: string = '随口报数'
  static readonly numbersHint: string = '心中默念两个 1–999 的数'
  static readonly lineNames: string[] = ['初爻', '二爻', '三爻', '四爻', '五爻', '上爻']
  static readonly manualOptionLabels: string[] = ['单 · 阳', '拆 · 阴', '重 ○ 动', '交 × 动']

  // 高风险分流
  static readonly safetySeal: string = '止'
  static readonly safetyTitle: string = '这个问题，请寻求现实帮助'
  static readonly safetyBody: string =
    '医疗与身心健康请咨询医生或专业机构；涉及人身安全请立即联系警方（110）或校园保卫处；' +
    '寻找失踪亲友请报警处理。占卜不适用于这些问题。'
  static readonly safetyBack: string = '我知道了 · 返回'

  // 卦盘页
  static readonly chartTitle: string = '卦盘'
  static readonly chartLoading: string = '正在起盘…'
  static readonly chartMissing: string = '未找到卦盘记录'
  static readonly chartInterpret: string = '求 解 读'
  static readonly chartSaved: string = '已存入卦历'
  static readonly liuyaoYongshen: string = '用神'
  static readonly liuyaoFacts: string = '规则事实'
  static readonly meihuaTiyong: string = '体用生克（规则层）'
  static readonly qimenYongshen: string = '用神宫'
  static readonly qimenFacts: string = '宫位事实'

  // 解读页
  static readonly readingTitle: string = '解 读'
  static readonly readingAction: string = '总结与行动'
  static readonly readingFollowup: string = '继 续 追 问'
  static readonly readingShare: string = '分 享'
  static readonly readingLoading: string = '正在推演解读…'
  static readonly readingError: string = '解读暂不可用，请稍后重试'
  static readonly readingEndseal: string = '观'
  static readonly disclaimerReading: string =
    '本内容基于玄学体系生成，仅供文化爱好与思维参考，不构成任何重大人生决策的专业建议。'

  // 追问页
  static readonly followupTitle: string = '继续追问'
  static readonly followupSubtitle: string = '沿用原盘 · 不重算'
  static readonly followupInputHint: string = '基于本卦继续追问…'
  static readonly followupSend: string = '发 送'
  static readonly followupEmpty: string = '还没有追问，输入你的问题吧'
  static readonly followupThinking: string = '正在推演…'
  static readonly followupFooter: string = '—— 继续追问将沿用本卦盘推演 ——'

  // 摇卦页
  static readonly castTitle: string = '静 心 · 问 事'
  static readonly castProgress: string = '第 %d 掷 · 共 6 掷'
  static readonly castToss: string = '掷 爻'
  static readonly castDone: string = '成 卦 · 进入卦盘'
  static readonly castQuitTitle: string = '丢弃本次摇卦？'
  static readonly castQuitBody: string = '爻盘未满六掷，离开将丢弃本次摇卦结果。'
}

/** 观象台子主题色快捷访问（避免每处写 DivColor.XXX，语义与安卓 DivColors.current 对齐） */
export class DivPalette {
  static readonly textPrimary: number = DivColor.TEXT_PRIMARY_LIGHT
  static readonly textSecondary: number = DivColor.TEXT_SECONDARY_LIGHT
  static readonly textTertiary: number = DivColor.TEXT_TERTIARY_LIGHT
  static readonly gold: number = DivColor.GOLD_LIGHT
  static readonly goldHi: number = DivColor.GOLD_HI_LIGHT
  static readonly goldLine: number = DivColor.GOLD_LINE_LIGHT
  static readonly goldHair: number = DivColor.GOLD_HAIR_LIGHT
  static readonly goldSoft: number = DivColor.GOLD_SOFT_LIGHT
  static readonly gold14: number = DivColor.GOLD_14_LIGHT
  static readonly surface: number = DivColor.SURFACE_LIGHT
  static readonly surface2: number = DivColor.SURFACE_2_LIGHT
  static readonly seal: number = DivColor.SEAL_LIGHT
  static readonly sealHi: number = DivColor.SEAL_HI_LIGHT
  static readonly jade: number = DivColor.JADE_LIGHT
  static readonly hair: number = DivColor.HAIR_LIGHT
  static readonly line: number = DivColor.LINE_LIGHT
  static readonly yaoTop: number = DivColor.YAO_TOP_LIGHT
  static readonly yaoBottom: number = DivColor.YAO_BOTTOM_LIGHT
  // 文字级金强调（对齐安卓 c.goldText：宣纸画板反转为墨，保对比 §10）
  static readonly goldText: number = DivColor.TEXT_PRIMARY_LIGHT
  // 金底墨字（对齐安卓 c.onGold 0xFF151412，问卦双板一致）
  static readonly onGold: number = 0xFF151412
  // 平涂深朱印（对齐安卓 c.sealDeep 0xFFA63A30）
  static readonly sealDeep: number = 0xFFA63A30
  // 金按钮渐变（宣纸侧 300→500：安卓 goldGradient = [DivGoldHiDark, DivGoldDark]）
  static readonly goldGradientStart: number = 0xFFD4B878
  static readonly goldGradientEnd: number = 0xFFC9A961
}


/** 六神顺序（对齐安卓 LiuYaoRow 的 listOf("青龙","朱雀","勾陈","螣蛇","白虎","玄武")） */
const SPIRIT6: string[] = ['青龙', '朱雀', '勾陈', '螣蛇', '白虎', '玄武']

/** 六神色点（青/赤/白/黑/黄/紫，对齐安卓 DivColors.spirits） */
export function divSpiritColor(liuShen: string): number {
  const index = SPIRIT6.indexOf(liuShen)
  if (index === 0) {
    return DivColor.SPIRIT_QING
  }
  if (index === 1) {
    return DivColor.SPIRIT_CHI
  }
  if (index === 2) {
    return DivColor.SPIRIT_BAI
  }
  if (index === 3) {
    return DivColor.SPIRIT_HEI
  }
  if (index === 4) {
    return DivColor.SPIRIT_HUANG
  }
  if (index === 5) {
    return DivColor.SPIRIT_ZI
  }
  return DivPalette.textTertiary
}

// ========== 触感（对齐安卓 HapticEffect：动爻 SUCCESS / 静爻 MEDIUM / 点击 LIGHT） ==========

/** 观象台触感反馈：设备不支持时静默忽略（对齐安卓 performHaptic 的 try/catch 语义） */
export class DivHaptic {
  static light(): void {
    DivHaptic.run(10)
  }

  static medium(): void {
    DivHaptic.run(20)
  }

  static success(): void {
    DivHaptic.run(30)
  }

  static selection(): void {
    DivHaptic.run(10)
  }

  private static run(duration: number): void {
    try {
      vibrator.startVibration({ type: 'time', duration: duration }, { id: 0, usage: 'unknown' })
    } catch (err) {
      // 不支持触感（模拟器 / 部分设备）时忽略
    }
  }
}

/**
 * reduced-motion 判定（对齐安卓 divReduceMotion()，DivinationScreen.kt:68-70）。
 * 当前 SDK 无 ANIMATOR_DURATION_SCALE 等价 API（已查 @ohos.animator / @ohos.window 均无对应属性），
 * 故先返回常量 false —— 降级分支保留在调用点（摇卦动画 / 罗盘缓转），待 API 可用或设置页接入后替换。
 */
export function divReduceMotion(): boolean {
  return false
}

@Builder
@Builder
function emptyContent() {
}

// ========== 衬线仪式文本（卦名 / 章节标题 / 干支——衬线出现本身即"传统"语义） ==========

@ComponentV2
export struct DivSerifText {
  @Param text: string = ''
  @Param textSize: number = 15
  @Param weight: number = 600
  @Param spacing: number = 2
  @Param color: number = DivPalette.textPrimary
  @Param textAlign: TextAlign = TextAlign.Start
  @Param maxLines: number = 0
  @Param overflow: TextOverflow = TextOverflow.Ellipsis
  // 行盒高度（vp）：0 = 不限制（字号自适应，默认）；>0 时要显式对齐安卓默认行高差（如六爻行 24）
  @Param lineHeight: number = 0
  build() {
    if (this.maxLines > 0) {
      Text(this.text)
        .fontSize(this.textSize)
        .fontWeight(this.weight)
        .fontColor(this.color)
        .letterSpacing(this.spacing)
        .fontFamily('serif')
        .textAlign(this.textAlign)
        .maxLines(this.maxLines)
        .textOverflow({ overflow: this.overflow })
        .lineHeight(this.lineHeight)
    } else {
      Text(this.text)
        .fontSize(this.textSize)
        .fontWeight(this.weight)
        .fontColor(this.color)
        .letterSpacing(this.spacing)
        .fontFamily('serif')
        .textAlign(this.textAlign)
        .lineHeight(this.lineHeight)
    }
  }
}

// ========== 朱砂印章（卦名印 / 类别印 / 落款印三职） ==========

@ComponentV2
export struct DivSeal {
  @Param text: string = ''
  @Param sealSize: number = 30
  @Param fontSize: number = 18
  // flat=平涂深朱无描边（卦盘/卦历画板口径，对齐安卓 sealDeep）
  @Param flat: boolean = false

  build() {
    Column() {
      DivSerifText({
        text: this.text,
        textSize: this.fontSize,
        weight: 700,
        spacing: 1,
        // 印面文字两主题恒为宣纸白（画板双板一致）
        color: 0xFFEFE9DA,
        textAlign: TextAlign.Center
      })
    }
    .width(this.sealSize)
    .height(this.sealSize)
    .borderRadius(8)
    // flat=平涂深朱（无渐变无描边）；否则 sealHi→seal 135° 渐变
    .linearGradient({
      angle: 135,
      colors: this.flat
        ? [[DivPalette.sealDeep, 0.0], [DivPalette.sealDeep, 1.0]]
        : [[DivPalette.sealHi, 0.0], [DivPalette.seal, 1.0]]
    })
    // 非 flat 用 22% 金线（安卓 c.line）；flat 无描边
    .border(this.flat ? { width: 0, color: Color.Transparent } : { width: 1, color: DivPalette.line })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
  }
}

// ========== 四术定制线描 glyph（六爻=方孔铜钱 / 梅花=五瓣梅花环 / 奇门=九宫格 / 六壬=三层盘环） ==========

/** 圆角矩形描边（CanvasRenderingContext2D.roundRect 需 SDK 20，当前 17，用 arcTo 手绘） */
function strokeRoundRect(ctx: CanvasRenderingContext2D, x: number, y: number,
  w: number, h: number, r: number): void {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.lineTo(x + w - r, y)
  ctx.arcTo(x + w, y, x + w, y + r, r)
  ctx.lineTo(x + w, y + h - r)
  ctx.arcTo(x + w, y + h, x + w - r, y + h, r)
  ctx.lineTo(x + r, y + h)
  ctx.arcTo(x, y + h, x, y + h - r, r)
  ctx.lineTo(x, y + r)
  ctx.arcTo(x, y, x + r, y, r)
  ctx.closePath()
  ctx.stroke()
}

@ComponentV2
export struct MethodGlyph {
  @Param method: string = ''
  // 命名注意：不能叫 size（与 CustomComponent 基类的 size() 属性方法冲突导致编译错误），
  // 同 AppComponents.ets IconBox 的 sizeValue 约定
  @Param sizeValue: number = 34
  @Param tint: number = DivPalette.gold
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))

  aboutToAppear(): void {
    this.draw()
  }

  /** 24 栅格 → 实际画布（中心 12,12 / 半径 10 基准）；stroke 2dp、round 端点 */
  private draw(): void {
    const size: number = this.sizeValue
    const unit: number = size / 24
    const cx: number = size / 2
    const cy: number = size / 2
    const p = (v: number): number => v * unit
    this.ctx.clearRect(0, 0, size, size)
    this.ctx.lineCap = 'round'
    this.ctx.lineJoin = 'round'
    this.ctx.lineWidth = 2 * unit
    this.ctx.strokeStyle = this.tint

    if (this.method === 'liuyao') {
      // 方孔铜钱：外圆 + 圆角方孔
      this.ctx.beginPath()
      this.ctx.arc(cx, cy, p(10), 0, Math.PI * 2, false)
      this.ctx.stroke()
      strokeRoundRect(this.ctx, cx - p(3.5), cy - p(3.5), p(7), p(7), p(1))
      return
    }
    if (this.method === 'meihua') {
      // 五瓣梅花环：5 小圆 + 中心点
      for (let i = 0; i < 5; i++) {
        const angle: number = (i * 72 - 90) * Math.PI / 180
        const px: number = cx + p(5.5) * Math.cos(angle)
        const py: number = cy + p(5.5) * Math.sin(angle)
        this.ctx.beginPath()
        this.ctx.arc(px, py, p(3.2), 0, Math.PI * 2, false)
        this.ctx.stroke()
      }
      this.ctx.fillStyle = this.tint
      this.ctx.beginPath()
      this.ctx.arc(cx, cy, p(1.2), 0, Math.PI * 2, false)
      this.ctx.fill()
      return
    }
    if (this.method === 'qimen') {
      // 九宫格：外方（圆角）+ 十字分割
      strokeRoundRect(this.ctx, cx - p(9), cy - p(9), p(18), p(18), p(2))
      const lines: number[][] = [[-3, 0], [3, 0]]
      for (let i = 0; i < lines.length; i++) {
        const v: number = lines[i][0]
        this.ctx.beginPath()
        this.ctx.moveTo(cx + p(v), cy - p(9))
        this.ctx.lineTo(cx + p(v), cy + p(9))
        this.ctx.stroke()
        this.ctx.beginPath()
        this.ctx.moveTo(cx - p(9), cy + p(v))
        this.ctx.lineTo(cx + p(9), cy + p(v))
        this.ctx.stroke()
      }
      return
    }
    // liuren：三层盘环 + 中环四向刻度点
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, p(10), 0, Math.PI * 2, false)
    this.ctx.stroke()
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, p(6.5), 0, Math.PI * 2, false)
    this.ctx.stroke()
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, p(3), 0, Math.PI * 2, false)
    this.ctx.stroke()
    this.ctx.fillStyle = this.tint
    const dirs: number[][] = [[0, -1], [0, 1], [-1, 0], [1, 0]]
    for (let i = 0; i < dirs.length; i++) {
      this.ctx.beginPath()
      this.ctx.arc(cx + dirs[i][0] * p(4.75), cy + dirs[i][1] * p(4.75), p(0.9), 0, Math.PI * 2, false)
      this.ctx.fill()
    }
  }

  build() {
    Canvas(this.ctx)
      .width(this.sizeValue)
      .height(this.sizeValue)
      .onReady(() => {
        this.draw()
      })
  }
}

// ========== 干支天象头：两侧鎏金渐隐线 —— 干支是本模块的时钟 ==========

@ComponentV2
export struct TianXiangHeader {
  @Param text: string = ''
  @Param modifierHeight: number = 1

  build() {
    Row() {
      // 两侧线为实色 30% 金线（安卓 c.goldLine，非渐隐线）
      Column()
        .width(52)
        .height(this.modifierHeight)
        .backgroundColor(DivPalette.goldLine)

      DivSerifText({
        text: this.text,
        textSize: 12,
        weight: 500,
        spacing: 0,
        color: DivPalette.goldText,
        textAlign: TextAlign.Center
      })
        .opacity(0.75)
        .padding({ left: 8, right: 8 })

      Column()
        .width(52)
        .height(this.modifierHeight)
        .backgroundColor(DivPalette.goldLine)
    }
    .width('100%')
    .padding({ left: 20, right: 20 })
    .alignItems(VerticalAlign.Center)
    .justifyContent(FlexAlign.Center)
  }
}

// ========== 观象台页头：返回（48 热区防抖）+ 居中衬线标题（可选印章）+ 右侧尾注/卦印 + 副题 ==========

@ComponentV2
export struct DivPageHeader {
  @Param title: string = ''
  @Param showBack: boolean = true
  @Param seal: string = ''
  @Param tail: string = ''
  // flatSeal=印章平涂口径（卦盘）；titleSpacing=标题字距（卦盘 2，其余 4）
  @Param flatSeal: boolean = false
  @Param titleSpacing: number = 4
  // alignStart=无返回页左对齐口径（卦历）；sealTrailing=页头右侧固定卦印（解读）；subtitle=标题下居中副题（追问）
  @Param alignStart: boolean = false
  @Param sealTrailing: string = ''
  @Param subtitle: string = ''
  @Event onBack: () => void = () => {
  }
  // 返回连击防抖时间戳（对齐安卓 rememberDebouncedClick）
  @Local lastBackTs: number = 0

  build() {
    Column() {
      Stack({ alignContent: Alignment.Center }) {
        // 返回箭头：48dp 热区（行首让位 12，使图形左缘落在 20）；整行垂直居中
        if (this.showBack) {
          Row() {
            Column() {
              SymbolGlyph($r('sys.symbol.chevron_left'))
                .fontSize(24)
                .fontColor([DivPalette.textSecondary])
            }
            .width(48)
            .height(48)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)
            .onClick(() => {
              const now = Date.now()
              if (now - this.lastBackTs < 600) {
                return
              }
              this.lastBackTs = now
              this.onBack()
            })
          }
          .width('100%')
          .justifyContent(FlexAlign.Start)
          .padding({ left: 12 })
          .align(Alignment.Center)
        }

        // 标题（居中；alignStart 时左对齐 x20）
        // hitTestBehavior=None：标题不可点；通栏/超宽行若参与命中会挡住下层返回热区
        //（问题13：问事页 tail 行 width 100% 盖住返回键，点击无响应）
        Row() {
          if (this.seal.length > 0) {
            DivSeal({ text: this.seal, sealSize: 30, fontSize: 14, flat: this.flatSeal })
            Column().width(10)
          }
          DivSerifText({
            text: this.title,
            textSize: 18,
            weight: 600,
            spacing: this.titleSpacing,
            color: DivPalette.textPrimary
          })
        }
        .width(this.alignStart ? '100%' : 'auto')
        .justifyContent(this.alignStart ? FlexAlign.Start : FlexAlign.Center)
        .padding(this.alignStart ? { left: DesignDimens.PAGE_PADDING } : {})
        .align(Alignment.Center)
        .alignItems(VerticalAlign.Center)
        .hitTestBehavior(HitTestMode.None)

        // 右侧尾注（金 75%）— 通栏行禁命中，避免挡住下层返回热区（问题13）
        if (this.tail.length > 0) {
          Row() {
            DivSerifText({
              text: this.tail,
              textSize: 12,
              weight: 400,
              spacing: 2,
              color: DivPalette.goldText,
              textAlign: TextAlign.End
            })
              .opacity(0.75)
          }
          .width('100%')
          .justifyContent(FlexAlign.End)
          .padding({ right: DesignDimens.PAGE_PADDING })
          .align(Alignment.Center)
          .hitTestBehavior(HitTestMode.None)
        }

        // 右侧固定卦印（解读画板：平涂深朱、印文 14/700；与 tail 互斥）— 同 tail 禁命中
        if (this.sealTrailing.length > 0) {
          Row() {
            DivSeal({ text: this.sealTrailing, sealSize: 30, fontSize: 14, flat: true })
          }
          .width('100%')
          .justifyContent(FlexAlign.End)
          .padding({ right: DesignDimens.PAGE_PADDING })
          .align(Alignment.Center)
          .hitTestBehavior(HitTestMode.None)
        }
      }
      .width('100%')
      .padding({ top: this.alignStart ? 6 : 10, bottom: 10 })

      // 标题下居中副题（追问画板：下距 4、12/16 字距 2 次要色）
      if (this.subtitle.length > 0) {
        Column().height(4)
        DivSerifText({
          text: this.subtitle,
          textSize: 12,
          weight: 400,
          spacing: 2,
          color: DivPalette.textSecondary,
          textAlign: TextAlign.Center
        })
      }
    }
    .width('100%')
  }
}

// ========== 章节标题（衬线 + 字距），如「案 卷」「起卦方式」 ==========

@ComponentV2
export struct DivSectionTitle {
  @Param title: string = ''
  @Param tail: string = ''
  @Param tailClickable: boolean = false
  @Event onTailClick: () => void = () => {
  }
  // 尾注连击防抖时间戳（内聚，对齐安卓 rememberDebouncedClick）
  @Local lastTailTs: number = 0

  build() {
    Row() {
      DivSerifText({ text: this.title, textSize: 14, weight: 600, spacing: 4, color: DivPalette.textPrimary })

      Blank()

      if (this.tail.length > 0) {
        DivSerifText({
          text: this.tail,
          textSize: 12,
          weight: 400,
          spacing: 0,
          // 可点击尾注为链接墨色（宣纸侧全墨），纯信息尾注降为次要灰
          color: this.tailClickable ? DivPalette.textTertiary : DivPalette.textSecondary,
          textAlign: TextAlign.End
        })
        .onClick(() => {
          if (this.tailClickable) {
            const now = Date.now()
            if (now - this.lastTailTs < 600) {
              return
            }
            this.lastTailTs = now
            DivHaptic.selection()
            this.onTailClick()
          }
        })
      }
    }
    .width('100%')
    .padding({
      left: DesignDimens.PAGE_PADDING,
      right: DesignDimens.PAGE_PADDING,
      top: DesignDimens.SPACE_XS,
      bottom: DesignDimens.SPACE_XS
    })
    .alignItems(VerticalAlign.Center)
  }
}

// ========== 鎏金描边主按钮：金底 8% + 金线描边 + 衬线字（对齐 DivGoldButton） ==========

@ComponentV2
export struct DivGoldButton {
  @Param text: string = ''
  @Param isEnabled: boolean = true
  @Param btnHeight: number = 48
  @Param fontSize: number = 15
  // soft=解读画板口径：14% 金底 + 金/墨字（玄墨金字 / 宣纸墨字），用于次级仪式主行动
  @Param soft: boolean = false
  @Event onAction: () => void = () => {
  }
  // 按压态（scale 0.97）—— 见下方 build 说明
  @Local pressed: boolean = false

  build() {
    Column() {
      DivSerifText({
        text: this.text,
        textSize: this.fontSize,
        weight: 600,
        spacing: 3,
        // 禁用=金 70%；soft=主题感知文字金（宣纸墨 / 玄墨金）；常态=金底墨字
        color: !this.isEnabled ? 0xB31C1C1E : (this.soft ? DivPalette.goldText : DivPalette.onGold),
        textAlign: TextAlign.Center
      })
    }
    .width('100%')
    .height(this.btnHeight)
    .borderRadius(this.btnHeight / 2)
    // 金渐变实底胶囊（无描边）：禁用/soft 退回 14% 金底（对齐安卓「已保存」口径）
    .backgroundColor(!this.isEnabled || this.soft ? DivPalette.gold14 : Color.Transparent)
    .linearGradient(!this.isEnabled || this.soft ? {
      angle: 135,
      colors: [[DivPalette.gold14, 0.0], [DivPalette.gold14, 1.0]]
    } : {
      direction: GradientDirection.Right,
      colors: [[DivPalette.goldGradientStart, 0.0], [DivPalette.goldGradientEnd, 1.0]]
    })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .enabled(this.isEnabled)
    // 规范 §7：交互反馈不使用 Material ripple；按压反馈统一由 scale 0.97 承载，禁用态不缩放
    .scale({ x: this.isEnabled && this.pressed ? 0.97 : 1, y: this.isEnabled && this.pressed ? 0.97 : 1 })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressed = true
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressed = false
      }
    })
    .onClick(() => {
      if (this.isEnabled) {
        DivHaptic.light()
        this.onAction()
      }
    })
  }
}

// ========== 发丝线卡片：surface 底 + 1px 渐变金线描边（135°：亮→隐→亮）+ 宣纸侧 level1 轻阴影 ==========

@ComponentV2
export struct DivCard {
  @Param bgColor: number | null = null
  @Param lineColor: number | null = null
  @Param clickable: boolean = false
  @BuilderParam content: () => void = emptyContent
  @Event onAction: () => void = () => {
  }
  // 按压态（scale 0.97）—— 见下方 build 说明
  @Local pressed: boolean = false

  build() {
    Column() {
      this.content()
    }
    .width('100%')
    .backgroundColor(this.bgColor ?? DivPalette.surface)
    .borderRadius(16)
    // 规范 §2.4 / §3.1 卡片唯一画法 = 1px 描边 + 0 2px 12px rgba(20,40,30,.05)，安卓 v0.4 取 AppElevation.level1。
    // 观象台侧把「白底 + OUTLINE」换成「宣纸 surface + 金线」，阴影取值不变。
    // §2.4「玄墨深色卡不加投影」豁免：观象台当前固定宣纸浅卡（DivPalette 全取 *_LIGHT），本组件无深色卡消费方；
    // 全仓唯一的玄墨深色卡是 Workbench 的观象台特色卡，不经 DivCard，本身即无投影。
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: DesignColor.SHADOW_COLOR_CARD,
      offsetX: 0,
      offsetY: 2
    })
    .border({ width: 1, color: this.lineColor ?? DivPalette.goldLine, radius: 16 })
    .enabled(this.clickable)
    // 规范 §7：不使用 Material ripple（原 .stateEffect() 非通用属性，实测编译报错），按压反馈由 scale 0.97 承载
    // （对齐安卓 DivCard 的 pressable：indication = null + scaleFactor 0.97）
    .scale({ x: this.clickable && this.pressed ? 0.97 : 1, y: this.clickable && this.pressed ? 0.97 : 1 })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressed = true
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressed = false
      }
    })
    .onClick(() => {
      if (this.clickable) {
        DivHaptic.light()
        this.onAction()
      }
    })
  }
}

// ========== 问事领域 chip：48 触控胶囊；选中金底 + 金环 + 勾（弹簧缩放） ==========

@ComponentV2
export struct DivCategoryChip {
  @Param text: string = ''
  @Param selected: boolean = false
  @Param horizontalPadding: number = 12
  @Event onAction: () => void = () => {
  }

  build() {
    Row() {
      DivSerifText({
        text: this.text,
        textSize: 12,
        weight: this.selected ? 600 : 400,
        spacing: 0,
        color: this.selected ? DivPalette.goldText : DivPalette.textSecondary,
        textAlign: TextAlign.Center,
        maxLines: 1
      })
      if (this.selected) {
        DivSerifText({
          text: '✓',
          textSize: 12,
          weight: 600,
          spacing: 0,
          color: DivPalette.goldText,
          textAlign: TextAlign.Center
        })
          .padding({ left: 3 })
          .scale({ x: 1, y: 1 })
          .animation({ duration: 200, curve: Curve.EaseOut })
      }
    }
    .height(48)
    .padding({ left: this.horizontalPadding, right: this.horizontalPadding })
    .borderRadius(999)
    .backgroundColor(this.selected ? DivPalette.gold14 : DivPalette.surface)
    .border({ width: 1, color: this.selected ? DivPalette.goldLine : DivPalette.goldLine })
    // 底色/描边/字色 200ms 过渡（隐式动画）
    .animation({ duration: 200 })
    .onClick(() => {
      DivHaptic.selection()
      this.onAction()
    })
  }
}

// ========== 单选行（起卦方式）：标题 + 副题 + 金色圆形单选点（弹簧缩放） ==========

@ComponentV2
export struct DivRadioRow {
  @Param title: string = ''
  @Param subtitle: string = ''
  @Param selected: boolean = false
  @Event onAction: () => void = () => {
  }

  build() {
    Row() {
      Column() {
        // 未选行标题次级色（对齐安卓）
        DivSerifText({ text: this.title, textSize: 14, weight: 500, spacing: 0, color: this.selected ? DivPalette.textPrimary : DivPalette.textSecondary })
        if (this.subtitle.length > 0) {
          DivSerifText({
            text: this.subtitle,
            textSize: 12,
            weight: 400,
            spacing: 0,
            color: DivPalette.textSecondary
          })
            .margin({ top: 2 })
        }
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)

      Stack() {
        Column()
          .width(20)
          .height(20)
          .borderRadius(10)
          .border({ width: 1, color: this.selected ? 0xE6C9A961 : DivPalette.goldLine })

        if (this.selected) {
          Column()
            .width(8)
            .height(8)
            .borderRadius(4)
            .backgroundColor(DivPalette.gold)
            .animation({ duration: 200, curve: Curve.EaseOut })
        }
      }
      .width(20)
      .height(20)
      .alignContent(Alignment.Center)
    }
    .width('100%')
    .padding({ left: 20, right: 20, top: 12, bottom: 12 })
    .alignItems(VerticalAlign.Center)
    .onClick(() => {
      DivHaptic.selection()
      this.onAction()
    })
  }
}

// ========== 常驻脚注（免责声明） ==========

@ComponentV2
export struct DivFooterNote {
  @Param text: string = ''

  build() {
    DivSerifText({
      text: this.text,
      textSize: 12,
      weight: 400,
      spacing: 1,
      color: DivPalette.textTertiary,
      textAlign: TextAlign.Center
    })
      .padding({
        left: DesignDimens.PAGE_PADDING,
        right: DesignDimens.PAGE_PADDING,
        top: DesignDimens.SPACE_XS,
        bottom: DesignDimens.SPACE_XS
      })
  }
}

// ========== 金色水波加载指示（对齐安卓 WaveIcon：观象台金色，自带 reduced motion 静止分支） ==========

@ComponentV2
export struct DivWaveIcon {
  // 命名注意：不能叫 size（与 CustomComponent 基类的 size() 属性方法冲突导致编译错误），
  // 同 AppComponents.ets IconBox 的 sizeValue 约定
  @Param sizeValue: number = 18
  @Param tint: number = DivPalette.gold
  @Local phase: number = 0
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))
  private timerId: number = -1

  aboutToAppear(): void {
    if (!divReduceMotion()) {
      this.timerId = setInterval(() => {
        this.phase = (this.phase + 0.12) % (Math.PI * 2)
        this.drawWave()
      }, 16)
    }
    this.drawWave()
  }

  aboutToDisappear(): void {
    if (this.timerId !== -1) {
      clearInterval(this.timerId)
      this.timerId = -1
    }
  }

  private drawWave(): void {
    const size: number = this.sizeValue
    const midY: number = size / 2
    const amp: number = size * 0.22
    this.ctx.clearRect(0, 0, size, size)
    this.ctx.lineCap = 'round'
    // 三条波：主线 + 两条相位错开的淡线（安卓水波指示的三层观感）
    for (let layer = 0; layer < 3; layer++) {
      const alpha: number = layer === 0 ? 1 : (0.55 - layer * 0.18)
      const shift: number = this.phase + layer * 0.9
      this.ctx.beginPath()
      this.ctx.lineWidth = layer === 0 ? 2 : 1.4
      this.ctx.strokeStyle = this.hexWithAlpha(this.tint, alpha)
      for (let x = 0; x <= size; x++) {
        const y = midY + amp * Math.sin((x / size) * Math.PI * 2 + shift)
        if (x === 0) {
          this.ctx.moveTo(x, y)
        } else {
          this.ctx.lineTo(x, y)
        }
      }
      this.ctx.stroke()
    }
  }

  private hexWithAlpha(color: number, alpha: number): string {
    const a: number = Math.round(Math.max(0, Math.min(1, alpha)) * 255)
    const r: number = (color >>> 16) & 0xFF
    const g: number = (color >>> 8) & 0xFF
    const b: number = color & 0xFF
    return `rgba(${r}, ${g}, ${b}, ${(a / 255).toFixed(3)})`
  }

  build() {
    Canvas(this.ctx)
      .width(this.sizeValue)
      .height(this.sizeValue)
  }
}

// ========== 观象台罗盘：十二支环缓转（140s/圈）+ 中宫「卜」 ==========

const COMPASS_DEFAULT_SIZE: number = 112
const COMPASS_SPIN_PERIOD_MS: number = 140000
const COMPASS_TICK_MS: number = 50
const ZHI_LABELS: string[] = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥']

@ComponentV2
export struct CompassDial {
  @Param dialText: string = '卜'
  // 尺寸参数化（首页 104，对齐安卓 CompassDial(modifier = Modifier.size(104.dp))）
  @Param dialSize: number = COMPASS_DEFAULT_SIZE
  @Local rotation: number = 0
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))
  private timerId: number = -1

  aboutToAppear(): void {
    // reduced-motion：停转（静态盘仍完整可读），对齐安卓 CompassDial.kt:38-51
    if (!divReduceMotion()) {
      this.timerId = setInterval(() => {
        this.rotation = (this.rotation + 360 / (COMPASS_SPIN_PERIOD_MS / COMPASS_TICK_MS)) % 360
        this.drawCompass()
      }, COMPASS_TICK_MS)
    }
    this.drawCompass()
  }

  aboutToDisappear(): void {
    // 定时器硬规则：页面销毁必须清理（CLAUDE.md 已知坑）
    if (this.timerId !== -1) {
      clearInterval(this.timerId)
      this.timerId = -1
    }
  }

  /** 罗盘绘制（外环 50% 金实线 + 12% 虚环 + 十二支环文 + 中宫字；无中环/四正刻线/天心点） */
  private drawCompass(): void {
    const size: number = this.dialSize
    const cx: number = size / 2
    const cy: number = size / 2
    const radius: number = size / 2
    const spin: number = divReduceMotion() ? 0 : this.rotation

    this.ctx.clearRect(0, 0, size, size)

    this.ctx.save()
    this.ctx.translate(cx, cy)
    this.ctx.rotate(spin * Math.PI / 180)
    this.ctx.translate(-cx, -cy)

    // 外环：50% 鎏金实线（安卓 c.gold.copy(alpha = 0.5f)）
    this.ctx.lineCap = 'round'
    this.ctx.lineWidth = 1
    this.ctx.strokeStyle = 0x80C9A961
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, radius, 0, Math.PI * 2, false)
    this.ctx.stroke()

    // 内虚线环：12% 金发丝（安卓 c.hair）
    this.ctx.lineWidth = 1
    this.ctx.strokeStyle = DivPalette.hair
    this.ctx.setLineDash([6, 10])
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, radius * 0.85, 0, Math.PI * 2, false)
    this.ctx.stroke()
    this.ctx.setLineDash([])

    // 十二支环文（画板：r=0.92、12sp、金/墨 70%）
    this.ctx.font = `${12 * size / 104}px serif`
    this.ctx.fillStyle = 0xB31C1C1E
    for (let index = 0; index < ZHI_LABELS.length; index++) {
      const angle: number = (index * 30 - 90) * Math.PI / 180
      const posX: number = cx + radius * 0.92 * Math.cos(angle)
      const posY: number = cy + radius * 0.92 * Math.sin(angle)
      const metrics = this.ctx.measureText(ZHI_LABELS[index])
      this.ctx.fillText(ZHI_LABELS[index], posX - metrics.width / 2, posY + metrics.actualBoundingBoxAscent / 2)
    }
    this.ctx.restore()

    // 中宫「卜」（不随罗盘旋转，画板 22sp 衬线半粗、主题金）
    this.ctx.font = `${22 * size / 104}px serif`
    this.ctx.fillStyle = DivPalette.goldText
    const dial = this.ctx.measureText(this.dialText)
    this.ctx.fillText(this.dialText, cx - dial.width / 2, cy + dial.actualBoundingBoxAscent / 2)
  }

  build() {
    Canvas(this.ctx)
      .width(this.dialSize)
      .height(this.dialSize)
  }
}

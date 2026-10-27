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

  // 摇卦页
  static readonly castTitle: string = '静 心 · 问 事'
  static readonly castProgress: string = '第 %d 掷 · 共 6 掷'
  static readonly castToss: string = '掷 爻'
  static readonly castDone: string = '成 卦 · 进入卦盘'
  static readonly castQuitTitle: string = '丢弃本次摇卦？'
  static readonly castQuitBody: string = '爻盘未满六掷，离开将丢弃本次摇卦结果。'
}

/** 观象台子主题色快捷访问（避免每处写 DivColor.XXX，语义与安卓 DivColors.current 对齐） */
export const DivPalette = {
  textPrimary: DivColor.TEXT_PRIMARY_LIGHT,
  textSecondary: DivColor.TEXT_SECONDARY_LIGHT,
  textTertiary: DivColor.TEXT_TERTIARY_LIGHT,
  gold: DivColor.GOLD_LIGHT,
  goldHi: DivColor.GOLD_HI_LIGHT,
  goldLine: DivColor.GOLD_LINE_LIGHT,
  goldHair: DivColor.GOLD_HAIR_LIGHT,
  goldSoft: DivColor.GOLD_SOFT_LIGHT,
  gold14: DivColor.GOLD_14_LIGHT,
  surface: DivColor.SURFACE_LIGHT,
  surface2: DivColor.SURFACE_2_LIGHT,
  seal: DivColor.SEAL_LIGHT,
  sealHi: DivColor.SEAL_HI_LIGHT,
  jade: DivColor.JADE_LIGHT,
  hair: DivColor.HAIR_LIGHT,
  line: DivColor.LINE_LIGHT,
  yaoTop: DivColor.YAO_TOP_LIGHT,
  yaoBottom: DivColor.YAO_BOTTOM_LIGHT,
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

function emptyContent(): void {
}

// ========== 衬线仪式文本（卦名 / 章节标题 / 干支——衬线出现本身即"传统"语义） ==========

@ComponentV2
export struct DivSerifText {
  @Param text: string = ''
  @Param size: number = 15
  @Param weight: number = 600
  @Param spacing: number = 2
  @Param color: number = DivPalette.textPrimary
  @Param align: TextAlign = TextAlign.Start
  @Param maxLines: number = 0
  @Param overflow: TextOverflow = TextOverflow.Ellipsis

  build() {
    if (this.maxLines > 0) {
      Text(this.text)
        .fontSize(this.size)
        .fontWeight(this.weight)
        .fontColor(this.color)
        .letterSpacing(this.spacing)
        .fontFamily('serif')
        .textAlign(this.align)
        .maxLines(this.maxLines)
        .textOverflow({ overflow: this.overflow })
    } else {
      Text(this.text)
        .fontSize(this.size)
        .fontWeight(this.weight)
        .fontColor(this.color)
        .letterSpacing(this.spacing)
        .fontFamily('serif')
        .textAlign(this.align)
    }
  }
}

// ========== 朱砂印章（卦名印 / 类别印 / 落款印三职） ==========

@ComponentV2
export struct DivSeal {
  @Param text: string = ''
  @Param size: number = 30
  @Param fontSize: number = 15

  build() {
    Column() {
      DivSerifText({
        text: this.text,
        size: this.fontSize,
        weight: 700,
        spacing: 1,
        color: 0xFFF6E8DC,
        align: TextAlign.Center
      })
    }
    .width(this.size)
    .height(this.size)
    .borderRadius(6)
    .linearGradient({
      angle: 135,
      colors: [[DivPalette.sealHi, 0.0], [DivPalette.seal, 1.0]]
    })
    .border({ width: 1, color: 0x38FFFFFF })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
  }
}

// ========== 干支天象头：两侧鎏金渐隐线 —— 干支是本模块的时钟 ==========

@ComponentV2
export struct TianXiangHeader {
  @Param text: string = ''
  @Param modifierHeight: number = 1

  build() {
    Row() {
      Column()
        .width(52)
        .height(this.modifierHeight)
        .linearGradient({
          direction: GradientDirection.Right,
          colors: [[Color.Transparent, 0.0], [DivPalette.goldLine, 1.0]]
        })

      DivSerifText({
        text: this.text,
        size: 12,
        weight: 500,
        spacing: 3,
        color: DivPalette.gold,
        align: TextAlign.Center
      })
        .padding({ left: 12, right: 12 })

      Column()
        .width(52)
        .height(this.modifierHeight)
        .linearGradient({
          direction: GradientDirection.Right,
          colors: [[DivPalette.goldLine, 0.0], [Color.Transparent, 1.0]]
        })
    }
    .width('100%')
    .padding({ left: 20, right: 20 })
    .alignItems(VerticalAlign.Center)
    .justifyContent(FlexAlign.Center)
  }
}

// ========== 观象台页头：返回 + 可选印章 + 衬线标题 + 尾注 ==========

@ComponentV2
export struct DivPageHeader {
  @Param title: string = ''
  @Param showBack: boolean = true
  @Param seal: string = ''
  @Param tail: string = ''
  @Event onBack: () => void = () => {
  }

  build() {
    Row() {
      if (this.showBack) {
        Column() {
          SymbolGlyph($r('sys.symbol.chevron_left'))
            .fontSize(24)
            .fontColor([DivPalette.textSecondary])
        }
        .width(40)
        .height(40)
        .borderRadius(20)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .onClick(() => {
          this.onBack()
        })
      }

      if (this.seal.length > 0) {
        DivSeal({ text: this.seal, size: 30, fontSize: 15 })
        Column().width(10)
      }

      DivSerifText({ text: this.title, size: 17, weight: 600, spacing: 3, color: DivPalette.textPrimary })

      Blank()

      if (this.tail.length > 0) {
        DivSerifText({
          text: this.tail,
          size: 11,
          weight: 400,
          spacing: 0,
          color: DivPalette.textTertiary,
          align: TextAlign.End
        })
      }
    }
    .width('100%')
    .padding({ left: 20, right: 20, top: 10, bottom: 10 })
    .alignItems(VerticalAlign.Center)
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

  build() {
    Row() {
      DivSerifText({ text: this.title, size: 14, weight: 600, spacing: 4, color: DivPalette.textPrimary })

      Blank()

      if (this.tail.length > 0) {
        DivSerifText({
          text: this.tail,
          size: 11,
          weight: 400,
          spacing: 0,
          color: DivPalette.textTertiary,
          align: TextAlign.End
        })
        .onClick(() => {
          if (this.tailClickable) {
            DivHaptic.selection()
            this.onTailClick()
          }
        })
      }
    }
    .width('100%')
    .padding({ left: 20, right: 20, top: 8, bottom: 8 })
    .alignItems(VerticalAlign.Center)
  }
}

// ========== 鎏金描边主按钮：金底 8% + 金线描边 + 衬线字（对齐 DivGoldButton） ==========

@ComponentV2
export struct DivGoldButton {
  @Param text: string = ''
  @Param isEnabled: boolean = true
  @Param height: number = 48
  @Param fontSize: number = 15
  @Event onClick: () => void = () => {
  }

  build() {
    Column() {
      DivSerifText({
        text: this.text,
        size: this.fontSize,
        weight: 600,
        spacing: 4,
        color: this.isEnabled ? DivPalette.gold : DivPalette.textTertiary,
        align: TextAlign.Center
      })
    }
    .width('100%')
    .height(this.height)
    .borderRadius(this.height / 2)
    .backgroundColor(this.isEnabled ? DivPalette.goldSoft : Color.Transparent)
    .border({ width: 1, color: this.isEnabled ? DivPalette.goldLine : DivPalette.hair })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .enabled(this.isEnabled)
    .stateEffect(this.isEnabled)
    .onClick(() => {
      if (this.isEnabled) {
        DivHaptic.light()
        this.onClick()
      }
    })
  }
}

// ========== 发丝线卡片：surface 底 + 1px 渐变金线描边（135°：亮→隐→亮） ==========

@ComponentV2
export struct DivCard {
  @Param background: number | null = null
  @Param borderColor: number | null = null
  @Param clickable: boolean = false
  @BuilderParam content: () => void = emptyContent
  @Event onClick: () => void = () => {
  }

  build() {
    Column() {
      this.content()
    }
    .width('100%')
    .backgroundColor(this.background ?? DivPalette.surface)
    .borderRadius(16)
    .border({ width: 1, color: this.borderColor ?? DivPalette.goldLine, radius: 16 })
    .enabled(this.clickable)
    .stateEffect(this.clickable)
    .onClick(() => {
      if (this.clickable) {
        DivHaptic.light()
        this.onClick()
      }
    })
  }
}

// ========== 问事领域 chip：选中金底 + 勾，未选素底 ==========

@ComponentV2
export struct DivCategoryChip {
  @Param text: string = ''
  @Param selected: boolean = false
  @Event onClick: () => void = () => {
  }

  build() {
    Row() {
      DivSerifText({
        text: this.selected ? this.text + ' ✓' : this.text,
        size: 12,
        weight: this.selected ? 600 : 400,
        spacing: 0,
        color: this.selected ? DivPalette.gold : DivPalette.textSecondary,
        align: TextAlign.Center
      })
    }
    .padding({ left: 14, right: 14, top: 7, bottom: 7 })
    .borderRadius(16)
    .backgroundColor(this.selected ? DivPalette.gold14 : DivPalette.surface)
    .border({ width: 1, color: this.selected ? DivPalette.goldLine : DivPalette.hair })
    .onClick(() => {
      DivHaptic.selection()
      this.onClick()
    })
  }
}

// ========== 单选行（起卦方式）：标题 + 副题 + 金色圆形单选点 ==========

@ComponentV2
export struct DivRadioRow {
  @Param title: string = ''
  @Param subtitle: string = ''
  @Param selected: boolean = false
  @Event onClick: () => void = () => {
  }

  build() {
    Row() {
      Column() {
        DivSerifText({ text: this.title, size: 14, weight: 400, spacing: 0, color: DivPalette.textPrimary })
        if (this.subtitle.length > 0) {
          DivSerifText({
            text: this.subtitle,
            size: 10,
            weight: 400,
            spacing: 0,
            color: DivPalette.textTertiary
          })
            .margin({ top: 2 })
        }
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)

      Stack() {
        Column()
          .width(19)
          .height(19)
          .borderRadius(9.5)
          .border({ width: 1.6, color: this.selected ? DivPalette.gold : DivPalette.hair })

        if (this.selected) {
          Column()
            .width(9)
            .height(9)
            .borderRadius(4.5)
            .backgroundColor(DivPalette.gold)
        }
      }
      .width(19)
      .height(19)
      .alignContent(Alignment.Center)
    }
    .width('100%')
    .padding({ left: 14, right: 14, top: 12, bottom: 12 })
    .alignItems(VerticalAlign.Center)
    .onClick(() => {
      DivHaptic.selection()
      this.onClick()
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
      size: 10,
      weight: 400,
      spacing: 1,
      color: DivPalette.textTertiary,
      align: TextAlign.Center
    })
      .width('100%')
      .padding({ left: 24, right: 24, top: 14, bottom: 14 })
  }
}

// ========== 观象台罗盘：十二支环缓转（140s/圈）+ 四正金点 + 中宫「卜」 ==========

const COMPASS_SIZE: number = 112
const COMPASS_SPIN_PERIOD_MS: number = 140000
const COMPASS_TICK_MS: number = 50
const ZHI_LABELS: string[] = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥']

@ComponentV2
export struct CompassDial {
  @Param dialText: string = '卜'
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

  /** 罗盘绘制（旋转环 + 中环 + 中宫字 + 天心金点） */
  private drawCompass(): void {
    const size: number = COMPASS_SIZE
    const cx: number = size / 2
    const cy: number = size / 2
    const radius: number = size / 2
    const spin: number = divReduceMotion() ? 0 : this.rotation

    this.ctx.clearRect(0, 0, size, size)

    // 旋转环：外环鎏金渐变 + 内虚线环 + 十二支环文 + 四正刻线
    this.ctx.save()
    this.ctx.translate(cx, cy)
    this.ctx.rotate(spin * Math.PI / 180)
    this.ctx.translate(-cx, -cy)

    this.ctx.lineCap = 'round'
    this.ctx.lineWidth = 1
    this.ctx.strokeStyle = 0x80C9A961
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, radius * 0.93, 0, Math.PI * 2, false)
    this.ctx.stroke()

    this.ctx.lineWidth = 1
    this.ctx.strokeStyle = DivPalette.goldHair
    this.ctx.setLineDash([6, 10])
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, radius * 0.78, 0, Math.PI * 2, false)
    this.ctx.stroke()
    this.ctx.setLineDash([])

    // 十二支环文
    this.ctx.fillStyle = 0xBFC9A961
    for (let index = 0; index < ZHI_LABELS.length; index++) {
      const angle: number = (index * 30 - 90) * Math.PI / 180
      const posX: number = cx + radius * 0.855 * Math.cos(angle)
      const posY: number = cy + radius * 0.855 * Math.sin(angle)
      const metrics = this.ctx.measureText(ZHI_LABELS[index])
      this.ctx.fillText(ZHI_LABELS[index], posX - metrics.width / 2, posY + metrics.actualBoundingBoxAscent / 2)
    }

    // 四正刻线
    this.ctx.lineWidth = 1
    this.ctx.strokeStyle = DivPalette.goldLine
    const ticks: number[] = [0, 90, 180, 270]
    for (let i = 0; i < ticks.length; i++) {
      const angle: number = ticks[i] * Math.PI / 180
      this.ctx.beginPath()
      this.ctx.moveTo(cx + radius * 0.70 * Math.cos(angle), cy + radius * 0.70 * Math.sin(angle))
      this.ctx.lineTo(cx + radius * 0.78 * Math.cos(angle), cy + radius * 0.78 * Math.sin(angle))
      this.ctx.stroke()
    }
    this.ctx.restore()

    // 中环（不随罗盘旋转）
    this.ctx.lineWidth = 1
    this.ctx.strokeStyle = DivPalette.goldLine
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, radius * 0.5, 0, Math.PI * 2, false)
    this.ctx.stroke()

    // 中宫「卜」
    this.ctx.font = '26px serif'
    this.ctx.fillStyle = 0xEB2A251C
    const dial = this.ctx.measureText(this.dialText)
    this.ctx.fillText(this.dialText, cx - dial.width / 2, cy + dial.actualBoundingBoxAscent / 2)

    // 天心金点
    this.ctx.fillStyle = DivPalette.gold
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, 3, 0, Math.PI * 2, false)
    this.ctx.fill()
  }

  build() {
    Canvas(this.ctx)
      .width(COMPASS_SIZE)
      .height(COMPASS_SIZE)
  }
}

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
import { DesignDimens, DesignElevation } from '../../../../constant/DesignSystem'
import { resColor } from '../../../../utils/ResourceColor'
import { resString } from '../../../../utils/ResourceString'

// ========== 文案（对齐安卓 res/values/strings.xml 的 div_* 键；纯中文 demo 暂用字面量） ==========

export class DivText {
  // 首页
  static get title(): string { return resString('div_title') }
  static get homeXunKongFormat(): string { return resString('div_text_home_xun_kong_format') }
  static get homeLedger(): string { return resString('div_home_ledger') }
  static get homeLedgerMore(): string { return resString('div_home_ledger_more') }
  static get disclaimerFooter(): string { return resString('div_disclaimer_footer') }
  static get signSeal(): string { return resString('div_sign_seal') }
  static get signCardTitle(): string { return resString('div_text_sign_card_title') }
  static get signCardSource(): string { return resString('div_sign_card_source') }
  // 保存中尾缀（audit-11 #6 / compare-10 #1，安卓 div_sign_saving：保存中禁点期间的轻量 loading 反馈）
  static readonly signSaving: string = '…'
  // 案卷空态行动按钮（audit-11 #5 / compare-10 #2，安卓 div_home_ledger_go_ask：跳问卦，与「卦历 ›」双出口）
  static get ledgerGoAsk(): string { return resString('div_home_ledger_go_ask') }
  static get methodLiuyaoName(): string { return resString('div_method_liuyao_name') }
  static get methodLiuyaoDesc(): string { return resString('div_method_liuyao_desc') }
  static get methodLiuyaoUseFor(): string { return resString('div_method_liuyao_use_for') }
  static get methodLiuyaoTag(): string { return resString('div_method_liuyao_tag') }
  static get methodMeihuaName(): string { return resString('div_method_meihua_name') }
  static get methodMeihuaDesc(): string { return resString('div_method_meihua_desc') }
  static get methodMeihuaUseFor(): string { return resString('div_method_meihua_use_for') }
  static get methodQimenName(): string { return resString('div_method_qimen_name') }
  static get methodQimenDesc(): string { return resString('div_method_qimen_desc') }
  static get methodQimenUseFor(): string { return resString('div_method_qimen_use_for') }
  static get methodLiurenName(): string { return resString('div_method_liuren_name') }
  static get methodLiurenDesc(): string { return resString('div_method_liuren_desc') }
  static get methodLiurenUseFor(): string { return resString('div_method_liuren_use_for') }
  static get historyEmpty(): string { return resString('div_history_empty') }
  static get historyTitle(): string { return resString('div_history_title') }
  static get historyNote(): string { return resString('div_history_note') }
  static get filterAll(): string { return resString('div_filter_all') }
  static get historySynced(): string { return resString('div_text_history_synced') }

  // 问事页
  static get askTitle(): string { return resString('div_ask_title') }
  static get askHint(): string { return resString('div_ask_hint') }
  static get questionExample(): string { return resString('div_question_example') }
  static get askCategoryLabel(): string { return resString('div_ask_category_label') }
  static get askCategoryAuto(): string { return resString('div_ask_category_auto') }
  static get askCategoryManual(): string { return resString('div_ask_category_manual') }
  static get categoryCareer(): string { return resString('div_category_career') }
  static get categoryWealth(): string { return resString('div_category_wealth') }
  static get categoryLove(): string { return resString('div_category_love') }
  static get categoryStudy(): string { return resString('div_category_study') }
  static get categoryTravel(): string { return resString('div_category_travel') }
  static get categoryOther(): string { return resString('div_category_other') }
  static get askCastMethodLabel(): string { return resString('div_ask_cast_method_label') }
  static get castMethodShake(): string { return resString('div_cast_method_shake') }
  static get castMethodShakeSub(): string { return resString('div_cast_method_shake_sub') }
  static get castMethodLines(): string { return resString('div_cast_method_lines') }
  static get castMethodLinesSub(): string { return resString('div_cast_method_lines_sub') }
  static get castMethodTime(): string { return resString('div_sign_card_source') }
  static get castMethodTimeSub(): string { return resString('div_cast_method_time_sub') }
  static get castMethodNumber(): string { return resString('div_cast_method_number') }
  static get castMethodNumberSub(): string { return resString('div_cast_method_number_sub') }
  static get castMethodNowJu(): string { return resString('div_cast_method_now_ju') }
  static get castMethodNowJuSub(): string { return resString('div_cast_method_now_ju_sub') }
  static get castMethodNowKe(): string { return resString('div_cast_method_now_ke') }
  static get castMethodNowKeSub(): string { return resString('div_cast_method_now_ke_sub') }
  static get askGo(): string { return resString('div_ask_go') }
  // 提交中尾缀（对齐安卓 div_ask_casting：排盘入库期间的轻量 loading 反馈）
  static get askCasting(): string { return resString('div_ask_casting') }
  static get askSafetyHint(): string { return resString('div_ask_safety_hint') }
  static get manualTitle(): string { return resString('div_cast_method_lines') }
  static get manualConfirm(): string { return resString('div_manual_confirm') }
  static get numbersTitle(): string { return resString('div_cast_method_number') }
  static get numbersHint(): string { return resString('div_numbers_hint') }
  static get lineNames(): string[] { return [resString('div_yao_position_1'), resString('div_yao_position_2'), resString('div_yao_position_3'), resString('div_yao_position_4'), resString('div_yao_position_5'), resString('div_yao_position_6')] }
  static get manualOptionLabels(): string[] { return [resString('div_manual_option_yang'), resString('div_manual_option_yin'), resString('div_manual_option_lao_yang'), resString('div_manual_option_lao_yin')] }

  // 高风险分流
  static get safetySeal(): string { return resString('div_safety_seal') }
  static get safetyTitle(): string { return resString('div_safety_title') }
  static readonly safetyBody: string =
    resString('div_components_1') +
    resString('div_components_2')
  static get safetyBack(): string { return resString('div_safety_back') }

  // 卦盘页
  static get chartTitle(): string { return resString('div_chart_title') }
  static get chartLoading(): string { return resString('div_chart_loading') }
  static get chartMissing(): string { return resString('div_chart_missing') }
  static get chartInterpret(): string { return resString('div_chart_interpret') }
  static get chartSaved(): string { return resString('div_chart_saved') }
  static get chartBuilding(): string { return resString('div_text_chart_building') }
  static get chartBuildFailed(): string { return resString('div_error_create_record') }
  // 观星台零兜底拦截文案（求解读前置检查；无平台默认模型，未配置一律引导去设置页）
  static get chartDivUseClosed(): string { return resString('div_interpret_platform_disabled') }
  static get chartModelMissing(): string { return resString('div_interpret_need_config') }
  static get chartConfigCheckFailed(): string { return resString('div_text_chart_config_check_failed') }
  static get liuyaoYongshen(): string { return resString('div_liuyao_yongshen') }
  static get liuyaoFacts(): string { return resString('div_liuyao_facts') }
  static get meihuaTiyong(): string { return resString('div_meihua_tiyong') }
  static get qimenYongshen(): string { return resString('div_qimen_yongshen') }
  static get qimenFacts(): string { return resString('div_qimen_facts') }

  // 解读页
  static get readingTitle(): string { return resString('div_chart_interpret') }
  static get readingAction(): string { return resString('div_reading_action') }
  static get readingFollowup(): string { return resString('div_reading_followup') }
  static get readingShare(): string { return resString('div_reading_share') }
  static get readingLoading(): string { return resString('div_reading_loading') }
  static get readingError(): string { return resString('div_reading_error') }
  static get readingRetry(): string { return resString('div_text_reading_retry') }
  // SSE error 帧 code=428（未配置解读模型）专属引导：跳设置页配置模型
  static get readingGoConfig(): string { return resString('div_text_reading_go_config') }
  static get readingEndseal(): string { return resString('div_reading_endseal') }
  static readonly disclaimerReading: string =
    resString('div_disclaimer_reading')
  static get feedbackTitle(): string { return resString('div_feedback_title') }
  static get ratingLabel(): string { return resString('div_text_rating_label') }
  static get outcomeLabel(): string { return resString('div_text_outcome_label') }
  static get outcomeYing(): string { return resString('div_text_outcome_ying') }
  static get outcomeWei(): string { return resString('div_text_outcome_wei') }
  static get outcomeNan(): string { return resString('div_text_outcome_nan') }
  static get feedbackSubmit(): string { return resString('div_text_feedback_submit') }
  static get feedbackDone(): string { return resString('div_feedback_done') }
  static get ratingScores(): string[] { return [resString('mood_week_mon'), resString('mood_week_tue'), resString('mood_week_wed'), resString('mood_week_thu'), resString('mood_week_fri')] }

  // 追问页
  static get followupTitle(): string { return resString('div_followup_title') }
  static get followupSubtitle(): string { return resString('div_followup_subtitle') }
  static get followupInputHint(): string { return resString('div_followup_input_hint') }
  static get followupSend(): string { return resString('div_followup_send') }
  static get followupEmpty(): string { return resString('div_followup_empty') }
  static get followupThinking(): string { return resString('div_followup_thinking') }
  static get followupInterrupted(): string { return resString('div_followup_interrupted') }
  static get followupRetry(): string { return resString('div_followup_retry') }
  static get followupFooter(): string { return resString('div_followup_footer') }

  // 摇卦页
  static get castTitle(): string { return resString('div_cast_title') }
  static get castProgress(): string { return resString('div_text_cast_progress') }
  static get castToss(): string { return resString('div_cast_toss') }
  static get castDone(): string { return resString('div_cast_done') }
  static get castQuitTitle(): string { return resString('div_cast_quit_title') }
  static get castQuitBody(): string { return resString('div_cast_quit_body') }
  // 六掷完成未入库态的弃局文案（对齐安卓 div_cast_quit_done_body）
  static get castQuitDoneBody(): string { return resString('div_cast_quit_done_body') }
  // 成卦入库中尾缀（对齐安卓 div_cast_saving）
  static get castSaving(): string { return resString('div_cast_saving') }
}

/** 观象台子主题色快捷访问（避免每处写 DivColor.XXX，语义与安卓 DivColors.current 对齐）
 *
 * 全部经 resColor 读 base 资源（域级固定宣纸/玄墨双档，不随系统主题切换）：
 * 系统深色下清氧域转暗、观象台域仍固定宣纸，为已知域级主题断层（compare-08 #12）。
 */
export class DivPalette {
  static get textPrimary(): number { return resColor('DIV_TEXT_PRIMARY_LIGHT') }
  static get textSecondary(): number { return resColor('DIV_TEXT_SECONDARY_LIGHT') }
  static get textTertiary(): number { return resColor('DIV_TEXT_TERTIARY_LIGHT') }
  static get gold(): number { return resColor('DIV_GOLD_LIGHT') }
  static get goldHi(): number { return resColor('DIV_GOLD_HI_LIGHT') }
  static get goldLine(): number { return resColor('DIV_GOLD_LINE_LIGHT') }
  static get goldHair(): number { return resColor('DIV_GOLD_HAIR_LIGHT') }
  static get goldSoft(): number { return resColor('DIV_GOLD_SOFT_LIGHT') }
  static get gold14(): number { return resColor('DIV_GOLD_14_LIGHT') }
  static get surface(): number { return resColor('DIV_SURFACE_LIGHT') }
  static get surface2(): number { return resColor('DIV_SURFACE_2_LIGHT') }
  static get seal(): number { return resColor('DIV_SEAL_LIGHT') }
  static get sealHi(): number { return resColor('DIV_SEAL_HI_LIGHT') }
  static get jade(): number { return resColor('DIV_JADE_LIGHT') }
  static get hair(): number { return resColor('DIV_HAIR_LIGHT') }
  static get line(): number { return resColor('DIV_LINE_LIGHT') }
  static get yaoTop(): number { return resColor('DIV_YAO_TOP_LIGHT') }
  static get yaoBottom(): number { return resColor('DIV_YAO_BOTTOM_LIGHT') }
  static get goldText(): number { return resColor('DIV_TEXT_PRIMARY_LIGHT') }
  static get onGold(): number { return resColor('DIV_ON_GOLD') }
  static get sealDeep(): number { return resColor('DIV_SEAL_DEEP') }
  static get goldGradientStart(): number { return resColor('DIV_GOLD_GRADIENT_START') }
  static get goldGradientEnd(): number { return resColor('DIV_GOLD_GRADIENT_END') }
}

// 画板：观象台域页面水平边距 20（compare-08 #10；勿改全局 DesignDimens.PAGE_PADDING=24，清氧域仍用 24）
export const DIV_PAGE_PADDING: number = 20


/** 六神顺序（对齐安卓 LiuYaoRow 的 listOf("青龙","朱雀","勾陈","螣蛇","白虎","玄武")） */
function SPIRIT6(): string[] { return [resString('div_components_3'), resString('div_components_4'), resString('div_components_5'), resString('div_components_6'), resString('div_components_7'), resString('div_components_8')] }

/** 六神色点（青/赤/白/黑/黄/紫，对齐安卓 DivColors.spirits） */
export function divSpiritColor(liuShen: string): number {
  const index = SPIRIT6().indexOf(liuShen)
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
  // OpenType 特性（如 'tnum' 等宽数字）：空串 = 不设置（Text 默认值）
  @Param fontFeature: string = ''
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
        .fontFeature(this.fontFeature)
    } else {
      Text(this.text)
        .fontSize(this.textSize)
        .fontWeight(this.weight)
        .fontColor(this.color)
        .letterSpacing(this.spacing)
        .fontFamily('serif')
        .textAlign(this.textAlign)
        .lineHeight(this.lineHeight)
        .fontFeature(this.fontFeature)
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
    // 造型规范 §4.2「2dp 描边」：固定 2vp 与牌尺寸解耦（compare-10 #11：原 2*unit 随 34/40 牌放大 40-65%）
    this.ctx.lineWidth = 2
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
        // 画板：alignStart 左对齐 x20（compare-08 #10，观象台域边距口径；DivHistoryPage 消费）
        .padding(this.alignStart ? { left: DIV_PAGE_PADDING } : {})
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
          // 画板：观象台域页头尾注右缘 20（DIV_PAGE_PADDING，compare-15 #55）
          .padding({ right: DIV_PAGE_PADDING })
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
          .padding({ right: DIV_PAGE_PADDING })
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
        // 非仪式文本：普通 Text 无衬线无字距（compare-10 #21，对齐安卓 DivCommon.kt:283 尾链默认字体）
        Text(this.tail)
          .fontSize(12)
          // 可点击尾注为链接墨色（宣纸侧全墨），纯信息尾注降为次要灰
          .fontColor(this.tailClickable ? DivPalette.textTertiary : DivPalette.textSecondary)
          .textAlign(TextAlign.End)
          // 尾注热区 31×16vp → 补 padding 到 ≥48vp（文字行盒 16 + 上下 16，左右各 12）
          .padding({ left: 12, right: 12, top: 16, bottom: 16 })
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
      // 画板：观象台域章节标题左右 20（compare-08 #10 / compare-10 #16）
      left: DIV_PAGE_PADDING,
      right: DIV_PAGE_PADDING,
      // 可点击尾链时垂直内边距让位给 48vp 热区（热区自身即行高，compare-10 #20 对齐安卓 onTailClick != null → vertical 0）
      top: this.tailClickable ? 0 : DesignDimens.SPACE_XS,
      bottom: this.tailClickable ? 0 : DesignDimens.SPACE_XS
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
        // 禁用=墨 38%（对齐安卓 onGold @38%，compare-15 #58）；soft=主题感知文字金（宣纸墨 / 玄墨金）；常态=金底墨字
        color: !this.isEnabled ? 0x61151412 : (this.soft ? DivPalette.goldText : DivPalette.onGold),
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

// ========== 发丝线卡片：surface 底 + 1px 金发丝描边（默认 hair）+ 宣纸侧 0 2 12 轻阴影 ==========

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
    // 规范 §2.4 / §3.1 卡片唯一画法 = 1px 描边 + 0 2px 12px rgba(20,40,30,.05)：
    // blur 12 取专用令牌 CARD_SHADOW_RADIUS（compare-08 #18：原误用 LEVEL_1=1 近乎硬边，
    // 对齐安卓 softCardShadow blur 12dp），色与 offsetY 2 不变。
    // 观象台侧把「白底 + OUTLINE」换成「宣纸 surface + 金发丝」，阴影取值不变。
    // §2.4「玄墨深色卡不加投影」豁免：观象台当前固定宣纸浅卡（DivPalette 全取 *_LIGHT），本组件无深色卡消费方；
    // 全仓唯一的玄墨深色卡是 Workbench 的观象台特色卡，不经 DivCard，本身即无投影。
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: resColor('SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: 2
    })
    // 描边默认 12% 金发丝 hair（画板「卡面与背景同色仅靠发丝线区隔」口径，compare-08 #7：
    // 原 goldLine 32% 金偏重、发丝观感丢失；显式传 lineColor 的调用点不受影响）
    .border({ width: 1, color: this.lineColor ?? DivPalette.hair, radius: 16 })
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
    // 选中底宣纸实底金（对齐安卓 c.gold #8F7233，compare-15 #6）；字色仍取 goldText 墨字
    .backgroundColor(this.selected ? DivPalette.gold : DivPalette.surface)
    .border({ width: 1, color: this.selected ? DivPalette.gold : DivPalette.goldLine })
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
    // 非仪式文本：普通 Text 无衬线无字距（compare-10 #21，对齐安卓 DivFooterNote 12sp textTertiary 默认字体）
    Text(this.text)
      .fontSize(12)
      .fontColor(DivPalette.textTertiary)
      .textAlign(TextAlign.Center)
      // 画板：脚注左右 20（compare-08 #10，观象台域边距口径）
      .padding({
        left: DIV_PAGE_PADDING,
        right: DIV_PAGE_PADDING,
        // 脚注上下已取 SPACE_XS=8（对齐安卓 DivFooterNote padding top/bottom 8，compare-15 #56 核对一致）
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
function ZHI_LABELS(): string[] { return [resString('div_components_9'), resString('div_components_10'), resString('div_components_11'), resString('div_components_12'), resString('div_components_13'), resString('div_components_14'), resString('div_components_15'), resString('div_components_16'), resString('div_components_17'), resString('div_components_18'), resString('div_components_19'), resString('div_components_20')] }

@ComponentV2
export struct CompassDial {
  @Param dialText: string = resString('div_components_21')
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

    // 外环：50% 鎏金实线（安卓 c.gold.copy(alpha = 0.5f)，宣纸侧合成 0x808F7233；
    // compare-10 #9：原硬编码玄墨金 0x80C9A961 在宣纸侧偏亮）
    this.ctx.lineCap = 'round'
    this.ctx.lineWidth = 1
    this.ctx.strokeStyle = 0x808F7233
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, radius, 0, Math.PI * 2, false)
    this.ctx.stroke()

    // 内虚线环：12% 金发丝（安卓 c.hair）；安卓 dashPathEffect(6f,10f) 为 px 单位（3x 屏 ≈2/3.3dp），
    // 此处 vp 单位取 [2, 3.3] 对齐 3x 密度观感（compare-10 #9，需真机目视校准后定值）
    this.ctx.lineWidth = 1
    this.ctx.strokeStyle = DivPalette.hair
    this.ctx.setLineDash([2, 3.3])
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, radius * 0.85, 0, Math.PI * 2, false)
    this.ctx.stroke()
    this.ctx.setLineDash([])

    // 十二支环文（画板：r=0.92、12sp、金/墨 75%——0.70 玄墨侧 ≈4.35:1 破 §10 正文 4.5 红线，
    // audit-11 #3 / compare-10 #7 提至 0.75 双主题达标）
    this.ctx.font = `${12 * size / 104}px serif`
    this.ctx.fillStyle = 0xBF1C1C1E
    for (let index = 0; index < ZHI_LABELS().length; index++) {
      const angle: number = (index * 30 - 90) * Math.PI / 180
      const posX: number = cx + radius * 0.92 * Math.cos(angle)
      const posY: number = cy + radius * 0.92 * Math.sin(angle)
      const metrics = this.ctx.measureText(ZHI_LABELS()[index])
      this.ctx.fillText(ZHI_LABELS()[index], posX - metrics.width / 2, posY + metrics.actualBoundingBoxAscent / 2)
    }
    this.ctx.restore()

    // 中宫「卜」（不随罗盘旋转，画板 22sp 衬线半粗、主题金；compare-10 #8 补 600 字重对齐安卓 SemiBold）
    this.ctx.font = `600 ${22 * size / 104}px serif`
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

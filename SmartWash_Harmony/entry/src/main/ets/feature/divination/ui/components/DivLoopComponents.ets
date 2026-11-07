/**
 * 观象台占卜闭环共享 UI —— 卦例搜索 / 待回访 / 盘感三页共用。
 *
 * 蓝本：安卓闭环三页（DivSearchPage / DivNudgePage / DivStatsPage）与 history 页 DivToolEntry。
 * 视觉规则：颜色一律走观象台既有令牌（constant/DivTheme.ets 玄墨子主题体系，
 * 经 DivPalette 快捷访问）；文案对齐安卓 res/values/strings.xml 的 div_loop_* 键
 * （纯中文 demo 暂用字面量，与 DivText 同口径）。
 */

import { DivHaptic, DivPalette, DivSeal, DivSerifText } from './DivComponents'

// ========== 文案（对齐安卓 strings.xml div_loop_* 键） ==========

export class DivLoopText {
  // 卦例搜索
  static readonly searchTitle: string = '卦例搜索'
  static readonly searchHint: string = '检 问题 · 解读 · 卦例'
  static readonly searchIdle: string = '输入关键词，翻检案卷与解读'
  static readonly searchLoading: string = '正在翻检…'
  static readonly searchEmpty: string = '未检得相关卦例'
  static readonly searchError: string = '检索失败，请稍后重试'
  static readonly searchNote: string = '搜索覆盖问题与解读全文 · 点击结果复盘原盘'
  // 待回访
  static readonly nudgeTitle: string = '待回访'
  static readonly nudgeLoading: string = '正在整理回访簿…'
  static readonly nudgeEmpty: string = '暂无待回访卦例'
  static readonly nudgeCastPrefix: string = '占于'
  static readonly nudgeDatePrefix: string = '应回访'
  static readonly nudgeNote: string = '回访应验情况 · 让每一次起卦都有回响'
  static readonly goFeedback: string = '去反馈'
  static readonly ignore: string = '忽 略'
  static readonly ignoring: string = '…'
  // 盘感
  static readonly statsTitle: string = '盘感'
  static readonly statsLoading: string = '正在推演盘感…'
  static readonly statsEmpty: string = '暂无卦例可统计'
  static readonly statsError: string = '盘感拉取失败，请稍后重试'
  static readonly statsRetry: string = '重 试'
  static readonly statsTotal: string = '总卦数'
  static readonly statsFeedback: string = '已反馈'
  static readonly statsVerify: string = '应验率'
  static readonly statsMethodTitle: string = '术 数 分 布'
  static readonly statsMonthTitle: string = '月 度 趋 势'
  static readonly statsCategoryTitle: string = '所 问 领 域'
  static readonly statsEmptyChart: string = '暂无数据'
  static readonly statsNote: string = '盘感由卦例统计生成 · 仅供复盘参考'
}

/** 术数印字（六/梅/奇/壬；未知术数兜底「卦」，对齐安卓闭环页 methodSeal 防陌生值异常） */
export function divLoopMethodSeal(method: string | null): string {
  if (method === 'liuyao') {
    return '六'
  }
  if (method === 'meihua') {
    return '梅'
  }
  if (method === 'qimen') {
    return '奇'
  }
  if (method === 'liuren') {
    return '壬'
  }
  return '卦'
}

/** 类别显示名（标准领域转中文；非标准值透传后端原文，对齐安卓闭环页 categoryLabel） */
export function divLoopCategoryLabel(category: string | null): string {
  if (category === 'career') {
    return '事业'
  }
  if (category === 'wealth') {
    return '财运'
  }
  if (category === 'love') {
    return '感情'
  }
  if (category === 'study') {
    return '学业'
  }
  if (category === 'travel') {
    return '出行'
  }
  if (category === 'other') {
    return '其他'
  }
  return category ?? ''
}

/** ISO 时间短格式（MM-dd HH:mm；后端 LocalDateTime 字符串直取子串，非标准格式回原串截前 16 位） */
export function divLoopFormatTime(iso: string | null): string {
  const raw = iso ?? ''
  if (raw.length === 0) {
    return ''
  }
  return raw.length >= 16 ? raw.substring(5, 16) : raw
}

/** ISO 日期短格式（MM-dd；后端 LocalDate 字符串直取子串，非标准格式回原串截前 10 位） */
export function divLoopFormatDate(iso: string | null): string {
  const raw = iso ?? ''
  if (raw.length === 0) {
    return ''
  }
  return raw.length >= 10 ? raw.substring(5, 10) : raw
}

// ========== 状态提示（引导 / 加载 / 错误 / 空态共用：印章 + 文案，对齐安卓闭环三页 Hint） ==========

@ComponentV2
export struct DivLoopHint {
  @Param seal: string = '检'
  @Param text: string = ''

  build() {
    Column() {
      DivSeal({ text: this.seal, sealSize: 44, fontSize: 22 })
      DivSerifText({
        text: this.text,
        textSize: 12,
        weight: 400,
        spacing: 1,
        color: DivPalette.textTertiary,
        textAlign: TextAlign.Center
      })
        .width('100%')
        .padding({ left: 40, right: 40 })
        .margin({ top: 16 })
    }
    .width('100%')
    .alignItems(HorizontalAlign.Center)
    .padding({ top: 80 })
  }
}

// ========== 闭环入口（印章 + 标签 + 可选角标，对齐安卓 DivHistoryPage.DivToolEntry） ==========

@ComponentV2
export struct DivLoopEntry {
  @Param seal: string = ''
  @Param label: string = ''
  /** 待回访角标数（>0 显示朱砂角标，>9 折叠 9+） */
  @Param badge: number = 0
  @Event onAction: () => void = () => {
  }

  build() {
    Column() {
      Stack({ alignContent: Alignment.TopEnd }) {
        DivSeal({ text: this.seal, sealSize: 36, fontSize: 16, flat: true })
        if (this.badge > 0) {
          // 朱砂角标：卦历印章同款深朱底 + 宣纸白字（双板一致）
          Text(this.badge > 9 ? '9+' : `${this.badge}`)
            .fontSize(9)
            .fontWeight(FontWeight.Bold)
            .fontColor(0xFFEFE9DA)
            .textAlign(TextAlign.Center)
            .width(16)
            .height(16)
            .borderRadius(8)
            .backgroundColor(DivPalette.sealDeep)
            .offset({ x: 5, y: -3 })
        }
      }
      .width(36)
      .height(36)

      DivSerifText({
        text: this.label,
        textSize: 12,
        weight: 400,
        spacing: 0,
        color: DivPalette.textSecondary,
        textAlign: TextAlign.Center
      })
        .margin({ top: 6 })
    }
    .alignItems(HorizontalAlign.Center)
    .onClick(() => {
      DivHaptic.light()
      this.onAction()
    })
  }
}

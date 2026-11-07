/**
 * 待回访页（占卜闭环二）—— status=0 待回访卦例回访簿。
 *
 * 蓝本：安卓 ui/page/nudge/DivNudgePage.kt + DivNudgeViewModel.kt。
 * 结构：页头 → 三态（加载 / 错误可重试 / 列表），卡片带两动作：
 * 去反馈 → 既有解读页（反馈区在解读页，跳 DivReading）；忽略 → POST ignore
 * 成功后乐观移除该条（失败静默保留，下次进入可再操作）。
 *
 * 分页：首页 20 条，List 触底 onReachEnd 续拉下一页（loading / total 双防护）。
 * 跳转：服务端 recordId 先按本地 serverRecordId 关联行落回本地 id（解读页读本地
 * chartJson）；无关联行退回原 id，解读页按空态兜底。
 */

import { DivRecordRepository } from '../../../data/DivinationStore'
import { DivNudgeVo, getDivNudges, ignoreDivNudge } from '../../../network/DivinationApi'
import {
  divLoopFormatDate,
  divLoopFormatTime,
  divLoopMethodSeal,
  DivLoopHint,
  DivLoopText
} from '../../components/DivLoopComponents'
import {
  DivCard,
  DivFooterNote,
  DivGoldButton,
  DivHaptic,
  DivPageHeader,
  DivPalette,
  DivSeal,
  DivSerifText
} from '../../components/DivComponents'
import { DivinationScreen } from '../../DivinationScreen'
import { RouteNames } from '../../../../../constant/RouteNames'
import { DesignDimens } from '../../../../../constant/DesignSystem'
import { pathStack } from '../../../../../utils/PathStackUtil'
import { logger } from '../../../../../utils/Logger'

/** 回访簿状态（对齐安卓 RequestState） */
type NudgeState = 'loading' | 'success' | 'error'

/** 分页条数（对齐安卓 size=20） */
const NUDGE_PAGE_SIZE = 20

@Builder
export function DivNudgeBuilder() {
  DivNudgePage()
}

@ComponentV2
export struct DivNudgePage {
  @Local items: DivNudgeVo[] = []
  @Local phase: NudgeState = 'loading'
  /** 忽略提交中的 recordId 集（按行防重复点击，不影响整页状态） */
  @Local ignoringIds: number[] = []
  private page: number = 1
  private total: number = 0
  private loading: boolean = false

  aboutToAppear(): void {
    this.reload()
  }

  /** 刷新回访簿（回第 1 页重拉） */
  private reload(): void {
    this.page = 1
    this.load(true)
  }

  /** 滚动触底续拉下一页（loading 中 / 已拉满则忽略） */
  private loadMore(): void {
    if (this.loading || this.total <= this.items.length) {
      return
    }
    this.page += 1
    this.load(false)
  }

  private async load(reset: boolean): Promise<void> {
    if (this.loading) {
      return
    }
    this.loading = true
    if (reset) {
      this.phase = 'loading'
    }
    try {
      const resp = await getDivNudges(this.page, NUDGE_PAGE_SIZE)
      if (resp.code === 200 && resp.data !== null) {
        this.total = resp.data.total
        const records = resp.data.records
        this.items = reset ? records : this.items.concat(records)
        this.phase = 'success'
      } else {
        this.phase = 'error'
        logger.warn('divNudgePage', `回访簿拉取业务失败 code=${resp.code}`)
      }
    } catch (err) {
      this.phase = 'error'
      logger.warn('divNudgePage', `回访簿拉取失败, ${err instanceof Error ? err.message : ''}`)
    }
    this.loading = false
  }

  /** 忽略一条待回访：成功才从列表移除并同步扣减 total（乐观移除，对齐安卓 VM） */
  private async ignore(vo: DivNudgeVo): Promise<void> {
    if (this.ignoringIds.indexOf(vo.recordId) >= 0) {
      return
    }
    this.ignoringIds = this.ignoringIds.concat([vo.recordId])
    try {
      const resp = await ignoreDivNudge(vo.recordId)
      if (resp.code === 200) {
        this.items = this.items.filter((item: DivNudgeVo): boolean => item.recordId !== vo.recordId)
        this.total = Math.max(0, this.total - 1)
      } else {
        // 忽略失败静默：该条保留在列表，用户可重试或下次进入再操作
        logger.warn('divNudgePage', `忽略待回访业务失败 recordId=${vo.recordId} code=${resp.code}`)
      }
    } catch (err) {
      logger.warn('divNudgePage', `忽略待回访失败 recordId=${vo.recordId}, ` +
        `${err instanceof Error ? err.message : ''}`)
    }
    this.ignoringIds = this.ignoringIds.filter((id: number): boolean => id !== vo.recordId)
  }

  /** 去反馈：既有解读页（反馈区在解读页）；服务端 recordId 落回本地行 id */
  private async goFeedback(vo: DivNudgeVo): Promise<void> {
    let targetId: number = vo.recordId
    try {
      const local = await DivRecordRepository.getByServerRecordId(vo.recordId)
      if (local !== null) {
        targetId = local.id
      }
    } catch (err) {
      logger.warn('divNudgePage', `回访本地关联反查失败 recordId=${vo.recordId}, ` +
        `${err instanceof Error ? err.message : ''}`)
    }
    pathStack.pushPathByName(RouteNames.DIV_READING, targetId)
  }

  private isIgnoring(vo: DivNudgeVo): boolean {
    return this.ignoringIds.indexOf(vo.recordId) >= 0
  }

  build() {
    NavDestination() {
      DivinationScreen({ scrollable: false }) {
        Column() {
          DivPageHeader({
            title: DivLoopText.nudgeTitle,
            alignStart: true,
            onBack: () => {
              pathStack.pop()
            }
          })

          if (this.phase === 'loading') {
            DivLoopHint({ seal: '访', text: DivLoopText.nudgeLoading })
          } else if (this.phase === 'error') {
            Column() {
              DivLoopHint({ seal: '访', text: DivLoopText.statsError })
              DivGoldButton({
                text: DivLoopText.statsRetry,
                onAction: () => {
                  this.reload()
                }
              })
                .margin({
                  left: 40,
                  right: 40,
                  top: DesignDimens.SPACE_LG
                })
            }
            .width('100%')
          } else if (this.items.length === 0) {
            DivLoopHint({ seal: '访', text: DivLoopText.nudgeEmpty })
          } else {
            List({ space: DesignDimens.SPACE_XS }) {
              ForEach(this.items, (vo: DivNudgeVo) => {
                ListItem() {
                  NudgeCard({
                    vo: vo,
                    ignoring: this.isIgnoring(vo),
                    onGoFeedback: () => {
                      this.goFeedback(vo)
                    },
                    onIgnore: () => {
                      this.ignore(vo)
                    }
                  })
                }
              }, (vo: DivNudgeVo) => `${vo.recordId}-${this.isIgnoring(vo) ? 1 : 0}`)
            }
            .width('100%')
            .layoutWeight(1)
            .padding({
              left: DesignDimens.PAGE_PADDING,
              right: DesignDimens.PAGE_PADDING,
              top: DesignDimens.SPACE_XXS
            })
            .scrollBar(BarState.Off)
            .onReachEnd(() => {
              this.loadMore()
            })
          }

          DivFooterNote({ text: DivLoopText.nudgeNote })
        }
        .width('100%')
        .height('100%')
      }
    }
    .hideTitleBar(true)
  }
}

// ========== 回访卡片（卦例信息 + 两动作按钮，对齐安卓 DivNudgePage.NudgeCard） ==========

@ComponentV2
struct NudgeCard {
  @Param vo: DivNudgeVo | null = null
  @Param ignoring: boolean = false
  @Event onGoFeedback: () => void = () => {
  }
  @Event onIgnore: () => void = () => {
  }

  build() {
    if (this.vo !== null) {
      DivCard() {
        Column() {
          Row() {
            DivSeal({
              text: divLoopMethodSeal(this.vo.method),
              sealSize: 32,
              fontSize: 14,
              flat: true
            })
            Column() {
              Text(this.vo.question !== null && this.vo.question.length > 0 ? this.vo.question : '—')
                .fontSize(14)
                .fontColor(DivPalette.textPrimary)
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
                .width('100%')
              Text(`${DivLoopText.nudgeCastPrefix} ${divLoopFormatTime(this.vo.castAt)} · ` +
                `${DivLoopText.nudgeDatePrefix} ${divLoopFormatDate(this.vo.nudgeDate)}`)
                .fontSize(12)
                .fontColor(DivPalette.textSecondary)
                .margin({ top: 2 })
                .maxLines(2)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
                .width('100%')
            }
            .layoutWeight(1)
            .alignItems(HorizontalAlign.Start)
            .margin({ left: 12 })
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)

          Column().height(12)

          Row() {
            Blank()
            NudgeActionButton({
              label: DivLoopText.goFeedback,
              onAction: () => {
                this.onGoFeedback()
              }
            })
            Column().width(8)
            NudgeActionButton({
              label: this.ignoring ? DivLoopText.ignoring : DivLoopText.ignore,
              isEnabled: !this.ignoring,
              onAction: () => {
                this.onIgnore()
              }
            })
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)
        }
        .width('100%')
        .padding(DesignDimens.CARD_PADDING)
      }
    }
  }
}

// ========== 回访簿卡片小按钮：胶囊实底金（宣纸板口径）+ 金线描边，禁用退回 14% 金底 ==========

@ComponentV2
struct NudgeActionButton {
  @Param label: string = ''
  @Param isEnabled: boolean = true
  @Event onAction: () => void = () => {
  }
  @Local pressed: boolean = false

  build() {
    Column() {
      DivSerifText({
        text: this.label,
        textSize: 12,
        weight: 500,
        spacing: 0,
        color: DivPalette.onGold,
        textAlign: TextAlign.Center,
        maxLines: 1
      })
    }
    .height(32)
    .padding({ left: 14, right: 14 })
    .borderRadius(999)
    .backgroundColor(this.isEnabled ? DivPalette.gold : DivPalette.gold14)
    .border({ width: 1, color: DivPalette.goldLine })
    .opacity(this.isEnabled ? 1 : 0.5)
    .enabled(this.isEnabled)
    // 规范 §7：按压反馈统一 scale 0.97 承载（对齐 DivGoldButton / DivCard）
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

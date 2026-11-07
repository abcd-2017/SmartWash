/**
 * 卦例搜索页（占卜闭环一）—— 关键词防抖 300ms 搜服务端案卷问题与解读全文。
 *
 * 蓝本：安卓 ui/page/search/DivSearchPage.kt + DivSearchViewModel.kt。
 * 结构：页头 → 搜索框（问题卡同款 DivCard 容器 + 透明输入框）→ 四态
 * （引导 / 加载 / 错误 / 结果列表），首页 size=20 一次拉足（结果按相关性排序，
 * 不做滚动分页，对齐安卓「翻页挂账闭环后续迭代」）。
 *
 * 跳转：结果卡点击跳既有卦盘复盘（DivChart，原盘原时刻）。服务端 recordId 先按
 * 本地 serverRecordId 关联行落回本地 id（复盘读本地 chartJson）；无关联行退回原 id，
 * 卦盘页按「未找到卦盘记录」空态兜底。
 *
 * 红线：本页只做检索与跳转，不改卦历 / 卦盘既有逻辑。
 */

import { DivRecordRepository } from '../../../data/DivinationStore'
import { DivRecordSearchVo, searchDivRecords } from '../../../network/DivinationApi'
import {
  divLoopCategoryLabel,
  divLoopFormatTime,
  divLoopMethodSeal,
  DivLoopHint,
  DivLoopText
} from '../../components/DivLoopComponents'
import {
  DivCard,
  DivFooterNote,
  DivPageHeader,
  DivPalette,
  DivSeal
} from '../../components/DivComponents'
import { DivinationScreen } from '../../DivinationScreen'
import { RouteNames } from '../../../../../constant/RouteNames'
import { DesignDimens } from '../../../../../constant/DesignSystem'
import { pathStack } from '../../../../../utils/PathStackUtil'
import { logger } from '../../../../../utils/Logger'

/** 搜索页状态（对齐安卓 RequestState：idle 引导 / loading / success / error） */
type SearchState = 'idle' | 'loading' | 'success' | 'error'

/** 防抖间隔（对齐安卓 VM debounce 300ms） */
const SEARCH_DEBOUNCE_MS = 300
/** 首页拉取条数（对齐安卓 size=20 一次拉足） */
const SEARCH_PAGE_SIZE = 20

@Builder
export function DivSearchBuilder() {
  DivSearchPage()
}

@ComponentV2
export struct DivSearchPage {
  @Local keyword: string = ''
  @Local results: DivRecordSearchVo[] = []
  @Local phase: SearchState = 'idle'
  /** 防抖定时器（aboutToDisappear 必须清理，CLAUDE.md 硬规则） */
  private debounceId: number = -1
  /** 请求序号：连输时丢弃上一轮迟到的响应（对齐安卓 collectLatest 取消语义） */
  private reqSeq: number = 0

  aboutToDisappear(): void {
    if (this.debounceId !== -1) {
      clearTimeout(this.debounceId)
      this.debounceId = -1
    }
  }

  /** 关键词输入：防抖 300ms 后检索；清空立即回 idle 态（恢复引导文案，不残留旧结果） */
  private onKeywordInput(value: string): void {
    this.keyword = value
    if (this.debounceId !== -1) {
      clearTimeout(this.debounceId)
      this.debounceId = -1
    }
    const trimmed = value.trim()
    if (trimmed.length === 0) {
      this.reqSeq++
      this.results = []
      this.phase = 'idle'
      return
    }
    this.debounceId = setTimeout(() => {
      this.debounceId = -1
      this.doSearch()
    }, SEARCH_DEBOUNCE_MS)
  }

  /** 键盘搜索键立即检索（跳过剩余防抖等待） */
  private searchNow(): void {
    if (this.debounceId !== -1) {
      clearTimeout(this.debounceId)
      this.debounceId = -1
    }
    this.doSearch()
  }

  private async doSearch(): Promise<void> {
    const trimmed = this.keyword.trim()
    if (trimmed.length === 0) {
      return
    }
    const seq = ++this.reqSeq
    this.phase = 'loading'
    try {
      const resp = await searchDivRecords(trimmed, 1, SEARCH_PAGE_SIZE)
      if (seq !== this.reqSeq) {
        return
      }
      if (resp.code === 200 && resp.data !== null) {
        this.results = resp.data.records
        this.phase = 'success'
      } else {
        this.results = []
        this.phase = 'error'
        logger.warn('divSearchPage', `卦例搜索业务失败 code=${resp.code}`)
      }
    } catch (err) {
      if (seq !== this.reqSeq) {
        return
      }
      this.results = []
      this.phase = 'error'
      logger.warn('divSearchPage', `卦例搜索失败, ${err instanceof Error ? err.message : ''}`)
    }
  }

  /** 结果卡点击：服务端 recordId 落回本地行 id 后进卦盘复盘 */
  private async openResult(vo: DivRecordSearchVo): Promise<void> {
    let targetId: number = vo.recordId
    try {
      const local = await DivRecordRepository.getByServerRecordId(vo.recordId)
      if (local !== null) {
        targetId = local.id
      }
    } catch (err) {
      // 反查失败退回服务端原 id（卦盘页空态兜底），warn 留痕
      logger.warn('divSearchPage', `搜索结果本地关联反查失败 recordId=${vo.recordId}, ` +
        `${err instanceof Error ? err.message : ''}`)
    }
    pathStack.pushPathByName(RouteNames.DIV_CHART, targetId)
  }

  build() {
    NavDestination() {
      DivinationScreen({ scrollable: false }) {
        Column() {
          DivPageHeader({
            title: DivLoopText.searchTitle,
            alignStart: true,
            onBack: () => {
              pathStack.pop()
            }
          })

          // 搜索框（问卦问题卡同款容器：DivCard + 透明输入框）
          Column() {
            DivCard() {
              TextInput({ text: this.keyword, placeholder: DivLoopText.searchHint })
                .width('100%')
                .height(48)
                .backgroundColor(Color.Transparent)
                .borderRadius(12)
                .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
                .fontSize(14)
                .fontColor(DivPalette.textPrimary)
                .placeholderColor(DivPalette.textSecondary)
                .placeholderFont({ size: 14 })
                .caretColor(DivPalette.gold)
                .enterKeyType(EnterKeyType.Search)
                .onChange((value: string) => {
                  this.onKeywordInput(value)
                })
                .onSubmit(() => {
                  this.searchNow()
                })
            }
          }
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

          Column().height(12)

          // 四态：引导 / 加载 / 错误 / 空态共用印章提示，结果列表互相替换
          if (this.phase === 'idle') {
            DivLoopHint({ seal: '检', text: DivLoopText.searchIdle })
          } else if (this.phase === 'loading') {
            DivLoopHint({ seal: '检', text: DivLoopText.searchLoading })
          } else if (this.phase === 'error') {
            DivLoopHint({ seal: '检', text: DivLoopText.searchError })
          } else if (this.results.length === 0) {
            DivLoopHint({ seal: '检', text: DivLoopText.searchEmpty })
          } else {
            List({ space: DesignDimens.SPACE_XS }) {
              ForEach(this.results, (vo: DivRecordSearchVo) => {
                ListItem() {
                  DivCard({
                    clickable: true,
                    onAction: () => {
                      this.openResult(vo)
                    }
                  }) {
                    // 画板：卦历卡同款内距，印章 32 + 问句 14/20 + 元信息 12/14 + 预览两行 12/16
                    Row() {
                      DivSeal({
                        text: divLoopMethodSeal(vo.method),
                        sealSize: 32,
                        fontSize: 14,
                        flat: true
                      })
                      Column() {
                        Text(vo.question !== null && vo.question.length > 0 ? vo.question : '—')
                          .fontSize(14)
                          .fontColor(DivPalette.textPrimary)
                          .maxLines(1)
                          .textOverflow({ overflow: TextOverflow.Ellipsis })
                          .width('100%')
                        Text(`${divLoopCategoryLabel(vo.category)} · ${divLoopFormatTime(vo.castAt)}`)
                          .fontSize(12)
                          .fontColor(DivPalette.textSecondary)
                          .margin({ top: 2 })
                          .maxLines(1)
                          .textOverflow({ overflow: TextOverflow.Ellipsis })
                          .width('100%')
                        if (vo.contentPreview !== null && vo.contentPreview.length > 0) {
                          Text(vo.contentPreview)
                            .fontSize(12)
                            .fontColor(DivPalette.textTertiary)
                            .margin({ top: 4 })
                            .maxLines(2)
                            .textOverflow({ overflow: TextOverflow.Ellipsis })
                            .width('100%')
                        }
                      }
                      .layoutWeight(1)
                      .alignItems(HorizontalAlign.Start)
                      .margin({ left: 12 })
                      Text('›')
                        .fontSize(14)
                        .fontColor(DivPalette.textSecondary)
                    }
                    .width('100%')
                    .alignItems(VerticalAlign.Center)
                    .padding({
                      left: DesignDimens.CARD_PADDING,
                      right: DesignDimens.SPACE_XS,
                      top: DesignDimens.SPACE_SM,
                      bottom: DesignDimens.SPACE_SM
                    })
                  }
                }
              }, (vo: DivRecordSearchVo) => vo.recordId.toString())
            }
            .width('100%')
            .layoutWeight(1)
            .padding({
              left: DesignDimens.PAGE_PADDING,
              right: DesignDimens.PAGE_PADDING,
              top: DesignDimens.SPACE_XXS
            })
            .scrollBar(BarState.Off)
          }

          DivFooterNote({ text: DivLoopText.searchNote })
        }
        .width('100%')
        .height('100%')
      }
    }
    .hideTitleBar(true)
  }
}

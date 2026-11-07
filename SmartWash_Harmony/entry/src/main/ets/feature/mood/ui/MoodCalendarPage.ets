// 月历热图页 — 对齐安卓 feature/mood/ui/calendar/MoodCalendarPage.kt
//
// 7×N 网格按 dateKey（yyyy-MM-dd）查表着色（无记录灰格、未来格更浅），
// 历史色按契约优先用 color 快照（非法值兜底词典基准 hex）。
// 点有记录的格子弹出当日详情（契约月历不含 note）；‹ › 切月。
//
// 跨页刷新：保存今日心情后 bump 情绪 tick，本页 @StorageLink + @Watch 消费重拉。

import { RouteNames } from '../../../constant/RouteNames'
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import { MoodCalendarDayVo, MoodText, moodMonthCalendar, moodRecordedDaysFormat } from '../api'
import {
  MoodCalendarCell,
  MoodDayCell,
  MoodDayDetailDialog,
  MoodErrorState,
  MoodMonthSwitcher,
  currentYm,
  dateKeyOf,
  daysInMonth,
  firstOffset,
  formatDayText,
  formatMonthText,
  normalizeMoodHex,
  shiftYm,
  todayKey
} from './MoodWidgets'
import { AppCard, SkeletonList } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { pathStack } from '../../../utils/PathStackUtil'
import { MOOD_TICK_KEY } from '../../../utils/RefreshSignal'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'moodCalendarPage'

/** 周表头（周一起始，与网格 offset 对齐） */
const WEEK_LABELS: Array<string> = [
  MoodText.mood_week_mon,
  MoodText.mood_week_tue,
  MoodText.mood_week_wed,
  MoodText.mood_week_thu,
  MoodText.mood_week_fri,
  MoodText.mood_week_sat,
  MoodText.mood_week_sun
]

/** 单元格槽位高（对齐安卓 CELL_HEIGHT 44） */
const CELL_SLOT_HEIGHT: number = 44

@Builder
export function MoodCalendarBuilder() {
  MoodCalendarPage()
}

@Component
struct MoodCalendarPage {
  @State ym: string = currentYm()
  @State cells: Array<MoodCalendarCell> = []
  @State isLoading: boolean = true
  @State isError: boolean = false
  // 跨页刷新信号（自增 tick）：保存今日心情后 bump，返回本页时重拉
  @StorageLink(MOOD_TICK_KEY) @Watch('onMoodTick') moodTick: number = 0
  /** dateKey → 当日记录（无记录日期端上缺省灰格） */
  private dayRecords: Map<string, MoodCalendarDayVo> = new Map<string, MoodCalendarDayVo>()

  async aboutToAppear(): Promise<void> {
    await this.load(this.ym)
  }

  /** 消费情绪变更 tick：重拉当前月（自增保证每次变更都触发） */
  private onMoodTick(): void {
    logger.debug(TAG, '消费到情绪变更信号，重拉月历')
    this.load(this.ym)
  }

  /** 拉当月月历并构建网格（失败可重试，草稿不受影响） */
  async load(ym: string): Promise<void> {
    this.isLoading = true
    this.isError = false
    try {
      const res = await moodMonthCalendar(ym)
      if (res.code === BizCode.SUCCESS) {
        this.buildCells(ym, res.data ?? [])
        this.isError = false
        logger.debug(TAG, `月历加载成功 ym=${ym} records=${this.dayRecords.size}`)
      } else {
        logger.warn(TAG, `月历加载失败 ym=${ym} code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.isError = true
        // 文案对齐安卓基线「月历加载失败」（勿用 mood_error_load 的「心情记录加载失败」）
        showToastSafety(res.message, '月历加载失败')
      }
    } catch (err) {
      logger.error(TAG, '月历加载异常', err instanceof Error ? err.message : '')
      this.isError = true
      showToastSafety('', '月历加载失败')
    } finally {
      this.isLoading = false
    }
  }

  /** 切月（delta 正=往后，负=往前）；跨月/未来月/空月接口返回空数组，端上灰格 */
  private shiftMonth(delta: number): void {
    this.ym = shiftYm(this.ym, delta)
    this.load(this.ym)
  }

  /** 构建网格：月初前占位空格 + 有记录着色 + 今日描边 + 未来灰格 */
  private buildCells(ym: string, days: Array<MoodCalendarDayVo>): void {
    const map = new Map<string, MoodCalendarDayVo>()
    for (const day of days) {
      if (day.date !== undefined && day.date.length > 0) {
        map.set(day.date, day)
      }
    }
    this.dayRecords = map

    const offset = firstOffset(ym)
    const total = daysInMonth(ym)
    const today = todayKey()
    const cells: Array<MoodCalendarCell> = []
    for (let i = 0; i < offset; i++) {
      cells.push({
        day: 0,
        dateKey: '',
        level: 0,
        colorHex: '',
        isToday: false,
        isFuture: false,
        hasRecord: false
      })
    }
    for (let d = 1; d <= total; d++) {
      const key = dateKeyOf(ym, d)
      const record = map.get(key)
      cells.push({
        day: d,
        dateKey: key,
        level: record?.level ?? 0,
        colorHex: record !== undefined ? normalizeMoodHex(record.color, record.level ?? 0) : '',
        isToday: key === today,
        isFuture: key > today,
        hasRecord: record !== undefined
      })
    }
    this.cells = cells
  }

  /** 点有记录的格子：弹出当日详情（契约月历不含 note，不展示备注） */
  private openDayDetail(dateKey: string): void {
    const record = this.dayRecords.get(dateKey)
    if (record === undefined) {
      return
    }
    const controller: CustomDialogController = new CustomDialogController({
      builder: MoodDayDetailDialog({
        dateText: formatDayText(dateKey),
        level: record.level ?? 0,
        word: record.word ?? '',
        onDismiss: (): void => {
          // 弹窗自行关闭，无需额外处理
        }
      }),
      autoCancel: true,
      alignment: DialogAlignment.Center,
      customStyle: true
    })
    controller.open()
  }

  build() {
    NavDestination() {
      Column() {
        // 页面标题（对齐安卓 PageHeader）
        Row() {
          Column() {
            SymbolGlyph($r('sys.symbol.chevron_left'))
              .fontSize(24)
              .fontColor([DesignColor.TEXT_PRIMARY])
          }
          .width(DesignDimens.BACK_HIT_AREA)
          .height(DesignDimens.BACK_HIT_AREA)
          .justifyContent(FlexAlign.Center)
          .alignItems(HorizontalAlign.Center)
          .onClick(() => {
            pathStack.pop()
          })

          Text(MoodText.mood_calendar_title)
            .fontSize(DesignFontSize.ROOT_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
        }
        .width('100%')
        .height(56)
        .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
        .alignItems(VerticalAlign.Center)

        if (this.isLoading) {
          SkeletonList({ rows: 4 })
            .width('100%')
            .layoutWeight(1)
        } else if (this.isError) {
          MoodErrorState({
            message: '月历加载失败',
            onRetry: (): void => {
              this.load(this.ym)
            }
          })
            .width('100%')
            .layoutWeight(1)
        } else {
          Column() {
            MoodMonthSwitcher({
              monthText: formatMonthText(this.ym),
              onPrev: (): void => {
                this.shiftMonth(-1)
              },
              onNext: (): void => {
                this.shiftMonth(1)
              }
            })

            AppCard() {
              Column() {
                // ---------- 周表头（周一起始） ----------
                Row() {
                  ForEach(WEEK_LABELS, (label: string) => {
                    Text(label)
                      .fontSize(DesignFontSize.CAPTION)
                      .fontColor(DesignColor.TEXT_TERTIARY)
                      .textAlign(TextAlign.Center)
                      .layoutWeight(1)
                  }, (label: string) => label)
                }
                .width('100%')
                .margin({ bottom: DesignDimens.SPACE_XS })

                // ---------- 7×N 热图网格 ----------
                Grid() {
                  ForEach(this.cells, (cell: MoodCalendarCell, index: number) => {
                    GridItem() {
                      if (cell.day === 0) {
                        Column()
                          .width('100%')
                          .height(CELL_SLOT_HEIGHT)
                      } else {
                        MoodDayCell({
                          day: cell.day,
                          dateKey: cell.dateKey,
                          hasRecord: cell.hasRecord,
                          level: cell.level,
                          colorHex: cell.colorHex,
                          isToday: cell.isToday,
                          isFuture: cell.isFuture,
                          onSelect: (dateKey: string): void => {
                            this.openDayDetail(dateKey)
                          }
                        })
                      }
                    }
                  }, (cell: MoodCalendarCell, index: number) => `${index}`)
                }
                .columnsTemplate('1fr 1fr 1fr 1fr 1fr 1fr 1fr')
                .width('100%')
                .height(Math.ceil(this.cells.length / 7) * CELL_SLOT_HEIGHT)
              }
              .width('100%')
              .alignItems(HorizontalAlign.Start)
            }

            // ---------- 统计与图例说明 ----------
            Column() {
              Text(moodRecordedDaysFormat(this.dayRecords.size))
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontColor(DesignColor.TEXT_SECONDARY)

              Text(MoodText.mood_calendar_day_empty_hint)
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontColor(DesignColor.TEXT_TERTIARY)
                .margin({ top: DesignDimens.SPACE_XXS })
            }
            .width('100%')
            .alignItems(HorizontalAlign.Start)
          }
          .width('100%')
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_SM,
            bottom: DesignDimens.SPACE_LG + safeBottomVp()
          })
          .alignItems(HorizontalAlign.Start)
          .layoutWeight(1)
        }
      }
      .width('100%')
      .height('100%')
      // 沉浸式：内容避让状态栏（背景延伸）
      .padding({ top: safeTopVp() })
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
  }
}

// 今日记录页 — 对齐安卓 feature/mood/ui/today/MoodTodayPage.kt
//
// pixy 式低负担：五个心情色块一次点按即存（upsert 覆盖），词/一句话为可选增强。
// 进入页面拉当月月历（契约无「查今日」端点），命中今天则回填 level/word
// （月历契约不含 note，备注不回填）；保存成功后用返回的当日记录整体回填。
//
// 跨页刷新：保存成功后 bump 情绪 tick（自增），月历/趋势页 onShown/@Watch 消费重拉。

import { BusinessError } from '@kit.BasicServicesKit'
import { RouteNames } from '../../../constant/RouteNames'
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BaseResponse } from '../../../network/BaseResponse'
import { BizCode } from '../../../network/HttpUtil'
import {
  MOOD_LEVEL_MAX,
  MOOD_LEVEL_MIN,
  MOOD_NOTE_MAX_LENGTH,
  MoodCalendarDayVo,
  MoodRecordVo,
  MoodText,
  SaveMoodForm,
  moodMonthCalendar,
  saveMoodToday
} from '../api'
import {
  MoodLevelRow,
  MoodSmallAction,
  MoodWordChip,
  currentYm,
  formatTodayText,
  moodSnapshotHex,
  todayKey
} from './MoodWidgets'
import { AppCard, SkeletonList } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { pathStack } from '../../../utils/PathStackUtil'
import { bumpMoodRefresh } from '../../../utils/RefreshSignal'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'moodTodayPage'

/** 词候选（"累/顺/静/燥/燃"等天气词，资源单一事实来源） */
const MOOD_WORDS: Array<string> = [
  MoodText.mood_word_tired,
  MoodText.mood_word_smooth,
  MoodText.mood_word_quiet,
  MoodText.mood_word_restless,
  MoodText.mood_word_burning
]

@Builder
export function MoodTodayBuilder() {
  MoodTodayPage()
}

@Component
struct MoodTodayPage {
  /** 0 = 未选；1..5 = 心情等级 */
  @State draftLevel: number = 0
  @State word: string = ''
  @State note: string = ''
  @State hasRecord: boolean = false
  @State isLoading: boolean = true
  @State isError: boolean = false
  /** 保存失败内联错误（保留后端 message） */
  @State saveError: string = ''
  @State saving: boolean = false
  @State noteFocused: boolean = false
  /** 最近一次已成功保存的等级（保存期间又点别的等级时补一次保存） */
  private lastSavedLevel: number = -1

  async aboutToAppear(): Promise<void> {
    await this.load()
  }

  /** 拉当月月历并回填今日记录（失败可重试，草稿不受影响） */
  async load(): Promise<void> {
    this.isLoading = true
    this.isError = false
    try {
      const res = await moodMonthCalendar(currentYm())
      if (res.code === BizCode.SUCCESS) {
        const days: Array<MoodCalendarDayVo> = res.data ?? []
        const record = days.find((d: MoodCalendarDayVo) => d.date === todayKey())
        if (record !== undefined) {
          // 月历契约无 note，备注不回填；只回填 level/word
          this.draftLevel = record.level ?? 0
          this.word = record.word ?? ''
          this.hasRecord = true
        } else {
          this.draftLevel = 0
          this.word = ''
          this.hasRecord = false
        }
        logger.debug(TAG, `今日记录回填完成 hasRecord=${this.hasRecord}`)
      } else {
        logger.warn(TAG, `今日记录加载失败 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.isError = true
        showToastSafety(res.message, MoodText.mood_error_load)
      }
    } catch (err) {
      logger.error(TAG, '今日记录加载异常', err instanceof Error ? err.message : '')
      this.isError = true
      showToastSafety('', MoodText.mood_error_load)
    } finally {
      this.isLoading = false
    }
  }

  /** 点心情色块：一次点按即存（重复点同一色块仍提交——覆盖语义对重复点击幂等） */
  private selectLevel(level: number): void {
    if (level < MOOD_LEVEL_MIN || level > MOOD_LEVEL_MAX) {
      return
    }
    this.draftLevel = level
    this.saveError = ''
    this.triggerSave()
  }

  /** 点词 chip：再点一次取消；当日已有 level 时即时保存，否则停留为草稿 */
  private toggleWord(word: string): void {
    this.word = this.word === word ? '' : word
    if (this.draftLevel > 0) {
      this.saveError = ''
      this.triggerSave()
    }
  }

  /** 备注提交（键盘「完成」触发）：当日已有 level 时即时保存 */
  private commitNote(): void {
    if (this.draftLevel > 0) {
      this.saveError = ''
      this.triggerSave()
    }
  }

  /**
   * 保存（upsert）。color 快照取词典基准 hex；word/note 空白按 null 存。
   * 成功后以服务端返回整体回填草稿（含 trim 后的 word/note），与服务器对齐。
   */
  private triggerSave(): void {
    if (this.saving || this.draftLevel <= 0) {
      return
    }
    this.saving = true
    const level = this.draftLevel
    const trimmedWord = this.word.trim()
    const trimmedNote = this.note.trim()
    const form: SaveMoodForm = {
      level: level,
      color: moodSnapshotHex(level),
      word: trimmedWord.length > 0 ? trimmedWord : undefined,
      note: trimmedNote.length > 0 ? trimmedNote : undefined
    }
    saveMoodToday(form).then((res: BaseResponse<MoodRecordVo>) => {
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const vo: MoodRecordVo = res.data
        this.draftLevel = vo.level ?? level
        this.word = vo.word ?? ''
        this.note = vo.note ?? ''
        this.hasRecord = true
        this.saveError = ''
        this.lastSavedLevel = this.draftLevel
        // 月历/趋势页据此在返回时重拉（自增 tick，避免布尔标记不触发）
        bumpMoodRefresh()
        logger.info(TAG, `心情记录保存成功 level=${this.draftLevel}（${this.hasRecord ? '覆盖' : '新建'}）`)
      } else {
        logger.warn(TAG, `心情记录保存被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.saveError = res.message ?? MoodText.mood_error_save
      }
    }).catch((err: BusinessError) => {
      logger.error(TAG, '心情记录保存异常', err.message)
      this.saveError = MoodText.mood_error_save
    }).finally(() => {
      this.saving = false
      // 保存期间又点了别的等级：补一次保存，避免最后一次选择丢失
      if (this.draftLevel > 0 && this.draftLevel !== this.lastSavedLevel) {
        this.triggerSave()
      }
    })
  }

  build() {
    NavDestination() {
      Column() {
        // 页面标题 + 月历/趋势入口（对齐安卓 PageHeader）
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

          Text(MoodText.mood_title)
            .fontSize(DesignFontSize.ROOT_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)

          Blank()

          Row({ space: DesignDimens.SPACE_XS }) {
            MoodSmallAction({
              text: MoodText.mood_action_calendar,
              onAction: (): void => {
                pathStack.pushPathByName(RouteNames.MOOD_CALENDAR, null)
              }
            })
            MoodSmallAction({
              text: MoodText.mood_action_trend,
              onAction: (): void => {
                pathStack.pushPathByName(RouteNames.MOOD_TREND, null)
              }
            })
          }
        }
        .width('100%')
        .height(56)
        .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
        .alignItems(VerticalAlign.Center)

        if (this.isLoading) {
          SkeletonList({ rows: 3 })
            .width('100%')
            .layoutWeight(1)
        } else {
          // 月历查询失败不阻塞心情记录（对齐安卓 17c32c15）：内容照常渲染，
          // 选择器/词 chips/一句话全部可用（保存链路独立报错），月历回填失败单独给错误条 + 重试
          Scroll() {
            Column() {
              // ---------- 月历回填错误条（独立于保存链路） ----------
              if (this.isError) {
                Column() {
                  AppCard() {
                    Row() {
                      Text(MoodText.mood_error_load)
                        .fontSize(DesignFontSize.BODY_SMALL)
                        .fontColor(DesignColor.ERROR)
                        .layoutWeight(1)

                      MoodSmallAction({
                        text: MoodText.mood_action_retry,
                        contentColor: DesignColor.PRIMARY,
                        onAction: (): void => {
                          this.load()
                        }
                      })
                    }
                    .width('100%')
                    .alignItems(VerticalAlign.Center)
                  }
                }
                .width('100%')
                .margin({ bottom: DesignDimens.SPACE_MD })
              }

              // ---------- 日期 + 问句 ----------
              Column() {
                Text(MoodText.mood_today_question)
                  .fontSize(DesignFontSize.SECTION_TITLE)
                  .fontWeight(DesignFontWeight.SEMI_BOLD)
                  .fontColor(DesignColor.TEXT_PRIMARY)

                Text(formatTodayText())
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .fontColor(DesignColor.TEXT_SECONDARY)
                  .margin({ top: DesignDimens.SPACE_XXS })
              }
              .width('100%')
              .alignItems(HorizontalAlign.Start)

              // ---------- 五个心情色块（本页锚点，一次点按即存） ----------
              AppCard() {
                Column() {
                  MoodLevelRow({
                    selectedLevel: this.draftLevel,
                    onSelect: (level: number): void => {
                      this.selectLevel(level)
                    }
                  })

                  Text(this.hasRecord ? MoodText.mood_today_recorded : MoodText.mood_today_hint)
                    .fontSize(DesignFontSize.BODY_SMALL)
                    .fontColor(DesignColor.TEXT_TERTIARY)
                    .margin({ top: DesignDimens.SPACE_SM })

                  if (this.saveError.length > 0) {
                    Text(this.saveError)
                      .fontSize(DesignFontSize.BODY_SMALL)
                      .fontColor(DesignColor.ERROR)
                      .margin({ top: DesignDimens.SPACE_XS })
                  }
                }
                .width('100%')
                .alignItems(HorizontalAlign.Start)
              }

              // ---------- 词 chips（可选，选中一个，再点取消） ----------
              AppCard() {
                Column() {
                  Text(MoodText.mood_word_section_title)
                    .fontSize(DesignFontSize.BODY)
                    .fontWeight(DesignFontWeight.SEMI_BOLD)
                    .fontColor(DesignColor.TEXT_PRIMARY)

                  Flex({ wrap: FlexWrap.Wrap }) {
                    ForEach(MOOD_WORDS, (word: string) => {
                      MoodWordChip({
                        text: word,
                        selected: this.word === word,
                        onChipClick: (): void => {
                          this.toggleWord(word)
                        }
                      })
                        .margin({ right: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
                    }, (word: string) => word)
                  }
                  .margin({ top: DesignDimens.SPACE_SM })
                }
                .width('100%')
                .alignItems(HorizontalAlign.Start)
              }

              // ---------- 一句话（可选） ----------
              AppCard() {
                Column() {
                  Text(MoodText.mood_note_section_title)
                    .fontSize(DesignFontSize.BODY)
                    .fontWeight(DesignFontWeight.SEMI_BOLD)
                    .fontColor(DesignColor.TEXT_PRIMARY)

                  TextInput({ text: this.note, placeholder: MoodText.mood_note_hint })
                    .height(48)
                    .fontSize(DesignFontSize.BODY)
                    .backgroundColor(DesignColor.SURFACE_VARIANT)
                    .borderRadius(DesignDimens.INPUT_RADIUS)
                    .borderWidth(1)
                    .borderColor(this.noteFocused ? DesignColor.PRIMARY : DesignColor.OUTLINE)
                    .placeholderColor(DesignColor.TEXT_TERTIARY)
                    .placeholderFont({ size: DesignFontSize.BODY })
                    .maxLength(MOOD_NOTE_MAX_LENGTH)
                    .margin({ top: DesignDimens.SPACE_SM })
                    .onFocus(() => {
                      this.noteFocused = true
                    })
                    .onBlur(() => {
                      this.noteFocused = false
                    })
                    .onChange((value: string) => {
                      this.note = value
                    })
                    .onSubmit(() => {
                      this.commitNote()
                    })
                }
                .width('100%')
                .alignItems(HorizontalAlign.Start)
              }
            }
            .width('100%')
            .padding({
              left: DesignDimens.PAGE_PADDING,
              right: DesignDimens.PAGE_PADDING,
              top: DesignDimens.SPACE_SM,
              bottom: DesignDimens.SPACE_LG + safeBottomVp()
            })
            .alignItems(HorizontalAlign.Start)
          }
          .width('100%')
          .layoutWeight(1)
          .align(Alignment.TopStart)
          .scrollBar(BarState.Off)
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

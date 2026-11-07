// 稍后读列表页 — 对齐安卓 feature/readlater/ui/ReadLaterPage.kt
//
// 收藏输入条（URL https 简单校验 + 标签 chips + 收藏）→ 未读/已读 Tab（status 过滤）→
// 搜索栏（防抖 300ms）→ 标签筛选行 → 卡片列表（触底分页 + 下拉刷新 + 骨架/空态/错误重试）。
// 条目卡操作：生成摘要（loading 骨架 + 失败 toast 后端文案）、已读↔未读切换、
// 转短链（复制剪贴板 + toast）、删除。
//
// 已知坑遵守：条目卡内容依赖状态 → @ComponentV2 + @Param/@Event（@Builder 按值传参不响应更新）；
// 条目级在途态放卡片自身 @Local（ForEach 同 key 复用不重执行 itemGenerator，页级 busy 传不进 @Param）；
// 搜索防抖 setTimeout 在 aboutToDisappear 清理；色值一律 DesignSystem 令牌。

import { BusinessError, pasteboard } from '@kit.BasicServicesKit'
import { AppButton, AppButtonVariant, AppPillTabRow, EmptyState, IconBox, PageHeader, SkeletonBlock, SkeletonList } from '../../view/common/AppComponents'
import { ThemeRefresh } from '../../view/common/ThemeRefresh'
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from '../../constant/DesignSystem'
import { BizCode } from '../../network/HttpUtil'
import { logger } from '../../utils/Logger'
import { PagingController } from '../../utils/PagingUtil'
import { safeBottomVp, safeTopVp } from '../../utils/SafeArea'
import { showToastSafety } from '../../utils/ToastUtil'
import {
  READ_LATER_KEYWORD_MAX_LENGTH,
  READ_LATER_PAGE_SIZE,
  READ_LATER_STATUS_READ,
  READ_LATER_STATUS_UNREAD,
  READ_LATER_TAG_MAX_COUNT,
  READ_LATER_TAG_MAX_LENGTH,
  READ_LATER_URL_MAX_LENGTH,
  AddReadLaterForm,
  ReadLaterText,
  ReadLaterVo,
  addReadLater,
  deleteReadLater,
  formatReadLaterCreatedAt,
  generateReadLaterDigest,
  isValidHttpsUrl,
  pageReadLaters,
  readLaterHostOf,
  shortenReadLater,
  updateReadLater
} from './api'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'readLaterPage'

/** 搜索防抖间隔（对齐安卓 pagingFlow debounce 300ms） */
const SEARCH_DEBOUNCE_MS: number = 300

@Builder
export function ReadLaterBuilder() {
  ReadLaterPage()
}

@Component
struct ReadLaterPage {
  // ---------- 列表四态（骨架 / 首刷失败重试 / 空态 / 列表 + 尾部） ----------
  @State list: Array<ReadLaterVo> = []
  @State isLoadingFirst: boolean = true
  @State isFirstError: boolean = false
  @State isRefreshing: boolean = false
  @State isLoadingMore: boolean = false
  @State appendError: boolean = false

  // ---------- 收藏输入条草稿（URL + 标签输入框 + 已提交标签 chips） ----------
  @State draftUrl: string = ''
  @State tagInput: string = ''
  @State draftTags: Array<string> = []
  @State adding: boolean = false

  // ---------- 筛选（Tab status / 关键词 / 标签筛选） ----------
  @State statusTab: number = READ_LATER_STATUS_UNREAD
  @State keyword: string = ''
  @State tagFilter: string | null = null
  /** 标签筛选候选：从已加载条目去重聚合（契约无独立标签枚举端点，对齐安卓 knownTags） */
  @State knownTags: Array<string> = []

  /** 搜索防抖定时器 id（aboutToDisappear 必须清理） */
  private searchTimerId: number = -1
  /** 业务失败追加路径已 toast 标记（防 catch 重复弹 toast） */
  private appendToastShown: boolean = false
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchPage(page)
    },
    (loading: boolean): void => {
      this.isLoadingMore = loading
    }
  )

  async aboutToAppear(): Promise<void> {
    logger.debug(TAG, '稍后读页挂载，开始首刷')
    this.isLoadingFirst = true
    await this.loadFirst()
    this.isLoadingFirst = false
  }

  aboutToDisappear(): void {
    // 定时器必须清理（硬规则）：搜索防抖 setTimeout
    if (this.searchTimerId !== -1) {
      clearTimeout(this.searchTimerId)
      this.searchTimerId = -1
    }
  }

  // ==================== 分页加载 ====================

  /**
   * 按页拉取收藏列表（status/tab + keyword + tagFilter 随请求捕获）。
   * @returns 本页是否有数据（供 PagingController 判断是否封底）
   */
  async fetchPage(page: number): Promise<boolean> {
    const status = this.statusTab
    const tag = this.tagFilter
    const keyword = this.keyword.trim()
    try {
      const res = await pageReadLaters(page, READ_LATER_PAGE_SIZE, status, tag, keyword)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const records: Array<ReadLaterVo> = res.data.records
        if (page === 1) {
          this.list = []
          this.isFirstError = false
        }
        this.refreshKnownTags()
        if (records.length === 0) {
          logger.debug(TAG, `分页封底 page=${page} 本页无数据`)
          this.appendError = false
          return false
        }
        this.list.push(...records)
        this.appendError = false
        logger.debug(TAG, `分页加载成功 page=${page} count=${records.length} total=${res.data.total}`)
        return true
      }
      if (page === 1) {
        logger.warn(TAG, `稍后读首刷失败 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.isFirstError = true
        showToastSafety(res.message, ReadLaterText.readlater_error_load)
        return false
      }
      logger.warn(TAG, `稍后读追加失败 page=${page} code=${res.code ?? 0} msg=${res.message ?? ''}`)
      this.appendError = true
      showToastSafety(res.message, ReadLaterText.readlater_error_load)
      // 已 toast 过：catch 据此跳过重复提示；throw 供分页器记失败页（footer 重试重拉同一页）
      this.appendToastShown = true
      throw new Error(ReadLaterText.readlater_error_load)
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：与「追加失败」同语义（reject 供分页器记失败页）
      const alreadyToasted = this.appendToastShown
      this.appendToastShown = false
      if (page === 1) {
        this.isFirstError = true
        showToastSafety('', ReadLaterText.readlater_error_load)
        return false
      }
      this.appendError = true
      if (!alreadyToasted) {
        showToastSafety('', ReadLaterText.readlater_error_load)
      }
      throw new Error(ReadLaterText.readlater_error_load)
    }
  }

  /** 整列表重置重拉（筛选切换/下拉刷新/条目动作后共用） */
  async loadFirst(): Promise<void> {
    this.appendError = false
    this.paging.reset()
    this.list = []
    await this.fetchPage(1)
  }

  /** 带骨架的重拉（切 Tab/搜索/条目动作后：列表清空期间显示骨架而非闪空态） */
  private async refreshWithSkeleton(): Promise<void> {
    this.isLoadingFirst = true
    await this.loadFirst()
    this.isLoadingFirst = false
  }

  /** 下拉刷新：保持列表语义由 Refresh 指示器承担 */
  private async refreshList(): Promise<void> {
    this.isRefreshing = true
    await this.loadFirst()
    this.isRefreshing = false
  }

  /** 标签筛选候选：从已加载条目去重聚合（itemCount 变化时重算，对齐安卓 remember(itemCount)） */
  private refreshKnownTags(): void {
    const tags: Array<string> = []
    for (let i = 0; i < this.list.length; i++) {
      const voTags: Array<string> = this.list[i].tags
      for (let j = 0; j < voTags.length; j++) {
        const tag: string = voTags[j]
        if (tag.length > 0 && !tags.includes(tag)) {
          tags.push(tag)
        }
      }
    }
    this.knownTags = tags
  }

  // ==================== 收藏输入条 ====================

  /** 提交标签 chip：去重 + 数量/长度上限（端上软约束）；空输入/重复/超限时只清输入框 */
  private commitTag(): void {
    const tag = this.tagInput.trim()
    if (tag.length === 0) {
      return
    }
    if (this.draftTags.length >= READ_LATER_TAG_MAX_COUNT || this.draftTags.includes(tag)) {
      this.tagInput = ''
      return
    }
    this.draftTags.push(tag.substring(0, READ_LATER_TAG_MAX_LENGTH))
    this.tagInput = ''
  }

  private removeTag(tag: string): void {
    const index: number = this.draftTags.indexOf(tag)
    if (index >= 0) {
      this.draftTags.splice(index, 1)
    }
  }

  /** 收藏：https 简单校验 → POST（标题由后端抓取）→ 成功清空输入条并刷新列表 */
  private async submitCollect(): Promise<void> {
    if (this.adding) {
      return
    }
    const url = this.draftUrl.trim()
    if (!isValidHttpsUrl(url)) {
      showToastSafety(ReadLaterText.readlater_toast_invalid_url)
      return
    }
    this.adding = true
    try {
      const form: AddReadLaterForm = { url: url, tags: this.draftTags.slice() }
      const res = await addReadLater(form)
      if (res.code === BizCode.SUCCESS) {
        logger.info(TAG, `收藏成功 id=${res.data ?? 0}`)
        // 成功清空输入条（对齐安卓 VM Editor() 复位）
        this.draftUrl = ''
        this.tagInput = ''
        this.draftTags = []
        showToastSafety(ReadLaterText.readlater_toast_add_success)
        await this.refreshWithSkeleton()
      } else {
        logger.warn(TAG, `收藏被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, ReadLaterText.readlater_toast_failed)
      }
    } catch (err) {
      showToastSafety('', ReadLaterText.readlater_toast_failed)
    } finally {
      this.adding = false
    }
  }

  // ==================== 筛选 ====================

  private async onStatusTabSelect(index: number): Promise<void> {
    const next: number = index === 1 ? READ_LATER_STATUS_READ : READ_LATER_STATUS_UNREAD
    if (this.statusTab === next) {
      return
    }
    this.statusTab = next
    await this.refreshWithSkeleton()
  }

  /** 搜索防抖：输入即时回显，300ms 后才触发重查（定时器在 aboutToDisappear 清理） */
  private onKeywordChange(value: string): void {
    this.keyword = value
    if (this.searchTimerId !== -1) {
      clearTimeout(this.searchTimerId)
      this.searchTimerId = -1
    }
    this.searchTimerId = setTimeout(() => {
      this.searchTimerId = -1
      this.refreshWithSkeleton()
    }, SEARCH_DEBOUNCE_MS)
  }

  private async onTagFilterSelect(tag: string | null): Promise<void> {
    if (this.tagFilter === tag) {
      return
    }
    this.tagFilter = tag
    await this.refreshWithSkeleton()
  }

  // ==================== 条目级动作（结果经卡片 Promise 回传收尾在途态） ====================

  /** 生成 AI 摘要（可能较慢）：成功 toast 后刷新，摘要随列表回填；失败 toast 后端文案 */
  private async generateDigest(vo: ReadLaterVo): Promise<void> {
    try {
      const res = await generateReadLaterDigest(vo.id)
      if (res.code === BizCode.SUCCESS) {
        logger.info(TAG, `摘要生成成功 id=${vo.id}`)
        showToastSafety(ReadLaterText.readlater_toast_digest_done)
        await this.refreshWithSkeleton()
      } else {
        logger.warn(TAG, `生成摘要被拒 id=${vo.id} code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, ReadLaterText.readlater_toast_failed)
      }
    } catch (err) {
      showToastSafety('', ReadLaterText.readlater_toast_failed)
    }
  }

  /** 标记已读↔未读（PUT patch：只传 status，tags 不传不更新）：成功后刷新让卡片在两个 Tab 间归位 */
  private async toggleStatus(vo: ReadLaterVo): Promise<void> {
    const next: number = vo.status === READ_LATER_STATUS_READ ? READ_LATER_STATUS_UNREAD : READ_LATER_STATUS_READ
    try {
      const res = await updateReadLater(vo.id, { status: next })
      if (res.code === BizCode.SUCCESS) {
        logger.debug(TAG, `已读状态切换成功 id=${vo.id} status=${next}`)
        await this.refreshWithSkeleton()
      } else {
        logger.warn(TAG, `已读状态切换被拒 id=${vo.id} code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, ReadLaterText.readlater_toast_failed)
      }
    } catch (err) {
      showToastSafety('', ReadLaterText.readlater_toast_failed)
    }
  }

  /** 转短链：成功复制到剪贴板并提示（不刷新列表，对齐安卓） */
  private async shorten(vo: ReadLaterVo): Promise<void> {
    try {
      const res = await shortenReadLater(vo.id)
      if (res.code === BizCode.SUCCESS && res.data !== null && res.data.length > 0) {
        this.copyShortUrl(res.data)
      } else {
        logger.warn(TAG, `转短链被拒 id=${vo.id} code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, ReadLaterText.readlater_toast_failed)
      }
    } catch (err) {
      showToastSafety('', ReadLaterText.readlater_toast_failed)
    }
  }

  /** 复制短链到剪贴板（模式对齐 toolbox 详情页复制） */
  private copyShortUrl(url: string): void {
    const pasteData = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, url)
    pasteboard.getSystemPasteboard().setData(pasteData).then(() => {
      showToastSafety(ReadLaterText.readlater_toast_copy_done)
    }).catch((err: BusinessError) => {
      // 转短链已成功，仅剪贴板写入失败：与「转短链失败」区分（对齐 toolbox 复制失败口径）
      showToastSafety(ReadLaterText.readlater_toast_failed)
      logger.error(TAG, '短链写入剪贴板失败', `${err.code} ${err.message}`)
    })
  }

  /** 删除收藏：成功 toast 后刷新（卡片从列表移除即反馈），失败 toast */
  private async deleteItem(vo: ReadLaterVo): Promise<void> {
    try {
      const res = await deleteReadLater(vo.id)
      if (res.code === BizCode.SUCCESS) {
        logger.info(TAG, `收藏已删除 id=${vo.id}`)
        showToastSafety(ReadLaterText.readlater_toast_delete_done)
        await this.refreshWithSkeleton()
      } else {
        logger.warn(TAG, `删除被拒 id=${vo.id} code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, ReadLaterText.readlater_toast_failed)
      }
    } catch (err) {
      showToastSafety('', ReadLaterText.readlater_toast_failed)
    }
  }

  // ==================== UI ====================

  /** 空态/错误态（规范 §3.6：滚筒圆母题 + 引导文案 + 行动按钮） */
  @Builder
  Placeholder(message: string, isError: boolean) {
    Column() {
      EmptyState({ message: message }) {
        Column() {
          if (isError) {
            AppButton({
              text: ReadLaterText.readlater_action_retry,
              click: (): void => {
                this.refreshWithSkeleton()
              }
            })
          }
        }
        .margin({ top: DesignDimens.SPACE_MD })
      }
    }
    .width('100%')
    .layoutWeight(1)
    .justifyContent(FlexAlign.Center)
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随：主题重建宿主包裹本页内容（静态令牌直读点随子树重建重读新值）
      ThemeRefresh() {
        Column() {
          PageHeader({ title: ReadLaterText.readlater_title })

          // ---------- 收藏输入条 ----------
          Column() {
            Column() {
              Row() {
                TextInput({ text: this.draftUrl, placeholder: ReadLaterText.readlater_url_hint })
                  .height(48)
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .layoutWeight(1)
                  .backgroundColor(DesignColor.SURFACE_VARIANT)
                  .borderRadius(DesignDimens.RADIUS_MD)
                  .borderWidth(1)
                  .borderColor(DesignColor.OUTLINE)
                  .placeholderColor(DesignColor.TEXT_TERTIARY)
                  .placeholderFont({ size: DesignFontSize.BODY_SMALL })
                  .maxLength(READ_LATER_URL_MAX_LENGTH)
                  .onChange((value: string) => {
                    this.draftUrl = value
                  })
                  .onSubmit(() => {
                    this.submitCollect()
                  })

                // AppButton 固定 48 高 + width('100%')，收进定宽容器成紧凑「收藏」钮（自带 loading 防重）
                Column() {
                  AppButton({
                    text: ReadLaterText.readlater_action_collect,
                    isEnabled: this.draftUrl.trim().length > 0,
                    loading: this.adding,
                    click: (): void => {
                      this.submitCollect()
                    }
                  })
                }
                .width(84)
                .margin({ left: DesignDimens.SPACE_XS })
              }
              .width('100%')
              .alignItems(VerticalAlign.Center)

              // ---------- 已提交标签 chips（可选，点击移除） ----------
              if (this.draftTags.length > 0) {
                Scroll() {
                  Row({ space: DesignDimens.SPACE_XXS }) {
                    ForEach(this.draftTags, (tag: string) => {
                      ReadLaterRemovableTagChip({
                        text: tag,
                        onRemove: (): void => {
                          this.removeTag(tag)
                        }
                      })
                    }, (tag: string) => tag)
                  }
                }
                .width('100%')
                .align(Alignment.Start)
                .scrollable(ScrollDirection.Horizontal)
                .scrollBar(BarState.Off)
                .margin({ top: DesignDimens.SPACE_XS })
              }

              // ---------- 标签输入（可选） ----------
              Row() {
                TextInput({ text: this.tagInput, placeholder: ReadLaterText.readlater_tag_hint })
                  .height(40)
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .layoutWeight(1)
                  .backgroundColor(DesignColor.SURFACE_VARIANT)
                  .borderRadius(DesignDimens.RADIUS_MD)
                  .borderWidth(1)
                  .borderColor(DesignColor.OUTLINE)
                  .placeholderColor(DesignColor.TEXT_TERTIARY)
                  .placeholderFont({ size: DesignFontSize.BODY_SMALL })
                  .maxLength(READ_LATER_TAG_MAX_LENGTH)
                  .onChange((value: string) => {
                    this.tagInput = value
                  })
                  .onSubmit(() => {
                    this.commitTag()
                  })

                AppButton({
                  text: ReadLaterText.readlater_tag_action_add,
                  variant: AppButtonVariant.TEXT,
                  isEnabled: this.tagInput.trim().length > 0,
                  click: (): void => {
                    this.commitTag()
                  }
                })
                  .margin({ left: DesignDimens.SPACE_XS })
              }
              .width('100%')
              .alignItems(VerticalAlign.Center)
              .margin({ top: DesignDimens.SPACE_XS })
            }
            .width('100%')
            .padding(DesignDimens.CARD_PADDING)
            .backgroundColor(DesignColor.SURFACE)
            .borderRadius(DesignDimens.CARD_RADIUS)
            .borderWidth(1)
            .borderColor(DesignColor.OUTLINE)
          }
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_XS })

          // ---------- 未读/已读 Tab（status 过滤） ----------
          AppPillTabRow({
            tabs: [ReadLaterText.readlater_tab_unread, ReadLaterText.readlater_tab_read],
            selectedIndex: this.statusTab === READ_LATER_STATUS_READ ? 1 : 0,
            tabSelected: (index: number): void => {
              this.onStatusTabSelect(index)
            }
          })
            .margin({ top: DesignDimens.SPACE_SM })

          // ---------- 搜索栏（防抖在页面） ----------
          Column() {
            Row() {
              SymbolGlyph($r('sys.symbol.magnifyingglass'))
                .fontSize(18)
                .fontColor([DesignColor.TEXT_TERTIARY])
                .margin({ left: DesignDimens.SPACE_SM })

              TextInput({ text: this.keyword, placeholder: ReadLaterText.readlater_search_hint })
                .height(40)
                .fontSize(DesignFontSize.BODY_SMALL)
                .layoutWeight(1)
                .backgroundColor(Color.Transparent)
                .padding({ left: DesignDimens.SPACE_XS, right: DesignDimens.SPACE_SM })
                .placeholderColor(DesignColor.TEXT_TERTIARY)
                .placeholderFont({ size: DesignFontSize.BODY_SMALL })
                .maxLength(READ_LATER_KEYWORD_MAX_LENGTH)
                .onChange((value: string) => {
                  this.onKeywordChange(value)
                })
            }
            .width('100%')
            .height(44)
            .alignItems(VerticalAlign.Center)
            .backgroundColor(DesignColor.SURFACE_VARIANT)
            .borderRadius(DesignDimens.RADIUS_MD)
            .borderWidth(1)
            .borderColor(DesignColor.OUTLINE)
          }
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_SM })

          // ---------- 标签筛选行（候选来自已加载条目去重聚合） ----------
          if (this.knownTags.length > 0) {
            Scroll() {
              Row({ space: DesignDimens.SPACE_XXS }) {
                ReadLaterFilterChip({
                  text: ReadLaterText.readlater_tag_filter_all,
                  selected: this.tagFilter === null,
                  onSelect: (): void => {
                    this.onTagFilterSelect(null)
                  }
                })
                ForEach(this.knownTags, (tag: string) => {
                  ReadLaterFilterChip({
                    text: tag,
                    selected: this.tagFilter === tag,
                    onSelect: (): void => {
                      this.onTagFilterSelect(tag)
                    }
                  })
                }, (tag: string) => tag)
              }
              .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
            }
            .width('100%')
            .align(Alignment.Start)
            .scrollable(ScrollDirection.Horizontal)
            .scrollBar(BarState.Off)
            .margin({ top: DesignDimens.SPACE_XXS })
          }

          // ---------- 卡片列表 ----------
          if (this.isLoadingFirst) {
            SkeletonList({ rows: 5 })
              .width('100%')
              .layoutWeight(1)
          } else if (this.isFirstError && this.list.length === 0) {
            this.Placeholder(ReadLaterText.readlater_error_load, true)
          } else if (this.list.length === 0) {
            this.Placeholder(ReadLaterText.readlater_empty_message, false)
          } else {
            Refresh({ refreshing: $$this.isRefreshing }) {
              List() {
                ForEach(this.list, (vo: ReadLaterVo) => {
                  ListItem() {
                    ReadLaterCard({
                      vo: vo,
                      onDigest: (): Promise<void> => {
                        return this.generateDigest(vo)
                      },
                      onToggleStatus: (): void => {
                        this.toggleStatus(vo)
                      },
                      onShorten: (): Promise<void> => {
                        return this.shorten(vo)
                      },
                      onDelete: (): Promise<void> => {
                        return this.deleteItem(vo)
                      }
                    })
                  }
                }, (vo: ReadLaterVo) => `${vo.id}`)

                // 尾部：加载中骨架 / 失败重试（对齐安卓 append LoadState）
                ListItem() {
                  if (this.isLoadingMore) {
                    Row() {
                      SkeletonBlock()
                        .width(120)
                        .height(14)
                    }
                    .width('100%')
                    .padding({ top: 12, bottom: 12 })
                    .justifyContent(FlexAlign.Center)
                  } else if (this.appendError) {
                    Text(ReadLaterText.readlater_error_load)
                      .fontSize(DesignFontSize.CAPTION)
                      .fontColor(DesignColor.DANGER_INK)
                      .width('100%')
                      .textAlign(TextAlign.Center)
                      .padding({ top: 12, bottom: 12 })
                      .onClick(() => {
                        this.paging.retry()
                      })
                  }
                }
              }
              .width('100%')
              .height('100%')
              .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS + safeBottomVp() })
              .divider({ strokeWidth: 0, color: Color.Transparent })
              .onScrollIndex((start: number, end: number) => {
                this.paging.onScrollIndex(end, this.list.length)
              })
              .onScrollFrameBegin((offset: number) => {
                this.paging.onScrollFrameBegin(offset)
                return { offsetRemain: offset }
              })
            }
            .onRefreshing(() => {
              this.refreshList()
            })
            .layoutWeight(1)
          }
        }
        .width('100%')
        .height('100%')
        // 沉浸式：内容避让状态栏（背景延伸）
        .padding({ top: safeTopVp() })
        .backgroundColor(DesignColor.BACKGROUND)
      }
    }
    .hideTitleBar(true)
  }
}

// ==================== 条目卡与域内小件（内容依赖状态 → @ComponentV2 + @Param/@Event） ====================

/**
 * 收藏条目卡（规范 §3.1 唯一卡片画法）：
 * 顶行 = 图标盒 36（蓝·链接语义）+ 标题（已读弱化为次要色）+ 域名 + 时间；
 * 摘要区（生成中骨架占位 / 无摘要给「生成 AI 摘要」入口 / 有摘要预览 3 行）；标签 chips；
 * 操作行（生成摘要/标记已读↔未读/转短链/删除）。
 * 条目级在途态由卡片自身 @Local 承担：动作事件返回 Promise，finally 收尾在途态。
 */
@ComponentV2
struct ReadLaterCard {
  @Require @Param vo: ReadLaterVo
  /** 生成摘要在途（摘要区骨架 + 生成中…） */
  @Local digestBusy: boolean = false
  /** 转短链/删除在途（操作行禁用防重） */
  @Local actionBusy: boolean = false
  @Event onDigest: () => Promise<void> = (): Promise<void> => {
    return Promise.resolve()
  }
  @Event onToggleStatus: () => void = () => {
  }
  @Event onShorten: () => Promise<void> = (): Promise<void> => {
    return Promise.resolve()
  }
  @Event onDelete: () => Promise<void> = (): Promise<void> => {
    return Promise.resolve()
  }

  private isRead(): boolean {
    return this.vo.status === READ_LATER_STATUS_READ
  }

  private isBusy(): boolean {
    return this.digestBusy || this.actionBusy
  }

  /** 标题展示（后端抓取失败以 url 兜底，对齐安卓 vo.title ?: vo.url） */
  private displayTitle(): string {
    return this.vo.title.length > 0 ? this.vo.title : this.vo.url
  }

  /** 摘要预览文本（空白按无摘要处理，对齐安卓 digest.isNullOrBlank） */
  private digestText(): string {
    return (this.vo.digest ?? '').trim()
  }

  private handleDigest(): void {
    if (this.isBusy()) {
      return
    }
    this.digestBusy = true
    this.onDigest().finally(() => {
      this.digestBusy = false
    })
  }

  private handleShorten(): void {
    if (this.isBusy()) {
      return
    }
    this.actionBusy = true
    this.onShorten().finally(() => {
      this.actionBusy = false
    })
  }

  private handleDelete(): void {
    if (this.isBusy()) {
      return
    }
    this.actionBusy = true
    this.onDelete().finally(() => {
      this.actionBusy = false
    })
  }

  build() {
    Column() {
      // ---------- 顶行：图标盒 + 标题/域名 + 时间 ----------
      Row() {
        IconBox({
          icon: $r('sys.symbol.link'),
          sizeValue: 36,
          iconSize: 20,
          containerColor: DesignColor.ICON_CONTAINER_BLUE,
          iconColor: DesignColor.ICON_FOREGROUND_BLUE
        })

        Column() {
          Text(this.displayTitle())
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(this.isRead() ? DesignColor.TEXT_SECONDARY : DesignColor.TEXT_PRIMARY)
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          Text(readLaterHostOf(this.vo.url))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .margin({ top: DesignDimens.SPACE_XXS })
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
        .margin({ left: DesignDimens.SPACE_SM })

        Text(formatReadLaterCreatedAt(this.vo.createdAt))
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_TERTIARY)
          .margin({ left: DesignDimens.SPACE_XS })
      }
      .width('100%')
      .alignItems(VerticalAlign.Top)

      // ---------- 摘要区 ----------
      if (this.digestBusy) {
        // 生成中：骨架占位（禁止转圈），完成后随列表刷新回填
        Column({ space: DesignDimens.SPACE_XXS }) {
          SkeletonBlock()
            .width('82%')
            .height(14)
          SkeletonBlock()
            .width('60%')
            .height(14)
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
        .margin({ top: DesignDimens.SPACE_SM })
      } else if (this.digestText().length === 0) {
        // 无摘要：「生成 AI 摘要」入口（primarySoft 描边行）
        Text(ReadLaterText.readlater_digest_entry)
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.PRIMARY_DARK)
          .textAlign(TextAlign.Center)
          .width('100%')
          .padding({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
          .backgroundColor(DesignColor.PRIMARY_SOFT)
          .borderRadius(DesignDimens.RADIUS_MD)
          .margin({ top: DesignDimens.SPACE_SM })
          .opacity(this.actionBusy ? 0.5 : 1)
          .onClick(() => {
            this.handleDigest()
          })
      } else {
        Text(this.digestText())
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .maxLines(3)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .margin({ top: DesignDimens.SPACE_SM })
      }

      // ---------- 标签 ----------
      if (this.vo.tags.length > 0) {
        Scroll() {
          Row({ space: DesignDimens.SPACE_XXS }) {
            ForEach(this.vo.tags, (tag: string) => {
              ReadLaterTagChip({ text: tag })
            }, (tag: string) => tag)
          }
          .padding({ left: DesignDimens.SPACE_XXS, right: DesignDimens.SPACE_XXS })
        }
        .width('100%')
        .align(Alignment.Start)
        .scrollable(ScrollDirection.Horizontal)
        .scrollBar(BarState.Off)
        .margin({ top: DesignDimens.SPACE_SM })
      }

      // ---------- 操作行 ----------
      Row({ space: DesignDimens.SPACE_XXS }) {
        ReadLaterPillAction({
          text: this.digestBusy ? ReadLaterText.readlater_digest_generating : ReadLaterText.readlater_action_digest,
          isEnabled: !this.isBusy(),
          onAction: (): void => {
            this.handleDigest()
          }
        })
        ReadLaterPillAction({
          text: this.isRead() ? ReadLaterText.readlater_action_mark_unread : ReadLaterText.readlater_action_mark_read,
          isEnabled: !this.actionBusy,
          onAction: (): void => {
            this.onToggleStatus()
          }
        })
        ReadLaterPillAction({
          text: ReadLaterText.readlater_action_shorten,
          isEnabled: !this.isBusy(),
          onAction: (): void => {
            this.handleShorten()
          }
        })
        ReadLaterPillAction({
          text: ReadLaterText.readlater_action_delete,
          isEnabled: !this.isBusy(),
          contentColor: DesignColor.DANGER_INK,
          onAction: (): void => {
            this.handleDelete()
          }
        })
      }
      .width('100%')
      .margin({ top: DesignDimens.SPACE_XS })
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor(DesignColor.OUTLINE)
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: DesignColor.SHADOW_COLOR_CARD,
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
  }
}

/** 静态标签 chip —— 胶囊（radiusFull），用于条目上的标签展示（对齐安卓 ReadLaterTagChip） */
@ComponentV2
struct ReadLaterTagChip {
  @Param text: string = ''

  build() {
    Text(this.text)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(DesignColor.TEXT_SECONDARY)
      .maxLines(1)
      .padding({ left: 8, right: 8, top: 3, bottom: 3 })
      .backgroundColor(DesignColor.SURFACE_VARIANT)
      .borderRadius(DesignDimens.RADIUS_FULL)
  }
}

/**
 * 标签筛选 chip —— 选中 = 品牌底白字，未选中 = 白底描边
 * （模式对齐 SubscriptionSelectableChip / AppPillTabRow）。
 */
@ComponentV2
struct ReadLaterFilterChip {
  @Param text: string = ''
  @Param selected: boolean = false
  @Event onSelect: () => void = () => {
  }

  build() {
    Text(this.text)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(this.selected ? DesignColor.ON_PRIMARY : DesignColor.TEXT_SECONDARY)
      .maxLines(1)
      .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: 14, bottom: 14 })
      .borderRadius(DesignDimens.RADIUS_FULL)
      .backgroundColor(this.selected ? DesignColor.PRIMARY : DesignColor.SURFACE)
      .border({
        width: 1,
        color: this.selected ? DesignColor.PRIMARY : DesignColor.OUTLINE
      })
      .onClick(() => {
        this.onSelect()
      })
  }
}

/** 可移除标签 chip（收藏输入条已提交的标签，点击移除）——胶囊（对齐安卓 ReadLaterRemovableTagChip） */
@ComponentV2
struct ReadLaterRemovableTagChip {
  @Param text: string = ''
  @Event onRemove: () => void = () => {
  }

  build() {
    Row() {
      Text(this.text)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(DesignColor.ON_PRIMARY_SOFT)
        .maxLines(1)
      Text('×')
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(DesignColor.ON_PRIMARY_SOFT)
        .margin({ left: 4 })
    }
    .alignItems(VerticalAlign.Center)
    .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM, top: 13, bottom: 13 })
    .backgroundColor(DesignColor.PRIMARY_SOFT)
    .borderRadius(DesignDimens.RADIUS_FULL)
    .onClick(() => {
      this.onRemove()
    })
  }
}

/**
 * 卡内操作胶囊按钮（生成摘要/标记已读↔未读/转短链/删除）：描边胶囊，禁用置灰不可点
 * （对齐安卓 ReadLaterPillAction；视觉高 ~44 含热区）。
 */
@ComponentV2
struct ReadLaterPillAction {
  @Param text: string = ''
  // 命名注意：不能叫 enabled（与 CustomComponent 基类的同名属性方法冲突导致编译错误）
  @Param isEnabled: boolean = true
  @Param contentColor: number = DesignColor.PRIMARY
  @Event onAction: () => void = () => {
  }

  build() {
    Text(this.text)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(this.isEnabled ? this.contentColor : DesignColor.TEXT_TERTIARY)
      .maxLines(1)
      .textAlign(TextAlign.Center)
      .padding({ left: 14, right: 14, top: 13, bottom: 13 })
      .borderRadius(DesignDimens.RADIUS_FULL)
      .border({
        width: 1,
        color: this.isEnabled ? DesignColor.OUTLINE : DesignColor.DIVIDER
      })
      .onClick(() => {
        if (this.isEnabled) {
          this.onAction()
        }
      })
  }
}

// 速记手账页 — 对齐安卓 feature/memo/ui/memo/MemoPage.kt（单页承载全部能力，flomo 式）
//
// 顶部随手记输入区（多行 ≤2000 + 标签 chips + 保存/更新双态，编辑模式复用回填）
// → 搜索栏（300ms 防抖实时过滤，对齐安卓 pagingFlow debounce）
// → 标签筛选行（tags 聚合接口驱动，「全部」= 空筛选）
// → 记录卡片列表（置顶优先由后端排序；卡片操作：置顶/编辑/删除）。
// 分页：触底加载 + 下拉刷新；骨架/空态/错误态齐备（规范 §3.6）。
//
// 已知坑遵守：定时器（搜索防抖）在 aboutToDisappear 中 cancel；
// 内容依赖状态的卡片用 @ComponentV2 + @Param/@Event；输入态由页面自身持有。

import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from '../../constant/DesignSystem'
import { BizCode } from '../../network/HttpUtil'
import {
  MEMO_CONTENT_MAX_LENGTH,
  MEMO_FREQUENT_TAGS_LIMIT,
  MEMO_PAGE_SIZE,
  MEMO_SEARCH_DEBOUNCE_MS,
  MemoTagCountVo,
  MemoText,
  MemoVo,
  createMemo,
  deleteMemo,
  listMemoTags,
  memoCounterFormat,
  memoTagWithCountFormat,
  memoTimeDisplay,
  pageMemos,
  updateMemo
} from './api'
import { AppButton, AppCard, EmptyState, PageHeader, SkeletonBlock, SkeletonList } from '../../view/common/AppComponents'
import { Debouncer } from '../../utils/DebounceUtil'
import { logger } from '../../utils/Logger'
import { showToastSafety } from '../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../utils/SafeArea'
import { ThemeRefresh } from '../../view/common/ThemeRefresh'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'memoPage'

@Builder
export function MemoBuilder() {
  MemoPage()
}

@Component
struct MemoPage {
  // ---------- 记录列表 ----------
  @State memos: Array<MemoVo> = []
  @State total: number = 0
  @State isLoadingFirst: boolean = true
  @State isFirstError: boolean = false
  @State isRefreshing: boolean = false
  @State isLoadingMore: boolean = false
  @State loadMoreError: boolean = false
  // ---------- 搜索与标签筛选 ----------
  @State keyword: string = ''
  /** 标签筛选（'' = 全部） */
  @State tagFilter: string = ''
  // ---------- 标签聚合（筛选行 + 输入区常用标签共用） ----------
  @State tags: Array<MemoTagCountVo> = []
  // ---------- 随手记输入区（新建/编辑双模式） ----------
  @State composerContent: string = ''
  @State tagDraft: string = ''
  @State selectedTags: Array<string> = []
  /** 编辑中的记录 id（-1 = 新建模式） */
  @State editingId: number = -1
  /** 编辑保持的置顶状态（保存时原样回传，置顶状态不受编辑影响） */
  @State editingPinned: boolean = false
  @State saving: boolean = false
  // ---------- 卡片操作互斥（置顶/删除） ----------
  @State actionBusy: boolean = false

  /** 搜索防抖（300ms，停顿后重拉首页；aboutToDisappear 必须 cancel） */
  private searchDebouncer: Debouncer = new Debouncer()
  /** 请求序号：筛选条件变化重拉时自增，过期响应按序号丢弃（对齐安卓 flatMapLatest 换流语义） */
  private requestSeq: number = 0

  aboutToAppear(): void {
    this.loadFirst()
    this.loadTags()
  }

  aboutToDisappear(): void {
    // 定时器硬规则：未触发的搜索防抖任务随页面销毁清理
    this.searchDebouncer.cancel()
  }

  // ==================== 列表加载 ====================

  /** 是否还有下一页 */
  private hasMore(): boolean {
    return this.memos.length < this.total
  }

  /** 输入区「常用标签」展示裁剪（仅展示层，非后端契约） */
  private frequentTags(): Array<MemoTagCountVo> {
    return this.tags.slice(0, MEMO_FREQUENT_TAGS_LIMIT)
  }

  /** 首刷（进入页面 / 下拉刷新 / 增删改成功后共用）：按当前筛选拉第 1 页 */
  private async loadFirst(): Promise<void> {
    const seq = this.requestSeq + 1
    this.requestSeq = seq
    this.isLoadingFirst = this.memos.length === 0
    this.isFirstError = false
    try {
      const res = await pageMemos(1, MEMO_PAGE_SIZE, this.keyword, this.tagFilter)
      if (seq !== this.requestSeq) {
        return
      }
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.memos = res.data.records
        this.total = res.data.total
      } else {
        logger.warn(TAG, `速记首刷失败 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.isFirstError = true
        showToastSafety(res.message, MemoText.memo_error_load)
      }
    } catch (err) {
      if (seq !== this.requestSeq) {
        return
      }
      logger.error(TAG, '速记首刷异常', err instanceof Error ? err.message : '')
      this.isFirstError = true
      showToastSafety('', MemoText.memo_error_load)
    } finally {
      if (seq === this.requestSeq) {
        this.isLoadingFirst = false
      }
    }
  }

  /** 触底加载更多（追加失败由页脚重试入口重试） */
  private async loadMore(): Promise<void> {
    if (this.isLoadingMore || !this.hasMore()) {
      return
    }
    this.isLoadingMore = true
    this.loadMoreError = false
    const nextPage = Math.floor(this.memos.length / MEMO_PAGE_SIZE) + 1
    const seq = this.requestSeq
    try {
      const res = await pageMemos(nextPage, MEMO_PAGE_SIZE, this.keyword, this.tagFilter)
      if (seq !== this.requestSeq) {
        return
      }
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.memos = this.memos.concat(res.data.records)
        this.total = res.data.total
      } else {
        logger.warn(TAG, `速记加载更多失败 page=${nextPage} code=${res.code ?? 0}`)
        this.loadMoreError = true
        showToastSafety(res.message, MemoText.memo_error_load)
      }
    } catch (err) {
      if (seq !== this.requestSeq) {
        return
      }
      logger.error(TAG, '速记加载更多异常', err instanceof Error ? err.message : '')
      this.loadMoreError = true
      showToastSafety('', MemoText.memo_error_load)
    } finally {
      this.isLoadingMore = false
    }
  }

  /** 下拉刷新：按当前筛选重拉首页 */
  private async refreshAll(): Promise<void> {
    this.isRefreshing = true
    await this.loadFirst()
    this.isRefreshing = false
  }

  /** 筛选条件（关键词/标签）变化：清空列表走骨架重拉，过期响应按序号丢弃 */
  private reloadForFilter(): void {
    this.memos = []
    this.total = 0
    this.loadMoreError = false
    this.isLoadingMore = false
    this.loadFirst()
  }

  // ==================== 搜索 / 标签筛选 ====================

  /** 搜索输入：状态直写（TextField 直读直写），重拉经 300ms 防抖触发 */
  private onSearchChange(value: string): void {
    this.keyword = value
    this.searchDebouncer.debounce(() => {
      this.reloadForFilter()
    }, MEMO_SEARCH_DEBOUNCE_MS)
  }

  /** 筛选 chip 点按：再点一次取消（回「全部」）；「全部」= 空筛选 */
  private selectTagFilter(tag: string): void {
    const next: string = tag.length === 0 ? '' : (this.tagFilter === tag ? '' : tag)
    if (next === this.tagFilter) {
      return
    }
    this.tagFilter = next
    this.reloadForFilter()
  }

  /** 空态文案：有关键词/筛选 → 无匹配；否则 → 还没有记录 */
  private emptyMessage(): string {
    return this.keyword.trim().length > 0 || this.tagFilter.length > 0
      ? MemoText.memo_empty_filtered_message
      : MemoText.memo_empty_message
  }

  // ==================== 标签聚合 ====================

  /** 拉取标签聚合（进入页面 + 增删改成功后调用；失败静默保留旧值，不阻塞列表主链路） */
  private async loadTags(): Promise<void> {
    try {
      const res = await listMemoTags()
      if (res.code === BizCode.SUCCESS) {
        this.tags = res.data ?? []
      } else {
        logger.warn(TAG, `标签聚合拉取失败 code=${res.code ?? 0} msg=${res.message ?? ''}`)
      }
    } catch (err) {
      logger.warn(TAG, `标签聚合拉取异常 ${err instanceof Error ? err.message : ''}`)
    }
  }

  // ==================== 随手记输入区 ====================

  /** 提交草稿标签：trim、去重、去空；已存在时仅清空草稿（静默去重，不打断输入） */
  private addTagFromDraft(): void {
    const tag: string = this.tagDraft.trim()
    if (tag.length === 0) {
      return
    }
    if (this.selectedTags.indexOf(tag) >= 0) {
      this.tagDraft = ''
      return
    }
    this.selectedTags = this.selectedTags.concat([tag])
    this.tagDraft = ''
  }

  /** 已选标签 chip 点按：整 chip 可点移除 */
  private removeSelectedTag(tag: string): void {
    this.selectedTags = this.selectedTags.filter((item: string): boolean => item !== tag)
  }

  /** 常用标签 chip 点按：未选 → 加入，已选 → 移除（切换语义） */
  private toggleFrequentTag(tag: string): void {
    if (this.selectedTags.indexOf(tag) >= 0) {
      this.removeSelectedTag(tag)
    } else {
      this.selectedTags = this.selectedTags.concat([tag])
    }
  }

  /** 卡片「编辑」：内容/标签回填顶部输入区，保存按钮切换为「更新」 */
  private startEdit(vo: MemoVo): void {
    this.composerContent = vo.content ?? ''
    this.selectedTags = (vo.tags ?? []).slice()
    this.editingId = vo.id ?? -1
    this.editingPinned = vo.pinned ?? false
  }

  /** 取消编辑：输入区整体复位回新建模式 */
  private cancelEdit(): void {
    this.resetComposer()
  }

  /** 输入区复位（新建/编辑共用终态） */
  private resetComposer(): void {
    this.composerContent = ''
    this.tagDraft = ''
    this.selectedTags = []
    this.editingId = -1
    this.editingPinned = false
  }

  /**
   * 保存（新建 POST / 编辑 PUT）：空内容拦截为 toast；
   * 编辑态按安卓先例全量提交（content/tags/pinned），置顶状态不受编辑影响。
   * 成功后复位输入区并刷新列表 + 标签聚合（新增/删除标签后计数与筛选行保持一致）。
   */
  private async save(): Promise<void> {
    if (this.saving) {
      return
    }
    const content: string = this.composerContent.trim()
    if (content.length === 0) {
      showToastSafety('', MemoText.memo_error_empty)
      return
    }
    this.saving = true
    const editingId = this.editingId
    const editingPinned = this.editingPinned
    const tags: Array<string> = this.selectedTags.slice()
    try {
      const res = editingId >= 0
        ? await updateMemo(editingId, content, tags, editingPinned)
        : await createMemo(content, tags)
      if (res.code === BizCode.SUCCESS) {
        logger.info(TAG, `速记保存成功 mode=${editingId >= 0 ? 'update' : 'create'} id=${editingId}`)
        showToastSafety(MemoText.memo_toast_saved)
        this.resetComposer()
        await this.loadFirst()
        this.loadTags()
      } else {
        logger.warn(TAG, `速记保存被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, MemoText.memo_error_save)
      }
    } catch (err) {
      logger.error(TAG, '速记保存异常', err instanceof Error ? err.message : '')
      showToastSafety('', MemoText.memo_error_save)
    } finally {
      this.saving = false
    }
  }

  // ==================== 卡片操作 ====================

  /** 置顶/取消置顶：PUT 全量透传当前内容与标签，仅翻转 pinned；置顶排序由后端裁决，需重拉首页 */
  private async togglePin(vo: MemoVo): Promise<void> {
    if (this.actionBusy) {
      return
    }
    this.actionBusy = true
    const willPin: boolean = !(vo.pinned ?? false)
    try {
      const res = await updateMemo(vo.id ?? 0, vo.content ?? '', vo.tags ?? [], willPin)
      if (res.code === BizCode.SUCCESS) {
        logger.info(TAG, `速记置顶切换成功 id=${vo.id ?? 0} pinned=${willPin}`)
        showToastSafety(willPin ? MemoText.memo_toast_pinned : MemoText.memo_toast_unpinned)
        await this.loadFirst()
      } else {
        logger.warn(TAG, `速记置顶切换被拒 id=${vo.id ?? 0} code=${res.code ?? 0}`)
        showToastSafety(res.message, MemoText.memo_error_action)
      }
    } catch (err) {
      logger.error(TAG, '速记置顶切换异常', err instanceof Error ? err.message : '')
      showToastSafety('', MemoText.memo_error_action)
    } finally {
      this.actionBusy = false
    }
  }

  /** 删除（后端逻辑删除）：成功即重拉列表移出该条，并刷新标签聚合 */
  private async removeMemo(vo: MemoVo): Promise<void> {
    if (this.actionBusy) {
      return
    }
    this.actionBusy = true
    try {
      const res = await deleteMemo(vo.id ?? 0)
      if (res.code === BizCode.SUCCESS) {
        logger.info(TAG, `速记删除成功 id=${vo.id ?? 0}`)
        showToastSafety(MemoText.memo_toast_deleted)
        // 编辑中的记录被删除：输入区一并复位，避免更新打在已删记录上（404 语义）
        if (this.editingId === (vo.id ?? -1)) {
          this.resetComposer()
        }
        await this.loadFirst()
        this.loadTags()
      } else {
        logger.warn(TAG, `速记删除被拒 id=${vo.id ?? 0} code=${res.code ?? 0}`)
        showToastSafety(res.message, MemoText.memo_error_action)
      }
    } catch (err) {
      logger.error(TAG, '速记删除异常', err instanceof Error ? err.message : '')
      showToastSafety('', MemoText.memo_error_action)
    } finally {
      this.actionBusy = false
    }
  }

  // ==================== 局部Builder ====================

  /** 顶部随手记输入区（新建/编辑双模式复用），画法对齐安卓 MemoComposerCard */
  @Builder
  ComposerCard() {
    AppCard() {
      Column({ space: DesignDimens.SPACE_SM }) {
        // 编辑态横幅：编辑中 chip + 取消编辑入口
        if (this.editingId >= 0) {
          Row() {
            MemoStaticChip({
              text: MemoText.memo_composer_editing,
              containerColor: DesignColor.PRIMARY_SOFT,
              contentColor: DesignColor.ON_PRIMARY_SOFT
            })
            Blank()
            MemoTextAction({
              text: MemoText.memo_action_cancel_edit,
              contentColor: DesignColor.TEXT_SECONDARY,
              onAction: (): void => {
                this.cancelEdit()
              }
            })
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)
        }

        // 多行内容输入（随手记主体；长文到上限后转输入框内滚动）
        TextArea({ text: this.composerContent, placeholder: MemoText.memo_composer_hint })
          .width('100%')
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .placeholderColor(DesignColor.TEXT_TERTIARY)
          .placeholderFont({ size: DesignFontSize.BODY })
          .backgroundColor(DesignColor.SURFACE_VARIANT)
          .borderRadius(DesignDimens.INPUT_RADIUS)
          .padding(DesignDimens.SPACE_SM)
          .maxLength(MEMO_CONTENT_MAX_LENGTH)
          .constraintSize({ minHeight: 96, maxHeight: 200 })
          .onChange((value: string) => {
            this.composerContent = value
          })

        // 字数计数（content ≤ 2000 契约，输入即截断）
        Text(memoCounterFormat(this.composerContent.length, MEMO_CONTENT_MAX_LENGTH))
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_TERTIARY)
          .fontFeature(DesignFontFeature.TNUM)
          .width('100%')
          .textAlign(TextAlign.End)

        // 已选标签：整 chip 可点移除，下方提示承载可发现性
        if (this.selectedTags.length > 0) {
          Column({ space: DesignDimens.SPACE_XXS }) {
            Flex({ wrap: FlexWrap.Wrap }) {
              ForEach(this.selectedTags, (tag: string) => {
                MemoRemovableTagChip({
                  text: tag,
                  onRemove: (): void => {
                    this.removeSelectedTag(tag)
                  }
                })
                  .margin({ right: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XXS })
              }, (tag: string) => tag)
            }
            .width('100%')

            Text(MemoText.memo_composer_selected_hint)
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.TEXT_TERTIARY)
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
        }

        // 标签草稿输入 + 添加（回车与「添加」等效提交）
        Row({ space: DesignDimens.SPACE_XS }) {
          TextInput({ text: this.tagDraft, placeholder: MemoText.memo_composer_tag_hint })
            .layoutWeight(1)
            .height(48)
            .fontSize(DesignFontSize.BODY)
            .backgroundColor(DesignColor.SURFACE_VARIANT)
            .borderRadius(DesignDimens.INPUT_RADIUS)
            .placeholderColor(DesignColor.TEXT_TERTIARY)
            .placeholderFont({ size: DesignFontSize.BODY })
            .onSubmit(() => {
              this.addTagFromDraft()
            })
            .onChange((value: string) => {
              this.tagDraft = value
            })

          Row() {
            Text(MemoText.memo_composer_tag_add)
              .fontSize(DesignFontSize.BUTTON_TEXT)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.PRIMARY)
          }
          .justifyContent(FlexAlign.Center)
          .constraintSize({ minHeight: DesignDimens.TOUCH_TARGET_MIN })
          .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD })
          .borderWidth(1)
          .borderColor(DesignColor.OUTLINE)
          .borderRadius(DesignDimens.RADIUS_FULL)
          .onClick(() => {
            this.addTagFromDraft()
          })
        }
        .width('100%')
        .alignItems(VerticalAlign.Center)

        // 常用标签（tags 聚合接口驱动；点按切换加入/移除）
        if (this.frequentTags().length > 0) {
          Column({ space: DesignDimens.SPACE_XS }) {
            Text(MemoText.memo_composer_frequent_tags)
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.TEXT_TERTIARY)

            Scroll() {
              Row({ space: DesignDimens.SPACE_XS }) {
                ForEach(this.frequentTags(), (tagVo: MemoTagCountVo) => {
                  MemoSelectableChip({
                    text: tagVo.tag ?? '',
                    selected: this.selectedTags.indexOf(tagVo.tag ?? '') >= 0,
                    onSelect: (): void => {
                      this.toggleFrequentTag(tagVo.tag ?? '')
                    }
                  })
                }, (tagVo: MemoTagCountVo) => `freq-${tagVo.tag ?? ''}`)
              }
            }
            .scrollable(ScrollDirection.Horizontal)
            .scrollBar(BarState.Off)
            .width('100%')
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
        }

        // 保存 / 更新（空内容禁用，AppButton 自带 loading 防重）
        AppButton({
          text: this.editingId >= 0 ? MemoText.memo_action_update : MemoText.memo_action_save,
          isEnabled: this.composerContent.trim().length > 0,
          loading: this.saving,
          click: (): void => {
            this.save()
          }
        })
      }
      .width('100%')
      .alignItems(HorizontalAlign.Start)
    }
  }

  /** 首刷错误态（规范 §3.6：空态母题 + 说明 + 重试按钮） */
  @Builder
  FirstErrorState() {
    Column() {
      EmptyState({ message: MemoText.memo_error_load }) {
        Column() {
          AppButton({
            text: MemoText.memo_action_retry,
            click: (): void => {
              this.loadFirst()
            }
          })
        }
        .margin({ top: DesignDimens.SPACE_MD })
      }
    }
    .width('100%')
    .justifyContent(FlexAlign.Center)
  }

  /** 列表空态：有关键词/筛选 → 无匹配；否则 → 引导写第一条 */
  @Builder
  MemoEmpty() {
    Column() {
      EmptyState({ message: this.emptyMessage() })
    }
    .width('100%')
    .padding({ top: DesignDimens.SPACE_XL, bottom: DesignDimens.SPACE_XL })
  }

  /** 追加页脚：加载中骨架 / 失败重试（对齐安卓 AppendLoadingRow，禁止转圈） */
  @Builder
  LoadMoreFooter() {
    Row() {
      if (this.isLoadingMore) {
        SkeletonBlock()
          .width(120)
          .height(14)
      } else {
        Text(MemoText.memo_error_load)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.DANGER_INK)
      }
    }
    .width('100%')
    .constraintSize({ minHeight: 44 })
    .justifyContent(FlexAlign.Center)
    .alignItems(VerticalAlign.Center)
    .onClick(() => {
      if (!this.isLoadingMore && this.loadMoreError) {
        this.loadMore()
      }
    })
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容，
      // 静态令牌直读点在色模式翻转后的子树重建中重读新值（机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        PageHeader({ title: MemoText.memo_title })

        // ---------- 随手记输入区 ----------
        Column() {
          this.ComposerCard()
        }
        .width('100%')
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_XXS })

        // ---------- 搜索栏（实时过滤：输入经 300ms 防抖后重拉） ----------
        TextInput({ text: this.keyword, placeholder: MemoText.memo_search_hint })
          .width('100%')
          .height(48)
          .fontSize(DesignFontSize.BODY)
          .backgroundColor(DesignColor.SURFACE_VARIANT)
          .borderRadius(DesignDimens.INPUT_RADIUS)
          .borderWidth(1)
          .borderColor(DesignColor.OUTLINE)
          .placeholderColor(DesignColor.TEXT_TERTIARY)
          .placeholderFont({ size: DesignFontSize.BODY })
          .margin({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_XS })
          .onChange((value: string) => {
            this.onSearchChange(value)
          })

        // ---------- 标签筛选行（tags 聚合驱动；「全部」= 空筛选） ----------
        Scroll() {
          Row({ space: DesignDimens.SPACE_XS }) {
            MemoSelectableChip({
              text: MemoText.memo_filter_all,
              selected: this.tagFilter.length === 0,
              onSelect: (): void => {
                this.selectTagFilter('')
              }
            })
            ForEach(this.tags, (tagVo: MemoTagCountVo) => {
              MemoSelectableChip({
                text: memoTagWithCountFormat(tagVo.tag ?? '', tagVo.count ?? 0),
                selected: this.tagFilter === (tagVo.tag ?? ''),
                onSelect: (): void => {
                  this.selectTagFilter(tagVo.tag ?? '')
                }
              })
            }, (tagVo: MemoTagCountVo) => `filter-${tagVo.tag ?? ''}`)
          }
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_XXS,
            bottom: DesignDimens.SPACE_XXS
          })
        }
        .scrollable(ScrollDirection.Horizontal)
        .scrollBar(BarState.Off)
        .width('100%')

        // ---------- 记录列表 ----------
        if (this.isLoadingFirst) {
          SkeletonList({ rows: 5 })
            .width('100%')
            .layoutWeight(1)
        } else if (this.isFirstError && this.memos.length === 0) {
          // 首刷错误态：layoutWeight(1) 占满列表区（Builder 根节点不能挂权重，外包一层）
          Column() {
            this.FirstErrorState()
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          Refresh({ refreshing: $$this.isRefreshing }) {
            List() {
              if (this.memos.length === 0) {
                ListItem() {
                  this.MemoEmpty()
                }
              } else {
                ForEach(this.memos, (vo: MemoVo) => {
                  ListItem() {
                    MemoCard({
                      vo: vo,
                      onTogglePin: (): void => {
                        this.togglePin(vo)
                      },
                      onEdit: (): void => {
                        this.startEdit(vo)
                      },
                      onDelete: (): void => {
                        this.removeMemo(vo)
                      }
                    })
                  }
                }, (vo: MemoVo) => `${vo.id ?? 0}`)

                if (this.isLoadingMore || this.loadMoreError) {
                  ListItem() {
                    this.LoadMoreFooter()
                  }
                }
              }
            }
            .width('100%')
            .height('100%')
            .padding({
              left: DesignDimens.PAGE_PADDING,
              right: DesignDimens.PAGE_PADDING,
              top: DesignDimens.SPACE_XXS,
              bottom: DesignDimens.SPACE_XS + safeBottomVp()
            })
            .divider({ strokeWidth: 0, color: Color.Transparent })
            .scrollBar(BarState.Off)
            .onReachEnd(() => {
              this.loadMore()
            })
          }
          .onRefreshing(() => {
            this.refreshAll()
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

// ==================== 局部小组件（对齐安卓 MemoPage 模块内组件） ====================

/** 静态标签 chip —— 胶囊，置顶标记/正文标签标注/「编辑中」横幅共用 */
@ComponentV2
struct MemoStaticChip {
  @Param text: string = ''
  @Param containerColor: number = DesignColor.SURFACE_VARIANT
  @Param contentColor: number = DesignColor.TEXT_SECONDARY

  build() {
    Row() {
      Text(this.text)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(this.contentColor)
    }
    .backgroundColor(this.containerColor)
    .borderRadius(DesignDimens.RADIUS_FULL)
    .padding({
      left: DesignDimens.SPACE_XS,
      right: DesignDimens.SPACE_XS,
      top: DesignDimens.SPACE_XXS,
      bottom: DesignDimens.SPACE_XXS
    })
  }
}

/** 可移除标签 chip（已选标签）：整 chip 可点移除，44vp 最小热区（规范 §2.3） */
@ComponentV2
struct MemoRemovableTagChip {
  @Param text: string = ''
  @Event onRemove: () => void = () => {
  }

  build() {
    Row({ space: DesignDimens.SPACE_XXS }) {
      Text(this.text)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.ON_PRIMARY_SOFT)

      Text('×')
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.ON_PRIMARY_SOFT)
    }
    .justifyContent(FlexAlign.Center)
    .constraintSize({ minHeight: DesignDimens.TOUCH_TARGET_MIN })
    .padding({ left: DesignDimens.SPACE_XS, right: DesignDimens.SPACE_XS })
    .backgroundColor(DesignColor.PRIMARY_SOFT)
    .borderRadius(DesignDimens.RADIUS_FULL)
    .onClick(() => {
      this.onRemove()
    })
  }
}

/**
 * 可选筛选/切换 chip —— 选中 = 品牌底白字，未选中 = 白底描边
 * （画法对齐 MoodWordChip：描边常驻防选中切换跳变，字重恒 SemiBold）。
 */
@ComponentV2
struct MemoSelectableChip {
  @Param text: string = ''
  @Param selected: boolean = false
  @Event onSelect: () => void = () => {
  }

  build() {
    Text(this.text)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.SEMI_BOLD)
      .fontColor(this.selected ? DesignColor.ON_PRIMARY : DesignColor.TEXT_SECONDARY)
      .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: 6, bottom: 6 })
      .backgroundColor(this.selected ? DesignColor.PRIMARY : DesignColor.SURFACE)
      .borderWidth(1)
      .borderColor(this.selected ? DesignColor.PRIMARY : DesignColor.OUTLINE)
      .borderRadius(DesignDimens.RADIUS_FULL)
      .constraintSize({ minHeight: DesignDimens.TOUCH_TARGET_MIN })
      .onClick(() => {
        this.onSelect()
      })
  }
}

/** 文字操作入口（卡片 置顶/编辑/删除、输入区取消编辑）：44vp 最小热区 */
@ComponentV2
struct MemoTextAction {
  @Param text: string = ''
  @Param contentColor: number = DesignColor.PRIMARY_DARK
  @Event onAction: () => void = () => {
  }

  build() {
    Row() {
      Text(this.text)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(this.contentColor)
    }
    .justifyContent(FlexAlign.Center)
    .constraintSize({ minHeight: DesignDimens.TOUCH_TARGET_MIN })
    .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM })
    .onClick(() => {
      this.onAction()
    })
  }
}

/**
 * 记录卡片（画法对齐短链卡先例：r16 surface 底 + 1px outline 描边 + 卡片档阴影）：
 * 置顶标记 chip → 内容 → 标签 chips → 时间行（updateTime 与 createTime 不同时标注「已编辑」）
 * → 操作行（置顶/编辑/删除）。
 */
@ComponentV2
struct MemoCard {
  @Require @Param vo: MemoVo
  @Event onTogglePin: () => void = () => {
  }
  @Event onEdit: () => void = () => {
  }
  @Event onDelete: () => void = () => {
  }

  /** 是否编辑过：updateTime 存在且与 createTime 不同（对齐安卓判定） */
  private isEdited(): boolean {
    const updated: string = this.vo.updateTime ?? ''
    const created: string = this.vo.createTime ?? ''
    return updated.length > 0 && updated !== created
  }

  build() {
    Column({ space: DesignDimens.SPACE_SM }) {
      if (this.vo.pinned === true) {
        MemoStaticChip({
          text: MemoText.memo_card_pinned,
          containerColor: DesignColor.PRIMARY_SOFT,
          contentColor: DesignColor.ON_PRIMARY_SOFT
        })
      }

      Text(this.vo.content ?? '')
        .fontSize(DesignFontSize.BODY)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .width('100%')

      if ((this.vo.tags ?? []).length > 0) {
        Flex({ wrap: FlexWrap.Wrap }) {
          ForEach(this.vo.tags ?? [], (tag: string, index: number) => {
            MemoStaticChip({
              text: tag,
              containerColor: DesignColor.SURFACE_VARIANT,
              contentColor: DesignColor.TEXT_SECONDARY
            })
              .margin({ right: DesignDimens.SPACE_XXS, bottom: DesignDimens.SPACE_XXS })
          }, (tag: string, index: number) => `${tag}-${index}`)
        }
        .width('100%')
      }

      Row() {
        Text(memoTimeDisplay(this.vo.createTime))
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .fontFeature(DesignFontFeature.TNUM)

        if (this.isEdited()) {
          Text(MemoText.memo_card_edited)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_TERTIARY)
            .margin({ left: DesignDimens.SPACE_XS })
        }
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      Row() {
        Blank()

        MemoTextAction({
          text: this.vo.pinned === true ? MemoText.memo_action_unpin : MemoText.memo_action_pin,
          contentColor: DesignColor.PRIMARY_DARK,
          onAction: (): void => {
            this.onTogglePin()
          }
        })
        MemoTextAction({
          text: MemoText.memo_action_edit,
          contentColor: DesignColor.PRIMARY_DARK,
          onAction: (): void => {
            this.onEdit()
          }
        })
        MemoTextAction({
          text: MemoText.memo_action_delete,
          contentColor: DesignColor.DANGER_INK,
          onAction: (): void => {
            this.onDelete()
          }
        })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
    }
    .width('100%')
    .padding({
      left: DesignDimens.CARD_PADDING,
      right: DesignDimens.CARD_PADDING,
      top: DesignDimens.CARD_PADDING,
      bottom: DesignDimens.SPACE_XS
    })
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
    .alignItems(HorizontalAlign.Start)
  }
}

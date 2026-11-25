// 点单创建/编辑页 — 对齐 Android DiningOrderCreatePage.kt（2026-11-18 协同点单改造契约）
//
// 产品模型：菜单即已选清单；全员共同编辑；发起者一步「提交订单」→ 订单直接结束。
// 常规模式：标题输入 + 分类 chips + 菜品列表（右侧加/移除按钮）+ 底部单行购物车栏
//（已选 N 道 · M 人 + 右 CTA「提交订单」）；点左侧信息区弹购物车弹窗（已选明细 + 移除 + 人数）。
// 草稿编辑模式（路由 orderId）：加载既有 DRAFT，加/移除走 dishes 接口，CTA=提交订单。
// 复用模式（query fromOrderId）：拉源单详情回显菜单只读 + 提示 + 提交带 fromOrderId
//（菜单由后端整份复制，端上只校验标题）。
// 提交成功 → replace 购买清单页（等价 Android popUpTo(OrderCreate)+navigate：返回直回列表）。

import { KeyboardAvoidMode, promptAction } from '@kit.ArkUI'
import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignFontSize, DesignFontWeight, MotionAlpha, MotionScale, MotionTiming } from '../../../constant/DesignSystem'
import { ReduceMotion } from '../../../utils/ReduceMotion'
import { BizCode } from '../../../network/HttpUtil'
import {
  DietaryPreferenceVo,
  DINING_PAGE_SIZE,
  DiningCategoryVo,
  DiningMenuRecipe,
  DiningOrderDetailVo,
  DiningRecipeVo,
  addDiningOrderDish,
  createDiningOrder,
  getDiningCategories,
  getDiningDietaryPreference,
  getDiningOrderDetail,
  getDiningRecipeDetail,
  getDiningRecipes,
  submitDiningOrder,
  removeDiningOrderDish
} from '../../../network/api/DiningApi'
import {
  DINING_PEOPLE_MIN,
  DiningAllergenHit,
  DiningDraftMenuEditor,
  DiningOrderFormError,
  DiningSelectedDish,
  DiningCategoryName,
  DiningMenuRecipeRow,
  DiningMenuSection,
  diningMenuIdSet,
  diningAllergenHits,
  groupDiningMenu,
  validateDiningOrderForm,
  validateDiningOrderReuse
} from '../DiningLogic'
import { DiningText } from '../DiningText'
import { DiningCartPopup } from './DiningUiCommon'
import { DINING_DEFAULT_CALORIE_TARGET } from '../../../network/vo/DiningVo'
import { DiningOrderCreateParam, DiningOrderShareParam } from '../api'
import { AppButton, EmptyState, SkeletonList } from '../../../view/common/AppComponents'
import { LoadingFooter } from '../../../view/LoadingFooter'
import { logger } from '../../../utils/Logger'
import { LazyListDataSource, PagingController } from '../../../utils/PagingUtil'
import { getObjectParam, pathStack } from '../../../utils/PathStackUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'

const TAG: string = 'diningOrderCreate'

/** 过敏原缓存条目（详情接口按需拉取结果） */
interface DiningAllergenCacheEntry {
  recipeId: number
  allergens: Array<string>
}

@Builder
export function DiningOrderCreateBuilder() {
  DiningOrderCreatePage()
}

@ComponentV2
struct DiningOrderCreatePage {
  // 复用模式参数（aboutToAppear 收窄；fromOrderId > 0 = 复用模式）
  private fromOrderId: number = 0
  private sourceTitle: string = ''
  private isReuseMode: boolean = false
  // 草稿编辑模式（路由 orderId > 0：加载既有 DRAFT，加/移除走 dishes 接口，CTA=提交订单）
  private editOrderId: number = 0
  private isDraftEditMode: boolean = false
  /** 草稿菜单编辑状态机（惰性创建 / 净差写 / version 乐观锁） */
  private draft: DiningDraftMenuEditor = new DiningDraftMenuEditor()
  /** 草稿写串行链：切换/邀请/提交 共用，保证 version 基准始终最新 */
  private draftWriteChain: Promise<void> = Promise.resolve()

  @Local titleInput: string = ''
  /** 常规模式：已勾选菜谱 ID（保序，提交按勾选顺序） */
  @Local selectedIds: Array<number> = []
  @Local categories: Array<DiningCategoryVo> = []
  @Local selectedCategoryId: number = 0
  @Local recipes: Array<DiningRecipeVo> = []
  @Local isLoading: boolean = true
  @Local isError: boolean = false
  @Local loadErrorMessage: string = ''
  @Local submitting: boolean = false
  // 复用模式：源单只读回显
  @Local reuseSections: Array<DiningMenuSection> = []
  @Local reuseRecipeCount: number = 0
  // 常规模式：触底加载更多态 / 追加失败态（驱动列表 footer）
  @Local isLoadingMore: boolean = false
  @Local appendError: boolean = false
  // 按压反馈 key（菜品行/入口，对齐安卓 pressable）+ reduced motion 降级
  @Local pressedKey: string = ''
  @Local reduceMotion: boolean = false
  /** LazyForEach 数据源（读取 recipes，实例稳定不可重建，避免整表重建） */
  private dataSource: LazyListDataSource<DiningRecipeVo> =
    new LazyListDataSource<DiningRecipeVo>(() => this.recipes)
  /** 轻量分页封装：首屏第 1 页 + 触底加载 / 上锁防重 / 封底与失败重试 */
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchRecipePage(page)
    },
    (loading: boolean): void => {
      this.isLoadingMore = loading
    }
  )
  /** 筛选切换序号：过期响应整体丢弃，避免旧分类结果污染新列表 */
  private recipeSeq: number = 0

  // ---- 忌口 / 份量（契约 §2.1/§2.5/§三.1/§三.5）----
  /** 用户忌口标签（GET 饮食偏好；空集=无忌口） */
  @Local userAllergens: Array<string> = []
  /** 每日热量目标（kcal；默认 2000） */
  @Local calorieTarget: number = DINING_DEFAULT_CALORIE_TARGET
  /** 人数本地态（1-20，默认 1；不落库） */
  @Local peopleCount: number = DINING_PEOPLE_MIN
  /** 已选菜 VO（保序，供热量合计/忌口警示派生） */
  @Local selectedRecipes: Array<DiningRecipeVo> = []
  /** 过敏原缓存（recipeId → 详情接口返回的过敏原码；按需拉取，失败不缓存） */
  @Local allergenCache: Array<DiningAllergenCacheEntry> = []
  /** 过敏原拉取在途（防重复请求） */
  @Local allergenPendingIds: Array<number> = []

  /** 购物车弹窗控制器（每次打开新建，保证入参为最新，对齐 MaterialListPage 先例） */
  private cartDialog: CustomDialogController | null = null

  aboutToDisappear(): void {
    // 键盘避让兜底复位（对齐 Workbench 先例）：路由守卫已收口，此处防焦点残留泄漏
    this.restoreKeyboardAvoidResize()
  }

  /**
   * 键盘避让 NONE 落位（仅标题输入聚焦期间，TD15 对齐安卓定案）：
   * 本页是「表单头 + 长列表 + 固定 CTA」混合页，全局默认 RESIZE 会整窗压缩——键盘弹出
   * 列表被压缩、底部 CTA 弹起跳变（与安卓 imePadding 整页上收同源问题）。改 NONE 让
   * 键盘直接覆盖列表下半部（输入场景列表本就无需交互），整页不上收。
   * 路由守卫（PathStackUtil.installKeyboardAvoidGuard）在导航切换时复位 RESIZE，
   * 失焦/离页页内自查兜底复位，避免影响登录/充值等需要避让的输入页。
   */
  private applyKeyboardAvoidNone(): void {
    try {
      this.getUIContext().setKeyboardAvoidMode(KeyboardAvoidMode.NONE)
      logger.debug(TAG, `键盘避让模式已设 NONE，回读=${this.getUIContext().getKeyboardAvoidMode()}`)
    } catch (err) {
      logger.warn(TAG, '设置键盘避让模式失败')
    }
  }

  /** 恢复全局 RESIZE（输入失焦 / 离开本页）：失败静默不阻塞 */
  private restoreKeyboardAvoidResize(): void {
    try {
      this.getUIContext().setKeyboardAvoidMode(KeyboardAvoidMode.RESIZE)
    } catch (err) {
      logger.warn(TAG, '恢复键盘避让模式失败')
    }
  }

  async aboutToAppear(): Promise<void> {
    this.reduceMotion = ReduceMotion.isEnabled()
    const param = getObjectParam<DiningOrderCreateParam>(RouteNames.DINING_ORDER_CREATE)
    if (param !== null) {
      this.editOrderId = param.orderId
      this.fromOrderId = param.fromOrderId
      this.sourceTitle = param.sourceTitle
    }
    // 草稿编辑优先（与 Android 同口径）：orderId > 0 时加载既有 DRAFT，否则按 fromOrderId 判复用
    this.isDraftEditMode = this.editOrderId > 0
    this.isReuseMode = !this.isDraftEditMode && this.fromOrderId > 0
    logger.debug(TAG, `创建页挂载 草稿编辑=${this.isDraftEditMode} orderId=${this.editOrderId} ` +
      `复用模式=${this.isReuseMode} fromOrderId=${this.fromOrderId}`)
    // 偏好首载（best-effort：失败按默认目标 + 无忌口降级，不阻断发起点单）
    await this.loadDietaryPreference()
    if (this.isDraftEditMode) {
      await this.loadCategories()
      await this.loadDraftDetail()
    } else if (this.isReuseMode) {
      await this.loadReuseSource()
    } else {
      await this.loadCategories()
    }
    this.isLoading = false
  }

  build() {
    NavDestination() {
      ThemeRefresh() {
        Column() {
          Row() {
            Column() {
              SymbolGlyph($r('sys.symbol.chevron_left'))
                .fontSize(24)
                .fontColor([$r('app.color.TEXT_PRIMARY')])
            }
            .width(DesignDimens.BACK_HIT_AREA)
            .height(DesignDimens.BACK_HIT_AREA)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)
            .onClick(() => {
              pathStack.pop()
            })

            Text(DiningText.dining_order_create_title)
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_PRIMARY'))

            Blank()

            // 草稿协同：邀请入口常显（复用模式无草稿语义，隐藏）；
            // 点击先惰性创建/冲刷草稿，再复制加入深链到剪贴板
            if (!this.isReuseMode) {
              Text(DiningText.dining_order_create_invite)
                .fontSize(DesignFontSize.PAGE_HEADER)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor($r('app.color.PRIMARY_DARK'))
                .padding({ left: 12, right: 12, top: 12, bottom: 12 })
                .onClick(() => {
                  this.requestInviteLink()
                })
            }
          }
          .width('100%')
          .height(56)
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
          .alignItems(VerticalAlign.Center)

          if (this.isLoading) {
            Column() {
              SkeletonList({ rows: 5 })
            }
            .width('100%')
            .layoutWeight(1)
            // 内容三态交叉淡变 300ms LinearOutSlowIn（对齐安卓 Crossfade(300)），reduced 降 180ms
            .transition(TransitionEffect.OPACITY)
            .animation(ReduceMotion.spec(MotionTiming.ENTER_CARD))
          } else if (this.isError) {
            Column() {
              EmptyState({ message: this.loadErrorMessage.length > 0
                ? this.loadErrorMessage
                : (this.isReuseMode ? DiningText.dining_order_detail_load_failed : DiningText.dining_order_create_load_failed) })
            }
            .width('100%')
            .layoutWeight(1)
            // 内容三态交叉淡变 300ms LinearOutSlowIn（对齐安卓 Crossfade(300)），reduced 降 180ms
            .transition(TransitionEffect.OPACITY)
            .animation(ReduceMotion.spec(MotionTiming.ENTER_CARD))
          } else {
            // 注：FormBody 为 @Builder 调用，无法挂 transition；内容态入场沿用安卓 Crossfade 语义，
            // 鸿蒙侧以骨架/错误态退场过渡近似（不额外包容器，避免破坏内部 layoutWeight 布局）
            this.FormBody()
          }
        }
        .width('100%')
        .height('100%')
        .padding({ top: safeTopVp(), bottom: safeBottomVp() })
        .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
  }

  /** 标题输入（TextInput 单行；占位与标签对齐安卓 dining_order_create_* 文案） */
  @Builder
  TitleInput() {
    Text(DiningText.dining_order_create_title_label)
      .fontSize(DesignFontSize.BODY_SMALL)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor($r('app.color.TEXT_PRIMARY'))
      .width('100%')
    TextInput({ text: this.titleInput, placeholder: DiningText.dining_order_create_title_hint })
      .placeholderColor($r('app.color.TEXT_TERTIARY'))
      .placeholderFont({ size: DesignFontSize.BODY })
      .caretColor($r('app.color.PRIMARY'))
      .backgroundColor($r('app.color.SURFACE_VARIANT'))
      .borderRadius(DesignDimens.INPUT_RADIUS)
      .fontSize(DesignFontSize.BODY)
      .fontColor($r('app.color.TEXT_PRIMARY'))
      .maxLength(64)
      .width('100%')
      .height(48)
      .margin({ top: DesignDimens.SPACE_XS })
      // 键盘弹出前设 NONE（Workbench 2026-11-05 实测：聚焦期设置才稳定生效），失焦复位
      .onFocus(() => {
        this.applyKeyboardAvoidNone()
      })
      .onBlur(() => {
        this.restoreKeyboardAvoidResize()
      })
      .onChange((value: string) => {
        this.titleInput = value
      })
  }

  /** 表单体：标题输入 →（复用提示+只读菜单 | 分类+菜品列表）→ 底部 CTA */
  @Builder
  FormBody() {
    Column() {
      if (this.isReuseMode) {
        // 复用模式：源单菜单只读且可能较长，整体滚动（视觉与改造前一致）
        Scroll() {
          Column() {
            this.TitleInput()
            this.ReuseHint()
            this.ReuseMenu()
            Column() {
            }
            .height(DesignDimens.SPACE_MD)
          }
          .width('100%')
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_SM,
            bottom: DesignDimens.SPACE_MD
          })
        }
        .scrollBar(BarState.Off)
        .layoutWeight(1)
        .align(Alignment.TopStart)
        .edgeEffect(EdgeEffect.Spring)
      } else {
        // 固定头部：标题输入 + 菜单标签 + 分类 chips + 已选计数（不随列表滚动）
        Column() {
          this.TitleInput()
          Text(DiningText.dining_order_create_menu_label)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor($r('app.color.TEXT_PRIMARY'))
            .width('100%')
            .margin({ top: DesignDimens.SPACE_MD })
          this.CategoryChips()
        }
        .width('100%')
        .padding({
          left: DesignDimens.PAGE_PADDING,
          right: DesignDimens.PAGE_PADDING,
          top: DesignDimens.SPACE_SM,
          bottom: DesignDimens.SPACE_MD
        })

        // 菜品列表（List + LazyForEach 懒加载；首屏第 1 页，触底按需加载更多）
        List({ space: DesignDimens.SPACE_XS }) {
          if (this.recipes.length === 0) {
            ListItem() {
              Text(DiningText.dining_recipe_list_empty_filtered)
                .fontSize(DesignFontSize.BODY)
                .fontColor($r('app.color.TEXT_SECONDARY'))
                .width('100%')
                .padding({ top: DesignDimens.SPACE_XL, bottom: DesignDimens.SPACE_XL })
            }
          }

          LazyForEach(this.dataSource, (recipe: DiningRecipeVo) => {
            ListItem() {
              this.RecipeRow(recipe)
            }
          // 键含勾选状态指纹：勾选变化若键不变，LazyForEach 复用旧子组件导致勾选框不刷新
          }, (recipe: DiningRecipeVo) => `recipe_${recipe.id}_${this.selectedIds.indexOf(recipe.id) >= 0 ? 1 : 0}`)

          ListItem() {
            this.ListFooter()
          }
        }
        .width('100%')
        .layoutWeight(1)
        .scrollBar(BarState.Off)
        .edgeEffect(EdgeEffect.Spring)
        .padding({
          left: DesignDimens.PAGE_PADDING,
          right: DesignDimens.PAGE_PADDING,
          bottom: DesignDimens.SPACE_MD
        })
        .onScrollIndex((start: number, end: number) => {
          // listLength 为 ListItem 真实数量（菜品行 + footer）
          this.paging.onScrollIndex(end, this.dataSource.totalCount() + 1)
        })
        .onScrollFrameBegin((offset: number) => {
          this.paging.onScrollFrameBegin(offset)
          return { offsetRemain: offset }
        })

        // 忌口警示（常规模式；固定于列表与底栏之间，非阻断）
        this.AllergenWarningBar()
      }

      // 底部单行购物车栏（复用模式只校验标题；常规/草稿编辑校验标题+菜单）
      this.BottomBar()
    }
    .width('100%')
    .layoutWeight(1)
  }

  /**
   * 底部单行购物车栏（契约 §五.2）：左侧信息区「已选 N 道 · M 人」（点击弹购物车弹窗，
   * 内含已选明细 + 移除 + 人数 stepper）+ 右侧主 CTA「提交订单」。
   * 复用模式无购物车交互，信息区展示源单菜品数且不可点。
   */
  @Builder
  BottomBar() {
    Row() {
      // 严格单行信息区（对齐安卓）：仅「已选 N 道 · M 人」，点击量与人数在购物车弹窗内
      Text(this.bottomInfoText())
        .fontSize(DesignFontSize.BODY)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor($r('app.color.TEXT_PRIMARY'))
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .layoutWeight(1)
      .onClick(() => {
        if (!this.isReuseMode) {
          this.openCart()
        }
      })

      AppButton({
        text: DiningText.dining_order_create_submit,
        loading: this.submitting,
        click: () => {
          this.confirmSubmit()
        }
      })
      .width(132)
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
    .padding({
      left: DesignDimens.PAGE_PADDING,
      right: DesignDimens.SPACE_SM,
      top: DesignDimens.SPACE_XS,
      bottom: Math.max(safeBottomVp(), DesignDimens.SPACE_MD)
    })
    .border({ width: { top: 0.5 }, color: $r('app.color.DIVIDER') })
    .backgroundColor($r('app.color.SURFACE'))
  }

  /** 底栏左侧信息文案：常规/草稿编辑「已选 N 道 · M 人」；复用模式「源单 N 道菜」 */
  private bottomInfoText(): string {
    if (this.isReuseMode) {
      return DiningText.menuCount(this.reuseRecipeCount)
    }
    return DiningText.cartSummary(this.selectedIds.length, this.peopleCount)
  }

  /**
   * 打开购物车弹窗（每次新建控制器，保证已选明细/人数/热量目标为最新快照）。
   * 弹窗内移除/人数变化经回调回写父页（父页写草稿差分 + 同步本地态）。
   */
  private openCart(): void {
    this.cartDialog = new CustomDialogController({
      builder: DiningCartPopup({
        selectedRecipes: this.selectedRecipes,
        peopleCount: this.peopleCount,
        calorieTarget: this.calorieTarget,
        onRemove: (recipeId: number): void => {
          this.removeSelectedRecipe(recipeId)
        },
        onPeopleChange: (count: number): void => {
          this.peopleCount = count
        }
      }),
      autoCancel: true,
      alignment: DialogAlignment.Bottom,
      customStyle: true
    })
    this.cartDialog.open()
  }

  /** 忌口警示条（选中菜 allergens ∩ 用户忌口；无命中不渲染） */
  @Builder
  AllergenWarningBar() {
    if (this.allergenWarningHits().length > 0) {
      Column({ space: 2 }) {
        Text(DiningText.dining_allergen_warning_title)
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.DANGER_INK'))
          .width('100%')
        ForEach(this.allergenWarningHits(), (hit: DiningAllergenHit) => {
          Text(DiningText.allergenWarningItem(DiningText.allergenLabel(hit.allergen), hit.recipeName))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor($r('app.color.DANGER_INK'))
            .width('100%')
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
        }, (hit: DiningAllergenHit): string => `allergen_${hit.recipeId}_${hit.allergen}`)
      }
      .alignItems(HorizontalAlign.Start)
      .width('100%')
      .backgroundColor($r('app.color.ICON_CONTAINER_PINK'))
      .borderRadius(DesignDimens.RADIUS_MD)
      .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM })
      .margin({
        left: DesignDimens.PAGE_PADDING,
        right: DesignDimens.PAGE_PADDING,
        bottom: DesignDimens.SPACE_XS
      })
    }
  }

  /** 列表 footer：加载更多 spinner / 失败重试 / 已全部加载 */
  @Builder
  ListFooter() {
    if (this.isLoadingMore) {
      LoadingFooter({ isLoading: true })
    } else if (this.appendError) {
      Text(DiningText.dining_order_create_load_failed)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor($r('app.color.DANGER_INK'))
        .width('100%')
        .textAlign(TextAlign.Center)
        .padding({ top: DesignDimens.SPACE_MD, bottom: DesignDimens.SPACE_MD })
        .onClick(() => {
          this.paging.retry()
        })
    } else if (this.recipes.length > 0) {
      Text(DiningText.dining_load_all_loaded)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor($r('app.color.TEXT_TERTIARY'))
        .width('100%')
        .textAlign(TextAlign.Center)
        .padding({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
    }
  }

  /** 复用提示条（浅绿胶囊提示历史菜单按原单复制不可修改） */
  @Builder
  ReuseHint() {
    Text(DiningText.reuseHint(this.sourceTitle.length > 0 ? this.sourceTitle : DiningText.dining_order_join_unknown_title,
      this.reuseRecipeCount))
      .fontSize(DesignFontSize.CAPTION)
      .fontColor($r('app.color.PRIMARY'))
      .backgroundColor($r('app.color.PRIMARY_LIGHT'))
      .borderRadius(DesignDimens.RADIUS_MD)
      .width('100%')
      .padding(DesignDimens.SPACE_MD)
      .margin({ top: DesignDimens.SPACE_MD })
  }

  /** 复用模式只读回显：源单菜单按分类分组（无勾选交互） */
  @Builder
  ReuseMenu() {
    Column({ space: DesignDimens.SPACE_MD }) {
      if (this.reuseSections.length === 0) {
        Text(DiningText.dining_order_detail_empty_menu)
          .fontSize(DesignFontSize.BODY)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .width('100%')
          .padding({ top: DesignDimens.SPACE_XL, bottom: DesignDimens.SPACE_XL })
      }
      ForEach(this.reuseSections, (section: DiningMenuSection) => {
        Column({ space: DesignDimens.SPACE_XS }) {
          Text(section.categoryName)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .width('100%')
            .padding({ top: DesignDimens.SPACE_XS })
          ForEach(section.recipes, (row: DiningMenuRecipeRow) => {
            Text(row.name)
              .fontSize(DesignFontSize.BODY)
              .fontColor($r('app.color.TEXT_PRIMARY'))
              .width('100%')
              .padding({ top: 6, bottom: 6 })
          }, (row: DiningMenuRecipeRow) => `reuse_${row.recipeId}`)
        }
        .width('100%')
      }, (section: DiningMenuSection) => `reuse_section_${section.categoryId}`)
    }
    .width('100%')
    .margin({ top: DesignDimens.SPACE_MD })
  }

  /** 分类筛选 chips（全部 + 分类目录；横滑） */
  @Builder
  CategoryChips() {
    Scroll() {
      Row({ space: DesignDimens.SPACE_XS }) {
        this.CategoryChip(0, DiningText.dining_category_all)
        ForEach(this.categories, (category: DiningCategoryVo) => {
          this.CategoryChip(category.id, category.name)
        }, (category: DiningCategoryVo) => `cat_${category.id}`)
      }
      .padding({ left: 2, right: 2 })
    }
    .scrollable(ScrollDirection.Horizontal)
    .scrollBar(BarState.Off)
    .align(Alignment.Start)
    .width('100%')
    .margin({ top: DesignDimens.SPACE_XS })
  }

  @Builder
  CategoryChip(categoryId: number, label: string) {
    Row() {
      Text(label)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(this.selectedCategoryId === categoryId ? $r('app.color.ON_PRIMARY') : $r('app.color.TEXT_SECONDARY'))
    }
    .justifyContent(FlexAlign.Center)
    .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: 10, bottom: 10 })
    .borderRadius(DesignDimens.RADIUS_FULL)
    .backgroundColor(this.selectedCategoryId === categoryId ? $r('app.color.PRIMARY') : $r('app.color.SURFACE'))
    .border({
      width: 1,
      color: this.selectedCategoryId === categoryId ? $r('app.color.PRIMARY') : $r('app.color.OUTLINE')
    })
    .onClick(() => {
      if (this.selectedCategoryId === categoryId) {
        return
      }
      this.selectedCategoryId = categoryId
      this.reloadRecipes()
    })
  }

  /**
   * 菜品行副行（对齐安卓定案）：食材名顿号连接（如「鸡蛋、牛奶、白砂糖…」），
   * 最多 2 行、超长省略；不显示热量与「N 种食材」计数。
   */
  private recipeIngredientLine(recipe: DiningRecipeVo): string {
    return recipe.ingredients.join('、')
  }

  /**
   * 菜品行：菜名（两行上限）+ 食材名副行（2 行上限）+ 右侧加/移除按钮。
   * 行点击 = 菜谱详情；右侧圆形按钮 = 加/移除（对齐安卓）。
   * 契约 §五.1 行内口径：按钮右内边距 12dp、文字（菜名/食材）右内边距 16dp（>12）。
   */
  @Builder
  RecipeRow(recipe: DiningRecipeVo) {
    Row() {
      Column({ space: 2 }) {
        // 两行上限（TD16）：大字体档单行只剩 3-4 字，两行保住菜名可读性
        Text(recipe.name)
          .fontSize(DesignFontSize.BODY)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        if (this.recipeIngredientLine(recipe).length > 0) {
          Text(this.recipeIngredientLine(recipe))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
        }
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
      .padding({ right: 16 })

      this.DishSelectButton(recipe)
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
    .padding({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM, left: DesignDimens.SPACE_XS, right: 12 })
    .borderRadius(DesignDimens.RADIUS_MD)
    .backgroundColor($r('app.color.SURFACE'))
    .border({ width: 1, color: $r('app.color.OUTLINE') })
    // 菜品行按压 100ms FastOutSlowIn scale 0.97（对齐安卓 pressable）；reduced 改 alpha 0.86
    .scale({
      x: !this.reduceMotion && this.pressedKey === `row_${recipe.id}` ? MotionScale.Press : 1,
      y: !this.reduceMotion && this.pressedKey === `row_${recipe.id}` ? MotionScale.Press : 1
    })
    .opacity(this.reduceMotion && this.pressedKey === `row_${recipe.id}` ? MotionAlpha.PressReduced : 1)
    .animation(MotionTiming.PRESS)
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = `row_${recipe.id}`
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = ''
      }
    })
    .onClick(() => {
      this.viewRecipe(recipe.id)
    })
  }

  /**
   * 加/移除按钮（48vp 触控热区 + 32vp 视觉圆，对齐安卓 AddDishButton）：
   * 未选 = outline 描边空心圆 + primary 加号；已选 = primary 实底 + 白色对勾。
   * 行点击进菜谱详情，加/移除只由本按钮承担（内层 onClick 消费，不与行点击冲突）。
   */
  @Builder
  DishSelectButton(recipe: DiningRecipeVo) {
    Stack({ alignContent: Alignment.Center }) {
      Column()
        .width(32)
        .height(32)
        .borderRadius(16)
        .backgroundColor(this.selectedIds.indexOf(recipe.id) >= 0
          ? $r('app.color.PRIMARY')
          : Color.Transparent)
        .border({
          width: 1,
          color: this.selectedIds.indexOf(recipe.id) >= 0
            ? $r('app.color.PRIMARY')
            : $r('app.color.OUTLINE')
        })

      if (this.selectedIds.indexOf(recipe.id) >= 0) {
        SymbolGlyph($r('sys.symbol.checkmark'))
          .fontSize(18)
          .fontColor([$r('app.color.ON_PRIMARY')])
      } else {
        SymbolGlyph($r('sys.symbol.plus'))
          .fontSize(18)
          .fontColor([$r('app.color.PRIMARY')])
      }
    }
    .width(48)
    .height(48)
    .onClick(() => {
      this.toggleRecipe(recipe.id)
    })
  }

  /** 拉全量分类（复用模式也拉，供分组回填分类名；失败随主流程进错误态） */
  private async loadCategories(): Promise<void> {
    try {
      const res = await getDiningCategories()
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.categories = res.data
      } else {
        logger.warn(TAG, `分类加载失败 code=${res.code} message=${res.message ?? ''}`)
        this.isError = true
        this.loadErrorMessage = DiningText.dining_category_load_failed
        showToastSafety(res.message, DiningText.dining_category_load_failed)
        return
      }
    } catch (err) {
      logger.error(TAG, '分类加载异常', err instanceof Error ? err.message : '')
      this.isError = true
      this.loadErrorMessage = DiningText.dining_category_load_failed
      showToastSafety('', DiningText.dining_category_load_failed)
      return
    }
    await this.reloadRecipes()
  }

  /**
   * 按当前分类重拉菜谱首屏（重置分页到第 1 页，整体替换数据源）。
   * 触底加载由 PagingController 驱动 fetchRecipePage(page>1)。
   */
  private async reloadRecipes(): Promise<void> {
    this.recipeSeq++
    this.paging.reset()
    this.recipes = []
    this.appendError = false
    this.dataSource.notifyReload()
    await this.fetchRecipePage(1)
  }

  /**
   * 按页拉取菜谱（PagingController 触底追加；第 1 页整体替换）。
   * @returns 本页是否有数据（供 PagingController 判断是否封底）
   */
  private async fetchRecipePage(page: number): Promise<boolean> {
    const seq = this.recipeSeq
    try {
      const res = await getDiningRecipes(page, DINING_PAGE_SIZE, this.selectedCategoryId, '')
      if (seq !== this.recipeSeq) {
        return false // 筛选已切换，过期响应整体丢弃
      }
      if (res.code !== BizCode.SUCCESS || res.data === null) {
        logger.warn(TAG, `菜谱分页未生效 page=${page} code=${res.code} message=${res.message ?? ''}`)
        if (page === 1) {
          this.isError = true
          this.loadErrorMessage = DiningText.dining_order_create_load_failed
          showToastSafety(res.message, DiningText.dining_order_create_load_failed)
          return false
        }
        this.appendError = true
        // 抛出以便 PagingController 记录失败页，footer 提供重试
        throw new Error(`菜谱第 ${page} 页加载失败`)
      }
      const records = res.data.records
      if (page === 1) {
        this.recipes = records
        this.dataSource.notifyReload()
      } else if (records.length > 0) {
        const startIndex: number = this.recipes.length
        this.recipes = this.recipes.concat(records)
        this.dataSource.notifyAppend(startIndex, records.length)
      }
      this.appendError = false
      logger.debug(TAG, `菜谱分页加载成功 page=${page} count=${records.length} total=${res.data.total}`)
      return records.length > 0
    } catch (err) {
      if (seq !== this.recipeSeq) {
        return false
      }
      logger.error(TAG, `菜谱分页加载异常 page=${page}`, err instanceof Error ? err.message : '')
      if (page === 1) {
        this.isError = true
        this.loadErrorMessage = DiningText.dining_order_create_load_failed
        showToastSafety('', DiningText.dining_order_create_load_failed)
        return false
      }
      this.appendError = true
      throw new Error(err instanceof Error ? err.message : '菜谱加载失败')
    }
  }

  /** 拉源单详情（复用模式：源单须为当前用户成员；后端非成员返回业务失败） */
  private async loadReuseSource(): Promise<void> {
    // 分类目录 best-effort 预拉（分组名回填；失败不致命，兜底「分类 {id}」）
    try {
      const catRes = await getDiningCategories()
      if (catRes.code === BizCode.SUCCESS && catRes.data !== null) {
        this.categories = catRes.data
      }
    } catch (err) {
      logger.debug(TAG, '复用模式分类目录预拉失败，分组名走兜底')
    }
    try {
      const res = await getDiningOrderDetail(this.fromOrderId, null)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const detail: DiningOrderDetailVo = res.data
        // 已结束的源单仍可复用（历史菜单复制），不拦截（后端为准）；此处仅留痕
        logger.debug(TAG, `复用源单状态=${detail.status}`)
        const rows: Array<DiningMenuRecipeRow> = detail.menu.map((recipe: DiningMenuRecipe): DiningMenuRecipeRow =>
          ({ recipeId: recipe.recipeId, name: recipe.name, categoryId: recipe.categoryId }))
        this.reuseRecipeCount = rows.length
        const names: Array<DiningCategoryName> = this.categories.map((c: DiningCategoryVo): DiningCategoryName =>
          ({ id: c.id, name: c.name }))
        this.reuseSections = groupDiningMenu(rows, names, DiningText.dining_order_create_menu_uncategorized)
      } else {
        logger.warn(TAG, `源单详情加载失败 code=${res.code} message=${res.message ?? ''}`)
        this.isError = true
        this.loadErrorMessage = DiningText.dining_order_detail_load_failed
        showToastSafety(res.message, DiningText.dining_order_detail_load_failed)
      }
    } catch (err) {
      logger.error(TAG, '源单详情加载异常', err instanceof Error ? err.message : '')
      this.isError = true
      this.loadErrorMessage = DiningText.dining_order_detail_load_failed
      showToastSafety('', DiningText.dining_order_detail_load_failed)
    }
  }

  /** 草稿编辑模式：加载既有 DRAFT（标题/菜单/version/凭证），失败进错误态 */
  private async loadDraftDetail(): Promise<void> {
    try {
      const res = await getDiningOrderDetail(this.editOrderId, null)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const detail: DiningOrderDetailVo = res.data
        const menu = diningMenuIdSet(detail.menu)
        this.draft.markCreated(detail.id, detail.joinToken)
        this.draft.onInitialSnapshot(detail.version, menu)
        this.titleInput = detail.title
        this.selectedIds = Array.from(menu)
        this.selectedRecipes = this.recipes.filter((recipe: DiningRecipeVo): boolean => menu.has(recipe.id))
        logger.debug(TAG, `草稿加载成功 orderId=${detail.id} menu=${menu.size} version=${detail.version}`)
      } else {
        logger.warn(TAG, `草稿详情加载失败 code=${res.code} message=${res.message ?? ''}`)
        this.isError = true
        this.loadErrorMessage = DiningText.dining_order_detail_load_failed
        showToastSafety(res.message, DiningText.dining_order_detail_load_failed)
      }
    } catch (err) {
      logger.error(TAG, '草稿详情加载异常', err instanceof Error ? err.message : '')
      this.isError = true
      this.loadErrorMessage = DiningText.dining_order_detail_load_failed
      showToastSafety('', DiningText.dining_order_detail_load_failed)
    }
  }

  /**
   * 同步本地选中态到草稿状态机意图（勾选框/营养聚合数据源）。
   * 顺序跟随状态机 localMenu（服务端顺序 + 未决差分），不依赖旧数组顺序。
   */
  private syncSelectedFromDraft(): void {
    const local = this.draft.localMenu
    this.selectedIds = Array.from(local)
    const kept: Array<DiningRecipeVo> = []
    const known = new Set<number>()
    this.selectedRecipes.forEach((recipe: DiningRecipeVo): void => {
      if (local.has(recipe.id)) {
        kept.push(recipe)
        known.add(recipe.id)
      }
    })
    this.recipes.forEach((recipe: DiningRecipeVo): void => {
      if (local.has(recipe.id) && !known.has(recipe.id)) {
        kept.push(recipe)
        known.add(recipe.id)
        this.ensureAllergen(recipe.id)
      }
    })
    this.selectedRecipes = kept
  }

  /** 触发草稿写链（串行）；forceCreate=true 时即使无待写差分也创建草稿（邀请入口用） */
  private ensureDraftAndFlush(forceCreate: boolean = false): void {
    this.draftWriteChain = this.draftWriteChain.then((): Promise<void> => {
      return this.runDraftWriteLoop(forceCreate)
    })
  }

  /** 草稿写循环：惰性创建 → 冲刷净差写（409 刷新重放；失败回滚并提示） */
  private async runDraftWriteLoop(forceCreate: boolean): Promise<void> {
    try {
      if (!this.draft.isCreated && (forceCreate || this.draft.pendingOp() !== null)) {
        await this.createDraft()
      }
      await this.flushDraftWrites()
    } catch (err) {
      logger.error(TAG, '草稿写异常', err instanceof Error ? err.message : '')
      showToastSafety('', DiningText.dining_order_create_write_failed)
    }
  }

  /** 惰性创建草稿：携带当前标题与本地菜单（空也可），落库后服务端真相即本地菜单 */
  private async createDraft(): Promise<void> {
    const menu = Array.from(this.draft.localMenu)
    const res = await createDiningOrder({
      title: this.titleInput.trim(),
      recipeIds: menu,
      fromOrderId: null
    })
    if (res.code !== BizCode.SUCCESS || res.data === null || res.data.orderId <= 0) {
      throw new Error(res.message ?? 'create failed')
    }
    this.draft.markCreated(res.data.orderId, res.data.joinToken)
    // create 已携带创建瞬间的菜单（version=0）；在途期间的新切换保留在未决里继续冲刷
    this.draft.onServerSnapshot(0, new Set<number>(menu))
    logger.info(TAG, `惰性创建草稿 orderId=${res.data.orderId} 菜品数=${menu.length}`)
  }

  /** 冲刷草稿菜单差分（调用方在写链内）：409 刷新后重放，直至本地意图与服务端一致 */
  private async flushDraftWrites(): Promise<void> {
    while (true) {
      const op = this.draft.pendingOp()
      if (op === null) {
        return
      }
      const version = this.draft.serverVersion
      try {
        const res = op.add
          ? await addDiningOrderDish(this.draft.orderId, { recipeId: op.recipeId, version: version })
          : await removeDiningOrderDish(this.draft.orderId, op.recipeId, version)
        if (res.code === BizCode.SUCCESS) {
          this.draft.onWriteSuccess(op)
        } else if (res.code === 409) {
          if (!(await this.refreshDraftMenu())) {
            return
          }
        } else {
          this.draft.onWriteFailure(op)
          this.syncSelectedFromDraft()
          showToastSafety(res.message, DiningText.dining_order_create_write_failed)
          return
        }
      } catch (err) {
        this.draft.onWriteFailure(op)
        this.syncSelectedFromDraft()
        logger.error(TAG, `草稿菜单写异常 recipeId=${op.recipeId}`, err instanceof Error ? err.message : '')
        showToastSafety('', DiningText.dining_order_create_write_failed)
        return
      }
    }
  }

  /** 拉取草稿最新详情刷新服务端真相（保留本地意图，差分在写链内重放） */
  private async refreshDraftMenu(): Promise<boolean> {
    if (!this.draft.isCreated) {
      return false
    }
    try {
      const res = await getDiningOrderDetail(this.draft.orderId, null)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.draft.onServerSnapshot(res.data.version, diningMenuIdSet(res.data.menu))
        this.syncSelectedFromDraft()
        return true
      }
      logger.warn(TAG, `草稿刷新失败 code=${res.code} message=${res.message ?? ''}`)
    } catch (err) {
      logger.error(TAG, '草稿刷新异常', err instanceof Error ? err.message : '')
    }
    return false
  }

  /**
   * 邀请：惰性创建（空草稿也可）并冲刷后跳邀请页（契约：邀请入口落 DiningOrderSharePage，
   * 邀请与分享合并；不再只复制到剪贴板）。无凭证提示不可邀请。
   */
  private requestInviteLink(): void {
    if (this.isReuseMode) {
      return
    }
    this.ensureDraftAndFlush(true)
    this.draftWriteChain.then((): void => {
      if (this.draft.orderId <= 0 || this.draft.joinToken.length === 0) {
        showToastSafety(DiningText.dining_order_invite_unavailable)
        return
      }
      const shareParam = new DiningOrderShareParam()
      shareParam.orderId = this.draft.orderId
      shareParam.joinToken = this.draft.joinToken
      pathStack.pushPathByName(RouteNames.DINING_ORDER_SHARE, shareParam)
    }).catch((err: Object): void => {
      logger.error(TAG, '邀请链接准备异常', err instanceof Error ? err.message : '')
      showToastSafety('', DiningText.dining_order_create_write_failed)
    })
  }

  /** 行点击：进菜谱详情（对齐安卓行点击语义；加/移除只由右侧按钮承担） */
  private viewRecipe(recipeId: number): void {
    if (recipeId <= 0) {
      return
    }
    pathStack.pushPathByName(RouteNames.DINING_RECIPE_DETAIL, recipeId)
  }

  /** 勾选/取消（草稿菜单净差写；复用模式只读忽略） */
  private toggleRecipe(recipeId: number): void {
    if (this.isReuseMode) {
      return
    }
    const willSelect = this.selectedIds.indexOf(recipeId) < 0
    if (willSelect && this.selectedIds.length >= 100) {
      showToastSafety(DiningText.dining_order_error_menu_too_many)
      return
    }
    this.draft.onToggle(recipeId)
    this.syncSelectedFromDraft()
    this.ensureDraftAndFlush()
  }

  /** 购物车弹窗移除：仅对本地意图中已存在的菜记移除差分（全员可移除任意菜） */
  private removeSelectedRecipe(recipeId: number): void {
    if (this.isReuseMode || !this.draft.localMenu.has(recipeId)) {
      return
    }
    this.draft.onToggle(recipeId)
    this.syncSelectedFromDraft()
    this.ensureDraftAndFlush()
  }

  /** 拉取饮食偏好（best-effort：失败按默认目标 + 无忌口降级，不阻断发起点单） */
  private async loadDietaryPreference(): Promise<void> {
    try {
      const res = await getDiningDietaryPreference()
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const preference: DietaryPreferenceVo = res.data
        this.userAllergens = preference.allergens === null ? [] : preference.allergens
        this.calorieTarget = preference.calorieTarget !== null && preference.calorieTarget > 0
          ? preference.calorieTarget
          : DINING_DEFAULT_CALORIE_TARGET
        logger.debug(TAG, `饮食偏好加载成功 忌口=${this.userAllergens.length} 热量目标=${this.calorieTarget}`)
      } else {
        logger.warn(TAG, `饮食偏好加载失败 code=${res.code} message=${res.message ?? ''}`)
      }
    } catch (err) {
      logger.error(TAG, '饮食偏好加载异常', err instanceof Error ? err.message : '')
    }
  }

  /** 过敏原缓存读取（未缓存返回 null，不阻塞渲染） */
  private allergenOf(recipeId: number): Array<string> | null {
    const entry = this.allergenCache.find((item: DiningAllergenCacheEntry): boolean => item.recipeId === recipeId)
    return entry !== undefined ? entry.allergens : null
  }

  /** 已选菜过敏原事实（缓存缺失按无过敏原参与，缓存补齐后警示自动出现） */
  private selectedDishes(): Array<DiningSelectedDish> {
    return this.selectedRecipes.map((recipe: DiningRecipeVo): DiningSelectedDish => ({
      recipeId: recipe.id,
      recipeName: recipe.name,
      allergens: this.allergenOf(recipe.id)
    }))
  }

  /** 忌口命中（选中菜 allergens ∩ 用户忌口） */
  private allergenWarningHits(): Array<DiningAllergenHit> {
    return diningAllergenHits(this.selectedDishes(), this.userAllergens)
  }

  /** 按需拉取菜谱详情缓存过敏原（失败静默，下次勾选重试） */
  private async ensureAllergen(recipeId: number): Promise<void> {
    if (recipeId <= 0 || this.allergenOf(recipeId) !== null) {
      return
    }
    if (this.allergenPendingIds.indexOf(recipeId) >= 0) {
      return
    }
    this.allergenPendingIds = this.allergenPendingIds.concat([recipeId])
    try {
      const res = await getDiningRecipeDetail(recipeId)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const allergens = res.data.allergens === null ? [] : res.data.allergens
        this.allergenCache = this.allergenCache.concat([{ recipeId: recipeId, allergens: allergens }])
        logger.debug(TAG, `过敏原缓存 recipeId=${recipeId} count=${allergens.length}`)
      } else {
        logger.warn(TAG, `过敏原加载失败 recipeId=${recipeId} code=${res.code}`)
      }
    } catch (err) {
      logger.error(TAG, `过敏原加载异常 recipeId=${recipeId}`, err instanceof Error ? err.message : '')
    } finally {
      this.allergenPendingIds = this.allergenPendingIds.filter((id: number): boolean => id !== recipeId)
    }
  }

  /** 结束点单二次确认（结束权=全体成员，防成员误触/抢先结束；确认后再走提交流程） */
  private async confirmSubmit(): Promise<void> {
    let confirmed = false
    try {
      const result = await promptAction.showDialog({
        title: DiningText.dining_order_submit_confirm_title,
        message: DiningText.dining_order_submit_confirm_message,
        buttons: [
          { text: DiningText.dining_order_submit_confirm_yes, color: $r('app.color.DANGER') },
          { text: '取消', color: $r('app.color.TEXT_SECONDARY') }
        ]
      })
      confirmed = result.index === 0
    } catch (err) {
      confirmed = false
    }
    if (confirmed) {
      await this.submit()
    }
  }

  /**
   * 提交订单（契约：全体成员均可「提交」→ 订单直接结束并生成购买清单）：
   * - 常规/草稿编辑：校验标题必填 + 菜单非空 → 冲刷草稿写 → submit（409 刷新重试）；
   * - 复用模式：校验标题 → create(fromOrderId) 复制菜单 → submit。
   * 成功后 replace 购买清单页（返回直回列表）。
   */
  private async submit(): Promise<void> {
    if (this.submitting) {
      return
    }
    if (this.isReuseMode) {
      const validation = validateDiningOrderReuse(this.titleInput)
      if (!validation.valid) {
        showToastSafety(this.orderErrorText(validation.error))
        return
      }
      this.submitting = true
      try {
        const orderId = await this.submitReusedOrder(this.fromOrderId, validation.normalizedTitle)
        this.afterSubmitSuccess(orderId)
      } catch (err) {
        const message = err instanceof Error ? err.message : ''
        logger.error(TAG, '复用模式提交异常', message)
        showToastSafety(message.length > 0 ? message : '', DiningText.dining_order_create_result_invalid)
      }
      this.submitting = false
      return
    }
    const menu = Array.from(this.draft.localMenu)
    const validation = validateDiningOrderForm(this.titleInput, menu)
    if (!validation.valid) {
      showToastSafety(this.orderErrorText(validation.error))
      return
    }
    this.submitting = true
    try {
      // 等待既有写链并补一次冲刷，保证 submit 携带最新 version
      this.ensureDraftAndFlush(true)
      await this.draftWriteChain
      const orderId = await this.submitDraft(validation.normalizedTitle)
      if (orderId !== null) {
        this.afterSubmitSuccess(orderId)
      }
    } catch (err) {
      logger.error(TAG, '提交订单异常', err instanceof Error ? err.message : '')
      showToastSafety('', DiningText.dining_order_create_result_invalid)
    }
    this.submitting = false
  }

  /**
   * 常规/草稿编辑：submit（409 刷新重试一次；菜单被并发清空时友好提示）。
   * @returns 成功订单 ID；失败已 Toast 并返回 null
   */
  private async submitDraft(normalizedTitle: string): Promise<number | null> {
    const res = await submitDiningOrder(this.draft.orderId, {
      title: normalizedTitle,
      version: this.draft.serverVersion
    })
    if (res.code === BizCode.SUCCESS) {
      return this.draft.orderId
    }
    if (res.code !== 409) {
      showToastSafety(res.message, DiningText.dining_order_create_result_invalid)
      return null
    }
    logger.info(TAG, 'submit 版本冲突，刷新草稿后重试')
    if (!(await this.refreshDraftMenu())) {
      showToastSafety('', DiningText.dining_order_create_result_invalid)
      return null
    }
    if (this.draft.serverMenu.size === 0) {
      showToastSafety(DiningText.dining_order_error_menu_empty)
      return null
    }
    const retry = await submitDiningOrder(this.draft.orderId, {
      title: normalizedTitle,
      version: this.draft.serverVersion
    })
    if (retry.code !== BizCode.SUCCESS) {
      showToastSafety(retry.message, DiningText.dining_order_create_result_invalid)
      return null
    }
    return this.draft.orderId
  }

  /** 复用模式：create(fromOrderId) 复制历史菜单 → 取服务端 version → submit */
  private async submitReusedOrder(fromOrderId: number, normalizedTitle: string): Promise<number> {
    const created = await createDiningOrder({
      title: normalizedTitle,
      recipeIds: null,
      fromOrderId: fromOrderId
    })
    if (created.code !== BizCode.SUCCESS || created.data === null || created.data.orderId <= 0) {
      throw new Error(created.message ?? 'create failed')
    }
    let version = 0
    const detail = await getDiningOrderDetail(created.data.orderId, null)
    if (detail.code === BizCode.SUCCESS && detail.data !== null) {
      version = detail.data.version
    }
    const submitted = await submitDiningOrder(created.data.orderId, {
      title: normalizedTitle,
      version: version
    })
    if (submitted.code !== BizCode.SUCCESS) {
      throw new Error(submitted.message ?? 'submit failed')
    }
    return created.data.orderId
  }

  /** 提交成功：Toast + replace 购买清单页（返回直回列表） */
  private afterSubmitSuccess(orderId: number): void {
    logger.info(TAG, `点单提交成功 orderId=${orderId}`)
    showToastSafety(DiningText.dining_order_create_submit_success)
    pathStack.replacePathByName(RouteNames.DINING_SHOPPING_LIST, orderId)
  }

  /** 表单错误枚举 → 文案 */
  private orderErrorText(error: DiningOrderFormError | null): string {
    if (error === DiningOrderFormError.TITLE_REQUIRED) {
      return DiningText.dining_order_error_title_required
    }
    if (error === DiningOrderFormError.TITLE_TOO_LONG) {
      return DiningText.dining_order_error_title_too_long
    }
    if (error === DiningOrderFormError.MENU_EMPTY) {
      return DiningText.dining_order_error_menu_empty
    }
    if (error === DiningOrderFormError.MENU_TOO_MANY) {
      return DiningText.dining_order_error_menu_too_many
    }
    return DiningText.dining_order_create_result_invalid
  }
}

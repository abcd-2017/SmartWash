// 点单页（共享购物车）— 对齐 Android DiningOrderDetailPage.kt（09 号票 + 10 号票简化版）
//
// 美团式左分类右菜品：左侧分类锚点点击滚动定位，右侧菜品行勾选/取消 + 备注
// （一人一菜一条，写操作带 knownVersion 的 version 乐观并发；写失败/409 一律回滚本地意图
// 并全量刷新，鸿蒙端按任务口径不做逐操作重放）+ 参与者区 + 底部已选栏
// + 发起者「结束点单」（二次确认）+ FINISHED 只读态。
// 轮询同步：页面可见时约 3s 带 knownVersion 轮询（unchanged 轻量），aboutToDisappear 停止；
// FINISHED 后停止轮询（对齐 Android DiningSyncLifecycle 语义）。
// 当前用户识别：详情成员只回传脱敏手机号，经 getUserInfo 手机号按同源脱敏规则比对定位。

import { BaseResponse } from '../../../network/BaseResponse'
import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import {
  DiningCategoryVo,
  DiningMenuRecipe,
  DiningOrderDetailVo,
  DiningOrderItem,
  DiningOrderMember,
  addDiningOrderItem,
  finishDiningOrder,
  getDiningCategories,
  getDiningOrderDetail,
  removeDiningOrderItem,
  updateDiningOrderItemNote
} from '../../../network/api/DiningApi'
import { getUserInfo } from '../../../network/api/UserApi'
import { UserInfoVo } from '../../../network/vo/UserInfoVo'
import {
  DiningCategoryName,
  DiningMenuRecipeRow,
  DiningMenuSection,
  DiningOrderStatus,
  diningOrderStatusFromCode,
  groupDiningMenu,
  resolveMyUserId
} from '../DiningLogic'
import { DiningText } from '../DiningText'
import { DiningCheckbox, DiningNoteDialog } from './DiningUiCommon'
import { DiningOrderShareParam } from '../api'
import {
  AppButton,
  AppButtonVariant,
  AppConfirmDialog,
  EmptyState,
  LoadingDots,
  SkeletonList
} from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { getNumberParam, pathStack } from '../../../utils/PathStackUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'

/** 日志 TAG */
const TAG: string = 'diningOrderDetail'

/** 轮询间隔（毫秒）：对齐 Android PollingDiningOrderSyncChannel 基准 3s */
const POLL_INTERVAL_MS: number = 3000

/** 左侧分类锚点宽度（vp） */
const CATEGORY_BAR_WIDTH: number = 88

/** 我的选菜本地意图条目（一人一菜一条） */
interface MyItemIntent {
  recipeId: number
  note: string
}

/** 菜品行其他选菜人展示条目 */
interface SelectorLabel {
  label: string
  note: string
  isMe: boolean
}

@Builder
export function DiningOrderDetailBuilder() {
  DiningOrderDetailPage()
}

@ComponentV2
struct DiningOrderDetailPage {
  private orderId: number = 0
  /** 服务端当前 version（轮询协议基准；写成功后经全量刷新同步） */
  private version: number = 0
  /** 当前用户 ID（手机号脱敏比对定位；0 = 未识别，降级只读不误判） */
  private myUserId: number = 0
  /** 成员 userId → 脱敏手机号目录（行级选菜人标识） */
  private memberLabels: Map<number, string> = new Map()

  @Local detail: DiningOrderDetailVo | null = null
  @Local isLoading: boolean = true
  @Local isError: boolean = false
  /** 我的选菜本地意图（乐观更新；服务端确认后重建） */
  @Local myItems: Array<MyItemIntent> = []
  /** 写操作进行中涉及的菜谱（阻止轮询覆盖本地意图 + 行级未决提示） */
  @Local pendingRecipeIds: Array<number> = []
  /** 分组后的左分类右菜品结构 */
  @Local sections: Array<DiningMenuSection> = []
  /** 左栏选中分类下标（滚动定位联动） */
  @Local activeSectionIndex: number = 0
  @Local finishing: boolean = false
  /** 备注弹窗目标菜谱（-1 = 关闭态） */
  @Local noteTargetRecipeId: number = -1
  @Local noteInitial: string = ''
  /** 写失败/刷新中的轻量同步提示 */
  @Local syncing: boolean = false
  // 右侧列表滚动控制器（左栏点击定位到对应分组）
  private listScroller: Scroller = new Scroller()
  /** 分组首行下标缓存（sections 重建时刷新） */
  private sectionOffsets: Array<number> = []
  /** 当前登录手机号（getUserInfo 拉取；识别「我的选菜」用） */
  private currentPhone: string = ''
  /** 分类目录（分组名回填源） */
  private categories: Array<DiningCategoryVo> = []
  // 轮询定时器（aboutToDisappear 必须 clear，CLAUDE.md 硬规则）
  private pollTimer: number = -1
  // 写操作互斥：进行中忽略新的勾选切换（防本地意图与在途写交错）
  private writeInFlight: boolean = false
  // 结束点单二次确认弹窗
  private finishDialog: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: DiningText.dining_order_detail_finish,
      message: DiningText.dining_order_detail_finish_confirm_message,
      onConfirm: (): void => {
        this.doFinish()
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  // 备注编辑弹窗
  private noteDialog: CustomDialogController = new CustomDialogController({
    builder: DiningNoteDialog({
      title: DiningText.dining_order_detail_note_title,
      hint: DiningText.dining_order_detail_note_hint,
      initialNote: this.noteInitial,
      onConfirm: (note: string): void => {
        this.saveNote(note)
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  async aboutToAppear(): Promise<void> {
    this.orderId = getNumberParam(RouteNames.DINING_ORDER_DETAIL) ?? 0
    if (this.orderId <= 0) {
      logger.warn(TAG, '点单页参数缺失 orderId')
      this.isLoading = false
      this.isError = true
      return
    }
    logger.debug(TAG, `点单页挂载 orderId=${this.orderId}`)
    await this.loadFirst()
    this.isLoading = false
    // 轮询仅在 ONGOING 时启动（FINISHED 静态只读）
    this.startPollingIfNeeded()
  }

  aboutToDisappear(): void {
    this.stopPolling()
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

            Text(DiningText.dining_order_detail_title)
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_PRIMARY'))
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .layoutWeight(1)

            Text(DiningText.dining_order_detail_share)
              .fontSize(DesignFontSize.PAGE_HEADER)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.PRIMARY_DARK'))
              .padding({ left: 12, right: 12, top: 12, bottom: 12 })
              .onClick(() => {
                this.goShare()
              })
          }
          .width('100%')
          .height(56)
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
          .alignItems(VerticalAlign.Center)

          if (this.isLoading) {
            Column() {
              SkeletonList({ rows: 6 })
            }
            .width('100%')
            .layoutWeight(1)
          } else if (this.isError || this.detail === null) {
            Column() {
              EmptyState({ message: DiningText.dining_order_detail_load_failed })
            }
            .width('100%')
            .layoutWeight(1)
          } else {
            Column() {
              if (this.readOnly) {
                Text(DiningText.dining_order_detail_finished_locked)
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor($r('app.color.TEXT_SECONDARY'))
                  .backgroundColor($r('app.color.SURFACE_VARIANT'))
                  .borderRadius(DesignDimens.RADIUS_FULL)
                  .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: 6, bottom: 6 })
                  .margin({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
              }
              this.ParticipantsBar()
              Row() {
                this.CategoryBar()
                this.MenuList()
              }
              .width('100%')
              .layoutWeight(1)
              .alignItems(VerticalAlign.Top)
              this.BottomBar()
            }
            .width('100%')
            .height('100%')
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

  /** 成员列表安全取值（ForEach 数据源显式类型化，规避 any 收窄告警） */
  private membersList(): Array<DiningOrderMember> {
    return this.detail?.members ?? new Array<DiningOrderMember>()
  }

  /** FINISHED 只读态（未知状态保守降级为只读） */
  private get readOnly(): boolean {
    return this.detail !== null && diningOrderStatusFromCode(this.detail.status) !== DiningOrderStatus.ONGOING
  }

  private get isCreator(): boolean {
    return this.detail !== null && this.detail.creatorUserId === this.myUserId && this.myUserId !== 0
  }

  /** 我的已选数量 */
  private get mySelectedCount(): number {
    return this.myItems.length
  }

  // ---------- 数据加载 ----------

  /** 首载：详情全量 + 分类目录（分组名回填，失败不致命）+ 当前手机号（定位「我的选菜」） */
  private async loadFirst(): Promise<void> {
    // 当前用户手机号 best-effort（失败降级为不识别我的选菜）
    try {
      const userRes: BaseResponse<UserInfoVo> = await getUserInfo()
      if (userRes.code === BizCode.SUCCESS && userRes.data !== null) {
        const phone = userRes.data.phoneNumber
        this.currentPhone = phone === null ? '' : phone
      }
    } catch (err) {
      logger.debug(TAG, '当前用户信息获取失败，「我的选菜」识别降级')
    }
    // 分类目录 best-effort（分组名回填）
    try {
      const catRes = await getDiningCategories()
      if (catRes.code === BizCode.SUCCESS && catRes.data !== null) {
        this.categories = catRes.data
      }
    } catch (err) {
      logger.debug(TAG, '分类目录加载失败，分组名走兜底')
    }
    try {
      const res = await getDiningOrderDetail(this.orderId, null)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.applyDetail(res.data)
        logger.debug(TAG, `点单详情首载成功 menu=${res.data.menu.length} items=${res.data.items.length} version=${res.data.version}`)
      } else {
        logger.warn(TAG, `点单详情加载失败 code=${res.code} message=${res.message ?? ''}`)
        this.isError = true
        showToastSafety(res.message, DiningText.dining_order_detail_load_failed)
      }
    } catch (err) {
      logger.error(TAG, '点单详情加载异常', err instanceof Error ? err.message : '')
      this.isError = true
      showToastSafety('', DiningText.dining_order_detail_load_failed)
    }
  }

  /**
   * 应用全量详情（服务端真相落地 + 分组派生 + 我的意图重建）。
   * 轮询全量与写后刷新共用：pending 写在途时轮询不进本方法（防覆盖本地意图）。
   */
  private applyDetail(data: DiningOrderDetailVo): void {
    this.detail = data
    this.version = data.version
    // 成员目录（userId → 脱敏手机号）与「我的」定位
    const ids: Array<number> = []
    const phones: Array<string> = []
    this.memberLabels = new Map()
    data.members.forEach((member: DiningOrderMember): void => {
      ids.push(member.userId)
      phones.push(member.phoneMasked)
      this.memberLabels.set(member.userId, member.phoneMasked)
    })
    this.myUserId = resolveMyUserId(ids, phones, this.currentPhone)
    // 分组派生（分类名回填；未分类收尾）
    const names: Array<DiningCategoryName> = this.categories.map((c: DiningCategoryVo): DiningCategoryName =>
      ({ id: c.id, name: c.name }))
    const rows: Array<DiningMenuRecipeRow> = data.menu.map((recipe: DiningMenuRecipe): DiningMenuRecipeRow =>
      ({ recipeId: recipe.recipeId, name: recipe.name, categoryId: recipe.categoryId }))
    this.sections = groupDiningMenu(rows, names, DiningText.dining_order_detail_uncategorized)
    this.sectionOffsets = []
    let offset = 0
    this.sections.forEach((section: DiningMenuSection): void => {
      this.sectionOffsets.push(offset)
      offset += section.recipes.length + 1 // 1 = 分组标题行
    })
    // 我的意图重建（服务端真相为准；仅在途写涉及的菜保留本地意图，防刚写入被旧快照覆盖）
    const rebuilt: Array<MyItemIntent> = []
    data.items.forEach((item: DiningOrderItem): void => {
      if (this.myUserId !== 0 && item.userId === this.myUserId) {
        rebuilt.push({ recipeId: item.recipeId, note: item.note })
      }
    })
    this.pendingRecipeIds.forEach((pendingId: number): void => {
      const local = this.myItems.find((entry: MyItemIntent): boolean => entry.recipeId === pendingId)
      if (local !== undefined && !rebuilt.some((entry: MyItemIntent): boolean => entry.recipeId === pendingId)) {
        rebuilt.push(local)
      }
    })
    this.myItems = rebuilt
    this.activeSectionIndex = 0
  }

  // ---------- 轮询同步 ----------

  private startPollingIfNeeded(): void {
    if (this.readOnly || this.pollTimer !== -1 || this.orderId <= 0) {
      return
    }
    this.pollTimer = setInterval(() => {
      this.pollOnce()
    }, POLL_INTERVAL_MS)
    logger.debug(TAG, '轮询已启动（3s 基准）')
  }

  private stopPolling(): void {
    if (this.pollTimer !== -1) {
      clearInterval(this.pollTimer)
      this.pollTimer = -1
      logger.debug(TAG, '轮询已停止')
    }
  }

  /** 单次轮询：knownVersion 轻量；unchanged 跳过；写在途跳过（防覆盖本地意图） */
  private async pollOnce(): Promise<void> {
    if (this.writeInFlight || this.version <= 0) {
      return
    }
    try {
      const res = await getDiningOrderDetail(this.orderId, this.version)
      if (res.code !== BizCode.SUCCESS || res.data === null) {
        return // 轮询失败静默（下个周期重试），不打断浏览
      }
      if (res.data.unchanged) {
        return
      }
      const finished = diningOrderStatusFromCode(res.data.status) !== DiningOrderStatus.ONGOING
      this.applyDetail(res.data)
      if (finished) {
        this.stopPolling()
        logger.info(TAG, '点单已被发起者结束，进入只读态')
      }
    } catch (err) {
      // 网络抖动静默，下个周期重试（debug 留痕不刷屏）
      logger.debug(TAG, `轮询拉取失败 ${err instanceof Error ? err.message : ''}`)
    }
  }

  /** 写后全量刷新（knownVersion=null：失败写不前进 version，带旧 version 会拿到 unchanged 拿不到最新） */
  private async refreshFromServer(): Promise<void> {
    this.syncing = true
    try {
      const res = await getDiningOrderDetail(this.orderId, null)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.applyDetail(res.data)
        if (diningOrderStatusFromCode(res.data.status) !== DiningOrderStatus.ONGOING) {
          this.stopPolling()
        }
      }
    } catch (err) {
      logger.warn(TAG, `写后全量刷新失败 ${err instanceof Error ? err.message : ''}`)
    }
    this.syncing = false
  }

  // ---------- 写操作 ----------

  /** 勾选/取消（乐观更新 → 写服务端；失败回滚 + 全量刷新） */
  private toggleRecipe(recipeId: number): void {
    if (this.readOnly || this.writeInFlight) {
      if (this.readOnly) {
        showToastSafety(DiningText.dining_order_detail_finished_locked)
      }
      return
    }
    const existing = this.myItems.find((entry: MyItemIntent): boolean => entry.recipeId === recipeId)
    this.writeInFlight = true
    if (existing !== undefined) {
      // 取消：乐观移除 → DELETE
      this.myItems = this.myItems.filter((entry: MyItemIntent): boolean => entry.recipeId !== recipeId)
      this.markPending(recipeId, true)
      removeDiningOrderItem(this.orderId, recipeId, this.version).then((res): void => {
        this.afterWrite(recipeId, res.code ?? 0, false)
      }).catch((err: Object): void => {
        logger.error(TAG, '取消选菜异常', err instanceof Error ? err.message : '')
        this.afterWrite(recipeId, 0, false)
      })
    } else {
      // 勾选：乐观加入（无备注）→ POST
      this.myItems = this.myItems.concat([{ recipeId: recipeId, note: '' }])
      this.markPending(recipeId, true)
      addDiningOrderItem(this.orderId, recipeId, '', this.version).then((res): void => {
        this.afterWrite(recipeId, res.code ?? 0, false)
      }).catch((err: Object): void => {
        logger.error(TAG, '勾选菜品异常', err instanceof Error ? err.message : '')
        this.afterWrite(recipeId, 0, false)
      })
    }
  }

  /** 保存备注（乐观更新 → PUT；空串 = 清空备注） */
  private saveNote(note: string): void {
    const recipeId = this.noteTargetRecipeId
    this.noteTargetRecipeId = -1
    if (recipeId <= 0 || this.readOnly || this.writeInFlight) {
      return
    }
    const existing = this.myItems.find((entry: MyItemIntent): boolean => entry.recipeId === recipeId)
    if (existing === undefined) {
      // 未勾选先勾选再写备注：一步 POST 携带 note（服务端一人一菜一条）
      this.writeInFlight = true
      this.myItems = this.myItems.concat([{ recipeId: recipeId, note: note }])
      this.markPending(recipeId, true)
      addDiningOrderItem(this.orderId, recipeId, note, this.version).then((res): void => {
        this.afterWrite(recipeId, res.code ?? 0, true)
      }).catch((err: Object): void => {
        logger.error(TAG, '勾选菜品（带备注）异常', err instanceof Error ? err.message : '')
        this.afterWrite(recipeId, 0, true)
      })
      return
    }
    if (existing.note === note) {
      return
    }
    this.writeInFlight = true
    this.myItems = this.myItems.map((entry: MyItemIntent): MyItemIntent =>
      entry.recipeId === recipeId ? { recipeId: entry.recipeId, note: note } : entry)
    this.markPending(recipeId, true)
    updateDiningOrderItemNote(this.orderId, recipeId, note, this.version).then((res): void => {
      this.afterWrite(recipeId, res.code ?? 0, true)
    }).catch((err: Object): void => {
      logger.error(TAG, '修改备注异常', err instanceof Error ? err.message : '')
      this.afterWrite(recipeId, 0, true)
    })
  }

  /**
   * 写结果统一收口：成功 → 全量刷新同步权威数据与 version；失败（含 409）→ 全量刷新回滚。
   * @param fromNoteSave 备注路径成功后的留痕
   */
  private afterWrite(recipeId: number, code: number, fromNoteSave: boolean): void {
    this.markPending(recipeId, false)
    this.writeInFlight = false
    if (code === BizCode.SUCCESS) {
      if (fromNoteSave) {
        logger.info(TAG, `备注已保存 recipeId=${recipeId}`)
      } else {
        logger.info(TAG, `选菜变更成功 recipeId=${recipeId}`)
      }
      this.refreshFromServer()
      return
    }
    if (code === 409) {
      logger.warn(TAG, `选菜写入版本冲突 recipeId=${recipeId}，全量刷新`)
      showToastSafety(DiningText.dining_order_conflict_refresh)
    } else {
      logger.warn(TAG, `选菜写入失败 code=${code}，全量刷新回滚`)
      showToastSafety(DiningText.dining_order_detail_write_failed)
    }
    this.refreshFromServer()
  }

  /** 未决标记（行级小点 + 轮询跳过） */
  private markPending(recipeId: number, pending: boolean): void {
    if (pending) {
      if (this.pendingRecipeIds.indexOf(recipeId) < 0) {
        this.pendingRecipeIds = this.pendingRecipeIds.concat([recipeId])
      }
    } else {
      this.pendingRecipeIds = this.pendingRecipeIds.filter((id: number): boolean => id !== recipeId)
    }
  }

  /** 结束点单（仅发起者且 ONGOING；成功 replace 购买清单页，对齐 popUpTo inclusive） */
  private async doFinish(): Promise<void> {
    if (this.finishing || !this.isCreator || this.readOnly) {
      return
    }
    this.finishing = true
    try {
      const res = await finishDiningOrder(this.orderId)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        logger.info(TAG, `点单已结束 orderId=${this.orderId}`)
        this.stopPolling()
        pathStack.replacePathByName(RouteNames.DINING_SHOPPING_LIST, this.orderId)
      } else {
        logger.warn(TAG, `结束点单未生效 code=${res.code} message=${res.message ?? ''}`)
        showToastSafety(res.message, DiningText.dining_order_detail_write_failed)
      }
    } catch (err) {
      logger.error(TAG, '结束点单异常', err instanceof Error ? err.message : '')
      showToastSafety('', DiningText.dining_order_detail_write_failed)
    }
    this.finishing = false
  }

  /** 进分享视图（发起者可带凭证重建二维码；参与者无凭证由分享页降级提示） */
  private goShare(): void {
    const param = new DiningOrderShareParam()
    param.orderId = this.orderId
    param.joinToken = this.detail?.joinToken ?? ''
    pathStack.pushPathByName(RouteNames.DINING_ORDER_SHARE, param)
  }

  // ---------- 行级派生 ----------

  /** 行的选菜人展示（含「我」标记与备注；他人按成员目录映射） */
  private selectorLabelsOf(recipeId: number): Array<SelectorLabel> {
    if (this.detail === null) {
      return []
    }
    const labels: Array<SelectorLabel> = []
    this.detail.items.forEach((item: DiningOrderItem): void => {
      if (item.recipeId !== recipeId) {
        return
      }
      const isMe = this.myUserId !== 0 && item.userId === this.myUserId
      const phone = this.memberLabels.get(item.userId) ?? ''
      labels.push({
        label: isMe ? '我' : (phone.length > 0 ? phone : `用户 ${item.userId}`),
        note: item.note,
        isMe: isMe
      })
    })
    return labels
  }

  // ---------- 子视图 ----------

  /** 参与者区（横向头像位 + 脱敏手机号 + 发起标记） */
  @Builder
  ParticipantsBar() {
    Scroll() {
      Row({ space: DesignDimens.SPACE_SM }) {
        Text(DiningText.participants(this.detail?.members.length ?? 0))
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor($r('app.color.TEXT_SECONDARY'))
        ForEach(this.membersList(), (member: DiningOrderMember): void => {
          Row({ space: 4 }) {
            Text(member.phoneMasked.length > 0 ? member.phoneMasked : `用户 ${member.userId}`)
              .fontSize(DesignFontSize.CAPTION)
              .fontColor($r('app.color.TEXT_PRIMARY'))
            if (member.isCreator) {
              Text(DiningText.dining_order_creator_badge)
                .fontSize(DesignFontSize.CAPTION)
                .fontColor($r('app.color.PRIMARY'))
                .backgroundColor($r('app.color.PRIMARY_LIGHT'))
                .borderRadius(DesignDimens.RADIUS_FULL)
                .padding({ left: 6, right: 6, top: 1, bottom: 1 })
            }
          }
          .alignItems(VerticalAlign.Center)
          .padding({ left: DesignDimens.SPACE_XS, right: DesignDimens.SPACE_XS, top: 4, bottom: 4 })
          .backgroundColor($r('app.color.SURFACE'))
          .borderRadius(DesignDimens.RADIUS_FULL)
          .border({ width: 1, color: $r('app.color.OUTLINE') })
        }, (member: DiningOrderMember): string => `member_${member.userId}`)
      }
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
    }
    .scrollable(ScrollDirection.Horizontal)
    .scrollBar(BarState.Off)
    .align(Alignment.Start)
    .width('100%')
    .padding({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
  }

  /** 左侧分类锚点栏 */
  @Builder
  CategoryBar() {
    Scroll() {
      Column({ space: DesignDimens.SPACE_XS }) {
        ForEach(this.sections, (section: DiningMenuSection, index: number) => {
          Column() {
            Text(section.categoryName)
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(this.activeSectionIndex === index ? DesignFontWeight.SEMI_BOLD : DesignFontWeight.NORMAL)
              .fontColor(this.activeSectionIndex === index ? $r('app.color.PRIMARY') : $r('app.color.TEXT_SECONDARY'))
              .textAlign(TextAlign.Center)
              .width('100%')
              .maxLines(2)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
          }
          .width(CATEGORY_BAR_WIDTH)
          .padding({ top: DesignDimens.SPACE_MD, bottom: DesignDimens.SPACE_MD })
          .backgroundColor(this.activeSectionIndex === index ? $r('app.color.SURFACE') : Color.Transparent)
          .borderRadius(DesignDimens.RADIUS_MD)
          .onClick(() => {
            this.scrollToSection(index)
          })
        }, (section: DiningMenuSection) => `cat_${section.categoryId}_${section.recipes.length}`)
      }
      .width('100%')
    }
    .scrollable(ScrollDirection.Vertical)
    .scrollBar(BarState.Off)
    // Scroll 内容小于视口时默认居中（与 AppPillTabRow 同款坑），必须显式 Top 对齐，
    // 否则分类锚点会垂到栏中部
    .align(Alignment.Top)
    .width(CATEGORY_BAR_WIDTH)
    .height('100%')
    .backgroundColor($r('app.color.BACKGROUND'))
  }

  /** 右侧菜品列表（分组标题 + 菜品行） */
  @Builder
  MenuList() {
    List({ scroller: this.listScroller, space: 0 }) {
      ForEach(this.sections, (section: DiningMenuSection) => {
        ListItem() {
          Text(section.categoryName)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .width('100%')
            .padding({
              left: DesignDimens.SPACE_SM,
              right: DesignDimens.SPACE_SM,
              top: DesignDimens.SPACE_MD,
              bottom: DesignDimens.SPACE_XS
            })
        }
        ForEach(section.recipes, (row: DiningMenuRecipeRow) => {
          ListItem() {
            this.MenuRow(row)
          }
        }, (row: DiningMenuRecipeRow) => `row_${row.recipeId}`)
      }, (section: DiningMenuSection) => `section_${section.categoryId}_${section.recipes.length}`)
    }
    .width('100%')
    .height('100%')
    .layoutWeight(1)
    .padding({ left: DesignDimens.SPACE_XS, right: DesignDimens.PAGE_PADDING })
    .scrollBar(BarState.Off)
    .edgeEffect(EdgeEffect.Spring)
    .onScrollIndex((start: number, end: number) => {
      // 右侧滚动 → 高亮当前分组（取可见首行所属分组）
      let active = 0
      for (let i = 0; i < this.sectionOffsets.length; i++) {
        if (end >= this.sectionOffsets[i]) {
          active = i
        }
      }
      if (active !== this.activeSectionIndex) {
        this.activeSectionIndex = active
      }
    })
  }

  /** 菜品行：勾选框 + 菜名 + 选菜人/备注 + 备注/取消入口 */
  @Builder
  MenuRow(row: DiningMenuRecipeRow) {
    Column({ space: DesignDimens.SPACE_XS }) {
      Row({ space: DesignDimens.SPACE_SM }) {
        // FINISHED 只读态：勾选框仅展示（行点击在 toggleRecipe 内拦截并提示）
        DiningCheckbox({ checked: this.isChecked(row.recipeId) })

        Text(row.name)
          .fontSize(DesignFontSize.BODY)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .layoutWeight(1)

        if (!this.readOnly && this.isChecked(row.recipeId)) {
          Text(DiningText.dining_order_detail_note_action)
            .fontSize(DesignFontSize.BUTTON_TEXT)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor($r('app.color.TEXT_GREEN'))
            .padding({ left: DesignDimens.SPACE_XS, right: DesignDimens.SPACE_XS, top: 6, bottom: 6 })
            .onClick(() => {
              this.openNoteDialog(row.recipeId)
            })
        }
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      // 选菜人行（共享购物车明细：谁选了 + 备注）
      if (this.selectorLabelsOf(row.recipeId).length > 0) {
        ForEach(this.selectorLabelsOf(row.recipeId), (selector: SelectorLabel) => {
          Row({ space: DesignDimens.SPACE_XS }) {
            Text(selector.label)
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(selector.isMe ? $r('app.color.PRIMARY') : $r('app.color.TEXT_SECONDARY'))
              .fontWeight(selector.isMe ? DesignFontWeight.SEMI_BOLD : DesignFontWeight.NORMAL)
            if (selector.note.length > 0) {
              Text(`「${selector.note}」`)
                .fontSize(DesignFontSize.CAPTION)
                .fontColor($r('app.color.TEXT_TERTIARY'))
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
                .layoutWeight(1)
            }
            if (this.pendingRecipeIds.indexOf(row.recipeId) >= 0) {
              LoadingDots()
            }
          }
          .width('100%')
          .padding({ left: 28 })
        }, (selector: SelectorLabel) => `sel_${row.recipeId}_${selector.label}_${selector.note}`)
      }
    }
    .width('100%')
    .padding({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM, left: DesignDimens.SPACE_XS, right: DesignDimens.SPACE_XS })
    .borderRadius(DesignDimens.RADIUS_MD)
    .backgroundColor($r('app.color.SURFACE'))
    .border({ width: 1, color: $r('app.color.OUTLINE') })
    .margin({ bottom: DesignDimens.SPACE_XS })
    .onClick(() => {
      this.toggleRecipe(row.recipeId)
    })
  }

  /** 底部已选栏：进行中 = 提示 + 已选数 + 同步态 + 发起者结束按钮；只读 = 查看购买清单 */
  @Builder
  BottomBar() {
    Column() {
      Divider()
        .color($r('app.color.HAIRLINE'))
        .strokeWidth(0.5)
      Row({ space: DesignDimens.SPACE_SM }) {
        Column({ space: 2 }) {
          if (!this.readOnly) {
            Text(DiningText.dining_order_detail_bottom_hint)
              .fontSize(DesignFontSize.CAPTION)
              .fontColor($r('app.color.TEXT_SECONDARY'))
            Row({ space: DesignDimens.SPACE_XS }) {
              Text(DiningText.detailSelectedCount(this.mySelectedCount))
                .fontSize(DesignFontSize.CAPTION)
                .fontWeight(DesignFontWeight.MEDIUM)
                .fontColor($r('app.color.TEXT_PRIMARY'))
              if (this.syncing || this.pendingRecipeIds.length > 0) {
                Text(DiningText.dining_order_detail_syncing)
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor($r('app.color.TEXT_TERTIARY'))
              }
            }
            .alignItems(VerticalAlign.Center)
          } else {
            Text(DiningText.dining_order_detail_finished_locked)
              .fontSize(DesignFontSize.CAPTION)
              .fontColor($r('app.color.TEXT_SECONDARY'))
          }
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)

        if (!this.readOnly && this.isCreator) {
          AppButton({
            small: true,
            text: DiningText.dining_order_detail_finish,
            loading: this.finishing,
            click: () => {
              this.finishDialog.open()
            }
          })
        } else if (this.readOnly) {
          AppButton({
            small: true,
            text: DiningText.dining_order_detail_view_shopping_list,
            click: () => {
              pathStack.pushPathByName(RouteNames.DINING_SHOPPING_LIST, this.orderId)
            }
          })
        }
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
      .padding({
        left: DesignDimens.PAGE_PADDING,
        right: DesignDimens.PAGE_PADDING,
        top: DesignDimens.SPACE_SM,
        bottom: Math.max(safeBottomVp(), DesignDimens.SPACE_MD)
      })
    }
    .width('100%')
    .backgroundColor($r('app.color.BACKGROUND'))
  }

  /** 是否已勾选（本地意图） */
  private isChecked(recipeId: number): boolean {
    return this.myItems.some((entry: MyItemIntent): boolean => entry.recipeId === recipeId)
  }

  /** 打开备注弹窗（回显当前备注） */
  private openNoteDialog(recipeId: number): void {
    const existing = this.myItems.find((entry: MyItemIntent): boolean => entry.recipeId === recipeId)
    this.noteInitial = existing?.note ?? ''
    this.noteTargetRecipeId = recipeId
    this.noteDialog.open()
  }

  /** 左栏点击定位：右侧列表滚到对应分组首行 */
  private scrollToSection(index: number): void {
    this.activeSectionIndex = index
    const offset = this.sectionOffsets[index] ?? 0
    this.listScroller.scrollToIndex(offset)
  }
}

import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
import { getUserInfo } from "../network/api/UserApi"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { LaundryItem } from "../network/vo/LaundryItem"
import { getLaundryItems } from "../network/api/LaundryItemsApi"
import { reservationLaundry } from "../network/api/OrderApi"
import { getNumberParam, pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { logger } from "../utils/Logger"
import { AppButton, AppButtonVariant, AppInfoDialog, EmptyState, IconBox, LoadingState, PageHeader } from "../view/common/AppComponents"
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight, MotionTiming } from "../constant/DesignSystem"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import vibrator from '@ohos.vibrator'
import { ThemeRefresh } from '../view/common/ThemeRefresh'
import { resColor } from '../utils/ResourceColor'
import { resString } from '../utils/ResourceString'

@Builder
export function LaundryBuilder() {
  Laundry()
}

@Component
struct Laundry {
  @State userInfo: UserInfoVo | null = null
  @State laundryItemList: Array<LaundryItem> | null = null
  @State selectItemId: number | null = null
  @State totalPrice: number | null = 0
  // 页面首屏加载态：请求期间全屏 Loading（对齐安卓 LoadingState）
  @State pageLoading: boolean = true
  // 套餐加载失败标记：置位后展示 EmptyState + 重试（S1，对齐安卓 Error toast + 空列表的可行动兜底）
  @State loadFailed: boolean = false
  // 预约请求进行中标记：配合 AppButton loading 展示并拦截连点。
  // 后端预约接口无幂等（评审报告 P0），前端防重是唯一防线
  @State reserving: boolean = false
  // 套餐卡 / 「更换」按压态（I2，对齐安卓 pressable scale 0.97）
  @State pressedItemId: number | null = null
  @State changePressed: boolean = false
  // 路由预选套餐 id（F1，-1 = 不指定；对齐安卓 LaundryRoute.Laundry 的 ARG_ITEM_ID）
  private defaultItemId: number = -1
  // 未选套餐提示弹窗（对齐安卓 AppInfoDialog，替代 toast 形态）
  private selectDialogController: CustomDialogController = new CustomDialogController({
    builder: AppInfoDialog({ message: resString('please_select_package') }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })
  // 更换学校提示弹窗（对齐安卓「更换学校信息请联系客服！」）
  private changeSchoolDialogController: CustomDialogController = new CustomDialogController({
    builder: AppInfoDialog({ message: resString('change_school_contact_service') }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  async aboutToAppear() {
    logger.debug('laundry', '预约寄存页挂载，拉取用户信息与套餐列表')
    // 路由预选参数（F1）：服务页套餐行携 id 跳入时落地默认选中（缺失/非法 = 不指定）
    const routeItemId = getNumberParam(RouteNames.LAUNDRY)
    if (routeItemId !== null && routeItemId > 0) {
      this.defaultItemId = routeItemId
    }
    // ArkTS 禁止解构声明（arkts-no-destruct-decls），改为先并发发起、再逐个 await（保持并发语义）
    const resTask = getUserInfo()
    const itemResTask = getLaundryItems()
    this.pageLoading = true
    this.loadFailed = false
    try {
      let res = await resTask
      let itemRes = await itemResTask
      if (res.code === HttpStatusCode.Ok) {
        this.userInfo = res.data
      } else {
        showToastSafety(res.message, resString('laundry_1'))
      }
      if (itemRes.code === HttpStatusCode.Ok) {
        this.laundryItemList = itemRes.data
        this.applyRoutePreselection()
      } else {
        logger.warn('laundry', `套餐列表加载失败 code=${itemRes.code} message=${itemRes.message ?? ""}`)
        logger.warn('laundry', `用户信息加载失败 code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(itemRes.message, resString('laundry_2'))
        this.loadFailed = true
      }
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：兜住并进失败态，避免 pageLoading 卡死永久骨架
      showToastSafety(resString('laundry_3'))
      this.loadFailed = true
    } finally {
      this.pageLoading = false
    }
  }

  /**
   * 路由预选落地（F1，对齐安卓 LaundryPage.kt:103-112）：
   * 仅首次（用户未手选）且套餐列表已就绪时按 id 预选，用户手动改选后不再覆盖
   */
  private applyRoutePreselection(): void {
    if (this.selectItemId !== null || this.defaultItemId <= 0 || this.laundryItemList === null) {
      return
    }
    const target = this.laundryItemList.find((item: LaundryItem) => item.itemId === this.defaultItemId)
    if (target !== undefined) {
      this.selectItemId = target.itemId
      this.totalPrice = target.basePrice
      // 路由预选落地留痕（低频一次性，记 debug）
      logger.debug('laundry', `路由预选套餐 itemId=${target.itemId}`)
    }
  }

  /** 失败重试（S1）：重拉套餐列表，成功后补落路由预选 */
  async retryFetchItems() {
    this.pageLoading = true
    this.loadFailed = false
    try {
      let itemRes = await getLaundryItems()
      if (itemRes.code === HttpStatusCode.Ok) {
        this.laundryItemList = itemRes.data
        this.applyRoutePreselection()
      } else {
        logger.warn('laundry', `套餐重试仍失败 code=${itemRes.code} message=${itemRes.message ?? ""}`)
        showToastSafety(itemRes.message, resString('laundry_2'))
        this.loadFailed = true
      }
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：兜住并进失败态，避免 pageLoading 卡死永久骨架
      showToastSafety(resString('laundry_3'))
      this.loadFailed = true
    } finally {
      this.pageLoading = false
    }
  }

  /** 选择触感（I1，对齐安卓 HapticEffect.SELECTION）：设备不支持时静默忽略 */
  private hapticSelection(): void {
    try {
      vibrator.startVibration({ type: 'time', duration: 10 }, { id: 0, usage: 'unknown' })
    } catch (err) {
      // 不支持触感（模拟器 / 部分设备）时忽略
    }
  }

  /**
   * 提交预约（资金链路防重）：
   * reserving 标记 + AppButton loading 双保险，请求期间按钮不可点，连点只发一次请求。
   */
  async submitReservation() {
    if (this.selectItemId === null) {
      this.selectDialogController.open()
      return
    }
    if (this.reserving) {
      logger.debug('laundry', '预约请求进行中，忽略重复触发')
      return
    }
    this.reserving = true
    try {
      let res = await reservationLaundry({
        itemsId: this.selectItemId,
        totalPrice: this.totalPrice
      })
      if (res.code === HttpStatusCode.Ok) {
        if (res.data !== null) {
          // 低频关键动作成功：预约提交成功（资金链路入口）留 info，仅记套餐与金额摘要
          logger.info('laundry', `预约提交成功 itemId=${this.selectItemId} totalPrice=${this.totalPrice?.toFixed(2)} orderId=${res.data}`)
          pathStack.pop()
          // 统一传 number 类型 orderId（评审 #6：原模板字符串导致 Payment 收到字符串）
          pathStack.pushPathByName(RouteNames.PAYMENT, res.data)
        } else {
          logger.warn('laundry', `下单成功但未返回订单号 code=${res.code} message=${res.message ?? ""}`)
          showToastSafety(res.message, resString('laundry_4'))
        }
      } else {
        logger.warn('laundry', `预约被拒绝 code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, resString('laundry_4'))
      }
    } finally {
      this.reserving = false
    }
  }

  @Builder
  LaundryTypeItem(item: LaundryItem) {
    // @Builder 内仅允许 UI 语法，选中态直接内联计算（原 let isSelected 不合法）
    // 选中态对齐安卓：primaryLight 底 + primary 描边 + 36dp 指示器盒（LaundryPage.kt:296-418）
    Row() {
      // 选中指示器（F2）：36dp 盒（radiusSmall 10）+ 20dp 空心环 + 12dp 勾，弹簧过渡
      Stack() {
        // 盒底色：选中 primaryLight / 未选 surfaceVariant
        Column()
          .width(36)
          .height(36)
          .borderRadius(DesignDimens.RADIUS_SMALL)
          .backgroundColor(this.selectItemId === item.itemId ? $r('app.color.PRIMARY_LIGHT') : $r('app.color.SURFACE_VARIANT'))
          .animation(MotionTiming.ENTER)

        // 空心环：选中 primary / 未选 outline
        Column()
          .width(20)
          .height(20)
          .borderRadius(10)
          .border({
            width: 1.5,
            color: this.selectItemId === item.itemId ? $r('app.color.PRIMARY') : $r('app.color.OUTLINE')
          })
          .animation(MotionTiming.ENTER)

        // 勾：选中时自圆心弹簧长出（尺寸恒定，仅内容显现/隐去）
        SymbolGlyph($r('sys.symbol.checkmark'))
          .fontSize(12)
          .fontColor([$r('app.color.PRIMARY')])
          .scale({ x: this.selectItemId === item.itemId ? 1 : 0, y: this.selectItemId === item.itemId ? 1 : 0 })
          .animation(MotionTiming.ENTER)
      }
      .width(36)
      .height(36)
      .margin({ right: 12 })

      Column() {
        // 套餐卡标题（V4：bodyLarge 16/500，原 18 Bold 与描述落差过大）
        Text(`${item.itemName}`)
          .fontSize(DesignFontSize.BODY)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor($r('app.color.TEXT_PRIMARY'))

        Text(`${item.description}`)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .margin({ top: 4 })
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)

      // 金额（V5：恒 primaryDark 600 + tnum，不随选中变色；单行防折行）
      Text(`¥${item.basePrice?.toFixed(2)}`)
        .fontSize(DesignFontSize.AMOUNT_MEDIUM)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontFeature(DesignFontFeature.TNUM)
        .fontColor($r('app.color.PRIMARY_DARK'))
        .maxLines(1)
        .margin({ left: 18 })
    }
    .width("100%")
    .alignItems(VerticalAlign.Center)
    .backgroundColor(this.selectItemId === item.itemId ? $r('app.color.PRIMARY_LIGHT') : $r('app.color.SURFACE'))
    .padding(DesignDimens.CARD_PADDING)
    // 卡片唯一画法（规范 §3.1）：1px 描边 + level1 卡片阴影；
    // 描边宽度不再随选中变化（对齐安卓 v0.4），选中强调由 primaryLight 底 + primary 描边承担
    .border({
      width: 1,
      color: this.selectItemId === item.itemId ? $r('app.color.PRIMARY') : $r('app.color.OUTLINE'),
      radius: DesignDimens.CARD_RADIUS
    })
    .borderRadius(DesignDimens.CARD_RADIUS)
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: $r('app.color.SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: 2
    })
    // V9 选中过渡曲线：200ms EaseOut → 弹簧（对齐安卓 defaultSpring）
    .animation(MotionTiming.ENTER)
    // I2 按压反馈：scale 0.97（对齐安卓 pressable；stateEffect 非通用属性，见 DivComponents.ets 注释）
    .scale({
      x: this.pressedItemId === item.itemId ? 0.97 : 1,
      y: this.pressedItemId === item.itemId ? 0.97 : 1
    })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedItemId = item.itemId
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedItemId = null
      }
    })
    .onClick(() => {
      // I1 选中触感先于选中赋值（对齐安卓 LaundryPage.kt:340-342 的因果顺序）
      this.hapticSelection()
      this.selectItemId = item.itemId
      this.totalPrice = item.basePrice
    })
    // V8 列表项间距：卡间距 16（对齐安卓每项后置 Spacer 16）；
    // 水平边距由 List 的 padding 承担（原 left/right margin 叠加 width 100% 会把卡片推出右屏，问题7）
    .margin({ bottom: DesignDimens.CARD_SPACING })
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Stack({ alignContent: Alignment.Bottom }) {
        Scroll() {
          Column() {
            // 页头（V1：公共 PageHeader —— 22/600 标题 + 48dp 返回热区 + 内置连击防抖）
            PageHeader({ title: resString('book_locker') })

            // 投递柜标题（T1：对齐安卓 delivery_locker）
            Text($r('app.string.delivery_locker'))
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_PRIMARY'))
              .width("100%")
              .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

            Column()
              .height(12)

            // 学校卡（问题7 同类修复：width("100%") 与 margin 叠加会被布局顶掉导致卡片横贯全屏，
            // 水平边距改由外层容器 padding 承担——与下方套餐列表 List 的 padding 修法一致）
            Column() {
              Row() {
                // 投递柜图标（V6：IconBox 44/24 绿系 tile，替代裸 SymbolGlyph）
                IconBox({
                  icon: $r('sys.symbol.circle_below_local_filled'),
                  sizeValue: 44,
                  iconSize: 24,
                  containerColor: $r('app.color.PRIMARY_LIGHT'),
                  iconColor: $r('app.color.PRIMARY_DARK')
                })

                Column() {
                  Text(`${this.userInfo?.schoolVo?.schoolName}`)
                    .fontSize(DesignFontSize.CARD_TITLE)
                    .fontWeight(DesignFontWeight.SEMI_BOLD)
                    .fontColor($r('app.color.TEXT_PRIMARY'))

                  Text(`${this.userInfo?.schoolVo?.location}`)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor($r('app.color.TEXT_SECONDARY'))
                    .maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                }
                .alignItems(HorizontalAlign.Start)
                .layoutWeight(1)
                .margin({ left: 12 })

                // 「更换」动作（V7）：16/600 primaryDark + 12/12 热区，弹「联系客服」提示弹窗
                // 垂直内边距 12 → 16：把触控热区从 42.9vp 抬到 ≥48vp
                Column() {
                  Text($r('app.string.change'))
                    .fontSize(DesignFontSize.BODY)
                    .fontWeight(DesignFontWeight.SEMI_BOLD)
                    .fontColor($r('app.color.PRIMARY_DARK'))
                    .padding({ left: 12, right: 12, top: 16, bottom: 16 })
                }
                .scale({ x: this.changePressed ? 0.97 : 1, y: this.changePressed ? 0.97 : 1 })
                .onTouch((event: TouchEvent) => {
                  if (event.type === TouchType.Down) {
                    this.changePressed = true
                  } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
                    this.changePressed = false
                  }
                })
                .onClick(() => {
                  this.changeSchoolDialogController.open()
                })
              }
              .width("100%")
              .alignItems(VerticalAlign.Center)
              .borderRadius(DesignDimens.CARD_RADIUS)
              .backgroundColor($r('app.color.SURFACE'))
              // 卡片唯一画法（规范 §3.1）：1px OUTLINE 描边 + level1 卡片阴影
              .border({ width: 1, color: $r('app.color.OUTLINE') })
              .shadow({
                radius: DesignElevation.LEVEL_1,
                color: $r('app.color.SHADOW_COLOR_CARD'),
                offsetX: 0,
                offsetY: 2
              })
              .padding(DesignDimens.CARD_PADDING)
            }
            .width("100%")
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

            Column()
              .height(DesignDimens.SECTION_SPACING)

            // 洗护套餐标题
            Text($r('app.string.laundry_package'))
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_PRIMARY'))
              .width("100%")
              .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

            Column()
              .height(12)

            // 套餐列表（补 ForEach key：itemId，对齐安卓 LazyColumn key）；
            // 水平内边距收在 List 上，ListItem 宽度即为卡宽（修复卡片横向溢出，问题7）
            List() {
              ForEach(this.laundryItemList, (item: LaundryItem) => {
                ListItem() {
                  this.LaundryTypeItem(item)
                }
              }, (item: LaundryItem) => item.itemId?.toString())
            }
            .width("100%")
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

            // 内容区 bottom 预留（V10）：底部 CTA 实高 ≈ 0.5 + 16 + 按钮 + 20
            Column()
              .height(100)
          }
        }
        .scrollBar(BarState.Off)
        .width("100%")
        .height("100%")
        .align(Alignment.Top)
        // 沉浸式：内容避让状态栏（背景延伸，问题5/1）
        .padding({ top: safeTopVp() })
        .backgroundColor($r('app.color.BACKGROUND'))

        // 底部操作栏（V2/V3：0.5dp 分隔 + 全宽按钮 + 金额内嵌文案，移除左置「预估费用」）
        Column() {
          Divider()
            .strokeWidth(0.5)
            .color($r('app.color.OUTLINE'))

          // AppButton 自带 loading 防重（请求中禁点），配合 reserving 拦截连点
          AppButton({
            text: this.selectItemId === null
              ? resString('confirm_booking')
              // 金额统一走 ¥ 前缀（对齐安卓 currency_format ¥%s，避免按钮只显示「确认预约 · 29.90」）
              : `确认预约 · ¥${this.totalPrice?.toFixed(2) ?? "0.00"}`,
            loading: this.reserving,
            click: () => {
              this.submitReservation()
            }
          })
          .margin({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_MD,
            bottom: DesignDimens.SPACE_LG + safeBottomVp()
          })
        }
        .width("100%")
        .backgroundColor($r('app.color.SURFACE'))
        .zIndex(1)
        .align(Alignment.Bottom)

        // 首屏加载态（对齐安卓全屏 LoadingState）
        if (this.pageLoading) {
          // 首屏加载态（对齐安卓 LoadingState：骨架屏，§3.6 禁转圈）
          LoadingState()
          .width("100%")
          .height("100%")
          .backgroundColor($r('app.color.BACKGROUND'))
          .zIndex(2)
        }

        // 失败态（S1）：EmptyState + 重试按钮，替代原「toast 后留空列表」
        if (!this.pageLoading && this.loadFailed) {
          Column() {
            EmptyState({
              message: resString('laundry_2'),
              action: () => {
                AppButton({
                  small: true,
                  text: resString('retry'),
                  variant: AppButtonVariant.SECONDARY,
                  click: () => {
                    this.retryFetchItems()
                  }
                })
                .margin({ top: DesignDimens.SPACE_MD })
              }
            })
          }
          .width("100%")
          .height("100%")
          .backgroundColor($r('app.color.BACKGROUND'))
          .justifyContent(FlexAlign.Center)
          .zIndex(3)
        }
      }
      }
    }
    // 修复（问题6）：未隐藏系统标题栏导致页面出现两个返回按钮（系统圆钮 + 自绘 PageHeader）
    .hideTitleBar(true)
  }
}

// 加入入口页 — 对齐 Android DiningJoinEntryPage.kt（12 号票站内信深链加入入口）
//
// 深链 `smartwash://dining/order/{orderId}?t={token}` 落点：brief 判重（DiningJoinEntry 纯函数）——
// 已是成员 → replace 直达点单页；非成员且进行中 → 标题预览 + 「加入」按钮；已结束 → 提示不引导；
// brief 失败 → 错误态重试。未登录由壳层深链守卫拦截（dining 不在公开白名单）。

import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import { DiningOrderBriefVo, getDiningOrderBrief, joinDiningOrder } from '../../../network/api/DiningApi'
import { DiningJoinEntryDecision, decideDiningJoinEntry } from '../DiningLogic'
import { DiningText } from '../DiningText'
import { DiningOrderJoinParam } from '../api'
import { AppButton, EmptyState, SkeletonBlock } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { getObjectParam, getNumberParam, getStringParam, pathStack } from '../../../utils/PathStackUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'

/** 日志 TAG */
const TAG: string = 'diningOrderJoin'

@Builder
export function DiningOrderJoinBuilder() {
  DiningOrderJoinPage()
}

@ComponentV2
struct DiningOrderJoinPage {
  private orderId: number = 0
  private token: string = ''
  /** 参数是否完整（orderId>0 且 token 非空；不完整不发起请求） */
  private paramValid: boolean = false

  @Local brief: DiningOrderBriefVo | null = null
  @Local isLoading: boolean = true
  /** join 提交中（防重） */
  @Local joining: boolean = false

  async aboutToAppear(): Promise<void> {
    // 参数双通道：深链注册表传 DiningOrderJoinParam；兜底 number + query 参数（getStringParam）
    const param = getObjectParam<DiningOrderJoinParam>(RouteNames.DINING_ORDER_JOIN)
    if (param !== null) {
      this.orderId = param.orderId
      this.token = param.token
    } else {
      this.orderId = getNumberParam(RouteNames.DINING_ORDER_JOIN) ?? 0
      this.token = getStringParam(RouteNames.DINING_ORDER_JOIN) ?? ''
    }
    this.paramValid = this.orderId > 0 && this.token.trim().length > 0
    if (!this.paramValid) {
      logger.warn(TAG, `加入入口参数不完整 orderId=${this.orderId} tokenEmpty=${this.token.length === 0}`)
      this.isLoading = false
      return
    }
    await this.loadBrief()
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

            Text(DiningText.dining_order_join_title)
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_PRIMARY'))

            Blank()
          }
          .width('100%')
          .height(56)
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
          .alignItems(VerticalAlign.Center)

          if (this.isLoading) {
            Column({ space: DesignDimens.CARD_SPACING }) {
              SkeletonBlock().height(120)
              SkeletonBlock().height(48)
            }
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_MD })
            .layoutWeight(1)
          } else if (!this.paramValid) {
            Column() {
              EmptyState({ message: DiningText.dining_order_join_param_invalid })
            }
            .width('100%')
            .layoutWeight(1)
          } else {
            this.BodyByDecision()
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

  /** 按决策四态渲染（brief 判重） */
  @Builder
  BodyByDecision() {
    Column() {
      if (this.decision() === DiningJoinEntryDecision.CONFIRM_JOIN) {
        this.ConfirmJoinBody()
      } else if (this.decision() === DiningJoinEntryDecision.ORDER_FINISHED) {
        Column() {
          EmptyState({ message: DiningText.dining_order_join_order_finished })
        }
        .width('100%')
        .layoutWeight(1)
      } else {
        Column() {
          EmptyState({ message: DiningText.dining_order_join_load_failed }) {
            Column() {
              AppButton({
                small: true,
                text: DiningText.dining_action_retry,
                click: (): void => {
                  this.retry()
                }
              })
            }
            .width('100%')
            .padding({ top: DesignDimens.SPACE_MD })
          }
        }
        .width('100%')
        .layoutWeight(1)
      }
    }
    .width('100%')
    .height('100%')
  }

  /** 非成员确认加入体：订单信息卡（标题/发起者/菜品数）+ 加入按钮 */
  @Builder
  ConfirmJoinBody() {
    Column() {
      Column({ space: DesignDimens.SPACE_SM }) {
        Text((this.brief?.title ?? '').length > 0 ? this.brief?.title ?? '' : DiningText.dining_order_join_unknown_title)
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .width('100%')

        Text(DiningText.joinCreator(this.brief?.creatorName ?? DiningText.dining_selected_none))
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .width('100%')

        Text(DiningText.joinRecipeCount(this.brief?.recipeCount ?? 0))
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .width('100%')

        Text('同学确认加入后即可在点单页勾选想吃的菜')
          .fontSize(DesignFontSize.CAPTION)
          .fontColor($r('app.color.TEXT_TERTIARY'))
          .width('100%')
      }
      .alignItems(HorizontalAlign.Start)
      .width('100%')
      .backgroundColor($r('app.color.SURFACE'))
      .borderRadius(DesignDimens.CARD_RADIUS)
      .borderWidth(1)
      .borderColor($r('app.color.OUTLINE'))
      .padding(DesignDimens.CARD_PADDING)
      .shadow({
        radius: DesignElevation.CARD_SHADOW_RADIUS,
        color: $r('app.color.SHADOW_COLOR_CARD'),
        offsetX: 0,
        offsetY: DesignElevation.CARD_SHADOW_OFFSET
      })

      Column() {
        AppButton({
          text: DiningText.dining_order_join_action,
          loading: this.joining,
          click: (): void => {
            this.doJoin()
          }
        })
      }
      .width('100%')
      .padding({
        top: DesignDimens.SECTION_SPACING,
        bottom: Math.max(safeBottomVp(), DesignDimens.SPACE_MD)
      })
    }
    .width('100%')
    .height('100%')
    .alignItems(HorizontalAlign.Start)
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_MD })
  }

  /** brief 判重决策（applyDetail 风格的集中派生） */
  private decision(): DiningJoinEntryDecision {
    return decideDiningJoinEntry(this.brief)
  }

  /** 拉取 brief（首载/重试共用） */
  private async loadBrief(): Promise<void> {
    try {
      const res = await getDiningOrderBrief(this.token.trim())
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.brief = res.data
        logger.debug(TAG, `brief 加载成功 orderId=${res.data.orderId} joined=${res.data.joined} status=${res.data.status}`)
        if (decideDiningJoinEntry(res.data) === DiningJoinEntryDecision.ENTER_DETAIL) {
          // 已是成员：直达点单页（replace：入口页不留返回栈）
          logger.debug(TAG, '已是成员，直达点单页')
          pathStack.replacePathByName(RouteNames.DINING_ORDER_DETAIL, res.data.orderId > 0 ? res.data.orderId : this.orderId)
        }
      } else {
        logger.warn(TAG, `brief 加载失败 code=${res.code} message=${res.message ?? ''}`)
        this.brief = null
        // 凭证无效（旧码/已重置）有专属文案：业务失败 message 已带具体语义，message 缺失时兜底
        showToastSafety(res.message, DiningText.dining_order_join_invalid_token)
      }
    } catch (err) {
      logger.error(TAG, 'brief 加载异常', err instanceof Error ? err.message : '')
      this.brief = null
      showToastSafety('', DiningText.dining_order_join_load_failed)
    }
  }

  /** 重试（错误态按钮） */
  private async retry(): Promise<void> {
    this.isLoading = true
    await this.loadBrief()
    this.isLoading = false
  }

  /** 加入订单：成功 replace 进点单页 */
  private async doJoin(): Promise<void> {
    if (this.joining || !this.paramValid) {
      return
    }
    this.joining = true
    try {
      const res = await joinDiningOrder(this.orderId, this.token.trim())
      if (res.code === BizCode.SUCCESS) {
        logger.info(TAG, `加入点单成功 orderId=${this.orderId}`)
        showToastSafety(DiningText.dining_order_join_success)
        pathStack.replacePathByName(RouteNames.DINING_ORDER_DETAIL, this.orderId)
      } else {
        logger.warn(TAG, `加入点单未生效 code=${res.code} message=${res.message ?? ''}`)
        showToastSafety(res.message, DiningText.dining_order_join_invalid_token)
      }
    } catch (err) {
      logger.error(TAG, '加入点单异常', err instanceof Error ? err.message : '')
      showToastSafety('', DiningText.dining_order_join_load_failed)
    }
    this.joining = false
  }
}


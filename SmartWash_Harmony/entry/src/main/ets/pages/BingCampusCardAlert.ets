import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight, MotionTiming } from "../constant/DesignSystem"
import { bindCampus, unBindCampus } from "../network/api/UserApi"
import { BaseResponse } from "../network/BaseResponse"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { getObjectParam, pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { logger } from "../utils/Logger"
import { bumpUserInfoRefresh } from "../utils/RefreshSignal"

@Builder
export function BingCampusCardAlertBuilder() {
  BingCampusCardAlert()
}

/**
 * 绑定/解绑校园卡弹窗（对齐安卓 AppInputDialog / AppConfirmDialog 皮肤）。
 * 形态保留鸿蒙对话页（F1 属产品决策），仅按新版设计系统重皮肤：
 * 设计令牌色 + 16dp 卡圆角 + 浮层阴影 + 22/600 标题 + 16sp 输入 + 48dp 分区按钮 + 入场动效。
 */
@Component
struct BingCampusCardAlert {
  @State userInfo: UserInfoVo | null = null
  // 绑定态判定统一为「campusCard 空串=未绑」（差异分析 02 §9.3，两端同语义防漂移）
  @State isBingFlag: boolean = false
  @State campusCard: string = ""
  @State errorMessage: string = ""
  @State isFocused: boolean = false
  // 请求进行中标记：确认按钮防重（对齐安卓 bindCampusState Loading 防重）
  @State submitting: boolean = false
  // 入场动效标记（对齐安卓 dialogEnterMotion：scale 0.92→1 + fade 0→1 弹簧一次性进场）
  @State entered: boolean = false
  private inputController = new TextInputController()

  aboutToAppear(): void {
    // 安全取参（评审 #31）：参数缺失/非对象时返回 null，isBingFlag 兜底为绑定态
    this.userInfo = getObjectParam<UserInfoVo>(RouteNames.BING_CAMPUS_CARD_ALERT)
    this.isBingFlag = (this.userInfo?.campusCard ?? "") === ""
    logger.debug('bingCampusCardAlert', `校园卡弹窗挂载，操作=${this.isBingFlag ? "绑定" : "解绑"}`)
    // 首帧后再触发，保证初始 0.92/0 先上屏，过渡才可见（退出动画窗口层不支持，两端一致不做）
    setTimeout(() => {
      animateTo(MotionTiming.DIALOG_ENTER, () => {
        this.entered = true
      })
    }, 16)
  }

  aboutToDisappear(): void {
    // 自增 tick（跨页刷新约定，同 Recharge）：绑定/解绑成功后刷新「我的」页校园卡状态行。
    // 真机实测（T4）：DIALOG 模式 NavDestination 的 pushPathByName onPop 回调不触发，
    // 入口处的 refreshUserInfo 永远不执行，导致绑定成功后行内仍显示「未绑定」。
    bumpUserInfoRefresh()
  }

  /** 输入框描边色：错误 > 聚焦 > 常规（对齐安卓 OutlinedTextField 三态） */
  private inputBorderColor(): number {
    if (this.errorMessage !== "") {
      return DesignColor.ERROR
    }
    return this.isFocused ? DesignColor.PRIMARY : DesignColor.OUTLINE
  }

  /** 确认绑定/解绑（防重：请求进行中忽略后续点击） */
  async confirmAction() {
    if (this.submitting) {
      logger.debug('bingCampusCardAlert', '请求进行中，忽略重复点击')
      return
    }
    if (this.isBingFlag && this.campusCard === "") {
      this.errorMessage = "校园卡号不能为空"
      return
    }
    this.errorMessage = ""
    let res: BaseResponse<boolean> | null = null
    this.submitting = true
    try {
      if (this.isBingFlag) {
        res = await bindCampus(this.campusCard)
      } else {
        res = await unBindCampus()
      }
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：兜住提示，避免 res 未赋值时解引用崩溃
      showToastSafety("网络异常，请稍后重试")
      return
    } finally {
      this.submitting = false
    }
    // 后端 Result.ok(boolean) 直传：data=false（更新失败）必须按失败处理，
    // 不能只看 code=200 就弹「绑定成功」（对齐 AddSchool 对 getUserByStudentId 的判法）
    if (res !== null && res.code === HttpStatusCode.Ok && res.data === true) {
      logger.info('bingCampusCardAlert', `校园卡${this.isBingFlag ? "绑定" : "解绑"}成功`)
      this.inputController.stopEditing()
      pathStack.pop()
      showToastSafety(this.isBingFlag ? "绑定成功" : "解绑成功")
    } else {
      showToastSafety(res?.message ?? "", this.isBingFlag ? "绑定失败，请重试" : "解绑失败，请重试")
      logger.warn('bingCampusCardAlert', `校园卡${this.isBingFlag ? "绑定" : "解绑"}被拒绝 code=${res.code} message=${res.message ?? ""}`)
    }
  }

  build() {
    NavDestination() {
      Stack({ alignContent: Alignment.Center }) {
        Column() {
          // 标题（对齐安卓 AppComponents.kt:961-965 headlineMedium 22/600）
          Text(`${this.isBingFlag ? "绑定校园卡" : "解绑校园卡"}`)
            .fontSize(DesignFontSize.STATUS_LARGE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .margin({ bottom: 16 })

          if (this.isBingFlag) {
            // 输入框（对齐安卓 OutlinedTextField r14 + 聚焦 primary 描边 + 16sp）
            TextInput({ text: this.campusCard, placeholder: "请输入校园卡号", controller: this.inputController })
              .width("100%")
              .height(48)
              .type(InputType.Number)
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .placeholderColor(DesignColor.TEXT_SECONDARY)
              .placeholderFont({ size: DesignFontSize.BODY })
              .backgroundColor(Color.Transparent)
              .borderRadius(DesignDimens.INPUT_RADIUS)
              .border({
                width: 1,
                color: this.inputBorderColor(),
                radius: DesignDimens.INPUT_RADIUS
              })
              .onFocus(() => {
                this.isFocused = true
              })
              .onBlur(() => {
                this.isFocused = false
              })
              .onChange((it: string) => {
                this.campusCard = it
                if (this.errorMessage !== "") {
                  this.errorMessage = ""
                }
              })

            // 错误行（对齐安卓 supportingText：ERROR 色，仅提交时展示）
            if (this.errorMessage !== "") {
              Text(this.errorMessage)
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(DesignColor.ERROR)
                .alignSelf(ItemAlign.Start)
                .margin({ top: 4 })
            }
          } else {
            Text("确定要解绑当前绑定的校园卡吗？")
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.TEXT_SECONDARY)
          }

          // 按钮行（对齐安卓 DialogAction：48dp 热区，左右贴合边缘）
          Row() {
            Row() {
              Text("取消")
                .fontSize(DesignFontSize.BODY)
                .fontColor(DesignColor.TEXT_SECONDARY)
            }
            .layoutWeight(1)
            .height(48)
            .justifyContent(FlexAlign.Center)
            .alignItems(VerticalAlign.Center)
            .onClick(() => {
              pathStack.pop()
            })

            Divider()
              .vertical(true)
              .color(DesignColor.DIVIDER)
              .strokeWidth(0.5)
              .height(48)

            Row() {
              Text(this.isBingFlag ? "确认" : "确认解绑")
                .fontSize(DesignFontSize.BODY)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor(this.isBingFlag ? DesignColor.PRIMARY : DesignColor.DANGER_INK)
            }
            .layoutWeight(1)
            .height(48)
            .justifyContent(FlexAlign.Center)
            .alignItems(VerticalAlign.Center)
            .onClick(() => {
              this.confirmAction()
            })
          }
          .width("100%")
          .margin({ top: 20 })
        }
        .width("80%")
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 20, bottom: 20 })
        .backgroundColor(DesignColor.SURFACE)
        .borderRadius(DesignDimens.CARD_RADIUS)
        // 浮层档阴影（§2.4 CSS 0 10px 24px rgba(20,40,30,.10)）
        .shadow({
          radius: DesignElevation.FLOAT_SHADOW_RADIUS,
          color: DesignColor.SHADOW_COLOR_FLOAT,
          offsetX: 0,
          offsetY: DesignElevation.FLOAT_SHADOW_OFFSET
        })
        .scale({ x: this.entered ? 1 : 0.92, y: this.entered ? 1 : 0.92 })
        .opacity(this.entered ? 1 : 0)
        .onClick(() => {
          // 消费点击，避免冒泡到遮罩触发关闭
        })
      }
      .height("100%")
      .width("100%")
    }
    .onClick(() => {
      pathStack.pop()
    })
    .backgroundColor('rgba(0,0,0,0.5)')
    .hideTitleBar(true)
    .mode(NavDestinationMode.DIALOG)
  }
}

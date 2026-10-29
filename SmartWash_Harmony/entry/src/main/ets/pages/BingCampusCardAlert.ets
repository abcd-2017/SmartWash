import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
import { CommonConstants } from "../constant/CommonConstants"
import { bindCampus, unBindCampus } from "../network/api/UserApi"
import { BaseResponse } from "../network/BaseResponse"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { getObjectParam, pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"

@Builder
export function BingCampusCardAlertBuilder() {
  BingCampusCardAlert()
}

@Component
struct BingCampusCardAlert {
  @State userInfo: UserInfoVo | null = null
  // 绑定态判定统一为「campusCard 空串=未绑」（差异分析 02 §9.3，两端同语义防漂移）
  @State isBingFlag: boolean = false
  @State campusCard: string | null = ""
  @State showErrorMessage: string | null = null
  // 请求进行中标记：确认按钮防重（对齐安卓 bindCampusState Loading 防重）
  @State submitting: boolean = false
  private inputController = new TextInputController()

  aboutToAppear(): void {
    // 安全取参（评审 #31）：参数缺失/非对象时返回 null，isBingFlag 兜底为绑定态
    this.userInfo = getObjectParam<UserInfoVo>(RouteNames.BING_CAMPUS_CARD_ALERT)
    this.isBingFlag = (this.userInfo?.campusCard ?? "") == ""
  }

  /** 确认绑定/解绑（防重：请求进行中忽略后续点击） */
  async confirmAction() {
    if (this.submitting) {
      return
    }
    let res: BaseResponse<boolean>
    if (this.isBingFlag) {
      if (this.campusCard == null || this.campusCard == "") {
        this.showErrorMessage = "校园卡号不能为空"
        return
      } else {
        this.showErrorMessage = ""
        this.submitting = true
        try {
          res = await bindCampus(this.campusCard)
        } finally {
          this.submitting = false
        }
      }
    } else {
      this.submitting = true
      try {
        res = await unBindCampus()
      } finally {
        this.submitting = false
      }
    }
    if (res.code == HttpStatusCode.Ok) {
      this.inputController.stopEditing()
      pathStack.pop()
      showToastSafety(this.isBingFlag ? "绑定成功" : "解绑成功")
    } else {
      showToastSafety(res.message, "操作失败")
    }
  }

  build() {
    NavDestination() {
      Stack({ alignContent: Alignment.Center }) {
        Column() {
          Row() {
            Text(`${this.isBingFlag ? "绑定校园卡" : "解绑校园卡"}`)
              .fontSize(18)
              .fontColor($r('sys.color.font_primary'))
              .fontWeight(FontWeight.Bold)
              .margin({ bottom: 18 })
          }.width("100%")
          .justifyContent(FlexAlign.Start)

          Row() {
            if (this.isBingFlag) {
              // 数字键盘限定（对齐安卓 KeyboardType.Number，卡号仅数字）
              TextInput({ text: this.campusCard, placeholder: "请输入校园卡号", controller: this.inputController })
                .onChange((it) => {
                  this.campusCard = it
                })
                .type(InputType.Number)
                .fontSize(14)
                .width("90%")
                .showUnderline(true)
                .showError(this.showErrorMessage)
            } else {
              Text("确定要解绑当前绑定的校园卡吗？")
                .fontSize(15)
            }
          }

          Row() {
            Text("取消").onClick(() => {
              pathStack.pop()
            }).fontColor($r('sys.color.brand'))
              .margin({ right: 20 })
              .fontSize(16)

            Text(this.submitting ? "处理中..." : `确认${this.isBingFlag ? "绑定" : "解绑"}`).onClick(() => {
              this.confirmAction()
            }).fontColor($r('sys.color.brand')).fontSize(16)
          }.width("100%")
          .margin({ top: 28 })
          .justifyContent(FlexAlign.End)
        }
        .justifyContent(FlexAlign.SpaceBetween)
        .backgroundColor($r('sys.color.comp_background_primary'))
        .borderRadius(CommonConstants.BORDER_RADIUS)
        .padding("22")
        .width('80%')
        .onClick(() => {
        })
      }.height("100%").width('100%')
    }
    .onClick(() => {
      pathStack.pop()
    })
    .backgroundColor('rgba(0,0,0,0.5)')
    .hideTitleBar(true)
    .mode(NavDestinationMode.DIALOG)
  }
}

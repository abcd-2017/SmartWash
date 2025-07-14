import { HttpStatusCode } from "@ohos/axios"
import { CommonConstants } from "../constant/CommonConstants"
import { bindCampus, unBindCampus } from "../network/api/UserApi"
import { BaseResponse } from "../network/BaseResponse"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { pathStack } from "../utils/PathStackUtil"
import { promptAction } from "@kit.ArkUI"

@Builder
export function BingCampusCardAlertBuilder() {
  BingCampusCardAlert()
}

@Component
struct BingCampusCardAlert {
  @State userInfo: UserInfoVo | null = null
  @State isBingFlag: boolean = false
  @State campusCard: string | null = ""
  @State showErrorMessage: string | null = null
  private inputController = new TextInputController()

  aboutToAppear(): void {
    this.userInfo = pathStack.getParamByName("BingCampusCardAlert")[0] as UserInfoVo
    this.isBingFlag = (this.userInfo?.campusCard ?? "") == ""
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
              TextInput({ text: this.campusCard, placeholder: "请输入校园卡号", controller: this.inputController })
                .onChange((it) => {
                  this.campusCard = it
                })
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
              pathStack.pop(11, false)
            }).fontColor($r('sys.color.brand'))
              .margin({ right: 20 })
              .fontSize(16)

            Text(`确认${this.isBingFlag ? "绑定" : "解绑"}`).onClick(async () => {
              let res: BaseResponse<boolean>
              if (this.isBingFlag) {
                if (this.campusCard == null || this.campusCard == "") {
                  this.showErrorMessage = "校园卡号不能为空"
                  return
                } else {
                  this.showErrorMessage = ""
                  res = await bindCampus(this.campusCard)
                }
              } else {
                res = await unBindCampus()
              }
              if (res.code == HttpStatusCode.Ok) {
                this.inputController.stopEditing()
                pathStack.pop(11, false)
                promptAction.showToast({ message: this.isBingFlag ? "绑定成功" : "解绑成功" })
              } else {
                promptAction.showToast({ message: res.message })
              }
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
      pathStack.pop(11, false)
    })
    .backgroundColor('rgba(0,0,0,0.5)')
    .hideTitleBar(true)
    .mode(NavDestinationMode.DIALOG)
  }
}
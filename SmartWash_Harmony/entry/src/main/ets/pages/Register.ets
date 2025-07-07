import { pathStack } from "../utils/PathStackUtil"
import { faceDetector } from "@kit.CoreVisionKit"
import { getCaptcha, register } from "../network/api/UserApi"
import { logger } from "../utils/Logger"
import { HttpStatusCode } from "@ohos/axios"
import { promptAction } from "@kit.ArkUI"
import { setValue } from "../utils/StoreageUtil"
import { CommonConstants } from "../constant/CommonConstants"

@Builder
export function RegisterBuilder() {
  Register()
}

@Entry
@Component
struct Register {
  @State phoneNumber: string | null = null
  @State password: string | null = null
  @State verificationCode: string | null = null
  @State checkPhoneNumber: boolean = false
  @State checkPassword: boolean = false
  @State checkCode: boolean = false

  check() {
    this.checkPhone()
    this.checkCode = this.verificationCode?.length != 6
    let len = this.password?.length ?? 0;
    this.checkPassword = len < 6 && len > 16
  }

  checkPhone() {
    let value = this.phoneNumber ?? ""
    this.checkPhoneNumber = !/^1[3-9]\d{9}$/.test(value)
  }

  build() {
    NavDestination() {
      Column({ space: 18 }) {
        Row() {
          Image($r('app.media.logo'))
            .width(160)
        }.height(100)
        .justifyContent(FlexAlign.Center)

        Text("智能洗衣")
          .fontSize(24)
          .fontWeight(700)

        Text("让生活更轻松")
          .fontSize(14)
          .fontWeight(300)
          .margin({ bottom: 18 })

        Row() {
          Column({ space: 20 }) {
            //手机号输入框
            Column() {
              TextInput({ text: $$this.phoneNumber, placeholder: "手机号" })
                .backgroundColor($r('sys.color.comp_background_tertiary'))
                .type(InputType.PhoneNumber)
                .maxLength(11)
                .onChange(it => {
                  this.phoneNumber = it
                })
              if (this.checkPhoneNumber) {
                Text("手机号非法")
                  .fontColor($r('sys.color.warning'))
                  .fontSize(14)
                  .margin(12)
              }
            }.alignItems(HorizontalAlign.Start)

            //验证码输入框
            Column() {
              Row() {
                TextInput({ text: $$this.verificationCode, placeholder: "验证码" })
                  .backgroundColor($r('sys.color.comp_background_tertiary'))
                  .type(InputType.Number)
                  .maxLength(6)
                  .width("60%")
                  .onChange(it => this.verificationCode = it)
                Button("获取验证码").onClick(async () => {
                  this.checkPhone()
                  if (!this.checkPhoneNumber) {
                    let res = await getCaptcha(this.phoneNumber ?? "")
                    if (res.code == HttpStatusCode.Ok) {
                      this.verificationCode = res.data
                    } else {
                      promptAction.showToast({ message: res.message })
                    }
                  }
                })
              }.width("100%").justifyContent(FlexAlign.SpaceBetween)

              if (this.checkCode) {
                Text("验证码不能为空")
                  .fontColor($r('sys.color.warning'))
                  .fontSize(14)
                  .margin(12)
              }
            }.alignItems(HorizontalAlign.Start)

            //密码输入框
            Column() {
              TextInput({ text: $$this.password, placeholder: "密码" })
                .backgroundColor($r('sys.color.comp_background_tertiary'))
                .type(InputType.Password)
                .maxLength(16).onChange(it => this.password = it)
              if (this.checkPassword) {
                Text("密码必须为6-16位")
                  .fontColor($r('sys.color.warning'))
                  .fontSize(14)
                  .margin(12)
              }
            }.alignItems(HorizontalAlign.Start)
          }
        }
        .width("85%")
        .borderRadius(CommonConstants.BORDER_RADIUS)
        .padding(16)
        .backgroundColor($r('sys.color.comp_background_primary'))
        .shadow(ShadowStyle.OUTER_DEFAULT_SM)

        RowSplit()

        //注册按钮
        Button("注册").onClick(async (event: ClickEvent) => {
          this.check()
          if (this.checkPhoneNumber || this.checkPassword || this.checkCode) {
            return
          }
          let res = await register({
            phoneNumber: this.phoneNumber,
            code: this.verificationCode,
            password: this.password
          })
          logger.info("info", `${res}`)
          if (res.code == HttpStatusCode.Ok) {
            promptAction.showToast({ message: "注册成功" })
            setValue(CommonConstants.USER_TOKEN, `${res.data}`)
            pathStack.replacePathByName("AddSchool", null)
          } else {
            promptAction.showToast({ message: res.message })
          }
        }).width("85%")

        Text("已有账号？立即登录")
          .fontSize(14)
          .onClick(() => {
            pathStack.pop()
          })
      }.width("100%")
      .height("100%")
      .backgroundColor($r('sys.color.background_primary'))
    }.hideTitleBar(true)
  }
}
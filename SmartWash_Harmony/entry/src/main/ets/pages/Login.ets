import { HttpStatusCode } from "@ohos/axios"
import { getUserSchoolId, login } from "../network/api/UserApi"
import { pathStack } from "../utils/PathStackUtil"
import { promptAction } from "@kit.ArkUI"
import { setValue } from "../utils/StoreageUtil"
import { CommonConstants } from "../constant/CommonConstants"
import { logger } from "../utils/Logger"

@Builder
export function LoginBuilder() {
  Login()
}

@Entry
@Component
struct Login {
  @State phoneNumber: string | null = null
  @State password: string | null = null
  @State checkPhoneNumber: boolean = false
  @State checkPassword: boolean = false

  check() {
    let value = this.phoneNumber ?? ""
    this.checkPhoneNumber = !/^1[3-9]\d{9}$/.test(value)
    let len = this.password?.length ?? 0;
    this.checkPassword = len < 6 && len > 16
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
            Column() {
              TextInput({ text: $$this.phoneNumber, placeholder: "手机号" })
                .backgroundColor($r('sys.color.comp_background_tertiary'))
                .type(InputType.PhoneNumber)
                .maxLength(11)
                .onChange(it => this.phoneNumber = it)
              if (this.checkPhoneNumber) {
                Text("手机号必须为11位")
                  .fontColor($r('sys.color.warning'))
                  .fontSize(14)
                  .margin(12)
              }
            }.alignItems(HorizontalAlign.Start)

            Column() {
              TextInput({ text: $$this.password, placeholder: "密码" })
                .backgroundColor($r('sys.color.comp_background_tertiary'))
                .type(InputType.Password)
                .maxLength(16).onChange(it => this.password = it)
              if (this.checkPhoneNumber) {
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

        Button("登录").onClick(async (event: ClickEvent) => {
          this.check()
          if (this.checkPhoneNumber || this.checkPassword) {
            return
          }
          let res = await login({
            phoneNumber: this.phoneNumber,
            password: this.password
          })
          if (res.code == HttpStatusCode.Ok) {
            setValue(CommonConstants.USER_TOKEN, `${res.data}`)
            let getIdRes = await getUserSchoolId()
            if (getIdRes.code == HttpStatusCode.Ok) {
              if (getIdRes.data == null) {
                pathStack.replacePathByName("AddSchool", null)
                return
              } else {
                if (pathStack.size() > 0) {
                  pathStack.clear()
                } else {
                  AppStorage.setOrCreate("userLoginFlag", true)
                }
              }
            }
          } else {
            promptAction.showToast({ message: res.message })
          }
        }).width("85%")

        Text("没有账号？立即注册")
          .fontSize(14)
          .onClick(() => {
            pathStack.replacePathByName("Register", null)
          })
      }.width("100%")
      .height("100%")
      .backgroundColor($r('sys.color.background_primary'))
    }.hideTitleBar(true)
    .height("100%")
  }
}
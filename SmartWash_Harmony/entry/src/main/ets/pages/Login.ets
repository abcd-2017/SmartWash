import { HttpStatusCode } from "@ohos/axios"
import { getUserSchoolId, login } from "../network/api/UserApi"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { setValue } from "../utils/StorageUtil"
import { CommonConstants } from "../constant/CommonConstants"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"

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
  @State showPassword: boolean = false
  // 登录请求进行中标记：按钮展示 loading 并拦截连点，防止重复提交（评审 #12）
  @State loginLoading: boolean = false
  private controller = new TextInputController()

  check() {
    let value = this.phoneNumber ?? ""
    this.checkPhoneNumber = !/^1[3-9]\d{9}$/.test(value)
    let len = this.password?.length ?? 0;
    this.checkPassword = len < 6 || len > 16
  }

  build() {
    NavDestination() {
      // 全屏渐变背景
      Column() {
        // 占位：将内容推到垂直居中位置
        Blank()

        // 品牌标识 — 外圈光环 + 内圈图标
        Column() {
          // 内圈 — 图标
          Column() {
            SymbolGlyph($r('sys.symbol.wave_3_right'))
              .fontSize(36)
              .fontColor([Color.White])
          }
          .width(72)
          .height(72)
          .borderRadius(36)
          .backgroundColor(DesignColor.GLASS_BG)
          .border({
            width: 1,
            color: DesignColor.GLASS_BORDER,
            style: BorderStyle.Solid
          })
          .justifyContent(FlexAlign.Center)
          .alignItems(HorizontalAlign.Center)
        }
        // 外圈 — 淡光环
        .width(88)
        .height(88)
        .borderRadius(44)
        .backgroundColor(DesignColor.GLASS_BG_SUBTLE)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)

        // 品牌文字
        Text("智能洗衣")
          .fontSize(DesignFontSize.PAGE_TITLE)
          .fontWeight(DesignFontWeight.BOLD)
          .fontColor(Color.White)
          .margin({ top: 20 })

        Text("让生活更轻松")
          .fontSize(DesignFontSize.BODY)
          .fontWeight(DesignFontWeight.NORMAL)
          .fontColor(DesignColor.GLASS_TEXT_DISABLED)
          .margin({ top: 6 })

        // 毛玻璃输入卡片
        Column() {
          // 手机号输入框
          TextInput({ placeholder: "手机号" })
            .type(InputType.PhoneNumber)
            .maxLength(11)
            .fontSize(DesignFontSize.CARD_TITLE)
            .fontColor(Color.White)
            .placeholderColor(DesignColor.GLASS_TEXT_HINT)
            .backgroundColor(Color.Transparent)
            .width("100%")
            .onChange((it: string) => this.phoneNumber = it)
          if (this.checkPhoneNumber) {
            Text("手机号必须为11位")
              .fontColor(DesignColor.WARNING)
              .fontSize(DesignFontSize.CAPTION)
              .margin({ top: 8 })
          }

          // 分隔线
          Divider()
            .color(DesignColor.GLASS_BG)
            .strokeWidth(0.5)
            .margin({ top: 12, bottom: 12 })

          // 密码输入框
          Row() {
            TextInput({ placeholder: "密码", controller: this.controller })
              .type(this.showPassword ? InputType.Normal : InputType.Password)
              .maxLength(16)
              .fontSize(DesignFontSize.CARD_TITLE)
              .fontColor(Color.White)
              .placeholderColor(DesignColor.GLASS_TEXT_HINT)
              .backgroundColor(Color.Transparent)
              .layoutWeight(1)
              .onChange((it: string) => this.password = it)

            // 密码可见性切换按钮
            Text(this.showPassword ? "Hide" : "Show")
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.GLASS_TEXT_HINT)
              .padding({ left: 8 })
              .onClick(() => {
                this.showPassword = !this.showPassword
              })
          }
          .width("100%")

          if (this.checkPassword) {
            Text("密码必须为6-16位")
              .fontColor(DesignColor.WARNING)
              .fontSize(DesignFontSize.CAPTION)
              .margin({ top: 8 })
          }
        }
        .width("100%")
        .borderRadius(28)
        .backgroundColor(DesignColor.GLASS_BG_SUBTLE)
        .border({
          width: 1,
          color: DesignColor.GLASS_BORDER_SUBTLE,
          style: BorderStyle.Solid
        })
        .padding({ left: 20, right: 20, top: 20, bottom: 20 })
        .margin({ top: 40, left: 24, right: 24 })

        // 登录按钮
        Text(this.loginLoading ? "登录中..." : "登录")
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(Color.White)
          .textAlign(TextAlign.Center)
          .width("100%")
          .height(50)
          .borderRadius(16)
          .backgroundColor(DesignColor.GLASS_INPUT)
          .margin({ top: 20, left: 40, right: 40 })
          .onClick(async (event: ClickEvent) => {
            // 防连点：请求进行中直接忽略
            if (this.loginLoading) {
              return
            }
            this.check()
            if (this.checkPhoneNumber || this.checkPassword) {
              return
            }
            this.loginLoading = true
            try {
              let res = await login({
                phoneNumber: this.phoneNumber,
                password: this.password
              })
              if (res.code === HttpStatusCode.Ok) {
                // 后端登录信封 data 为 {token, role} 对象（评审 #1 适配），取 data.token 持久化
                const token = res.data?.token ?? ""
                if (token === "") {
                  showToastSafety(res.message, "登录失败，请稍后重试")
                  return
                }
                // await 确保写入完成后，后续 getUserSchoolId 等请求才能带上新 token
                await setValue(CommonConstants.USER_TOKEN, token)
                // role 存入 AppStorage 备用（普通用户固定 "user"）
                AppStorage.setOrCreate("userRole", res.data?.role ?? "user")
                let getIdRes = await getUserSchoolId()
                if (getIdRes.code === HttpStatusCode.Ok) {
                  this.controller.stopEditing()
                  if (getIdRes.data == null) {
                    pathStack.replacePathByName("AddSchool", null)
                    return
                  } else {
                    if (pathStack.size() > 0) {
                      pathStack.clear()
                    }
                    AppStorage.setOrCreate("userLoginFlag", true)
                  }
                } else {
                  showToastSafety(getIdRes.message, "获取学校信息失败，请重新登录")
                }
              } else {
                showToastSafety(res.message, "登录失败，请稍后重试")
              }
            } finally {
              this.loginLoading = false
            }
          })

        // 占位：将注册入口推到底部
        Blank()

        // 注册入口
        Row() {
          Text("没有账号？")
            .fontSize(DesignFontSize.BODY)
            .fontColor(DesignColor.GLASS_TEXT_HINT)
          Text("立即注册")
            .fontSize(DesignFontSize.BODY)
            .fontColor(Color.White)
        }
        .margin({ bottom: 24 })
        .onClick(() => {
          pathStack.replacePathByName("Register", null)
        })
      }
      .width("100%")
      .height("100%")
      .linearGradient({
        direction: GradientDirection.Bottom,
        colors: [[DesignColor.AUTH_GRADIENT_TOP, 0], [DesignColor.AUTH_GRADIENT_BOTTOM, 1]]
      })
    }
    .hideTitleBar(true)
    .height("100%")
  }
}

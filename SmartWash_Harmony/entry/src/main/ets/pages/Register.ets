import { pathStack } from "../utils/PathStackUtil"
import { getCaptcha, register } from "../network/api/UserApi"
import { logger } from "../utils/Logger"
import { HttpStatusCode } from "@ohos/axios"
import { promptAction } from "@kit.ArkUI"
import { setValue } from "../utils/StorageUtil"
import { CommonConstants } from "../constant/CommonConstants"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"

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
  @State countDown: number = 0
  private timer: number = -1

  startCountDown() {
    this.countDown = 60
    this.timer = setInterval(() => {
      this.countDown--
      if (this.countDown <= 0) {
        clearInterval(this.timer)
        this.timer = -1
        this.countDown = 0
      }
    }, 1000)
  }

  check() {
    this.checkPhone()
    this.checkCode = this.verificationCode?.length != 6
    let len = this.password?.length ?? 0;
    this.checkPassword = len < 6 || len > 16
  }

  checkPhone() {
    let value = this.phoneNumber ?? ""
    this.checkPhoneNumber = !/^1[3-9]\d{9}$/.test(value)
  }

  build() {
    NavDestination() {
      Stack({ alignContent: Alignment.Top }) {
        // 全屏渐变背景
        Column()
          .width("100%")
          .height("100%")
          .linearGradient({
            direction: GradientDirection.Bottom,
            colors: [
              [DesignColor.AUTH_GRADIENT_TOP, 0.0],
              [DesignColor.AUTH_GRADIENT_BOTTOM, 1.0]
            ]
          })

        // 主内容区域
        Column() {
          // 顶部品牌标识区域
          Column({ space: 8 }) {
            // 品牌标识 — 外圈光环 + 内圈图标
            Column() {
              // 内圈 — 图标
              Column() {
                Text('✨')
                  .fontSize(DesignFontSize.PAGE_TITLE)
                  .fontColor(Color.White)
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
            .margin({ top: 20 })

            // 品牌文字
            Text("创建账号")
              .fontSize(DesignFontSize.PAGE_TITLE)
              .fontWeight(DesignFontWeight.BOLD)
              .fontColor(Color.White)
              .margin({ top: 20 })

            Text("开启洗衣新旅程")
              .fontSize(DesignFontSize.BODY)
              .fontWeight(DesignFontWeight.NORMAL)
              .fontColor(DesignColor.GLASS_TEXT_DISABLED)
              .margin({ top: 6 })

            // 进度条
            Row({ space: 8 }) {
              // 激活段 — 白色渐变
              Column()
                .width(32)
                .height(6)
                .borderRadius(3)
                .linearGradient({
                  direction: GradientDirection.Right,
                  colors: [[Color.White, 0.0], [0xCCFFFFFF, 1.0]]
                })

              // 未激活段
              Column()
                .width(32)
                .height(6)
                .borderRadius(3)
                .backgroundColor(DesignColor.GLASS_INPUT)

              Column()
                .width(32)
                .height(6)
                .borderRadius(3)
                .backgroundColor(DesignColor.GLASS_INPUT)
            }
            .margin({ top: 24 })
          }
          .width("100%")
          .alignItems(HorizontalAlign.Center)

          // 毛玻璃输入卡片
          Column({ space: 0 }) {
            // 手机号输入框
            TextInput({ text: $$this.phoneNumber, placeholder: "请输入手机号" })
              .type(InputType.PhoneNumber)
              .maxLength(11)
              .width("100%")
              .height(50)
              .fontSize(DesignFontSize.BODY)
              .fontColor(Color.White)
              .placeholderColor(DesignColor.GLASS_TEXT_HINT)
              .backgroundColor(Color.Transparent)
              .onChange(it => {
                this.phoneNumber = it
              })

            if (this.checkPhoneNumber) {
              Text("手机号非法")
                .fontColor(DesignColor.ERROR_LIGHT)
                .fontSize(DesignFontSize.CAPTION)
                .margin({ top: 4 })
            }

            // 分隔线
            Divider()
              .color(DesignColor.GLASS_BORDER_SUBTLE)
              .strokeWidth(0.5)
              .margin({ top: 12, bottom: 12 })

            // 验证码输入框 + 获取验证码按钮
            Row({ space: 12 }) {
              TextInput({ text: $$this.verificationCode, placeholder: "请输入验证码" })
                .type(InputType.Number)
                .maxLength(6)
                .layoutWeight(1)
                .height(50)
                .fontSize(DesignFontSize.BODY)
                .fontColor(Color.White)
                .placeholderColor(DesignColor.GLASS_TEXT_HINT)
                .backgroundColor(Color.Transparent)
                .onChange(it => this.verificationCode = it)

              Button(this.countDown > 0 ? `${this.countDown}s` : "获取验证码")
                .height(36)
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(this.countDown > 0 ? DesignColor.GLASS_TEXT_SECONDARY : Color.White)
                .backgroundColor(DesignColor.GLASS_INPUT)
                .borderRadius(8)
                .enabled(this.countDown === 0)
                .onClick(async () => {
                  this.checkPhone()
                  if (!this.checkPhoneNumber) {
                    this.startCountDown()
                    let res = await getCaptcha(this.phoneNumber ?? "")
                    if (res.code == HttpStatusCode.Ok) {
                      promptAction.showToast({ message: res.data ?? "验证码已发送" })
                    } else {
                      promptAction.showToast({ message: res.message })
                      clearInterval(this.timer)
                      this.timer = -1
                      this.countDown = 0
                    }
                  }
                })
            }
            .width("100%")

            if (this.checkCode) {
              Text("验证码不能为空")
                .fontColor(DesignColor.ERROR_LIGHT)
                .fontSize(DesignFontSize.CAPTION)
                .margin({ top: 4 })
            }

            // 分隔线
            Divider()
              .color(DesignColor.GLASS_BORDER_SUBTLE)
              .strokeWidth(0.5)
              .margin({ top: 12, bottom: 12 })

            // 密码输入框
            TextInput({ text: $$this.password, placeholder: "请输入密码（6-16位）" })
              .type(InputType.Password)
              .maxLength(16)
              .width("100%")
              .height(50)
              .fontSize(DesignFontSize.BODY)
              .fontColor(Color.White)
              .placeholderColor(DesignColor.GLASS_TEXT_HINT)
              .backgroundColor(Color.Transparent)
              .onChange(it => this.password = it)

            if (this.checkPassword) {
              Text("密码必须为6-16位")
                .fontColor(DesignColor.ERROR_LIGHT)
                .fontSize(DesignFontSize.CAPTION)
                .margin({ top: 4 })
            }
          }
          .width("85%")
          .padding({ vertical: 12 })
          .backgroundColor(DesignColor.GLASS_BG)
          .borderRadius(28)
          .border({
            width: 1,
            color: DesignColor.GLASS_BORDER
          })
          .margin({ top: 40 })

          Spacer()

          // 注册按钮
          Button("注册")
            .width("85%")
            .height(50)
            .fontSize(DesignFontSize.SECTION_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(Color.White)
            .backgroundColor(DesignColor.GLASS_INPUT)
            .borderRadius(16)
            .onClick(async (event: ClickEvent) => {
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
            })

          // 登录入口
          Row() {
            Text("已有账号？")
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.GLASS_TEXT_SECONDARY)
            Text("立即登录")
              .fontSize(DesignFontSize.BODY)
              .fontColor(Color.White)
              .fontWeight(DesignFontWeight.MEDIUM)
          }
          .margin({ top: 16, bottom: 40 })
          .onClick(() => {
            pathStack.pop()
          })
        }
        .width("100%")
        .height("100%")
        .alignItems(HorizontalAlign.Center)
      }
      .width("100%")
      .height("100%")
    }
    .hideTitleBar(true)
    .height("100%")
  }
}

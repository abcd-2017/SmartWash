import { getCaptcha, resetPassword } from "../network/api/UserApi"
import { ResetPasswordForm } from "../network/entity/ResetPasswordForm"
import { HttpStatusCode } from "@ohos/axios"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { Debouncer } from "../utils/DebounceUtil"
import { logger } from "../utils/Logger"
import { DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import { ThemeRefresh } from '../view/common/ThemeRefresh'
import { resColor } from '../utils/ResourceColor'
import { resString } from '../utils/ResourceString'

// ================= 页面级设计常量（对齐认证页玻璃风格，与 Login/Register 同源，DesignSystem.ets 暂无对应 token）=================
const BUTTON_FONT_SIZE: number = 15
const BUTTON_LETTER_SPACING: number = 1
// 玻璃卡底白 8%（对齐 Login/Register GLASS_CARD_BG）
const GLASS_CARD_BG: number = 0x14FFFFFF
// 卡内分隔线白 12%
const GLASS_DIVIDER: number = 0x1FFFFFFF
// 占位符白 45%
const GLASS_PLACEHOLDER: number = 0x73FFFFFF
// 前缀图标白 50%
const GLASS_PREFIX: number = 0x80FFFFFF
// 尾部图标白 92%
const GLASS_ICON_ACTIVE: number = 0xEBFFFFFF
// 发送验证码按钮禁用态：描边白 30%×0.55、文字白 92%×0.55（对齐 Register 发送按钮）
const GLASS_SEND_BORDER_DISABLED: number = 0x2AFFFFFF
const GLASS_SEND_TEXT_DISABLED: number = 0x81FFFFFF
// 输入错误色：认证页深色玻璃底专用 ErrorLight（对齐 Login/Register AUTH_ERROR）
const AUTH_ERROR: number = 0xFFFFB4AB
// 返回节流间隔（对齐 Register 的 800ms 防抖，防转场残影期连点弹空导航栈）
const NAV_THROTTLE_MS: number = 800

@Builder
export function ResetPasswordBuilder() {
  ResetPassword()
}

@Component
struct ResetPassword {
  @State phoneNumber: string | null = null
  @State verificationCode: string | null = null
  @State newPassword: string | null = null
  @State checkPhoneNumber: boolean = false
  @State checkCode: boolean = false
  @State checkPassword: boolean = false
  @State countDown: number = 0
  // 提交进行中标记：按钮 loading 并拦截连点（对齐 Register registering）
  @State submitting: boolean = false
  // 验证码请求进行中标记：发送按钮禁用（对齐 Register captchaLoading）
  @State captchaLoading: boolean = false
  @State showPassword: boolean = false
  private timer: number = -1
  // 导航防抖（返回节流，防连点弹空导航栈）
  private navDebouncer: Debouncer = new Debouncer()

  // 页面销毁时清理验证码倒计时定时器，避免泄漏（定时器硬规则）
  aboutToDisappear() {
    if (this.timer !== -1) {
      clearInterval(this.timer)
      this.timer = -1
    }
    this.navDebouncer.cancel()
  }

  private isValidPhone(value: string): boolean {
    return /^1[3-9]\d{9}$/.test(value)
  }

  private sendEnabled(): boolean {
    return this.countDown === 0 && !this.captchaLoading
  }

  private resetCountDown() {
    if (this.timer !== -1) {
      clearInterval(this.timer)
      this.timer = -1
    }
    this.countDown = 0
  }

  startCountDown() {
    this.countDown = 60
    // 重复点击获取验证码前先清理旧定时器，防止多个倒计时并存
    if (this.timer !== -1) {
      clearInterval(this.timer)
    }
    this.timer = setInterval(() => {
      this.countDown--
      if (this.countDown <= 0) {
        clearInterval(this.timer)
        this.timer = -1
        this.countDown = 0
      }
    }, 1000)
  }

  // 发送验证码（对齐 Register.sendCaptcha：先校验手机号，失败立即可重发；
  // 本地 stub 模式下响应 data 为含验证码的回显文案，直接 toast 该文案）
  private async sendCaptcha() {
    if (!this.sendEnabled()) {
      return
    }
    const value = this.phoneNumber ?? ""
    this.checkPhoneNumber = !this.isValidPhone(value)
    if (this.checkPhoneNumber) {
      // 发送验证码前本地校验拒绝：只记事实，不落手机号（敏感红线）
      logger.warn('resetPassword', '发送验证码前手机号校验未通过')
      return
    }
    this.captchaLoading = true
    this.startCountDown()
    try {
      let res = await getCaptcha(this.phoneNumber ?? "")
      if (res.code === HttpStatusCode.Ok) {
        // 低频关键动作成功：验证码已发出（不打验证码值与手机号）
        logger.info('resetPassword', '重置密码验证码发送成功')
        showToastSafety(res.data, resString('captcha_sent'))
      } else {
        // 发送被拒（频控/服务端拒绝）：回滚倒计时允许立即重试
        logger.warn('resetPassword', `验证码发送被拒 code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, resString('error_get_captcha_failed'))
        this.resetCountDown()
      }
    } finally {
      this.captchaLoading = false
    }
  }

  private check(): boolean {
    const phone = this.phoneNumber ?? ""
    this.checkPhoneNumber = !this.isValidPhone(phone)
    this.checkCode = (this.verificationCode?.length ?? 0) !== 6
    const len = this.newPassword?.length ?? 0
    this.checkPassword = len < 6 || len > 16
    return this.checkPhoneNumber || this.checkCode || this.checkPassword
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Stack({ alignContent: Alignment.Top }) {
        // 全屏渐变背景（与 Login/Register 同一渐变，认证域视觉一致）
        Column()
          .width("100%")
          .height("100%")
          .linearGradient({
            direction: GradientDirection.Bottom,
            colors: [
              [resColor('AUTH_GRADIENT_TOP'), 0.0],
              [resColor('AUTH_GRADIENT_MID'), 0.5],
              [resColor('AUTH_GRADIENT_BOTTOM'), 1.0]
            ]
          })

        // 主内容区域
        Column() {
          // 顶部导航行 — 48dp 返回热区 + 22/600 标题居中 + 48 占位（对齐 Register 顶栏）
          Row() {
            Stack({ alignContent: Alignment.Center }) {
              SymbolGlyph($r('sys.symbol.chevron_left'))
                .fontSize(24)
                .fontColor([Color.White])
                .opacity(0.8)
            }
            .width(48)
            .height(48)
            .accessibilityDescription(resString('back'))
            .onClick(() => {
              // 防抖：转场残影期连点 pop 会弹空导航栈导致黑屏（对齐 Register）
              this.navDebouncer.throttle(() => {
                pathStack.pop()
              }, NAV_THROTTLE_MS)
            })

            Text($r('app.string.reset_password'))
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(Color.White)
              .layoutWeight(1)
              .textAlign(TextAlign.Center)

            // 右侧 48 占位，保持标题视觉居中
            Stack()
              .width(48)
              .height(48)
          }
          .width("100%")
          .padding({ top: 16, bottom: 16 })
          .alignItems(VerticalAlign.Center)

          // 引导区（锁形图标 + 标题 + 说明，对齐 Register 品牌区规格）
          Column() {
            Stack({ alignContent: Alignment.Center }) {
              SymbolGlyph($r('sys.symbol.lock'))
                .fontSize(42)
                .fontColor([Color.White])
            }
            .width(92)
            .height(92)
            .borderRadius(46)
            .backgroundColor(resColor('GLASS_BG_SUBTLE'))
            .border({
              width: 1,
              color: resColor('GLASS_BORDER_SUBTLE'),
              style: BorderStyle.Solid
            })

            Text($r('app.string.reset_password_1'))
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(Color.White)
              .lineHeight(28)
              .margin({ top: 12 })

            Text($r('app.string.reset_password_2'))
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(DesignFontWeight.NORMAL)
              .fontColor(Color.White)
              .lineHeight(16)
              .margin({ top: 4 })
          }
          .width("100%")
          .alignItems(HorizontalAlign.Center)
          .margin({ top: 16 })

          // 毛玻璃输入卡片（白 8% / r16 / 内边距水平16·垂直20，对齐 Register 输入卡）
          Column() {
            // 手机号输入行（高 48 + 前缀图标 16 + 间距 12，对齐 Register 手机号行）
            Row() {
              SymbolGlyph($r('sys.symbol.phone'))
                .fontSize(16)
                .fontColor([this.checkPhoneNumber ? AUTH_ERROR : GLASS_PREFIX])
              TextInput({ placeholder: resString('phone_number') })
                .type(InputType.PhoneNumber)
                .maxLength(11)
                .fontSize(DesignFontSize.BODY)
                .fontColor(Color.White)
                .placeholderColor(GLASS_PLACEHOLDER)
                .backgroundColor(Color.Transparent)
                .layoutWeight(1)
                .margin({ left: 12 })
                .accessibilityText($r('app.string.phone_number'))
                .key("resetPhoneInput")
                .onChange((it: string) => {
                  this.phoneNumber = it
                  // 对齐 Register：仅完成（11 位）或清空时校验
                  this.checkPhoneNumber = it.length === 11 ? !this.isValidPhone(it) : false
                  // 输满 11 位且合法自动聚焦验证码框
                  if (it.length === 11 && this.isValidPhone(it)) {
                    focusControl.requestFocus("resetCodeInput")
                  }
                })
            }
            .width("100%")
            .height(48)
            .alignItems(VerticalAlign.Center)

            if (this.checkPhoneNumber) {
              Text($r('app.string.invalid_phone'))
                .fontColor(AUTH_ERROR)
                .fontSize(DesignFontSize.CAPTION)
                .lineHeight(16)
                .margin({ top: 4 })
                .transition(TransitionEffect.OPACITY)
            }

            // 卡内分隔线（白 12% / 1dp / 缩进 24，对齐 Register）
            Divider()
              .color(GLASS_DIVIDER)
              .strokeWidth(1)
              .margin({ left: 24, right: 24, top: 8, bottom: 8 })

            // 验证码输入行（高 48 + Key 16 + 发送按钮，对齐 Register 验证码行）
            Row() {
              SymbolGlyph($r('sys.symbol.key'))
                .fontSize(16)
                .fontColor([this.checkCode ? AUTH_ERROR : GLASS_PREFIX])
              TextInput({ placeholder: resString('verification_code') })
                .type(InputType.Number)
                .maxLength(6)
                .fontSize(DesignFontSize.BODY)
                .fontColor(Color.White)
                .placeholderColor(GLASS_PLACEHOLDER)
                .backgroundColor(Color.Transparent)
                .layoutWeight(1)
                .margin({ left: 12 })
                .key("resetCodeInput")
                .onChange((it: string) => {
                  // 只允许输入数字，过滤非数字字符（对齐 Register）
                  const filtered = it.replace(/[^0-9]/g, '')
                  if (filtered.length <= 6) {
                    this.verificationCode = filtered
                    this.checkCode = false
                  }
                  // 验证码输满 6 位自动聚焦新密码框
                  if (filtered.length === 6) {
                    focusControl.requestFocus("resetPasswordInput")
                  }
                })

              // 发送按钮 — 1dp 白30% 描边 + r12 + h40（对齐 Register 发送按钮）
              Stack({ alignContent: Alignment.Center }) {
                Text(this.countDown > 0 ? `重新发送(${this.countDown}s)` : resString('get_verification_code'))
                  .fontSize(DesignFontSize.CAPTION)
                  .fontWeight(DesignFontWeight.MEDIUM)
                  .fontColor(this.sendEnabled() ? GLASS_ICON_ACTIVE : GLASS_SEND_TEXT_DISABLED)
              }
              .height(40)
              .padding({ left: 14, right: 14 })
              .borderRadius(DesignDimens.RADIUS_MD)
              .border({
                width: 1,
                color: this.sendEnabled() ? resColor('GLASS_INPUT') : GLASS_SEND_BORDER_DISABLED
              })
              .onClick(() => {
                this.sendCaptcha()
              })
            }
            .width("100%")
            .height(48)
            .alignItems(VerticalAlign.Center)

            if (this.checkCode) {
              Text($r('app.string.invalid_verification_code'))
                .fontColor(AUTH_ERROR)
                .fontSize(DesignFontSize.CAPTION)
                .lineHeight(16)
                .margin({ top: 4 })
                .transition(TransitionEffect.OPACITY)
            }

            // 卡内分隔线（白 12% / 1dp / 缩进 24）
            Divider()
              .color(GLASS_DIVIDER)
              .strokeWidth(1)
              .margin({ left: 24, right: 24, top: 8, bottom: 8 })

            // 新密码输入行（占位「设置新密码」，对齐 Register 密码行）
            Row() {
              SymbolGlyph($r('sys.symbol.lock'))
                .fontSize(16)
                .fontColor([this.checkPassword ? AUTH_ERROR : GLASS_PREFIX])
              TextInput({ placeholder: resString('reset_password_3') })
                .type(this.showPassword ? InputType.Normal : InputType.Password)
                .maxLength(16)
                .fontSize(DesignFontSize.BODY)
                .fontColor(Color.White)
                .placeholderColor(GLASS_PLACEHOLDER)
                .backgroundColor(Color.Transparent)
                .layoutWeight(1)
                .margin({ left: 12 })
                .key("resetPasswordInput")
                .showPasswordIcon(false)
                .onChange((it: string) => {
                  this.newPassword = it
                  this.checkPassword = false
                })

              // 密码可见性切换 — 图标化 + 48dp 热区（对齐 Register）
              Stack({ alignContent: Alignment.Center }) {
                SymbolGlyph(this.showPassword ? $r('sys.symbol.eye_slash') : $r('sys.symbol.eye'))
                  .fontSize(20)
                  .fontColor([GLASS_ICON_ACTIVE])
              }
              .width(48)
              .height(48)
              .accessibilityDescription(this.showPassword ? resString('hide_password') : resString('show_password'))
              .onClick(() => {
                this.showPassword = !this.showPassword
              })
            }
            .width("100%")
            .height(48)
            .alignItems(VerticalAlign.Center)

            if (this.checkPassword) {
              Text($r('app.string.invalid_password'))
                .fontColor(AUTH_ERROR)
                .fontSize(DesignFontSize.CAPTION)
                .lineHeight(16)
                .margin({ top: 4 })
                .transition(TransitionEffect.OPACITY)
            }

            // 重置按钮 — 卡内 primary 实底胶囊（对齐 AuthCtaButton：48dp / r999 / 15sp·600·ls1）
            Stack() {
              if (this.submitting) {
                LoadingProgress()
                  .width(22)
                  .height(22)
                  .color(Color.White)
              } else {
                Text($r('app.string.reset_password'))
                  .fontSize(BUTTON_FONT_SIZE)
                  .fontWeight(DesignFontWeight.SEMI_BOLD)
                  .fontColor(Color.White)
                  .letterSpacing(BUTTON_LETTER_SPACING)
              }
            }
            .width("100%")
            .height(48)
            .borderRadius(DesignDimens.RADIUS_FULL)
            .backgroundColor(resColor('PRIMARY'))
            .opacity(this.submitting ? 0.38 : 1)
            .onClick(() => {
              // 防连点：请求进行中直接忽略
              if (this.submitting) {
                return
              }
              if (this.check()) {
                // 提交本地校验拒绝：只记校验事实布尔值，不落输入内容（敏感红线）
                logger.warn('resetPassword',
                  `重置密码提交校验未通过 phone=${this.checkPhoneNumber} code=${this.checkCode} password=${this.checkPassword}`)
                return
              }
              this.submitting = true
              this.requestReset()
            })
            .margin({ top: 20 })
          }
          .width("100%")
          .borderRadius(DesignDimens.CARD_RADIUS)
          .backgroundColor(GLASS_CARD_BG)
          .border({
            width: 1,
            color: resColor('GLASS_BORDER_SUBTLE'),
            style: BorderStyle.Solid
          })
          .padding({ left: 16, right: 16, top: 20, bottom: 20 })
          .margin({ top: 24 })

          // 返回登录入口 — 仅「返回登录」可点（13sp + 48dp 热区 + 800ms 节流，对齐 Register 登录入口）
          Row() {
            Text($r('app.string.reset_password_4'))
              .fontSize(DesignFontSize.CAPTION)
              .lineHeight(16)
              .fontColor(Color.White)
            Text($r('app.string.reset_password_5'))
              .fontSize(DesignFontSize.CAPTION)
              .lineHeight(16)
              .fontWeight(DesignFontWeight.MEDIUM)
              .fontColor(Color.White)
              .padding({ left: 4, right: 4, top: 16, bottom: 16 })
              .onClick(() => {
                // 防抖：转场残影期连点 pop 会弹空导航栈导致黑屏（对齐 Register）
                this.navDebouncer.throttle(() => {
                  pathStack.pop()
                }, NAV_THROTTLE_MS)
              })
          }
          .margin({ top: 8 })
        }
        .width("100%")
        .height("100%")
        .alignItems(HorizontalAlign.Center)
        // 沉浸式：内容按状态栏/底部导航条避让，背景渐变由 Stack 底层全屏承载（对齐 Register）
        .padding({ left: 24, right: 24, top: safeTopVp(), bottom: safeBottomVp() })
      }
      .width("100%")
      .height("100%")
      }
    }
    .hideTitleBar(true)
    .height("100%")
  }

  private async requestReset() {
    try {
      const form: ResetPasswordForm = {
        phoneNumber: this.phoneNumber,
        code: this.verificationCode,
        newPassword: this.newPassword
      }
      let res = await resetPassword(form)
      if (res.code === HttpStatusCode.Ok) {
        // 低频关键动作成功：密码重置成功
        logger.info('resetPassword', '密码重置成功，引导回登录页')
        showToastSafety(resString('reset_password_6'))
        // 重置成功回登录页：本页由登录页 push 而来，pop 即回（不 replace，保留登录页栈位）
        pathStack.pop()
      } else {
        // 业务拒绝（验证码错误/已过期等）：留在本页便于修正重试
        logger.warn('resetPassword', `密码重置被拒 code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, resString('reset_password_7'))
      }
    } finally {
      this.submitting = false
    }
  }
}

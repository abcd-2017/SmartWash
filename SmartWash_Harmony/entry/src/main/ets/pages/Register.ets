import { pathStack } from "../utils/PathStackUtil"
import { RouteNames } from "../constant/RouteNames"
import { getCaptcha, register } from "../network/api/UserApi"
import { HttpStatusCode } from "@ohos/axios"
import { showToastSafety } from "../utils/ToastUtil"
import { setValue } from "../utils/StorageUtil"
import { CommonConstants } from "../constant/CommonConstants"
import { Debouncer } from "../utils/DebounceUtil"
import { logger } from "../utils/Logger"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import { ThemeRefresh } from '../view/common/ThemeRefresh'

// ================= 页面级设计常量（对齐 Android 认证页，DesignSystem.ets 暂无对应 token）=================
// 主按钮文字 15sp·600·ls1sp 为组件级规格（DESIGN.md §3.3 / Android AuthCtaButton.kt:66-70），
// 不在 8 档字阶内；全局按钮批次补 token 后回收本常量。
const BUTTON_FONT_SIZE: number = 15
const BUTTON_LETTER_SPACING: number = 1
// 玻璃卡底白 8%（Android GlassBgCard = Color.White.copy(alpha = 0.08f)）
const GLASS_CARD_BG: number = 0x14FFFFFF
// 卡内分隔线白 12%（Android RegisterPage.kt:470-480）
const GLASS_DIVIDER: number = 0x1FFFFFFF
// 占位符白 45%（Android contentColor.copy(alpha = 0.45f)）
const GLASS_PLACEHOLDER: number = 0x73FFFFFF
// 前缀图标白 50%（Android contentColor.copy(alpha = 0.5f)）
const GLASS_PREFIX: number = 0x80FFFFFF
// 尾部图标白 92%（Android contentColor.copy(alpha = 0.92f)）
const GLASS_ICON_ACTIVE: number = 0xEBFFFFFF
// 进度条未激活段白 28%（Android RegisterPage.kt:107 ProgressInactive）
const GLASS_PROGRESS_INACTIVE: number = 0x47FFFFFF
// 发送验证码按钮禁用态：描边白 30%×0.55、文字白 92%×0.55（Android RegisterPage.kt:545-552）
const GLASS_SEND_BORDER_DISABLED: number = 0x2AFFFFFF
const GLASS_SEND_TEXT_DISABLED: number = 0x81FFFFFF
// 输入错误色：Android 认证页用 ErrorLight #FFB4AB（深色玻璃底专用），
// 与 DESIGN.md 浅色 surface 的 error #A8435A 冲突，按「Android 代码优先」取 ErrorLight
const AUTH_ERROR: number = 0xFFFFB4AB
// 返回/去登录的节流间隔：与安卓 RegisterPage.kt:114-121 的 800ms 防抖一致，
// 防转场残影期连点把导航栈弹空（黑屏）
const NAV_THROTTLE_MS: number = 800

@Builder
export function RegisterBuilder() {
  Register()
}

@Component
struct Register {
  @State phoneNumber: string | null = null
  @State password: string | null = null
  @State verificationCode: string | null = null
  @State checkPhoneNumber: boolean = false
  @State checkPassword: boolean = false
  @State checkCode: boolean = false
  @State countDown: number = 0
  // 注册请求进行中标记：按钮展示 loading 并拦截连点（评审 #12）
  @State registering: boolean = false
  // 验证码请求进行中标记：发送按钮禁用（对齐 Android sendEnabled = countDown===0 && captchaState !is Loading）
  @State captchaLoading: boolean = false
  @State showPassword: boolean = false
  private timer: number = -1
  // 导航防抖（返回/去登录节流，防连点弹空导航栈）
  private navDebouncer: Debouncer = new Debouncer()

  // 页面销毁时清理验证码倒计时定时器，避免泄漏（评审 #5）
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

  // 发送验证码（对齐 Android RegisterPage.kt:376-395：先校验手机号，失败立即可重发）
  private async sendCaptcha() {
    if (!this.sendEnabled()) {
      return
    }
    this.checkPhone()
    if (this.checkPhoneNumber) {
      // 发送验证码前本地校验拒绝：只记事实，不落手机号（敏感红线）
      logger.warn('register', '发送验证码前手机号校验未通过')
      return
    }
    this.captchaLoading = true
    this.startCountDown()
    try {
      let res = await getCaptcha(this.phoneNumber ?? "")
      if (res.code === HttpStatusCode.Ok) {
        // 低频关键动作成功：验证码已发出（不打验证码值与手机号）
        logger.info('register', '验证码发送成功')
        showToastSafety(res.data, "验证码已发送，请查看短信")
      } else {
        // 发送被拒（频控/服务端拒绝）：回滚倒计时允许立即重试
        logger.warn('register', `验证码发送被拒 code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, "验证码获取失败")
        this.resetCountDown()
      }
    } finally {
      this.captchaLoading = false
    }
  }

  check() {
    this.checkPhone()
    this.checkCode = (this.verificationCode?.length ?? 0) !== 6
    let len = this.password?.length ?? 0;
    this.checkPassword = len < 6 || len > 16
  }

  checkPhone() {
    let value = this.phoneNumber ?? ""
    this.checkPhoneNumber = !this.isValidPhone(value)
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内 DesignColor 静态令牌直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Stack({ alignContent: Alignment.Top }) {
        // 全屏渐变背景
        Column()
          .width("100%")
          .height("100%")
          .linearGradient({
            direction: GradientDirection.Bottom,
            // 三段渐变对齐安卓（160deg #1A9E6E → #0D7A4A@50% → #0B5C3A，差异分析 2026-10-27）
            colors: [
              [DesignColor.AUTH_GRADIENT_TOP, 0.0],
              [DesignColor.AUTH_GRADIENT_MID, 0.5],
              [DesignColor.AUTH_GRADIENT_BOTTOM, 1.0]
            ]
          })

        // 主内容区域
        Column() {
          // 顶部导航行 — 48dp 返回热区 + 22/600 标题居中 + 48 占位（对齐 Android RegisterPage.kt:221-254）
          Row() {
            Stack({ alignContent: Alignment.Center }) {
              SymbolGlyph($r('sys.symbol.chevron_left'))
                .fontSize(24)
                .fontColor([Color.White])
                .opacity(0.8)
            }
            .width(48)
            .height(48)
            .onClick(() => {
              // 防抖：转场残影期连点 pop 会弹空导航栈导致黑屏（对齐安卓 RegisterPage.kt:116-121）
              this.navDebouncer.throttle(() => {
                pathStack.pop()
              }, NAV_THROTTLE_MS)
            })

            Text("注 册")
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

          // 品牌区（92dp 单层圆形 + 标题 22/600 + 副标题 12，对齐 Android RegisterPage.kt:259-292）
          Column() {
            Stack({ alignContent: Alignment.Center }) {
              SymbolGlyph($r('sys.symbol.drop'))
                .fontSize(42)
                .fontColor([Color.White])
            }
            .width(92)
            .height(92)
            .borderRadius(46)
            .backgroundColor(DesignColor.GLASS_BG_SUBTLE)
            .border({
              width: 1,
              color: DesignColor.GLASS_BORDER_SUBTLE,
              style: BorderStyle.Solid
            })

            Text("创建账号")
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(Color.White)
              .lineHeight(28)
              .margin({ top: 12 })

            Text("开启智能洗衣之旅")
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(DesignFontWeight.NORMAL)
              .fontColor(Color.White)
              .lineHeight(16)
              .margin({ top: 4 })

            // 进度条（激活纯白 + 未激活白 28%，对齐 Android RegisterPage.kt:296-319）
            Row({ space: 8 }) {
              Column()
                .width(32)
                .height(6)
                .borderRadius(DesignDimens.RADIUS_FULL)
                .backgroundColor(Color.White)

              Column()
                .width(32)
                .height(6)
                .borderRadius(DesignDimens.RADIUS_FULL)
                .backgroundColor(GLASS_PROGRESS_INACTIVE)

              Column()
                .width(32)
                .height(6)
                .borderRadius(DesignDimens.RADIUS_FULL)
                .backgroundColor(GLASS_PROGRESS_INACTIVE)
            }
            .margin({ top: 20 })
          }
          .width("100%")
          .alignItems(HorizontalAlign.Center)
          .margin({ top: 16 })

          // 毛玻璃输入卡片（白 8% / r16 / 内边距水平16·垂直20，对齐 Android RegisterPage.kt:325-331）
          Column() {
            // 手机号输入行（高 48 + 前缀图标 16/.5 + 间距 12，对齐 Android PhoneNumberInput.kt:53-68）
            Row() {
              SymbolGlyph($r('sys.symbol.phone'))
                .fontSize(16)
                .fontColor([this.checkPhoneNumber ? AUTH_ERROR : GLASS_PREFIX])
              TextInput({ text: $$this.phoneNumber, placeholder: "手机号" })
                .type(InputType.PhoneNumber)
                .maxLength(11)
                .fontSize(DesignFontSize.BODY)
                .fontColor(Color.White)
                .placeholderColor(GLASS_PLACEHOLDER)
                .backgroundColor(Color.Transparent)
                .layoutWeight(1)
                .margin({ left: 12 })
                .key("registerPhoneInput")
                .onChange((it: string) => {
                  this.phoneNumber = it
                  // 对齐 Android RegisterPage.kt:340-348：仅完成（11 位）或清空时校验
                  this.checkPhoneNumber = it.length === 11 ? !this.isValidPhone(it) : false
                  // 输入完成自动跳转到验证码框
                  if (it.length === 11 && this.isValidPhone(it)) {
                    focusControl.requestFocus("registerCodeInput")
                  }
                })
            }
            .width("100%")
            .height(48)
            .alignItems(VerticalAlign.Center)

            if (this.checkPhoneNumber) {
              Text("请输入正确的手机号")
                .fontColor(AUTH_ERROR)
                .fontSize(DesignFontSize.CAPTION)
                .lineHeight(16)
                .margin({ top: 4 })
                .transition(TransitionEffect.OPACITY)
            }

            // 卡内分隔线（白 12% / 1dp / 缩进 24，对齐 Android RegisterPage.kt:470-480）
            Divider()
              .color(GLASS_DIVIDER)
              .strokeWidth(1)
              .margin({ left: 24, right: 24, top: 8, bottom: 8 })

            // 验证码输入行（高 48 + Key 16/.5，对齐 Android RegisterPage.kt:497-512）
            Row() {
              SymbolGlyph($r('sys.symbol.key'))
                .fontSize(16)
                .fontColor([this.checkCode ? AUTH_ERROR : GLASS_PREFIX])
              TextInput({ text: $$this.verificationCode, placeholder: "验证码" })
                .type(InputType.Number)
                .maxLength(6)
                .fontSize(DesignFontSize.BODY)
                .fontColor(Color.White)
                .placeholderColor(GLASS_PLACEHOLDER)
                .backgroundColor(Color.Transparent)
                .layoutWeight(1)
                .margin({ left: 12 })
                .key("registerCodeInput")
                .onChange((it: string) => {
                  // 只允许输入数字，过滤非数字字符（对齐 Android RegisterPage.kt:364-370）
                  const filtered = it.replace(/[^0-9]/g, '')
                  if (filtered.length <= 6) {
                    this.verificationCode = filtered
                    this.checkCode = false
                  }
                  // 验证码输满 6 位自动聚焦密码框（对齐安卓 RegisterPage.kt:371-374）
                  if (filtered.length === 6) {
                    focusControl.requestFocus("registerPasswordInput")
                  }
                })

              // 发送按钮 — 1dp 白30% 描边 + r12 + h40（对齐 Android RegisterPage.kt:553-568）
              Stack({ alignContent: Alignment.Center }) {
                Text(this.countDown > 0 ? `重新发送(${this.countDown}s)` : "获取验证码")
                  .fontSize(DesignFontSize.CAPTION)
                  .fontWeight(DesignFontWeight.MEDIUM)
                  .fontColor(this.sendEnabled() ? GLASS_ICON_ACTIVE : GLASS_SEND_TEXT_DISABLED)
              }
              .height(40)
              .padding({ left: 14, right: 14 })
              .borderRadius(DesignDimens.RADIUS_MD)
              .border({
                width: 1,
                color: this.sendEnabled() ? DesignColor.GLASS_INPUT : GLASS_SEND_BORDER_DISABLED
              })
              .onClick(() => {
                this.sendCaptcha()
              })
            }
            .width("100%")
            .height(48)
            .alignItems(VerticalAlign.Center)

            if (this.checkCode) {
              Text("请输入正确的验证码")
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

            // 密码输入行（占位「设置密码」，对齐 Android RegisterPage.kt:404-416）
            Row() {
              SymbolGlyph($r('sys.symbol.lock'))
                .fontSize(16)
                .fontColor([this.checkPassword ? AUTH_ERROR : GLASS_PREFIX])
              TextInput({ text: $$this.password, placeholder: "设置密码" })
                .type(this.showPassword ? InputType.Normal : InputType.Password)
                .maxLength(16)
                .fontSize(DesignFontSize.BODY)
                .fontColor(Color.White)
                .placeholderColor(GLASS_PLACEHOLDER)
                .backgroundColor(Color.Transparent)
                .layoutWeight(1)
                .margin({ left: 12 })
                .key("registerPasswordInput")
                .showPasswordIcon(false)
                .onChange((it: string) => this.password = it)

              // 密码可见性切换 — 图标化 + 48dp 热区（对齐 Android PasswordInput.kt:112-136）
              Stack({ alignContent: Alignment.Center }) {
                SymbolGlyph(this.showPassword ? $r('sys.symbol.eye_slash') : $r('sys.symbol.eye'))
                  .fontSize(20)
                  .fontColor([GLASS_ICON_ACTIVE])
              }
              .width(48)
              .height(48)
              .onClick(() => {
                this.showPassword = !this.showPassword
              })
            }
            .width("100%")
            .height(48)
            .alignItems(VerticalAlign.Center)

            if (this.checkPassword) {
              Text("密码必须为6-16位")
                .fontColor(AUTH_ERROR)
                .fontSize(DesignFontSize.CAPTION)
                .lineHeight(16)
                .margin({ top: 4 })
                .transition(TransitionEffect.OPACITY)
            }

            // 注册按钮 — 卡内 primary 实底胶囊（对齐 Android AuthCtaButton：48dp / r999 / 15sp·600·ls1）
            Stack() {
              if (this.registering) {
                LoadingProgress()
                  .width(22)
                  .height(22)
                  .color(Color.White)
              } else {
                Text("注 册")
                  .fontSize(BUTTON_FONT_SIZE)
                  .fontWeight(DesignFontWeight.SEMI_BOLD)
                  .fontColor(Color.White)
                  .letterSpacing(BUTTON_LETTER_SPACING)
              }
            }
            .width("100%")
            .height(48)
            .borderRadius(DesignDimens.RADIUS_FULL)
            .backgroundColor(DesignColor.PRIMARY)
            .opacity(this.registering ? 0.38 : 1)
            .onClick(() => {
              // 防连点：请求进行中直接忽略
              if (this.registering) {
                return
              }
              this.check()
              if (this.checkPhoneNumber || this.checkPassword || this.checkCode) {
                // 注册提交本地校验拒绝：只记校验事实布尔值，不落输入内容（敏感红线）
                logger.warn('register', `注册提交校验未通过 phone=${this.checkPhoneNumber} code=${this.checkCode} password=${this.checkPassword}`)
                return
              }
              this.registering = true
              this.requestRegister()
            })
            .margin({ top: 20 })
          }
          .width("100%")
          .borderRadius(DesignDimens.CARD_RADIUS)
          .backgroundColor(GLASS_CARD_BG)
          .border({
            width: 1,
            color: DesignColor.GLASS_BORDER_SUBTLE,
            style: BorderStyle.Solid
          })
          .padding({ left: 16, right: 16, top: 20, bottom: 20 })
          .margin({ top: 24 })

          // 登录入口 — 仅「立即登录」可点（12sp + 48dp 热区 + 800ms 节流，对齐 Android RegisterPage.kt:443-465）
          Row() {
            Text("已有账号？")
              .fontSize(DesignFontSize.CAPTION)
              .lineHeight(16)
              .fontColor(Color.White)
            Text("立即登录")
              .fontSize(DesignFontSize.CAPTION)
              .lineHeight(16)
              .fontWeight(DesignFontWeight.MEDIUM)
              .fontColor(Color.White)
              .padding({ left: 4, right: 4, top: 16, bottom: 16 })
              .onClick(() => {
                // 防抖：转场残影期连点 pop 会弹空导航栈导致黑屏（对齐安卓 RegisterPage.kt:116-121）
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
        // 沉浸式：内容按状态栏/底部导航条避让，背景渐变由 Stack 底层全屏承载（问题1）
        .padding({ left: 24, right: 24, top: safeTopVp(), bottom: safeBottomVp() })
      }
      .width("100%")
      .height("100%")
      }
    }
    .hideTitleBar(true)
    .height("100%")
  }

  private async requestRegister() {
    try {
      let res = await register({
        phoneNumber: this.phoneNumber,
        code: this.verificationCode,
        password: this.password
      })
      if (res.code === HttpStatusCode.Ok) {
        this.showPassword = false
        // 低频关键动作成功：注册成功
        logger.info('register', '注册成功')
        showToastSafety("注册成功")
        // 注册即自动登录（双 token 契约升级）：信封 data 为 {token, refreshToken, role} 对象，
        // 不再是 token 字符串；token 缺失按注册失败降级处理（与登录页同语义）
        const token = res.data?.token ?? ""
        if (token === "") {
          logger.warn('register', '注册响应缺少 token，按注册失败处理')
          showToastSafety(res.message, "注册失败，请稍后重试")
          return
        }
        // await 确保写入完成后，后续完善信息/首页请求才能带上新 token
        await setValue(CommonConstants.USER_TOKEN, token)
        await setValue(CommonConstants.USER_REFRESH_TOKEN, res.data?.refreshToken ?? "")
        // role 存入 AppStorage 备用（普通用户固定 "user"）
        AppStorage.setOrCreate("userRole", res.data?.role ?? "user")
        // 注册成功后去向：鸿蒙对应页 AddSchool，等价 Android UpdateUserInfo
        pathStack.replacePathByName(RouteNames.ADD_SCHOOL, null)
      } else {
        // 注册业务拒绝（验证码错误/手机号已注册等）
        logger.warn('register', `注册被拒绝 code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, "注册失败")
      }
    } finally {
      this.registering = false
    }
  }
}

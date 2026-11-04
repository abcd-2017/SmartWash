import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
// 学校缺失判定改用 getUserInfo（对齐 Android LoginViewModel.schoolInfoMissing 取 schoolId + studentId）
import { getUserInfo, login } from "../network/api/UserApi"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { setValue } from "../utils/StorageUtil"
import { CommonConstants } from "../constant/CommonConstants"
import { Debouncer } from "../utils/DebounceUtil"
import { logger } from "../utils/Logger"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import vibrator from "@ohos.vibrator"

// ================= 页面级设计常量（对齐 Android 认证页，DesignSystem.ets 暂无对应 token）=================
// 主按钮文字 15sp·600·ls1sp 为组件级规格（DESIGN.md §3.3 / Android AuthCtaButton.kt:66-70），
// 不在 8 档字阶内；全局按钮批次补 token 后回收本常量。
const BUTTON_FONT_SIZE: number = 15
const BUTTON_LETTER_SPACING: number = 1
// 玻璃卡底白 8%（Android GlassBgCard = Color.White.copy(alpha = 0.08f)）
const GLASS_CARD_BG: number = 0x14FFFFFF
// 卡内分隔线白 12%（Android LoginPage.kt:245-251）
const GLASS_DIVIDER: number = 0x1FFFFFFF
// 占位符白 45%（Android contentColor.copy(alpha = 0.45f)）
const GLASS_PLACEHOLDER: number = 0x73FFFFFF
// 前缀图标白 50%（Android contentColor.copy(alpha = 0.5f)）
const GLASS_PREFIX: number = 0x80FFFFFF
// 尾部图标白 92%（Android contentColor.copy(alpha = 0.92f)）
const GLASS_ICON_ACTIVE: number = 0xEBFFFFFF
// 输入错误色：Android 认证页用 ErrorLight #FFB4AB（深色玻璃底专用），
// 与 DESIGN.md 浅色 surface 的 error #A8435A 冲突，按「Android 代码优先」取 ErrorLight
const AUTH_ERROR: number = 0xFFFFB4AB
// 注册入口 800ms 节流（对齐 Android ClickDebouncer.ACTION_CLICK_INTERVAL_MS）
const NAV_THROTTLE_MS: number = 800
// 触感时长（对齐 Android performHaptic 的 MEDIUM / ERROR 档位）
const HAPTIC_MS: number = 10
const HAPTIC_ERROR_MS: number = 30

@Builder
export function LoginBuilder() {
  Login()
}

@Component
struct Login {
  @State phoneNumber: string | null = null
  @State password: string | null = null
  @State checkPhoneNumber: boolean = false
  @State checkPassword: boolean = false
  @State showPassword: boolean = false
  // 登录请求进行中标记：按钮展示 loading 并拦截连点，防止重复提交（评审 #12）
  @State loginLoading: boolean = false
  // 按压缩放标记（对齐 Android pressScale 0.97 / 100ms）
  @State registerPressed: boolean = false
  @State ctaPressed: boolean = false
  private controller = new TextInputController()
  // 注册入口 800ms 节流（对齐 Android rememberDebouncedClick，防转场残影期连点压栈）
  private navDebouncer: Debouncer = new Debouncer()

  aboutToDisappear() {
    this.navDebouncer.cancel()
  }

  private isValidPhone(value: string): boolean {
    return /^1[3-9]\d{9}$/.test(value)
  }

  // 触感反馈（对齐 Android performHaptic：MEDIUM=有效提交 / ERROR=校验失败）
  private performHaptic(error: boolean) {
    try {
      vibrator.startVibration({ type: 'time', duration: error ? HAPTIC_ERROR_MS : HAPTIC_MS },
        { id: 0, usage: 'unknown' })
    } catch (err) {
      // 忽略不支持的设备（与 AppComponents 一致）
    }
  }

  check() {
    const value = this.phoneNumber ?? ""
    this.checkPhoneNumber = !this.isValidPhone(value)
    const len = this.password?.length ?? 0
    this.checkPassword = len < 6 || len > 16
  }

  private doLogin() {
    // 防连点：请求进行中直接忽略（按钮同时置禁用态）
    if (this.loginLoading) {
      return
    }
    this.check()
    if (this.checkPhoneNumber || this.checkPassword) {
      // 登录提交本地校验拒绝：只记校验事实布尔值，不落输入内容（敏感红线）
      logger.warn('login', `登录提交校验未通过 phone=${this.checkPhoneNumber} password=${this.checkPassword}`)
      this.performHaptic(true)
      return
    }
    this.performHaptic(false)
    // 提交时收起键盘（对齐 Android LoginPage.kt:279）
    this.controller.stopEditing()
    this.loginLoading = true
    this.requestLogin()
  }

  private async requestLogin() {
    try {
      let res = await login({
        phoneNumber: this.phoneNumber,
        password: this.password
      })
      if (res.code === HttpStatusCode.Ok) {
        // 后端登录信封 data 为 {token, role} 对象（评审 #1 适配），取 data.token 持久化
        const token = res.data?.token ?? ""
        if (token === "") {
          // 信封异常降级：code 成功但 data.token 缺失
          logger.warn('login', '登录响应缺少 token，按登录失败处理')
          showToastSafety(res.message, "登录失败，请稍后重试")
          return
        }
        // await 确保写入完成后，后续 getUserInfo 等请求才能带上新 token
        await setValue(CommonConstants.USER_TOKEN, token)
        // role 存入 AppStorage 备用（普通用户固定 "user"）
        AppStorage.setOrCreate("userRole", res.data?.role ?? "user")
        // 低频关键动作成功：登录成功（不带手机号，敏感红线）
        logger.info('login', '登录成功')
        // 登录成功 toast（对齐安卓 LoginPage.kt:140「登录成功」）
        showToastSafety("登录成功")
        const infoRes = await getUserInfo()
        // 学校信息缺失判定对齐 Android LoginViewModel.schoolInfoMissing()（LoginViewModel.kt:72-83）：
        // schoolId <= 0 或 studentId 为空 → 去完善信息页（鸿蒙对应页 AddSchool，等价 Android UpdateUserInfo）；
        // 网络失败/数据缺失（无法确认）→ 放行进首页，不误拦（同 Android null 语义）
        let schoolMissing = false
        if (infoRes.code === HttpStatusCode.Ok && infoRes.data !== null) {
          const schoolId = infoRes.data.schoolVo?.schoolId ?? 0
          const studentId = infoRes.data.studentId ?? ""
          schoolMissing = schoolId <= 0 || studentId.trim() === ""
        }
        if (infoRes.code !== HttpStatusCode.Ok || infoRes.data === null) {
          // 降级留痕：无法确认学校信息时按齐全放行（401 已由网络层统一清理并跳登录）
          logger.warn('login', '登录后拉取用户信息失败，跳过学校信息缺失判定（默认放行）')
        }
        if (schoolMissing) {
          pathStack.replacePathByName(RouteNames.ADD_SCHOOL, null)
        } else {
          if (pathStack.size() > 0) {
            pathStack.clear()
          }
          AppStorage.setOrCreate("userLoginFlag", true)
        }
      } else {
        // 业务拒绝（如密码错误/账号不存在）：warn 留痕，message 为服务端提示文案，无敏感字段
        logger.warn('login', `登录被拒绝 code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, "登录失败，请稍后重试")
      }
    } finally {
      this.loginLoading = false
    }
  }

  build() {
    NavDestination() {
      // 全屏渐变背景
      Column() {
        // 占位：将内容推到垂直居中位置（对齐 Android Spacer(weight 1f)）
        Blank()

        // 品牌标识 — 96dp 单层圆形（对齐 Android LoginPage.kt:181-196）
        Stack({ alignContent: Alignment.Center }) {
          SymbolGlyph($r('sys.symbol.laundry'))
            .fontSize(56)
            .fontColor([Color.White])
        }
        .width(96)
        .height(96)
        .borderRadius(48)
        .backgroundColor(DesignColor.GLASS_BG_SUBTLE)
        .border({
          width: 1,
          color: DesignColor.GLASS_BORDER_SUBTLE,
          style: BorderStyle.Solid
        })

        // 品牌文字（headlineLarge 28/36/700，对齐 Android LoginPage.kt:200-204）
        Text("智能洗衣")
          .fontSize(DesignFontSize.HEADLINE)
          .fontWeight(DesignFontWeight.BOLD)
          .fontColor(Color.White)
          .lineHeight(36)
          .margin({ top: 18 })

        // 副标题（12sp 白 + ls0.5，文案对齐 Android brand_subtitle）
        Text("智慧校园 · 轻松洗衣")
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.NORMAL)
          .fontColor(Color.White)
          .lineHeight(16)
          .letterSpacing(0.5)
          .margin({ top: 8 })

        // 毛玻璃输入卡片（白 8% / r16 / 内边距水平16·垂直20，对齐 Android LoginPage.kt:221-227）
        Column() {
          // 手机号输入行（高 48 + 前缀图标 16/.5 + 间距 12，对齐 Android PhoneNumberInput.kt:53-68）
          Row() {
            SymbolGlyph($r('sys.symbol.phone'))
              .fontSize(16)
              .fontColor([this.checkPhoneNumber ? AUTH_ERROR : GLASS_PREFIX])
            TextInput({ placeholder: "手机号" })
              .type(InputType.PhoneNumber)
              .maxLength(11)
              .fontSize(DesignFontSize.BODY)
              .fontColor(Color.White)
              .placeholderColor(GLASS_PLACEHOLDER)
              .backgroundColor(Color.Transparent)
              .layoutWeight(1)
              .margin({ left: 12 })
              .key("loginPhoneInput")
              .onChange((it: string) => {
                this.phoneNumber = it
                // 实时校验：满 11 位即校验（对齐 Android LoginPage.kt:236）
                if (it.length === 11) {
                  this.checkPhoneNumber = !this.isValidPhone(it)
                }
                // 手机号输满 11 位且合法自动聚焦密码框（对齐安卓 LoginPage.kt:238-240）
                if (it.length === 11 && this.isValidPhone(it)) {
                  focusControl.requestFocus("loginPasswordInput")
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

          // 卡内分隔线（白 12% / 1dp / 缩进 8，对齐 Android LoginPage.kt:245-251）
          Divider()
            .color(GLASS_DIVIDER)
            .strokeWidth(1)
            .margin({ left: 8, right: 8, top: 8, bottom: 8 })

          // 密码输入行
          Row() {
            SymbolGlyph($r('sys.symbol.lock'))
              .fontSize(16)
              .fontColor([this.checkPassword ? AUTH_ERROR : GLASS_PREFIX])
            TextInput({ placeholder: "密码", controller: this.controller })
              .type(this.showPassword ? InputType.Normal : InputType.Password)
              .maxLength(16)
              .fontSize(DesignFontSize.BODY)
              .fontColor(Color.White)
              .placeholderColor(GLASS_PLACEHOLDER)
              .backgroundColor(Color.Transparent)
              .layoutWeight(1)
              .margin({ left: 12 })
              .key("loginPasswordInput")
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

          // 登录按钮 — 卡内 primary 实底胶囊（对齐 Android AuthCtaButton：48dp / r999 / 15sp·600·ls1）
          Stack() {
            if (this.loginLoading) {
              // loading 形态：spinner 替代文字（对齐 Android AuthCtaButton.kt:51-73）
              LoadingProgress()
                .width(22)
                .height(22)
                .color(Color.White)
            } else {
              Text("登 录")
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
          .opacity(this.loginLoading ? 0.38 : 1)
          .scale({ x: this.ctaPressed ? 0.97 : 1, y: this.ctaPressed ? 0.97 : 1 })
          .animation({ duration: 100, curve: Curve.EaseOut })
          .onTouch((event: TouchEvent) => {
            if (event.type === TouchType.Down) {
              this.ctaPressed = true
            } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
              this.ctaPressed = false
            }
          })
          .onClick(() => {
            this.doLogin()
          })
          .margin({ top: 24 })
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
        .margin({ top: 32 })

        // 注册入口 — 仅「立即注册」可点（12sp + 48dp 热区 + 800ms 节流 + pressScale，对齐 Android LoginPage.kt:290-319）
        Row() {
          Text("没有账号？")
            .fontSize(DesignFontSize.CAPTION)
            .lineHeight(16)
            .fontColor(Color.White)
          Text("立即注册")
            .fontSize(DesignFontSize.CAPTION)
            .lineHeight(16)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(Color.White)
            .padding({ left: 4, right: 4, top: 16, bottom: 16 })
            .scale({ x: this.registerPressed ? 0.97 : 1, y: this.registerPressed ? 0.97 : 1 })
            .animation({ duration: 100, curve: Curve.EaseOut })
            .onTouch((event: TouchEvent) => {
              if (event.type === TouchType.Down) {
                this.registerPressed = true
              } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
                this.registerPressed = false
              }
            })
            .onClick(() => {
              this.navDebouncer.throttle(() => {
                pathStack.replacePathByName(RouteNames.REGISTER, null)
              }, NAV_THROTTLE_MS)
            })
        }
        .margin({ top: 24 })

        // 占位：将注册入口推到底部
        Blank()
      }
      .width("100%")
      .height("100%")
      .padding({ left: 24, right: 24 })
      .linearGradient({
        direction: GradientDirection.Bottom,
        // 三段渐变对齐安卓（160deg #1A9E6E → #0D7A4A@50% → #0B5C3A，差异分析 2026-10-27）
        colors: [
          [DesignColor.AUTH_GRADIENT_TOP, 0],
          [DesignColor.AUTH_GRADIENT_MID, 0.5],
          [DesignColor.AUTH_GRADIENT_BOTTOM, 1]
        ]
      })
    }
    .hideTitleBar(true)
    .height("100%")
  }
}

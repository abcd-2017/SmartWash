import { changePassword } from "../network/api/UserApi"
import { ChangePasswordForm } from "../network/entity/ChangePasswordForm"
import { CommonConstants } from "../constant/CommonConstants"
import { setValue } from "../utils/StorageUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { logger } from "../utils/Logger"
import { AppButton, PageHeader } from "../view/common/AppComponents"
import { DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import { HttpStatusCode } from "@ohos/axios"
import { ThemeRefresh } from '../view/common/ThemeRefresh'
import { resColor } from '../utils/ResourceColor'
import { resString } from '../utils/ResourceString'

/** 输入框描边样式（ArkTS 对象字面量需显式类型，Setting.ets 同款约定） */
interface ChangePasswordBorderStyle {
  width: number
  color: number | Resource
}

/**
 * 密码输入行（子组件承载聚焦/明文状态）：
 * 不用 @Builder 按值参数传状态——@Builder 按值参数首帧定格不刷新（已知坑），
 * 聚焦描边 / 明文切换经 @Prop 单向同步驱动子组件重渲染，输入与点击经回调回传父级。
 */
@Component
struct PasswordFieldRow {
  @Prop placeholder: string = ""
  @Prop focused: boolean = false
  @Prop showPlain: boolean = false
  onTextChange: (value: string) => void = (_: string) => {
  }
  onFocusChange: (focused: boolean) => void = (_: boolean) => {
  }
  onTogglePlain: () => void = () => {
  }

  /** 输入框通用描边（surfaceVariant 底 + outline 描边 + 聚焦主色，Setting.ets 同款） */
  private inputBorder(focused: boolean): ChangePasswordBorderStyle {
    return { width: 1, color: focused ? $r('app.color.PRIMARY') : $r('app.color.OUTLINE') }
  }

  build() {
    Row() {
      TextInput({ placeholder: this.placeholder })
        .type(this.showPlain ? InputType.Normal : InputType.Password)
        .maxLength(16)
        .height(48)
        .fontSize(DesignFontSize.BODY)
        .backgroundColor($r('app.color.SURFACE_VARIANT'))
        .borderRadius(DesignDimens.INPUT_RADIUS)
        .border(this.inputBorder(this.focused))
        .placeholderColor($r('app.color.TEXT_TERTIARY'))
        .placeholderFont({ size: DesignFontSize.BODY })
        .showPasswordIcon(false)
        .layoutWeight(1)
        .onFocus(() => {
          this.onFocusChange(true)
        })
        .onBlur(() => {
          this.onFocusChange(false)
        })
        .onChange((value: string) => {
          this.onTextChange(value)
        })

      Stack({ alignContent: Alignment.Center }) {
        SymbolGlyph(this.showPlain ? $r('sys.symbol.eye_slash') : $r('sys.symbol.eye'))
          .fontSize(20)
          .fontColor([$r('app.color.TEXT_SECONDARY')])
      }
      .width(DesignDimens.BACK_HIT_AREA)
      .height(DesignDimens.BACK_HIT_AREA)
      .accessibilityDescription(this.showPlain ? resString('hide_password') : resString('show_password'))
      .onClick(() => {
        this.onTogglePlain()
      })
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
  }
}

@Builder
export function ChangePasswordBuilder() {
  ChangePassword()
}

@Component
struct ChangePassword {
  private scroller: Scroller = new Scroller()
  // 三个密码字段（仅内存态，提交后统一清空；密码不落任何本地存储）
  @State oldPassword: string = ""
  @State newPassword: string = ""
  @State confirmPassword: string = ""
  // 明文可见性（三个字段独立切换）
  @State oldVisible: boolean = false
  @State newVisible: boolean = false
  @State confirmVisible: boolean = false
  // 提交校验失败标记（错误文案随标记渲染；只记布尔事实，不落输入内容）
  @State checkOld: boolean = false
  @State checkNew: boolean = false
  @State checkConfirm: boolean = false
  // 输入框聚焦态（聚焦描边主色，Setting.ets 同款）
  @State oldFocused: boolean = false
  @State newFocused: boolean = false
  @State confirmFocused: boolean = false
  // 提交进行中标记：AppButton loading 展示并拦截连点
  @State submitting: boolean = false

  /** 表单字段标签（Setting.ets FormLabel 同款视觉） */
  @Builder
  FormLabel(title: string) {
    Text(title)
      .fontSize(DesignFontSize.BODY_SMALL)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor($r('app.color.TEXT_SECONDARY'))
      .margin({ bottom: DesignDimens.SPACE_XS })
  }

  /** 校验失败提示（错误文案 + 12vp 颜色语义，仅渲染布尔事实对应的固定文案） */
  @Builder
  FieldError(message: string) {
    Text(message)
      .fontSize(DesignFontSize.CAPTION)
      .fontColor($r('app.color.ERROR'))
      .margin({ top: DesignDimens.SPACE_XXS })
  }

  /**
   * 修改密码成功后的登出（账号安全收口）：
   * 直接经 StorageUtil 清 USER_TOKEN + USER_REFRESH_TOKEN 双 token，并复位内存登录态
   * （userRole / userLoginFlag / isLoggedIn 置 false）—— isLoggedIn 是登录根页的唯一切换信号，
   * Index 监听后把 Login 作为根内容渲染并清空页面栈，等价 replace 回登录页语义。
   * 注：不调用 Axios.clearLoginState（其不感知 refreshToken），本页自行完成双 token 清理。
   */
  private async logoutAfterPasswordChange(): Promise<void> {
    await setValue(CommonConstants.USER_TOKEN, "")
    await setValue(CommonConstants.USER_REFRESH_TOKEN, "")
    AppStorage.setOrCreate("userRole", "")
    AppStorage.setOrCreate("userLoginFlag", false)
    AppStorage.setOrCreate("isLoggedIn", false)
    // 低频关键动作成功：改密成功并登出（凭据不落日志）
    logger.info('changePassword', '密码修改成功，已清理本地会话并回登录页')
  }

  private async submit(): Promise<void> {
    if (this.submitting) {
      return
    }
    // 本地校验：三段密码 6-16 位 + 两次新密码一致（与服务端 6-16 位契约对齐）
    const oldLen = this.oldPassword.length
    const newLen = this.newPassword.length
    this.checkOld = oldLen < 6 || oldLen > 16
    this.checkNew = newLen < 6 || newLen > 16
    this.checkConfirm = this.confirmPassword.length === 0 || this.confirmPassword !== this.newPassword
    if (this.checkOld || this.checkNew || this.checkConfirm) {
      logger.warn('changePassword',
        `修改密码提交校验未通过 old=${this.checkOld} new=${this.checkNew} confirm=${this.checkConfirm}`)
      return
    }
    this.submitting = true
    try {
      const form: ChangePasswordForm = {
        oldPassword: this.oldPassword,
        newPassword: this.newPassword
      }
      const res = await changePassword(form)
      if (res.code === HttpStatusCode.Ok) {
        // 低频关键动作成功 toast（后端 Result<String>「密码修改成功」）
        showToastSafety(res.data ?? resString('change_password_1'))
        this.clearForm()
        // 成功即登出：清双 token + 复位会话态，Index 切回登录根页
        await this.logoutAfterPasswordChange()
      } else {
        // 业务拒绝（原密码不正确等）：message 为服务端文案，留在本页便于修正重试
        logger.warn('changePassword', `修改密码被拒 code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, resString('change_password_2'))
      }
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw（401 已由网络层统一清理并跳登录）
      showToastSafety(resString('change_password_3'))
    } finally {
      this.submitting = false
    }
  }

  /** 提交后清空三个密码字段（仅内存态；页面随登出销毁，此处防御性兜底） */
  private clearForm(): void {
    this.oldPassword = ""
    this.newPassword = ""
    this.confirmPassword = ""
    this.oldVisible = false
    this.newVisible = false
    this.confirmVisible = false
    this.checkOld = false
    this.checkNew = false
    this.checkConfirm = false
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        // 公共页头 — 22/600 标题 + 48dp 返回热区
        PageHeader({ title: resString('change_password') })

        Scroll(this.scroller) {
          Column() {
            this.FormLabel(resString('old_password'))
            PasswordFieldRow({
              placeholder: resString('error_old_password_empty'),
              focused: this.oldFocused,
              showPlain: this.oldVisible,
              onTextChange: (value: string) => {
                this.oldPassword = value
                this.checkOld = false
              },
              onFocusChange: (focused: boolean) => {
                this.oldFocused = focused
              },
              onTogglePlain: () => {
                this.oldVisible = !this.oldVisible
              }
            })
            if (this.checkOld) {
              this.FieldError(resString('invalid_password'))
            }

            Column().height(DesignDimens.SPACE_LG)

            this.FormLabel(resString('reset_new_password'))
            PasswordFieldRow({
              placeholder: resString('change_password_4'),
              focused: this.newFocused,
              showPlain: this.newVisible,
              onTextChange: (value: string) => {
                this.newPassword = value
                this.checkNew = false
                // 已确认过时改动新密码需复检一致性
                if (this.confirmPassword !== "") {
                  this.checkConfirm = this.confirmPassword !== value
                }
              },
              onFocusChange: (focused: boolean) => {
                this.newFocused = focused
              },
              onTogglePlain: () => {
                this.newVisible = !this.newVisible
              }
            })
            if (this.checkNew) {
              this.FieldError(resString('invalid_password'))
            }

            Column().height(DesignDimens.SPACE_LG)

            this.FormLabel(resString('confirm_new_password'))
            PasswordFieldRow({
              placeholder: resString('change_password_5'),
              focused: this.confirmFocused,
              showPlain: this.confirmVisible,
              onTextChange: (value: string) => {
                this.confirmPassword = value
                // 非空即校验一致性（空串留给提交时兜底）
                this.checkConfirm = value !== "" && value !== this.newPassword
              },
              onFocusChange: (focused: boolean) => {
                this.confirmFocused = focused
              },
              onTogglePlain: () => {
                this.confirmVisible = !this.confirmVisible
              }
            })
            if (this.checkConfirm) {
              this.FieldError(resString('change_password_6'))
            }

            // 修改成功后即登出回登录页，本页无后续动作，仅一个提交按钮
            AppButton({
              text: resString('confirm_change_password'),
              loading: this.submitting,
              click: () => {
                this.submit()
              }
            })
              .margin({ top: DesignDimens.SPACE_XL })
          }
          .width("100%")
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_SM,
            bottom: DesignDimens.SPACE_XL
          })
        }
        .layoutWeight(1)
        .scrollBar(BarState.Off)
        .align(Alignment.Top)
        .padding({ bottom: safeBottomVp() })
      }
      .width("100%")
      .height("100%")
      .padding({ top: safeTopVp() })
      .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    // 隐藏系统标题栏，避免与自绘 PageHeader 出现双返回按钮（对齐 Setting.ets）
    .hideTitleBar(true)
  }
}

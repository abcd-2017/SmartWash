import { RouteNames } from "../constant/RouteNames"
import { clearLoginState } from "../network/Axios"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontFeature, DesignFontWeight } from "../constant/DesignSystem"
import { getValue, setValue } from "../utils/StorageUtil"
import { pathStack } from "../utils/PathStackUtil"
import { logger } from "../utils/Logger"
import { AppButton, AppButtonVariant, AppCard, AppConfirmDialog, AppInfoDialog, PageHeader } from "../view/common/AppComponents"
import { getAppVersionName } from "../utils/AppInfoUtil"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import { notificationManager } from "@kit.NotificationKit"
import vibrator from "@ohos.vibrator"

// 触感反馈开关持久化 key（对齐安卓 HapticSettings.STORAGE_KEY 语义）
const HAPTIC_PREF_KEY: string = "haptic_enabled"

// 隐私与免责声明全文（对齐安卓 privacy_disclaimer_content）
const PRIVACY_DISCLAIMER: string =
  "本应用收集的信息与用途：\n\n" +
  "· 手机号与学号：仅用于账号注册、登录与校内身份识别；\n" +
  "· 学校与校园卡信息：用于寄存柜投递、取件与计费；\n" +
  "· 订单与余额数据：用于洗衣服务履约，余额仅存于平台账户；\n" +
  "· 观象台占卜记录：默认保存在本机数据库，解读请求经平台网关转发大模型，不含个人身份信息；\n" +
  "· 设备标识与崩溃日志：仅用于问题排查与热更新。\n\n" +
  "以上数据均传输与存储于平台自有服务器，不向任何第三方共享，不用于广告。你可在系统设置中随时撤回通知等权限，或联系客服注销账号并删除数据。\n\n" +
  "免责声明：观象台提供的卦象解读属传统文化娱乐内容，不构成任何现实决策建议，请理性看待。"

// ========== 设置页面（对齐安卓 SettingPage.kt）==========
@Builder
export function SettingBuilder() {
  Setting()
}

@Component
struct Setting {
  private scroller = new Scroller()

  // 消息通知真实授权态（对齐安卓 SettingPage.kt:100-124：组合期查一次 + ON_RESUME 重查）
  @State notificationGranted: boolean = false
  // 触感反馈开关（内存态即时生效 + preferences 异步落盘，对齐安卓 SettingViewModel）
  @State hapticEnabled: boolean = true

  // 退出登录弹窗（对齐安卓 AppConfirmDialog isDanger，统一使用 #23 产出的通用组件）
  private logoutDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: "提示",
      message: "是否要退出登录？",
      confirmText: "确认",
      cancelText: "取消",
      // 退出登录是不可逆操作 → 确认键走 dangerInk（对齐安卓 AppConfirmDialog(isDanger = true)）
      isDanger: true,
      onConfirm: () => {
        // 登出是不可逆的确认操作：先给中触，再清会话（对齐安卓 SettingPage.kt:291）
        this.performHaptic()
        // 低频关键动作成功：退出登录留 info（token 等凭据不落日志）
        logger.info('setting', '退出登录，清本地会话并跳转登录页')
        // 登出链路（对齐安卓 SettingViewModel.logout）：清本地 token + 清 role + 复位内存登录态，
        // 再置会话态 isLoggedIn=false —— Index 监听后把 Login 作为根内容渲染并清空页面栈，
        // Home 随之销毁（5s 订单轮询停止）。不再 push 登录页：旧写法会让 Home 存活，
        // 侧滑能回首页、重登停在旧 tab（问题 1/2）
        clearLoginState()
        pathStack.clear()
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  // 关于我们弹窗（对齐安卓 AppInfoDialog，版本号动态读取）
  private aboutDialogController: CustomDialogController = new CustomDialogController({
    builder: AppInfoDialog({
      title: "关于 SmartWash",
      message: `版本号：${getAppVersionName()}`
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  // 隐私与免责声明弹窗（对齐安卓 AppInfoDialog + privacy_disclaimer_content 全文）
  private privacyDialogController: CustomDialogController = new CustomDialogController({
    builder: AppInfoDialog({
      title: "隐私与免责声明",
      message: PRIVACY_DISCLAIMER
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  async aboutToAppear() {
    this.refreshNotification()
    // 触感反馈启动补读：覆盖冷启动后未预热就进设置页的窗口（对齐安卓 SettingViewModel.init）
    const stored = await getValue(HAPTIC_PREF_KEY, true) as boolean
    this.hapticEnabled = stored
  }

  /** 消息通知真实授权态（系统通知开关） */
  private refreshNotification(): void {
    try {
      this.notificationGranted = notificationManager.isNotificationEnabledSync()
    } catch (err) {
      this.notificationGranted = false
    }
  }

  /** 触感反馈开关：先改内存态（热路径即时生效），再异步落盘；开启瞬间给一次确认触感 */
  private toggleHaptic(): void {
    const newValue = !this.hapticEnabled
    this.hapticEnabled = newValue
    // 偏好开关切换（低频非关键动作，记 debug）
    logger.debug('setting', `触感反馈开关切换为 ${newValue ? "开" : "关"}`)
    setValue(HAPTIC_PREF_KEY, newValue)
    if (newValue) {
      this.performHaptic()
    }
  }

  /** 触感反馈（对齐安卓 performHaptic；不支持的设备静默忽略） */
  private performHaptic(): void {
    try {
      vibrator.startVibration({ type: "time", duration: 30 }, { id: 0, usage: "unknown" })
    } catch (err) {
      // 忽略不支持的设备
    }
  }

  build() {
    NavDestination() {
      Column() {
        // 公共页头 — 22/600 标题 + 48dp 返回热区 + 内置连击防抖（对齐安卓 PageHeader）
        PageHeader({ title: "设置" })

        // 滚动内容
        Scroll(this.scroller) {
          Column() {
            // ===== 通用 =====
            Text("通用")
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .width("100%")
              .margin({ top: DesignDimens.SECTION_SPACING, bottom: 8 })

            SettingGroupCard() {
              // 消息通知（真实授权态，行尾显示系统实际授予情况，点击进权限管理页）
              SettingRow({
                icon: $r('sys.symbol.bell'),
                containerColor: DesignColor.ICON_CONTAINER_ORANGE,
                iconColor: DesignColor.ICON_FOREGROUND_ORANGE,
                title: "消息通知",
                trailingValue: this.notificationGranted ? "已开启" : "未授权",
                onRowClick: () => {
                  pathStack.pushPathByName(RouteNames.PERMISSION_MANAGEMENT, null)
                }
              })

              Divider()
                .color(DesignColor.HAIRLINE)
                .strokeWidth(0.5)

              // 触感反馈（真实开关，内存态即时生效 + 异步落盘）
              SettingRow({
                icon: $r('sys.symbol.hand_tap'),
                containerColor: DesignColor.ICON_CONTAINER_TEAL,
                iconColor: DesignColor.ICON_FOREGROUND_TEAL,
                title: "触感反馈",
                trailingValue: this.hapticEnabled ? "已开启" : "已关闭",
                onRowClick: () => {
                  this.toggleHaptic()
                }
              })
            }

            // ===== 观象台 =====
            Text("观象台")
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .width("100%")
              .margin({ top: DesignDimens.SECTION_SPACING, bottom: 8 })

            SettingGroupCard() {
              SettingRow({
                icon: $r('sys.symbol.questionmark_circle'),
                containerColor: DesignColor.ICON_CONTAINER_PURPLE,
                iconColor: DesignColor.ICON_FOREGROUND_PURPLE,
                title: "隐私与免责声明",
                trailingValue: "",
                onRowClick: () => {
                  this.privacyDialogController.open()
                }
              })
            }

            // ===== 其他 =====
            Text("其他")
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .width("100%")
              .margin({ top: DesignDimens.SECTION_SPACING, bottom: 8 })

            SettingGroupCard() {
              // 权限管理入口（申请 / 跳系统设置都在权限页收口）
              SettingRow({
                icon: $r('sys.symbol.gearshape'),
                containerColor: DesignColor.ICON_CONTAINER_TEAL,
                iconColor: DesignColor.ICON_FOREGROUND_TEAL,
                title: "权限管理",
                trailingValue: "",
                onRowClick: () => {
                  pathStack.pushPathByName(RouteNames.PERMISSION_MANAGEMENT, null)
                }
              })

              Divider()
                .color(DesignColor.HAIRLINE)
                .strokeWidth(0.5)

              // 关于 SmartWash（行尾版本号 tnum，与关于弹窗同源）
              SettingRow({
                icon: $r('sys.symbol.info_circle'),
                containerColor: DesignColor.ICON_CONTAINER_TEAL,
                iconColor: DesignColor.ICON_FOREGROUND_TEAL,
                title: "关于 SmartWash",
                trailingValue: `v${getAppVersionName()}`,
                onRowClick: () => {
                  this.aboutDialogController.open()
                }
              })
            }

            // 退出登录 — 规范 §2.1 唯一按钮例外：次按钮变体（对齐安卓 AppButton(SECONDARY) + dangerInk）
            AppButton({
              text: "退出登录",
              variant: AppButtonVariant.SECONDARY,
              contentColor: DesignColor.DANGER_INK,
              click: () => {
                this.logoutDialogController.open()
              }
            })
            .margin({ top: 16, bottom: 24 })
          }
          .width("100%")
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        }
        .layoutWeight(1)
        .scrollBar(BarState.Off)
        // 内容不足一屏顶对齐 + 底部安全区避让（沉浸式）
        .align(Alignment.Top)
        .padding({ bottom: safeBottomVp() })
      }
      .width("100%")
      .height("100%")
      .padding({ top: safeTopVp() })
      .backgroundColor(DesignColor.BACKGROUND)
    }
    // 补齐：隐藏系统标题栏，避免与自绘 PageHeader 出现双返回按钮（问题6）
    .hideTitleBar(true)
    .onShown(() => {
      // 从权限页 / 系统设置返回后重查授权态（对齐安卓 ON_RESUME）
      this.refreshNotification()
    })
  }
}

/**
 * 设置行（对齐安卓 SettingRow：64dp 高 / ib32 / 14sp/500 标题 / 行尾值 / 0.5 hairline）。
 * 水平缩进由分组卡内边距统一承载，行自身不叠 horizontal padding。
 * 按压反馈自绘状态层（按下铺 surfaceVariant），不使用 Material ripple/state layer。
 */
@Component
struct SettingRow {
  @Prop icon: Resource = $r('sys.symbol.info_circle')
  @Prop containerColor: number = DesignColor.PRIMARY_LIGHT
  @Prop iconColor: number = DesignColor.PRIMARY
  @Prop title: string = ""
  @Prop trailingValue: string = ""
  onRowClick: () => void = () => {
  }
  @State pressed: boolean = false

  build() {
    Row() {
      // 图标容器 — 32vp，12vp 圆角，语义浅底 + 语义色前景图标
      Column() {
        SymbolGlyph(this.icon)
          .fontSize(20)
          .fontColor([this.iconColor])
      }
      .width(32)
      .height(32)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      .backgroundColor(this.containerColor)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)

      Text(this.title)
        .fontSize(DesignFontSize.BODY_SMALL)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .layoutWeight(1)
        .margin({ left: 12 })

      if (this.trailingValue !== "") {
        Text(this.trailingValue)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .fontFeature(DesignFontFeature.TNUM)
      }

      SymbolGlyph($r('sys.symbol.chevron_right'))
        .fontSize(20)
        .fontColor([DesignColor.TEXT_TERTIARY])
        .margin({ left: 4 })
    }
    .width("100%")
    .height(64)
    .alignItems(VerticalAlign.Center)
    .backgroundColor(this.pressed ? DesignColor.SURFACE_VARIANT : Color.Transparent)
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressed = true
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressed = false
      }
    })
    .onClick(() => {
      this.onRowClick()
    })
  }
}

/**
 * 设置分组卡（对齐安卓 SettingGroupCard：卡内边距仅水平 20，行铺满；
 * 卡高 = 行数 × 64，纵向不加内边距）。卡片本体复用组件层 AppCard 的唯一画法。
 */
@Component
struct SettingGroupCard {
  @BuilderParam content: () => void = emptyContent

  build() {
    AppCard({ paddingValue: 0 }) {
      Column() {
        this.content()
      }
      .width("100%")
      .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
    }
  }
}

// @BuilderParam 的默认空插槽（ArkTS 要求默认值必须是 @Builder 函数，不能用内联箭头函数）
@Builder
function emptyContent() {
}

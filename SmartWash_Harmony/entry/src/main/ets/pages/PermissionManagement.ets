import { DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { logger } from "../utils/Logger"
import { AppConfirmDialog, AppCard, PageHeader } from "../view/common/AppComponents"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import { notificationManager } from "@kit.NotificationKit"
import { common } from "@kit.AbilityKit"
import { promptAction } from "@kit.ArkUI"
import vibrator from "@ohos.vibrator"
import { getValue, setValue } from "../utils/StorageUtil"
import { ThemeRefresh } from '../view/common/ThemeRefresh'
import { resColor } from '../utils/ResourceColor'
import { resString } from '../utils/ResourceString'

// ========== 权限管理页（对齐安卓 PermissionManagementPage.kt）==========
//
// 本页只归集「用户可随时开关的权限与设置」：通知权限（未授权→申请；已授权→去系统设置关闭）
// 与触感反馈（端内开关，自设置页迁入）。鸿蒙无 APK 热更新（无「安装未知应用」）、无前台下载服务，
// 安装即授予的 normal 权限（网络访问等）不再列出，避免「展示当前授予状态」的误读。
// 授权态变更无任何回调，只能靠页面可见性兜底重查（对齐安卓 ON_RESUME）。

@Builder
export function PermissionManagementBuilder() {
  PermissionManagement()
}

// 触感反馈开关持久化 key（对齐安卓 HapticSettings.STORAGE_KEY 语义，自设置页迁入）
const HAPTIC_PREF_KEY: string = "haptic_enabled"

@Component
struct PermissionManagement {
  @State notificationGranted: boolean = false
  // 触感反馈开关（内存态即时生效 + preferences 异步落盘）
  @State hapticEnabled: boolean = true

  // 通知权限被拒且系统弹窗无法再拉起时，引导去系统设置
  private settingsDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: resString('tip'),
      message: resString('permission_management_1'),
      confirmText: resString('permission_management_2'),
      cancelText: resString('cancel'),
      onConfirm: () => {
        this.openNotificationSettings()
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  async aboutToAppear(): Promise<void> {
    this.refreshGranted()
    // 触感反馈开关回显（读取失败/未初始化按默认开启）
    const stored = await getValue(HAPTIC_PREF_KEY, true)
    this.hapticEnabled = stored === null ? true : stored === true
  }

  /** 全量重查授予态（页面可见性兜底，权限变更无回调） */
  private refreshGranted(): void {
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
    logger.debug('permissionManagement', `触感反馈开关切换为 ${newValue ? "开" : "关"}`)
    setValue(HAPTIC_PREF_KEY, newValue)
    if (newValue) {
      try {
        vibrator.startVibration({ type: "time", duration: 30 }, { id: 0, usage: "unknown" })
      } catch (err) {
        // 忽略不支持的设备
      }
    }
  }

  /** 申请通知权限：拉起系统授权弹窗，结束后重查；仍拒绝则引导去系统设置 */
  private requestNotification(): void {
    const context = getContext(this) as common.UIAbilityContext
    notificationManager.requestEnableNotification(context).then(() => {
      this.refreshGranted()
      if (!this.notificationGranted) {
        this.settingsDialogController.open()
      }
    }).catch(() => {
      // 业务拒绝留痕：用户拒绝授权或系统弹窗拉起失败（引导系统设置的成因）
      logger.warn('permissionManagement', '通知权限申请被拒或拉起失败，引导系统设置')
      this.settingsDialogController.open()
    })
  }

  /**
   * 打开本应用通知设置页（半模态），失败 toast 兜底。
   * 半模态窗口不触发 NavDestination onShown，关闭后必须主动重查授权态，
   * 否则页面停留在旧状态（实测：开启通知返回后仍显示「去授权」）。
   */
  private openNotificationSettings(): void {
    const context = getContext(this) as common.UIAbilityContext
    notificationManager.openNotificationSettings(context).then(() => {
      // 系统设置半模态关闭回调：重查通知授权态
      logger.debug('permissionManagement', '系统通知设置已关闭，重查授权态')
      this.refreshGranted()
    }).catch(() => {
      // 外部调用失败留痕：toast 已兜底，此处仅补摘要（不序列化 error）
      logger.warn('permissionManagement', '打开系统通知设置失败，已 toast 兜底')
      promptAction.showToast({ message: resString('permission_management_3') })
    })
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      // 沉浸式：根容器避让状态栏（内容位置不变，背景延伸）
      Column() {
        PageHeader({ title: resString('permission_management') })

        Scroll() {
          Column() {
            Text($r('app.string.general'))
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_SECONDARY'))
              .width('100%')
              // 分组标题顶部间距对齐安卓 Spacer(24dp)（D15）
              .margin({ top: DesignDimens.SPACE_XL, bottom: 8 })

            // 分组卡：卡内边距仅水平 20（行铺满，卡高 = 行数 × 72），对齐安卓 PermissionGroupCard
            AppCard({ paddingValue: 0 }) {
              Column() {
                PermissionRow({
                  icon: $r('sys.symbol.bell'),
                  containerColor: $r('app.color.ICON_CONTAINER_ORANGE'),
                  iconColor: $r('app.color.ICON_FOREGROUND_ORANGE'),
                  title: resString('permission_notification'),
                  // 鸿蒙语义文案（去掉安卓专属的 APK 下载 / Android 13+ 表述）
                  description: resString('permission_management_4'),
                  trailingValue: this.notificationGranted ? resString('permission_granted') : resString('permission_action_request'),
                  trailingColor: this.notificationGranted ? $r('app.color.SUCCESS') : $r('app.color.PRIMARY'),
                  showAction: true,
                  onRowClick: () => {
                    // 双向可达：未授权 → 拉起系统授权；已授权 → 去系统设置关闭（对齐安卓）
                    if (!this.notificationGranted) {
                      this.requestNotification()
                    } else {
                      this.openNotificationSettings()
                    }
                  }
                })

                Divider()
                  .color($r('app.color.HAIRLINE'))
                  .strokeWidth(0.5)

                // 触感反馈（端内开关，非系统权限；自设置页迁入，与通知同页归集）
                PermissionRow({
                  icon: $r('sys.symbol.hand_tap'),
                  containerColor: $r('app.color.ICON_CONTAINER_TEAL'),
                  iconColor: $r('app.color.ICON_FOREGROUND_TEAL'),
                  title: resString('permission_haptic'),
                  description: resString('permission_haptic_desc'),
                  trailingValue: this.hapticEnabled ? resString('lab_drum_cell_on') : resString('lab_drum_cell_off'),
                  trailingColor: $r('app.color.TEXT_SECONDARY'),
                  showAction: true,
                  onRowClick: () => {
                    this.toggleHaptic()
                  }
                })
              }
              .width('100%')
              .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
            }

            Text($r('app.string.permission_footer'))
              .fontSize(DesignFontSize.CAPTION)
              .fontColor($r('app.color.TEXT_SECONDARY'))
              .width('100%')
              .margin({ top: 16, bottom: 24 })
          }
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        }
        .layoutWeight(1)
        .scrollBar(BarState.Off)
        // 内容不足一屏时顶对齐（Scroll 默认居中，会把内容挤到屏幕中部）
        .align(Alignment.Top)
        .padding({ bottom: safeBottomVp() })
      }
      .width('100%')
      .height('100%')
      .padding({ top: safeTopVp() })
      .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
    .onShown(() => {
      // 从系统设置 / 授权弹窗返回后重查（对齐安卓 ON_RESUME）
      this.refreshGranted()
    })
  }
}

/**
 * 权限/设置行（对齐安卓 PermissionRow：72dp 高 / ib32 / 标题+描述 / 行尾状态+箭头）。
 * 用 @Component + @Prop 而非 @Builder：@Builder 按值参数首帧定格，
 * 系统设置返回后行尾状态变更不刷新（同 UserInfo 校园卡行已知坑）。
 * 行自身零水平 padding，水平缩进由分组卡内边距统一承载。
 */
@Component
struct PermissionRow {
  @Prop icon: Resource = $r('sys.symbol.bell')
  @Prop containerColor: ResourceColor = $r('app.color.ICON_CONTAINER_ORANGE')
  @Prop iconColor: ResourceColor = $r('app.color.ICON_FOREGROUND_ORANGE')
  @Prop title: string = ''
  @Prop description: string = ''
  // 行尾状态/动作文案与颜色由调用方按条目语义给定（对齐安卓 trailingText/trailingColor：
  // 权限用「已授权/去授权」，端内开关用「已开启/已关闭」）
  @Prop trailingValue: string = ''
  @Prop trailingColor: ResourceColor = $r('app.color.TEXT_SECONDARY')
  @Prop showAction: boolean = true
  onRowClick: () => void = () => {
  }

  build() {
    Row() {
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

      Column() {
        Text(this.title)
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontWeight(DesignFontWeight.NORMAL)
          .fontColor($r('app.color.TEXT_PRIMARY'))

        // 标题→描述无额外间距（对齐安卓：靠 bodySmall 行盒，D18）
        Text(this.description)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor($r('app.color.TEXT_SECONDARY'))
      }
      .layoutWeight(1)
      .margin({ left: 12 })
      .alignItems(HorizontalAlign.Start)

      // 非操作行状态文字贴内容右缘 0（对齐安卓，D17）；
      // 操作行的 4dp 间距由 chevron 左间距承载（对齐安卓 Spacer(4.dp)）
      Text(this.trailingValue)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(this.trailingColor)

      if (this.showAction) {
        SymbolGlyph($r('sys.symbol.chevron_right'))
          .fontSize(20)
          .fontColor([$r('app.color.TEXT_TERTIARY')])
          .margin({ left: 4 })
      }
    }
    .width('100%')
    .height(72)
    .alignItems(VerticalAlign.Center)
    .onClick(() => {
      if (this.showAction) {
        this.onRowClick()
      }
    })
  }
}

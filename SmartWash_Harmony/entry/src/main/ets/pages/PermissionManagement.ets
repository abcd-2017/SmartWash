import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { logger } from "../utils/Logger"
import { AppConfirmDialog, AppCard, PageHeader } from "../view/common/AppComponents"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import { notificationManager } from "@kit.NotificationKit"
import { abilityAccessCtrl, bundleManager, common } from "@kit.AbilityKit"
import { promptAction } from "@kit.ArkUI"

// ========== 权限管理页（对齐安卓 PermissionManagementPage.kt）==========
//
// 安卓页列「通知 / 安装未知应用 / 网络访问 / 后台下载服务」四行；鸿蒙端权限模型不同：
// 无 APK 热更新（无「安装未知应用」）、无前台下载服务，故只列两项——
// 通知（用户可关、可拉起系统授权）与网络（normal 权限、安装即授予）。
// 授权态变更无任何回调，只能靠页面可见性兜底重查（对齐安卓 ON_RESUME）。

@Builder
export function PermissionManagementBuilder() {
  PermissionManagement()
}

@Component
struct PermissionManagement {
  @State notificationGranted: boolean = false
  @State internetGranted: boolean = true

  // 通知权限被拒且系统弹窗无法再拉起时，引导去系统设置
  private settingsDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: "提示",
      message: "通知权限未开启，是否前往系统设置开启？",
      confirmText: "去设置",
      cancelText: "取消",
      onConfirm: () => {
        this.openNotificationSettings()
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  aboutToAppear(): void {
    this.refreshGranted()
  }

  /** 全量重查授予态（页面可见性兜底，权限变更无回调） */
  private refreshGranted(): void {
    try {
      this.notificationGranted = notificationManager.isNotificationEnabledSync()
    } catch (err) {
      this.notificationGranted = false
    }
    this.internetGranted = this.checkInternetGranted()
  }

  /** 网络访问为 normal 权限：经 accessToken 校验真实授予态（查询失败按已授予处理） */
  private checkInternetGranted(): boolean {
    try {
      const bundleInfo = bundleManager.getBundleInfoForSelfSync(bundleManager.BundleFlag.GET_BUNDLE_INFO_DEFAULT)
      const tokenId = bundleInfo.appInfo.accessTokenId
      const atManager = abilityAccessCtrl.createAtManager()
      return atManager.checkAccessTokenSync(tokenId, 'ohos.permission.INTERNET') ===
        abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED
    } catch (err) {
      return true
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
      // 系统设置半模态关闭回调：重查通知 / 网络授予态
      logger.debug('permissionManagement', '系统通知设置已关闭，重查授权态')
      this.refreshGranted()
    }).catch(() => {
      // 外部调用失败留痕：toast 已兜底，此处仅补摘要（不序列化 error）
      logger.warn('permissionManagement', '打开系统通知设置失败，已 toast 兜底')
      promptAction.showToast({ message: "无法打开系统设置" })
    })
  }

  build() {
    NavDestination() {
      // 沉浸式：根容器避让状态栏（内容位置不变，背景延伸）
      Column() {
        PageHeader({ title: "权限管理" })

        Scroll() {
          Column() {
            Text("通用")
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .width('100%')
              // 分组标题顶部间距对齐安卓 Spacer(24dp)（D15）
              .margin({ top: DesignDimens.SPACE_XL, bottom: 8 })

            // 分组卡：卡内边距仅水平 20（行铺满，卡高 = 行数 × 72），对齐安卓 PermissionGroupCard
            AppCard({ paddingValue: 0 }) {
              Column() {
                PermissionRow({
                  icon: $r('sys.symbol.bell'),
                  containerColor: DesignColor.ICON_CONTAINER_ORANGE,
                  iconColor: DesignColor.ICON_FOREGROUND_ORANGE,
                  title: "通知权限",
                  // 文案对齐安卓 permission_notification_desc（D14）
                  description: "APK 下载进度与更新提醒（Android 13+ 需授权）",
                  granted: this.notificationGranted,
                  showAction: true,
                  onRowClick: () => {
                    if (!this.notificationGranted) {
                      this.requestNotification()
                    }
                  }
                })

                Divider()
                  .color(DesignColor.HAIRLINE)
                  .strokeWidth(0.5)

                PermissionRow({
                  icon: $r('sys.symbol.wifi'),
                  containerColor: DesignColor.ICON_CONTAINER_BLUE,
                  iconColor: DesignColor.ICON_FOREGROUND_BLUE,
                  title: "网络访问",
                  description: "与服务端同步订单、余额与洗衣项目（安装即授予）",
                  granted: this.internetGranted,
                  showAction: false,
                  onRowClick: () => {
                  }
                })
              }
              .width('100%')
              .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
            }

            Text("权限仅用于上述功能所需的最小场景；拒绝某项权限只会影响对应功能，不影响应用正常使用。")
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.TEXT_SECONDARY)
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
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
    .onShown(() => {
      // 从系统设置 / 授权弹窗返回后重查（对齐安卓 ON_RESUME）
      this.refreshGranted()
    })
  }
}

/**
 * 权限行（对齐安卓 PermissionRow：72dp 高 / ib32 / 标题+描述 / 行尾状态+箭头）。
 * 用 @Component + @Prop 而非 @Builder：@Builder 按值参数首帧定格，
 * 系统设置返回后 granted 变更不刷新（同 UserInfo 校园卡行已知坑）。
 * 行自身零水平 padding，水平缩进由分组卡内边距统一承载。
 */
@Component
struct PermissionRow {
  @Prop icon: Resource = $r('sys.symbol.bell')
  @Prop containerColor: number = DesignColor.ICON_CONTAINER_ORANGE
  @Prop iconColor: number = DesignColor.ICON_FOREGROUND_ORANGE
  @Prop title: string = ''
  @Prop description: string = ''
  @Prop granted: boolean = false
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
          .fontColor(DesignColor.TEXT_PRIMARY)

        // 标题→描述无额外间距（对齐安卓：靠 bodySmall 行盒，D18）
        Text(this.description)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
      }
      .layoutWeight(1)
      .margin({ left: 12 })
      .alignItems(HorizontalAlign.Start)

      // 非操作行状态文字贴内容右缘 0（对齐安卓，D17）；
      // 操作行的 4dp 间距由 chevron 左间距承载（对齐安卓 Spacer(4.dp)）
      Text(this.granted ? "已授权" : "去授权")
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(this.granted ? DesignColor.SUCCESS : DesignColor.PRIMARY)

      if (this.showAction) {
        SymbolGlyph($r('sys.symbol.chevron_right'))
          .fontSize(20)
          .fontColor([DesignColor.TEXT_TERTIARY])
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

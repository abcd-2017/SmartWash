import { RouteNames } from "../constant/RouteNames"
import { clearLoginState } from "../network/Axios"
import { DesignDimens, DesignFontSize, DesignFontFeature, DesignFontWeight } from "../constant/DesignSystem"
import { pathStack } from "../utils/PathStackUtil"
import { logger } from "../utils/Logger"
import { AppButton, AppButtonVariant, AppCard, AppConfirmDialog, AppInfoDialog, PageHeader } from "../view/common/AppComponents"
import { getAppVersionCode, getAppVersionName } from "../utils/AppInfoUtil"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import { isPickupReminderEnabled, setPickupReminderEnabled } from "../utils/ReminderService"
import { HttpStatusCode } from "@ohos/axios"
import { getAppVersion } from "../network/api/AppVersionApi"
import { showToastSafety } from "../utils/ToastUtil"
import { getDivUserApiConfig } from "../feature/divination/network/DivSettingsApi"
import vibrator from "@ohos.vibrator"
import { ThemeRefresh } from '../view/common/ThemeRefresh'
import { resString } from '../utils/ResourceString'

// 隐私与免责声明全文（对齐安卓 privacy_disclaimer_content）
const PRIVACY_DISCLAIMER: string =
  resString('setting_1') +
  resString('setting_2') +
  resString('setting_3') +
  resString('setting_4') +
  resString('setting_5') +
  resString('setting_6') +
  resString('setting_7') +
  resString('setting_8')

// 平台总开关关闭（GET settings/api 下发 divUseEnabled=0）的入口行提示（入口置灰展示）
function BYOK_DISABLED_NOTICE(): string { return resString('div_interpret_platform_disabled') }

// 未配置引导文案（零兜底：未配置模型不能 AI 解读，替代旧「未配置，使用平台默认模型」）
function BYOK_UNCONFIGURED_NOTICE(): string { return resString('api_config_unconfigured') }

// ========== 设置页面（对齐安卓 SettingPage.kt）==========
@Builder
export function SettingBuilder() {
  Setting()
}

@Component
struct Setting {
  private scroller = new Scroller()

  // 取件提醒开关（内存态即时生效 + ReminderService 异步落盘；关闭即全量撤销未触发提醒）
  @State pickupReminderEnabled: boolean = true
  // 检查更新进行中标记：拦截连点，避免重复拉取版本接口
  @State checkingUpdate: boolean = false
  // 「发现新版本」弹窗内容（open 前赋值，弹窗以当次值初始化）
  @State updateDialogMessage: string = ""

  // ===== 解读模型（BYOK）入口行回显态：仅从后端 GET 拉取展示，端上不持久化 key（key 只在服务端密文）=====
  // 首次 GET 是否已返回（返回前行尾不显示摘要，避免加载中误显「未配置」）
  @State configLoaded: boolean = false
  @State loadingConfig: boolean = false
  @State hasApiConfig: boolean = false
  @State configVerified: boolean = false
  // 观星台 AI 解读总开关（divUseEnabled===0 关闭）：入口置灰 + 不可进配置页；
  // =1 / 字段缺失（旧响应兼容）均视为开启，行为不变
  @State byokDisabled: boolean = false

  // 退出登录弹窗（对齐安卓 AppConfirmDialog isDanger，统一使用 #23 产出的通用组件）
  private logoutDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: resString('tip'),
      message: resString('confirm_logout'),
      confirmText: resString('confirm'),
      cancelText: resString('cancel'),
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
      title: resString('about_smartwash'),
      message: `版本号：${getAppVersionName()}`
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  // 隐私与免责声明弹窗（对齐安卓 AppInfoDialog + privacy_disclaimer_content 全文）
  private privacyDialogController: CustomDialogController = new CustomDialogController({
    builder: AppInfoDialog({
      title: resString('privacy_disclaimer'),
      message: PRIVACY_DISCLAIMER
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  // 「发现新版本」弹窗（对齐安卓手动检查更新的版本提示；内容由检查结果动态注入）
  private updateDialogController: CustomDialogController = new CustomDialogController({
    builder: AppInfoDialog({
      title: resString('new_version_available'),
      message: this.updateDialogMessage
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  async aboutToAppear() {
    // 解读模型配置拉取：端上不持久化，进页即从后端回显掩码态（#81）
    this.loadApiConfig()
    // 取件提醒开关回显（读取失败/未初始化按默认开启，见 ReminderService.isPickupReminderEnabled）
    this.pickupReminderEnabled = await isPickupReminderEnabled()
  }

  /**
   * 取件提醒开关：先改内存态（行尾值即时翻转），再经 ReminderService 异步落盘；
   * 关闭时服务内部全量撤销未触发提醒（fire-and-forget，撤销失败仅日志不影响开关态）。
   */
  private togglePickupReminder(): void {
    const newValue = !this.pickupReminderEnabled
    this.pickupReminderEnabled = newValue
    logger.debug('setting', `取件提醒开关切换为 ${newValue ? "开" : "关"}`)
    setPickupReminderEnabled(newValue)
  }

  /** 触感反馈（对齐安卓 performHaptic；不支持的设备静默忽略） */
  private performHaptic(): void {
    try {
      vibrator.startVibration({ type: "time", duration: 30 }, { id: 0, usage: "unknown" })
    } catch (err) {
      // 忽略不支持的设备
    }
  }

  /**
   * 检查更新（对齐安卓 UpdateViewModel.checkForUpdate + UpdateFlow 手动检查）：
   * 拉取服务端最新版本，与本地 versionCode 比较——远程更高则弹窗告知新版本，否则 toast 已是最新。
   * 鸿蒙无 APK 热更新链路，发现新版本仅告知版本信息，不触发下载。
   */
  private async checkUpdate(): Promise<void> {
    if (this.checkingUpdate) {
      return
    }
    this.checkingUpdate = true
    try {
      const res = await getAppVersion()
      if (res.code === HttpStatusCode.Ok && res.data !== null) {
        const remote = res.data
        const remoteCode = remote.versionCode ?? 0
        if (remoteCode > getAppVersionCode()) {
          const versionText = remote.versionName ?? ""
          const changelog = remote.changelog ?? ""
          this.updateDialogMessage = `最新版本：v${versionText}\n${changelog}`
          // 低频关键动作：检查到新版本留 info（仅记版本号，不含敏感信息）
          logger.info('setting', `检查更新发现新版本 versionName=${versionText}`)
          this.updateDialogController.open()
        } else {
          showToastSafety(resString('already_latest'))
        }
      } else {
        // 业务拒绝留痕：版本信息缺失/读取失败（仅记 code/message 摘要）
        logger.warn('setting', `检查更新失败 code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, resString('update_check_failed'))
      }
    } catch (err) {
      // 外部调用失败（网络异常）：error 留痕，只记 message 摘要，不序列化完整 error
      logger.error('setting', '检查更新请求异常', err instanceof Error ? err.message : "")
      showToastSafety(resString('setting_18'))
    } finally {
      this.checkingUpdate = false
    }
  }

  /**
   * 401 会话态判断：Axios 拦截器处理 401 后已清内存登录态（isLoggedIn=false），
   * 此时调用方不再叠加「网络异常」类 toast（既有 401 语义收口，避免双 toast 误导）。
   */
  private isSessionActive(): boolean {
    return AppStorage.get<boolean>("isLoggedIn") !== false
  }

  /**
   * 拉取解读模型配置回显（仅掩码，端上不持久化）。
   * 业务失败/网络异常静默留痕不打断设置页首屏（401 已由 Axios 拦截器统一处理）。
   */
  private async loadApiConfig(): Promise<void> {
    if (this.loadingConfig) {
      return
    }
    this.loadingConfig = true
    try {
      const res = await getDivUserApiConfig()
      if (res.code === HttpStatusCode.Ok) {
        const config = res.data
        this.hasApiConfig = config !== null
        this.configVerified = config?.verified === 1
        // 观星台 AI 解读总开关：仅 === 0 关闭；1 / undefined / null（旧响应）均视为开启（兼容）
        this.byokDisabled = config !== null && config.divUseEnabled === 0
        this.configLoaded = true
        // 回显仅掩码，日志不含 baseUrl/model/key（敏感红线：明文不留存不进日志）
        logger.debug('setting', `解读模型配置回显 hasConfig=${config !== null} verified=${this.configVerified}`)
      } else {
        logger.warn('setting', `解读模型配置获取失败 code=${res.code} message=${res.message ?? ""}`)
      }
    } catch (err) {
      logger.error('setting', '解读模型配置获取异常', err instanceof Error ? err.message : "")
    } finally {
      this.loadingConfig = false
    }
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        // 公共页头 — 22/600 标题 + 48dp 返回热区 + 内置连击防抖（对齐安卓 PageHeader）
        PageHeader({ title: resString('settings') })

        // 滚动内容
        Scroll(this.scroller) {
          Column() {
            // ===== 通用 =====
            Text($r('app.string.general'))
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_SECONDARY'))
              .width("100%")
              .margin({ top: DesignDimens.SECTION_SPACING, bottom: 8 })

            SettingGroupCard() {
              // 取件提醒（真实开关，调研候选 2）：订单进入待取件后 30 分钟本地提醒取件；
              // 关闭即撤销全部未触发提醒，重新开启后列表/详情同步自动补登记
              SettingRow({
                icon: $r('sys.symbol.hourglass'),
                containerColor: $r('app.color.ICON_CONTAINER_BLUE'),
                iconColor: $r('app.color.ICON_FOREGROUND_BLUE'),
                title: resString('setting_33'),
                trailingValue: this.pickupReminderEnabled ? resString('lab_drum_cell_on') : resString('lab_drum_cell_off'),
                onRowClick: () => {
                  this.togglePickupReminder()
                }
              })
            }

            // ===== 观象台 =====
            Text($r('app.string.workbench_divination_name'))
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_SECONDARY'))
              .width("100%")
              .margin({ top: DesignDimens.SECTION_SPACING, bottom: 8 })

            // 解读模型（BYOK）配置入口（issue #79；对齐安卓尾注精简版）
            SettingGroupCard() {
              // 已配置 = 「已配置」+「已验证」徽标；未配置 = 「未配置」；
              // divUseEnabled=0（总开关关闭）= 置灰提示不可进配置页。
              // 配置仅从后端 GET 回显，端上不持久化 key；编辑在独立配置页（ApiConfigPage）。
              ModelConfigRow({
                icon: $r('sys.symbol.key'),
                containerColor: $r('app.color.ICON_CONTAINER_PURPLE'),
                iconColor: $r('app.color.ICON_FOREGROUND_PURPLE'),
                title: resString('api_config_title'),
                hasConfig: this.configLoaded && this.hasApiConfig,
                verified: this.configVerified,
                loading: this.loadingConfig,
                disabled: this.byokDisabled,
                onRowClick: () => {
                  pathStack.pushPathByName(RouteNames.API_CONFIG, null)
                }
              })

              Divider()
                .color($r('app.color.HAIRLINE'))
                .strokeWidth(0.5)

              SettingRow({
                icon: $r('sys.symbol.questionmark_circle'),
                containerColor: $r('app.color.ICON_CONTAINER_PURPLE'),
                iconColor: $r('app.color.ICON_FOREGROUND_PURPLE'),
                title: resString('privacy_disclaimer'),
                trailingValue: "",
                onRowClick: () => {
                  this.privacyDialogController.open()
                }
              })
            }

            // ===== 其他 =====
            Text($r('app.string.div_category_other'))
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_SECONDARY'))
              .width("100%")
              .margin({ top: DesignDimens.SECTION_SPACING, bottom: 8 })

            SettingGroupCard() {
              // 检查更新（对齐安卓「其他」组 CheckUpdateSettingRow，64dp 行 + 行尾当前版本号）
              // 图标对齐安卓 Icons.Default.SystemUpdate（下载/更新语义）取 sys.symbol.download
              SettingRow({
                icon: $r('sys.symbol.download'),
                containerColor: $r('app.color.ICON_CONTAINER_TEAL'),
                iconColor: $r('app.color.ICON_FOREGROUND_TEAL'),
                title: resString('check_update'),
                trailingValue: `v${getAppVersionName()}`,
                // 检查中显示行尾 spinner（checkingUpdate 同时拦截连点，见 checkUpdate 首行）
                loading: this.checkingUpdate,
                onRowClick: () => {
                  this.checkUpdate()
                }
              })

              Divider()
                .color($r('app.color.HAIRLINE'))
                .strokeWidth(0.5)

              // 修改密码（验证原密码后改密，成功后登出重登，账号安全域入口）
              SettingRow({
                icon: $r('sys.symbol.lock'),
                containerColor: $r('app.color.ICON_CONTAINER_TEAL'),
                iconColor: $r('app.color.ICON_FOREGROUND_TEAL'),
                title: resString('change_password'),
                trailingValue: "",
                onRowClick: () => {
                  pathStack.pushPathByName(RouteNames.CHANGE_PASSWORD, null)
                }
              })

              Divider()
                .color($r('app.color.HAIRLINE'))
                .strokeWidth(0.5)

              // 权限管理入口（申请 / 跳系统设置都在权限页收口）
              // 图标对齐安卓 SettingPage 的 AdminPanelSettings（盾牌 + 用户徽标）取 sys.symbol.person_shield
              SettingRow({
                icon: $r('sys.symbol.person_shield'),
                containerColor: $r('app.color.ICON_CONTAINER_TEAL'),
                iconColor: $r('app.color.ICON_FOREGROUND_TEAL'),
                title: resString('permission_management'),
                trailingValue: "",
                onRowClick: () => {
                  pathStack.pushPathByName(RouteNames.PERMISSION_MANAGEMENT, null)
                }
              })

              Divider()
                .color($r('app.color.HAIRLINE'))
                .strokeWidth(0.5)

              // 关于 SmartWash（行尾版本号 tnum，与关于弹窗同源）
              SettingRow({
                icon: $r('sys.symbol.info_circle'),
                containerColor: $r('app.color.ICON_CONTAINER_TEAL'),
                iconColor: $r('app.color.ICON_FOREGROUND_TEAL'),
                title: resString('about_smartwash'),
                trailingValue: `v${getAppVersionName()}`,
                onRowClick: () => {
                  this.aboutDialogController.open()
                }
              })
            }

            // 退出登录 — 规范 §2.1 唯一按钮例外：次按钮变体（对齐安卓 AppButton(SECONDARY) + dangerInk）
            AppButton({
              text: resString('logout'),
              variant: AppButtonVariant.SECONDARY,
              contentColor: $r('app.color.DANGER_INK'),
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
      .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    // 补齐：隐藏系统标题栏，避免与自绘 PageHeader 出现双返回按钮（问题6）
    .hideTitleBar(true)
    .onShown(() => {
      // 返回本页时重拉解读模型配置（在配置页改动后回显保持新鲜；loadingConfig 挡并发）
      this.loadApiConfig()
    })
  }
}

/**
 * 设置行（对齐安卓 SettingRow：64dp 高 / ib32 / 14sp/400 标题 / 行尾值 / 0.5 hairline）。
 * 水平缩进由分组卡内边距统一承载，行自身不叠 horizontal padding。
 * 按压反馈自绘状态层（按下铺 surfaceVariant），不使用 Material ripple/state layer。
 */
@Component
struct SettingRow {
  @Prop icon: Resource = $r('sys.symbol.info_circle')
  @Prop containerColor: ResourceColor = $r('app.color.PRIMARY_LIGHT')
  @Prop iconColor: ResourceColor = $r('app.color.PRIMARY')
  @Prop title: string = ""
  @Prop trailingValue: string = ""
  // 行尾加载态（检查更新等）：为 true 时以 spinner 替代行尾文案（对齐安卓）
  @Prop loading: boolean = false
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
        .fontWeight(DesignFontWeight.NORMAL)
        .fontColor($r('app.color.TEXT_PRIMARY'))
        .layoutWeight(1)
        .margin({ left: 12 })

      if (this.loading) {
        // 行尾加载态：spinner 替代版本号（对齐安卓检查更新反馈）
        LoadingProgress()
          .width(16)
          .height(16)
          .color($r('app.color.TEXT_SECONDARY'))
      } else if (this.trailingValue !== "") {
        Text(this.trailingValue)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .fontFeature(DesignFontFeature.TNUM)
          // 单行截断防溢出（标题有 layoutWeight(1) 优先收缩）
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .constraintSize({ maxWidth: '55%' })
      }

      SymbolGlyph($r('sys.symbol.chevron_right'))
        .fontSize(20)
        .fontColor([$r('app.color.TEXT_TERTIARY')])
        .margin({ left: 4 })
    }
    .width("100%")
    .height(64)
    .alignItems(VerticalAlign.Center)
    .backgroundColor(this.pressed ? $r('app.color.SURFACE_VARIANT') : Color.Transparent)
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
 * 解读模型入口行（对齐安卓 SettingRow 尾注精简版）：
 * 已配置 = 「已配置」+「已验证」徽标；未配置 = 「未配置」；
 * 平台总开关关闭（divUseEnabled=0）= 置灰 + 提示「观星台 AI 解读暂未开放」，不可点击进入配置页。
 * 其余视觉与 SettingRow 一致（64dp 高 / ib32 图标容器 / 按压铺 surfaceVariant）。
 */
@Component
struct ModelConfigRow {
  @Prop icon: Resource = $r('sys.symbol.info_circle')
  @Prop containerColor: ResourceColor = $r('app.color.PRIMARY_LIGHT')
  @Prop iconColor: ResourceColor = $r('app.color.PRIMARY')
  @Prop title: string = ""
  @Prop hasConfig: boolean = false
  @Prop verified: boolean = false
  @Prop loading: boolean = false
  // 平台总开关关闭（divUseEnabled=0）：整行置灰（降透明 0.5，对齐 AppButton 禁用惯例）、
  // 去 chevron、不响应点击
  @Prop disabled: boolean = false
  onRowClick: () => void = () => {
  }
  @State pressed: boolean = false

  build() {
    Row() {
      // 图标容器 — 32vp，12vp 圆角（同 SettingRow）
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
        .fontWeight(DesignFontWeight.NORMAL)
        .fontColor($r('app.color.TEXT_PRIMARY'))
        .layoutWeight(1)
        .margin({ left: 12 })
        // 单行省略：尾注较宽时标题不得折行（对齐安卓 2026-11-12 换行缺陷修复）
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })

      if (this.loading) {
        // 加载中：spinner 占位（对齐安卓行内加载反馈）
        LoadingProgress()
          .width(16)
          .height(16)
          .color($r('app.color.TEXT_SECONDARY'))
      } else if (this.disabled) {
        // 平台总开关关闭：提示文案（入口置灰分支）
        Text(BYOK_DISABLED_NOTICE())
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .maxLines(1)
      } else if (this.hasConfig) {
        // 已配置：状态词 + 「已验证」徽标（模型名/掩码的详细回显在配置页）
        Row({ space: 4 }) {
          Text(resString('api_config_configured'))
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .maxLines(1)
          if (this.verified) {
            VerifiedBadge()
          }
        }
        .alignItems(VerticalAlign.Center)
      } else {
        // 未配置引导文案（零兜底口径：无平台默认模型，需配置后才能 AI 解读）
        Text(BYOK_UNCONFIGURED_NOTICE())
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .maxLines(1)
      }

      // chevron 是「可进入」的视觉信号：置灰态不渲染
      if (!this.disabled) {
        SymbolGlyph($r('sys.symbol.chevron_right'))
          .fontSize(20)
          .fontColor([$r('app.color.TEXT_TERTIARY')])
          .margin({ left: 4 })
      }
    }
    .width("100%")
    .height(64)
    .alignItems(VerticalAlign.Center)
    // 置灰：整行降透明（对齐 AppButton 禁用降透明惯例），内容灰化弱化可点预期
    .opacity(this.disabled ? 0.5 : 1)
    .backgroundColor(this.pressed ? $r('app.color.SURFACE_VARIANT') : Color.Transparent)
    .onTouch((event: TouchEvent) => {
      // 置灰行不播按压反馈
      if (this.disabled) {
        return
      }
      if (event.type === TouchType.Down) {
        this.pressed = true
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressed = false
      }
    })
    .onClick(() => {
      if (this.disabled) {
        return
      }
      this.onRowClick()
    })
  }
}

/**
 * 「已验证」徽标 — SUCCESS_CONTAINER 底 + ON_SUCCESS_CONTAINER 字的胶囊小标（对齐安卓 VerifiedBadge）。
 */
@Component
struct VerifiedBadge {
  build() {
    Row({ space: 2 }) {
      SymbolGlyph($r('sys.symbol.checkmark'))
        .fontSize(10)
        .fontColor([$r('app.color.ON_SUCCESS_CONTAINER')])
      Text(resString('api_config_verified'))
        .fontSize(10)
        .fontColor($r('app.color.ON_SUCCESS_CONTAINER'))
    }
    .padding({ left: 6, right: 6, top: 2, bottom: 2 })
    .borderRadius(DesignDimens.RADIUS_FULL)
    .backgroundColor($r('app.color.SUCCESS_CONTAINER'))
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

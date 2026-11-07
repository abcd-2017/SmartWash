import { RouteNames } from "../constant/RouteNames"
import { clearLoginState } from "../network/Axios"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontFeature, DesignFontWeight } from "../constant/DesignSystem"
import { getValue, setValue } from "../utils/StorageUtil"
import { pathStack } from "../utils/PathStackUtil"
import { logger } from "../utils/Logger"
import { AppButton, AppButtonVariant, AppCard, AppConfirmDialog, AppInfoDialog, PageHeader } from "../view/common/AppComponents"
import { getAppVersionCode, getAppVersionName } from "../utils/AppInfoUtil"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import { notificationManager } from "@kit.NotificationKit"
import { common } from "@kit.AbilityKit"
import { promptAction } from "@kit.ArkUI"
import { HttpStatusCode } from "@ohos/axios"
import { getAppVersion } from "../network/api/AppVersionApi"
import { showToastSafety } from "../utils/ToastUtil"
import {
  DivModelVo,
  SaveDivUserApiForm,
  deleteDivUserApiConfig,
  getDivModels,
  getDivUserApiConfig,
  saveDivUserApiConfig,
  testDivUserApiConfig
} from "../feature/divination/network/DivSettingsApi"
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

// 解读模型（BYOK）说明文案（对齐安卓 #79；与上方隐私声明「解读请求经平台网关转发大模型」口径一致）
const BYOK_NOTICE: string =
  "API Key 经平台网关加密存储，仅用于后端转发解读请求；不配置则由平台默认模型解读；支持任意 OpenAI 兼容接入点。"

// 平台关闭 BYOK 的入口行提示（后端 GET settings/api 下发 byokEnabled=0 时置灰展示）
const BYOK_DISABLED_NOTICE: string = "平台已关闭自带模型"

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
  // 检查更新进行中标记：拦截连点，避免重复拉取版本接口
  @State checkingUpdate: boolean = false
  // 「发现新版本」弹窗内容（open 前赋值，弹窗以当次值初始化）
  @State updateDialogMessage: string = ""

  // ===== 解读模型（BYOK）配置态（#81）：仅从后端 GET 拉取展示，端上不持久化 key（key 只在服务端密文）=====
  // 首次 GET 是否已返回（返回前行尾不显示摘要，避免加载中误显「未配置」）
  @State configLoaded: boolean = false
  @State loadingConfig: boolean = false
  @State hasApiConfig: boolean = false
  // 行尾摘要字段（对齐安卓两行 trailing：model + 徽标 / mask）
  @State configModel: string = ""
  @State configMask: string = ""
  @State configVerified: boolean = false
  // 平台级 BYOK 开关（byokEnabled===0 关闭）：入口置灰 + 不可进编辑层；
  // =1 / 字段缺失（旧响应兼容）均视为开启，行为不变
  @State byokDisabled: boolean = false
  // 编辑态预填源（编辑弹层打开时回填；key 不回填，后端仅回掩码）
  @State configBaseUrl: string = ""

  // ===== 解读模型编辑半模态态：表单值只在内存 @State，不落任何本地存储 =====
  @State showModelSheet: boolean = false
  @State editBaseUrl: string = ""
  @State editModel: string = ""
  @State editApiKey: string = ""
  @State showApiKeyPlain: boolean = false
  @State testing: boolean = false
  @State saving: boolean = false
  @State deleting: boolean = false
  // 「测试连接」即时反馈（空串 = 无反馈；ok/fail 决定图标与颜色）
  @State testFeedback: string = ""
  @State testFeedbackOk: boolean = false
  // 平台模型目录建议（拉取一次缓存；BYOK 不限于该目录；折叠展开交互对齐安卓 #79）
  @State platformModels: Array<DivModelVo> = []
  @State modelsLoaded: boolean = false
  @State modelsExpanded: boolean = false
  // 目录拉取成功但为空（显示「暂无平台模型目录」，对齐安卓 api_config_model_catalog_empty）
  @State modelsEmpty: boolean = false
  // 输入框聚焦态（聚焦描边主色，SubscriptionEditPage 同款）
  @State baseUrlFocused: boolean = false
  @State modelFocused: boolean = false
  @State apiKeyFocused: boolean = false

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

  // 删除解读模型配置二次确认弹窗（对齐安卓 #79「删除配置」需确认；不可逆操作走 dangerInk）
  private deleteConfigDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: "删除解读模型配置？",
      message: "删除后将恢复使用平台默认模型解读，已保存的密钥将从服务端清除。",
      confirmText: "删除",
      cancelText: "取消",
      isDanger: true,
      onConfirm: () => {
        // 不可逆确认操作：先给中触再执行（对齐退出登录弹窗）
        this.performHaptic()
        this.deleteConfig()
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  // 「发现新版本」弹窗（对齐安卓手动检查更新的版本提示；内容由检查结果动态注入）
  private updateDialogController: CustomDialogController = new CustomDialogController({
    builder: AppInfoDialog({
      title: "发现新版本",
      message: this.updateDialogMessage
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  async aboutToAppear() {
    this.refreshNotification()
    // 解读模型配置拉取：端上不持久化，进页即从后端回显掩码态（#81）
    this.loadApiConfig()
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

  /**
   * 打开系统应用通知设置（对齐安卓 SettingPage.kt openSystemNotificationSettings，P-1501）：
   * 授权 / 去关闭都在系统页完成，返回后由 onShown 重查刷新行内状态，不再进权限管理页。
   */
  private openNotificationSettings(): void {
    const context = getContext(this) as common.UIAbilityContext
    notificationManager.openNotificationSettings(context).then(() => {
      // 系统设置半模态关闭回调：重查通知授权态（半模态不触发 NavDestination onShown）
      logger.debug('setting', '系统通知设置已关闭，重查授权态')
      this.refreshNotification()
    }).catch(() => {
      // 外部调用失败留痕：toast 已兜底，此处仅补摘要（不序列化 error）
      logger.warn('setting', '打开系统通知设置失败，已 toast 兜底')
      promptAction.showToast({ message: "无法打开系统设置" })
    })
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
          showToastSafety("当前已是最新版本")
        }
      } else {
        // 业务拒绝留痕：版本信息缺失/读取失败（仅记 code/message 摘要）
        logger.warn('setting', `检查更新失败 code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, "检查更新失败")
      }
    } catch (err) {
      // 外部调用失败（网络异常）：error 留痕，只记 message 摘要，不序列化完整 error
      logger.error('setting', '检查更新请求异常', err instanceof Error ? err.message : "")
      showToastSafety("网络异常，请稍后重试")
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
   * 拉取 BYOK 配置回显（仅掩码，端上不持久化）。
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
        this.configBaseUrl = config?.customBaseUrl ?? ""
        // 行尾摘要（对齐安卓两行 trailing）：模型名缺失兜底平台预设名，掩码独立一行
        const model = config?.customModel ?? ""
        const providerName = config?.providerName ?? ""
        this.configModel = model !== "" ? model : providerName
        this.configMask = config?.apiKeyMask ?? ""
        this.configVerified = config?.verified === 1
        // 平台开关：仅 === 0 关闭；1 / undefined / null（旧响应）均视为开启（additive 兼容）
        this.byokDisabled = config !== null && config.byokEnabled === 0
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

  /**
   * 打开编辑半模态（对齐安卓 #79 编辑态预填）：baseUrl/model 回填已配置值，
   * key 一律置空——后端 PUT 三字段均必填，占位文案引导「重新输入 API Key」。
   */
  private openModelEditor(): void {
    // 平台已关闭 BYOK：入口行置灰不可进（ModelConfigRow 已不响应点击，此处双保险）
    if (this.byokDisabled) {
      return
    }
    this.editBaseUrl = this.hasApiConfig ? this.configBaseUrl : ""
    this.editModel = this.hasApiConfig ? this.configModel : ""
    this.editApiKey = ""
    this.showApiKeyPlain = false
    this.testFeedback = ""
    this.testFeedbackOk = false
    this.modelsExpanded = false
    this.showModelSheet = true
    this.fetchModelSuggestions()
  }

  /** 平台模型目录建议（可选参考）：拉取一次缓存，失败静默降级为无建议 */
  private async fetchModelSuggestions(): Promise<void> {
    if (this.modelsLoaded) {
      return
    }
    try {
      const res = await getDivModels()
      if (res.code === HttpStatusCode.Ok && res.data !== null) {
        this.platformModels = res.data
        this.modelsEmpty = res.data.length === 0
        this.modelsLoaded = true
        logger.debug('setting', `平台模型目录已加载 ${res.data.length} 条`)
      }
    } catch (err) {
      // 建议是增强功能，失败不打扰用户
      logger.debug('setting', '平台模型目录拉取失败，降级为无建议')
    }
  }

  /** 展开/收起平台模型目录（对齐安卓 api_config_model_catalog_expand/collapse） */
  private toggleModelsExpanded(): void {
    this.modelsExpanded = !this.modelsExpanded
  }

  /**
   * 表单预校验（镜像服务端 SaveUserApiFrom @NotBlank + SSRF 的 https 规则），
   * 返回错误文案；空串 = 通过。
   */
  private validateForm(): string {
    if (this.editBaseUrl.trim() === "") {
      return "请输入接入点地址"
    }
    if (!this.editBaseUrl.trim().startsWith("https://")) {
      return "接入点必须以 https:// 开头"
    }
    if (this.editModel.trim() === "") {
      return "请输入模型名称"
    }
    if (this.editApiKey.trim() === "") {
      return "请输入 API Key"
    }
    return ""
  }

  /** 组请求体（trim 后提交；key 明文仅进请求体，不落日志） */
  private buildForm(): SaveDivUserApiForm {
    return {
      baseUrl: this.editBaseUrl.trim(),
      model: this.editModel.trim(),
      apiKey: this.editApiKey.trim()
    }
  }

  /** 测试连接（不保存，独立试呼）：即时反馈走弹层内文案，成功/失败都留在原处便于修正 */
  private async testConnection(): Promise<void> {
    if (this.testing) {
      return
    }
    const invalid = this.validateForm()
    if (invalid !== "") {
      this.testFeedbackOk = false
      this.testFeedback = invalid
      return
    }
    this.testing = true
    this.testFeedback = ""
    try {
      const res = await testDivUserApiConfig(this.buildForm())
      if (res.code === HttpStatusCode.Ok) {
        // 后端 data 为 boolean：true 连通 / false 试呼未通过（key/接入点/模型任一不匹配）
        const ok = res.data === true
        this.testFeedbackOk = ok
        // 文案对齐安卓 api_config_test_ok / api_config_test_fail
        this.testFeedback = ok ? "连通性正常，可保存使用" : "连通性测试未通过，请检查接入点、模型名称与 API Key"
        logger.debug('setting', `BYOK 试呼完成 ok=${ok}`)
      } else {
        this.testFeedbackOk = false
        this.testFeedback = res.message ?? "测试失败"
      }
    } catch (err) {
      logger.error('setting', 'BYOK 试呼异常', err instanceof Error ? err.message : "")
      if (this.isSessionActive()) {
        this.testFeedbackOk = false
        this.testFeedback = "网络异常，请稍后重试"
      }
    } finally {
      this.testing = false
    }
  }

  /** 保存（服务端试呼通过才加密落库）：失败保留弹层并展示服务端文案 */
  private async saveConfig(): Promise<void> {
    if (this.saving) {
      return
    }
    const invalid = this.validateForm()
    if (invalid !== "") {
      showToastSafety(invalid)
      return
    }
    this.saving = true
    try {
      const res = await saveDivUserApiConfig(this.buildForm())
      if (res.code === HttpStatusCode.Ok) {
        // 低频关键动作成功留 info（不含 baseUrl/model/key）
        logger.info('setting', '解读模型配置已保存')
        // 文案对齐安卓 api_config_save_success
        showToastSafety("保存成功，解读将使用你的模型")
        this.showModelSheet = false
        this.loadApiConfig()
      } else {
        // 服务端连通校验等业务失败文案（decode 已按 data 字符串优先转译）
        logger.warn('setting', `解读模型配置保存失败 code=${res.code}`)
        showToastSafety(res.message, "保存失败")
      }
    } catch (err) {
      logger.error('setting', '解读模型配置保存异常', err instanceof Error ? err.message : "")
      if (this.isSessionActive()) {
        showToastSafety("网络异常，请稍后重试")
      }
    } finally {
      this.saving = false
    }
  }

  /** 删除 BYOK（删除确认弹窗二次确认后进入）：成功回落未配置态（平台默认模型） */
  private async deleteConfig(): Promise<void> {
    if (this.deleting) {
      return
    }
    this.deleting = true
    try {
      const res = await deleteDivUserApiConfig()
      if (res.code === HttpStatusCode.Ok) {
        logger.info('setting', '解读模型配置已删除，回落平台默认模型')
        // 文案对齐安卓 api_config_delete_success
        showToastSafety("已删除配置")
        this.showModelSheet = false
        this.loadApiConfig()
      } else {
        logger.warn('setting', `解读模型配置删除失败 code=${res.code}`)
        showToastSafety(res.message, "删除失败")
      }
    } catch (err) {
      logger.error('setting', '解读模型配置删除异常', err instanceof Error ? err.message : "")
      if (this.isSessionActive()) {
        showToastSafety("网络异常，请稍后重试")
      }
    } finally {
      this.deleting = false
    }
  }

  /** 输入框通用描边（surfaceVariant 底 + outline 描边 + 聚焦主色，SubscriptionEditPage 同款） */
  private inputBorder(focused: boolean): SettingBorderStyle {
    return { width: 1, color: focused ? DesignColor.PRIMARY : DesignColor.OUTLINE }
  }

  /** 表单字段标签（SubscriptionEditPage FormLabel 同款视觉） */
  @Builder
  FormLabel(title: string) {
    Text(title)
      .fontSize(DesignFontSize.BODY_SMALL)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(DesignColor.TEXT_SECONDARY)
      .margin({ bottom: DesignDimens.SPACE_XS })
  }

  /** 平台目录建议 chip：点击回填模型名（接入点仍由用户自填，BYOK 不限目录） */
  @Builder
  ModelChip(model: DivModelVo) {
    Text(model.modelId ?? "")
      .fontSize(DesignFontSize.CAPTION)
      .fontColor(DesignColor.TEXT_SECONDARY)
      .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM, top: 8, bottom: 8 })
      .borderRadius(DesignDimens.RADIUS_FULL)
      .backgroundColor(DesignColor.SURFACE_VARIANT)
      .border({ width: 1, color: DesignColor.OUTLINE })
      .margin({ right: DesignDimens.SPACE_XS })
      .constraintSize({ minHeight: DesignDimens.TOUCH_TARGET_MIN })
      .onClick(() => {
        if (model.modelId !== undefined) {
          this.editModel = model.modelId
        }
      })
  }

  /**
   * 解读模型编辑半模态（#81，对齐安卓 #79 编辑弹层）：
   * 自绘 handle（bindSheet dragBar 关闭，样式走令牌，Payment.ets 同款）+ 说明文案 + 三字段表单 +
   * 测试连接即时反馈 + 保存 + 已配置时的删除入口。
   */
  @Builder
  ModelEditorSheet() {
    Column() {
      // 拖拽指示条
      Row()
        .width(32)
        .height(4)
        .borderRadius(DesignDimens.RADIUS_FULL)
        .backgroundColor(DesignColor.OUTLINE)
        .margin({ top: DesignDimens.SPACE_SM })

      // 标题 + 说明文案（与隐私声明口径一致）
      Column({ space: DesignDimens.SPACE_XXS }) {
        Text("解读模型")
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .margin({ top: DesignDimens.SPACE_MD })

        Text(BYOK_NOTICE)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_TERTIARY)
          .lineHeight(18)
      }
      .width("100%")
      .alignItems(HorizontalAlign.Start)
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

      // 表单（可滚动）
      Scroll() {
        Column() {
          // ---- 接入点（label/hint 对齐安卓 api_config_base_url_label/hint）----
          this.FormLabel("接入点地址")
          TextInput({ text: this.editBaseUrl, placeholder: "https://api.example.com" })
            .height(48)
            .fontSize(DesignFontSize.BODY)
            .backgroundColor(DesignColor.SURFACE_VARIANT)
            .borderRadius(DesignDimens.INPUT_RADIUS)
            .border(this.inputBorder(this.baseUrlFocused))
            .placeholderColor(DesignColor.TEXT_TERTIARY)
            .placeholderFont({ size: DesignFontSize.BODY })
            .onFocus(() => {
              this.baseUrlFocused = true
            })
            .onBlur(() => {
              this.baseUrlFocused = false
            })
            .onChange((value: string) => {
              this.editBaseUrl = value
            })

          Text("支持任意 OpenAI 兼容接入点，需 HTTPS")
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_TERTIARY)
            .margin({ top: DesignDimens.SPACE_XS })

          Column().height(DesignDimens.SPACE_LG)

          // ---- 模型名称 ----
          this.FormLabel("模型名称")
          TextInput({ text: this.editModel, placeholder: "如 deepseek-chat" })
            .height(48)
            .fontSize(DesignFontSize.BODY)
            .backgroundColor(DesignColor.SURFACE_VARIANT)
            .borderRadius(DesignDimens.INPUT_RADIUS)
            .border(this.inputBorder(this.modelFocused))
            .placeholderColor(DesignColor.TEXT_TERTIARY)
            .placeholderFont({ size: DesignFontSize.BODY })
            .onFocus(() => {
              this.modelFocused = true
            })
            .onBlur(() => {
              this.modelFocused = false
            })
            .onChange((value: string) => {
              this.editModel = value
            })

          // ---- 平台模型目录（折叠展开，交互对齐安卓 #79；BYOK 不限目录，仅为输入建议）----
          if (this.modelsLoaded && this.platformModels.length > 0) {
            Text(this.modelsExpanded ? "收起平台模型目录" : "从平台模型目录选择")
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.PRIMARY)
              // 上下 14 + 行高 16 = 44 触控热区（规范 §2.3，Payment「去领取」同款处理）
              .padding({ top: 14, bottom: 14 })
              .margin({ top: 4 })
              .onClick(() => {
                this.toggleModelsExpanded()
              })
            if (this.modelsExpanded) {
              Scroll() {
                Row() {
                  ForEach(this.platformModels, (model: DivModelVo) => {
                    this.ModelChip(model)
                  }, (model: DivModelVo) => `model-${model.id ?? model.modelId ?? ""}`)
                }
                .width("100%")
              }
              .width("100%")
              .scrollable(ScrollDirection.Horizontal)
              .scrollBar(BarState.Off)
              .align(Alignment.Start)
              .margin({ top: DesignDimens.SPACE_XS })
            }
          } else if (this.modelsLoaded && this.modelsEmpty) {
            // 目录拉取成功但为空（对齐安卓 api_config_model_catalog_empty）
            Text("暂无平台模型目录")
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.TEXT_TERTIARY)
              .margin({ top: DesignDimens.SPACE_XS })
          }

          Column().height(DesignDimens.SPACE_LG)

          // ---- API Key（密码框可切明文，DeliveryCreatePage 同款）----
          this.FormLabel("API Key")
          Row() {
            TextInput({
              text: this.editApiKey,
              placeholder: this.hasApiConfig ? "重新输入 API Key" : "输入 API Key"
            })
              .type(this.showApiKeyPlain ? InputType.Normal : InputType.Password)
              .height(48)
              .fontSize(DesignFontSize.BODY)
              .backgroundColor(DesignColor.SURFACE_VARIANT)
              .borderRadius(DesignDimens.INPUT_RADIUS)
              .border(this.inputBorder(this.apiKeyFocused))
              .placeholderColor(DesignColor.TEXT_TERTIARY)
              .placeholderFont({ size: DesignFontSize.BODY_SMALL })
              .showPasswordIcon(false)
              .layoutWeight(1)
              .onFocus(() => {
                this.apiKeyFocused = true
              })
              .onBlur(() => {
                this.apiKeyFocused = false
              })
              .onChange((value: string) => {
                this.editApiKey = value
              })

            Stack({ alignContent: Alignment.Center }) {
              SymbolGlyph(this.showApiKeyPlain ? $r('sys.symbol.eye_slash') : $r('sys.symbol.eye'))
                .fontSize(20)
                .fontColor([DesignColor.TEXT_SECONDARY])
            }
            .width(DesignDimens.BACK_HIT_AREA)
            .height(DesignDimens.BACK_HIT_AREA)
            .accessibilityDescription(this.showApiKeyPlain ? '隐藏 API Key' : '显示 API Key')
            .onClick(() => {
              this.showApiKeyPlain = !this.showApiKeyPlain
            })
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)

          Text("Key 仅提交后端加密存储，端上不保存；已配置时更换任何字段都需重新输入 Key")
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_TERTIARY)
            .margin({ top: DesignDimens.SPACE_XS })

          // ---- 测试连接即时反馈 ----
          if (this.testFeedback !== "") {
            Row({ space: DesignDimens.SPACE_XXS }) {
              SymbolGlyph(this.testFeedbackOk
                ? $r('sys.symbol.checkmark')
                : $r('sys.symbol.exclamationmark_triangle_fill'))
                .fontSize(14)
                .fontColor([this.testFeedbackOk ? DesignColor.SUCCESS : DesignColor.DANGER])
              Text(this.testFeedback)
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(this.testFeedbackOk ? DesignColor.SUCCESS : DesignColor.DANGER)
                .layoutWeight(1)
            }
            .width("100%")
            .alignItems(VerticalAlign.Center)
            .margin({ top: DesignDimens.SPACE_MD })
          }

          // ---- 操作按钮：测试连接 + 保存（DeliveryCreatePage 并排 AppButton 同款）----
          Row({ space: DesignDimens.SPACE_SM }) {
            AppButton({
              text: "测试连接",
              variant: AppButtonVariant.SECONDARY,
              loading: this.testing,
              click: () => {
                this.testConnection()
              }
            })
              .layoutWeight(1)
            AppButton({
              text: "保存",
              loading: this.saving,
              click: () => {
                this.saveConfig()
              }
            })
              .layoutWeight(1)
          }
          .width("100%")
          .margin({ top: DesignDimens.SPACE_LG })

          // ---- 删除配置（仅已配置时；二次确认见 deleteConfigDialogController）----
          if (this.hasApiConfig) {
            AppButton({
              text: "删除配置",
              variant: AppButtonVariant.SECONDARY,
              contentColor: DesignColor.DANGER_INK,
              loading: this.deleting,
              click: () => {
                this.deleteConfigDialogController.open()
              }
            })
              .margin({ top: DesignDimens.SPACE_SM })
          }
        }
        .width("100%")
        .padding({
          left: DesignDimens.PAGE_PADDING,
          right: DesignDimens.PAGE_PADDING,
          top: DesignDimens.SPACE_LG,
          bottom: DesignDimens.SPACE_XL
        })
      }
      .layoutWeight(1)
      .scrollBar(BarState.Off)
      .align(Alignment.Top)
    }
    .width("100%")
    .height("100%")
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
              // 消息通知（真实授权态，行尾显示系统实际授予情况）
              // 点击直达系统应用通知设置（对齐安卓 P-1501：授权 / 去关闭都在系统页完成，
              // 返回后由 onShown 重查刷新行内状态，不再进权限管理页）
              SettingRow({
                icon: $r('sys.symbol.bell'),
                containerColor: DesignColor.ICON_CONTAINER_ORANGE,
                iconColor: DesignColor.ICON_FOREGROUND_ORANGE,
                title: "消息通知",
                trailingValue: this.notificationGranted ? "已开启" : "未授权",
                onRowClick: () => {
                  this.openNotificationSettings()
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

            // 解读模型（BYOK）编辑半模态挂在外层容器（bindSheet 双向绑定 showModelSheet）
            Column() {
              SettingGroupCard() {
                // 解读模型入口行（#81，对齐安卓 #79 自定义 trailing 摘要）：
                // 已配置 = 第一行 customModel +「已验证」徽标、第二行 apiKeyMask（仅掩码）；
                // 未配置 = 「未配置，使用平台默认模型」；byokEnabled=0 = 置灰提示不可进编辑层。
                // 配置仅从后端 GET 回显，端上不持久化 key。
                ModelConfigRow({
                  icon: $r('sys.symbol.key'),
                  containerColor: DesignColor.ICON_CONTAINER_PURPLE,
                  iconColor: DesignColor.ICON_FOREGROUND_PURPLE,
                  title: "解读模型",
                  hasConfig: this.configLoaded && this.hasApiConfig,
                  modelText: this.configModel,
                  maskText: this.configMask,
                  verified: this.configVerified,
                  loading: this.loadingConfig,
                  disabled: this.byokDisabled,
                  onRowClick: () => {
                    this.openModelEditor()
                  }
                })

                Divider()
                  .color(DesignColor.HAIRLINE)
                  .strokeWidth(0.5)

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
            }
            .width("100%")
            .bindSheet($$this.showModelSheet, this.ModelEditorSheet(), {
              // 近 3/4 高表单浮层（Payment 选券浮层同款参数口径）；
              // dragBar:false + 自绘 handle，容器色对齐 Android surface
              height: "72%",
              backgroundColor: DesignColor.SURFACE,
              showClose: false,
              dragBar: false
            })

            // ===== 其他 =====
            Text("其他")
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .width("100%")
              .margin({ top: DesignDimens.SECTION_SPACING, bottom: 8 })

            SettingGroupCard() {
              // 检查更新（对齐安卓「其他」组 CheckUpdateSettingRow，64dp 行 + 行尾当前版本号）
              // 图标对齐安卓 Icons.Default.SystemUpdate（下载/更新语义）取 sys.symbol.download
              SettingRow({
                icon: $r('sys.symbol.download'),
                containerColor: DesignColor.ICON_CONTAINER_TEAL,
                iconColor: DesignColor.ICON_FOREGROUND_TEAL,
                title: "检查更新",
                trailingValue: `v${getAppVersionName()}`,
                // 检查中显示行尾 spinner（checkingUpdate 同时拦截连点，见 checkUpdate 首行）
                loading: this.checkingUpdate,
                onRowClick: () => {
                  this.checkUpdate()
                }
              })

              Divider()
                .color(DesignColor.HAIRLINE)
                .strokeWidth(0.5)

              // 权限管理入口（申请 / 跳系统设置都在权限页收口）
              // 图标对齐安卓 SettingPage 的 AdminPanelSettings（盾牌 + 用户徽标）取 sys.symbol.person_shield
              SettingRow({
                icon: $r('sys.symbol.person_shield'),
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
      // 返回本页时重拉解读模型配置（在别的入口改动后回显保持新鲜；loadingConfig 挡并发）
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
  @Prop containerColor: number = DesignColor.PRIMARY_LIGHT
  @Prop iconColor: number = DesignColor.PRIMARY
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
        .fontColor(DesignColor.TEXT_PRIMARY)
        .layoutWeight(1)
        .margin({ left: 12 })

      if (this.loading) {
        // 行尾加载态：spinner 替代版本号（对齐安卓检查更新反馈）
        LoadingProgress()
          .width(16)
          .height(16)
          .color(DesignColor.TEXT_SECONDARY)
      } else if (this.trailingValue !== "") {
        Text(this.trailingValue)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .fontFeature(DesignFontFeature.TNUM)
          // 单行截断防溢出（标题有 layoutWeight(1) 优先收缩）
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .constraintSize({ maxWidth: '55%' })
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
 * 解读模型入口行（#81，对齐安卓 #79 SettingRow(apiConfig) 的自定义 trailing 摘要）：
 * 已配置 = 右对齐两行（第一行 customModel +「已验证」徽标，第二行 apiKeyMask 仅掩码）；
 * 未配置 = 单行「未配置，使用平台默认模型」；
 * 平台关闭 BYOK（byokEnabled=0）= 置灰 + 提示「平台已关闭自带模型」，不可点击进入编辑层。
 * 其余视觉与 SettingRow 一致（64dp 高 / ib32 图标容器 / 按压铺 surfaceVariant）。
 */
@Component
struct ModelConfigRow {
  @Prop icon: Resource = $r('sys.symbol.info_circle')
  @Prop containerColor: number = DesignColor.PRIMARY_LIGHT
  @Prop iconColor: number = DesignColor.PRIMARY
  @Prop title: string = ""
  @Prop hasConfig: boolean = false
  @Prop modelText: string = ""
  @Prop maskText: string = ""
  @Prop verified: boolean = false
  @Prop loading: boolean = false
  // 平台关闭 BYOK：整行置灰（降透明 0.5，对齐 AppButton 禁用惯例）、去 chevron、不响应点击
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
        .fontColor(DesignColor.TEXT_PRIMARY)
        .layoutWeight(1)
        .margin({ left: 12 })

      if (this.loading) {
        // 加载中：spinner 占位（对齐安卓行内加载反馈）
        LoadingProgress()
          .width(16)
          .height(16)
          .color(DesignColor.TEXT_SECONDARY)
      } else if (this.disabled) {
        // 平台关闭 BYOK：提示文案（对齐安卓「入口隐藏或置灰提示」的置灰分支）
        Text(BYOK_DISABLED_NOTICE)
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor(DesignColor.TEXT_SECONDARY)
      } else if (this.hasConfig) {
        // 已配置：右对齐两行摘要（对齐安卓 Column(horizontalAlignment = End)）
        Column({ space: 2 }) {
          Row({ space: 4 }) {
            Text(this.modelText)
              .fontSize(DesignFontSize.BODY_SMALL)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .constraintSize({ maxWidth: 150 })
            if (this.verified) {
              // 「已验证」徽标（微字号 10 非字阶档位，对齐安卓 VerifiedBadge 视觉）
              Row({ space: 2 }) {
                SymbolGlyph($r('sys.symbol.checkmark'))
                  .fontSize(10)
                  .fontColor([DesignColor.ON_SUCCESS_CONTAINER])
                Text("已验证")
                  .fontSize(10)
                  .fontColor(DesignColor.ON_SUCCESS_CONTAINER)
              }
              .padding({ left: 6, right: 6, top: 2, bottom: 2 })
              .borderRadius(DesignDimens.RADIUS_FULL)
              .backgroundColor(DesignColor.SUCCESS_CONTAINER)
            }
          }
          .alignItems(VerticalAlign.Center)

          if (this.maskText !== "") {
            Text(this.maskText)
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.TEXT_TERTIARY)
              .fontFeature(DesignFontFeature.TNUM)
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .constraintSize({ maxWidth: 180 })
          }
        }
        .alignItems(HorizontalAlign.End)
      } else {
        // 未配置引导文案（对齐安卓 api_config_unconfigured）
        Text("未配置，使用平台默认模型")
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor(DesignColor.TEXT_SECONDARY)
      }

      // chevron 是「可进入」的视觉信号：置灰态不渲染
      if (!this.disabled) {
        SymbolGlyph($r('sys.symbol.chevron_right'))
          .fontSize(20)
          .fontColor([DesignColor.TEXT_TERTIARY])
          .margin({ left: 4 })
      }
    }
    .width("100%")
    .height(64)
    .alignItems(VerticalAlign.Center)
    // 置灰：整行降透明（对齐 AppButton 禁用降透明惯例），内容灰化弱化可点预期
    .opacity(this.disabled ? 0.5 : 1)
    .backgroundColor(this.pressed ? DesignColor.SURFACE_VARIANT : Color.Transparent)
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

/** 输入框描边样式（ArkTS 对象字面量需显式类型，SubscriptionEditPage 同款约定） */
interface SettingBorderStyle {
  width: number
  color: number | Resource
}

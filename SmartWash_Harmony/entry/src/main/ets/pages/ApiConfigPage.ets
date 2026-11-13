// 解读模型（BYOK）配置页 — 对齐安卓 feature/user/impl/ui/setting/ApiConfigPage.kt
//
// 由设置页「解读模型」半模态弹层改为独立页面（2026-11-13）：表单含模式切换 + 双联动选择 +
// key 输入 + 测试/保存/删除，内容长度超出弹层合理承载范围，半屏态在小屏拥挤；与设置页其他
// 编辑入口（修改密码 / 权限管理）统一走独立页面。观象台 428 拦截直达本页。
//
// 双模式二选一：
// - 目录模式（默认）：GET /models 一次返回「厂商 → 模型」目录树，两个联动选择器——
//   第一个选厂商，第二个联动列该厂商的模型条目点选回填 modelConfigId，接入点/模型名
//   由服务端从目录取（不显示 baseUrl 输入）；
// - 自定义模式：baseUrl + 模型名 + apiKey 三输入（OpenAI 兼容接入点）。
// apiKey 两模式均必填（编辑态恒不回显，占位引导重输）；「测试连接」走 POST test 即时反馈，
// 「保存」走 PUT（服务端真实试呼）；已配置时提供「删除配置」二次确认。
//
// 隐私红线：明文 API Key 只存在于输入框内存态（@State），不落本地存储、不进日志；
// 列表回显一律掩码。
//
// 选择器形态说明：安卓用 M3 ExposedDropdownMenuBox 悬浮下拉；鸿蒙端无既有下拉组件先例，
// 这里用「字段 + 内联展开选项面板」实现等价交互（同一份数据、同一联动语义），
// 避免引入未验证的弹窗 API 带来真机定位/层级风险。

import { DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from '../constant/DesignSystem'
import { HttpStatusCode } from '@ohos/axios'
import vibrator from '@ohos.vibrator'
import {
  DivUserApiConfigVo,
  DivVendorGroupVo,
  SaveDivUserApiForm,
  deleteDivUserApiConfig,
  getDivModels,
  getDivUserApiConfig,
  saveDivUserApiConfig,
  testDivUserApiConfig
} from '../feature/divination/network/DivSettingsApi'
import { AppButton, AppButtonVariant, AppConfirmDialog, PageHeader } from '../view/common/AppComponents'
import { ThemeRefresh } from '../view/common/ThemeRefresh'
import { logger } from '../utils/Logger'
import { pathStack } from '../utils/PathStackUtil'
import { showToastSafety } from '../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../utils/SafeArea'
import { resString } from '../utils/ResourceString'

/** 解读模型说明文案（观星台零兜底口径；与设置页隐私声明口径一致） */
const BYOK_NOTICE: string = resString('setting_9') + resString('setting_10')

/** 字段级校验错误标识（仅提交时展示；输入即清除） */
type ApiConfigField = 'MODEL_CONFIG' | 'BASE_URL' | 'MODEL' | 'API_KEY' | ''

/** 输入框描边样式（ArkTS 对象字面量需显式类型） */
interface ApiConfigBorderStyle {
  width: number
  color: ResourceColor
}

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'apiConfigPage'

@Builder
export function ApiConfigBuilder() {
  ApiConfigPage()
}

@Component
struct ApiConfigPage {
  // ===== 配置回显态（仅掩码；端上不持久化 key）=====
  @State config: DivUserApiConfigVo | null = null
  @State loadingConfig: boolean = false
  @State configLoadFailed: boolean = false
  /** 观星台 AI 解读总开关（divUseEnabled===0 关闭）：兜底返回设置页 */
  @State byokDisabled: boolean = false

  // ===== 平台模型目录（树形整包缓存；目录模式联动数据源）=====
  @State platformModels: Array<DivVendorGroupVo> = []
  @State modelsLoaded: boolean = false
  @State modelsLoading: boolean = false
  @State modelsLoadFailed: boolean = false
  @State modelsEmpty: boolean = false

  // ===== 表单内存态（仅编辑期存在；明文 key 不持久化）=====
  @State editModeCatalog: boolean = true
  @State selectedVendorIndex: number = -1
  @State selectedModelId: number = 0
  @State editBaseUrl: string = ''
  @State editModel: string = ''
  @State editApiKey: string = ''
  @State showApiKeyPlain: boolean = false
  @State testing: boolean = false
  @State saving: boolean = false
  @State deleting: boolean = false
  @State testFeedback: string = ''
  @State testFeedbackOk: boolean = false
  @State saveFeedback: string = ''
  @State fieldError: ApiConfigField = ''
  @State baseUrlFocused: boolean = false
  @State modelFocused: boolean = false
  @State apiKeyFocused: boolean = false
  /** 编辑态预填只执行一次（平台目录异步到达后不覆盖用户后续选择） */
  @State formInitialized: boolean = false

  // 删除配置二次确认（不可逆操作走 dangerInk，与登出/解绑同款）
  private deleteDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: resString('api_config_confirm_delete_title'),
      message: resString('setting_12'),
      confirmText: resString('api_config_delete'),
      cancelText: resString('cancel'),
      isDanger: true,
      onConfirm: () => {
        this.performHaptic()
        this.deleteConfig()
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  async aboutToAppear(): Promise<void> {
    await this.loadConfig()
    this.loadModels()
    // 平台总开关关闭（divUseEnabled=0）兜底拦截：设置页行已置灰，本页双保险返回
    if (this.byokDisabled) {
      setTimeout(() => {
        pathStack.pop()
      }, 0)
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
   * 拉取解读模型配置回显（仅掩码，端上不持久化）。
   * 业务失败/网络异常静默留痕不打断页面（401 已由 Axios 拦截器统一处理）。
   */
  private async loadConfig(): Promise<void> {
    if (this.loadingConfig) {
      return
    }
    this.loadingConfig = true
    try {
      const res = await getDivUserApiConfig()
      if (res.code === HttpStatusCode.Ok) {
        this.config = res.data
        this.byokDisabled = res.data !== null && res.data.divUseEnabled === 0
        this.configLoadFailed = false
        this.initFormFromConfig()
        logger.debug(TAG, `解读模型配置回显 hasConfig=${res.data !== null} verified=${this.config?.verified === 1}`)
      } else {
        this.configLoadFailed = true
        logger.warn(TAG, `解读模型配置获取失败 code=${res.code} message=${res.message ?? ""}`)
      }
    } catch (err) {
      this.configLoadFailed = true
      logger.error(TAG, '解读模型配置获取异常', err instanceof Error ? err.message : "")
    } finally {
      this.loadingConfig = false
    }
  }

  /** 编辑态预填：GET 返回后按配置回显模式与字段；apiKey 始终留空（后端仅回掩码） */
  private initFormFromConfig(): void {
    if (this.formInitialized) {
      return
    }
    const cfg = this.config
    if (cfg === null) {
      this.formInitialized = true
      return
    }
    this.formInitialized = true
    const catalogId = cfg.modelConfigId ?? null
    this.editModeCatalog = catalogId !== null
    this.selectedModelId = catalogId ?? 0
    this.editBaseUrl = cfg.customBaseUrl ?? ''
    this.editModel = cfg.customModel ?? ''
  }

  /**
   * 平台模型目录拉取：目录模式联动选择的数据源（树形整包，第二级本地过滤）。
   * 拉取一次缓存；失败置 modelsLoadFailed，可点击重试。
   */
  private async loadModels(): Promise<void> {
    if (this.modelsLoaded || this.modelsLoading) {
      return
    }
    this.modelsLoading = true
    try {
      const res = await getDivModels()
      if (res.code === HttpStatusCode.Ok && res.data !== null) {
        this.platformModels = res.data
        this.modelsEmpty = res.data.length === 0
        this.modelsLoaded = true
        this.modelsLoadFailed = false
        this.locateVendorForSelectedModel()
        logger.debug(TAG, `平台模型目录已加载 ${res.data.length} 个厂商`)
      } else {
        this.modelsLoadFailed = true
        logger.debug(TAG, `平台模型目录拉取失败 code=${res.code}`)
      }
    } catch (err) {
      this.modelsLoadFailed = true
      logger.error(TAG, '平台模型目录拉取异常', err instanceof Error ? err.message : "")
    } finally {
      this.modelsLoading = false
    }
  }

  /** 编辑态回填补位：目录模式下按 modelConfigId 在目录树中定位所属厂商（不覆盖用户手动选择） */
  private locateVendorForSelectedModel(): void {
    if (this.selectedVendorIndex >= 0 || this.selectedModelId <= 0) {
      return
    }
    for (let i = 0; i < this.platformModels.length; i++) {
      const models = this.platformModels[i].models
      for (let j = 0; j < models.length; j++) {
        if ((models[j].id ?? 0) === this.selectedModelId) {
          this.selectedVendorIndex = i
          return
        }
      }
    }
  }

  /** 当前选中厂商分组（未选返回 null） */
  private selectedGroup(): DivVendorGroupVo | null {
    if (this.selectedVendorIndex < 0 || this.selectedVendorIndex >= this.platformModels.length) {
      return null
    }
    return this.platformModels[this.selectedVendorIndex]
  }

  /** 厂商显示名：vendor 优先、provider 兜底 */
  private displayVendor(group: DivVendorGroupVo): string {
    const vendor = group.vendor ?? ''
    if (vendor !== '') {
      return vendor
    }
    return group.provider ?? ''
  }

  /** 厂商选择器条目标题 */
  private vendorTitles(): Array<string> {
    const titles: Array<string> = []
    for (let i = 0; i < this.platformModels.length; i++) {
      titles.push(this.displayVendor(this.platformModels[i]))
    }
    return titles
  }

  /** 厂商选择器条目副文本（接入点） */
  private vendorSubtitles(): Array<string> {
    const subtitles: Array<string> = []
    for (let i = 0; i < this.platformModels.length; i++) {
      subtitles.push(this.platformModels[i].baseUrl ?? '')
    }
    return subtitles
  }

  /** 模型选择器条目标题（当前厂商范围内） */
  private modelTitles(): Array<string> {
    const titles: Array<string> = []
    const group = this.selectedGroup()
    if (group !== null) {
      for (let i = 0; i < group.models.length; i++) {
        titles.push(group.models[i].name ?? '')
      }
    }
    return titles
  }

  /** 模型选择器条目副文本（modelId） */
  private modelSubtitles(): Array<string> {
    const subtitles: Array<string> = []
    const group = this.selectedGroup()
    if (group !== null) {
      for (let i = 0; i < group.models.length; i++) {
        subtitles.push(group.models[i].modelId ?? '')
      }
    }
    return subtitles
  }

  /** 当前选中模型在所属厂商下的索引（未选返回 -1） */
  private selectedModelIndex(): number {
    const group = this.selectedGroup()
    if (group === null || this.selectedModelId <= 0) {
      return -1
    }
    for (let i = 0; i < group.models.length; i++) {
      if ((group.models[i].id ?? 0) === this.selectedModelId) {
        return i
      }
    }
    return -1
  }

  /** 切换选择模式：目录模式（默认）/ 自定义接入；切换清空两模式不兼容的字段报错 */
  private switchMode(catalog: boolean): void {
    if (this.editModeCatalog === catalog) {
      return
    }
    this.editModeCatalog = catalog
    this.fieldError = ''
    this.saveFeedback = ''
    this.testFeedback = ''
    this.testFeedbackOk = false
  }

  /** 目录模式选中厂商：联动模型列表；换厂商即清空旧模型选择 */
  private selectVendor(index: number): void {
    if (index < 0 || index >= this.platformModels.length) {
      return
    }
    if (this.selectedVendorIndex !== index) {
      this.selectedModelId = 0
    }
    this.selectedVendorIndex = index
    this.fieldError = ''
  }

  /** 目录模式选中模型条目：记录 modelConfigId（接入点/模型名由服务端按条目取） */
  private selectModel(index: number): void {
    const group = this.selectedGroup()
    if (group === null || index < 0 || index >= group.models.length) {
      return
    }
    this.selectedModelId = group.models[index].id ?? 0
    this.fieldError = ''
  }

  /**
   * 表单预校验（镜像服务端双模式校验）：目录模式 modelConfigId 必选；自定义模式镜像
   * SaveUserApiFrom @NotBlank + SSRF 的 https 规则。apiKey 两模式均必填。
   * 返回错误文案；空串 = 通过。
   */
  private validateForm(): string {
    if (this.editModeCatalog) {
      if (this.selectedModelId <= 0) {
        return resString('setting_16')
      }
    } else {
      if (this.editBaseUrl.trim() === '') {
        return resString('api_config_error_base_url_empty')
      }
      if (!this.editBaseUrl.trim().startsWith("https://")) {
        return resString('api_config_error_base_url_https')
      }
      if (this.editModel.trim() === '') {
        return resString('api_config_error_model_empty')
      }
    }
    if (this.editApiKey.trim() === '') {
      return resString('api_config_error_key_empty')
    }
    return ''
  }

  /** 组请求体（双模式二选一，trim 后提交；key 明文仅进请求体，不落日志） */
  private buildForm(): SaveDivUserApiForm {
    if (this.editModeCatalog) {
      const form: SaveDivUserApiForm = { apiKey: this.editApiKey.trim() }
      form.modelConfigId = this.selectedModelId
      return form
    }
    return {
      baseUrl: this.editBaseUrl.trim(),
      model: this.editModel.trim(),
      apiKey: this.editApiKey.trim()
    }
  }

  /** 测试连接（不保存，独立试呼）：即时反馈留在原处便于修正 */
  private async testConnection(): Promise<void> {
    if (this.testing) {
      return
    }
    const invalid = this.validateForm()
    if (invalid !== '') {
      this.fieldError = this.fieldErrorFromMessage(invalid)
      this.testFeedbackOk = false
      this.testFeedback = invalid
      return
    }
    this.testing = true
    this.testFeedback = ''
    try {
      const res = await testDivUserApiConfig(this.buildForm())
      if (res.code === HttpStatusCode.Ok) {
        // 后端 data 为 boolean：true 连通 / false 试呼未通过（key/接入点/模型任一不匹配）
        const ok = res.data === true
        this.testFeedbackOk = ok
        this.testFeedback = ok ? resString('api_config_test_ok') : resString('setting_19')
        if (ok) {
          this.performHaptic()
        }
        logger.debug(TAG, `BYOK 试呼完成 ok=${ok}`)
      } else {
        this.testFeedbackOk = false
        this.testFeedback = res.message ?? resString('setting_20')
      }
    } catch (err) {
      logger.error(TAG, 'BYOK 试呼异常', err instanceof Error ? err.message : "")
      this.testFeedbackOk = false
      this.testFeedback = resString('setting_18')
    } finally {
      this.testing = false
    }
  }

  /** 校验文案 → 字段标识（用于字段级错误展示） */
  private fieldErrorFromMessage(message: string): ApiConfigField {
    if (message === resString('setting_16')) {
      return 'MODEL_CONFIG'
    }
    if (message === resString('api_config_error_base_url_empty') ||
      message === resString('api_config_error_base_url_https')) {
      return 'BASE_URL'
    }
    if (message === resString('api_config_error_model_empty')) {
      return 'MODEL'
    }
    return 'API_KEY'
  }

  /** 保存（服务端试呼通过才加密落库）：失败保留页面并 inline 展示服务端文案 */
  private async saveConfig(): Promise<void> {
    if (this.saving) {
      return
    }
    const invalid = this.validateForm()
    if (invalid !== '') {
      this.fieldError = this.fieldErrorFromMessage(invalid)
      showToastSafety(invalid)
      return
    }
    this.saving = true
    this.saveFeedback = ''
    try {
      const res = await saveDivUserApiConfig(this.buildForm())
      if (res.code === HttpStatusCode.Ok) {
        // 低频关键动作成功留 info（不含 baseUrl/model/key）
        logger.info(TAG, '解读模型配置已保存')
        showToastSafety(resString('api_config_save_success'))
        pathStack.pop()
      } else {
        logger.warn(TAG, `解读模型配置保存失败 code=${res.code}`)
        this.saveFeedback = res.message ?? resString('setting_21')
      }
    } catch (err) {
      logger.error(TAG, '解读模型配置保存异常', err instanceof Error ? err.message : "")
      this.saveFeedback = resString('setting_18')
    } finally {
      this.saving = false
    }
  }

  /** 删除解读模型配置（二次确认后进入）：成功回落未配置态（零兜底，需重新配置才能 AI 解读） */
  private async deleteConfig(): Promise<void> {
    if (this.deleting) {
      return
    }
    this.deleting = true
    try {
      const res = await deleteDivUserApiConfig()
      if (res.code === HttpStatusCode.Ok) {
        logger.info(TAG, '解读模型配置已删除，需重新配置模型才能使用 AI 解读')
        showToastSafety(resString('api_config_delete_success'))
        pathStack.pop()
      } else {
        logger.warn(TAG, `解读模型配置删除失败 code=${res.code}`)
        showToastSafety(res.message, resString('setting_22'))
      }
    } catch (err) {
      logger.error(TAG, '解读模型配置删除异常', err instanceof Error ? err.message : "")
      showToastSafety(resString('setting_18'))
    } finally {
      this.deleting = false
    }
  }

  /** 输入框通用描边（surfaceVariant 底 + outline 描边 + 聚焦/错误主色） */
  private inputBorder(focused: boolean): ApiConfigBorderStyle {
    return { width: 1, color: focused ? $r('app.color.PRIMARY') : $r('app.color.OUTLINE') }
  }

  /** 表单字段标签（SubscriptionEditPage FormLabel 同款视觉） */
  @Builder
  FormLabel(title: string) {
    Text(title)
      .fontSize(DesignFontSize.BODY_SMALL)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor($r('app.color.TEXT_SECONDARY'))
      .width('100%')
      .margin({ bottom: DesignDimens.SPACE_XS })
  }

  /** 选择模式分段 chip（目录模式默认；选中主色浅底 + 描边） */
  @Builder
  EditModeChip(label: string, selected: boolean, onSelect: () => void) {
    Text(label)
      .fontSize(DesignFontSize.BODY_SMALL)
      .fontWeight(selected ? DesignFontWeight.MEDIUM : DesignFontWeight.NORMAL)
      .fontColor(selected ? $r('app.color.PRIMARY') : $r('app.color.TEXT_SECONDARY'))
      .textAlign(TextAlign.Center)
      .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: 8, bottom: 8 })
      .borderRadius(DesignDimens.RADIUS_FULL)
      .backgroundColor(selected ? $r('app.color.PRIMARY_LIGHT') : $r('app.color.SURFACE_VARIANT'))
      .border({ width: 1, color: selected ? $r('app.color.PRIMARY') : $r('app.color.OUTLINE') })
      .constraintSize({ minHeight: DesignDimens.TOUCH_TARGET_MIN })
      .onClick(() => {
        onSelect()
      })
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        // 公共页头 — 22/600 标题 + 48dp 返回热区（对齐安卓 PageHeader）
        PageHeader({ title: resString('api_config_title') })

        Scroll() {
          Column() {
            // 说明文案（与隐私声明口径一致）
            Text(BYOK_NOTICE)
              .fontSize(DesignFontSize.CAPTION)
              .fontColor($r('app.color.TEXT_TERTIARY'))
              .lineHeight(18)
              .width('100%')
              .margin({ top: DesignDimens.SPACE_MD })

            // 已配置摘要（仅掩码回显，帮助理解为何编辑需重输 key）
            if (this.config !== null) {
              Column() {
                Row() {
                  Text(this.summaryModelText())
                    .fontSize(DesignFontSize.BODY)
                    .fontWeight(DesignFontWeight.MEDIUM)
                    .fontColor($r('app.color.TEXT_PRIMARY'))
                    .maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                    .layoutWeight(1)
                  if (this.config?.verified === 1) {
                    VerifiedBadge()
                  }
                }
                .width('100%')
                .alignItems(VerticalAlign.Center)

                if (this.summarySubtitle() !== '') {
                  Text(this.summarySubtitle())
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor($r('app.color.TEXT_TERTIARY'))
                    .maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                    .width('100%')
                    .margin({ top: 2 })
                }
              }
              .width('100%')
              .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: 10, bottom: 10 })
              .backgroundColor($r('app.color.SURFACE_VARIANT'))
              .borderRadius(DesignDimens.RADIUS_MD)
              .margin({ top: DesignDimens.SPACE_MD })
              .alignItems(HorizontalAlign.Start)
            }

            // 配置加载失败：给重试（说明发生了什么 + 怎么解决）
            if (this.configLoadFailed && this.config === null) {
              Row() {
                Text(resString('api_config_load_failed'))
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .fontColor($r('app.color.DANGER'))
                  .layoutWeight(1)
                Text(resString('api_config_retry'))
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .fontWeight(DesignFontWeight.SEMI_BOLD)
                  .fontColor($r('app.color.PRIMARY'))
                  .padding({ top: 12, bottom: 12 })
                  .onClick(() => {
                    this.loadConfig()
                  })
              }
              .width('100%')
              .alignItems(VerticalAlign.Center)
              .margin({ top: DesignDimens.SPACE_SM })
            }

            // ---- 选择模式（目录模式默认）----
            this.FormLabel(resString('api_config_mode_label'))
            Row({ space: DesignDimens.SPACE_SM }) {
              this.EditModeChip(resString('api_config_mode_catalog'), this.editModeCatalog, (): void => {
                this.switchMode(true)
              })
                .layoutWeight(1)
              this.EditModeChip(resString('api_config_mode_custom'), !this.editModeCatalog, (): void => {
                this.switchMode(false)
              })
                .layoutWeight(1)
            }
            .width('100%')
            .margin({ top: DesignDimens.SPACE_MD })

            if (this.editModeCatalog) {
              // ---- 目录模式：厂商 → 模型 双联动 ----
              if (this.modelsLoading) {
                Text(resString('setting_24'))
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor($r('app.color.TEXT_TERTIARY'))
                  .width('100%')
                  .margin({ top: DesignDimens.SPACE_MD })
              } else if (this.modelsLoadFailed) {
                Text(resString('setting_23'))
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor($r('app.color.PRIMARY'))
                  .padding({ top: 12, bottom: 12 })
                  .margin({ top: DesignDimens.SPACE_XS })
                  .onClick(() => {
                    this.loadModels()
                  })
              } else if (this.modelsEmpty) {
                Text(resString('setting_25'))
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor($r('app.color.TEXT_TERTIARY'))
                  .width('100%')
                  .margin({ top: DesignDimens.SPACE_MD })
              } else {
                ApiConfigDropdown({
                  label: resString('api_config_catalog_vendor_label'),
                  titles: this.vendorTitles(),
                  subtitles: this.vendorSubtitles(),
                  selectedIndex: this.selectedVendorIndex,
                  placeholder: resString('api_config_catalog_vendor_placeholder'),
                  enabled: true,
                  isError: this.fieldError === 'MODEL_CONFIG',
                  onSelect: (index: number): void => {
                    this.selectVendor(index)
                  }
                })

                ApiConfigDropdown({
                  label: resString('api_config_catalog_model_label'),
                  titles: this.modelTitles(),
                  subtitles: this.modelSubtitles(),
                  selectedIndex: this.selectedModelIndex(),
                  placeholder: this.selectedGroup() === null
                    ? resString('api_config_catalog_model_disabled_hint')
                    : resString('api_config_catalog_model_placeholder'),
                  enabled: this.selectedGroup() !== null,
                  isError: this.fieldError === 'MODEL_CONFIG',
                  onSelect: (index: number): void => {
                    this.selectModel(index)
                  }
                })

                // 已选回显（点选即时反馈）
                if (this.selectedModelIndex() >= 0) {
                  Text(this.selectedEchoText())
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor($r('app.color.PRIMARY'))
                    .width('100%')
                    .margin({ top: DesignDimens.SPACE_XS })
                }
                if (this.fieldError === 'MODEL_CONFIG') {
                  Text(resString('setting_16'))
                    .fontSize(DesignFontSize.BODY_SMALL)
                    .fontColor($r('app.color.DANGER'))
                    .width('100%')
                    .margin({ top: DesignDimens.SPACE_XS })
                }
              }
              Text(resString('setting_27'))
                .fontSize(DesignFontSize.CAPTION)
                .fontColor($r('app.color.TEXT_TERTIARY'))
                .width('100%')
                .margin({ top: DesignDimens.SPACE_XS })
            } else {
              // ---- 自定义接入：接入点 + 模型名 ----
              this.FormLabel(resString('api_config_base_url_label'))
              TextInput({ text: this.editBaseUrl, placeholder: resString('api_config_base_url_hint') })
                .height(48)
                .fontSize(DesignFontSize.BODY)
                .backgroundColor($r('app.color.SURFACE_VARIANT'))
                .borderRadius(DesignDimens.INPUT_RADIUS)
                .border(this.inputBorder(this.baseUrlFocused))
                .placeholderColor($r('app.color.TEXT_TERTIARY'))
                .placeholderFont({ size: DesignFontSize.BODY })
                .onFocus(() => {
                  this.baseUrlFocused = true
                })
                .onBlur(() => {
                  this.baseUrlFocused = false
                })
                .onChange((value: string) => {
                  this.editBaseUrl = value
                  this.clearFieldError('BASE_URL')
                })
              Text(resString('setting_28'))
                .fontSize(DesignFontSize.CAPTION)
                .fontColor($r('app.color.TEXT_TERTIARY'))
                .width('100%')
                .margin({ top: DesignDimens.SPACE_XS })

              Column().height(DesignDimens.SPACE_LG)

              this.FormLabel(resString('api_config_model_label'))
              TextInput({ text: this.editModel, placeholder: resString('api_config_model_hint') })
                .height(48)
                .fontSize(DesignFontSize.BODY)
                .backgroundColor($r('app.color.SURFACE_VARIANT'))
                .borderRadius(DesignDimens.INPUT_RADIUS)
                .border(this.inputBorder(this.modelFocused))
                .placeholderColor($r('app.color.TEXT_TERTIARY'))
                .placeholderFont({ size: DesignFontSize.BODY })
                .onFocus(() => {
                  this.modelFocused = true
                })
                .onBlur(() => {
                  this.modelFocused = false
                })
                .onChange((value: string) => {
                  this.editModel = value
                  this.clearFieldError('MODEL')
                })
              if (this.fieldError === 'MODEL') {
                Text(resString('api_config_error_model_empty'))
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .fontColor($r('app.color.DANGER'))
                  .width('100%')
                  .margin({ top: DesignDimens.SPACE_XS })
              }
            }

            Column().height(DesignDimens.SPACE_LG)

            // ---- API Key（密码框可切明文；两模式均必填）----
            this.FormLabel(resString('api_config_key_label'))
            Row() {
              TextInput({
                text: this.editApiKey,
                placeholder: this.config !== null
                  ? resString('api_config_key_hint_edit')
                  : resString('api_config_key_hint_new')
              })
                .type(this.showApiKeyPlain ? InputType.Normal : InputType.Password)
                .height(48)
                .fontSize(DesignFontSize.BODY)
                .backgroundColor($r('app.color.SURFACE_VARIANT'))
                .borderRadius(DesignDimens.INPUT_RADIUS)
                .border(this.inputBorder(this.apiKeyFocused))
                .placeholderColor($r('app.color.TEXT_TERTIARY'))
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
                  this.clearFieldError('API_KEY')
                })

              Stack({ alignContent: Alignment.Center }) {
                SymbolGlyph(this.showApiKeyPlain ? $r('sys.symbol.eye_slash') : $r('sys.symbol.eye'))
                  .fontSize(20)
                  .fontColor([$r('app.color.TEXT_SECONDARY')])
              }
              .width(DesignDimens.BACK_HIT_AREA)
              .height(DesignDimens.BACK_HIT_AREA)
              .accessibilityDescription(this.showApiKeyPlain
                ? resString('api_config_key_hide')
                : resString('api_config_key_show'))
              .onClick(() => {
                this.showApiKeyPlain = !this.showApiKeyPlain
              })
            }
            .width('100%')
            .alignItems(VerticalAlign.Center)

            Text(resString('setting_32'))
              .fontSize(DesignFontSize.CAPTION)
              .fontColor($r('app.color.TEXT_TERTIARY'))
              .width('100%')
              .margin({ top: DesignDimens.SPACE_XS })

            // ---- 测试连接即时反馈 ----
            if (this.testFeedback !== '') {
              Row({ space: DesignDimens.SPACE_XXS }) {
                SymbolGlyph(this.testFeedbackOk
                  ? $r('sys.symbol.checkmark')
                  : $r('sys.symbol.exclamationmark_triangle_fill'))
                  .fontSize(14)
                  .fontColor([this.testFeedbackOk ? $r('app.color.SUCCESS') : $r('app.color.DANGER')])
                Text(this.testFeedback)
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor(this.testFeedbackOk ? $r('app.color.SUCCESS') : $r('app.color.DANGER'))
                  .layoutWeight(1)
              }
              .width('100%')
              .alignItems(VerticalAlign.Center)
              .margin({ top: DesignDimens.SPACE_MD })
            }

            // ---- 操作按钮：测试连接 + 保存 ----
            Row({ space: DesignDimens.SPACE_SM }) {
              AppButton({
                text: resString('api_config_test'),
                variant: AppButtonVariant.SECONDARY,
                loading: this.testing,
                click: () => {
                  this.testConnection()
                }
              })
                .layoutWeight(1)
              AppButton({
                text: resString('api_config_save'),
                loading: this.saving,
                click: () => {
                  this.saveConfig()
                }
              })
                .layoutWeight(1)
            }
            .width('100%')
            .margin({ top: DesignDimens.SPACE_LG })

            // 服务端拒绝 / 网络异常反馈（保存）：inline 展示后端原因文案
            if (this.saveFeedback !== '') {
              Text(this.saveFeedback)
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontColor($r('app.color.DANGER'))
                .width('100%')
                .margin({ top: DesignDimens.SPACE_SM })
            }

            // ---- 删除配置（仅已配置时）----
            if (this.config !== null) {
              AppButton({
                text: resString('api_config_delete'),
                variant: AppButtonVariant.SECONDARY,
                contentColor: $r('app.color.DANGER_INK'),
                loading: this.deleting,
                click: () => {
                  this.deleteDialogController.open()
                }
              })
                .margin({ top: DesignDimens.SPACE_SM })
            }
          }
          .width('100%')
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_XS,
            bottom: DesignDimens.SPACE_XL
          })
        }
        .layoutWeight(1)
        .scrollBar(BarState.Off)
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
      // 返回本页时重拉解读模型配置（保存/删除在别处改动后回显保持新鲜）
      this.loadConfig()
    })
  }

  /** 摘要主行：目录模式「vendor · modelName」；自定义模式 customModel */
  private summaryModelText(): string {
    const cfg = this.config
    if (cfg === null) {
      return ''
    }
    if (cfg.modelConfigId !== null && cfg.modelConfigId !== undefined) {
      const vendor = cfg.vendor ?? ''
      const modelName = cfg.modelName ?? ''
      if (vendor !== '' && modelName !== '') {
        return `${vendor} · ${modelName}`
      }
      return vendor !== '' ? vendor : modelName
    }
    return cfg.customModel ?? ''
  }

  /** 摘要副行：自定义接入点 + 掩码 */
  private summarySubtitle(): string {
    const cfg = this.config
    if (cfg === null) {
      return ''
    }
    const parts: Array<string> = []
    if (cfg.modelConfigId === null || cfg.modelConfigId === undefined) {
      const baseUrl = cfg.customBaseUrl ?? ''
      if (baseUrl !== '') {
        parts.push(baseUrl)
      }
    }
    const mask = cfg.apiKeyMask ?? ''
    if (mask !== '') {
      parts.push(mask)
    }
    return parts.join('  ·  ')
  }

  /** 已选回显文案：已选：厂商 · 模型 */
  private selectedEchoText(): string {
    const group = this.selectedGroup()
    const vendor = group === null ? '' : this.displayVendor(group)
    const index = this.selectedModelIndex()
    const modelTitle = group !== null && index >= 0 ? (group.models[index].name ?? '') : ''
    return `${resString('api_config_selected_prefix')}${vendor} · ${modelTitle}`
  }

  /** 输入即清除对应字段错误（规范 §3.7「输入中不报错」） */
  private clearFieldError(field: ApiConfigField): void {
    if (this.fieldError === field) {
      this.fieldError = ''
    }
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
 * 目录模式选择器 — 字段 + 内联展开选项面板（厂商 / 模型共用）。
 *
 * 形态：只读字段行（label 在上、值/占位 + 下拉箭头）→ 点击展开选项卡片（主标题 + 副文本，
 * 选中项主色浅底 + 尾部对勾）→ 点选项回填并收起。禁用态（未选厂商）降透明不可展开。
 * 选项区超高时内部滚动（厂商 11 个、单厂商模型最多 13 个）。
 */
@Component
struct ApiConfigDropdown {
  @Prop label: string = ''
  @Prop titles: Array<string> = []
  @Prop subtitles: Array<string> = []
  @Prop selectedIndex: number = -1
  @Prop placeholder: string = ''
  @Prop enabled: boolean = true
  @Prop isError: boolean = false
  @State expanded: boolean = false
  onSelect: (index: number) => void = () => {
  }

  build() {
    Column() {
      Text(this.label)
        .fontSize(DesignFontSize.BODY_SMALL)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor($r('app.color.TEXT_SECONDARY'))
        .width('100%')
        .margin({ bottom: DesignDimens.SPACE_XS })

      // 只读字段行
      Row() {
        Text(this.selectedIndex >= 0 && this.selectedIndex < this.titles.length
          ? this.titles[this.selectedIndex] : this.placeholder)
          .fontSize(DesignFontSize.BODY)
          .fontColor(this.selectedIndex >= 0 && this.selectedIndex < this.titles.length
            ? $r('app.color.TEXT_PRIMARY') : $r('app.color.TEXT_TERTIARY'))
          .layoutWeight(1)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        SymbolGlyph($r('sys.symbol.chevron_down'))
          .fontSize(16)
          .fontColor([$r('app.color.TEXT_TERTIARY')])
          .margin({ left: DesignDimens.SPACE_XS })
          .rotate({ angle: this.expanded ? 180 : 0 })
          .animation({ duration: 200, curve: Curve.EaseOut })
      }
      .width('100%')
      .height(52)
      .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD })
      .backgroundColor($r('app.color.SURFACE_VARIANT'))
      .borderRadius(DesignDimens.INPUT_RADIUS)
      .border({
        width: 1,
        color: this.isError ? $r('app.color.DANGER') : $r('app.color.OUTLINE')
      })
      .opacity(this.enabled ? 1 : 0.5)
      .onClick(() => {
        if (this.enabled) {
          this.expanded = !this.expanded
        }
      })

      // 选项面板（内联展开）
      if (this.expanded && this.titles.length > 0) {
        Column() {
          Scroll() {
            Column() {
              ForEach(this.titles, (title: string, index: number) => {
                Row() {
                  Column() {
                    Text(title)
                      .fontSize(DesignFontSize.BODY)
                      .fontColor($r('app.color.TEXT_PRIMARY'))
                      .maxLines(1)
                      .textOverflow({ overflow: TextOverflow.Ellipsis })
                    if (index < this.subtitles.length && this.subtitles[index] !== '') {
                      Text(this.subtitles[index])
                        .fontSize(DesignFontSize.CAPTION)
                        .fontColor($r('app.color.TEXT_TERTIARY'))
                        .maxLines(1)
                        .textOverflow({ overflow: TextOverflow.Ellipsis })
                    }
                  }
                  .alignItems(HorizontalAlign.Start)
                  .layoutWeight(1)
                  .margin({ right: DesignDimens.SPACE_SM })

                  if (index === this.selectedIndex) {
                    SymbolGlyph($r('sys.symbol.checkmark'))
                      .fontSize(16)
                      .fontColor([$r('app.color.PRIMARY')])
                      .margin({ left: DesignDimens.SPACE_XS })
                  }
                }
                .width('100%')
                .constraintSize({ minHeight: DesignDimens.TOUCH_TARGET_MIN })
                .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: 10, bottom: 10 })
                .borderRadius(DesignDimens.RADIUS_SMALL)
                .backgroundColor(index === this.selectedIndex
                  ? $r('app.color.PRIMARY_LIGHT')
                  : Color.Transparent)
                .onClick(() => {
                  this.expanded = false
                  this.onSelect(index)
                })
              })
            }
            .width('100%')
          }
          .width('100%')
          .constraintSize({ maxHeight: 260 })
          .scrollBar(BarState.Off)
        }
        .width('100%')
        .backgroundColor($r('app.color.SURFACE'))
        .borderRadius(DesignDimens.RADIUS_MD)
        .border({ width: 1, color: $r('app.color.OUTLINE') })
        .margin({ top: DesignDimens.SPACE_XS })
        .shadow({
          radius: DesignElevation.FLOAT_SHADOW_RADIUS,
          color: $r('app.color.SHADOW_COLOR_FLOAT'),
          offsetX: 0,
          offsetY: DesignElevation.FLOAT_SHADOW_OFFSET
        })
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}

/**
 * 洗衣订单状态卡片提供方（FormExtensionAbility）。
 *
 * 生命周期与刷新链路（查证依据：SDK @ohos.app.form.FormExtensionAbility.d.ts、
 * @ohos.app.form.formProvider.d.ts:191 updateForm(formId, formBindingData): Promise<void>、
 * 参考项目 WisdomDaily/healthy-life-arkts 同构实现）：
 * - onAddForm：卡片添加到桌面时回调，**必须同步返回** binding —— 先推「正在加载…」占位，
 *   随后异步拉数据经 updateForm 覆盖；本次 want 携带 FormParam.IDENTITY_KEY（formId）与
 *   FormParam.DIMENSION_KEY（尺寸枚举，仅此一次可得），尺寸经 WidgetFormStore 登记落盘，
 *   供后续无参定时刷新读回装配版式；
 * - onUpdateForm：系统按 form_config.json 的 updateDuration 定时回调（本卡配 updateDuration=1，
 *   即每 30 分钟一次 —— SDK toolchains/modulecheck/forms.json schema 查证：updateDuration
 *   单位为 30 分钟、卡片最大刷新频率即每 30 分钟一次；hvigor 校验 updateEnabled=true 时
 *   updateDuration 与 scheduledUpdateTime 不得同时为空，本卡二者取其一），拉数据后 updateForm；
 * - onRemoveForm：卡片删除，注销尺寸登记。
 *
 * 刷新失败策略：try-catch 全包、仅日志留痕（logger 现有封装），不重试不刷屏 ——
 * 系统会按定时策略再次回调 onUpdateForm；卡片保持上一次成功数据或加载占位。
 *
 * 进程模型：卡片跑在独立 FormExtensionAbility 进程（与 App 进程不共享内存单例），
 * 网络与 preferences 均在本进程内初始化（见 WidgetDataLoader）。
 */

import { formBindingData, FormExtensionAbility, formInfo, formProvider } from '@kit.FormKit'
import { Want } from '@kit.AbilityKit'
import { logger } from '../utils/Logger'
import { WidgetBinding, buildLoadingBinding, withCardWide } from '../widget/WidgetOrderModel'
import { loadWidgetBinding } from '../widget/WidgetDataLoader'
import { dimensionOf, removeForm, saveFormDimension } from '../widget/WidgetFormStore'

const TAG: string = 'EntryFormAbility'

/** formInfo.FormDimension.Dimension_2_4 的枚举值（formInfo.d.ts:1141，2x4 宽卡） */
const DIMENSION_2X4: number = 3

/** 卡片 binding → createFormBindingData 入参（键与 WidgetCard.ets 的 @LocalStorageProp 一一对应） */
function bindingToRecord(binding: WidgetBinding): Record<string, Object> {
  const record: Record<string, Object> = {
    'cardWide': binding.cardWide,
    'hasOrder': binding.hasOrder,
    'statusText': binding.statusText,
    'statusColor': binding.statusColor,
    'infoText': binding.infoText,
    'summaryText': binding.summaryText,
    'targetUri': binding.targetUri
  }
  return record
}

export default class EntryFormAbility extends FormExtensionAbility {
  /**
   * 添加卡片：登记尺寸 → 同步返回加载占位 → 异步刷新真实数据。
   * formId 缺失（want 异常）时仍返回占位（卡片有默认形态，异步刷新静默放弃）。
   */
  onAddForm(want: Want): formBindingData.FormBindingData {
    const formId: string = EntryFormAbility.formIdOf(want)
    const dimension: number = EntryFormAbility.dimensionOf(want)
    if (formId.length > 0) {
      // 先登记尺寸再刷新：定时刷新读回的版式依赖这次登记，顺序不能反
      saveFormDimension(this.context, formId, dimension)
    }
    // 同步返回占位（onAddForm 不允许阻塞等网络）；binding 里的版式按本次尺寸装配
    const placeholder = withCardWide(buildLoadingBinding(), dimension)
    if (formId.length > 0) {
      this.refresh(formId)
    }
    return formBindingData.createFormBindingData(bindingToRecord(placeholder))
  }

  /** 定时刷新回调：读回登记尺寸 → 拉数据 → updateForm 推送 */
  onUpdateForm(formId: string): void {
    this.refresh(formId)
  }

  /** 卡片删除：注销尺寸登记 */
  onRemoveForm(formId: string): void {
    removeForm(this.context, formId)
  }

  /** 系统查询卡片状态：常备就绪（刷新失败不改变卡片可用性，由占位/旧数据兜底） */
  onAcquireFormState(want: Want): formInfo.FormState {
    return formInfo.FormState.READY
  }

  /**
   * 拉取订单数据并推送指定卡片（单卡粒度；失败仅日志，等系统下次定时回调重试）。
   * 卡片进程无 UI：任何失败都不弹窗不重试，未登录/无订单/网络失败统一收敛为引导态。
   */
  private async refresh(formId: string): Promise<void> {
    try {
      if (formId.length === 0) {
        return
      }
      const binding: WidgetBinding = await loadWidgetBinding(this.context)
      // 定时刷新链路拿不到尺寸，按登记值装配版式（未登记按窄卡兜底）
      const dimension = dimensionOf(this.context, formId)
      const shaped = dimension === DIMENSION_2X4
        ? withCardWide(binding, DIMENSION_2X4)
        : binding
      await formProvider.updateForm(formId,
        formBindingData.createFormBindingData(bindingToRecord(shaped)))
      logger.debug(TAG, `卡片 ${formId} 已刷新：${shaped.statusText}`)
    } catch (err) {
      logger.error(TAG, '刷新洗衣状态卡片失败',
        err instanceof Error ? err.message : '')
    }
  }

  /** formId 藏在 want.parameters（键 FormParam.IDENTITY_KEY，字符串） */
  private static formIdOf(want: Want): string {
    const params = want.parameters
    if (params === undefined) {
      return ''
    }
    const raw: Object = params[formInfo.FormParam.IDENTITY_KEY]
    return typeof raw === 'string' ? raw : ''
  }

  /** 卡片尺寸枚举值（FormParam.DIMENSION_KEY，仅 onAddForm 可得；number 或数字字符串） */
  private static dimensionOf(want: Want): number {
    const params = want.parameters
    if (params === undefined) {
      return 0
    }
    const raw: Object | undefined = params[formInfo.FormParam.DIMENSION_KEY]
    if (typeof raw === 'number') {
      return raw
    }
    if (typeof raw === 'string') {
      const parsed = parseInt(raw, 10)
      return Number.isNaN(parsed) ? 0 : parsed
    }
    return 0
  }
}

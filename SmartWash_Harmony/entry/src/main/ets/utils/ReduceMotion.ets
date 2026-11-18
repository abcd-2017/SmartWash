// reduced motion（系统「减少动态效果」）统一判定与降级规格
// 对齐安卓 common/utils/AnimationUtils.kt：isReduceMotionEnabled + LocalReduceMotion + motionSpec
//
// 安卓读 Settings.Global.ANIMATOR_DURATION_SCALE == 0f；鸿蒙等价 API 为
// @ohos.settings 的 settings.display.ANIMATOR_DURATION_SCALE（since 7，21 起标记 deprecated 但当前 SDK 17 可用）。
// 该键在系统「减少动态效果」开启时为 0（动画立即结束）。读取失败一律降级为 false，不阻断业务。
//
// 共享方式：EntryAbility 冷启动调 refresh() 写入 AppStorage；组件在 aboutToAppear / onAppear 读 isEnabled()。
// 注意：AppStorage 变更不会主动重建未订阅组件，故调用点均为「一次性读取 + 降级分支」，与安卓 remember(backStackEntry) 同口径。

import { common } from '@kit.AbilityKit'
import settings from '@ohos.settings'
import { MotionDuration, MotionTiming } from '../constant/DesignSystem'

/** AppStorage key：全局 reduced motion 开关（与 EntryAbility 预热、组件读取共用） */
export const REDUCE_MOTION_KEY: string = 'motionReduceEnabled'

/**
 * reduced motion 工具 — 全应用唯一入口。
 * spec() 把任意动效规格降级为 CrossfadeAlt（180ms Linear）：
 * 规范 §8「取消无限旋转 / 回弹 / 位移 / 缩放，保留文字、颜色、图标与状态可读性」。
 */
export class ReduceMotion {
  /** 当前是否开启减少动态效果（默认 false；读取失败同 false） */
  static isEnabled(): boolean {
    const value: boolean | undefined = AppStorage.get<boolean>(REDUCE_MOTION_KEY)
    return value ?? false
  }

  /**
   * 读取系统动画时长缩放并写入 AppStorage。
   * 缩放为 0（含 "0" / "0.0"）即视为开启减少动态效果。
   */
  static refresh(context: common.Context): void {
    try {
      const raw: string = settings.getValueSync(context, settings.display.ANIMATOR_DURATION_SCALE, '1')
      const scale: number = parseFloat(raw)
      const enabled: boolean = !Number.isNaN(scale) && scale === 0
      AppStorage.setOrCreate<boolean>(REDUCE_MOTION_KEY, enabled)
    } catch (err) {
      // 系统 API 不可用 / 无权限 / 返回非法值：保守降级为不启用，避免误关动画
      AppStorage.setOrCreate<boolean>(REDUCE_MOTION_KEY, false)
    }
  }

  /**
   * 动效规格降级选择器 — 对齐安卓 motionSpec(full)：
   * reduced motion 时返回 180ms 纯 Linear tween，否则原样返回完整规格。
   */
  static spec(full: AnimateParam): AnimateParam {
    if (!ReduceMotion.isEnabled()) {
      return full
    }
    return { duration: MotionDuration.CrossfadeAltMs, curve: Curve.Linear }
  }

  /**
   * 曲线型规格降级选择器 — 入参为 ICurve（如 springMotion）。
   * 非降级态包成 AnimateParam 返回；降级态返回 180ms 纯 Linear tween。
   */
  static specCurve(full: ICurve): AnimateParam {
    if (!ReduceMotion.isEnabled()) {
      return { curve: full }
    }
    return { duration: MotionDuration.CrossfadeAltMs, curve: Curve.Linear }
  }

  /**
   * 列表项进出场 / 位移过渡 — 对齐安卓 animateItem(placementSpec = motionSpec(defaultSpring()))。
   * 端限制：ArkUI 无 LazyForEach 项级 placement 动画，用插入/删除淡入淡出（弹簧）近似；
   * reduced motion 时按规范 §8 直接落位不动画（IDENTITY）。
   */
  static listItemTransition(): TransitionEffect {
    if (!ReduceMotion.isEnabled()) {
      return TransitionEffect.OPACITY.animation({ curve: MotionTiming.ITEM })
    }
    return TransitionEffect.IDENTITY
  }
}

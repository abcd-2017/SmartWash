/**
 * 解压实验舱公共工具 — 触感档位 + reduced motion 判定。
 *
 * 对齐安卓 feature/lab/util + common/utils/HapticUtils：
 * - 触感统一走 vibrator 时间振动（模拟器 / 部分设备不支持时静默忽略）；
 * - reduced motion 当前 SDK 无等价 API（同 divination 的 divReduceMotion 取舍），
 *   先返回常量 false，降级分支保留在调用点，待设置页接入后替换。
 */

import vibrator from '@ohos.vibrator'

/** 解压实验舱触感档位（对齐安卓 HapticUtils 的 LIGHT/MEDIUM/HEAVY/SUCCESS/SELECTION） */
export class LabHaptic {
  /** 轻触（点击 / 选择） */
  static light(): void {
    LabHaptic.run(10)
  }

  /** 中等（戳破中泡 / 全部戳完前的重档 / 播放切换） */
  static medium(): void {
    LabHaptic.run(20)
  }

  /** 重触（长按驱散 / 大泡戳破） */
  static heavy(): void {
    LabHaptic.run(30)
  }

  /** 成功（泡泡全部戳完） */
  static success(): void {
    LabHaptic.run(40)
  }

  /** 选择（切换元素 / 节奏型 / 预设） */
  static selection(): void {
    LabHaptic.run(10)
  }

  private static run(duration: number): void {
    try {
      vibrator.startVibration({ type: 'time', duration: duration }, { id: 0, usage: 'unknown' })
    } catch (err) {
      // 不支持触感（模拟器 / 部分设备）时忽略
    }
  }
}

/**
 * reduced motion 判定（对齐安卓 divReduceMotion 的既有取舍）。
 * 当前 SDK 无 ANIMATOR_DURATION_SCALE 等价 API，先返回常量 false；
 * 各页降级分支保留在调用点，待 API 可用或设置页接入后替换。
 */
export function labReduceMotion(): boolean {
  return false
}

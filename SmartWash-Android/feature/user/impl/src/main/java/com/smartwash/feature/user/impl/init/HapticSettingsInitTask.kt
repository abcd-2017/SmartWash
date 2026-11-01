package com.smartwash.feature.user.impl.init

import com.smartwash.common.init.InitTask
import com.smartwash.common.utils.HapticSettings
import com.smartwash.common.utils.SharePreferenceUtils
import javax.inject.Inject

/**
 * 触感反馈开关预热启动任务（2026-11-01 设置页接真实数据）：
 * 进程启动时把 DataStore 持久化的开关值载入 [HapticSettings] 内存态，
 * 让全应用热路径（View.performHaptic）免阻塞同步读取。
 *
 * 非阻塞低优先级（设置加载完成前的极短窗口内触感按默认开启执行，属可接受降级）；
 * 归属用户域——触感开关的持久化读写 UI 在本模块设置页。
 */
class HapticSettingsInitTask @Inject constructor(
    private val prefs: SharePreferenceUtils,
) : InitTask() {

    override val taskId = "haptic_settings"

    /** 非阻塞：不拦启动，读完后自然生效 */
    override val blocking = false

    /** 排在会话任务之后（session=10），避免与会话预热争抢 DataStore 句柄 */
    override val priority = 200

    override suspend fun execute() {
        HapticSettings.enabled = prefs.getData(HapticSettings.STORAGE_KEY, true)
    }
}

package com.smartwash.ui.page.update

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.work.WorkManager

/**
 * 变体入口（lite）：不接入 full 的自动热更新链路，但支持**手动**检查更新。
 *
 * - [UpdateFlowHost]：lite 无 :feature:update:impl（无 UpdateInitTask 启动静默检查、
 *   无 UpdateEventBus/UpdateViewModel），故不挂载事件驱动的更新流程；
 * - [rememberCheckUpdateContent]：lite 设置页自带「检查更新」行（见
 *   `com.smartwash.lite.ui.setting.LiteUpdateSection`），不消费 full 设置页的
 *   [com.smartwash.feature.user.impl.ui.LocalCheckUpdateContent] 插槽，返回 null。
 *
 * 手动检查更新复用 :feature:update:core 的 AppUpdateRepository（版本比较 + 预签名地址）
 * 与 ApkDownloadWorker（下载），安装经 ApkInstaller。
 */

@Composable
internal fun UpdateFlowHost(context: Context, workManager: WorkManager) {
    // lite 变体无自动热更新链路：不收集事件、不静默检查、不弹自动更新窗
}

@Composable
internal fun rememberCheckUpdateContent(): (@Composable () -> Unit)? = null

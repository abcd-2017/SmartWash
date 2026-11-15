package com.smartwash.ui.page.update

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.WorkManager
import com.smartwash.feature.update.event.UpdateEventBus
import com.smartwash.feature.update.ui.UpdateViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 变体入口（full）：组装热更新链路。
 *
 * - [UpdateFlowHost]：挂载更新流程（事件收集 → 下载调度 → 弹窗），供 MainActivity 在主题内调用；
 * - [rememberCheckUpdateContent]：设置页「检查更新」插槽内容（自动更新开关 + 检查更新行）。
 *
 * 这两个符号在 `app/src/lite` 有同名同签名实现（lite 为空实现），MainActivity（main 源集）
 * 只按契约调用，编译期由变体源集解析——从而 main 无需引用 :feature:update:impl 的 full 专属类型。
 */

/** 更新事件总线经 EntryPoint 取用，避免 main 源集直接依赖 full 专属类型。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateFlavorEntryPoint {
    fun updateEventBus(): UpdateEventBus
}

@Composable
internal fun UpdateFlowHost(context: Context, workManager: WorkManager) {
    val updateViewModel: UpdateViewModel = hiltViewModel()
    val updateEventBus = EntryPointAccessors.fromApplication(
        context.applicationContext,
        UpdateFlavorEntryPoint::class.java,
    ).updateEventBus()
    UpdateFlow(context, workManager, updateViewModel, updateEventBus)
}

@Composable
internal fun rememberCheckUpdateContent(): (@Composable () -> Unit)? {
    // Activity 级 ViewModel：与 UpdateFlowHost 取到同一实例（设置页行与弹窗状态同源）
    val updateViewModel: UpdateViewModel = hiltViewModel()
    return { UpdateSettingRows(updateViewModel) }
}

package com.smartwash.ui.page.workbench

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.smartwash.R

/** 工具卡图标容器语义色组（规范 §2.1 六色浅底，与设计稿 tint-g/b/c/v 对应） */
enum class WorkbenchTint {
    GREEN,
    BLUE,
    TEAL,
    PURPLE,
}

/** 工具点击行为：前三个为已落地的真实页面，其余在接口就绪前占位 */
enum class WorkbenchAction {
    /** 观象台首页（玄墨特色卡与「洗衣黄历」此前占位无关，观象台专属入口） */
    DIVINATION_HOME,

    /** 我的卦历 → 观象台卦历库 */
    DIV_HISTORY,

    /** AI 助手 → AI 工具工作台 */
    AI_ASSISTANT,

    /** 功能建设中（等后端工作台接口） */
    COMING_SOON,
}

/**
 * 工作台工具条目。
 * 当前为假数据（[WorkbenchViewModel] 内置默认列表）；后端工作台接口就绪后，
 * title/description 改服务端下发、跳转目标按 key 映射，本结构即接口 VO 的客户端映射层。
 */
data class WorkbenchTool(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val tint: WorkbenchTint,
    val action: WorkbenchAction,
)

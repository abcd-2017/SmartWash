package com.smartwash.feature.lab.ui.home

import androidx.lifecycle.ViewModel
import com.smartwash.feature.lab.LabToy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 实验舱首页 ViewModel。
 *
 * 纯本地静态入口配置：本模块无网络请求、无后端接口、无数据库，
 * 三个玩具入口为编译期固定列表（[LabToy]），经 StateFlow 暴露保持
 * 与其他页面一致的 MVVM 数据流形态（Page 只渲染，不持业务列表）。
 */
@HiltViewModel
class LabHomeViewModel @Inject constructor() : ViewModel() {

    /** 玩具入口卡列表（静态，顺序即展示序） */
    private val _toys = MutableStateFlow(LabToy.entries.toList())
    val toys: StateFlow<List<LabToy>> = _toys.asStateFlow()
}

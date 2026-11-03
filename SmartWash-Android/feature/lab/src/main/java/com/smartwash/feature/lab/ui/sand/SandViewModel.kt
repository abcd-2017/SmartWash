package com.smartwash.feature.lab.ui.sand

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 落沙实验室 ViewModel：持有元胞自动机内核 [SandSimulator]（纯 Kotlin，VM 作用域存活
 * ——旋转屏不失沙盒；tick 与笔刷由页面注入）与当前选中元素。
 * 模块约定：玩具状态会话内即弃，退出页面即释放，不持久化。
 */
@HiltViewModel
class SandViewModel @Inject constructor() : ViewModel() {

    /** 沙盒模拟内核（页面测量画布尺寸后经 [resize] 注入网格） */
    val simulator: SandSimulator = SandSimulator()

    private val _selectedElement = MutableStateFlow(SandElement.SAND)
    val selectedElement: StateFlow<SandElement> = _selectedElement.asStateFlow()

    /** 切换当前笔刷元素（页面据此重绘调色板选中态） */
    fun selectElement(element: SandElement) {
        _selectedElement.value = element
    }

    /** 清空沙盒（页面随后触发一次重绘） */
    fun clear() {
        simulator.clear()
    }
}

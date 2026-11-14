package com.smartwash.feature.lab.ui.fish

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 指尖鱼群 ViewModel：持有 boids 模拟内核（纯 Kotlin [BoidsSimulator]，
 * VM 作用域存活——旋转屏不失鱼群；tick 与交互由页面注入）。
 * 模块约定：状态会话内即弃，退出页面即释放。
 */
@HiltViewModel
class FishViewModel @Inject constructor() : ViewModel() {

    /** boids 模拟内核（resize 由页面注入画布尺寸后自动重铺鱼群） */
    val simulator: BoidsSimulator = BoidsSimulator()
}

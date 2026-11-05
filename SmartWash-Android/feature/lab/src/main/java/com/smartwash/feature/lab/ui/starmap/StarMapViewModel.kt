package com.smartwash.feature.lab.ui.starmap

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 掌上星图 UI 状态快照。
 *
 * [rotationMatrix] 为已按屏幕方向重映射的 3x3 姿态矩阵（行主序，世界 = R × 设备）；
 * 无传感器时为 null，页面回退到 [StarMapEngine.fallbackRotationMatrix] 的静态星空。
 * [selectedName] / [selectedMagnitude] 为点星选中的天体（null 表示未选中）。
 */
data class StarMapUiState(
    /** null = 尚未检测；true = 可用；false = 无磁力计 / 加速度计（降级静态星空） */
    val sensorsAvailable: Boolean? = null,
    val nightVision: Boolean = false,
    val rotationMatrix: FloatArray? = null,
    val selectedName: String? = null,
    val selectedMagnitude: Float = 0f,
) {
    // FloatArray 用引用相等即可：传感器每次产出新数组，页面需要每次都重绘。
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StarMapUiState) return false
        return sensorsAvailable == other.sensorsAvailable &&
            nightVision == other.nightVision &&
            (rotationMatrix === other.rotationMatrix) &&
            selectedName == other.selectedName &&
            selectedMagnitude == other.selectedMagnitude
    }

    override fun hashCode(): Int {
        var result = sensorsAvailable.hashCode()
        result = 31 * result + nightVision.hashCode()
        result = 31 * result + System.identityHashCode(rotationMatrix)
        result = 31 * result + (selectedName?.hashCode() ?: 0)
        result = 31 * result + selectedMagnitude.hashCode()
        return result
    }
}

/**
 * 掌上星图 ViewModel：持有 [SensorManager] 采样（加速度计 + 磁力计，
 * [SensorManager.SENSOR_DELAY_GAME]），经 [StarMapEngine] 解算姿态并回投到 UI 状态。
 *
 * 注册 / 注销由页面 `DisposableEffect` 驱动（[start] / [stop]），跟随页面生命周期；
 * 组合期无副作用。模块约定：玩具状态会话内即弃，退出页面即释放。
 */
@HiltViewModel
class StarMapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _state = MutableStateFlow(StarMapUiState())
    val state: StateFlow<StarMapUiState> = _state.asStateFlow()

    @Volatile
    private var gravity: FloatArray? = null

    @Volatile
    private var magnetic: FloatArray? = null

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    gravity = event.values.clone()
                    updateRotation()
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    magnetic = event.values.clone()
                    updateRotation()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /**
     * 开始采样（页面进入时调用）。无磁力计 / 加速度计（模拟器或部分设备）时
     * 标记不可用并切到静态星空，不崩。
     */
    fun start() {
        val acc = accelerometer
        val mag = magnetometer
        if (acc == null || mag == null) {
            _state.value = _state.value.copy(sensorsAvailable = false)
            return
        }
        val manager = sensorManager ?: return
        _state.value = _state.value.copy(sensorsAvailable = true)
        manager.registerListener(sensorListener, acc, SensorManager.SENSOR_DELAY_GAME)
        manager.registerListener(sensorListener, mag, SensorManager.SENSOR_DELAY_GAME)
    }

    /** 停止采样（页面退出时调用；重复调用安全） */
    fun stop() {
        sensorManager?.unregisterListener(sensorListener)
    }

    /** 夜视模式开关（页面同时发 SELECTION 触觉） */
    fun toggleNightVision() {
        _state.value = _state.value.copy(nightVision = !_state.value.nightVision)
    }

    /** 选中天体（页面已按投影坐标取到最近星） */
    fun selectStar(name: String, magnitude: Float) {
        _state.value = _state.value.copy(selectedName = name, selectedMagnitude = magnitude)
    }

    /** 清空选中（点按空白处） */
    fun clearSelection() {
        _state.value = _state.value.copy(selectedName = null, selectedMagnitude = 0f)
    }

    private fun updateRotation() {
        val g = gravity ?: return
        val m = magnetic ?: return
        val matrix = StarMapEngine.computeRotationMatrix(g, m) ?: return
        // P0 崩溃修复：[context] 是 @ApplicationContext，Android 17 起 Context.getDisplay() 对非
        // 可视化 Context 直接抛 UnsupportedOperationException（复现路径：工作台 → 解压实验舱 →
        // 滚动到底 → 掌上星图 → 传感器回调即崩）。改用 DisplayManager 按 DEFAULT_DISPLAY 取 display，
        // Application Context 全程可用，空安全回退 ROTATION_0 保持原有降级语义。
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val rotation = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
            ?: Surface.ROTATION_0
        val remapped = StarMapEngine.remapForScreenRotation(matrix, rotation)
        _state.value = _state.value.copy(rotationMatrix = remapped)
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}

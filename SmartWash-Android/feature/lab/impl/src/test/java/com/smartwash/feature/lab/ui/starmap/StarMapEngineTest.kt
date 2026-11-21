package com.smartwash.feature.lab.ui.starmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 星图引擎 JVM 单测（纯 Kotlin 数学内核：旋转矩阵构造 / 天体投影 / 最近命中）。
 *
 * 锚点契约：无传感器降级矩阵（正北、仰角 45°）；设备姿态矩阵与
 * SensorManager.getRotationMatrix 等价（East = 重力×磁场，North = East×重力，Up = -重力）；
 * 投影只输出视方向前半球（z > 0.02）且落在画布边缘容差内的天体。
 */
class StarMapEngineTest {

    private fun assertArrayEquals(expected: FloatArray, actual: FloatArray, delta: Float) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) ->
            assertTrue("期望 $e 实际 $a", abs(e - a) <= delta)
        }
    }

    // ==================== rotationMatrixFromOrientation ====================

    @Test
    fun `方位 90 度仰角 0 度的旋转矩阵逐项精确`() {
        // az=90°: cosAz=0, sinAz=1; el=0: cosEl=1, sinEl=0
        assertArrayEquals(
            floatArrayOf(0f, 0f, -1f, -1f, 0f, 0f, 0f, 1f, 0f),
            StarMapEngine.rotationMatrixFromOrientation(azimuthDeg = 90f, altitudeDeg = 0f),
            1e-5f,
        )
    }

    @Test
    fun `仰角 90 度（天顶）旋转矩阵第三列为正上方`() {
        val matrix = StarMapEngine.rotationMatrixFromOrientation(azimuthDeg = 0f, altitudeDeg = 90f)
        // Up（第三行）= (0, cos90=0?, ...)：el=90 → cosEl≈0, sinEl≈1 → 第三行 ≈ (0,0,-1) 的相反方向
        assertEquals(-1f, matrix[8], 1e-5f) // -sinEl = -1
        assertEquals(0f, matrix[6], 1e-5f)
        assertEquals(0f, matrix[7], 1e-5f)
    }

    @Test
    fun `无传感器降级矩阵为正北仰角 45 度`() {
        val fallback = StarMapEngine.fallbackRotationMatrix()
        val expected = StarMapEngine.rotationMatrixFromOrientation(azimuthDeg = 0f, altitudeDeg = 45f)
        assertArrayEquals(expected, fallback, 0f)
        // 45° 时第三行 Up = (0, cos45, -sin45)
        assertEquals(1f / kotlin.math.sqrt(2f), fallback[7], 1e-5f)
        assertEquals(-1f / kotlin.math.sqrt(2f), fallback[8], 1e-5f)
    }

    // ==================== computeRotationMatrix ====================

    @Test
    fun `重力与磁场正交时姿态矩阵符合右手系构造`() {
        // 设备平放屏幕朝上：重力指向地心 (0,0,-1)，磁场水平指北 (0,-1,0)
        val matrix = StarMapEngine.computeRotationMatrix(
            gravity = floatArrayOf(0f, 0f, -1f),
            magnetic = floatArrayOf(0f, -1f, 0f),
        )
        assertNotNull(matrix)
        // East = A×E = (-1,0,0)；North = East×A = (0,-1,0)；Up = -A = (0,0,1)
        assertArrayEquals(
            floatArrayOf(-1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f),
            matrix!!,
            1e-5f,
        )
    }

    @Test
    fun `重力与磁场平行或过小时返回 null`() {
        // 平行：叉积零向量无法归一
        assertNull(StarMapEngine.computeRotationMatrix(floatArrayOf(0f, 0f, -1f), floatArrayOf(0f, 0f, -2f)))
        // 模长过小
        assertNull(StarMapEngine.computeRotationMatrix(floatArrayOf(0f, 0f, -1e-9f), floatArrayOf(0f, -1f, 0f)))
        // 长度不足
        assertNull(StarMapEngine.computeRotationMatrix(floatArrayOf(0f, 0f), floatArrayOf(0f, -1f, 0f)))
    }

    // ==================== projectSky ====================

    @Test
    fun `投影确定性 固定时刻两次投影结果完全一致`() {
        val matrix = StarMapEngine.fallbackRotationMatrix()
        val epoch = 1_763_500_000_000L // 固定 epoch（ms）
        val first = StarMapEngine.projectSky(matrix, widthPx = 1080f, heightPx = 2400f, fovDegrees = 60f, epochMillis = epoch)
        val second = StarMapEngine.projectSky(matrix, widthPx = 1080f, heightPx = 2400f, fovDegrees = 60f, epochMillis = epoch)
        assertEquals(first, second)
    }

    @Test
    fun `投影含恒星且北极星在无传感器降级视场内`() {
        val matrix = StarMapEngine.fallbackRotationMatrix()
        val stars = StarMapEngine.projectSky(
            rotationMatrix = matrix,
            widthPx = 1080f,
            heightPx = 2400f,
            fovDegrees = 60f,
            epochMillis = 1_763_500_000_000L,
        )
        val names = stars.map { it.name }.toSet()
        // 降级视向为正北仰角 45°（北京纬度），北极星（赤纬 89.26°，高度≈纬度）必在视场内
        assertTrue("北极星应在视场内", "北极星" in names)
        // 投影出的每个天体都来自已知星表或太阳/月亮/五大行星，不出现未知天体
        val known = StarMapEngine.BRIGHT_STARS.map { it.name }.toSet() +
            setOf("太阳", "月亮", "水星", "金星", "火星", "木星", "土星")
        assertTrue("投影天体应全部来自已知星表", names.all { it in known })
        stars.forEach { star ->
            assertTrue(star.alpha in 0f..1f)
            assertTrue(star.radiusPx > 0f)
        }
    }

    @Test
    fun `投影结果都在画布边缘容差内且为前半球`() {
        val matrix = StarMapEngine.fallbackRotationMatrix()
        val width = 1080f
        val height = 2400f
        // 私有常量 STAR_MARGIN_PX = 64（此处按实现值对齐，漂移时本测试作为回归信号）
        val margin = 64f
        val stars = StarMapEngine.projectSky(matrix, width, height, fovDegrees = 60f, epochMillis = 1_763_500_000_000L)
        assertTrue(stars.isNotEmpty())
        stars.forEach { star ->
            assertTrue(star.x >= -margin && star.x <= width + margin)
            assertTrue(star.y >= -margin && star.y <= height + margin)
        }
    }

    @Test
    fun `非法输入投影返回空列表`() {
        assertTrue(
            StarMapEngine.projectSky(
                rotationMatrix = FloatArray(3), // 长度不足
                widthPx = 1080f, heightPx = 2400f, fovDegrees = 60f, epochMillis = 0L,
            ).isEmpty(),
        )
        assertTrue(
            StarMapEngine.projectSky(
                rotationMatrix = StarMapEngine.fallbackRotationMatrix(),
                widthPx = 0f, heightPx = 2400f, fovDegrees = 60f, epochMillis = 0L,
            ).isEmpty(),
        )
    }

    // ==================== findNearest ====================

    private fun star(name: String, x: Float, y: Float) = StarMapEngine.ProjectedStar(
        name = name, magnitude = 1f, kind = StarMapEngine.Kind.STAR,
        x = x, y = y, radiusPx = 4f, alpha = 1f,
    )

    @Test
    fun `最近命中返回距离最小的天体`() {
        val stars = listOf(star("远", 500f, 500f), star("近", 110f, 120f))
        assertEquals("近", StarMapEngine.findNearest(stars, x = 100f, y = 100f, maxDistancePx = 1000f)?.name)
    }

    @Test
    fun `超出最大命中距离返回 null`() {
        val stars = listOf(star("a", 130f, 100f))
        assertNull(StarMapEngine.findNearest(stars, x = 100f, y = 100f, maxDistancePx = 29.9f))
        // 恰好在边界上（距离 30 <= 30）仍命中
        assertEquals("a", StarMapEngine.findNearest(stars, x = 100f, y = 100f, maxDistancePx = 30f)?.name)
    }

    @Test
    fun `并列距离取列表中后出现的天体（小于等于判定）`() {
        val stars = listOf(star("先", 110f, 100f), star("后", 90f, 100f))
        // distSq 相等时 `<=` 会覆盖前值：并列距离由后出现者胜（实现语义，作为回归锚点固化）
        assertEquals("后", StarMapEngine.findNearest(stars, x = 100f, y = 100f, maxDistancePx = 50f)?.name)
    }

    @Test
    fun `空列表返回 null`() {
        assertNull(StarMapEngine.findNearest(emptyList(), 0f, 0f, 100f))
    }
}

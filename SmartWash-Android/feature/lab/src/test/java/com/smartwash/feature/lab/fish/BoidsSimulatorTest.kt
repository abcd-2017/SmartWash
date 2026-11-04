package com.smartwash.feature.lab.fish

import com.smartwash.feature.lab.ui.fish.BoidsSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * BoidsSimulator 锚点单测（JVM）：均匀网格邻居查询与暴力全扫等价（空间细分不漏不重）、
 * 感知半径边界、三规则行为锚点（聚集收敛/冲击波径向/边界回转）与同 seed 确定性。
 * 内核为纯 Kotlin（零 Compose/零 Android 依赖），可直跑 JVM。
 */
class BoidsSimulatorTest {

    /** 小池 + 3 条鱼：便于手工布点与断言 */
    private fun simulator(fishCount: Int = 3, seed: Long = 42L): BoidsSimulator =
        BoidsSimulator(
            random = Random(seed),
            params = BoidsSimulator.Params(fishCount = fishCount),
        )

    /** 暴力全扫参照实现（与内核同判定：平方距离 <= r²，不含自身） */
    private fun bruteForceNeighbors(sim: BoidsSimulator, target: BoidsSimulator.Boid): List<BoidsSimulator.Boid> {
        val r2 = 56f * 56f
        return sim.boids.filter { it !== target }.filter {
            val dx = it.x - target.x
            val dy = it.y - target.y
            dx * dx + dy * dy <= r2
        }
    }

    @Test
    fun `均匀网格邻居查询与暴力全扫等价`() {
        val sim = simulator(fishCount = 40)
        sim.resize(400f, 400f)
        // 随机分布后推进数帧（鱼群跨 cell 流动），逐条断言网格查询 == 暴力全扫
        repeat(10) { sim.tick(16L) }
        sim.boids.forEach { boid ->
            sim.rebuildGrid()
            val gridResult = sim.neighborsOf(boid)
            val expected = bruteForceNeighbors(sim, boid)
            assertEquals(expected.toSet(), gridResult.toSet())
        }
    }

    @Test
    fun `跨 cell 近邻不漏且恰在半径外的不算邻居`() {
        val sim = simulator(fishCount = 3)
        sim.resize(400f, 400f)
        val a = sim.boids[0]
        val b = sim.boids[1]
        val c = sim.boids[2]
        // a 在 cell (0,0) 靠右下边缘；b 跨在相邻 cell 内但距离 < 感知半径 56 → 必须查到
        a.x = 55f; a.y = 55f; a.vx = 0f; a.vy = 0f
        b.x = 58f; b.y = 58f
        c.x = 200f; c.y = 200f
        sim.rebuildGrid()
        val neighbors = sim.neighborsOf(a)
        assertTrue("跨 cell 的近邻 b 必须在邻居表内", b in neighbors)
        assertFalse("远处的 c 不算邻居", c in neighbors)
        // 半径外（56 * 1.05）不算邻居
        c.x = 55f + 56f * 1.05f
        c.y = 55f
        sim.rebuildGrid()
        assertFalse("感知半径外的 c 不算邻居", c in sim.neighborsOf(a))
    }

    @Test
    fun `聚集力使鱼群随时间收拢（平均邻居数上升）`() {
        val sim = simulator(fishCount = 60)
        sim.resize(600f, 600f)
        fun avgNeighbors(): Float {
            sim.rebuildGrid()
            return sim.boids.map { sim.neighborsOf(it).size }.average().toFloat()
        }
        val before = avgNeighbors()
        repeat(180) { sim.tick(16L) }
        val after = avgNeighbors()
        assertTrue(
            "聚集 3 秒后平均邻居数应上升（before=$before after=$after）",
            after > before,
        )
    }

    @Test
    fun `冲击波把附近鱼沿径向推离炸点`() {
        val sim = simulator(fishCount = 5)
        sim.resize(600f, 600f)
        val center = 300f
        // 鱼分散在炸点周围不同方向（同点重合无径向方向可言，pulse 跳过 d=0）
        val offsets = listOf(
            0f to 10f, 0f to -10f, 10f to 0f, -10f to 0f, 0f to 40f,
        )
        sim.boids.forEachIndexed { index, boid ->
            boid.x = center + offsets[index].first
            boid.y = center + offsets[index].second
            boid.vx = 0f
            boid.vy = 0f
        }
        sim.pulse(center, center)
        sim.boids.forEach { boid ->
            // 与自身相对位置点积 > 0：速度分量沿径向朝外
            val radial = boid.vx * (boid.x - center) + boid.vy * (boid.y - center)
            assertTrue("炸点附近的鱼应有径向向外的速度（radial=$radial）", radial > 0f)
        }
    }

    @Test
    fun `边界柔和转向把冲向边缘的鱼拉回池内`() {
        val sim = simulator(fishCount = 1)
        sim.resize(600f, 600f)
        val fish = sim.boids[0]
        // 放在左边缘内、速度朝左（出界方向）
        fish.x = 10f
        fish.y = 300f
        fish.vx = -50f
        fish.vy = 0f
        sim.tick(16L)
        // 转向力朝内：vx 应已增大（仍在池内）
        assertTrue("左边缘鱼应获得朝内的转向力（vx=${fish.vx}）", fish.vx > -50f)
        // 位置不穿出
        assertTrue(fish.x >= 0f)
    }

    @Test
    fun `同 seed 两次模拟状态完全一致（确定性）`() {
        fun run(): List<FloatArray> {
            val sim = simulator(fishCount = 30, seed = 7L)
            sim.resize(500f, 500f)
            repeat(60) { sim.tick(16L) }
            return sim.boids.map { floatArrayOf(it.x, it.y, it.vx, it.vy) }
        }
        val a = run()
        val b = run()
        assertEquals(a.size, b.size)
        a.zip(b).forEach { (pa, pb) ->
            pa.zip(pb).forEach { (va, vb) ->
                assertTrue("同 seed 模拟必须逐位一致", abs(va - vb) < 1e-4f)
            }
        }
    }

    @Test
    fun `速度限幅在 min 与 max 之间且夹界不穿出`() {
        val sim = simulator(fishCount = 50)
        sim.resize(500f, 500f)
        sim.pulse(250f, 250f)
        repeat(60) { sim.tick(50L) }
        sim.boids.forEach { fish ->
            val speed = kotlin.math.sqrt(fish.vx * fish.vx + fish.vy * fish.vy)
            assertTrue("速度不得低于 minSpeed（$speed）", speed >= 26f - 1e-2f)
            assertTrue("速度不得高于 maxSpeed（$speed）", speed <= 96f + 1e-2f)
            assertTrue(fish.x in 0f..500f)
            assertTrue(fish.y in 0f..500f)
        }
    }
}

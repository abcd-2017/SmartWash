package com.smartwash.feature.lab.ui.sand

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * 落沙实验室元胞自动机 JVM 单测（纯 Kotlin 内核，随机源注入固定种子 + 概率参数置
 * 全 1/全 0 消除随机性，逐帧断言确定性行为）。
 *
 * 锚点契约：每帧顺序 = 反应 → 下落/侧流 → 上升 → 衰老；
 * 反应规则：水+污渍→溶解、洗衣粉+水→泡沫、火+水→蒸汽且火熄灭、火+织物→点燃、
 * 蒸汽到顶/寿命到→凝结为水；沙/水直落，水被堵后侧流。
 */
class SandSimulatorTest {

    /** 运动全关（上升/快升概率 0）、反应全开、笔刷 100% 命中——反应确定、元素不漂移 */
    private fun reactionParams(
        stainResistance: Int = 2,
        steamLife: Int = 3,
        fireLife: Int = 3,
    ) = SandSimulator.Params(
        stainResistance = stainResistance,
        steamLife = steamLife,
        fireLife = fireLife,
        stainDissolveChance = 1f,
        detergentFoamChance = 1f,
        fireWaterChance = 1f,
        fireFabricChance = 1f,
        foamRiseChance = 0f,
        fireRiseChance = 0f,
        steamRiseChance = 0f,
        brushDensity = 1f,
    )

    private fun simulator(params: SandSimulator.Params) = SandSimulator(random = Random(42), params = params)

    /** 底行铺织物墙，把待测元素钉在底行（织物静止不迁移，只与火反应） */
    private fun pinBottom(sim: SandSimulator, skipCols: Set<Int> = emptySet()) {
        for (col in 0 until sim.cols) {
            if (col !in skipCols) sim.paint(col, sim.rows - 1, SandElement.FABRIC, radius = 0)
        }
    }

    // ==================== 网格基础 ====================

    @Test
    fun `resize 与 clear 与越界读取`() {
        val sim = simulator(reactionParams())
        sim.resize(cols = 5, rows = 5)
        assertEquals(5, sim.cols)
        assertEquals(5, sim.rows)

        sim.paint(2, 2, SandElement.SAND, radius = 0)
        assertEquals(SandElement.SAND, sim.elementAt(2, 2))
        sim.clear()
        assertEquals(SandElement.EMPTY, sim.elementAt(2, 2))

        // 越界（含负坐标）一律 EMPTY，脏坐标不崩
        assertEquals(SandElement.EMPTY, sim.elementAt(-1, 0))
        assertEquals(SandElement.EMPTY, sim.elementAt(0, -1))
        assertEquals(SandElement.EMPTY, sim.elementAt(5, 0))
        assertEquals(SandElement.EMPTY, sim.elementAt(0, 5))
    }

    @Test
    fun `resize 非法尺寸不生效且 tick 不崩`() {
        val sim = simulator(reactionParams())
        sim.resize(cols = 0, rows = 5)
        assertEquals(0, sim.cols)
        sim.paint(0, 0, SandElement.SAND) // 未初始化时笔刷为 no-op
        sim.tick()                          // 未初始化时不推进
        assertEquals(SandElement.EMPTY, sim.elementAt(0, 0))
    }

    // ==================== 下落运动 ====================

    @Test
    fun `沙粒逐帧直落到底堆积`() {
        val sim = SandSimulator(
            random = Random(42),
            params = reactionParams().copy(brushDensity = 1f),
        )
        sim.resize(cols = 3, rows = 4)
        sim.paint(1, 0, SandElement.SAND, radius = 0)

        sim.tick() // 反应(no-op) → 下落：直落一格
        assertEquals(SandElement.SAND, sim.elementAt(1, 1))
        assertEquals(SandElement.EMPTY, sim.elementAt(1, 0))

        sim.tick()
        sim.tick()
        // 到底（row 3）后不再迁移
        assertEquals(SandElement.SAND, sim.elementAt(1, 3))
        sim.tick()
        assertEquals(SandElement.SAND, sim.elementAt(1, 3))
    }

    @Test
    fun `水被堵后侧流 不再直落`() {
        val sim = SandSimulator(random = Random(42), params = reactionParams())
        sim.resize(cols = 5, rows = 5)
        // 底行除中间列外全铺织物；水钉在 (2,4)
        pinBottom(sim, skipCols = setOf(2))
        sim.paint(2, 4, SandElement.WATER, radius = 0)

        sim.tick()
        // 下方是空（skip 列）——先直落一格试试？不：底行就是最后一行，无下一行，只能侧流；
        // 左右都是织物墙 → 水不迁移
        assertEquals(SandElement.WATER, sim.elementAt(2, 4))
    }

    // ==================== 反应规则 ====================

    @Test
    fun `火遇水 水变蒸汽且火熄灭`() {
        val sim = SandSimulator(random = Random(42), params = reactionParams())
        sim.resize(cols = 5, rows = 5)
        // 底行：织物(0) 水(1) 火(2) 织物(3) 织物(4)——水/火互为邻接且被织物钉住
        sim.paint(0, 4, SandElement.FABRIC, radius = 0)
        sim.paint(1, 4, SandElement.WATER, radius = 0)
        sim.paint(2, 4, SandElement.FIRE, radius = 0)
        sim.paint(3, 4, SandElement.FABRIC, radius = 0)
        sim.paint(4, 4, SandElement.FABRIC, radius = 0)

        sim.tick()
        assertEquals(SandElement.STEAM, sim.elementAt(1, 4))
        assertEquals(SandElement.EMPTY, sim.elementAt(2, 4)) // 火熄灭
        assertEquals(SandElement.FABRIC, sim.elementAt(0, 4)) // 邻接火? 织物(0,4)与火(2,4)不相邻，未被点燃
    }

    @Test
    fun `火点燃相邻织物 且每帧只点燃首个命中目标`() {
        val sim = SandSimulator(random = Random(42), params = reactionParams())
        sim.resize(cols = 5, rows = 5)
        sim.paint(1, 4, SandElement.FABRIC, radius = 0)
        sim.paint(2, 4, SandElement.FIRE, radius = 0)
        sim.paint(3, 4, SandElement.FABRIC, radius = 0)

        sim.tick()
        assertEquals(SandElement.FIRE, sim.elementAt(1, 4)) // 首个邻接织物被点燃
        assertEquals(SandElement.FABRIC, sim.elementAt(3, 4)) // 同帧只点燃一个目标，右侧织物未波及
        assertEquals(SandElement.FIRE, sim.elementAt(2, 4)) // 火本体仍在（点燃不灭火）

        sim.tick()
        assertEquals(SandElement.FIRE, sim.elementAt(3, 4)) // 第二帧轮到右侧织物
    }

    @Test
    fun `洗衣粉遇水变泡沫`() {
        val sim = SandSimulator(random = Random(42), params = reactionParams())
        sim.resize(cols = 5, rows = 5)
        sim.paint(0, 4, SandElement.FABRIC, radius = 0)
        sim.paint(1, 4, SandElement.DETERGENT, radius = 0)
        sim.paint(2, 4, SandElement.WATER, radius = 0)
        sim.paint(3, 4, SandElement.FABRIC, radius = 0)

        sim.tick()
        assertEquals(SandElement.FOAM, sim.elementAt(1, 4)) // 洗衣粉变泡沫
        assertEquals(SandElement.WATER, sim.elementAt(2, 4)) // 水保留
        // 泡沫上升概率 0：不迁移
        assertEquals(SandElement.EMPTY, sim.elementAt(1, 3))
    }

    @Test
    fun `污渍邻水按溶解抗性逐步溶解`() {
        val sim = SandSimulator(
            random = Random(42),
            params = reactionParams(stainResistance = 2),
        )
        sim.resize(cols = 5, rows = 5)
        // 污渍钉在水正上方（8 邻接可反应），左右织物墙把水钉死——溶解后空位不被水侧流占领
        sim.paint(1, 4, SandElement.FABRIC, radius = 0)
        sim.paint(2, 4, SandElement.WATER, radius = 0)
        sim.paint(3, 4, SandElement.FABRIC, radius = 0)
        sim.paint(2, 3, SandElement.STAIN, radius = 0)

        sim.tick() // 抗性 2：第一次反应 aux 2→1，仍是污渍
        assertEquals(SandElement.STAIN, sim.elementAt(2, 3))
        sim.tick() // aux 1→0 ≤ 0 → 溶解为空
        assertEquals(SandElement.EMPTY, sim.elementAt(2, 3))
        assertEquals(SandElement.WATER, sim.elementAt(2, 4)) // 水不因溶解而消失（左右墙钉住不侧流）
    }

    // ==================== 蒸汽与衰老 ====================

    @Test
    fun `蒸汽到顶凝结为水`() {
        val sim = SandSimulator(random = Random(42), params = reactionParams())
        sim.resize(cols = 3, rows = 3)
        sim.paint(1, 0, SandElement.STEAM, radius = 0) // 直接铺在顶行（行 0）

        sim.tick() // moveRise：顶行无处再升；age：顶行蒸汽立即凝结
        assertEquals(SandElement.WATER, sim.elementAt(1, 0))
    }

    @Test
    fun `蒸汽寿命到凝结为水`() {
        val sim = SandSimulator(
            random = Random(42),
            params = reactionParams(steamLife = 1),
        )
        sim.resize(cols = 3, rows = 4)
        sim.paint(1, 2, SandElement.STEAM, radius = 0) // 中部行，未到顶

        sim.tick() // age：寿命 1 → 0 ≤ 0 → 凝结
        assertEquals(SandElement.WATER, sim.elementAt(1, 2))
    }

    @Test
    fun `泡沫与火寿命到消失`() {
        val sim = SandSimulator(
            random = Random(42),
            params = reactionParams().copy(foamLife = 1, fireLife = 1),
        )
        sim.resize(cols = 4, rows = 3)
        sim.paint(0, 1, SandElement.FOAM, radius = 0)
        sim.paint(2, 1, SandElement.FIRE, radius = 0)

        sim.tick() // age：寿命 1 → 0 → 归空
        assertEquals(SandElement.EMPTY, sim.elementAt(0, 1))
        assertEquals(SandElement.EMPTY, sim.elementAt(2, 1))
    }

    // ==================== 渲染映射 ====================

    @Test
    fun `render 按元素 id 索引调色板`() {
        val sim = SandSimulator(random = Random(42), params = reactionParams())
        sim.resize(cols = 2, rows = 1)
        sim.paint(0, 0, SandElement.SAND, radius = 0) // id = 1
        val palette = IntArray(SandElement.entries.size) { it * 100 }
        val buffer = sim.render(palette)
        assertEquals(palette[SandElement.SAND.id], buffer[0])
        assertEquals(palette[SandElement.EMPTY.id], buffer[1])
    }

    // ==================== SandElement 反查契约 ====================

    @Test
    fun `fromId 越界脏数据回落 EMPTY`() {
        assertEquals(SandElement.SAND, SandElement.fromId(1))
        assertEquals(SandElement.STEAM, SandElement.fromId(8))
        assertEquals(SandElement.EMPTY, SandElement.fromId(9))
        assertEquals(SandElement.EMPTY, SandElement.fromId(-1))
        assertEquals(SandElement.EMPTY, SandElement.fromId(999))
    }

    @Test
    fun `元素 id 与数组下标一致（0 起连续）`() {
        SandElement.entries.forEachIndexed { index, element ->
            assertEquals(index, element.id)
        }
    }
}

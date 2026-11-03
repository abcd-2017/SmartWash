package com.smartwash.feature.lab.ui.sand

import kotlin.random.Random

/**
 * 落沙实验室元素类型（9 种）。
 *
 * [id] 为稳定编号（与数组下标一致），[SandSimulator.render] 的调色板按 id 索引；
 * 页面调色板按 [entries] 顺序展示。运动/反应语义见 [SandSimulator]。
 */
enum class SandElement(val id: Int) {

    /** 空（渲染为透明，画布底色透出） */
    EMPTY(0),

    /** 沙：直落 + 斜落 */
    SAND(1),

    /** 水：直落 + 斜落 + 侧流；溶解污渍、与洗衣粉生成泡沫、遇火成蒸汽 */
    WATER(2),

    /** 洗衣粉：直落 + 斜落；遇水变泡沫 */
    DETERGENT(3),

    /** 泡沫：缓升；寿命到破裂消失 */
    FOAM(4),

    /** 污渍：静止；邻接水时按概率溶解 */
    STAIN(5),

    /** 织物：静止；邻接火时被点燃 */
    FABRIC(6),

    /** 火：上升；遇水熄灭、点燃织物 */
    FIRE(7),

    /** 蒸汽：快升；到顶或寿命到凝结为水 */
    STEAM(8);

    companion object {

        private val VALUES = entries.toTypedArray()

        /** 按 id 反查元素（越界回落 [EMPTY]，保证脏数据不崩） */
        fun fromId(id: Int): SandElement = VALUES.getOrElse(id) { EMPTY }
    }
}

/**
 * 落沙实验室元胞自动机内核（纯 Kotlin，零 Compose / 零 Android 依赖，便于 JVM 锚点单测）。
 *
 * 网格：[cols] × [rows]，行 0 为顶。[cells] 存元素 id（[ByteArray]），[aux] 存辅助状态
 * （寿命 / 溶解进度，语义随元素而定）。每帧顺序：反应 → 下落/侧流 → 上升 → 衰老。
 *
 * 运动：
 * - 沙 / 洗衣粉：直落，被挡时随机顺序斜落；
 * - 水：直落 → 斜落 → 左右侧流（同帧内 [movedFall] 防重复移动）；
 * - 泡沫：按概率缓升；火：按概率上升；蒸汽：按概率一次上升两格（快升），否则走 [moveUp]；
 * - 污渍 / 织物：静止。
 *
 * 反应（8 邻接 + 概率）：
 * 1. 水 + 污渍 → 污渍溶解（aux 递减至空）；
 * 2. 水 + 洗衣粉 → 洗衣粉变泡沫；
 * 3. 火 + 水 → 水变蒸汽、火熄灭；
 * 4. 火 + 织物 → 织物变火；
 * 5. 蒸汽到顶 / 寿命到 → 凝结为水。
 *
 * 坐标单位 = 网格 cell；概率与寿命收敛在 [Params]，测试可注入 [Random] 保证确定性。
 */
class SandSimulator(
    private val random: Random = Random.Default,
    private val params: Params = Params(),
) {

    /** 模拟参数（概率 ∈ [0,1]；寿命 / 溶解抗性单位 = tick 帧数） */
    data class Params(
        val foamLife: Int = 140,
        val steamLife: Int = 70,
        val fireLife: Int = 110,
        val stainResistance: Int = 6,
        val stainDissolveChance: Float = 0.35f,
        val detergentFoamChance: Float = 0.18f,
        val fireWaterChance: Float = 0.60f,
        val fireFabricChance: Float = 0.12f,
        val foamRiseChance: Float = 0.35f,
        val fireRiseChance: Float = 0.80f,
        val steamRiseChance: Float = 0.90f,
        val brushDensity: Float = 0.70f,
    )

    /** 网格列数（resize 注入；0 = 未初始化） */
    var cols: Int = 0
        private set

    /** 网格行数（行 0 为顶） */
    var rows: Int = 0
        private set

    private var cells: ByteArray = ByteArray(0)
    private var aux: IntArray = IntArray(0)
    private var movedFall: BooleanArray = BooleanArray(0)
    private var movedRise: BooleanArray = BooleanArray(0)

    /** 渲染缓冲（复用，避免每帧分配；调用方需立即消费，如 Bitmap.setPixels） */
    private var renderBuffer: IntArray = IntArray(0)

    /** 注入网格尺寸（首次或旋转/分屏变化时调用；会清空既有沙盒） */
    fun resize(cols: Int, rows: Int) {
        if (cols <= 0 || rows <= 0) return
        this.cols = cols
        this.rows = rows
        cells = ByteArray(cols * rows)
        aux = IntArray(cols * rows)
        movedFall = BooleanArray(cols * rows)
        movedRise = BooleanArray(cols * rows)
    }

    /** 清空沙盒（元素与辅助状态一并归零） */
    fun clear() {
        cells.fill(SandElement.EMPTY.id.toByte())
        aux.fill(0)
    }

    /** 读取指定格元素（越界返回 [SandElement.EMPTY]） */
    fun elementAt(col: Int, row: Int): SandElement {
        if (col < 0 || row < 0 || col >= cols || row >= rows) return SandElement.EMPTY
        return SandElement.fromId(cells[row * cols + col].toInt())
    }

    /**
     * 圆形概率笔刷：以 (col,row) 为圆心、[radius] 为半径，按 [Params.brushDensity]
     * 概率写入 [element]（留出自然笔触感；[SandElement.EMPTY] 即橡皮）。
     */
    fun paint(col: Int, row: Int, element: SandElement, radius: Int = DEFAULT_BRUSH_RADIUS) {
        if (cols <= 0 || rows <= 0 || radius < 0) return
        val r2 = radius * radius
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (dx * dx + dy * dy > r2) continue
                if (random.nextFloat() > params.brushDensity) continue
                val nc = col + dx
                val nr = row + dy
                if (nc < 0 || nr < 0 || nc >= cols || nr >= rows) continue
                replace(nr * cols + nc, element, lifetimeOf(element))
            }
        }
    }

    /** 推进一帧：反应 → 下落/侧流 → 上升 → 衰老 */
    fun tick() {
        if (cols <= 0 || rows <= 0) return
        react()
        moveFall()
        moveRise()
        age()
    }

    /**
     * 按调色板把当前网格渲染为 ARGB 数组（长度 = cols * rows）。
     * 返回复用的内部缓冲，调用方须立即消费（如 [android.graphics.Bitmap.setPixels]）。
     */
    fun render(palette: IntArray): IntArray {
        if (renderBuffer.size != cells.size) renderBuffer = IntArray(cells.size)
        for (index in cells.indices) {
            renderBuffer[index] = palette[SandElement.fromId(cells[index].toInt()).id]
        }
        return renderBuffer
    }

    // ---------- 反应 ----------

    private fun react() {
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val index = row * cols + col
                when (SandElement.fromId(cells[index].toInt())) {
                    SandElement.FIRE -> reactFire(col, row, index)
                    SandElement.WATER -> reactWater(col, row, index)
                    SandElement.STAIN -> reactStain(col, row, index)
                    SandElement.DETERGENT -> reactDetergent(col, row, index)
                    else -> Unit
                }
            }
        }
    }

    /** 火：先查水（水变蒸汽、火熄灭），否则点燃织物 */
    private fun reactFire(col: Int, row: Int, index: Int) {
        val water = firstNeighbor(col, row, SandElement.WATER)
        if (water >= 0 && random.nextFloat() < params.fireWaterChance) {
            replace(water, SandElement.STEAM, params.steamLife)
            replace(index, SandElement.EMPTY, 0)
            return
        }
        val fabric = firstNeighbor(col, row, SandElement.FABRIC)
        if (fabric >= 0 && random.nextFloat() < params.fireFabricChance) {
            replace(fabric, SandElement.FIRE, params.fireLife)
        }
    }

    /** 水：邻接火时自身变蒸汽、火熄灭（火未先处理的兜底路径） */
    private fun reactWater(col: Int, row: Int, index: Int) {
        val fire = firstNeighbor(col, row, SandElement.FIRE)
        if (fire >= 0 && random.nextFloat() < params.fireWaterChance) {
            replace(index, SandElement.STEAM, params.steamLife)
            replace(fire, SandElement.EMPTY, 0)
        }
    }

    /** 污渍：邻接水时按概率消耗溶解进度，归零变空 */
    private fun reactStain(col: Int, row: Int, index: Int) {
        if (firstNeighbor(col, row, SandElement.WATER) >= 0 &&
            random.nextFloat() < params.stainDissolveChance
        ) {
            aux[index] -= 1
            if (aux[index] <= 0) replace(index, SandElement.EMPTY, 0)
        }
    }

    /** 洗衣粉：邻接水时按概率变泡沫 */
    private fun reactDetergent(col: Int, row: Int, index: Int) {
        if (firstNeighbor(col, row, SandElement.WATER) >= 0 &&
            random.nextFloat() < params.detergentFoamChance
        ) {
            replace(index, SandElement.FOAM, params.foamLife)
        }
    }

    /** 8 邻域内首个目标元素下标（无则 -1） */
    private fun firstNeighbor(col: Int, row: Int, target: SandElement): Int {
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nc = col + dx
                val nr = row + dy
                if (nc < 0 || nr < 0 || nc >= cols || nr >= rows) continue
                val ni = nr * cols + nc
                if (SandElement.fromId(cells[ni].toInt()) == target) return ni
            }
        }
        return -1
    }

    // ---------- 运动 ----------

    /** 下落 / 侧流：自底向上遍历，斜落方向随机；水在被堵后左右侧流 */
    private fun moveFall() {
        movedFall.fill(false)
        for (row in rows - 1 downTo 0) {
            for (col in 0 until cols) {
                val index = row * cols + col
                if (movedFall[index]) continue
                when (SandElement.fromId(cells[index].toInt())) {
                    SandElement.SAND, SandElement.DETERGENT -> moveDown(index, col, row)
                    SandElement.WATER -> moveWater(index, col, row)
                    else -> Unit
                }
            }
        }
    }

    /** 沙 / 洗衣粉：直落 → 随机顺序斜落 */
    private fun moveDown(index: Int, col: Int, row: Int) {
        if (row + 1 >= rows) return
        val below = index + cols
        if (isEmpty(below)) {
            swap(index, below)
            movedFall[index] = true
            movedFall[below] = true
            return
        }
        val directions = diagonalDirections()
        for (dx in directions) {
            val nc = col + dx
            if (nc < 0 || nc >= cols) continue
            val target = below + dx
            if (isEmpty(target)) {
                swap(index, target)
                movedFall[index] = true
                movedFall[target] = true
                return
            }
        }
    }

    /** 水：直落 → 斜落 → 同排左右侧流 */
    private fun moveWater(index: Int, col: Int, row: Int) {
        if (row + 1 < rows) {
            val below = index + cols
            if (isEmpty(below)) {
                swap(index, below)
                movedFall[index] = true
                movedFall[below] = true
                return
            }
            val directions = diagonalDirections()
            for (dx in directions) {
                val nc = col + dx
                if (nc < 0 || nc >= cols) continue
                val target = below + dx
                if (isEmpty(target)) {
                    swap(index, target)
                    movedFall[index] = true
                    movedFall[target] = true
                    return
                }
            }
        }
        val directions = diagonalDirections()
        for (dx in directions) {
            val nc = col + dx
            if (nc < 0 || nc >= cols) continue
            val target = index + dx
            if (isEmpty(target)) {
                swap(index, target)
                movedFall[index] = true
                movedFall[target] = true
                return
            }
        }
    }

    /** 上升：自上而下遍历，防止同一帧内被反复抬升 */
    private fun moveRise() {
        movedRise.fill(false)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val index = row * cols + col
                if (movedRise[index]) continue
                when (SandElement.fromId(cells[index].toInt())) {
                    SandElement.FOAM ->
                        if (random.nextFloat() < params.foamRiseChance) moveUp(index, col, row)
                    SandElement.FIRE ->
                        if (random.nextFloat() < params.fireRiseChance) moveUp(index, col, row)
                    SandElement.STEAM -> moveSteam(index, col, row)
                    else -> Unit
                }
            }
        }
    }

    /** 蒸汽快升：两格皆空时一次上升两格，否则退化为常规上升 */
    private fun moveSteam(index: Int, col: Int, row: Int) {
        if (random.nextFloat() >= params.steamRiseChance) return
        if (row >= 2) {
            val oneAbove = index - cols
            val twoAbove = index - 2 * cols
            if (isEmpty(oneAbove) && isEmpty(twoAbove)) {
                swap(index, twoAbove)
                movedRise[index] = true
                movedRise[twoAbove] = true
                return
            }
        }
        moveUp(index, col, row)
    }

    /** 常规上升：直上 → 随机顺序斜上 */
    private fun moveUp(index: Int, col: Int, row: Int) {
        if (row - 1 < 0) return
        val above = index - cols
        if (isEmpty(above)) {
            swap(index, above)
            movedRise[index] = true
            movedRise[above] = true
            return
        }
        val directions = diagonalDirections()
        for (dx in directions) {
            val nc = col + dx
            if (nc < 0 || nc >= cols) continue
            val target = above + dx
            if (isEmpty(target)) {
                swap(index, target)
                movedRise[index] = true
                movedRise[target] = true
                return
            }
        }
    }

    // ---------- 衰老 ----------

    /** 寿命推进：泡沫/火归零变空；蒸汽到顶或寿命到变水 */
    private fun age() {
        for (index in cells.indices) {
            when (SandElement.fromId(cells[index].toInt())) {
                SandElement.FOAM -> {
                    aux[index] -= 1
                    if (aux[index] <= 0) replace(index, SandElement.EMPTY, 0)
                }
                SandElement.FIRE -> {
                    aux[index] -= 1
                    if (aux[index] <= 0) replace(index, SandElement.EMPTY, 0)
                }
                SandElement.STEAM -> {
                    aux[index] -= 1
                    if (index < cols || aux[index] <= 0) replace(index, SandElement.WATER, 0)
                }
                else -> Unit
            }
        }
    }

    // ---------- 基础工具 ----------

    /** 写入元素并初始化其辅助状态（寿命 / 溶解抗性） */
    private fun replace(index: Int, element: SandElement, lifetime: Int) {
        cells[index] = element.id.toByte()
        aux[index] = lifetime
    }

    /** 新元素对应的初始辅助状态 */
    private fun lifetimeOf(element: SandElement): Int = when (element) {
        SandElement.FOAM -> params.foamLife
        SandElement.STEAM -> params.steamLife
        SandElement.FIRE -> params.fireLife
        SandElement.STAIN -> params.stainResistance
        else -> 0
    }

    private fun isEmpty(index: Int): Boolean =
        index >= 0 && index < cells.size && cells[index] == SandElement.EMPTY.id.toByte()

    /** 交换两格元素与辅助状态 */
    private fun swap(a: Int, b: Int) {
        val cell = cells[a]
        cells[a] = cells[b]
        cells[b] = cell
        val state = aux[a]
        aux[a] = aux[b]
        aux[b] = state
    }

    /** 斜向移动方向（随机顺序，避免单侧堆积） */
    private fun diagonalDirections(): IntArray =
        if (random.nextBoolean()) DIRECTIONS else DIRECTIONS_REVERSED

    companion object {

        /** 默认笔刷半径（cell 数） */
        const val DEFAULT_BRUSH_RADIUS = 2

        private val DIRECTIONS = intArrayOf(-1, 1)
        private val DIRECTIONS_REVERSED = intArrayOf(1, -1)
    }
}

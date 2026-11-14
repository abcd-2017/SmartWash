package com.smartwash.feature.lab.ui.fish

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 指尖鱼群 boids 模拟内核（纯 Kotlin，零 Compose/零 Android 依赖，便于 JVM 锚点单测）。
 *
 * 三规则（Reynolds boids）：分离 / 对齐 / 聚集；邻居查询经**均匀网格空间细分**
 * （cell 尺寸 = 感知半径，仅查 3x3 邻域，整体 O(n)——每条鱼一次邻域查询共享给三规则）。
 * 边界取**柔和转向**（margin 内朝内转向力 + 位置夹界兜底）而非环绕：环绕需跨界
 * 邻居特判，柔和转向实现最稳且符合封闭"水池"意象。
 *
 * 坐标单位 = px（页面注入画布尺寸）；dt 单位 = 秒（tick 入参 ms）。
 * 指针交互：按下吸引 / [engageRepel] 切驱散（长按 >400ms 由页面判定）/ 抬起清除；
 * [pulse] = 双击径向冲击波（一次性速度冲量，随距离线性衰减，之后靠聚集力回拢）。
 */
class BoidsSimulator(
    private val random: Random = Random.Default,
    private val params: Params = Params(),
) {

    /** 模拟参数（坐标 px、速度 px/s、力 px/s²；默认值面向 ~1080x1600px 池） */
    data class Params(
        val fishCount: Int = 180,
        val perceptionRadius: Float = 56f,
        val separationRadius: Float = 22f,
        val maxSpeed: Float = 96f,
        val minSpeed: Float = 26f,
        val maxForce: Float = 300f,
        val edgeMargin: Float = 48f,
        val edgeTurn: Float = 260f,
        val separationWeight: Float = 1.7f,
        val alignmentWeight: Float = 1.0f,
        val cohesionWeight: Float = 0.85f,
        val attractStrength: Float = 320f,
        val repelStrength: Float = 420f,
        val pulseStrength: Float = 320f,
    )

    /**
     * 单条鱼：位置/速度 + 绘制侧数据。sizeFactor ∈ [0,1] 由页面映射 4~8dp；
     * colorIndex 指向页面令牌色组（primary/water 系）。字段 public var 供测试布点。
     */
    class Boid(
        val sizeFactor: Float,
        val colorIndex: Int,
        var x: Float = 0f,
        var y: Float = 0f,
        var vx: Float = 0f,
        var vy: Float = 0f,
    )

    /** 池宽/池高（px），resize 注入 */
    var width: Float = 0f
        private set
    var height: Float = 0f
        private set

    val boids: List<Boid>
        get() = _boids

    private val _boids = ArrayList<Boid>(params.fishCount)

    /** 鱼色档位数（页面映射 primary/water 系三种令牌色） */
    val colorChoices: Int = COLOR_CHOICES

    // 指针状态（attract/repel 二态；冲击波经 pulse 一次性施加，无在途状态）
    private var pointerX = 0f
    private var pointerY = 0f
    private var repelling = false
    private var pointerActive = false

    // 均匀网格（cell = perceptionRadius，桶惰性建列表复用）
    private var cols = 0
    private var rows = 0
    private var grid: Array<MutableList<Boid>?> = emptyArray()

    /** 注入画布尺寸（首次或尺寸变化时调用；重铺鱼群分布） */
    fun resize(width: Float, height: Float) {
        this.width = width
        this.height = height
        cols = max(1, (width / params.perceptionRadius).toInt() + 1)
        rows = max(1, (height / params.perceptionRadius).toInt() + 1)
        grid = arrayOfNulls(cols * rows)
        reset()
    }

    /** 均匀随机重铺鱼群（随机朝向、三色混排、尺寸随机档） */
    fun reset() {
        if (width <= 0f || height <= 0f) return
        _boids.clear()
        repeat(params.fishCount) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            _boids.add(
                Boid(
                    sizeFactor = random.nextFloat(),
                    colorIndex = random.nextInt(COLOR_CHOICES),
                    x = random.nextFloat() * width,
                    y = random.nextFloat() * height,
                    vx = cos(angle) * params.maxSpeed * 0.6f,
                    vy = sin(angle) * params.maxSpeed * 0.6f,
                )
            )
        }
    }

    // ---------- 指针交互 ----------

    /** 按下：吸引模式（指针位置固定为按下点） */
    fun pointerDown(x: Float, y: Float) {
        pointerX = x
        pointerY = y
        pointerActive = true
        repelling = false
    }

    /** 长按超阈值（页面判 400ms 后调用）：切驱散 */
    fun engageRepel(x: Float, y: Float) {
        pointerX = x
        pointerY = y
        pointerActive = true
        repelling = true
    }

    /** 抬起：清除指针力 */
    fun pointerUp() {
        pointerActive = false
        repelling = false
    }

    /** 双击：径向冲击波（一次性速度冲量，随距离线性衰减；炸开后靠聚集力回拢） */
    fun pulse(px: Float, py: Float) {
        if (width <= 0f) return
        _boids.forEach { boid ->
            val dx = boid.x - px
            val dy = boid.y - py
            val d = sqrt(dx * dx + dy * dy)
            if (d < 1e-3f) return@forEach
            val falloff = 1f - (d / PULSE_RADIUS).coerceIn(0f, 1f)
            if (falloff <= 0f) return@forEach
            val impulse = params.pulseStrength * falloff
            boid.vx += dx / d * impulse
            boid.vy += dy / d * impulse
        }
    }

    /**
     * 推进一帧。[dtMs] 为实际帧间隔（页面 clamp 1..50ms）；
     * 单帧实现超预算的降 tick 率由页面层负责，内核保持无时钟。
     */
    fun tick(dtMs: Long) {
        if (width <= 0f || height <= 0f || _boids.isEmpty()) return
        val dt = (dtMs / 1000f).coerceIn(0.001f, 0.05f)
        rebuildGrid()
        _boids.forEach { boid ->
            // 一次邻域查询共享给三规则（O(n) 关键：查询次数 = 鱼数）
            val neighbors = neighborsOf(boid)
            val (sx, sy) = steerSeparation(boid, neighbors)
            val (ax, ay) = steerAlignment(boid, neighbors)
            val (cx, cy) = steerCohesion(boid, neighbors)
            var fx = sx * params.separationWeight + ax * params.alignmentWeight +
                cx * params.cohesionWeight
            var fy = sy * params.separationWeight + ay * params.alignmentWeight +
                cy * params.cohesionWeight
            // 指针力（吸引朝指针 / 驱散背离指针，随距离线性衰减）
            if (pointerActive) {
                val dx = pointerX - boid.x
                val dy = pointerY - boid.y
                val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val falloff = 1f - (d / POINTER_RADIUS).coerceIn(0f, 1f)
                if (falloff > 0f) {
                    val strength =
                        (if (repelling) -params.repelStrength else params.attractStrength) * falloff
                    fx += dx / d * strength
                    fy += dy / d * strength
                }
            }
            // 边界柔和转向（margin 内朝内，越贴边转向越强）
            if (boid.x < params.edgeMargin) {
                fx += params.edgeTurn * (1f - boid.x / params.edgeMargin)
            }
            if (boid.x > width - params.edgeMargin) {
                fx -= params.edgeTurn * (1f - (width - boid.x) / params.edgeMargin)
            }
            if (boid.y < params.edgeMargin) {
                fy += params.edgeTurn * (1f - boid.y / params.edgeMargin)
            }
            if (boid.y > height - params.edgeMargin) {
                fy -= params.edgeTurn * (1f - (height - boid.y) / params.edgeMargin)
            }
            // 力限幅 → 速度积分 → 速度限幅（minSpeed 保证鱼群永不停摆）
            val fMag = sqrt(fx * fx + fy * fy)
            if (fMag > params.maxForce) {
                fx = fx / fMag * params.maxForce
                fy = fy / fMag * params.maxForce
            }
            boid.vx += fx * dt
            boid.vy += fy * dt
            val speed = sqrt(boid.vx * boid.vx + boid.vy * boid.vy).coerceAtLeast(1e-4f)
            val clamped = speed.coerceIn(params.minSpeed, params.maxSpeed)
            boid.vx = boid.vx / speed * clamped
            boid.vy = boid.vy / speed * clamped
            // 位置积分 + 夹界兜底（柔和转向为主，防个别鱼穿出）
            boid.x = (boid.x + boid.vx * dt).coerceIn(0f, width)
            boid.y = (boid.y + boid.vy * dt).coerceIn(0f, height)
        }
    }

    // ---------- 均匀网格空间细分（邻居查询 O(n)） ----------

    /** 坐标 → 网格桶下标（夹界防越格） */
    private fun cellIndexOf(x: Float, y: Float): Int {
        val cx = (x / params.perceptionRadius).toInt().coerceIn(0, cols - 1)
        val cy = (y / params.perceptionRadius).toInt().coerceIn(0, rows - 1)
        return cy * cols + cx
    }

    /** 重建网格桶（每 tick 一次；桶列表惰性建、跨帧复用减少分配） */
    internal fun rebuildGrid() {
        if (grid.size != cols * rows) grid = arrayOfNulls(cols * rows)
        grid.forEach { it?.clear() }
        _boids.forEach { boid ->
            val idx = cellIndexOf(boid.x, boid.y)
            val bucket = grid[idx] ?: ArrayList<Boid>().also { grid[idx] = it }
            bucket.add(boid)
        }
    }

    /**
     * 3x3 邻域内感知半径筛选（不含自身）。internal 供 JVM 锚点单测：
     * 断言与暴力全扫等价、跨 cell 的近邻不漏、恰在半径外的邻居排除。
     */
    internal fun neighborsOf(target: Boid): List<Boid> {
        if (cols <= 0 || rows <= 0) return emptyList()
        val cx = (target.x / params.perceptionRadius).toInt().coerceIn(0, cols - 1)
        val cy = (target.y / params.perceptionRadius).toInt().coerceIn(0, rows - 1)
        val result = ArrayList<Boid>()
        val r2 = params.perceptionRadius * params.perceptionRadius
        for (gy in max(0, cy - 1)..min(rows - 1, cy + 1)) {
            for (gx in max(0, cx - 1)..min(cols - 1, cx + 1)) {
                grid[gy * cols + gx]?.forEach { other ->
                    if (other === target) return@forEach
                    val dx = other.x - target.x
                    val dy = other.y - target.y
                    if (dx * dx + dy * dy <= r2) result.add(other)
                }
            }
        }
        return result
    }

    // ---------- 三规则（输入 = 预查询的邻域列表） ----------

    /** 分离：近邻（< separationRadius）按 1/d² 反比推开 */
    private fun steerSeparation(boid: Boid, neighbors: List<Boid>): Pair<Float, Float> {
        var fx = 0f
        var fy = 0f
        val r2 = params.separationRadius * params.separationRadius
        neighbors.forEach { other ->
            val dx = boid.x - other.x
            val dy = boid.y - other.y
            val d2 = dx * dx + dy * dy
            if (d2 > 1e-6f && d2 < r2) {
                val inv = 1f / d2
                fx += dx * inv
                fy += dy * inv
            }
        }
        return steerTo(boid, fx, fy)
    }

    /** 对齐：期望速度方向 = 邻均速方向 */
    private fun steerAlignment(boid: Boid, neighbors: List<Boid>): Pair<Float, Float> {
        if (neighbors.isEmpty()) return 0f to 0f
        var vx = 0f
        var vy = 0f
        neighbors.forEach { other ->
            vx += other.vx
            vy += other.vy
        }
        return steerTo(boid, vx / neighbors.size, vy / neighbors.size)
    }

    /** 聚集：期望方向 = 指向邻质心 */
    private fun steerCohesion(boid: Boid, neighbors: List<Boid>): Pair<Float, Float> {
        if (neighbors.isEmpty()) return 0f to 0f
        var px = 0f
        var py = 0f
        neighbors.forEach { other ->
            px += other.x
            py += other.y
        }
        return steerTo(boid, px / neighbors.size - boid.x, py / neighbors.size - boid.y)
    }

    /** 期望方向 → 转向力：want = 方向归一 * maxSpeed；steer = want - 当前速，限幅 maxForce */
    private fun steerTo(boid: Boid, dirX: Float, dirY: Float): Pair<Float, Float> {
        val mag = sqrt(dirX * dirX + dirY * dirY)
        if (mag < 1e-6f) return 0f to 0f
        var sx = dirX / mag * params.maxSpeed - boid.vx
        var sy = dirY / mag * params.maxSpeed - boid.vy
        val sMag = sqrt(sx * sx + sy * sy)
        if (sMag > params.maxForce) {
            sx = sx / sMag * params.maxForce
            sy = sy / sMag * params.maxForce
        }
        return sx to sy
    }

    companion object {

        /** 冲击波作用半径（px） */
        const val PULSE_RADIUS = 180f

        /** 指针力作用半径（px） */
        const val POINTER_RADIUS = 220f

        /** 鱼色档位数（页面令牌色组 primary/water 系） */
        const val COLOR_CHOICES = 3

        /** 朝向角（度）：页面绘制鱼形三角用（atan2 → 角度） */
        fun headingDegrees(boid: Boid): Float =
            atan2(boid.vy, boid.vx) * 180f / PI.toFloat()
    }
}

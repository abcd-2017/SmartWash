package com.smartwash.feature.lab.ui.painting

import kotlin.random.Random

/**
 * 今日一画 · 程序化水墨山水生成内核（纯 Kotlin，零 Compose / 零 Android 依赖，便于 JVM 单测）。
 *
 * 构图参考 LingDong-/shan-shui-inf（MIT）的分层山水思路，**重新实现**：
 * 远山（淡墨）→ 云雾（留白）→ 近山（中墨）→ 水面（淡墨横线）→
 * 近景树木 / 屋舍 / 小舟 / 飞鸟 / 红日 / 朱印。
 *
 * 确定性：所有随机数都来自 [PaintingRandom]（SplitMix64），同一种子必然生成同一幅画；
 * 屏幕 Canvas 与保存 Bitmap 两条渲染路径共用同一份场景数据（见 `ui/painting/PaintingPage.kt`）。
 *
 * 坐标系：归一化 `[0,1] × [0,1]`，y 向下；渲染时按画布宽高线性映射，
 * 因此同一场景可无损渲染到任意尺寸。
 */
class PaintingGenerator(private val seed: PaintingSeed) {

    private val random = PaintingRandom(seed.value)

    /** 生成一幅完整场景（元素顺序即绘制顺序，先画者被后画者遮挡） */
    fun generate(): PaintingScene {
        val far = buildRidge(
            yMin = 0.20f,
            yMax = 0.40f,
            baseY = 0.58f,
            alpha = 0.10f..0.16f,
            role = InkRole.INK_PALE,
        )
        val mist = buildMist()
        val near = buildRidge(
            yMin = 0.40f,
            yMax = 0.58f,
            baseY = 0.72f,
            alpha = 0.20f..0.30f,
            role = InkRole.INK_LIGHT,
        )
        return PaintingScene(
            seed = seed,
            mountainsFar = listOf(far),
            mountainsNear = listOf(near),
            mistBands = mist,
            waterLines = buildWaterLines(),
            trees = buildTrees(),
            houses = buildHouses(),
            boats = buildBoats(),
            birds = buildBirds(),
            sun = buildSun(),
            seal = buildSeal(),
        )
    }

    // ---------- 分层构建（调用顺序固定，保证确定性） ----------

    /**
     * 一条山脊：x 从 -0.05 走到 1.05，y 在 `[yMin,yMax]` 内做随机往返
     * （方向按概率翻转，形成山峰与山谷），底部 `[baseY]` 封闭成可填充的山体。
     */
    private fun buildRidge(
        yMin: Float,
        yMax: Float,
        baseY: Float,
        alpha: ClosedFloatingPointRange<Float>,
        role: InkRole,
    ): MountainLayer {
        val points = mutableListOf<Pt>()
        var x = -0.05f
        var y = random.nextRange(yMin, yMax)
        points += Pt(x, y)
        var direction = if (random.nextBoolean()) -1f else 1f
        while (x < 1.05f) {
            if (random.nextFloat() < 0.28f) direction = -direction
            y = (y + direction * random.nextRange(0.02f, 0.08f)).coerceIn(yMin, yMax)
            x += random.nextRange(0.05f, 0.12f)
            points += Pt(x, y)
        }
        return MountainLayer(
            ridge = points,
            baseY = baseY,
            role = role,
            alpha = random.nextRange(alpha.start, alpha.endInclusive),
        )
    }

    /** 云雾留白带：远山与近山之间的半透明宣纸带，拉开空间层次 */
    private fun buildMist(): List<MistBand> {
        val count = random.nextInt(2) + 1 // 1..2
        return (0 until count).map {
            MistBand(
                x = random.nextRange(-0.15f, 0.55f),
                y = random.nextRange(0.44f, 0.56f),
                width = random.nextRange(0.45f, 0.95f),
                height = random.nextRange(0.015f, 0.035f),
                alpha = random.nextRange(0.35f, 0.60f),
            )
        }
    }

    /** 水面淡墨横线：集中在画面下三分之一，长短不一 */
    private fun buildWaterLines(): List<WaterLine> {
        val count = random.nextInt(8) + 8 // 8..15
        return (0 until count).map {
            WaterLine(
                x = random.nextRange(-0.05f, 1.0f),
                y = random.nextRange(0.68f, 0.98f),
                length = random.nextRange(0.05f, 0.26f),
                width = random.nextRange(0.0015f, 0.0035f),
                role = InkRole.INK_PALE,
                alpha = random.nextRange(0.25f, 0.50f),
            )
        }
    }

    /** 近景树木：松（枝干笔触）与点叶树（青绿点簇）两种，按 y 排序保证近者压远者 */
    private fun buildTrees(): List<Tree> {
        val count = random.nextInt(4) + 3 // 3..6
        val trees = (0 until count).map {
            val style = if (random.nextBoolean()) TreeStyle.PINE else TreeStyle.DOT
            val x = random.nextRange(0.06f, 0.94f)
            val y = random.nextRange(0.64f, 0.90f)
            val height = random.nextRange(0.10f, 0.20f)
            Tree(
                x = x,
                y = y,
                height = height,
                lean = random.nextRange(-0.10f, 0.10f),
                style = style,
                role = if (random.nextFloat() < 0.5f) InkRole.INK_MID else InkRole.INK_DARK,
                alpha = random.nextRange(0.55f, 0.85f),
                foliage = if (style == TreeStyle.DOT) buildFoliage(x, y, height) else emptyList(),
            )
        }
        return trees.sortedBy { it.y }
    }

    /** 点叶树的叶簇：相对树基的归一化偏移，渲染时取青绿（[InkRole.VERDIGRIS]） */
    private fun buildFoliage(x: Float, y: Float, height: Float): List<Pt> {
        val count = random.nextInt(4) + 3 // 3..6
        return (0 until count).map {
            Pt(
                x = x + random.nextRange(-0.035f, 0.035f),
                y = y - height * random.nextRange(0.45f, 1.0f),
            )
        }
    }

    /** 近景屋舍：1..2 座，按 y 排序 */
    private fun buildHouses(): List<House> {
        val count = random.nextInt(2) + 1 // 1..2
        val houses = (0 until count).map {
            House(
                x = random.nextRange(0.12f, 0.88f),
                y = random.nextRange(0.72f, 0.90f),
                width = random.nextRange(0.08f, 0.13f),
                height = random.nextRange(0.045f, 0.075f),
                roofHeight = random.nextRange(0.030f, 0.050f),
                role = InkRole.INK_DARK,
            )
        }
        return houses.sortedBy { it.y }
    }

    /** 水面小舟：0..2 条，带朱砂帆 */
    private fun buildBoats(): List<Boat> {
        val count = random.nextInt(3) // 0..2
        return (0 until count).map {
            Boat(
                x = random.nextRange(0.15f, 0.85f),
                y = random.nextRange(0.72f, 0.93f),
                width = random.nextRange(0.09f, 0.16f),
                sailHeight = random.nextRange(0.05f, 0.09f),
                role = InkRole.INK_MID,
            )
        }
    }

    /** 天空飞鸟：0..4 只，极简「人」形两笔 */
    private fun buildBirds(): List<Bird> {
        val count = random.nextInt(5) // 0..4
        return (0 until count).map {
            Bird(
                x = random.nextRange(0.15f, 0.90f),
                y = random.nextRange(0.12f, 0.30f),
                size = random.nextRange(0.010f, 0.020f),
                role = InkRole.INK_MID,
            )
        }
    }

    /** 红日：70% 概率出现（朱砂圆，画面唯一暖色锚点） */
    private fun buildSun(): Sun? {
        if (random.nextFloat() >= 0.70f) return null
        return Sun(
            x = random.nextRange(0.15f, 0.85f),
            y = random.nextRange(0.08f, 0.22f),
            radius = random.nextRange(0.030f, 0.055f),
            role = InkRole.CINNABAR,
            alpha = random.nextRange(0.55f, 0.80f),
        )
    }

    /** 朱印：固定右下角，印文为朱文「山」字（渲染层用宣纸色三竖一横表达） */
    private fun buildSeal(): Seal = Seal(
        x = 0.90f,
        y = 0.94f,
        size = 0.045f,
        role = InkRole.CINNABAR,
    )
}

// ---------- 种子 ----------

/**
 * 画作种子：一个 Long 数字。日期种子（如 20261103）人类可读可分享，
 * 随机种子取六位数字，便于口头复述。
 */
@JvmInline
value class PaintingSeed(val value: Long) {

    companion object {

        /** 日期种子：`year*10000 + month*100 + day`（2026-11-03 → 20261103） */
        fun fromDate(year: Int, month: Int, day: Int): PaintingSeed =
            PaintingSeed(year * 10_000L + month * 100L + day)

        /** 随机六位数字种子（100000..999999） */
        fun random(): PaintingSeed = PaintingSeed(Random.nextLong(from = 100_000L, until = 1_000_000L))
    }
}

// ---------- 随机数 ----------

/**
 * SplitMix64 伪随机数生成器（纯 Kotlin，确定性）。
 * 不依赖 [Random] 的平台实现：同一种子在任何 JVM / Android 上产出完全一致的序列。
 *
 * 常量说明：Kotlin 的 Long 十六进制字面量不允许符号位置位（与 Java 不同），
 * 故三个魔数经无符号字面量 `[ULong.toLong]` 落地。
 */
class PaintingRandom(seed: Long) {

    private companion object {
        /** SplitMix64 黄金增量 γ */
        val GOLDEN_GAMMA: Long = 0x9E3779B97F4A7C15uL.toLong()

        /** 混合乘数 1 */
        val MIX1: Long = 0xBF58476D1CE4E5B9uL.toLong()

        /** 混合乘数 2 */
        val MIX2: Long = 0x94D049BB133111EBuL.toLong()
    }

    private var state = seed

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    /** `[0,1)` 双精度小数 */
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    fun nextFloat(): Float = nextDouble().toFloat()

    /** `[0, until)` 整数 */
    fun nextInt(until: Int): Int {
        require(until > 0) { "until must be > 0" }
        return (nextDouble() * until).toInt().coerceIn(0, until - 1)
    }

    fun nextBoolean(): Boolean = (nextLong() and 1L) != 0L

    /** `[min, max]` 闭区间浮点 */
    fun nextRange(min: Float, max: Float): Float = min + nextFloat() * (max - min)
}

// ---------- 场景模型（归一化坐标，渲染层按画布尺寸映射） ----------

/** 归一化点：x/y ∈ [0,1]，y 向下 */
data class Pt(val x: Float, val y: Float)

/**
 * 墨色角色：只描述「用哪种墨」，具体色值由渲染层从设计令牌解析
 * （宣纸底 / 淡墨 / 中墨 / 浓墨 / 朱砂 / 青绿），保证深浅两套主题下都成立。
 */
enum class InkRole { INK_PALE, INK_LIGHT, INK_MID, INK_DARK, CINNABAR, VERDIGRIS }

/** 一层山：山脊折线 + 山脚基准线（填充成山体）+ 墨色角色 + 透明度 */
data class MountainLayer(
    val ridge: List<Pt>,
    val baseY: Float,
    val role: InkRole,
    val alpha: Float,
)

/** 云雾留白带（宣纸色半透明横带） */
data class MistBand(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val alpha: Float,
)

/** 水面淡墨横线 */
data class WaterLine(
    val x: Float,
    val y: Float,
    val length: Float,
    val width: Float,
    val role: InkRole,
    val alpha: Float,
)

/** 树木样式：松（枝干笔触）/ 点叶（青绿点簇） */
enum class TreeStyle { PINE, DOT }

/** 近景树：`(x,y)` 为树基，`height` 为归一化高度，`lean` 为倾斜量 */
data class Tree(
    val x: Float,
    val y: Float,
    val height: Float,
    val lean: Float,
    val style: TreeStyle,
    val role: InkRole,
    val alpha: Float,
    val foliage: List<Pt> = emptyList(),
)

/** 近景屋舍：`(x,y)` 为屋底中心 */
data class House(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val roofHeight: Float,
    val role: InkRole,
)

/** 水面小舟：`(x,y)` 为船身中心，`sailHeight` 为帆高 */
data class Boat(
    val x: Float,
    val y: Float,
    val width: Float,
    val sailHeight: Float,
    val role: InkRole,
)

/** 飞鸟：`(x,y)` 为鸟身中心，`size` 为翼展 */
data class Bird(
    val x: Float,
    val y: Float,
    val size: Float,
    val role: InkRole,
)

/** 红日：`(x,y)` 为圆心，`radius` 为半径 */
data class Sun(
    val x: Float,
    val y: Float,
    val radius: Float,
    val role: InkRole,
    val alpha: Float,
)

/** 朱印：`(x,y)` 为印面中心，`size` 为边长 */
data class Seal(
    val x: Float,
    val y: Float,
    val size: Float,
    val role: InkRole,
)

/** 完整场景（元素顺序即绘制顺序） */
data class PaintingScene(
    val seed: PaintingSeed,
    val mountainsFar: List<MountainLayer>,
    val mountainsNear: List<MountainLayer>,
    val mistBands: List<MistBand>,
    val waterLines: List<WaterLine>,
    val trees: List<Tree>,
    val houses: List<House>,
    val boats: List<Boat>,
    val birds: List<Bird>,
    val sun: Sun?,
    val seal: Seal,
)

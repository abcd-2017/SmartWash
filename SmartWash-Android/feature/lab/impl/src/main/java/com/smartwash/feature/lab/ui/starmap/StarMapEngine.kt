package com.smartwash.feature.lab.ui.starmap

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 掌上星图引擎（纯 Kotlin，零 Compose / 零 Android 依赖，便于 JVM 锚点单测）。
 *
 * 三块职责：
 * 1. **姿态解算**：[computeRotationMatrix] 由加速度计 + 磁力计求设备姿态旋转矩阵
 *    （与 `SensorManager.getRotationMatrix` 同算法：East = 重力 × 磁场，North = East × 重力，
 *    Up = -重力，行主序拼装）；[remapForScreenRotation] 按屏幕方向重映射坐标轴
 *    （与 `SensorManager.remapCoordinateSystem` 同语义）。
 * 2. **星表与位置**：内置 56 颗亮星（名称 / 星等 / 赤经 / 赤纬）+ 太阳 / 月亮 / 五大行星。
 *    太阳 / 行星走 JPL 低精度开普勒根数（1800–2050 有效，行星精度约 1–数角分，太阳约 1 角分）；
 *    月亮走 Schlyter 低精度公式（含 12 项经度 / 5 项纬度摄动，精度约 1–2 角分）。
 *    行星位置仅作科普示意，**不可用于真实天文观测**。
 * 3. **投影**：[projectSky] 把赤经 / 赤纬经本地恒星时转为 ENU 单位向量，再以设备姿态为视中心
 *    做切平面（gnomonic）投影，只返回视场内的天体；星等映射为圆点半径与透明度。
 *
 * 坐标约定：
 * - 设备坐标：X 屏幕右、Y 屏幕上、Z 屏幕外（指向观察者）。
 * - 世界坐标：X 东、Y 北、Z 天（ENU）。
 * - 旋转矩阵行主序，映射 `世界 = R × 设备`。
 * - 视角方向（后置相机）= 设备 -Z 在世界中的方向 = `-R 的第 3 列`。
 *
 * MVP 近似项（均在注释标明精度）：
 * - 观测点固定为北京（[DEFAULT_LATITUDE_DEG] / [DEFAULT_LONGITUDE_DEG]），不申请定位权限；
 * - 忽略大气折射、章动、光行差；
 * - 行星 / 月亮用低精度近似公式。
 */
object StarMapEngine {

    // ==================== 对外常量 ====================

    /** 默认观测纬度（度，北纬为正）——MVP 固定观测点，不申请定位权限 */
    const val DEFAULT_LATITUDE_DEG = 39.9f

    /** 默认观测经度（度，东经为正）——MVP 固定观测点，不申请定位权限 */
    const val DEFAULT_LONGITUDE_DEG = 116.4f

    /** 天体类型（用于选中态与绘制的差异化表现） */
    enum class Kind { STAR, SUN, MOON, PLANET }

    /** 天体（星表项或运行时算出的太阳 / 月亮 / 行星） */
    data class CelestialBody(
        val name: String,
        val magnitude: Float,
        /** 赤经（小时，0..24） */
        val raHours: Float,
        /** 赤纬（度，-90..90） */
        val decDeg: Float,
        val kind: Kind = Kind.STAR,
    )

    /** 投影后的天体（屏幕像素坐标） */
    data class ProjectedStar(
        val name: String,
        val magnitude: Float,
        val kind: Kind,
        val x: Float,
        val y: Float,
        /** 圆点半径（px，已乘密度） */
        val radiusPx: Float,
        /** 基础不透明度（0..1，未叠加闪烁） */
        val alpha: Float,
    )

    // ==================== 星表（56 颗亮星） ====================

    /**
     * 精简亮星表：名称 / 星等 / 赤经（小时）/ 赤纬（度）。
     * 数据为 J2000 近似值，覆盖北半球中纬度可见的主要亮星（含北斗七星、猎户座、
     * 仙后座、南十字等），足够玩具级指向识别。
     */
    val BRIGHT_STARS: List<CelestialBody> = listOf(
        CelestialBody("天狼星", -1.46f, 6.7525f, -16.716f),
        CelestialBody("老人星", -0.74f, 6.3992f, -52.696f),
        CelestialBody("大角星", -0.05f, 14.2610f, 19.182f),
        CelestialBody("织女星", 0.03f, 18.6156f, 38.784f),
        CelestialBody("五车二", 0.08f, 5.2782f, 45.998f),
        CelestialBody("参宿七", 0.13f, 5.2423f, -8.202f),
        CelestialBody("南河三", 0.34f, 7.6550f, 5.225f),
        CelestialBody("参宿四", 0.42f, 5.9195f, 7.407f),
        CelestialBody("水委一", 0.46f, 1.6286f, -57.237f),
        CelestialBody("马腹一", 0.61f, 14.0637f, -60.373f),
        CelestialBody("牛郎星", 0.76f, 19.8464f, 8.868f),
        CelestialBody("十字架二", 0.77f, 12.4433f, -63.099f),
        CelestialBody("毕宿五", 0.85f, 4.5987f, 16.509f),
        CelestialBody("角宿一", 0.97f, 13.4199f, -11.161f),
        CelestialBody("心宿二", 1.06f, 16.4901f, -26.432f),
        CelestialBody("北河三", 1.14f, 7.7553f, 28.026f),
        CelestialBody("北落师门", 1.16f, 22.9608f, -29.622f),
        CelestialBody("天津四", 1.25f, 20.6905f, 45.280f),
        CelestialBody("十字架三", 1.25f, 12.7953f, -59.689f),
        CelestialBody("轩辕十四", 1.35f, 10.1395f, 11.967f),
        CelestialBody("北河二", 1.58f, 7.5766f, 31.888f),
        CelestialBody("弧矢七", 1.50f, 6.9771f, -28.972f),
        CelestialBody("参宿五", 1.64f, 5.4189f, 6.350f),
        CelestialBody("五车五", 1.90f, 5.9921f, 44.947f),
        CelestialBody("尾宿八", 1.62f, 17.5601f, -37.104f),
        CelestialBody("玉衡", 1.77f, 12.9005f, 55.960f),
        CelestialBody("参宿一", 1.77f, 5.6793f, -1.943f),
        CelestialBody("参宿二", 1.69f, 5.6036f, -1.202f),
        CelestialBody("天船三", 1.79f, 3.4054f, 49.861f),
        CelestialBody("天枢", 1.79f, 11.0621f, 61.751f),
        CelestialBody("摇光", 1.86f, 13.7923f, 49.313f),
        CelestialBody("北极星", 1.98f, 2.5303f, 89.264f),
        CelestialBody("白羊座α", 2.00f, 2.1196f, 23.463f),
        CelestialBody("卷舌三", 2.12f, 3.1361f, 40.956f),
        CelestialBody("五帝座一", 2.14f, 11.8177f, 14.572f),
        CelestialBody("天璇", 2.37f, 11.0307f, 56.382f),
        CelestialBody("天玑", 2.44f, 11.8972f, 53.695f),
        CelestialBody("参宿三", 2.23f, 5.5334f, -0.299f),
        CelestialBody("开阳", 2.27f, 13.3988f, 54.925f),
        CelestialBody("北冕座α", 2.23f, 15.5781f, 26.715f),
        CelestialBody("天鹅座γ", 2.23f, 20.3705f, 40.257f),
        CelestialBody("仙后座α", 2.24f, 0.6751f, 56.537f),
        CelestialBody("仙后座β", 2.28f, 0.1530f, 59.150f),
        CelestialBody("飞马座β", 2.42f, 23.0629f, 28.083f),
        CelestialBody("飞马座α", 2.48f, 23.0793f, 15.205f),
        CelestialBody("仙后座γ", 2.47f, 0.9451f, 60.717f),
        CelestialBody("武仙座β", 2.77f, 16.5037f, 21.490f),
        CelestialBody("常陈一", 2.81f, 12.9338f, 38.318f),
        CelestialBody("飞马座γ", 2.84f, 0.2206f, 15.184f),
        CelestialBody("天权", 3.31f, 12.2571f, 57.033f),
        CelestialBody("觜宿一", 3.33f, 5.5856f, 9.934f),
        CelestialBody("天琴座β", 3.52f, 18.8347f, 33.363f),
        CelestialBody("房宿三", 2.89f, 16.3531f, -25.593f),
        CelestialBody("双子座γ", 1.92f, 6.6285f, 16.399f),
        CelestialBody("金牛座β", 1.65f, 5.4382f, 28.608f),
        CelestialBody("仙女座α", 2.06f, 0.1398f, 29.091f),
    )

    // ==================== 姿态解算 ====================

    /**
     * 由加速度计（重力）与磁力计求设备姿态旋转矩阵（行主序，映射 世界 = R × 设备）。
     *
     * 算法与 `SensorManager.getRotationMatrix` 等价：
     * - East = 重力 × 磁场（水平、指向磁东）
     * - North = East × 重力（水平、指向磁北）
     * - Up = -重力
     * 行依次为 East / North / Up 在设备坐标下的分量。
     *
     * @return 3x3 旋转矩阵（长度 9）；输入无效（模长过小或重力与磁场平行）时返回 null。
     */
    fun computeRotationMatrix(gravity: FloatArray, magnetic: FloatArray): FloatArray? {
        if (gravity.size < 3 || magnetic.size < 3) return null
        val a = normalize3(gravity) ?: return null
        val e = normalize3(magnetic) ?: return null
        // East = A × E
        val east = cross3(a, e)
        val eastNorm = normalize3(east) ?: return null
        // North = East × A
        val north = cross3(eastNorm, a)
        // 行主序：East / North / Up
        return floatArrayOf(
            eastNorm[0], eastNorm[1], eastNorm[2],
            north[0], north[1], north[2],
            -a[0], -a[1], -a[2],
        )
    }

    /**
     * 按屏幕方向重映射旋转矩阵（与 `SensorManager.remapCoordinateSystem` 同语义）。
     *
     * 传感器给出的是设备物理坐标；屏幕旋转后，视觉上的「屏幕右 / 屏幕上」对应
     * 不同的物理轴。本函数把矩阵的输入坐标轴改为屏幕朝向：
     * - ROTATION_0：屏幕右 = 物理 X，屏幕上 = 物理 Y
     * - ROTATION_90：屏幕右 = 物理 Y，屏幕上 = 物理 -X
     * - ROTATION_180：屏幕右 = 物理 -X，屏幕上 = 物理 -Y
     * - ROTATION_270：屏幕右 = 物理 -Y，屏幕上 = 物理 X
     *
     * @param rotation `android.view.Surface.ROTATION_*` 的数值（0/1/2/3）
     */
    fun remapForScreenRotation(inR: FloatArray, rotation: Int): FloatArray {
        if (inR.size < 9) return inR
        return when (rotation) {
            ROTATION_90 -> remapAxes(inR, AXIS_Y, AXIS_MINUS_X)
            ROTATION_180 -> remapAxes(inR, AXIS_MINUS_X, AXIS_MINUS_Y)
            ROTATION_270 -> remapAxes(inR, AXIS_MINUS_Y, AXIS_X)
            else -> inR.copyOf()
        }
    }

    /**
     * 由方位角 / 仰角构造旋转矩阵（零 roll），用于**无传感器降级**的静态星空。
     * 视方向 = 方位角 [azimuthDeg]（北顺时针）、仰角 [altitudeDeg]。
     */
    fun rotationMatrixFromOrientation(azimuthDeg: Float, altitudeDeg: Float): FloatArray {
        val az = Math.toRadians(azimuthDeg.toDouble())
        val el = Math.toRadians(altitudeDeg.toDouble())
        val cosAz = cos(az)
        val sinAz = sin(az)
        val cosEl = cos(el)
        val sinEl = sin(el)
        // 列：屏幕右 / 屏幕上 / 屏幕外（= -视方向）
        return floatArrayOf(
            cosAz.toFloat(), (-sinEl * sinAz).toFloat(), (-cosEl * sinAz).toFloat(),
            (-sinAz).toFloat(), (-sinEl * cosAz).toFloat(), (-cosEl * cosAz).toFloat(),
            0f, cosEl.toFloat(), (-sinEl).toFloat(),
        )
    }

    /** 无传感器时的静态星空视方向：正北、仰角 45° */
    fun fallbackRotationMatrix(): FloatArray = rotationMatrixFromOrientation(
        azimuthDeg = 0f,
        altitudeDeg = 45f,
    )

    // ==================== 投影 ====================

    /**
     * 把当前视场内的天体投影到屏幕。
     *
     * @param rotationMatrix 已按屏幕方向重映射的 3x3 旋转矩阵
     * @param widthPx / heightPx 画布尺寸（px）
     * @param fovDegrees 垂直视场角（度）
     * @param epochMillis 当前时间（用于恒星时与行星 / 月亮位置）
     * @param starScale 星点半径缩放（传 `LocalDensity.current.density`）
     * @param latitudeDeg / longitudeDeg 观测点（默认北京）
     * @return 视场内天体的屏幕坐标（已按星等映射半径与透明度）
     */
    fun projectSky(
        rotationMatrix: FloatArray,
        widthPx: Float,
        heightPx: Float,
        fovDegrees: Float,
        epochMillis: Long,
        starScale: Float = 1f,
        latitudeDeg: Float = DEFAULT_LATITUDE_DEG,
        longitudeDeg: Float = DEFAULT_LONGITUDE_DEG,
    ): List<ProjectedStar> {
        if (rotationMatrix.size < 9 || widthPx <= 0f || heightPx <= 0f) return emptyList()

        val jd = julianDate(epochMillis)
        val lst = localSiderealTimeHours(jd, longitudeDeg)
        val dSchlyter = jd - 2_451_543.5

        // 相机基向量（世界系）：右 / 上 / 前（视方向）
        val right = floatArrayOf(rotationMatrix[0], rotationMatrix[3], rotationMatrix[6])
        val up = floatArrayOf(rotationMatrix[1], rotationMatrix[4], rotationMatrix[7])
        val forward = floatArrayOf(-rotationMatrix[2], -rotationMatrix[5], -rotationMatrix[8])

        val scale = (heightPx / 2.0) / tan(Math.toRadians(fovDegrees / 2.0))

        val bodies = ArrayList<CelestialBody>(BRIGHT_STARS.size + 8)
        bodies.addAll(BRIGHT_STARS)
        bodies.add(sunBody(jd))
        bodies.add(moonBody(dSchlyter))
        for (planet in PLANETS) {
            bodies.add(planetBody(planet, jd))
        }

        val out = ArrayList<ProjectedStar>(bodies.size)
        for (body in bodies) {
            val dir = equatorialToEnu(body.raHours, body.decDeg, lst, latitudeDeg)
            val x = dot3(dir, right)
            val y = dot3(dir, up)
            val z = dot3(dir, forward)
            // 视方向背面（含接近 90° 的天顶距）不画
            if (z <= 0.02) continue
            val px = (widthPx / 2.0 + (x / z) * scale).toFloat()
            val py = (heightPx / 2.0 - (y / z) * scale).toFloat()
            if (px < -STAR_MARGIN_PX || px > widthPx + STAR_MARGIN_PX) continue
            if (py < -STAR_MARGIN_PX || py > heightPx + STAR_MARGIN_PX) continue
            out.add(
                ProjectedStar(
                    name = body.name,
                    magnitude = body.magnitude,
                    kind = body.kind,
                    x = px,
                    y = py,
                    radiusPx = magnitudeToRadius(body.magnitude) * starScale,
                    alpha = magnitudeToAlpha(body.magnitude),
                )
            )
        }
        return out
    }

    /**
     * 取距离 (x, y) 最近的天体（用于点星选中）。
     * @param maxDistancePx 最大命中距离（px），超出返回 null
     */
    fun findNearest(
        stars: List<ProjectedStar>,
        x: Float,
        y: Float,
        maxDistancePx: Float,
    ): ProjectedStar? {
        var best: ProjectedStar? = null
        var bestDistSq = maxDistancePx * maxDistancePx
        for (star in stars) {
            val dx = star.x - x
            val dy = star.y - y
            val distSq = dx * dx + dy * dy
            if (distSq <= bestDistSq) {
                bestDistSq = distSq
                best = star
            }
        }
        return best
    }

    // ==================== 时间 / 坐标变换 ====================

    /** 儒略日（UT） */
    private fun julianDate(epochMillis: Long): Double =
        epochMillis / 86_400_000.0 + 2_440_587.5

    /** 本地恒星时（小时） */
    private fun localSiderealTimeHours(jd: Double, longitudeDeg: Float): Double {
        val d = jd - 2_451_545.0
        val gmstDeg = 280.46061837 + 360.98564736629 * d
        return normalizeDegrees(gmstDeg + longitudeDeg) / 15.0
    }

    /**
     * 赤经 / 赤纬 → ENU 单位向量。
     * @param lstHours 本地恒星时（小时）
     */
    private fun equatorialToEnu(
        raHours: Float,
        decDeg: Float,
        lstHours: Double,
        latitudeDeg: Float,
    ): FloatArray {
        val raDeg = raHours * 15.0
        val dec = Math.toRadians(decDeg.toDouble())
        val hourAngle = Math.toRadians(lstHours * 15.0 - raDeg)
        val lat = Math.toRadians(latitudeDeg.toDouble())
        val cosDec = cos(dec)
        val sinDec = sin(dec)
        val cosH = cos(hourAngle)
        val sinH = sin(hourAngle)
        val sinLat = sin(lat)
        val cosLat = cos(lat)
        val east = -cosDec * sinH
        val north = sinDec * cosLat - cosDec * sinLat * cosH
        val up = sinLat * sinDec + cosLat * cosDec * cosH
        return floatArrayOf(east.toFloat(), north.toFloat(), up.toFloat())
    }

    // ==================== 太阳 / 月亮 / 行星 ====================

    /** 太阳视位置（地球绕日开普勒根数的反向） */
    private fun sunBody(jd: Double): CelestialBody {
        val d = jd - 2_451_545.0
        val earth = heliocentricEcliptic(Planet.EARTH, d)
        val (ra, dec) = eclipticRectToRaDec(-earth[0], -earth[1], -earth[2])
        return CelestialBody("太阳", -26.74f, ra, dec, Kind.SUN)
    }

    /** 月亮位置（Schlyter 低精度公式，精度约 1–2 角分） */
    private fun moonBody(d: Double): CelestialBody {
        val n = Math.toRadians(125.1228 - 0.0529538083 * d)
        val inclination = Math.toRadians(5.1454)
        val w = Math.toRadians(318.0634 + 0.1643573223 * d)
        val a = 60.2666
        val e = 0.054900
        val m = Math.toRadians(normalizeDegrees(115.3654 + 13.0649929509 * d))

        var eccentric = m + e * sin(m) * (1.0 + e * cos(m))
        eccentric -= (eccentric - e * sin(eccentric) - m) / (1.0 - e * cos(eccentric))

        val x = a * (cos(eccentric) - e)
        val y = a * sqrt(1.0 - e * e) * sin(eccentric)
        val v = atan2(y, x)
        val r = sqrt(x * x + y * y)
        val u = v + w

        val cosN = cos(n)
        val sinN = sin(n)
        val cosU = cos(u)
        val sinU = sin(u)
        val cosI = cos(inclination)
        val sinI = sin(inclination)

        val xh = r * (cosN * cosU - sinN * sinU * cosI)
        val yh = r * (sinN * cosU + cosN * sinU * cosI)
        val zh = r * (sinU * sinI)

        var lon = atan2(yh, xh)
        var lat = asin(zh / r)

        // 主要摄动项（经度 12 项 / 纬度 5 项）
        val ms = Math.toRadians(normalizeDegrees(356.0470 + 0.9856002585 * d))
        val ws = Math.toRadians(282.9404 + 4.70935e-5 * d)
        val ls = ms + ws
        val lm = m + w + n
        val dElong = lm - ls
        val f = lm - n

        lon += Math.toRadians(
            -1.274 * sin(m - 2 * dElong) +
                0.658 * sin(2 * dElong) -
                0.186 * sin(ms) -
                0.059 * sin(2 * m - 2 * dElong) -
                0.057 * sin(m - 2 * dElong + ms) +
                0.053 * sin(m + 2 * dElong) +
                0.046 * sin(2 * dElong - ms) +
                0.041 * sin(m - ms) -
                0.035 * sin(dElong) -
                0.031 * sin(m + ms) -
                0.015 * sin(2 * f - 2 * dElong) +
                0.011 * sin(m - 4 * dElong)
        )
        lat += Math.toRadians(
            -0.173 * sin(f - 2 * dElong) -
                0.055 * sin(m - f - 2 * dElong) -
                0.046 * sin(m + f - 2 * dElong) +
                0.033 * sin(f + 2 * dElong) +
                0.017 * sin(2 * m + f)
        )

        val obliq = Math.toRadians(23.4393)
        val ra = atan2(sin(lon) * cos(obliq) - tan(lat) * sin(obliq), cos(lon))
        val dec = asin(
            sin(lat) * cos(obliq) + cos(lat) * sin(obliq) * sin(lon)
        )
        return CelestialBody(
            name = "月亮",
            magnitude = -12.7f,
            raHours = normalizeRaHours(Math.toDegrees(ra)),
            decDeg = Math.toDegrees(dec).toFloat(),
            kind = Kind.MOON,
        )
    }

    /** 行星位置（JPL 低精度开普勒根数，1800–2050 有效） */
    private fun planetBody(planet: Planet, jd: Double): CelestialBody {
        val d = jd - 2_451_545.0
        val p = heliocentricEcliptic(planet, d)
        val earth = heliocentricEcliptic(Planet.EARTH, d)
        val (ra, dec) = eclipticRectToRaDec(
            p[0] - earth[0],
            p[1] - earth[1],
            p[2] - earth[2],
        )
        return CelestialBody(planet.displayName, planet.magnitude, ra, dec, Kind.PLANET)
    }

    /** 日心黄道直角坐标（低精度开普勒根数） */
    private fun heliocentricEcliptic(planet: Planet, d: Double): DoubleArray {
        val t = d / 36_525.0
        val a = planet.a0 + planet.aRate * t
        val e = planet.e0 + planet.eRate * t
        val i = Math.toRadians(planet.i0 + planet.iRate * t)
        val l = planet.l0 + planet.lRate * t
        val wbar = planet.wbar0 + planet.wbarRate * t
        val omega = planet.omega0 + planet.omegaRate * t
        val w = Math.toRadians(wbar - omega)
        val m = Math.toRadians(normalizeDegrees(l - wbar))

        // 开普勒方程牛顿迭代（低偏心率，3 次足够）
        var eccentric = m + e * sin(m) * (1.0 + e * cos(m))
        repeat(3) {
            eccentric -= (eccentric - e * sin(eccentric) - m) / (1.0 - e * cos(eccentric))
        }

        val x = a * (cos(eccentric) - e)
        val y = a * sqrt(1.0 - e * e) * sin(eccentric)
        val v = atan2(y, x)
        val r = sqrt(x * x + y * y)
        val u = v + w
        val om = Math.toRadians(omega)
        val cosOm = cos(om)
        val sinOm = sin(om)
        val cosU = cos(u)
        val sinU = sin(u)
        val cosI = cos(i)
        val sinI = sin(i)
        return doubleArrayOf(
            r * (cosOm * cosU - sinOm * sinU * cosI),
            r * (sinOm * cosU + cosOm * sinU * cosI),
            r * (sinU * sinI),
        )
    }

    /** 黄道直角坐标 → 赤经（小时）/ 赤纬（度） */
    private fun eclipticRectToRaDec(x: Double, y: Double, z: Double): Pair<Float, Float> {
        val raDeg = normalizeDegrees(Math.toDegrees(atan2(y, x)))
        val decDeg = Math.toDegrees(atan2(z, sqrt(x * x + y * y)))
        return (raDeg / 15.0).toFloat() to decDeg.toFloat()
    }

    // ==================== 绘制参数映射 ====================

    /** 星等 → 圆点半径（dp 基准，调用方乘密度） */
    private fun magnitudeToRadius(magnitude: Float): Float = when {
        magnitude < -20f -> 9f   // 太阳
        magnitude < 0f -> 5.5f
        magnitude < 1f -> 4.2f
        magnitude < 2f -> 3.2f
        magnitude < 3f -> 2.5f
        magnitude < 4f -> 2.0f
        else -> 1.5f
    }

    /** 星等 → 基础不透明度 */
    private fun magnitudeToAlpha(magnitude: Float): Float = when {
        magnitude < 0f -> 1f
        magnitude < 1f -> 0.95f
        magnitude < 2f -> 0.85f
        magnitude < 3f -> 0.75f
        magnitude < 4f -> 0.65f
        else -> 0.55f
    }

    // ==================== 私有工具 ====================

    private fun normalize3(v: FloatArray): FloatArray? {
        val norm = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        if (norm < 0.1f) return null
        return floatArrayOf(v[0] / norm, v[1] / norm, v[2] / norm)
    }

    private fun cross3(a: FloatArray, b: FloatArray): FloatArray = floatArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )

    private fun dot3(a: FloatArray, b: FloatArray): Float =
        a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    /** 角度规范化到 [0, 360) */
    private fun normalizeDegrees(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0) d += 360.0
        return d
    }

    /** 赤经规范化到 [0, 24) */
    private fun normalizeRaHours(raDeg: Double): Float {
        val hours = normalizeDegrees(raDeg) / 15.0
        return hours.toFloat()
    }

    /** 轴索引（0=X, 1=Y, 2=Z） */
    private fun axisIndex(axis: Int): Int = axis and 0x3

    /** 轴符号（第 3 位为负） */
    private fun axisSign(axis: Int): Float = if (axis and 0x4 != 0) -1f else 1f

    /**
     * 把旋转矩阵的输入坐标轴改为 (newX, newY) 指定的旧轴。
     * 等价于 `outR = inR × R_remap`，其中 R_remap 的列是 newX / newY / newX×newY。
     */
    private fun remapAxes(inR: FloatArray, newX: Int, newY: Int): FloatArray {
        val xIndex = axisIndex(newX)
        val yIndex = axisIndex(newY)
        val xSign = axisSign(newX)
        val ySign = axisSign(newY)
        val xAxis = axisVector(xIndex, xSign)
        val yAxis = axisVector(yIndex, ySign)
        val zAxis = cross3(xAxis, yAxis)

        val out = FloatArray(9)
        for (row in 0 until 3) {
            val base = row * 3
            out[base + 0] = inR[base + xIndex] * xSign
            out[base + 1] = inR[base + yIndex] * ySign
            out[base + 2] = inR[base + 0] * zAxis[0] +
                inR[base + 1] * zAxis[1] +
                inR[base + 2] * zAxis[2]
        }
        return out
    }

    private fun axisVector(index: Int, sign: Float): FloatArray {
        val v = FloatArray(3)
        v[index] = sign
        return v
    }

    // ==================== 常量 ====================

    /** 投影保留的屏幕外边距（px），避免边缘星体被硬切 */
    private const val STAR_MARGIN_PX = 64f

    // 屏幕旋转常量（与 android.view.Surface.ROTATION_* 一致，避免引擎依赖 Android）
    private const val ROTATION_90 = 1
    private const val ROTATION_180 = 2
    private const val ROTATION_270 = 3

    // 坐标轴常量（与 android.hardware.SensorManager.AXIS_* 一致）
    private const val AXIS_X = 0x0
    private const val AXIS_Y = 0x1
    private const val AXIS_Z = 0x2
    private const val AXIS_MINUS_X = 0x4
    private const val AXIS_MINUS_Y = 0x5
    private const val AXIS_MINUS_Z = 0x6

    /** 五大行星（顺序即绘制顺序） */
    private val PLANETS = listOf(
        Planet.MERCURY,
        Planet.VENUS,
        Planet.MARS,
        Planet.JUPITER,
        Planet.SATURN,
    )

    /**
     * 行星低精度开普勒根数（J2000 + 每世纪变率），来源 JPL 近似行星位置表
     * （1800–2050 有效）。精度：内行星约 1–数角分，外行星约数角分–1°。
     * 字段：a(AU) / e / i(°) / L(°) / ϖ(°) / Ω(°)，Rate 为每世纪变率。
     */
    private enum class Planet(
        val displayName: String,
        val magnitude: Float,
        val a0: Double,
        val e0: Double,
        val i0: Double,
        val l0: Double,
        val wbar0: Double,
        val omega0: Double,
        val aRate: Double,
        val eRate: Double,
        val iRate: Double,
        val lRate: Double,
        val wbarRate: Double,
        val omegaRate: Double,
    ) {
        MERCURY(
            "水星", -0.4f,
            0.38709927, 0.20563593, 7.00497902, 252.25032350, 77.45779628, 48.33076593,
            0.00000037, 0.00001906, -0.00594749, 149472.67411175, 0.16047689, -0.12534081,
        ),
        VENUS(
            "金星", -4.0f,
            0.72333566, 0.00677672, 3.39467605, 181.97909950, 131.60246718, 76.67984255,
            0.00000390, -0.00004107, -0.00078890, 58517.81538729, 0.00268329, -0.27769418,
        ),
        EARTH(
            "地球", 0f,
            1.00000261, 0.01671123, -0.00001531, 100.46457166, 102.93768193, 0.0,
            0.00000562, -0.00004392, -0.01294668, 35999.37244981, 0.32327364, 0.0,
        ),
        MARS(
            "火星", 0.5f,
            1.52371034, 0.09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891,
            0.00001847, 0.00007882, -0.00813131, 19140.30268499, 0.44441088, -0.29257343,
        ),
        JUPITER(
            "木星", -2.0f,
            5.20288700, 0.04838624, 1.30439695, 34.39644051, 14.72847983, 100.47390909,
            -0.00011607, -0.00013253, -0.00183714, 3034.74612775, 0.21252668, 0.20469106,
        ),
        SATURN(
            "土星", 0.5f,
            9.53667594, 0.05386179, 2.48599187, 49.95424423, 92.59887831, 113.66242448,
            -0.00125060, -0.00050991, 0.00193609, 1222.49362201, -0.41897216, -0.28867794,
        ),
    }
}

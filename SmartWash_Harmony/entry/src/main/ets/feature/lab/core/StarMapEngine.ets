/**
 * 掌上星图引擎（纯 ArkTS，零 UI 依赖）— 对齐安卓 StarMapEngine.kt 逐函数翻译。
 *
 * 三块职责：
 * 1. **姿态解算**：[computeRotationMatrix] 由加速度计 + 磁力计求设备姿态旋转矩阵
 *    （East = 重力 × 磁场，North = East × 重力，Up = -重力，行主序拼装）；
 *    [remapForScreenRotation] 按屏幕方向重映射坐标轴（页面经 @ohos.display 取旋转角）；
 *    [rotationMatrixFromOrientation] 供无传感器降级的静态星空。
 * 2. **星表与位置**：内置 56 颗亮星（名称 / 星等 / 赤经 / 赤纬）+ 太阳 / 月亮 / 五大行星。
 *    太阳 / 行星走 JPL 低精度开普勒根数（1800–2050 有效，行星精度约 1–数角分）；
 *    月亮走 Schlyter 低精度公式（12 项经度 / 5 项纬度摄动，精度约 1–2 角分）。
 *    行星位置仅作科普示意，**不可用于真实天文观测**。
 * 3. **投影**：[projectSky] 把赤经 / 赤纬经本地恒星时转为 ENU 单位向量，再以设备姿态为
 *    视中心做切平面（gnomonic）投影，只返回视场内的天体；星等映射为圆点半径与透明度。
 *
 * 坐标约定（与安卓一致）：
 * - 设备坐标：X 屏幕右、Y 屏幕上、Z 屏幕外（指向观察者）。
 * - 世界坐标：X 东、Y 北、Z 天（ENU）。
 * - 旋转矩阵行主序，映射 `世界 = R × 设备`；视角方向 = 设备 -Z 在世界中的方向。
 *
 * MVP 近似项（与安卓一致）：
 * - 观测点固定为北京，不申请定位权限；
 * - 忽略大气折射、章动、光行差；
 * - 行星 / 月亮用低精度近似公式。
 */

/** 天体类型（用于选中态与绘制的差异化表现） */
export enum CelestialKind {
  STAR = 0,
  SUN = 1,
  MOON = 2,
  PLANET = 3,
}

/** 天体（星表项或运行时算出的太阳 / 月亮 / 行星） */
export class CelestialBody {
  readonly name: string
  readonly magnitude: number
  /** 赤经（小时，0..24） */
  readonly raHours: number
  /** 赤纬（度，-90..90） */
  readonly decDeg: number
  readonly kind: CelestialKind

  constructor(name: string, magnitude: number, raHours: number, decDeg: number,
    kind: CelestialKind = CelestialKind.STAR) {
    this.name = name
    this.magnitude = magnitude
    this.raHours = raHours
    this.decDeg = decDeg
    this.kind = kind
  }
}

/** 投影后的天体（屏幕像素坐标） */
export class ProjectedStar {
  readonly name: string
  readonly magnitude: number
  readonly kind: CelestialKind
  readonly x: number
  readonly y: number
  /** 圆点半径（vp，已含星等映射） */
  readonly radiusPx: number
  /** 基础不透明度（0..1，未叠加闪烁） */
  readonly alpha: number

  constructor(name: string, magnitude: number, kind: CelestialKind, x: number, y: number,
    radiusPx: number, alpha: number) {
    this.name = name
    this.magnitude = magnitude
    this.kind = kind
    this.x = x
    this.y = y
    this.radiusPx = radiusPx
    this.alpha = alpha
  }
}

/** 黄道直角坐标 → 赤经（小时）/ 赤纬（度）的返回结构 */
interface RaDec {
  raHours: number
  decDeg: number
}

/**
 * 行星低精度开普勒根数（J2000 + 每世纪变率），来源 JPL 近似行星位置表
 * （1800–2050 有效）。精度：内行星约 1–数角分，外行星约数角分–1°。
 * 字段：a(AU) / e / i(°) / L(°) / ϖ(°) / Ω(°)，Rate 为每世纪变率。
 * ArkTS 枚举不能带字段，安卓的 enum class 改为静态实例表。
 */
class PlanetElements {
  readonly name: string
  readonly magnitude: number
  readonly a0: number
  readonly e0: number
  readonly i0: number
  readonly l0: number
  readonly wbar0: number
  readonly omega0: number
  readonly aRate: number
  readonly eRate: number
  readonly iRate: number
  readonly lRate: number
  readonly wbarRate: number
  readonly omegaRate: number

  constructor(name: string, magnitude: number, a0: number, e0: number, i0: number, l0: number,
    wbar0: number, omega0: number, aRate: number, eRate: number, iRate: number, lRate: number,
    wbarRate: number, omegaRate: number) {
    this.name = name
    this.magnitude = magnitude
    this.a0 = a0
    this.e0 = e0
    this.i0 = i0
    this.l0 = l0
    this.wbar0 = wbar0
    this.omega0 = omega0
    this.aRate = aRate
    this.eRate = eRate
    this.iRate = iRate
    this.lRate = lRate
    this.wbarRate = wbarRate
    this.omegaRate = omegaRate
  }
}

/** 五大行星（顺序即绘制顺序）+ 地球（换算行星地心视位置用） */
const PLANET_MERCURY: PlanetElements = new PlanetElements(
  '水星', -0.4,
  0.38709927, 0.20563593, 7.00497902, 252.25032350, 77.45779628, 48.33076593,
  0.00000037, 0.00001906, -0.00594749, 149472.67411175, 0.16047689, -0.12534081,
)
const PLANET_VENUS: PlanetElements = new PlanetElements(
  '金星', -4.0,
  0.72333566, 0.00677672, 3.39467605, 181.97909950, 131.60246718, 76.67984255,
  0.00000390, -0.00004107, -0.00078890, 58517.81538729, 0.00268329, -0.27769418,
)
const PLANET_EARTH: PlanetElements = new PlanetElements(
  '地球', 0,
  1.00000261, 0.01671123, -0.00001531, 100.46457166, 102.93768193, 0.0,
  0.00000562, -0.00004392, -0.01294668, 35999.37244981, 0.32327364, 0.0,
)
const PLANET_MARS: PlanetElements = new PlanetElements(
  '火星', 0.5,
  1.52371034, 0.09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891,
  0.00001847, 0.00007882, -0.00813131, 19140.30268499, 0.44441088, -0.29257343,
)
const PLANET_JUPITER: PlanetElements = new PlanetElements(
  '木星', -2.0,
  5.20288700, 0.04838624, 1.30439695, 34.39644051, 14.72847983, 100.47390909,
  -0.00011607, -0.00013253, -0.00183714, 3034.74612775, 0.21252668, 0.20469106,
)
const PLANET_SATURN: PlanetElements = new PlanetElements(
  '土星', 0.5,
  9.53667594, 0.05386179, 2.48599187, 49.95424423, 92.59887831, 113.66242448,
  -0.00125060, -0.00050991, 0.00193609, 1222.49362201, -0.41897216, -0.28867794,
)

const PLANETS: PlanetElements[] = [
  PLANET_MERCURY, PLANET_VENUS, PLANET_MARS, PLANET_JUPITER, PLANET_SATURN,
]

export class StarMapEngine {

  // ==================== 对外常量 ====================

  /** 默认观测纬度（度，北纬为正）——MVP 固定观测点，不申请定位权限 */
  static readonly DEFAULT_LATITUDE_DEG: number = 39.9

  /** 默认观测经度（度，东经为正）——MVP 固定观测点，不申请定位权限 */
  static readonly DEFAULT_LONGITUDE_DEG: number = 116.4

  /** 屏幕旋转常量（与 @ohos.display 的 rotation 取值一致：0/1/2/3） */
  private static readonly ROTATION_90: number = 1
  private static readonly ROTATION_180: number = 2
  private static readonly ROTATION_270: number = 3

  // 坐标轴常量（与安卓 SensorManager.AXIS_* 一致）
  private static readonly AXIS_X: number = 0x0
  private static readonly AXIS_Y: number = 0x1
  private static readonly AXIS_MINUS_X: number = 0x4
  private static readonly AXIS_MINUS_Y: number = 0x5

  /** 投影保留的屏幕外边距（px），避免边缘星体被硬切 */
  private static readonly STAR_MARGIN_PX: number = 64

  // ==================== 星表（56 颗亮星，J2000 近似值，对齐安卓） ====================

  static readonly BRIGHT_STARS: CelestialBody[] = [
    new CelestialBody('天狼星', -1.46, 6.7525, -16.716),
    new CelestialBody('老人星', -0.74, 6.3992, -52.696),
    new CelestialBody('大角星', -0.05, 14.2610, 19.182),
    new CelestialBody('织女星', 0.03, 18.6156, 38.784),
    new CelestialBody('五车二', 0.08, 5.2782, 45.998),
    new CelestialBody('参宿七', 0.13, 5.2423, -8.202),
    new CelestialBody('南河三', 0.34, 7.6550, 5.225),
    new CelestialBody('参宿四', 0.42, 5.9195, 7.407),
    new CelestialBody('水委一', 0.46, 1.6286, -57.237),
    new CelestialBody('马腹一', 0.61, 14.0637, -60.373),
    new CelestialBody('牛郎星', 0.76, 19.8464, 8.868),
    new CelestialBody('十字架二', 0.77, 12.4433, -63.099),
    new CelestialBody('毕宿五', 0.85, 4.5987, 16.509),
    new CelestialBody('角宿一', 0.97, 13.4199, -11.161),
    new CelestialBody('心宿二', 1.06, 16.4901, -26.432),
    new CelestialBody('北河三', 1.14, 7.7553, 28.026),
    new CelestialBody('北落师门', 1.16, 22.9608, -29.622),
    new CelestialBody('天津四', 1.25, 20.6905, 45.280),
    new CelestialBody('十字架三', 1.25, 12.7953, -59.689),
    new CelestialBody('轩辕十四', 1.35, 10.1395, 11.967),
    new CelestialBody('北河二', 1.58, 7.5766, 31.888),
    new CelestialBody('弧矢七', 1.50, 6.9771, -28.972),
    new CelestialBody('参宿五', 1.64, 5.4189, 6.350),
    new CelestialBody('五车五', 1.90, 5.9921, 44.947),
    new CelestialBody('尾宿八', 1.62, 17.5601, -37.104),
    new CelestialBody('玉衡', 1.77, 12.9005, 55.960),
    new CelestialBody('参宿一', 1.77, 5.6793, -1.943),
    new CelestialBody('参宿二', 1.69, 5.6036, -1.202),
    new CelestialBody('天船三', 1.79, 3.4054, 49.861),
    new CelestialBody('天枢', 1.79, 11.0621, 61.751),
    new CelestialBody('摇光', 1.86, 13.7923, 49.313),
    new CelestialBody('北极星', 1.98, 2.5303, 89.264),
    new CelestialBody('白羊座α', 2.00, 2.1196, 23.463),
    new CelestialBody('卷舌三', 2.12, 3.1361, 40.956),
    new CelestialBody('五帝座一', 2.14, 11.8177, 14.572),
    new CelestialBody('天璇', 2.37, 11.0307, 56.382),
    new CelestialBody('天玑', 2.44, 11.8972, 53.695),
    new CelestialBody('参宿三', 2.23, 5.5334, -0.299),
    new CelestialBody('开阳', 2.27, 13.3988, 54.925),
    new CelestialBody('北冕座α', 2.23, 15.5781, 26.715),
    new CelestialBody('天鹅座γ', 2.23, 20.3705, 40.257),
    new CelestialBody('仙后座α', 2.24, 0.6751, 56.537),
    new CelestialBody('仙后座β', 2.28, 0.1530, 59.150),
    new CelestialBody('飞马座β', 2.42, 23.0629, 28.083),
    new CelestialBody('飞马座α', 2.48, 23.0793, 15.205),
    new CelestialBody('仙后座γ', 2.47, 0.9451, 60.717),
    new CelestialBody('武仙座β', 2.77, 16.5037, 21.490),
    new CelestialBody('常陈一', 2.81, 12.9338, 38.318),
    new CelestialBody('飞马座γ', 2.84, 0.2206, 15.184),
    new CelestialBody('天权', 3.31, 12.2571, 57.033),
    new CelestialBody('觜宿一', 3.33, 5.5856, 9.934),
    new CelestialBody('天琴座β', 3.52, 18.8347, 33.363),
    new CelestialBody('房宿三', 2.89, 16.3531, -25.593),
    new CelestialBody('双子座γ', 1.92, 6.6285, 16.399),
    new CelestialBody('金牛座β', 1.65, 5.4382, 28.608),
    new CelestialBody('仙女座α', 2.06, 0.1398, 29.091),
  ]

  // ==================== 姿态解算 ====================

  /**
   * 由加速度计（重力）与磁力计求设备姿态旋转矩阵（行主序，映射 世界 = R × 设备）。
   * East = 重力 × 磁场；North = East × 重力；Up = -重力。
   * 输入无效（模长过小或重力与磁场平行）时返回 null。
   */
  static computeRotationMatrix(gravity: number[], magnetic: number[]): number[] | null {
    if (gravity.length < 3 || magnetic.length < 3) {
      return null
    }
    const a: number[] | null = StarMapEngine.normalize3(gravity)
    if (a === null) {
      return null
    }
    const e: number[] | null = StarMapEngine.normalize3(magnetic)
    if (e === null) {
      return null
    }
    const eastRaw: number[] = StarMapEngine.cross3(a, e)
    const eastNorm: number[] | null = StarMapEngine.normalize3(eastRaw)
    if (eastNorm === null) {
      return null
    }
    const north: number[] = StarMapEngine.cross3(eastNorm, a)
    // 行主序：East / North / Up
    return [
      eastNorm[0], eastNorm[1], eastNorm[2],
      north[0], north[1], north[2],
      -a[0], -a[1], -a[2],
    ]
  }

  /**
   * 按屏幕方向重映射旋转矩阵（与安卓 SensorManager.remapCoordinateSystem 同语义）。
   * rotation 取 @ohos.display 的 rotation 数值（0/1/2/3）。
   */
  static remapForScreenRotation(inR: number[], rotation: number): number[] {
    if (inR.length < 9) {
      return inR
    }
    if (rotation === StarMapEngine.ROTATION_90) {
      return StarMapEngine.remapAxes(inR, StarMapEngine.AXIS_Y, StarMapEngine.AXIS_MINUS_X)
    }
    if (rotation === StarMapEngine.ROTATION_180) {
      return StarMapEngine.remapAxes(inR, StarMapEngine.AXIS_MINUS_X, StarMapEngine.AXIS_MINUS_Y)
    }
    if (rotation === StarMapEngine.ROTATION_270) {
      return StarMapEngine.remapAxes(inR, StarMapEngine.AXIS_MINUS_Y, StarMapEngine.AXIS_X)
    }
    return inR.slice()
  }

  /**
   * 由方位角 / 仰角构造旋转矩阵（零 roll），用于**无传感器降级**的静态星空。
   * 视方向 = 方位角 azimuthDeg（北顺时针）、仰角 altitudeDeg。
   */
  static rotationMatrixFromOrientation(azimuthDeg: number, altitudeDeg: number): number[] {
    const az: number = azimuthDeg * Math.PI / 180
    const el: number = altitudeDeg * Math.PI / 180
    const cosAz: number = Math.cos(az)
    const sinAz: number = Math.sin(az)
    const cosEl: number = Math.cos(el)
    const sinEl: number = Math.sin(el)
    // 列：屏幕右 / 屏幕上 / 屏幕外（= -视方向）
    return [
      cosAz, -sinEl * sinAz, -cosEl * sinAz,
      -sinAz, -sinEl * cosAz, -cosEl * cosAz,
      0, cosEl, -sinEl,
    ]
  }

  /** 无传感器时的静态星空视方向：正北、仰角 45° */
  static fallbackRotationMatrix(): number[] {
    return StarMapEngine.rotationMatrixFromOrientation(0, 45)
  }

  // ==================== 投影 ====================

  /**
   * 把当前视场内的天体投影到屏幕。
   *
   * @param rotationMatrix 已按屏幕方向重映射的 3x3 旋转矩阵
   * @param widthPx / heightPx 画布尺寸（px）
   * @param fovDegrees 垂直视场角（度）
   * @param epochMillis 当前时间（用于恒星时与行星 / 月亮位置）
   * @param starScale 星点半径缩放（vp 下传 1 即可）
   * @param latitudeDeg / longitudeDeg 观测点（默认北京）
   */
  static projectSky(rotationMatrix: number[], widthPx: number, heightPx: number,
    fovDegrees: number, epochMillis: number, starScale: number = 1,
    latitudeDeg: number = StarMapEngine.DEFAULT_LATITUDE_DEG,
    longitudeDeg: number = StarMapEngine.DEFAULT_LONGITUDE_DEG): ProjectedStar[] {
    if (rotationMatrix.length < 9 || widthPx <= 0 || heightPx <= 0) {
      return []
    }

    const jd: number = StarMapEngine.julianDate(epochMillis)
    const lst: number = StarMapEngine.localSiderealTimeHours(jd, longitudeDeg)
    const dSchlyter: number = jd - 2451543.5

    // 相机基向量（世界系）：右 / 上 / 前（视方向）
    const right: number[] = [rotationMatrix[0], rotationMatrix[3], rotationMatrix[6]]
    const up: number[] = [rotationMatrix[1], rotationMatrix[4], rotationMatrix[7]]
    const forward: number[] = [-rotationMatrix[2], -rotationMatrix[5], -rotationMatrix[8]]

    const scale: number = (heightPx / 2) / Math.tan(fovDegrees / 2 * Math.PI / 180)

    const bodies: CelestialBody[] = StarMapEngine.BRIGHT_STARS.slice()
    bodies.push(StarMapEngine.sunBody(jd))
    bodies.push(StarMapEngine.moonBody(dSchlyter))
    for (let i: number = 0; i < PLANETS.length; i++) {
      bodies.push(StarMapEngine.planetBody(PLANETS[i], jd))
    }

    const out: ProjectedStar[] = []
    for (let i: number = 0; i < bodies.length; i++) {
      const body: CelestialBody = bodies[i]
      const dir: number[] = StarMapEngine.equatorialToEnu(body.raHours, body.decDeg, lst,
        latitudeDeg)
      const x: number = StarMapEngine.dot3(dir, right)
      const y: number = StarMapEngine.dot3(dir, up)
      const z: number = StarMapEngine.dot3(dir, forward)
      // 视方向背面（含接近 90° 的天顶距）不画
      if (z <= 0.02) {
        continue
      }
      const px: number = widthPx / 2 + (x / z) * scale
      const py: number = heightPx / 2 - (y / z) * scale
      if (px < -StarMapEngine.STAR_MARGIN_PX || px > widthPx + StarMapEngine.STAR_MARGIN_PX) {
        continue
      }
      if (py < -StarMapEngine.STAR_MARGIN_PX || py > heightPx + StarMapEngine.STAR_MARGIN_PX) {
        continue
      }
      out.push(new ProjectedStar(
        body.name, body.magnitude, body.kind, px, py,
        StarMapEngine.magnitudeToRadius(body.magnitude) * starScale,
        StarMapEngine.magnitudeToAlpha(body.magnitude),
      ))
    }
    return out
  }

  /**
   * 取距离 (x, y) 最近的天体（用于点星选中）。
   * maxDistancePx 为最大命中距离（px），超出返回 null。
   */
  static findNearest(stars: ProjectedStar[], x: number, y: number,
    maxDistancePx: number): ProjectedStar | null {
    let best: ProjectedStar | null = null
    let bestDistSq: number = maxDistancePx * maxDistancePx
    for (let i: number = 0; i < stars.length; i++) {
      const star: ProjectedStar = stars[i]
      const dx: number = star.x - x
      const dy: number = star.y - y
      const distSq: number = dx * dx + dy * dy
      if (distSq <= bestDistSq) {
        bestDistSq = distSq
        best = star
      }
    }
    return best
  }

  // ==================== 时间 / 坐标变换 ====================

  /** 儒略日（UT） */
  private static julianDate(epochMillis: number): number {
    return epochMillis / 86400000 + 2440587.5
  }

  /** 本地恒星时（小时） */
  private static localSiderealTimeHours(jd: number, longitudeDeg: number): number {
    const d: number = jd - 2451545.0
    const gmstDeg: number = 280.46061837 + 360.98564736629 * d
    return StarMapEngine.normalizeDegrees(gmstDeg + longitudeDeg) / 15
  }

  /** 赤经 / 赤纬 → ENU 单位向量（lstHours：本地恒星时，小时） */
  private static equatorialToEnu(raHours: number, decDeg: number, lstHours: number,
    latitudeDeg: number): number[] {
    const raDeg: number = raHours * 15
    const dec: number = decDeg * Math.PI / 180
    const hourAngle: number = (lstHours * 15 - raDeg) * Math.PI / 180
    const lat: number = latitudeDeg * Math.PI / 180
    const cosDec: number = Math.cos(dec)
    const sinDec: number = Math.sin(dec)
    const cosH: number = Math.cos(hourAngle)
    const sinH: number = Math.sin(hourAngle)
    const sinLat: number = Math.sin(lat)
    const cosLat: number = Math.cos(lat)
    const east: number = -cosDec * sinH
    const north: number = sinDec * cosLat - cosDec * sinLat * cosH
    const up: number = sinLat * sinDec + cosLat * cosDec * cosH
    return [east, north, up]
  }

  // ==================== 太阳 / 月亮 / 行星 ====================

  /** 太阳视位置（地球绕日开普勒根数的反向） */
  private static sunBody(jd: number): CelestialBody {
    const d: number = jd - 2451545.0
    const earth: number[] = StarMapEngine.heliocentricEcliptic(PLANET_EARTH, d)
    const raDec: RaDec = StarMapEngine.eclipticRectToRaDec(-earth[0], -earth[1], -earth[2])
    return new CelestialBody('太阳', -26.74, raDec.raHours, raDec.decDeg, CelestialKind.SUN)
  }

  /** 月亮位置（Schlyter 低精度公式，精度约 1–2 角分） */
  private static moonBody(d: number): CelestialBody {
    const rad: number = Math.PI / 180
    const n: number = (125.1228 - 0.0529538083 * d) * rad
    const inclination: number = 5.1454 * rad
    const w: number = (318.0634 + 0.1643573223 * d) * rad
    const a: number = 60.2666
    const e: number = 0.054900
    const m: number = StarMapEngine.normalizeDegrees(115.3654 + 13.0649929509 * d) * rad

    let eccentric: number = m + e * Math.sin(m) * (1 + e * Math.cos(m))
    eccentric -= (eccentric - e * Math.sin(eccentric) - m) / (1 - e * Math.cos(eccentric))

    const x: number = a * (Math.cos(eccentric) - e)
    const y: number = a * Math.sqrt(1 - e * e) * Math.sin(eccentric)
    const v: number = Math.atan2(y, x)
    const r: number = Math.sqrt(x * x + y * y)
    const u: number = v + w

    const cosN: number = Math.cos(n)
    const sinN: number = Math.sin(n)
    const cosU: number = Math.cos(u)
    const sinU: number = Math.sin(u)
    const cosI: number = Math.cos(inclination)
    const sinI: number = Math.sin(inclination)

    const xh: number = r * (cosN * cosU - sinN * sinU * cosI)
    const yh: number = r * (sinN * cosU + cosN * sinU * cosI)
    const zh: number = r * (sinU * sinI)

    let lon: number = Math.atan2(yh, xh)
    let lat: number = Math.asin(zh / r)

    // 主要摄动项（经度 12 项 / 纬度 5 项）
    const ms: number = StarMapEngine.normalizeDegrees(356.0470 + 0.9856002585 * d) * rad
    const ws: number = (282.9404 + 4.70935e-5 * d) * rad
    const ls: number = ms + ws
    const lm: number = m + w + n
    const dElong: number = lm - ls
    const f: number = lm - n

    lon += (-1.274 * Math.sin(m - 2 * dElong) +
      0.658 * Math.sin(2 * dElong) -
      0.186 * Math.sin(ms) -
      0.059 * Math.sin(2 * m - 2 * dElong) -
      0.057 * Math.sin(m - 2 * dElong + ms) +
      0.053 * Math.sin(m + 2 * dElong) +
      0.046 * Math.sin(2 * dElong - ms) +
      0.041 * Math.sin(m - ms) -
      0.035 * Math.sin(dElong) -
      0.031 * Math.sin(m + ms) -
      0.015 * Math.sin(2 * f - 2 * dElong) +
      0.011 * Math.sin(m - 4 * dElong)) * rad
    lat += (-0.173 * Math.sin(f - 2 * dElong) -
      0.055 * Math.sin(m - f - 2 * dElong) -
      0.046 * Math.sin(m + f - 2 * dElong) +
      0.033 * Math.sin(f + 2 * dElong) +
      0.017 * Math.sin(2 * m + f)) * rad

    const obliq: number = 23.4393 * rad
    const ra: number = Math.atan2(
      Math.sin(lon) * Math.cos(obliq) - Math.tan(lat) * Math.sin(obliq), Math.cos(lon))
    const dec: number = Math.asin(
      Math.sin(lat) * Math.cos(obliq) + Math.cos(lat) * Math.sin(obliq) * Math.sin(lon))
    return new CelestialBody(
      '月亮', -12.7,
      StarMapEngine.normalizeRaHours(ra / rad),
      dec / rad,
      CelestialKind.MOON,
    )
  }

  /** 行星位置（JPL 低精度开普勒根数，1800–2050 有效） */
  private static planetBody(planet: PlanetElements, jd: number): CelestialBody {
    const d: number = jd - 2451545.0
    const p: number[] = StarMapEngine.heliocentricEcliptic(planet, d)
    const earth: number[] = StarMapEngine.heliocentricEcliptic(PLANET_EARTH, d)
    const raDec: RaDec = StarMapEngine.eclipticRectToRaDec(
      p[0] - earth[0], p[1] - earth[1], p[2] - earth[2])
    return new CelestialBody(planet.name, planet.magnitude, raDec.raHours, raDec.decDeg,
      CelestialKind.PLANET)
  }

  /** 日心黄道直角坐标（低精度开普勒根数） */
  private static heliocentricEcliptic(planet: PlanetElements, d: number): number[] {
    const rad: number = Math.PI / 180
    const t: number = d / 36525.0
    const a: number = planet.a0 + planet.aRate * t
    const e: number = planet.e0 + planet.eRate * t
    const i: number = (planet.i0 + planet.iRate * t) * rad
    const l: number = planet.l0 + planet.lRate * t
    const wbar: number = planet.wbar0 + planet.wbarRate * t
    const omega: number = planet.omega0 + planet.omegaRate * t
    const w: number = (wbar - omega) * rad
    const m: number = StarMapEngine.normalizeDegrees(l - wbar) * rad

    // 开普勒方程牛顿迭代（低偏心率，3 次足够）
    let eccentric: number = m + e * Math.sin(m) * (1 + e * Math.cos(m))
    for (let k: number = 0; k < 3; k++) {
      eccentric -= (eccentric - e * Math.sin(eccentric) - m) / (1 - e * Math.cos(eccentric))
    }

    const x: number = a * (Math.cos(eccentric) - e)
    const y: number = a * Math.sqrt(1 - e * e) * Math.sin(eccentric)
    const v: number = Math.atan2(y, x)
    const r: number = Math.sqrt(x * x + y * y)
    const u: number = v + w
    const om: number = omega * rad
    const cosOm: number = Math.cos(om)
    const sinOm: number = Math.sin(om)
    const cosU: number = Math.cos(u)
    const sinU: number = Math.sin(u)
    const cosI: number = Math.cos(i)
    const sinI: number = Math.sin(i)
    return [
      r * (cosOm * cosU - sinOm * sinU * cosI),
      r * (sinOm * cosU + cosOm * sinU * cosI),
      r * (sinU * sinI),
    ]
  }

  /** 黄道直角坐标 → 赤经（小时）/ 赤纬（度） */
  private static eclipticRectToRaDec(x: number, y: number, z: number): RaDec {
    const raDeg: number = StarMapEngine.normalizeDegrees(Math.atan2(y, x) * 180 / Math.PI)
    const decDeg: number = Math.atan2(z, Math.sqrt(x * x + y * y)) * 180 / Math.PI
    const result: RaDec = { raHours: raDeg / 15, decDeg: decDeg }
    return result
  }

  // ==================== 绘制参数映射 ====================

  /** 星等 → 圆点半径（vp 基准） */
  private static magnitudeToRadius(magnitude: number): number {
    if (magnitude < -20) {
      return 9
    }
    if (magnitude < 0) {
      return 5.5
    }
    if (magnitude < 1) {
      return 4.2
    }
    if (magnitude < 2) {
      return 3.2
    }
    if (magnitude < 3) {
      return 2.5
    }
    if (magnitude < 4) {
      return 2.0
    }
    return 1.5
  }

  /** 星等 → 基础不透明度 */
  private static magnitudeToAlpha(magnitude: number): number {
    if (magnitude < 0) {
      return 1
    }
    if (magnitude < 1) {
      return 0.95
    }
    if (magnitude < 2) {
      return 0.85
    }
    if (magnitude < 3) {
      return 0.75
    }
    if (magnitude < 4) {
      return 0.65
    }
    return 0.55
  }

  // ==================== 私有工具 ====================

  private static normalize3(v: number[]): number[] | null {
    const norm: number = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    if (norm < 0.1) {
      return null
    }
    return [v[0] / norm, v[1] / norm, v[2] / norm]
  }

  private static cross3(a: number[], b: number[]): number[] {
    return [
      a[1] * b[2] - a[2] * b[1],
      a[2] * b[0] - a[0] * b[2],
      a[0] * b[1] - a[1] * b[0],
    ]
  }

  private static dot3(a: number[], b: number[]): number {
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
  }

  /** 角度规范化到 [0, 360) */
  private static normalizeDegrees(deg: number): number {
    let d: number = deg % 360
    if (d < 0) {
      d += 360
    }
    return d
  }

  /** 赤经规范化到 [0, 24) */
  private static normalizeRaHours(raDeg: number): number {
    return StarMapEngine.normalizeDegrees(raDeg) / 15
  }

  /** 轴索引（0=X, 1=Y, 2=Z） */
  private static axisIndex(axis: number): number {
    return axis & 0x3
  }

  /** 轴符号（第 3 位为负） */
  private static axisSign(axis: number): number {
    if ((axis & 0x4) !== 0) {
      return -1
    }
    return 1
  }

  /**
   * 把旋转矩阵的输入坐标轴改为 (newX, newY) 指定的旧轴。
   * 等价于 `outR = inR × R_remap`，其中 R_remap 的列是 newX / newY / newX×newY。
   */
  private static remapAxes(inR: number[], newX: number, newY: number): number[] {
    const xIndex: number = StarMapEngine.axisIndex(newX)
    const yIndex: number = StarMapEngine.axisIndex(newY)
    const xSign: number = StarMapEngine.axisSign(newX)
    const ySign: number = StarMapEngine.axisSign(newY)
    const xAxis: number[] = StarMapEngine.axisVector(xIndex, xSign)
    const yAxis: number[] = StarMapEngine.axisVector(yIndex, ySign)
    const zAxis: number[] = StarMapEngine.cross3(xAxis, yAxis)

    const out: number[] = new Array<number>(9).fill(0)
    for (let row: number = 0; row < 3; row++) {
      const base: number = row * 3
      out[base + 0] = inR[base + xIndex] * xSign
      out[base + 1] = inR[base + yIndex] * ySign
      out[base + 2] = inR[base + 0] * zAxis[0] +
        inR[base + 1] * zAxis[1] +
        inR[base + 2] * zAxis[2]
    }
    return out
  }

  private static axisVector(index: number, sign: number): number[] {
    const v: number[] = [0, 0, 0]
    v[index] = sign
    return v
  }
}

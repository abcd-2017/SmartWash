/**
 * 今日一画 · 程序化水墨山水生成内核 — 纯逻辑（零 UI 依赖）。
 *
 * 逐函数对齐安卓 feature/lab/ui/painting/PaintingGenerator.kt（重新实现的
 * shan-shui-inf 分层山水思路）：远山（淡墨）→ 云雾（留白）→ 近山（中墨）→
 * 水面（淡墨横线）→ 近景树木 / 屋舍 / 小舟 / 飞鸟 / 红日 / 朱印。
 *
 * 确定性：所有随机数都来自 [PaintingRandom]（SplitMix64，BigInt 实现 64 位环绕），
 * 同一种子必然生成同一幅画；随机数调用顺序与安卓端逐条一致，因此同一数字种子
 * 在 Android 与鸿蒙两端生成同一幅画。
 *
 * 坐标系：归一化 [0,1] × [0,1]，y 向下；渲染时按画布宽高线性映射。
 */

/** 归一化点：x/y ∈ [0,1]，y 向下 */
export class Pt {
  readonly x: number
  readonly y: number

  constructor(x: number, y: number) {
    this.x = x
    this.y = y
  }
}

/** 墨色角色：具体色值由渲染层从设计令牌解析（保证主题成立） */
export enum InkRole {
  INK_PALE,
  INK_LIGHT,
  INK_MID,
  INK_DARK,
  CINNABAR,
  VERDIGRIS,
}

/** 一层山：山脊折线 + 山脚基准线（填充成山体）+ 墨色角色 + 透明度 */
export class MountainLayer {
  readonly ridge: Pt[]
  readonly baseY: number
  readonly role: InkRole
  readonly alpha: number

  constructor(ridge: Pt[], baseY: number, role: InkRole, alpha: number) {
    this.ridge = ridge
    this.baseY = baseY
    this.role = role
    this.alpha = alpha
  }
}

/** 云雾留白带（宣纸色半透明横带） */
export class MistBand {
  readonly x: number
  readonly y: number
  readonly width: number
  readonly height: number
  readonly alpha: number

  constructor(x: number, y: number, width: number, height: number, alpha: number) {
    this.x = x
    this.y = y
    this.width = width
    this.height = height
    this.alpha = alpha
  }
}

/** 水面淡墨横线 */
export class WaterLine {
  readonly x: number
  readonly y: number
  readonly length: number
  readonly width: number
  readonly role: InkRole
  readonly alpha: number

  constructor(x: number, y: number, length: number, width: number, role: InkRole, alpha: number) {
    this.x = x
    this.y = y
    this.length = length
    this.width = width
    this.role = role
    this.alpha = alpha
  }
}

/** 树木样式：松（枝干笔触）/ 点叶（青绿点簇） */
export enum TreeStyle {
  PINE,
  DOT,
}

/** 近景树：(x,y) 为树基，height 为归一化高度，lean 为倾斜量 */
export class Tree {
  readonly x: number
  readonly y: number
  readonly height: number
  readonly lean: number
  readonly style: TreeStyle
  readonly role: InkRole
  readonly alpha: number
  readonly foliage: Pt[]

  constructor(x: number, y: number, height: number, lean: number, style: TreeStyle,
    role: InkRole, alpha: number, foliage: Pt[]) {
    this.x = x
    this.y = y
    this.height = height
    this.lean = lean
    this.style = style
    this.role = role
    this.alpha = alpha
    this.foliage = foliage
  }
}

/** 近景屋舍：(x,y) 为屋底中心 */
export class House {
  readonly x: number
  readonly y: number
  readonly width: number
  readonly height: number
  readonly roofHeight: number
  readonly role: InkRole

  constructor(x: number, y: number, width: number, height: number, roofHeight: number,
    role: InkRole) {
    this.x = x
    this.y = y
    this.width = width
    this.height = height
    this.roofHeight = roofHeight
    this.role = role
  }
}

/** 水面小舟：(x,y) 为船身中心，sailHeight 为帆高 */
export class Boat {
  readonly x: number
  readonly y: number
  readonly width: number
  readonly sailHeight: number
  readonly role: InkRole

  constructor(x: number, y: number, width: number, sailHeight: number, role: InkRole) {
    this.x = x
    this.y = y
    this.width = width
    this.sailHeight = sailHeight
    this.role = role
  }
}

/** 飞鸟：(x,y) 为鸟身中心，size 为翼展 */
export class Bird {
  readonly x: number
  readonly y: number
  readonly size: number
  readonly role: InkRole

  constructor(x: number, y: number, size: number, role: InkRole) {
    this.x = x
    this.y = y
    this.size = size
    this.role = role
  }
}

/** 红日：(x,y) 为圆心，radius 为半径 */
export class Sun {
  readonly x: number
  readonly y: number
  readonly radius: number
  readonly role: InkRole
  readonly alpha: number

  constructor(x: number, y: number, radius: number, role: InkRole, alpha: number) {
    this.x = x
    this.y = y
    this.radius = radius
    this.role = role
    this.alpha = alpha
  }
}

/** 朱印：(x,y) 为印面中心，size 为边长 */
export class Seal {
  readonly x: number
  readonly y: number
  readonly size: number
  readonly role: InkRole

  constructor(x: number, y: number, size: number, role: InkRole) {
    this.x = x
    this.y = y
    this.size = size
    this.role = role
  }
}

/** 完整场景（元素顺序即绘制顺序，先画者被后画者遮挡） */
export class PaintingScene {
  readonly seed: number
  readonly mountainsFar: MountainLayer[]
  readonly mountainsNear: MountainLayer[]
  readonly mistBands: MistBand[]
  readonly waterLines: WaterLine[]
  readonly trees: Tree[]
  readonly houses: House[]
  readonly boats: Boat[]
  readonly birds: Bird[]
  readonly sun: Sun | null
  readonly seal: Seal

  constructor(seed: number, mountainsFar: MountainLayer[], mountainsNear: MountainLayer[],
    mistBands: MistBand[], waterLines: WaterLine[], trees: Tree[], houses: House[],
    boats: Boat[], birds: Bird[], sun: Sun | null, seal: Seal) {
    this.seed = seed
    this.mountainsFar = mountainsFar
    this.mountainsNear = mountainsNear
    this.mistBands = mistBands
    this.waterLines = waterLines
    this.trees = trees
    this.houses = houses
    this.boats = boats
    this.birds = birds
    this.sun = sun
    this.seal = seal
  }
}

/**
 * SplitMix64 伪随机数生成器（BigInt 实现，确定性，跨平台一致）。
 * 常量与位运算对齐安卓 PaintingRandom.kt：同一种子产出完全一致的序列。
 */
export class PaintingRandom {
  private static readonly MASK_64: bigint = 0xFFFFFFFFFFFFFFFFn
  private static readonly GOLDEN_GAMMA: bigint = 0x9E3779B97F4A7C15n
  private static readonly MIX1: bigint = 0xBF58476D1CE4E5B9n
  private static readonly MIX2: bigint = 0x94D049BB133111EBn
  private static readonly POW_2_53: number = 9007199254740992.0

  private state: bigint

  constructor(seed: number) {
    this.state = BigInt(seed) & PaintingRandom.MASK_64
  }

  nextLong(): bigint {
    this.state = (this.state + PaintingRandom.GOLDEN_GAMMA) & PaintingRandom.MASK_64
    let z: bigint = this.state
    z = ((z ^ (z >> 30n)) * PaintingRandom.MIX1) & PaintingRandom.MASK_64
    z = ((z ^ (z >> 27n)) * PaintingRandom.MIX2) & PaintingRandom.MASK_64
    return z ^ (z >> 31n)
  }

  /** [0,1) 双精度小数 */
  nextDouble(): number {
    return Number(this.nextLong() >> 11n) / PaintingRandom.POW_2_53
  }

  nextFloat(): number {
    return this.nextDouble()
  }

  /** [0, until) 整数 */
  nextInt(until: number): number {
    if (until <= 0) {
      return 0
    }
    const value: number = Math.floor(this.nextDouble() * until)
    return Math.min(until - 1, Math.max(0, value))
  }

  nextBoolean(): boolean {
    return (this.nextLong() & 1n) !== 0n
  }

  /** [min, max] 闭区间浮点 */
  nextRange(min: number, max: number): number {
    return min + this.nextFloat() * (max - min)
  }
}

/** 日期种子：year*10000 + month*100 + day（2026-11-03 → 20261103） */
export function paintingSeedFromDate(year: number, month: number, day: number): number {
  return year * 10000 + month * 100 + day
}

/** 随机六位数字种子（100000..999999） */
export function paintingSeedRandom(): number {
  return 100000 + Math.floor(Math.random() * 900000)
}

/**
 * 今日一画生成器：构图参数与随机数调用顺序逐条对齐安卓 PaintingGenerator.kt，
 * 保证同种子跨端同画。
 */
export class PaintingGenerator {
  private readonly seed: number
  private random: PaintingRandom

  constructor(seed: number) {
    this.seed = seed
    this.random = new PaintingRandom(seed)
  }

  /** 生成一幅完整场景（构建顺序固定，保证确定性） */
  generate(): PaintingScene {
    const far: MountainLayer = this.buildRidge(0.20, 0.40, 0.58, 0.10, 0.16, InkRole.INK_PALE)
    const mist: MistBand[] = this.buildMist()
    const near: MountainLayer = this.buildRidge(0.40, 0.58, 0.72, 0.20, 0.30, InkRole.INK_LIGHT)
    return new PaintingScene(
      this.seed,
      [far],
      [near],
      mist,
      this.buildWaterLines(),
      this.buildTrees(),
      this.buildHouses(),
      this.buildBoats(),
      this.buildBirds(),
      this.buildSun(),
      this.buildSeal(),
    )
  }

  /**
   * 一条山脊：x 从 -0.05 走到 1.05，y 在 [yMin,yMax] 内做随机往返
   * （方向按概率翻转，形成山峰与山谷），底部 baseY 封闭成可填充的山体。
   */
  private buildRidge(yMin: number, yMax: number, baseY: number,
    alphaMin: number, alphaMax: number, role: InkRole): MountainLayer {
    const points: Pt[] = []
    let x: number = -0.05
    let y: number = this.random.nextRange(yMin, yMax)
    points.push(new Pt(x, y))
    let direction: number = this.random.nextBoolean() ? -1 : 1
    while (x < 1.05) {
      if (this.random.nextFloat() < 0.28) {
        direction = -direction
      }
      y = Math.min(yMax, Math.max(yMin, y + direction * this.random.nextRange(0.02, 0.08)))
      x += this.random.nextRange(0.05, 0.12)
      points.push(new Pt(x, y))
    }
    return new MountainLayer(points, baseY, role, this.random.nextRange(alphaMin, alphaMax))
  }

  /** 云雾留白带：远山与近山之间的半透明宣纸带，拉开空间层次 */
  private buildMist(): MistBand[] {
    const count: number = this.random.nextInt(2) + 1
    const bands: MistBand[] = []
    for (let i = 0; i < count; i++) {
      bands.push(new MistBand(
        this.random.nextRange(-0.15, 0.55),
        this.random.nextRange(0.44, 0.56),
        this.random.nextRange(0.45, 0.95),
        this.random.nextRange(0.015, 0.035),
        this.random.nextRange(0.35, 0.60),
      ))
    }
    return bands
  }

  /** 水面淡墨横线：集中在画面下三分之一，长短不一 */
  private buildWaterLines(): WaterLine[] {
    const count: number = this.random.nextInt(8) + 8
    const lines: WaterLine[] = []
    for (let i = 0; i < count; i++) {
      lines.push(new WaterLine(
        this.random.nextRange(-0.05, 1.0),
        this.random.nextRange(0.68, 0.98),
        this.random.nextRange(0.05, 0.26),
        this.random.nextRange(0.0015, 0.0035),
        InkRole.INK_PALE,
        this.random.nextRange(0.25, 0.50),
      ))
    }
    return lines
  }

  /** 近景树木：松（枝干笔触）与点叶树（青绿点簇）两种，按 y 排序保证近者压远者 */
  private buildTrees(): Tree[] {
    const count: number = this.random.nextInt(4) + 3
    const trees: Tree[] = []
    for (let i = 0; i < count; i++) {
      const style: TreeStyle = this.random.nextBoolean() ? TreeStyle.PINE : TreeStyle.DOT
      const x: number = this.random.nextRange(0.06, 0.94)
      const y: number = this.random.nextRange(0.64, 0.90)
      const height: number = this.random.nextRange(0.10, 0.20)
      const role: InkRole = this.random.nextFloat() < 0.5 ? InkRole.INK_MID : InkRole.INK_DARK
      const alpha: number = this.random.nextRange(0.55, 0.85)
      const foliage: Pt[] = style === TreeStyle.DOT ? this.buildFoliage(x, y, height) : []
      trees.push(new Tree(x, y, height, this.random.nextRange(-0.10, 0.10), style, role, alpha, foliage))
    }
    trees.sort((a: Tree, b: Tree) => a.y - b.y)
    return trees
  }

  /** 点叶树的叶簇：相对树基的归一化偏移，渲染时取青绿 */
  private buildFoliage(x: number, y: number, height: number): Pt[] {
    const count: number = this.random.nextInt(4) + 3
    const dots: Pt[] = []
    for (let i = 0; i < count; i++) {
      dots.push(new Pt(
        x + this.random.nextRange(-0.035, 0.035),
        y - height * this.random.nextRange(0.45, 1.0),
      ))
    }
    return dots
  }

  /** 近景屋舍：1..2 座，按 y 排序 */
  private buildHouses(): House[] {
    const count: number = this.random.nextInt(2) + 1
    const houses: House[] = []
    for (let i = 0; i < count; i++) {
      houses.push(new House(
        this.random.nextRange(0.12, 0.88),
        this.random.nextRange(0.72, 0.90),
        this.random.nextRange(0.08, 0.13),
        this.random.nextRange(0.045, 0.075),
        this.random.nextRange(0.030, 0.050),
        InkRole.INK_DARK,
      ))
    }
    houses.sort((a: House, b: House) => a.y - b.y)
    return houses
  }

  /** 水面小舟：0..2 条，带朱砂帆 */
  private buildBoats(): Boat[] {
    const count: number = this.random.nextInt(3)
    const boats: Boat[] = []
    for (let i = 0; i < count; i++) {
      boats.push(new Boat(
        this.random.nextRange(0.15, 0.85),
        this.random.nextRange(0.72, 0.93),
        this.random.nextRange(0.09, 0.16),
        this.random.nextRange(0.05, 0.09),
        InkRole.INK_MID,
      ))
    }
    return boats
  }

  /** 天空飞鸟：0..4 只，极简「人」形两笔 */
  private buildBirds(): Bird[] {
    const count: number = this.random.nextInt(5)
    const birds: Bird[] = []
    for (let i = 0; i < count; i++) {
      birds.push(new Bird(
        this.random.nextRange(0.15, 0.90),
        this.random.nextRange(0.12, 0.30),
        this.random.nextRange(0.010, 0.020),
        InkRole.INK_MID,
      ))
    }
    return birds
  }

  /** 红日：70% 概率出现（朱砂圆，画面唯一暖色锚点） */
  private buildSun(): Sun | null {
    if (this.random.nextFloat() >= 0.70) {
      return null
    }
    return new Sun(
      this.random.nextRange(0.15, 0.85),
      this.random.nextRange(0.08, 0.22),
      this.random.nextRange(0.030, 0.055),
      InkRole.CINNABAR,
      this.random.nextRange(0.55, 0.80),
    )
  }

  /** 朱印：固定右下角，印文为朱文「山」字（渲染层用宣纸色三竖一横表达） */
  private buildSeal(): Seal {
    return new Seal(0.90, 0.94, 0.045, InkRole.CINNABAR)
  }
}

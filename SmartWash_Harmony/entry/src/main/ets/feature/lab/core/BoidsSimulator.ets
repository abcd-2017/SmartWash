/**
 * 指尖鱼群 boids 模拟内核 — 纯逻辑（零 UI 依赖），对齐安卓 BoidsSimulator.kt。
 *
 * 三规则（Reynolds boids）：分离 / 对齐 / 聚集；邻居查询经均匀网格空间细分
 * （cell 尺寸 = 感知半径，仅查 3x3 邻域，整体 O(n)）。边界取柔和转向而非环绕。
 * 坐标单位 = vp（页面注入画布尺寸）；dt 单位 = 秒（tick 入参 ms）。
 * 指针交互：按下吸引 / 长按驱散 / 双击径向冲击波（一次性速度冲量）。
 */

/** 模拟参数（坐标 vp、速度 vp/s、力 vp/s²；默认值面向常见手机池） */
export class BoidsParams {
  fishCount: number = 180
  perceptionRadius: number = 56
  separationRadius: number = 22
  maxSpeed: number = 96
  minSpeed: number = 26
  maxForce: number = 300
  edgeMargin: number = 48
  edgeTurn: number = 260
  separationWeight: number = 1.7
  alignmentWeight: number = 1.0
  cohesionWeight: number = 0.85
  attractStrength: number = 320
  repelStrength: number = 420
  pulseStrength: number = 320
}

/** 单条鱼：位置 / 速度 + 绘制侧数据 */
export class Boid {
  sizeFactor: number
  colorIndex: number
  x: number
  y: number
  vx: number
  vy: number

  constructor(sizeFactor: number, colorIndex: number, x: number, y: number, vx: number, vy: number) {
    this.sizeFactor = sizeFactor
    this.colorIndex = colorIndex
    this.x = x
    this.y = y
    this.vx = vx
    this.vy = vy
  }
}

/** 冲击波作用半径（vp） */
const PULSE_RADIUS: number = 180

/** 指针力作用半径（vp） */
const POINTER_RADIUS: number = 220

/** 鱼色档位数（页面令牌色组 primary/water 系） */
const COLOR_CHOICES: number = 3

export class BoidsSimulator {
  private params: BoidsParams
  private boids: Boid[] = []
  private widthValue: number = 0
  private heightValue: number = 0

  // 指针状态（attract / repel 二态）
  private pointerX: number = 0
  private pointerY: number = 0
  private repelling: boolean = false
  private pointerActive: boolean = false

  // 均匀网格（cell = perceptionRadius，桶惰性建列表复用）
  private cols: number = 0
  private rows: number = 0
  private grid: Array<Array<Boid> | null> = []

  constructor(params: BoidsParams | null = null) {
    this.params = params !== null ? params : new BoidsParams()
  }

  get boidsList(): Boid[] {
    return this.boids
  }

  get width(): number {
    return this.widthValue
  }

  get height(): number {
    return this.heightValue
  }

  /** 注入画布尺寸（首次或尺寸变化时调用；重铺鱼群分布） */
  resize(width: number, height: number): void {
    this.widthValue = width
    this.heightValue = height
    this.cols = Math.max(1, Math.floor(width / this.params.perceptionRadius) + 1)
    this.rows = Math.max(1, Math.floor(height / this.params.perceptionRadius) + 1)
    this.grid = new Array<Array<Boid> | null>(this.cols * this.rows).fill(null)
    this.reset()
  }

  /** 均匀随机重铺鱼群（随机朝向、三色混排、尺寸随机档） */
  reset(): void {
    if (this.widthValue <= 0 || this.heightValue <= 0) {
      return
    }
    this.boids = []
    for (let i = 0; i < this.params.fishCount; i++) {
      const angle: number = Math.random() * 2 * Math.PI
      this.boids.push(new Boid(
        Math.random(),
        Math.floor(Math.random() * COLOR_CHOICES),
        Math.random() * this.widthValue,
        Math.random() * this.heightValue,
        Math.cos(angle) * this.params.maxSpeed * 0.6,
        Math.sin(angle) * this.params.maxSpeed * 0.6,
      ))
    }
  }

  /** 按下：吸引模式（指针位置固定为按下点） */
  pointerDown(x: number, y: number): void {
    this.pointerX = x
    this.pointerY = y
    this.pointerActive = true
    this.repelling = false
  }

  /** 长按超阈值（页面判定后调用）：切驱散 */
  engageRepel(x: number, y: number): void {
    this.pointerX = x
    this.pointerY = y
    this.pointerActive = true
    this.repelling = true
  }

  /** 抬起：清除指针力 */
  pointerUp(): void {
    this.pointerActive = false
    this.repelling = false
  }

  /** 双击：径向冲击波（一次性速度冲量，随距离线性衰减） */
  pulse(px: number, py: number): void {
    if (this.widthValue <= 0) {
      return
    }
    for (const boid of this.boids) {
      const dx: number = boid.x - px
      const dy: number = boid.y - py
      const d: number = Math.sqrt(dx * dx + dy * dy)
      if (d < 1e-3) {
        continue
      }
      const falloff: number = 1 - Math.min(1, Math.max(0, d / PULSE_RADIUS))
      if (falloff <= 0) {
        continue
      }
      const impulse: number = this.params.pulseStrength * falloff
      boid.vx += dx / d * impulse
      boid.vy += dy / d * impulse
    }
  }

  /** 推进一帧。[dtMs] 为实际帧间隔（页面 clamp 1..50ms） */
  tick(dtMs: number): void {
    if (this.widthValue <= 0 || this.heightValue <= 0 || this.boids.length === 0) {
      return
    }
    const dt: number = Math.min(0.05, Math.max(0.001, dtMs / 1000))
    this.rebuildGrid()
    for (const boid of this.boids) {
      const neighbors: Boid[] = this.neighborsOf(boid)
      const separation: number[] = this.steerSeparation(boid, neighbors)
      const alignment: number[] = this.steerAlignment(boid, neighbors)
      const cohesion: number[] = this.steerCohesion(boid, neighbors)
      let fx: number = separation[0] * this.params.separationWeight +
        alignment[0] * this.params.alignmentWeight + cohesion[0] * this.params.cohesionWeight
      let fy: number = separation[1] * this.params.separationWeight +
        alignment[1] * this.params.alignmentWeight + cohesion[1] * this.params.cohesionWeight
      // 指针力（吸引朝指针 / 驱散背离指针，随距离线性衰减）
      if (this.pointerActive) {
        const dx: number = this.pointerX - boid.x
        const dy: number = this.pointerY - boid.y
        const d: number = Math.max(1, Math.sqrt(dx * dx + dy * dy))
        const falloff: number = 1 - Math.min(1, Math.max(0, d / POINTER_RADIUS))
        if (falloff > 0) {
          const strength: number =
            (this.repelling ? -this.params.repelStrength : this.params.attractStrength) * falloff
          fx += dx / d * strength
          fy += dy / d * strength
        }
      }
      // 边界柔和转向（margin 内朝内，越贴边转向越强）
      if (boid.x < this.params.edgeMargin) {
        fx += this.params.edgeTurn * (1 - boid.x / this.params.edgeMargin)
      }
      if (boid.x > this.widthValue - this.params.edgeMargin) {
        fx -= this.params.edgeTurn * (1 - (this.widthValue - boid.x) / this.params.edgeMargin)
      }
      if (boid.y < this.params.edgeMargin) {
        fy += this.params.edgeTurn * (1 - boid.y / this.params.edgeMargin)
      }
      if (boid.y > this.heightValue - this.params.edgeMargin) {
        fy -= this.params.edgeTurn * (1 - (this.heightValue - boid.y) / this.params.edgeMargin)
      }
      // 力限幅 → 速度积分 → 速度限幅（minSpeed 保证鱼群永不停摆）
      const fMag: number = Math.sqrt(fx * fx + fy * fy)
      if (fMag > this.params.maxForce) {
        fx = fx / fMag * this.params.maxForce
        fy = fy / fMag * this.params.maxForce
      }
      boid.vx += fx * dt
      boid.vy += fy * dt
      const speed: number = Math.max(1e-4, Math.sqrt(boid.vx * boid.vx + boid.vy * boid.vy))
      const clamped: number = Math.min(this.params.maxSpeed, Math.max(this.params.minSpeed, speed))
      boid.vx = boid.vx / speed * clamped
      boid.vy = boid.vy / speed * clamped
      // 位置积分 + 夹界兜底
      boid.x = Math.min(this.widthValue, Math.max(0, boid.x + boid.vx * dt))
      boid.y = Math.min(this.heightValue, Math.max(0, boid.y + boid.vy * dt))
    }
  }

  /** 坐标 → 网格桶下标（夹界防越格） */
  private cellIndexOf(x: number, y: number): number {
    const cx: number = Math.min(this.cols - 1, Math.max(0, Math.floor(x / this.params.perceptionRadius)))
    const cy: number = Math.min(this.rows - 1, Math.max(0, Math.floor(y / this.params.perceptionRadius)))
    return cy * this.cols + cx
  }

  /** 重建网格桶（每 tick 一次；桶列表惰性建、跨帧复用减少分配） */
  private rebuildGrid(): void {
    if (this.grid.length !== this.cols * this.rows) {
      this.grid = new Array<Array<Boid> | null>(this.cols * this.rows).fill(null)
    }
    for (let i = 0; i < this.grid.length; i++) {
      const bucket: Array<Boid> | null = this.grid[i]
      if (bucket !== null) {
        bucket.length = 0
      }
    }
    for (const boid of this.boids) {
      const idx: number = this.cellIndexOf(boid.x, boid.y)
      let bucket: Array<Boid> | null = this.grid[idx]
      if (bucket === null) {
        bucket = []
        this.grid[idx] = bucket
      }
      bucket.push(boid)
    }
  }

  /** 3x3 邻域内感知半径筛选（不含自身） */
  private neighborsOf(target: Boid): Boid[] {
    if (this.cols <= 0 || this.rows <= 0) {
      return []
    }
    const cx: number = Math.min(this.cols - 1, Math.max(0, Math.floor(target.x / this.params.perceptionRadius)))
    const cy: number = Math.min(this.rows - 1, Math.max(0, Math.floor(target.y / this.params.perceptionRadius)))
    const result: Boid[] = []
    const r2: number = this.params.perceptionRadius * this.params.perceptionRadius
    const minGy: number = Math.max(0, cy - 1)
    const maxGy: number = Math.min(this.rows - 1, cy + 1)
    const minGx: number = Math.max(0, cx - 1)
    const maxGx: number = Math.min(this.cols - 1, cx + 1)
    for (let gy = minGy; gy <= maxGy; gy++) {
      for (let gx = minGx; gx <= maxGx; gx++) {
        const bucket: Array<Boid> | null = this.grid[gy * this.cols + gx]
        if (bucket === null) {
          continue
        }
        for (const other of bucket) {
          if (other === target) {
            continue
          }
          const dx: number = other.x - target.x
          const dy: number = other.y - target.y
          if (dx * dx + dy * dy <= r2) {
            result.push(other)
          }
        }
      }
    }
    return result
  }

  /** 分离：近邻（< separationRadius）按 1/d² 反比推开 */
  private steerSeparation(boid: Boid, neighbors: Boid[]): number[] {
    let fx: number = 0
    let fy: number = 0
    const r2: number = this.params.separationRadius * this.params.separationRadius
    for (const other of neighbors) {
      const dx: number = boid.x - other.x
      const dy: number = boid.y - other.y
      const d2: number = dx * dx + dy * dy
      if (d2 > 1e-6 && d2 < r2) {
        const inv: number = 1 / d2
        fx += dx * inv
        fy += dy * inv
      }
    }
    return this.steerTo(boid, fx, fy)
  }

  /** 对齐：期望速度方向 = 邻均速方向 */
  private steerAlignment(boid: Boid, neighbors: Boid[]): number[] {
    if (neighbors.length === 0) {
      return [0, 0]
    }
    let vx: number = 0
    let vy: number = 0
    for (const other of neighbors) {
      vx += other.vx
      vy += other.vy
    }
    return this.steerTo(boid, vx / neighbors.length, vy / neighbors.length)
  }

  /** 聚集：期望方向 = 指向邻质心 */
  private steerCohesion(boid: Boid, neighbors: Boid[]): number[] {
    if (neighbors.length === 0) {
      return [0, 0]
    }
    let px: number = 0
    let py: number = 0
    for (const other of neighbors) {
      px += other.x
      py += other.y
    }
    return this.steerTo(boid, px / neighbors.length - boid.x, py / neighbors.length - boid.y)
  }

  /** 期望方向 → 转向力：want = 方向归一 * maxSpeed；steer = want - 当前速，限幅 maxForce */
  private steerTo(boid: Boid, dirX: number, dirY: number): number[] {
    const mag: number = Math.sqrt(dirX * dirX + dirY * dirY)
    if (mag < 1e-6) {
      return [0, 0]
    }
    let sx: number = dirX / mag * this.params.maxSpeed - boid.vx
    let sy: number = dirY / mag * this.params.maxSpeed - boid.vy
    const sMag: number = Math.sqrt(sx * sx + sy * sy)
    if (sMag > this.params.maxForce) {
      sx = sx / sMag * this.params.maxForce
      sy = sy / sMag * this.params.maxForce
    }
    return [sx, sy]
  }

  /** 朝向角（度）：页面绘制鱼形三角用（atan2 → 角度） */
  static headingDegrees(boid: Boid): number {
    return Math.atan2(boid.vy, boid.vx) * 180 / Math.PI
  }
}

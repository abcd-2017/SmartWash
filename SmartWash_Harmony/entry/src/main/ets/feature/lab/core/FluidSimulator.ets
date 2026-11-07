/**
 * 指尖流体 stable fluids 求解器内核 — 纯逻辑（零 UI 依赖）。
 *
 * 对齐安卓 feature/lab/ui/fluid/FluidRenderer.kt 的八步管线，按本批次的 CPU 数值解
 * 方案移植（Android 为 GLSL 着色器，鸿蒙为 Float32Array CPU 解）：
 * 1. 涡量（curl）→ 2. 涡量约束（vorticity confinement，手感灵魂）→ 3. 散度
 * → 4. 压力衰减 → 5. Jacobi 压力迭代 → 6. 梯度减法（投影）→ 7. 半拉格朗日平流速度
 * → 8. 半拉格朗日平流染料（乘衰减系数自然消散）。
 *
 * 双分辨率结构：速度/压力场低分辨率（短边 [SIM_RESOLUTION]，保帧率），
 * 染料场高分辨率（短边 [DYE_RESOLUTION]，保锐利）；网格按画布宽高比取长边缩放，
 * 边界采用 clamp-to-edge 采样（与 GL CLAMP_TO_EDGE 同义），散度边界按速度取反（自由滑移）。
 *
 * 坐标约定：uv ∈ [0,1]，网格行 0 为画布顶部（y 向下），与 Canvas 2D 触摸坐标同向，
 * 触摸位移不做 Android 端的 y 翻转（GL uv y-up 所需，此处无需）。
 * 渲染：染料场加法叠在底色上，由页面经 PixelMap（RGBA_8888）drawImage 放大上屏。
 */

/** 单个触摸注入点（uv 坐标 + 速度单位位移） */
class FluidSplatPoint {
  readonly x: number
  readonly y: number
  readonly dx: number
  readonly dy: number

  constructor(x: number, y: number, dx: number, dy: number) {
    this.x = x
    this.y = y
    this.dx = dx
    this.dy = dy
  }
}

export class FluidSimulator {
  // ---------- 网格规格 ----------
  private simCols: number = 0
  private simRows: number = 0
  private dyeCols: number = 0
  private dyeRows: number = 0
  private aspectRatio: number = 1

  // ---------- 场数据（速度双缓冲 + 压力双缓冲 + 染料双缓冲） ----------
  private vx: Float32Array = new Float32Array(0)
  private vy: Float32Array = new Float32Array(0)
  private vx0: Float32Array = new Float32Array(0)
  private vy0: Float32Array = new Float32Array(0)
  private pressure: Float32Array = new Float32Array(0)
  private pressure0: Float32Array = new Float32Array(0)
  private divergence: Float32Array = new Float32Array(0)
  private curlField: Float32Array = new Float32Array(0)
  private dyeR: Float32Array = new Float32Array(0)
  private dyeG: Float32Array = new Float32Array(0)
  private dyeB: Float32Array = new Float32Array(0)
  private dyeR0: Float32Array = new Float32Array(0)
  private dyeG0: Float32Array = new Float32Array(0)
  private dyeB0: Float32Array = new Float32Array(0)

  // ---------- 画布与注入 ----------
  private canvasWidth: number = 0
  private canvasHeight: number = 0
  private seeded: boolean = false
  private splatQueue: FluidSplatPoint[] = []
  private palette: number[][] = [[1, 1, 1]]
  private baseColor: number[] = [0, 0, 0]

  // ---------- 规格 ----------

  get dyeColumns(): number {
    return this.dyeCols
  }

  get dyeRowCount(): number {
    return this.dyeRows
  }

  /** 注入画布尺寸（onAreaChange 首次/变化时调用；网格规格变化时重建场并重铺种子） */
  resize(canvasWidth: number, canvasHeight: number): void {
    if (canvasWidth <= 0 || canvasHeight <= 0) {
      return
    }
    if (this.canvasWidth === canvasWidth && this.canvasHeight === canvasHeight) {
      return
    }
    this.canvasWidth = canvasWidth
    this.canvasHeight = canvasHeight
    this.aspectRatio = canvasWidth / canvasHeight
    const sim: number[] = this.gridOf(SIM_RESOLUTION)
    const dye: number[] = this.gridOf(DYE_RESOLUTION)
    if (sim[0] === this.simCols && sim[1] === this.simRows &&
      dye[0] === this.dyeCols && dye[1] === this.dyeRows) {
      return
    }
    this.allocFields(sim[0], sim[1], dye[0], dye[1])
    this.seeded = false
  }

  /** 按画布宽高比把长边对齐到目标分辨率（与安卓 getResolution 同构） */
  private gridOf(resolution: number): number[] {
    let aspect: number = this.canvasHeight > 0 ? this.canvasWidth / this.canvasHeight : 1
    if (aspect < 1) {
      aspect = 1 / aspect
    }
    const min: number = resolution
    const max: number = Math.max(1, Math.round(resolution * aspect))
    return this.canvasWidth >= this.canvasHeight ? [max, min] : [min, max]
  }

  private allocFields(simCols: number, simRows: number, dyeCols: number, dyeRows: number): void {
    this.simCols = simCols
    this.simRows = simRows
    this.dyeCols = dyeCols
    this.dyeRows = dyeRows
    const simCount: number = simCols * simRows
    const dyeCount: number = dyeCols * dyeRows
    this.vx = new Float32Array(simCount)
    this.vy = new Float32Array(simCount)
    this.vx0 = new Float32Array(simCount)
    this.vy0 = new Float32Array(simCount)
    this.pressure = new Float32Array(simCount)
    this.pressure0 = new Float32Array(simCount)
    this.divergence = new Float32Array(simCount)
    this.curlField = new Float32Array(simCount)
    this.dyeR = new Float32Array(dyeCount)
    this.dyeG = new Float32Array(dyeCount)
    this.dyeB = new Float32Array(dyeCount)
    this.dyeR0 = new Float32Array(dyeCount)
    this.dyeG0 = new Float32Array(dyeCount)
    this.dyeB0 = new Float32Array(dyeCount)
  }

  // ---------- 配色注入 ----------

  /** 注入染料调色板（页面从设计令牌映射为 [r,g,b] 0..1；渲染层不写死色值） */
  setPalette(palette: number[][]): void {
    this.palette = palette
  }

  /** 注入画布底色（染料以加法叠在底色上） */
  setBaseColor(color: number[]): void {
    this.baseColor = color
  }

  // ---------- 注入 / 重置 ----------

  /**
   * 入队一次手触注入（像素坐标 + 像素位移；内部归一化为 uv 与速度单位）。
   * 做宽高比校正，力与安卓参考实现同量级（划得越快涡旋越猛）。
   */
  enqueueSplat(xPixels: number, yPixels: number, dxPixels: number, dyPixels: number): void {
    const width: number = this.canvasWidth
    const height: number = this.canvasHeight
    if (width <= 0 || height <= 0) {
      return
    }
    let dx: number = dxPixels / width
    let dy: number = dyPixels / height
    const aspect: number = this.aspectRatio
    if (aspect < 1) {
      dx *= aspect
    }
    if (aspect > 1) {
      dy /= aspect
    }
    const u: number = Math.min(1, Math.max(0, xPixels / width))
    const v: number = Math.min(1, Math.max(0, yPixels / height))
    if (this.splatQueue.length < MAX_SPLAT_QUEUE) {
      this.splatQueue.push(new FluidSplatPoint(u, v, dx * SPLAT_FORCE, dy * SPLAT_FORCE))
    }
  }

  /** 重置模拟：清空队列与场，下一帧重新布静态种子 */
  reset(): void {
    this.splatQueue.length = 0
    this.seeded = false
    this.clearFields()
  }

  // ---------- 帧推进 ----------

  /** 单帧：静态种子（首帧）→ 出队注入 → 模拟步进（playing=false 时仅响应注入与种子） */
  frame(dt: number, playing: boolean): void {
    if (this.simCols <= 0 || this.dyeCols <= 0) {
      return
    }
    if (!this.seeded) {
      this.seedStaticScene()
    }
    this.drainSplats()
    if (playing) {
      this.stepSimulation(dt)
    }
  }

  private stepSimulation(dt: number): void {
    this.computeCurl()
    this.applyVorticity(dt)
    this.computeDivergence()
    this.decayPressure()
    for (let i = 0; i < PRESSURE_ITERATIONS; i++) {
      this.jacobiPressure()
    }
    this.subtractGradient()
    this.advectVelocity(dt)
    this.advectDye(dt)
  }

  // ---------- 1/2. 涡量与涡量约束 ----------

  private computeCurl(): void {
    const cols: number = this.simCols
    const rows: number = this.simRows
    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) {
        const i: number = y * cols + x
        const l: number = this.vy[y * cols + Math.max(x - 1, 0)]
        const r: number = this.vy[y * cols + Math.min(x + 1, cols - 1)]
        const t: number = this.vx[Math.min(y + 1, rows - 1) * cols + x]
        const b: number = this.vx[Math.max(y - 1, 0) * cols + x]
        this.curlField[i] = 0.5 * (r - l - t + b)
      }
    }
  }

  /** 涡量约束：把数值耗散掉的旋涡重新推回去（手感灵魂） */
  private applyVorticity(dt: number): void {
    const cols: number = this.simCols
    const rows: number = this.simRows
    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) {
        const i: number = y * cols + x
        const l: number = this.curlField[y * cols + Math.max(x - 1, 0)]
        const r: number = this.curlField[y * cols + Math.min(x + 1, cols - 1)]
        const t: number = this.curlField[Math.min(y + 1, rows - 1) * cols + x]
        const b: number = this.curlField[Math.max(y - 1, 0) * cols + x]
        const c: number = this.curlField[i]
        let fx: number = 0.5 * (Math.abs(t) - Math.abs(b))
        let fy: number = 0.5 * (Math.abs(r) - Math.abs(l))
        const len: number = Math.sqrt(fx * fx + fy * fy)
        fx /= len + 0.0001
        fy /= len + 0.0001
        fx *= CURL * c
        fy *= CURL * c
        fy *= -1
        this.vx0[i] = this.vx[i] + fx * dt
        this.vy0[i] = this.vy[i] + fy * dt
      }
    }
    this.swapVelocity()
  }

  // ---------- 3~6. 压力求解与投影 ----------

  private computeDivergence(): void {
    const cols: number = this.simCols
    const rows: number = this.simRows
    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) {
        const i: number = y * cols + x
        const cx: number = this.vx[i]
        const cy: number = this.vy[i]
        const l: number = x > 0 ? this.vx[y * cols + x - 1] : -cx
        const r: number = x < cols - 1 ? this.vx[y * cols + x + 1] : -cx
        const t: number = y < rows - 1 ? this.vy[(y + 1) * cols + x] : -cy
        const b: number = y > 0 ? this.vy[(y - 1) * cols + x] : -cy
        this.divergence[i] = 0.5 * (r - l + t - b)
      }
    }
  }

  /** 压力衰减（每帧乘算，防止压力累积） */
  private decayPressure(): void {
    for (let i = 0; i < this.pressure.length; i++) {
      this.pressure0[i] = this.pressure[i] * PRESSURE_DECAY
    }
    const temp: Float32Array = this.pressure
    this.pressure = this.pressure0
    this.pressure0 = temp
  }

  /** Jacobi 迭代：p = (L+R+B+T-div)/4，边界 clamp-to-edge */
  private jacobiPressure(): void {
    const cols: number = this.simCols
    const rows: number = this.simRows
    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) {
        const i: number = y * cols + x
        const l: number = this.pressure[y * cols + Math.max(x - 1, 0)]
        const r: number = this.pressure[y * cols + Math.min(x + 1, cols - 1)]
        const t: number = this.pressure[Math.min(y + 1, rows - 1) * cols + x]
        const b: number = this.pressure[Math.max(y - 1, 0) * cols + x]
        this.pressure0[i] = (l + r + b + t - this.divergence[i]) * 0.25
      }
    }
    const temp: Float32Array = this.pressure
    this.pressure = this.pressure0
    this.pressure0 = temp
  }

  /** 梯度减法（投影：把速度场修正为无散度） */
  private subtractGradient(): void {
    const cols: number = this.simCols
    const rows: number = this.simRows
    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) {
        const i: number = y * cols + x
        const l: number = this.pressure[y * cols + Math.max(x - 1, 0)]
        const r: number = this.pressure[y * cols + Math.min(x + 1, cols - 1)]
        const t: number = this.pressure[Math.min(y + 1, rows - 1) * cols + x]
        const b: number = this.pressure[Math.max(y - 1, 0) * cols + x]
        this.vx0[i] = this.vx[i] - (r - l)
        this.vy0[i] = this.vy[i] - (t - b)
      }
    }
    this.swapVelocity()
  }

  // ---------- 7/8. 半拉格朗日平流 ----------

  private advectVelocity(dt: number): void {
    const cols: number = this.simCols
    const rows: number = this.simRows
    const dissipation: number = 1 + VELOCITY_DISSIPATION * dt
    for (let y = 0; y < rows; y++) {
      const v: number = (y + 0.5) / rows
      for (let x = 0; x < cols; x++) {
        const i: number = y * cols + x
        const u: number = (x + 0.5) / cols
        // 半拉格朗日：沿速度场反向回溯一个时间步
        const su: number = u - dt * this.vx[i] / cols
        const sv: number = v - dt * this.vy[i] / rows
        this.vx0[i] = this.sampleField(this.vx, cols, rows, su, sv) / dissipation
        this.vy0[i] = this.sampleField(this.vy, cols, rows, su, sv) / dissipation
      }
    }
    this.swapVelocity()
  }

  private advectDye(dt: number): void {
    const cols: number = this.dyeCols
    const rows: number = this.dyeRows
    const dissipation: number = 1 + DENSITY_DISSIPATION * dt
    for (let y = 0; y < rows; y++) {
      const v: number = (y + 0.5) / rows
      for (let x = 0; x < cols; x++) {
        const i: number = y * cols + x
        const u: number = (x + 0.5) / cols
        // 染料平流在染料网格 uv 上采样速度场，texelSize 用速度网格（与安卓着色器一致）
        const velX: number = this.sampleField(this.vx, this.simCols, this.simRows, u, v)
        const velY: number = this.sampleField(this.vy, this.simCols, this.simRows, u, v)
        const su: number = u - dt * velX / this.simCols
        const sv: number = v - dt * velY / this.simRows
        this.dyeR0[i] = this.sampleField(this.dyeR, cols, rows, su, sv) / dissipation
        this.dyeG0[i] = this.sampleField(this.dyeG, cols, rows, su, sv) / dissipation
        this.dyeB0[i] = this.sampleField(this.dyeB, cols, rows, su, sv) / dissipation
      }
    }
    this.swapDye()
  }

  /** 双线性采样（uv → cell 中心对齐网格，越界 clamp-to-edge） */
  private sampleField(field: Float32Array, cols: number, rows: number,
    u: number, v: number): number {
    const gx: number = Math.min(cols - 1, Math.max(0, u * cols - 0.5))
    const gy: number = Math.min(rows - 1, Math.max(0, v * rows - 0.5))
    const x0: number = Math.floor(gx)
    const y0: number = Math.floor(gy)
    const x1: number = Math.min(x0 + 1, cols - 1)
    const y1: number = Math.min(y0 + 1, rows - 1)
    const tx: number = gx - x0
    const ty: number = gy - y0
    const a: number = field[y0 * cols + x0]
    const b: number = field[y0 * cols + x1]
    const c: number = field[y1 * cols + x0]
    const d: number = field[y1 * cols + x1]
    const top: number = a + (b - a) * tx
    const bottom: number = c + (d - c) * tx
    return top + (bottom - top) * ty
  }

  // ---------- 注入 / 种子 ----------

  private drainSplats(): void {
    let count: number = 0
    while (count < MAX_SPLAT_QUEUE && this.splatQueue.length > 0) {
      const point: FluidSplatPoint | undefined = this.splatQueue.shift()
      if (point === undefined) {
        break
      }
      this.applySplat(point.x, point.y, point.dx, point.dy, this.randomColor(), SPLAT_RADIUS)
      count++
    }
  }

  /** 静态种子：清空场后铺 8 个随机色斑（与安卓 seedStaticScene 同构） */
  private seedStaticScene(): void {
    this.clearFields()
    for (let i = 0; i < SEED_SPLATS; i++) {
      const x: number = 0.15 + Math.random() * 0.7
      const y: number = 0.15 + Math.random() * 0.7
      const angle: number = Math.random() * 2 * Math.PI
      const speed: number = 20 + Math.random() * 40
      this.applySplat(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed,
        this.colorAt(i), SEED_SPLAT_RADIUS)
    }
    this.seeded = true
  }

  private applySplat(x: number, y: number, dx: number, dy: number,
    color: number[], radius: number): void {
    let r: number = radius / SPLAT_RADIUS_SCALE
    if (this.aspectRatio > 1) {
      r *= this.aspectRatio
    }
    this.splatVelocity(x, y, dx, dy, r)
    this.splatDye(x, y, color[0] * DYE_INTENSITY, color[1] * DYE_INTENSITY,
      color[2] * DYE_INTENSITY, r)
  }

  /** 高斯注入速度场：exp(-dot(p,p)/radius)（p.x 乘宽高比，与安卓 splat shader 同式） */
  private splatVelocity(x: number, y: number, dx: number, dy: number, radius: number): void {
    const cols: number = this.simCols
    const rows: number = this.simRows
    const half: number = Math.ceil(Math.sqrt(radius) * Math.max(cols, rows)) + 1
    const cx: number = Math.floor(x * cols)
    const cy: number = Math.floor(y * rows)
    for (let gy = Math.max(0, cy - half); gy <= Math.min(rows - 1, cy + half); gy++) {
      for (let gx = Math.max(0, cx - half); gx <= Math.min(cols - 1, cx + half); gx++) {
        const u: number = (gx + 0.5) / cols
        const v: number = (gy + 0.5) / rows
        const px: number = (u - x) * this.aspectRatio
        const py: number = v - y
        const g: number = Math.exp(-(px * px + py * py) / radius)
        const i: number = gy * cols + gx
        this.vx[i] += dx * g
        this.vy[i] += dy * g
      }
    }
  }

  /** 高斯注入染料场（rgb 三通道同半径同核） */
  private splatDye(x: number, y: number, cr: number, cg: number, cb: number,
    radius: number): void {
    const cols: number = this.dyeCols
    const rows: number = this.dyeRows
    const half: number = Math.ceil(Math.sqrt(radius) * Math.max(cols, rows)) + 1
    const cx: number = Math.floor(x * cols)
    const cy: number = Math.floor(y * rows)
    for (let gy = Math.max(0, cy - half); gy <= Math.min(rows - 1, cy + half); gy++) {
      for (let gx = Math.max(0, cx - half); gx <= Math.min(cols - 1, cx + half); gx++) {
        const u: number = (gx + 0.5) / cols
        const v: number = (gy + 0.5) / rows
        const px: number = (u - x) * this.aspectRatio
        const py: number = v - y
        const g: number = Math.exp(-(px * px + py * py) / radius)
        const i: number = gy * cols + gx
        this.dyeR[i] += cr * g
        this.dyeG[i] += cg * g
        this.dyeB[i] += cb * g
      }
    }
  }

  // ---------- 渲染支持 ----------

  /**
   * 把染料场（加法叠底色）写入 RGBA8888 缓冲（每通道 0..255，alpha 恒 255）。
   * 页面经 createPixelMapSync + drawImage 放大上屏。
   */
  fillDyeBuffer(target: Uint8Array): void {
    const count: number = this.dyeCols * this.dyeRows
    const br: number = this.baseColor[0]
    const bg: number = this.baseColor[1]
    const bb: number = this.baseColor[2]
    for (let i = 0; i < count; i++) {
      const r: number = Math.min(1, Math.max(0, br + this.dyeR[i])) * 255
      const g: number = Math.min(1, Math.max(0, bg + this.dyeG[i])) * 255
      const b: number = Math.min(1, Math.max(0, bb + this.dyeB[i])) * 255
      const o: number = i * 4
      target[o] = r
      target[o + 1] = g
      target[o + 2] = b
      target[o + 3] = 255
    }
  }

  // ---------- 工具 ----------

  private randomColor(): number[] {
    const index: number = Math.floor(Math.random() * this.palette.length)
    return this.colorAt(index)
  }

  private colorAt(index: number): number[] {
    const len: number = this.palette.length
    const mod: number = ((index % len) + len) % len
    return this.palette[mod]
  }

  private clearFields(): void {
    this.vx.fill(0)
    this.vy.fill(0)
    this.vx0.fill(0)
    this.vy0.fill(0)
    this.pressure.fill(0)
    this.pressure0.fill(0)
    this.divergence.fill(0)
    this.curlField.fill(0)
    this.dyeR.fill(0)
    this.dyeG.fill(0)
    this.dyeB.fill(0)
    this.dyeR0.fill(0)
    this.dyeG0.fill(0)
    this.dyeB0.fill(0)
  }

  private swapVelocity(): void {
    const tx: Float32Array = this.vx
    this.vx = this.vx0
    this.vx0 = tx
    const ty: Float32Array = this.vy
    this.vy = this.vy0
    this.vy0 = ty
  }

  private swapDye(): void {
    const tr: Float32Array = this.dyeR
    this.dyeR = this.dyeR0
    this.dyeR0 = tr
    const tg: Float32Array = this.dyeG
    this.dyeG = this.dyeG0
    this.dyeG0 = tg
    const tb: Float32Array = this.dyeB
    this.dyeB = this.dyeB0
    this.dyeB0 = tb
  }
}

/** 速度/压力场短边分辨率（低分辨率保帧率） */
const SIM_RESOLUTION: number = 48

/** 染料场短边分辨率（高分辨率保锐利） */
const DYE_RESOLUTION: number = 96

/** Jacobi 压力迭代次数（CPU 版由安卓端 20 次降至 8 次保帧率） */
const PRESSURE_ITERATIONS: number = 8

/** 压力衰减（每帧乘算，防止压力累积） */
const PRESSURE_DECAY: number = 0.8

/** 涡量约束强度（越大旋涡越猛） */
const CURL: number = 30

/** 触摸注入半径（nominal 占画布比例，进高斯前按 SPLAT_RADIUS_SCALE 换算） */
const SPLAT_RADIUS: number = 0.25

/** 静态种子注入半径（nominal；有效半径 = 0.36/300，与安卓调参结论一致） */
const SEED_SPLAT_RADIUS: number = 0.36

/** 半径换算除数：高斯指数 exp(-dot(p,p)/radius) 的分母（平方尺度） */
const SPLAT_RADIUS_SCALE: number = 300

/** 触摸注入力（划得越快速度增量越大） */
const SPLAT_FORCE: number = 6000

/** 速度耗散（平流衰减系数） */
const VELOCITY_DISSIPATION: number = 0.2

/** 染料耗散（半衰期约 2.3s，连续注入不整盘饱和） */
const DENSITY_DISSIPATION: number = 0.30

/** 染料亮度系数（调色板色乘算后注入） */
const DYE_INTENSITY: number = 0.20

/** 静态种子注入次数 */
const SEED_SPLATS: number = 8

/** 单帧最多应用的注入点数（防积压） */
const MAX_SPLAT_QUEUE: number = 64

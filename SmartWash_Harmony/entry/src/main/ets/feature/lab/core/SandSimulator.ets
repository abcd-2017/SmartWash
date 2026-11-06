/**
 * 落沙实验室元胞自动机内核 — 纯逻辑（零 UI 依赖），对齐安卓 SandSimulator.kt。
 *
 * 网格：[cols] × [rows]，行 0 为顶。[cells] 存元素 id（Int8Array），[aux] 存辅助状态
 * （寿命 / 溶解进度）。每帧顺序：反应 → 下落/侧流 → 上升 → 衰老。
 *
 * 运动：沙/洗衣粉直落+斜落；水直落→斜落→左右侧流；泡沫缓升；火上升；蒸汽快升两格；
 * 污渍/织物静止。反应（8 邻接 + 概率）：水溶污渍 / 水+洗衣粉成泡沫 / 水灭火成蒸汽 /
 * 火点燃织物 / 蒸汽到顶或寿命到凝结为水。
 */

/** 落沙实验室元素类型（9 种，id 与数组下标一致） */
export enum SandElement {
  /** 空（渲染为透明，画布底色透出） */
  EMPTY = 0,
  /** 沙：直落 + 斜落 */
  SAND = 1,
  /** 水：直落 + 斜落 + 侧流；溶解污渍、与洗衣粉生成泡沫、遇火成蒸汽 */
  WATER = 2,
  /** 洗衣粉：直落 + 斜落；遇水变泡沫 */
  DETERGENT = 3,
  /** 泡沫：缓升；寿命到破裂消失 */
  FOAM = 4,
  /** 污渍：静止；邻接水时按概率溶解 */
  STAIN = 5,
  /** 织物：静止；邻接火时被点燃 */
  FABRIC = 6,
  /** 火：上升；遇水熄灭、点燃织物 */
  FIRE = 7,
  /** 蒸汽：快升；到顶或寿命到凝结为水 */
  STEAM = 8,
}

/** 模拟参数（概率 ∈ [0,1]；寿命 / 溶解抗性单位 = tick 帧数） */
export class SandParams {
  foamLife: number = 140
  steamLife: number = 70
  fireLife: number = 110
  stainResistance: number = 6
  stainDissolveChance: number = 0.35
  detergentFoamChance: number = 0.18
  fireWaterChance: number = 0.60
  fireFabricChance: number = 0.12
  foamRiseChance: number = 0.35
  fireRiseChance: number = 0.80
  steamRiseChance: number = 0.90
  brushDensity: number = 0.70
}

/** 默认笔刷半径（cell 数） */
const DEFAULT_BRUSH_RADIUS: number = 2

/** 斜向移动方向（随机顺序，避免单侧堆积） */
const DIRECTIONS: number[] = [-1, 1]
const DIRECTIONS_REVERSED: number[] = [1, -1]

/** 元素 id → 枚举查找表（越界回落 EMPTY，保证脏数据不崩） */
const SAND_ELEMENTS: SandElement[] = [
  SandElement.EMPTY,
  SandElement.SAND,
  SandElement.WATER,
  SandElement.DETERGENT,
  SandElement.FOAM,
  SandElement.STAIN,
  SandElement.FABRIC,
  SandElement.FIRE,
  SandElement.STEAM,
]

export class SandSimulator {
  private params: SandParams
  private colsValue: number = 0
  private rowsValue: number = 0

  private cells: Int8Array = new Int8Array(0)
  private aux: Int32Array = new Int32Array(0)
  private movedFall: boolean[] = []
  private movedRise: boolean[] = []

  constructor(params: SandParams | null = null) {
    this.params = params !== null ? params : new SandParams()
  }

  get cols(): number {
    return this.colsValue
  }

  get rows(): number {
    return this.rowsValue
  }

  /** 注入网格尺寸（首次或旋转/分屏变化时调用；会清空既有沙盒） */
  resize(cols: number, rows: number): void {
    if (cols <= 0 || rows <= 0) {
      return
    }
    this.colsValue = cols
    this.rowsValue = rows
    this.cells = new Int8Array(cols * rows)
    this.aux = new Int32Array(cols * rows)
    this.movedFall = new Array<boolean>(cols * rows).fill(false)
    this.movedRise = new Array<boolean>(cols * rows).fill(false)
  }

  /** 清空沙盒（元素与辅助状态一并归零） */
  clear(): void {
    this.cells.fill(SandElement.EMPTY)
    this.aux.fill(0)
  }

  /** 读取指定格元素（越界返回 EMPTY） */
  elementAt(col: number, row: number): SandElement {
    if (col < 0 || row < 0 || col >= this.colsValue || row >= this.rowsValue) {
      return SandElement.EMPTY
    }
    return this.elementFromId(this.cells[row * this.colsValue + col])
  }

  /** 按 id 反查元素（越界回落 EMPTY，保证脏数据不崩） */
  elementFromId(id: number): SandElement {
    if (id < 0 || id >= SAND_ELEMENTS.length) {
      return SandElement.EMPTY
    }
    return SAND_ELEMENTS[id]
  }

  /**
   * 圆形概率笔刷：以 (col,row) 为圆心、[radius] 为半径，按 brushDensity 概率写入
   * [element]（留出自然笔触感；EMPTY 即橡皮）。
   */
  paint(col: number, row: number, element: SandElement, radius: number = DEFAULT_BRUSH_RADIUS): void {
    if (this.colsValue <= 0 || this.rowsValue <= 0 || radius < 0) {
      return
    }
    const r2: number = radius * radius
    for (let dy = -radius; dy <= radius; dy++) {
      for (let dx = -radius; dx <= radius; dx++) {
        if (dx * dx + dy * dy > r2) {
          continue
        }
        if (Math.random() > this.params.brushDensity) {
          continue
        }
        const nc: number = col + dx
        const nr: number = row + dy
        if (nc < 0 || nr < 0 || nc >= this.colsValue || nr >= this.rowsValue) {
          continue
        }
        this.replace(nr * this.colsValue + nc, element, this.lifetimeOf(element))
      }
    }
  }

  /** 推进一帧：反应 → 下落/侧流 → 上升 → 衰老 */
  tick(): void {
    if (this.colsValue <= 0 || this.rowsValue <= 0) {
      return
    }
    this.react()
    this.moveFall()
    this.moveRise()
    this.age()
  }

  // ---------- 反应 ----------

  private react(): void {
    for (let row = 0; row < this.rowsValue; row++) {
      for (let col = 0; col < this.colsValue; col++) {
        const index: number = row * this.colsValue + col
        const element: SandElement = this.elementFromId(this.cells[index])
        if (element === SandElement.FIRE) {
          this.reactFire(col, row, index)
        } else if (element === SandElement.WATER) {
          this.reactWater(col, row, index)
        } else if (element === SandElement.STAIN) {
          this.reactStain(col, row, index)
        } else if (element === SandElement.DETERGENT) {
          this.reactDetergent(col, row, index)
        }
      }
    }
  }

  /** 火：先查水（水变蒸汽、火熄灭），否则点燃织物 */
  private reactFire(col: number, row: number, index: number): void {
    const water: number = this.firstNeighbor(col, row, SandElement.WATER)
    if (water >= 0 && Math.random() < this.params.fireWaterChance) {
      this.replace(water, SandElement.STEAM, this.params.steamLife)
      this.replace(index, SandElement.EMPTY, 0)
      return
    }
    const fabric: number = this.firstNeighbor(col, row, SandElement.FABRIC)
    if (fabric >= 0 && Math.random() < this.params.fireFabricChance) {
      this.replace(fabric, SandElement.FIRE, this.params.fireLife)
    }
  }

  /** 水：邻接火时自身变蒸汽、火熄灭（火未先处理的兜底路径） */
  private reactWater(col: number, row: number, index: number): void {
    const fire: number = this.firstNeighbor(col, row, SandElement.FIRE)
    if (fire >= 0 && Math.random() < this.params.fireWaterChance) {
      this.replace(index, SandElement.STEAM, this.params.steamLife)
      this.replace(fire, SandElement.EMPTY, 0)
    }
  }

  /** 污渍：邻接水时按概率消耗溶解进度，归零变空 */
  private reactStain(col: number, row: number, index: number): void {
    if (this.firstNeighbor(col, row, SandElement.WATER) >= 0 &&
      Math.random() < this.params.stainDissolveChance) {
      this.aux[index] -= 1
      if (this.aux[index] <= 0) {
        this.replace(index, SandElement.EMPTY, 0)
      }
    }
  }

  /** 洗衣粉：邻接水时按概率变泡沫 */
  private reactDetergent(col: number, row: number, index: number): void {
    if (this.firstNeighbor(col, row, SandElement.WATER) >= 0 &&
      Math.random() < this.params.detergentFoamChance) {
      this.replace(index, SandElement.FOAM, this.params.foamLife)
    }
  }

  /** 8 邻域内首个目标元素下标（无则 -1） */
  private firstNeighbor(col: number, row: number, target: SandElement): number {
    for (let dy = -1; dy <= 1; dy++) {
      for (let dx = -1; dx <= 1; dx++) {
        if (dx === 0 && dy === 0) {
          continue
        }
        const nc: number = col + dx
        const nr: number = row + dy
        if (nc < 0 || nr < 0 || nc >= this.colsValue || nr >= this.rowsValue) {
          continue
        }
        const ni: number = nr * this.colsValue + nc
        if (this.elementFromId(this.cells[ni]) === target) {
          return ni
        }
      }
    }
    return -1
  }

  // ---------- 运动 ----------

  /** 下落 / 侧流：自底向上遍历，斜落方向随机；水在被堵后左右侧流 */
  private moveFall(): void {
    this.movedFall.fill(false)
    for (let row = this.rowsValue - 1; row >= 0; row--) {
      for (let col = 0; col < this.colsValue; col++) {
        const index: number = row * this.colsValue + col
        if (this.movedFall[index]) {
          continue
        }
        const element: SandElement = this.elementFromId(this.cells[index])
        if (element === SandElement.SAND || element === SandElement.DETERGENT) {
          this.moveDown(index, col, row)
        } else if (element === SandElement.WATER) {
          this.moveWater(index, col, row)
        }
      }
    }
  }

  /** 沙 / 洗衣粉：直落 → 随机顺序斜落 */
  private moveDown(index: number, col: number, row: number): void {
    if (row + 1 >= this.rowsValue) {
      return
    }
    const below: number = index + this.colsValue
    if (this.isEmpty(below)) {
      this.swap(index, below)
      this.movedFall[index] = true
      this.movedFall[below] = true
      return
    }
    const directions: number[] = this.diagonalDirections()
    for (const dx of directions) {
      const nc: number = col + dx
      if (nc < 0 || nc >= this.colsValue) {
        continue
      }
      const target: number = below + dx
      if (this.isEmpty(target)) {
        this.swap(index, target)
        this.movedFall[index] = true
        this.movedFall[target] = true
        return
      }
    }
  }

  /** 水：直落 → 斜落 → 同排左右侧流 */
  private moveWater(index: number, col: number, row: number): void {
    if (row + 1 < this.rowsValue) {
      const below: number = index + this.colsValue
      if (this.isEmpty(below)) {
        this.swap(index, below)
        this.movedFall[index] = true
        this.movedFall[below] = true
        return
      }
      const directions: number[] = this.diagonalDirections()
      for (const dx of directions) {
        const nc: number = col + dx
        if (nc < 0 || nc >= this.colsValue) {
          continue
        }
        const target: number = below + dx
        if (this.isEmpty(target)) {
          this.swap(index, target)
          this.movedFall[index] = true
          this.movedFall[target] = true
          return
        }
      }
    }
    const directions: number[] = this.diagonalDirections()
    for (const dx of directions) {
      const nc: number = col + dx
      if (nc < 0 || nc >= this.colsValue) {
        continue
      }
      const target: number = index + dx
      if (this.isEmpty(target)) {
        this.swap(index, target)
        this.movedFall[index] = true
        this.movedFall[target] = true
        return
      }
    }
  }

  /** 上升：自上而下遍历，防止同一帧内被反复抬升 */
  private moveRise(): void {
    this.movedRise.fill(false)
    for (let row = 0; row < this.rowsValue; row++) {
      for (let col = 0; col < this.colsValue; col++) {
        const index: number = row * this.colsValue + col
        if (this.movedRise[index]) {
          continue
        }
        const element: SandElement = this.elementFromId(this.cells[index])
        if (element === SandElement.FOAM) {
          if (Math.random() < this.params.foamRiseChance) {
            this.moveUp(index, col, row)
          }
        } else if (element === SandElement.FIRE) {
          if (Math.random() < this.params.fireRiseChance) {
            this.moveUp(index, col, row)
          }
        } else if (element === SandElement.STEAM) {
          this.moveSteam(index, col, row)
        }
      }
    }
  }

  /** 蒸汽快升：两格皆空时一次上升两格，否则退化为常规上升 */
  private moveSteam(index: number, col: number, row: number): void {
    if (Math.random() >= this.params.steamRiseChance) {
      return
    }
    if (row >= 2) {
      const oneAbove: number = index - this.colsValue
      const twoAbove: number = index - 2 * this.colsValue
      if (this.isEmpty(oneAbove) && this.isEmpty(twoAbove)) {
        this.swap(index, twoAbove)
        this.movedRise[index] = true
        this.movedRise[twoAbove] = true
        return
      }
    }
    this.moveUp(index, col, row)
  }

  /** 常规上升：直上 → 随机顺序斜上 */
  private moveUp(index: number, col: number, row: number): void {
    if (row - 1 < 0) {
      return
    }
    const above: number = index - this.colsValue
    if (this.isEmpty(above)) {
      this.swap(index, above)
      this.movedRise[index] = true
      this.movedRise[above] = true
      return
    }
    const directions: number[] = this.diagonalDirections()
    for (const dx of directions) {
      const nc: number = col + dx
      if (nc < 0 || nc >= this.colsValue) {
        continue
      }
      const target: number = above + dx
      if (this.isEmpty(target)) {
        this.swap(index, target)
        this.movedRise[index] = true
        this.movedRise[target] = true
        return
      }
    }
  }

  // ---------- 衰老 ----------

  /** 寿命推进：泡沫/火归零变空；蒸汽到顶或寿命到变水 */
  private age(): void {
    for (let index = 0; index < this.cells.length; index++) {
      const element: SandElement = this.elementFromId(this.cells[index])
      if (element === SandElement.FOAM) {
        this.aux[index] -= 1
        if (this.aux[index] <= 0) {
          this.replace(index, SandElement.EMPTY, 0)
        }
      } else if (element === SandElement.FIRE) {
        this.aux[index] -= 1
        if (this.aux[index] <= 0) {
          this.replace(index, SandElement.EMPTY, 0)
        }
      } else if (element === SandElement.STEAM) {
        this.aux[index] -= 1
        if (index < this.colsValue || this.aux[index] <= 0) {
          this.replace(index, SandElement.WATER, 0)
        }
      }
    }
  }

  // ---------- 基础工具 ----------

  /** 写入元素并初始化其辅助状态（寿命 / 溶解抗性） */
  private replace(index: number, element: SandElement, lifetime: number): void {
    this.cells[index] = Number(element)
    this.aux[index] = lifetime
  }

  /** 新元素对应的初始辅助状态 */
  private lifetimeOf(element: SandElement): number {
    if (element === SandElement.FOAM) {
      return this.params.foamLife
    }
    if (element === SandElement.STEAM) {
      return this.params.steamLife
    }
    if (element === SandElement.FIRE) {
      return this.params.fireLife
    }
    if (element === SandElement.STAIN) {
      return this.params.stainResistance
    }
    return 0
  }

  private isEmpty(index: number): boolean {
    return index >= 0 && index < this.cells.length && this.cells[index] === SandElement.EMPTY
  }

  /** 交换两格元素与辅助状态 */
  private swap(a: number, b: number): void {
    const cell: number = this.cells[a]
    this.cells[a] = this.cells[b]
    this.cells[b] = cell
    const state: number = this.aux[a]
    this.aux[a] = this.aux[b]
    this.aux[b] = state
  }

  /** 斜向移动方向（随机顺序，避免单侧堆积） */
  private diagonalDirections(): number[] {
    return Math.random() < 0.5 ? DIRECTIONS : DIRECTIONS_REVERSED
  }
}

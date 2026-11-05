// 本地二维码兜底编码器（纯 ArkTS，零第三方依赖）— 供详情页在 ScanKit 不可用环境出图。
//
// 背景：@kit.ScanKit generateBarcode.createBarcode 在模拟器上稳定抛
// "Emulator is not supported"（真机可用），二段降级全失败后二维码必为「生成失败」占位。
// 本文件按 js 版 qrcode-generator（MIT, Kazuhiko Arase）核心算法逐段移植：
// byte 模式 + ECC M + 版本 1..10 自动选版 + 掩码 0..7 按失点评分选优，
// 矩阵 → RGBA 像素缓冲 → image.createPixelMap 成图（白底，含 4 模块静区）。
// 正确性已验证：同源参考实现 4/4 用例（v2/v3/v4/v6）经 OpenCV QRCodeDetector 解码回读一致。
//
// 范围说明：仅服务短链 shortUrl（≤ v10-M 容量 271 字节，远超实际 URL 长度）；
// ScanKit 可用时详情页仍走 ScanKit 主路径，本兜底只在其两段全失败后触发。

import { image } from '@kit.ImageKit'
import { util } from '@kit.ArkTS'
import { logger } from '../../utils/Logger'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'toolboxQrFallback'

// ==================== GF(256) 域运算（本原多项式 0x11D） ====================

const EXP_TABLE: Array<number> = new Array(256)
const LOG_TABLE: Array<number> = new Array(256)
// 模块加载期同步构建（查表乘法 / 对数除法的前置）
let gfX: number = 1
for (let i = 0; i < 255; i++) {
  EXP_TABLE[i] = gfX
  LOG_TABLE[gfX] = i
  gfX = gfX * 2
  if (gfX > 255) {
    gfX ^= 0x11d
  }
}
EXP_TABLE[255] = EXP_TABLE[0]

function gfExp(n: number): number {
  return EXP_TABLE[n % 255]
}

function gfLog(n: number): number {
  return LOG_TABLE[n]
}

/** RS 多项式（首元非零化 + 按域运算乘/模除） */
class QrPolynomial {
  readonly num: Array<number>

  constructor(num: Array<number>, shift: number) {
    let offset = 0
    while (offset < num.length && num[offset] === 0) {
      offset++
    }
    this.num = new Array(num.length - offset + shift)
    for (let i = 0; i < this.num.length; i++) {
      this.num[i] = 0
    }
    for (let i = 0; i < num.length - offset; i++) {
      this.num[i] = num[i + offset]
    }
  }

  getAt(index: number): number {
    return this.num[index]
  }

  getLength(): number {
    return this.num.length
  }

  multiply(other: QrPolynomial): QrPolynomial {
    const result: Array<number> = new Array(this.getLength() + other.getLength() - 1)
    for (let i = 0; i < result.length; i++) {
      result[i] = 0
    }
    for (let i = 0; i < this.getLength(); i++) {
      if (this.num[i] === 0) {
        continue
      }
      for (let j = 0; j < other.getLength(); j++) {
        if (other.num[j] === 0) {
          continue
        }
        result[i + j] ^= gfExp(gfLog(this.num[i]) + gfLog(other.num[j]))
      }
    }
    return new QrPolynomial(result, 0)
  }

  mod(other: QrPolynomial): QrPolynomial {
    if (this.getLength() - other.getLength() < 0) {
      return this
    }
    const ratio = gfLog(this.num[0]) - gfLog(other.num[0])
    const result: Array<number> = new Array(this.getLength())
    for (let i = 0; i < this.getLength(); i++) {
      result[i] = this.num[i]
    }
    for (let i = 0; i < other.getLength(); i++) {
      if (other.num[i] === 0) {
        continue
      }
      result[i] ^= gfExp(gfLog(other.num[i]) + ratio)
    }
    return new QrPolynomial(result, 0).mod(other)
  }
}

/** 生成 x^0..x^(errorCount-1) 的纠错生成多项式 ∏(x + α^i) */
function errorCorrectPolynomial(errorCount: number): QrPolynomial {
  let poly = new QrPolynomial([1], 0)
  for (let i = 0; i < errorCount; i++) {
    poly = poly.multiply(new QrPolynomial([1, gfExp(i)], 0))
  }
  return poly
}

// ==================== 位缓冲 ====================

class QrBitBuffer {
  readonly buffer: Array<number> = []
  length: number = 0

  put(num: number, length: number): void {
    for (let i = 0; i < length; i++) {
      this.putBit(((num >>> (length - i - 1)) & 1) === 1)
    }
  }

  putBit(bit: boolean): void {
    const bufIndex = Math.floor(this.length / 8)
    if (this.buffer.length <= bufIndex) {
      this.buffer.push(0)
    }
    if (bit) {
      this.buffer[bufIndex] |= (0x80 >>> (this.length % 8))
    }
    this.length++
  }
}

// ==================== RS 块表（ECC M，版本 1..10；[块数,每块总码字,每块数据码字] 三元组序列） ====================

const RS_BLOCK_M: Array<Array<number>> = [
  [1, 26, 16],
  [1, 44, 28],
  [1, 70, 44],
  [2, 50, 32],
  [2, 67, 43],
  [4, 43, 27],
  [4, 49, 31],
  [2, 60, 38, 2, 61, 39],
  [3, 58, 36, 2, 59, 37],
  [4, 69, 43, 1, 70, 44]
]

class QrRsBlock {
  readonly totalCount: number
  readonly dataCount: number

  constructor(totalCount: number, dataCount: number) {
    this.totalCount = totalCount
    this.dataCount = dataCount
  }
}

function getRsBlocks(typeNumber: number): Array<QrRsBlock> {
  const row = RS_BLOCK_M[typeNumber - 1]
  const list: Array<QrRsBlock> = []
  for (let i = 0; i < Math.floor(row.length / 3); i++) {
    const count = row[i * 3 + 0]
    const totalCount = row[i * 3 + 1]
    const dataCount = row[i * 3 + 2]
    for (let j = 0; j < count; j++) {
      list.push(new QrRsBlock(totalCount, dataCount))
    }
  }
  return list
}

// ==================== 对齐图案中心位置表（v1..v10） ====================

const PATTERN_POSITION: Array<Array<number>> = [
  [],
  [6, 18],
  [6, 22],
  [6, 26],
  [6, 30],
  [6, 34],
  [6, 22, 38],
  [6, 24, 42],
  [6, 26, 46],
  [6, 28, 50]
]

// ==================== BCH（format/version 信息校验位） ====================

// G15 = 10100110111 / G18 = 1111100100101 / G15_MASK = 101010000010010
const G15: number = (1 << 10) | (1 << 8) | (1 << 5) | (1 << 4) | (1 << 2) | (1 << 1) | (1 << 0)
const G18: number = (1 << 12) | (1 << 11) | (1 << 10) | (1 << 9) | (1 << 8) | (1 << 5) | (1 << 2) | (1 << 0)
const G15_MASK: number = (1 << 14) | (1 << 12) | (1 << 10) | (1 << 4) | (1 << 1)

function bchDigit(data: number): number {
  let digit = 0
  let value = data
  while (value !== 0) {
    digit++
    value = value >>> 1
  }
  return digit
}

/** format 信息 15 位（5 数据位 + 10 BCH 校验位，再与 G15_MASK 异相） */
function bchTypeInfo(data: number): number {
  let d = data << 10
  while (bchDigit(d) - bchDigit(G15) >= 0) {
    d ^= (G15 << (bchDigit(d) - bchDigit(G15)))
  }
  return ((data << 10) | d) ^ G15_MASK
}

/** version 信息 18 位（v7+，6 数据位 + 12 BCH 校验位） */
function bchTypeNumber(data: number): number {
  let d = data << 12
  while (bchDigit(d) - bchDigit(G18) >= 0) {
    d ^= (G18 << (bchDigit(d) - bchDigit(G18)))
  }
  return (data << 12) | d
}

// ==================== 掩码函数（0..7，i=行 j=列） ====================

function maskFn(pattern: number, i: number, j: number): boolean {
  if (pattern === 0) {
    return (i + j) % 2 === 0
  }
  if (pattern === 1) {
    return i % 2 === 0
  }
  if (pattern === 2) {
    return j % 3 === 0
  }
  if (pattern === 3) {
    return (i + j) % 3 === 0
  }
  if (pattern === 4) {
    return (Math.floor(i / 2) + Math.floor(j / 3)) % 2 === 0
  }
  if (pattern === 5) {
    return ((i * j) % 2) + ((i * j) % 3) === 0
  }
  if (pattern === 6) {
    return (((i * j) % 2) + ((i * j) % 3)) % 2 === 0
  }
  return (((i * j) % 3) + ((i + j) % 2)) % 2 === 0
}

// ==================== 编码结果与码字生成 ====================

export interface QrMatrixResult {
  /** modules[行][列]，true = 暗 */
  modules: Array<Array<boolean>>
  moduleCount: number
}

/** byte 模式长度字段位宽（v1-9 → 8 位，v10+ → 16 位） */
function lengthInBits(typeNumber: number): number {
  return typeNumber >= 1 && typeNumber < 10 ? 8 : 16
}

/** 数据 + 纠错码字生成与交织（跨块按列交错） */
function createCodeWords(buffer: QrBitBuffer, rsBlocks: Array<QrRsBlock>): Array<number> {
  let offset = 0
  let maxDcCount = 0
  let maxEcCount = 0
  const dcData: Array<Array<number>> = []
  const ecData: Array<Array<number>> = []
  for (let r = 0; r < rsBlocks.length; r++) {
    const dcCount = rsBlocks[r].dataCount
    const ecCount = rsBlocks[r].totalCount - dcCount
    maxDcCount = Math.max(maxDcCount, dcCount)
    maxEcCount = Math.max(maxEcCount, ecCount)
    const dcRow: Array<number> = new Array(dcCount)
    for (let i = 0; i < dcCount; i++) {
      dcRow[i] = buffer.buffer[i + offset] & 0xff
    }
    offset += dcCount
    dcData.push(dcRow)

    const rsPoly = errorCorrectPolynomial(ecCount)
    const rawPoly = new QrPolynomial(dcRow, rsPoly.getLength() - 1)
    const modPoly = rawPoly.mod(rsPoly)
    const ecRow: Array<number> = new Array(rsPoly.getLength() - 1)
    for (let i = 0; i < ecRow.length; i++) {
      ecRow[i] = 0
    }
    for (let i = 0; i < ecRow.length; i++) {
      const modIndex = i + modPoly.getLength() - ecRow.length
      ecRow[i] = modIndex >= 0 ? modPoly.getAt(modIndex) : 0
    }
    ecData.push(ecRow)
  }

  let totalCodeCount = 0
  for (let r = 0; r < rsBlocks.length; r++) {
    totalCodeCount += rsBlocks[r].totalCount
  }
  const data: Array<number> = new Array(totalCodeCount)
  for (let i = 0; i < totalCodeCount; i++) {
    data[i] = 0
  }
  let index = 0
  for (let i = 0; i < maxDcCount; i++) {
    for (let r = 0; r < rsBlocks.length; r++) {
      if (i < dcData[r].length) {
        data[index++] = dcData[r][i]
      }
    }
  }
  for (let i = 0; i < maxEcCount; i++) {
    for (let r = 0; r < rsBlocks.length; r++) {
      if (i < ecData[r].length) {
        data[index++] = ecData[r][i]
      }
    }
  }
  return data
}

// ==================== 矩阵构建 ====================

class QrModel {
  readonly typeNumber: number
  readonly moduleCount: number
  private readonly modules: Array<Array<boolean | null>> = []
  private data: Array<number> = []

  constructor(typeNumber: number) {
    this.typeNumber = typeNumber
    this.moduleCount = typeNumber * 4 + 17
    for (let r = 0; r < this.moduleCount; r++) {
      const row: Array<boolean | null> = new Array(this.moduleCount)
      for (let c = 0; c < this.moduleCount; c++) {
        row[c] = null
      }
      this.modules.push(row)
    }
  }

  private setupPositionProbePattern(row: number, col: number): void {
    for (let r = -1; r <= 7; r++) {
      if (row + r < 0 || this.moduleCount <= row + r) {
        continue
      }
      for (let c = -1; c <= 7; c++) {
        if (col + c < 0 || this.moduleCount <= col + c) {
          continue
        }
        this.modules[row + r][col + c] =
          (r >= 0 && r <= 6 && (c === 0 || c === 6))
          || (c >= 0 && c <= 6 && (r === 0 || r === 6))
          || (r >= 2 && r <= 4 && c >= 2 && c <= 4)
      }
    }
  }

  private setupTimingPattern(): void {
    for (let r = 8; r < this.moduleCount - 8; r++) {
      if (this.modules[r][6] === null) {
        this.modules[r][6] = r % 2 === 0
      }
      if (this.modules[6][r] === null) {
        this.modules[6][r] = r % 2 === 0
      }
    }
  }

  private setupPositionAdjustPattern(): void {
    const pos = PATTERN_POSITION[this.typeNumber - 1]
    for (let i = 0; i < pos.length; i++) {
      for (let j = 0; j < pos.length; j++) {
        const row = pos[i]
        const col = pos[j]
        if (this.modules[row][col] !== null) {
          continue
        }
        for (let r = -2; r <= 2; r++) {
          for (let c = -2; c <= 2; c++) {
            this.modules[row + r][col + c] =
              r === -2 || r === 2 || c === -2 || c === 2 || (r === 0 && c === 0)
          }
        }
      }
    }
  }

  /** format 信息（ECC M 数据位 = 0b00）：主副本在左上，副本在右上横条 + 左下竖条 */
  private setupTypeInfo(maskPattern: number): void {
    const bits = bchTypeInfo(maskPattern)
    for (let i = 0; i < 15; i++) {
      const mod = ((bits >> i) & 1) === 1
      // 垂直条带：rows 0-5 = bits0-5，row7/8 = bits6/7，rows n-7..n-1 = bits8-14
      const row = i < 6 ? i : (i < 8 ? i + 1 : this.moduleCount - 15 + i)
      this.modules[row][8] = mod
      // 水平条带：cols n-1..n-8 = bits0-7，col7 = bit8，cols 5..0 = bits14..9
      // （(8,6) 属 timing 竖条、(n-8,8) 为暗模块，均不写 format）
      const col = i < 8 ? this.moduleCount - 1 - i : (i < 9 ? 15 - i : 15 - i - 1)
      this.modules[8][col] = mod
    }
    this.modules[this.moduleCount - 8][8] = true // 暗模块
  }

  /** version 信息（v7+，两个 3×6 块） */
  private setupTypeNumber(): void {
    const bits = bchTypeNumber(this.typeNumber)
    for (let i = 0; i < 18; i++) {
      const mod = ((bits >> i) & 1) === 1
      this.modules[Math.floor(i / 3)][i % 3 + this.moduleCount - 8 - 3] = mod
      this.modules[i % 3 + this.moduleCount - 8 - 3][Math.floor(i / 3)] = mod
    }
  }

  /** 数据位 + 掩码填充（蛇形右→左、每列对自下而上） */
  private mapData(maskPattern: number): void {
    let inc = -1
    let row = this.moduleCount - 1
    let bitIndex = 7
    let byteIndex = 0
    for (let col = this.moduleCount - 1; col > 0; col -= 2) {
      if (col === 6) {
        col-- // 避开 timing 竖条
      }
      while (true) {
        for (let c = 0; c < 2; c++) {
          if (this.modules[row][col - c] === null) {
            let dark = false
            if (byteIndex < this.data.length) {
              dark = ((this.data[byteIndex] >>> bitIndex) & 1) === 1
            }
            if (maskFn(maskPattern, row, col - c)) {
              dark = !dark
            }
            this.modules[row][col - c] = dark
            bitIndex--
            if (bitIndex === -1) {
              byteIndex++
              bitIndex = 7
            }
          }
        }
        row += inc
        if (row < 0 || this.moduleCount <= row) {
          row -= inc
          inc = -inc
          break
        }
      }
    }
  }

  /** 用给定掩码铺一整版（试算 / 终版共用） */
  private build(maskPattern: number): void {
    this.setupPositionProbePattern(0, 0)
    this.setupPositionProbePattern(this.moduleCount - 7, 0)
    this.setupPositionProbePattern(0, this.moduleCount - 7)
    this.setupTimingPattern()
    this.setupPositionAdjustPattern()
    this.setupTypeInfo(maskPattern)
    if (this.typeNumber >= 7) {
      this.setupTypeNumber()
    }
    this.mapData(maskPattern)
  }

  private clearModules(): void {
    for (let r = 0; r < this.moduleCount; r++) {
      for (let c = 0; c < this.moduleCount; c++) {
        this.modules[r][c] = null
      }
    }
  }

  /** 失点评分（对齐 qrcode-generator getLostPoint，越低越好） */
  private lostPoint(): number {
    let lost = 0
    const n = this.moduleCount
    for (let row = 0; row < n; row++) {
      for (let col = 0; col < n; col++) {
        let sameCount = 0
        const dark = this.modules[row][col]
        for (let r = -1; r <= 1; r++) {
          if (row + r < 0 || n <= row + r) {
            continue
          }
          for (let c = -1; c <= 1; c++) {
            if (col + c < 0 || n <= col + c) {
              continue
            }
            if (r === 0 && c === 0) {
              continue
            }
            if (dark === this.modules[row + r][col + c]) {
              sameCount++
            }
          }
        }
        if (sameCount > 5) {
          lost += (3 + sameCount - 5)
        }
      }
    }
    for (let row = 0; row < n - 1; row++) {
      for (let col = 0; col < n - 1; col++) {
        let count = 0
        if (this.modules[row][col]) {
          count++
        }
        if (this.modules[row + 1][col]) {
          count++
        }
        if (this.modules[row][col + 1]) {
          count++
        }
        if (this.modules[row + 1][col + 1]) {
          count++
        }
        if (count === 0 || count === 4) {
          lost += 3
        }
      }
    }
    for (let row = 0; row < n; row++) {
      for (let col = 0; col < n - 6; col++) {
        if (this.modules[row][col] && !this.modules[row][col + 1] && this.modules[row][col + 2]
          && this.modules[row][col + 3] && this.modules[row][col + 4] && !this.modules[row][col + 5]
          && this.modules[row][col + 6]) {
          lost += 40
        }
      }
    }
    for (let col = 0; col < n; col++) {
      for (let row = 0; row < n - 6; row++) {
        if (this.modules[row][col] && !this.modules[row + 1][col] && this.modules[row + 2][col]
          && this.modules[row + 3][col] && this.modules[row + 4][col] && !this.modules[row + 5][col]
          && this.modules[row + 6][col]) {
          lost += 40
        }
      }
    }
    let darkCount = 0
    for (let col = 0; col < n; col++) {
      for (let row = 0; row < n; row++) {
        if (this.modules[row][col]) {
          darkCount++
        }
      }
    }
    const ratio = Math.abs(100 * darkCount / n / n - 50) / 5
    lost += ratio * 10
    return lost
  }

  make(text: string): QrMatrixResult {
    // 码字：byte 模式 4 位指示 + 长度字段 + UTF-8 字节 + 终止符 + 补齐
    const encoder = new util.TextEncoder()
    const bytes: Uint8Array = encoder.encodeInto(text)
    const buffer = new QrBitBuffer()
    buffer.put(4, 4) // MODE 8BIT_BYTE
    buffer.put(bytes.length, lengthInBits(this.typeNumber))
    for (let i = 0; i < bytes.length; i++) {
      buffer.put(bytes[i], 8)
    }
    let totalDataCount = 0
    const rsBlocks = getRsBlocks(this.typeNumber)
    for (let i = 0; i < rsBlocks.length; i++) {
      totalDataCount += rsBlocks[i].dataCount
    }
    if (buffer.length > totalDataCount * 8) {
      throw new Error('qr code length overflow')
    }
    if (buffer.length + 4 <= totalDataCount * 8) {
      buffer.put(0, 4)
    }
    while (buffer.length % 8 !== 0) {
      buffer.putBit(false)
    }
    while (buffer.length < totalDataCount * 8) {
      buffer.put(0xec, 8)
      if (buffer.length >= totalDataCount * 8) {
        break
      }
      buffer.put(0x11, 8)
    }
    this.data = createCodeWords(buffer, rsBlocks)

    // 掩码选优：8 版全铺试算，取失点最低者重铺终版
    let bestMask = 0
    let bestLost = -1
    for (let m = 0; m < 8; m++) {
      this.clearModules()
      this.build(m)
      const lost = this.lostPoint()
      if (bestLost < 0 || lost < bestLost) {
        bestLost = lost
        bestMask = m
      }
    }
    this.clearModules()
    this.build(bestMask)

    const result: boolean[][] = []
    for (let r = 0; r < this.moduleCount; r++) {
      const row: boolean[] = new Array(this.moduleCount)
      for (let c = 0; c < this.moduleCount; c++) {
        row[c] = this.modules[r][c] === true
      }
      result.push(row)
    }
    const out: QrMatrixResult = { modules: result, moduleCount: this.moduleCount }
    logger.debug(TAG, `本地二维码编码完成 v${this.typeNumber} mask=${bestMask} modules=${this.moduleCount}`)
    return out
  }
}

/**
 * 编码文本 → QR 矩阵（byte 模式 + ECC M，v1..v10 自动选版）。
 * 超容量抛错（详情页 shortUrl 远达不到）。
 */
export function encodeQrMatrix(text: string): QrMatrixResult {
  for (let v = 1; v <= 10; v++) {
    const rsBlocks = getRsBlocks(v)
    let totalDataCount = 0
    for (let i = 0; i < rsBlocks.length; i++) {
      totalDataCount += rsBlocks[i].dataCount
    }
    const needBits = 4 + lengthInBits(v) + new util.TextEncoder().encodeInto(text).length * 8
    if (needBits <= totalDataCount * 8) {
      return new QrModel(v).make(text)
    }
  }
  throw new Error('qr text too long for v10-M')
}

/** 每模块渲染像素（v3 短链 → (29+8)×12 = 444px 成图，200vp 显示下模块约 21px，扫描清晰） */
const QR_MODULE_PX: number = 12
/** 静区（4 模块，规格默认值） */
const QR_QUIET_MODULES: number = 4

/**
 * 编码并渲染为白底 PixelMap（RGBA_8888）。
 * 失败时抛错，由调用方决定是否进「生成失败」态。
 */
export async function createQrPixelMap(url: string): Promise<image.PixelMap> {
  const qr = encodeQrMatrix(url)
  const size = (qr.moduleCount + QR_QUIET_MODULES * 2) * QR_MODULE_PX
  const buffer: ArrayBuffer = new ArrayBuffer(size * size * 4)
  const pixels: Uint8Array = new Uint8Array(buffer)
  for (let i = 0; i < pixels.length; i++) {
    pixels[i] = 255 // 白底
  }
  for (let row = 0; row < qr.moduleCount; row++) {
    for (let col = 0; col < qr.moduleCount; col++) {
      if (!qr.modules[row][col]) {
        continue
      }
      const x0 = (col + QR_QUIET_MODULES) * QR_MODULE_PX
      const y0 = (row + QR_QUIET_MODULES) * QR_MODULE_PX
      for (let dy = 0; dy < QR_MODULE_PX; dy++) {
        const rowStart = ((y0 + dy) * size + x0) * 4
        for (let dx = 0; dx < QR_MODULE_PX; dx++) {
          const p = rowStart + dx * 4
          pixels[p] = 0
          pixels[p + 1] = 0
          pixels[p + 2] = 0
          pixels[p + 3] = 255
        }
      }
    }
  }
  const options: image.InitializationOptions = {
    size: { width: size, height: size },
    pixelFormat: image.PixelMapFormat.RGBA_8888,
    srcPixelFormat: image.PixelMapFormat.RGBA_8888
  }
  return image.createPixelMap(buffer, options)
}

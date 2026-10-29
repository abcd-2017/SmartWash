/**
 * 卦盘解码（读库还原）—— 把 DivChartCodec 解码出的纯对象还原为四术 chart 类实例。
 *
 * 背景：DivChartCodec.decodeChartJson 只做「Map 感知的 JSON 反序列化」，产物是纯对象
 * （无类方法、无枚举实例）。而断法层 LiuYaoFacts.of(chart, category) 与盘面方法
 * （gongDeclaration / juDeclaration）依赖 LiuYaoChart / QiMenChart 等类实例。
 * 安卓由 Gson 反射直接还原为类实例；ArkTS 无反射，故在此显式转换（VO 用 interface +
 * 转换函数的等价物：此处转换目标是内核类实例，字段逐一校验，缺失即抛错，不裸 as）。
 *
 * 红线（与 DivinationStore / DivChartCodec 文件头一致）：
 *   卦盘 / 解读 / 追问 / 复盘一律读库解码，禁止进卦盘页重算（当前时刻已变会漂移）。
 */

import { WuXing, GanZhi, SiZhu } from '../core/GanZhi'
import { YaoValue } from '../core/Yao'
import { Trigram } from '../core/DivinationCommon'
import { LiuYaoChart, LiuYaoLine, LiuYaoBianLine, LiuYaoHidden } from '../core/liuyao/LiuYaoChart'
import { MeiHuaChart, TiYongRelation } from '../core/meihua/MeiHuaChart'
import { QiMenChart } from '../core/qimen/QiMenChart'
import { LiuRenChart, LiuRenCourse } from '../core/liuren/LiuRenChart'
import { decodeChartJson } from './DivChartCodec'

/** 解码产物的键值容器（JSON 解析结果，键为字符串） */
type Raw = Record<string, Object>

// ========== 通用取值（运行时类型校验，缺失返回 null，不裸 as） ==========

function rawOf(value: Object): Raw {
  // JSON 解析产物为动态键对象，此处仅做键访问的类型桥接（同 DivChartCodec.reviveValue 口径）
  return value as Raw
}

function get(raw: Object, key: string): Object | null {
  const value = rawOf(raw)[key]
  return value === undefined ? null : value
}

function getString(raw: Object, key: string): string | null {
  const value = get(raw, key)
  return typeof value === 'string' ? value : null
}

function getNumber(raw: Object, key: string): number | null {
  const value = get(raw, key)
  return typeof value === 'number' ? value : null
}

function getBoolean(raw: Object, key: string): boolean | null {
  const value = get(raw, key)
  return typeof value === 'boolean' ? value : null
}

function getArray(raw: Object, key: string): Object[] | null {
  const value = get(raw, key)
  return Array.isArray(value) ? value : null
}

function mustString(raw: Object, key: string): string {
  const value = getString(raw, key)
  if (value === null) {
    throw new Error(`卦盘字段缺失或非字符串: ${key}`)
  }
  return value
}

function mustNumber(raw: Object, key: string): number {
  const value = getNumber(raw, key)
  if (value === null) {
    throw new Error(`卦盘字段缺失或非数值: ${key}`)
  }
  return value
}

function mustBoolean(raw: Object, key: string): boolean {
  const value = getBoolean(raw, key)
  if (value === null) {
    throw new Error(`卦盘字段缺失或非布尔: ${key}`)
  }
  return value
}

function stringArray(raw: Object, key: string): string[] {
  const arr = getArray(raw, key)
  const out: string[] = []
  if (arr !== null) {
    for (const item of arr) {
      if (typeof item === 'string') {
        out.push(item)
      }
    }
  }
  return out
}

function numberArray(raw: Object, key: string): number[] {
  const arr = getArray(raw, key)
  const out: number[] = []
  if (arr !== null) {
    for (const item of arr) {
      if (typeof item === 'number') {
        out.push(item)
      }
    }
  }
  return out
}

function mapNumStr(raw: Object, key: string): Map<number, string> {
  const child = get(raw, key)
  const out = new Map<number, string>()
  if (child instanceof Map) {
    const map = child as Map<Object, Object>
    map.forEach((v: Object, k: Object): void => {
      if (typeof v === 'string' && typeof k === 'number') {
        out.set(k, v)
      }
    })
  }
  return out
}

function mapStrStr(raw: Object, key: string): Map<string, string> {
  const child = get(raw, key)
  const out = new Map<string, string>()
  if (child instanceof Map) {
    const map = child as Map<Object, Object>
    map.forEach((v: Object, k: Object): void => {
      if (typeof v === 'string' && typeof k === 'string') {
        out.set(k, v)
      }
    })
  }
  return out
}

// ========== 内核类型还原 ==========

function wuXingOf(label: string): WuXing {
  const all: WuXing[] = [WuXing.MU, WuXing.HUO, WuXing.TU, WuXing.JIN, WuXing.SHUI]
  const found = all.find((w) => w.label === label)
  if (found === undefined) {
    throw new Error(`未知五行: ${label}`)
  }
  return found
}

function wuXingField(raw: Object, key: string): WuXing {
  const child = get(raw, key)
  if (child === null) {
    throw new Error(`卦盘字段缺失: ${key}`)
  }
  return wuXingOf(mustString(child, 'label'))
}

function ganZhiOf(label: string): GanZhi {
  return GanZhi.parse(label)
}

/**
 * 干支字段还原。
 * 注意：GanZhi.label / jiaZiIndex / xun / xunKong 均为 getter，JSON.stringify 不会序列化，
 * 库中只存 gan / zhi 两个子对象（各含 label），故由二者拼回干支文本再 parse。
 */
function ganZhiField(raw: Object, key: string): GanZhi {
  const child = get(raw, key)
  if (child === null) {
    throw new Error(`卦盘字段缺失: ${key}`)
  }
  const ganChild = get(child, 'gan')
  const zhiChild = get(child, 'zhi')
  if (ganChild === null || zhiChild === null) {
    throw new Error(`干支缺 gan/zhi: ${key}`)
  }
  return ganZhiOf(mustString(ganChild, 'label') + mustString(zhiChild, 'label'))
}

function siZhuOf(raw: Object): SiZhu {
  return new SiZhu(
    ganZhiField(raw, 'year'),
    ganZhiField(raw, 'month'),
    ganZhiField(raw, 'day'),
    ganZhiField(raw, 'hour'))
}

function trigramField(raw: Object, key: string): Trigram {
  const child = get(raw, key)
  if (child === null) {
    throw new Error(`卦盘字段缺失: ${key}`)
  }
  return Trigram.fromBinary(mustString(child, 'binary'))
}

function tiYongOf(label: string): TiYongRelation {
  const found = TiYongRelation.ALL.find((r) => r.label === label)
  if (found === undefined) {
    throw new Error(`未知体用关系: ${label}`)
  }
  return found
}

function tiYongField(raw: Object, key: string): TiYongRelation {
  const child = get(raw, key)
  if (child === null) {
    throw new Error(`卦盘字段缺失: ${key}`)
  }
  return tiYongOf(mustString(child, 'label'))
}

// ========== 六爻 ==========

function liuYaoLineOf(raw: Object): LiuYaoLine {
  const yaoRaw = get(raw, 'yao')
  if (yaoRaw === null) {
    throw new Error('六爻行缺 yao 字段')
  }
  return new LiuYaoLine(
    mustNumber(raw, 'position'),
    YaoValue.fromCode(mustNumber(yaoRaw, 'code')),
    mustString(raw, 'ganZhi'),
    wuXingField(raw, 'zhiWuXing'),
    mustString(raw, 'liuQin'),
    mustString(raw, 'liuShen'),
    getString(raw, 'shiYing'),
    getString(raw, 'bianGanZhi'),
    getString(raw, 'bianLiuQin'))
}

function liuYaoBianLineOf(raw: Object): LiuYaoBianLine {
  return new LiuYaoBianLine(
    mustNumber(raw, 'position'),
    mustString(raw, 'ganZhi'),
    mustString(raw, 'liuQin'),
    mustBoolean(raw, 'isChanged'))
}

function liuYaoHiddenOf(raw: Object): LiuYaoHidden {
  const seatsRaw = get(raw, 'seats')
  const seats = new Map<number, [string, string]>()
  if (seatsRaw instanceof Map) {
    const map = seatsRaw as Map<Object, Object>
    map.forEach((v: Object, k: Object): void => {
      if (!Array.isArray(v)) {
        return
      }
      const pair = v as Array<Object>
      const qin = pair[0]
      const gz = pair[1]
      if (typeof qin === 'string' && typeof gz === 'string' && typeof k === 'number') {
        seats.set(k, [qin, gz])
      }
    })
  }
  return new LiuYaoHidden(mustString(raw, 'gongName'), seats)
}

/** 六爻卦盘还原 */
export function readLiuYaoChart(chartJson: string): LiuYaoChart {
  const raw = decodeChartJson(chartJson)
  const rowsRaw = getArray(raw, 'rows')
  const rows: LiuYaoLine[] = []
  if (rowsRaw !== null) {
    for (const item of rowsRaw) {
      rows.push(liuYaoLineOf(item))
    }
  }
  const bianRowsRaw = getArray(raw, 'bianRows')
  let bianRows: LiuYaoBianLine[] | null = null
  if (bianRowsRaw !== null) {
    bianRows = []
    for (const item of bianRowsRaw) {
      bianRows.push(liuYaoBianLineOf(item))
    }
  }
  const hiddenRaw = get(raw, 'hidden')
  return new LiuYaoChart(
    numberArray(raw, 'lines'),
    mustString(raw, 'mark'),
    mustString(raw, 'name'),
    mustString(raw, 'gong'),
    wuXingField(raw, 'gongWuXing'),
    mustString(raw, 'type'),
    mustNumber(raw, 'shiYao'),
    mustNumber(raw, 'yingYao'),
    rows,
    numberArray(raw, 'movingPositions'),
    getString(raw, 'bianName'),
    getString(raw, 'bianMark'),
    getString(raw, 'bianGong'),
    getString(raw, 'bianType'),
    bianRows,
    hiddenRaw === null ? null : liuYaoHiddenOf(hiddenRaw),
    siZhuOf(raw),
    mustString(raw, 'xunKong'),
    mustString(raw, 'lunarText'))
}

// ========== 梅花 ==========

/** 梅花卦盘还原 */
export function readMeiHuaChart(chartJson: string): MeiHuaChart {
  const raw = decodeChartJson(chartJson)
  return new MeiHuaChart(
    numberArray(raw, 'lines'),
    mustString(raw, 'benMark'),
    mustString(raw, 'benName'),
    mustString(raw, 'huMark'),
    mustString(raw, 'huName'),
    mustString(raw, 'bianMark'),
    mustString(raw, 'bianName'),
    mustNumber(raw, 'movingYao'),
    trigramField(raw, 'upper'),
    trigramField(raw, 'lower'),
    trigramField(raw, 'ti'),
    trigramField(raw, 'yong'),
    tiYongField(raw, 'tiYongRelation'),
    wuXingField(raw, 'huWuXing'),
    wuXingField(raw, 'bianWuXing'),
    getString(raw, 'derivation'))
}

// ========== 奇门 ==========

/** 奇门遁甲卦盘还原 */
export function readQiMenChart(chartJson: string): QiMenChart {
  const raw = decodeChartJson(chartJson)
  return new QiMenChart(
    siZhuOf(raw),
    mustString(raw, 'jieQiName'),
    mustString(raw, 'yinYang'),
    mustNumber(raw, 'juNumber'),
    mustString(raw, 'yuan'),
    mustString(raw, 'xunShouYi'),
    mapNumStr(raw, 'diPan'),
    mapNumStr(raw, 'tianPan'),
    mapNumStr(raw, 'jiuXing'),
    mapNumStr(raw, 'baMen'),
    mapNumStr(raw, 'baShen'),
    mapNumStr(raw, 'anGan'),
    mustString(raw, 'zhiFuXing'),
    mustNumber(raw, 'zhiFuLuoGong'),
    mustNumber(raw, 'zhiFuYuanGong'),
    mustString(raw, 'zhiShiMen'),
    mustNumber(raw, 'zhiShiGong'),
    mustNumber(raw, 'zhiShiGongRaw'),
    stringArray(raw, 'kongWangZhi'),
    numberArray(raw, 'kongWangGong'),
    mustString(raw, 'maZhi'),
    mustNumber(raw, 'maGong'))
}

// ========== 六壬 ==========

function liuRenCourseOf(raw: Object): LiuRenCourse {
  return new LiuRenCourse(
    mustString(raw, 'label'),
    mustString(raw, 'upper'),
    mustString(raw, 'lower'),
    mustBoolean(raw, 'lowerIsGan'),
    mustBoolean(raw, 'shangKeXia'),
    mustBoolean(raw, 'xiaZeShang'))
}

/** 大六壬卦盘还原 */
export function readLiuRenChart(chartJson: string): LiuRenChart {
  const raw = decodeChartJson(chartJson)
  const coursesRaw = getArray(raw, 'courses')
  const courses: LiuRenCourse[] = []
  if (coursesRaw !== null) {
    for (const item of coursesRaw) {
      courses.push(liuRenCourseOf(item))
    }
  }
  const sanChuanRaw = getArray(raw, 'sanChuan')
  const sanChuanJiangRaw = getArray(raw, 'sanChuanJiang')
  return new LiuRenChart(
    mustString(raw, 'dayGanZhi'),
    mustString(raw, 'hourGanZhi'),
    mustString(raw, 'yueJiang'),
    mustString(raw, 'yueJiangName'),
    mapStrStr(raw, 'tianPan'),
    courses,
    sanChuanRaw === null ? null : stringArray(raw, 'sanChuan'),
    sanChuanJiangRaw === null ? null : stringArray(raw, 'sanChuanJiang'),
    getString(raw, 'keTi'),
    getString(raw, 'jiuZongMen'),
    getString(raw, 'unsupportedNote'),
    mustString(raw, 'guiRen'),
    mustBoolean(raw, 'isDay'),
    mapStrStr(raw, 'tianJiang'))
}

// ========== 摘要名（解读 / 追问 / 卦历复用） ==========

/** 术数名（兜底） */
export function divMethodName(method: string): string {
  if (method === 'liuyao') {
    return '六爻'
  }
  if (method === 'meihua') {
    return '梅花'
  }
  if (method === 'qimen') {
    return '奇门'
  }
  if (method === 'liuren') {
    return '六壬'
  }
  return method
}

/**
 * 盘面卦名（解读页 / 追问页摘要用）：六爻「本卦 之 变卦」、梅花本卦名、奇门/六壬固定名。
 * 解码失败时回退术数名（与安卓 methodLabel 兜底一致）。
 */
export function chartNameOf(method: string, chartJson: string): string {
  try {
    if (method === 'liuyao') {
      const chart = readLiuYaoChart(chartJson)
      return chart.bianName !== null ? `${chart.name} 之 ${chart.bianName}` : chart.name
    }
    if (method === 'meihua') {
      return readMeiHuaChart(chartJson).benName
    }
    if (method === 'qimen') {
      return '奇门遁甲'
    }
    if (method === 'liuren') {
      return '大六壬'
    }
  } catch (err) {
    // 解码失败回退术数名
  }
  return divMethodName(method)
}

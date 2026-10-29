/**
 * 观象台本地存储与仓储层 —— 卦象记录落库、读取与今日一签去重。
 *
 * 蓝本：安卓 feature/divination/.../data/DivRecordRepository.kt + database/DivRecordEntity.kt。
 * 实现：HarmonyOS 无 Room，改用 @ohos.data.relationalStore（RDB）；查询结果一次性取回，
 * 列表刷新沿用项目现有 onShown 重拉模式（与订单/充值记录页一致）。
 *
 * ★ 铁律（必须在任何改动中保持）：
 *   排盘结果只在「起卦瞬间」计算一次并落库；卦盘 / 解读 / 追问 / 复盘一律读库（chartJson）。
 *   禁止进入卦盘页重新排盘——当前时刻已变，重算会导致结果漂移（起卦时刻 castAt 已固化在库中，
 *   复盘必须沿用原时刻才有意义）。
 */

import { relationalStore } from '@kit.ArkData'
import { common } from '@kit.AbilityKit'

const DIV_DB_NAME = 'divination_db'
const DIV_TABLE = 'div_records'

const CREATE_TABLE_SQL =
  'CREATE TABLE IF NOT EXISTS ' + DIV_TABLE + ' (' +
  'id INTEGER PRIMARY KEY AUTOINCREMENT, ' +
  'method TEXT NOT NULL, ' +
  'category TEXT NOT NULL, ' +
  'question TEXT NOT NULL, ' +
  'lines TEXT NOT NULL DEFAULT \'[]\', ' +
  'castAt INTEGER NOT NULL, ' +
  'chartJson TEXT NOT NULL, ' +
  'status INTEGER NOT NULL DEFAULT 0, ' +
  'createdAt INTEGER NOT NULL' +
  ')'

/** 卦象记录行（VO 用 interface + 转换函数，不用 class 直接接 JSON） */
export interface DivRecordRow {
  id: number
  method: string
  category: string
  question: string
  lines: string
  castAt: number
  chartJson: string
  status: number
  createdAt: number
}

/**
 * 四术共用的卦盘信封：method 决定 chartJson 该按哪套术数解码。
 * 安卓用 Gson 反射序列化，ArkTS 手写编解码。
 */
export interface DivChartBundle {
  method: string
  liuyao?: string
  meihua?: string
  qimen?: string
  liuren?: string
}

export function encodeBundle(method: string, chartJson: string): DivChartBundle {
  const bundle: DivChartBundle = { method: method }
  if (method === 'liuyao') {
    bundle.liuyao = chartJson
  } else if (method === 'meihua') {
    bundle.meihua = chartJson
  } else if (method === 'qimen') {
    bundle.qimen = chartJson
  } else if (method === 'liuren') {
    bundle.liuren = chartJson
  }
  return bundle
}

export function decodeBundle(bundle: DivChartBundle): string {
  if (bundle.liuyao !== undefined) {
    return bundle.liuyao
  }
  if (bundle.meihua !== undefined) {
    return bundle.meihua
  }
  if (bundle.qimen !== undefined) {
    return bundle.qimen
  }
  if (bundle.liuren !== undefined) {
    return bundle.liuren
  }
  return ''
}

export function encodeBundleToJson(bundle: DivChartBundle): string {
  return JSON.stringify(bundle)
}

export function decodeBundleFromJson(json: string): DivChartBundle {
  const raw = JSON.parse(json) as Record<string, string>
  const bundle: DivChartBundle = { method: raw['method'] ?? '' }
  const liuyao = raw['liuyao']
  const meihua = raw['meihua']
  const qimen = raw['qimen']
  const liuren = raw['liuren']
  if (liuyao !== undefined) {
    bundle.liuyao = liuyao
  }
  if (meihua !== undefined) {
    bundle.meihua = meihua
  }
  if (qimen !== undefined) {
    bundle.qimen = qimen
  }
  if (liuren !== undefined) {
    bundle.liuren = liuren
  }
  return bundle
}

let rdbStore: relationalStore.RdbStore | null = null

/** 建库 + 建表（幂等）；已打开则直接返回 */
export async function initStore(context: Context): Promise<relationalStore.RdbStore> {
  if (rdbStore !== null) {
    return rdbStore
  }
  const store = await relationalStore.getRdbStore(context, {
    name: DIV_DB_NAME,
    securityLevel: relationalStore.SecurityLevel.S1
  })
  await store.executeSql(CREATE_TABLE_SQL)
  rdbStore = store
  return store
}

export function closeStore(): void {
  if (rdbStore !== null) {
    rdbStore.close()
    rdbStore = null
  }
}

function rowFromResultSet(resultSet: relationalStore.ResultSet): DivRecordRow {
  return {
    id: resultSet.getLong(resultSet.getColumnIndex('id')),
    method: resultSet.getString(resultSet.getColumnIndex('method')),
    category: resultSet.getString(resultSet.getColumnIndex('category')),
    question: resultSet.getString(resultSet.getColumnIndex('question')),
    lines: resultSet.getString(resultSet.getColumnIndex('lines')),
    castAt: resultSet.getLong(resultSet.getColumnIndex('castAt')),
    chartJson: resultSet.getString(resultSet.getColumnIndex('chartJson')),
    status: resultSet.getLong(resultSet.getColumnIndex('status')),
    createdAt: resultSet.getLong(resultSet.getColumnIndex('createdAt'))
  }
}

export class DivRecordRepository {
  /**
   * 保存排盘记录。仅在起卦瞬间调用一次（见文件头铁律）。
   * @returns 新记录 id；失败返回 -1
   */
  static async save(method: string, category: string, question: string,
    lines: string, castAt: number, chartJson: string): Promise<number> {
    const context = getContext() as common.UIAbilityContext
    const store = await initStore(context)
    const now = Date.now()
    const row: relationalStore.ValuesBucket = {
      'method': method,
      'category': category,
      'question': question,
      'lines': lines,
      'castAt': castAt,
      'chartJson': chartJson,
      'status': 0,
      'createdAt': now
    }
    const id = await store.insert(DIV_TABLE, row)
    return id >= 0 ? id : -1
  }

  static async getById(id: number): Promise<DivRecordRow | null> {
    const context = getContext() as common.UIAbilityContext
    const store = await initStore(context)
    const resultSet = await store.querySql(
      'SELECT * FROM ' + DIV_TABLE + ' WHERE id = ?', [id.toString()])
    if (resultSet.rowCount <= 0 || !resultSet.goToFirstRow()) {
      resultSet.close()
      return null
    }
    const row = rowFromResultSet(resultSet)
    resultSet.close()
    return row
  }

  /** 按 id 倒序取最近 limit 条（观象台首页「案卷」与卦历用） */
  static async recent(limit: number): Promise<Array<DivRecordRow>> {
    const context = getContext() as common.UIAbilityContext
    const store = await initStore(context)
    const resultSet = await store.querySql(
      'SELECT * FROM ' + DIV_TABLE + ' ORDER BY id DESC LIMIT ?', [limit.toString()])
    const rows: Array<DivRecordRow> = []
    while (resultSet.goToNextRow()) {
      rows.push(rowFromResultSet(resultSet))
    }
    resultSet.close()
    return rows
  }

  static async observeAll(): Promise<Array<DivRecordRow>> {
    const context = getContext() as common.UIAbilityContext
    const store = await initStore(context)
    const resultSet = await store.querySql('SELECT * FROM ' + DIV_TABLE + ' ORDER BY id DESC', [])
    const rows: Array<DivRecordRow> = []
    while (resultSet.goToNextRow()) {
      rows.push(rowFromResultSet(resultSet))
    }
    resultSet.close()
    return rows
  }

  /**
   * 今日一签去重：同一 method + 同一问题 + 当天已存在记录则复用，不重复落库。
   * 当天口径按 castAt 的本地自然日（00:00 ~ 次日 00:00）。
   */
  static async findTodaySign(method: string, question: string): Promise<DivRecordRow | null> {
    const context = getContext() as common.UIAbilityContext
    const store = await initStore(context)
    const start = startOfTodayMillis()
    const end = start + 24 * 60 * 60 * 1000
    const resultSet = await store.querySql(
      'SELECT * FROM ' + DIV_TABLE +
      ' WHERE method = ? AND question = ? AND castAt >= ? AND castAt < ?' +
      ' ORDER BY id DESC LIMIT 1',
      [method, question, start.toString(), end.toString()])
    if (resultSet.rowCount <= 0 || !resultSet.goToFirstRow()) {
      resultSet.close()
      return null
    }
    const row = rowFromResultSet(resultSet)
    resultSet.close()
    return row
  }
}

function startOfTodayMillis(): number {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0, 0)
  return start.getTime()
}

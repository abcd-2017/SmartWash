// src/views/platform/game/levelImportParser.js
// 关卡批量导入解析（与 gameDirectory.js 同思路：解析归一为纯函数，GameList 弹窗与单测共用）。
//
// 双兼容文件格式（顶层数组，两种元素可在同一文件混用）：
// - 裸格式：元素整体就是关卡数据（string / array / object 均可）——游戏包内 DEFAULT_LEVELS 拷出即用
// - 信封格式：元素为对象且含 data 键 → 按 {data, levelNo?, name?, difficulty?, enabled?, sort?} 读
//
// 元数据缺省规则与后端对齐：levelNo 缺省由 planLevelImport 从现有关卡顺延分配，
// sort 缺省 = levelNo×10，enabled 缺省 = 1 下发；dataJson 仅保证 JSON 合法，结构语义由游戏自定。

/** 单次导入关卡数上限（与后端 GameLevelBatchFrom @Size(max=500) 对齐） */
export const MAX_IMPORT_COUNT = 500;

/** 导入文件大小上限（字节，与 LevelImportDialog 预检一致） */
export const MAX_IMPORT_FILE_SIZE = 2 * 1024 * 1024;

/** 关卡名长度上限（与后端 LevelItem @Size(max=32)、编辑表单 maxlength 对齐） */
const NAME_MAX_LENGTH = 32;

const isPlainObject = (v) => typeof v === 'object' && v !== null && !Array.isArray(v);

const isPositiveInt = (v) => Number.isInteger(v) && v >= 1;

/** 校验单个信封字段，非法返回错误文案（合法/缺省返回 null） */
function validateEnvelopeField(field, value) {
  switch (field) {
    case 'levelNo':
      return isPositiveInt(value) ? null : 'levelNo 必须是正整数';
    case 'difficulty':
      return value === undefined || value === null || Number.isInteger(value)
        ? null
        : 'difficulty 必须是整数';
    case 'enabled':
      // 后端语义：缺省/1=下发，0=下架；其他值静默归一成 0 会造成「想下发却下架」，故预检拦截
      return value === undefined || value === null || value === 0 || value === 1
        ? null
        : 'enabled 仅允许 0 或 1';
    case 'sort':
      return value === undefined || value === null || Number.isInteger(value)
        ? null
        : 'sort 必须是整数';
    case 'name':
      return value === undefined || value === null || (typeof value === 'string' && value.length <= NAME_MAX_LENGTH)
        ? null
        : `name 最长 ${NAME_MAX_LENGTH} 字`;
    default:
      return null;
  }
}

const ENVELOPE_FIELDS = ['levelNo', 'name', 'difficulty', 'enabled', 'sort'];

/**
 * 解析批量导入文本为条目列表（不含序号分配，序号决策见 planLevelImport）。
 * @param {string} text 文件内容或粘贴文本
 * @returns {{ok: boolean, reason: string, items: Array<{sourceIndex: number, levelNo: number|null,
 *   name: string|null, difficulty: number|null, enabled: number|null, sort: number|null,
 *   dataJson: string, error: string|null}>}}
 *   ok=false 时 reason 为整体失败原因；ok=true 时逐项解析，error 非空的行禁止提交
 */
export function parseLevelImportText(text) {
  let root;
  try {
    root = JSON.parse(text);
  } catch (e) {
    return { ok: false, reason: `JSON 解析失败：${e.message}`, items: [] };
  }
  if (!Array.isArray(root)) {
    return { ok: false, reason: '顶层必须是 JSON 数组（每项一关：裸数据或 {data, ...} 信封）', items: [] };
  }
  if (root.length === 0) {
    return { ok: false, reason: '文件不含任何关卡', items: [] };
  }
  if (root.length > MAX_IMPORT_COUNT) {
    return { ok: false, reason: `单次最多导入 ${MAX_IMPORT_COUNT} 关，本次 ${root.length} 关`, items: [] };
  }

  const items = root.map((el, index) => {
    const seq = index + 1;
    const base = {
      sourceIndex: index,
      levelNo: null,
      name: null,
      difficulty: null,
      enabled: null,
      sort: null,
      dataJson: '',
      error: null,
    };
    let data;
    if (isPlainObject(el) && 'data' in el) {
      // 信封格式：data 必须携带可序列化的关卡数据
      if (el.data === undefined || el.data === null) {
        return { ...base, error: `第 ${seq} 项：信封缺少 data 关卡数据` };
      }
      for (const field of ENVELOPE_FIELDS) {
        if (el[field] !== undefined && el[field] !== null) {
          const err = validateEnvelopeField(field, el[field]);
          if (err) return { ...base, error: `第 ${seq} 项：${err}` };
        }
      }
      data = el.data;
      base.levelNo = isPositiveInt(el.levelNo) ? el.levelNo : null;
      base.name = typeof el.name === 'string' ? el.name : null;
      base.difficulty = Number.isInteger(el.difficulty) ? el.difficulty : null;
      base.enabled = el.enabled === 0 || el.enabled === 1 ? el.enabled : null;
      base.sort = Number.isInteger(el.sort) ? el.sort : null;
    } else {
      // 裸格式：元素整体即关卡数据
      data = el;
    }
    let dataJson;
    try {
      dataJson = JSON.stringify(data);
    } catch {
      return { ...base, error: `第 ${seq} 项：关卡数据无法序列化为 JSON（含循环引用等）` };
    }
    return { ...base, dataJson };
  });
  return { ok: true, reason: '', items };
}

/**
 * 序号分配与现状合并：给缺省序号的条目从 existingMaxNo 之后顺延（跳过信封显式占用），
 * 并按现有关卡表标记每行处置（新增 / 覆盖）。
 * @param {Array} items parseLevelImportText 的输出（error 行原样保留不参与分配）
 * @param {Array} levelList 现有关卡列表（GameLevel 行，含 levelNo）
 * @returns {{rows: Array, created: number, updated: number, blocked: boolean,
 *   duplicateNos: number[]}} blocked=true 表示存在错误/重复行，禁止提交
 */
export function planLevelImport(items, levelList) {
  const existingNos = new Set((levelList || []).map((it) => it.levelNo));
  const maxExistingNo = (levelList || []).reduce((m, it) => Math.max(m, it.levelNo || 0), 0);

  // 显式序号批内重复检测
  const seen = new Map();
  const duplicateNos = [];
  for (const item of items) {
    if (item.error || item.levelNo == null) continue;
    if (seen.has(item.levelNo)) {
      duplicateNos.push(item.levelNo);
    } else {
      seen.set(item.levelNo, item);
    }
  }

  let next = maxExistingNo + 1;
  const rows = items.map((item) => {
    if (item.error) return { ...item, fate: 'error' };
    if (duplicateNos.includes(item.levelNo)) {
      return { ...item, fate: 'error', error: `序号 ${item.levelNo} 在本批内重复` };
    }
    if (item.levelNo == null) {
      while (seen.has(next)) next += 1;
      item = { ...item, levelNo: next };
      seen.set(next, item);
    }
    return { ...item, fate: existingNos.has(item.levelNo) ? 'update' : 'create' };
  });

  const created = rows.filter((r) => r.fate === 'create').length;
  const updated = rows.filter((r) => r.fate === 'update').length;
  return { rows, created, updated, blocked: rows.some((r) => r.fate === 'error'), duplicateNos };
}

/** 提交前构建请求体条目（仅合法行；enabled 缺省交后端按 1 处理，与单关保存一致） */
export function buildImportPayload(rows, { gameKey, replaceAll }) {
  return {
    gameKey,
    replaceAll,
    levels: rows.map((r) => ({
      levelNo: r.levelNo,
      name: r.name,
      dataJson: r.dataJson,
      difficulty: r.difficulty,
      enabled: r.enabled,
      sort: r.sort,
    })),
  };
}

/**
 * 按单批上限切分请求体：序号已在 planLevelImport 分配完毕，这里只做切片。
 * 游戏上传串联关卡导入时复用（BatchUploadDialog / GameList）；replaceAll 仅随首批下发
 *（清空动作只需执行一次），后续批次按 upsert 追加，后端每批独立事务。
 * @param {Array} rows planLevelImport 输出中 fate !== 'error' 的行（调用方负责过滤错误行）
 * @param {{gameKey: string, replaceAll: boolean}} options
 * @param {number} [batchSize] 单批上限，默认 MAX_IMPORT_COUNT（与后端 @Size(max=500) 对齐）
 * @returns {Array<{gameKey: string, replaceAll: boolean, levels: Array}>} 至少一批（rows 为空时返回空数组）
 */
export function buildChunkedPayloads(rows, { gameKey, replaceAll }, batchSize = MAX_IMPORT_COUNT) {
  const payloads = [];
  for (let start = 0; start < rows.length; start += batchSize) {
    payloads.push(
      buildImportPayload(rows.slice(start, start + batchSize), {
        gameKey,
        replaceAll: replaceAll && start === 0,
      })
    );
  }
  return payloads;
}

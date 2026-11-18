// src/views/dining/diningDirectory.js
// 吃啥「目录式菜谱上传」纯逻辑模块（与 game/gameDirectory.js 同思路：解析/校验/判重归一为
// 纯函数，DiningDirectoryImportDialog 与单测共用，避免第 12 份复制粘贴）。
//
// 目录规范：选父目录（webkitdirectory），根层仅认 *.csv 数据文件（可多个，列与导入模板一致）
// + image/ 封面目录；.DS_Store 等隐藏文件静默忽略；其余文件/子目录给出忽略提示。
// CSV 行通过「封面对象键」列的文件名（如 dining/covers/番茄炒蛋.png → 番茄炒蛋.png）在
// image/ 中找到本地封面文件，上传拿到新对象键后随 add/update 落库。
//
// 判重语义与后端 DiningRecipeImportServiceImpl.resolveExistingRecipe 对齐（含 147bcb62 三级兜底）：
// sourceKey 优先 →（菜名,分类）兜底 → 同名且库内唯一（已分类）宽松兜底（覆盖分类改名场景）；
// 兜底命中排除「双方都挂了不同 key」的情形（不同上游来源的菜按新增不覆盖）。逐条上传走 /add
// /update，因此分类名需在前端解析为分类 id（不存在的分类先经 addDiningCategory 补建——
// 该接口不回传 id，需重拉列表映射）。
import Papa from 'papaparse';

/** 目录规范提示文案（整体报错信息尾部统一拼接） */
export const DINING_DIR_RULES =
  '目录规范：根层放 *.csv 数据文件（可多个，列与导入模板一致）+ image/ 封面目录，其他文件自动忽略';

/** 封面图片扩展名（与后端 RecipeImageValidator / 表单 accept 对齐） */
export const COVER_IMAGE_EXTS = ['.jpg', '.jpeg', '.png', '.webp'];

/** 单张封面上限（与后端封面上传接口 5MB 一致） */
export const MAX_COVER_BYTES = 5 * 1024 * 1024;

/**
 * 列表全量分页拉取的单页 size。后端 MybatisConfig.MAX_PAGE_SIZE=200 会把更大的 size
 * 钳到 200——若按 500 请求、实际只回 200 条，按 (page-1)*500<total 推进页码会整段漏数据，
 * 故直接按 200 请求推进。
 */
export const RECIPE_PAGE_SIZE = 200;

/** 模板列名（表头按列名匹配、顺序无关，与后端 DiningRecipeImportServiceImpl 对齐） */
export const CSV_COL = {
  CATEGORY: '分类名',
  NAME: '菜名',
  INGREDIENTS: '食材名列表',
  INGREDIENT_DETAILS: '食材明细列表',
  STEPS: '步骤',
  COVER_KEY: '封面对象键',
  COVER_URL: '封面链接',
  CALORIES_PER_100G: '热量每100g',
  CALORIES_TOTAL: '整份热量',
  PROTEIN: '蛋白质g',
  FAT: '脂肪g',
  CARBS: '碳水g',
  SODIUM: '钠mg',
  SOURCE_KEY: '唯一标识',
};

/** 必选表头（后端 resolveHeader 同款 5 列：既有模板必选列，缺任一列整文件拒绝） */
export const REQUIRED_CSV_COLUMNS = [
  CSV_COL.CATEGORY,
  CSV_COL.NAME,
  CSV_COL.INGREDIENTS,
  CSV_COL.STEPS,
  CSV_COL.COVER_URL,
];

/** 字段长度/数量上限（与 AddDiningRecipeFrom 及导入器对齐，预检拦截省一次必然失败的请求） */
export const FIELD_LIMITS = {
  NAME_MAX: 64,
  CATEGORY_MAX: 32,
  INGREDIENT_MAX: 32,
  INGREDIENT_COUNT_MAX: 100,
  STEPS_MAX: 20000,
  SOURCE_KEY_MAX: 64,
};

/** 营养列上限（对齐后端 NUTRITION_MAX_* / DB 列精度 DECIMAL(8,2)/(10,2)/(6,2)） */
export const NUTRITION_MAX = {
  PER_100G: 999999.99,
  TOTAL: 99999999.99,
  NUTRIENT: 9999.99,
};

/** 营养列定义：CSV 列名 → payload 字段 → 上限 */
const NUTRITION_DEFS = [
  [CSV_COL.CALORIES_PER_100G, 'caloriesPer100g', NUTRITION_MAX.PER_100G],
  [CSV_COL.CALORIES_TOTAL, 'caloriesTotal', NUTRITION_MAX.TOTAL],
  [CSV_COL.PROTEIN, 'proteinG', NUTRITION_MAX.NUTRIENT],
  [CSV_COL.FAT, 'fatG', NUTRITION_MAX.NUTRIENT],
  [CSV_COL.CARBS, 'carbsG', NUTRITION_MAX.NUTRIENT],
  [CSV_COL.SODIUM, 'sodiumMg', NUTRITION_MAX.PER_100G],
];

/** 文件相对路径段（webkitdirectory 选择时浏览器提供 webkitRelativePath） */
export const relSegs = (file) => (file.webkitRelativePath || file.name).split('/');

const isCoverImage = (name) => COVER_IMAGE_EXTS.some((ext) => name.toLowerCase().endsWith(ext));

/**
 * 父目录文件分组：根层 *.csv + image/ 目录索引 + 忽略清单。
 * 隐藏文件/目录（.DS_Store、.git 等）任意层级静默忽略；image/ 只索引图片扩展名的文件
 *（其他扩展名不进索引，引用它的行在行级校验以「未找到」报出）。
 * @param {File[]} files 选择父目录得到的全部文件
 * @returns {{csvFiles: File[], imageIndex: Map<string, File>, imageDirPresent: boolean,
 *   ignoredFiles: string[]}} imageIndex 键为文件名小写（同名取先见者）
 */
export function splitDiningDir(files) {
  const csvFiles = [];
  const imageIndex = new Map();
  const ignoredFiles = [];
  let imageDirPresent = false;
  for (const file of files) {
    const segs = relSegs(file);
    if (segs.some((seg) => seg.startsWith('.'))) continue; // 隐藏文件/目录静默忽略
    const name = segs[segs.length - 1];
    if (segs.length <= 2) {
      // 根层（父目录/文件）：仅认 *.csv
      if (name.toLowerCase().endsWith('.csv')) {
        csvFiles.push(file);
      } else {
        ignoredFiles.push(name);
      }
      continue;
    }
    if (segs[1].toLowerCase() === 'image') {
      // 封面目录（目录名大小写不敏感），内部子层级一并索引
      imageDirPresent = true;
      if (isCoverImage(name) && !imageIndex.has(name.toLowerCase())) {
        imageIndex.set(name.toLowerCase(), file);
      }
      continue;
    }
    // 其余子目录下的文件 → 忽略清单（带相对路径便于定位）
    ignoredFiles.push(segs.slice(1).join('/'));
  }
  return { csvFiles, imageIndex, imageDirPresent, ignoredFiles };
}

/**
 * 目录整体校验：无 csv 或缺 image/ 目录直接整体报错（对齐任务规范，不留静默半导入）。
 * @param {{csvFiles: File[], imageDirPresent: boolean}} split splitDiningDir 的输出
 */
export function inspectDiningDir(split) {
  if (!split.csvFiles.length) {
    return { ok: false, reason: `未找到 .csv 数据文件。${DINING_DIR_RULES}` };
  }
  if (!split.imageDirPresent) {
    return { ok: false, reason: `未找到 image/ 封面目录。${DINING_DIR_RULES}` };
  }
  return { ok: true, reason: '' };
}

/**
 * CSV 文本解码：UTF-8 优先；出现替换符（U+FFFD，典型为 Excel 在中文 Windows 另存的 GBK）
 * 时尝试 GBK 再解码，取替换符更少的结果（后端导入器同样双编码探测，语义对齐）。
 * @param {File} file
 * @returns {Promise<string>}
 */
export async function decodeCsvText(file) {
  const buf = await file.arrayBuffer();
  const utf8Text = new TextDecoder('utf-8').decode(buf);
  if (!utf8Text.includes('\uFFFD')) return utf8Text;
  const utf8Bad = utf8Text.split('\uFFFD').length - 1;
  try {
    const gbkText = new TextDecoder('gbk').decode(buf);
    const gbkBad = gbkText.split('\uFFFD').length - 1;
    if (gbkBad < utf8Bad) return gbkText;
  } catch {
    // 当前环境不支持 GBK 解码（罕见），退回 UTF-8 结果并让乱码可见
  }
  return utf8Text;
}

/**
 * CSV 解析（papaparse，完整 RFC4180：引号转义、字段内逗号/换行、CRLF）。
 * 表头按列名匹配（顺序无关）；缺必选列 / 引号损坏 / 空文件整体报错。
 * @param {string} text 解码后的 CSV 文本
 * @returns {{ok: boolean, reason: string, rows: Array<Object>}} rows 为表头键值对象数组
 *   （含跳过的空行不计；字段缺失处 papaparse 补 undefined，消费方统一按空串处理）
 */
export function parseRecipeCsv(text) {
  const parsed = Papa.parse(String(text).replace(/^\uFEFF/, ''), {
    header: true,
    skipEmptyLines: 'greedy',
    transformHeader: (h) => String(h).trim(),
  });
  // FieldMismatch（行内字段数与表头不一致）由 papaparse 补齐容忍，与 Excel 观感一致；
  // 其余（如引号未闭合）为结构性损坏，整体拒绝
  const fatal = parsed.errors.filter((e) => e.type && e.type !== 'FieldMismatch');
  if (fatal.length) {
    return {
      ok: false,
      reason: `CSV 解析失败：${fatal[0].message}${fatal.length > 1 ? `（共 ${fatal.length} 处）` : ''}`,
      rows: [],
    };
  }
  const columns = parsed.meta.fields || [];
  const missing = REQUIRED_CSV_COLUMNS.filter((col) => !columns.includes(col));
  if (missing.length) {
    return {
      ok: false,
      reason: `缺少必选列：${missing.join('、')}（请对照导入模板补齐表头）`,
      rows: [],
    };
  }
  if (!parsed.data.length) {
    return { ok: false, reason: '文件不含任何数据行', rows: [] };
  }
  return { ok: true, reason: '', rows: parsed.data };
}

/** (菜名,分类) 兜底判重的映射键（\u0000 分隔防拼接歧义；分类空 = 未分类） */
export const nameCategoryKey = (name, categoryName) => `${name}\u0000${categoryName || ''}`;

/** 营养单元格解析：空 → null（不修改/未录入）；非数字/越界返回错误文案 */
export function parseNutritionValue(raw, max) {
  const text = (raw == null ? '' : String(raw)).trim();
  if (!text) return { value: null, error: null };
  const num = Number(text);
  if (!Number.isFinite(num)) return { value: null, error: `「${text}」不是数字` };
  if (num < 0) return { value: null, error: `不能为负数（${text}）` };
  if (num > max) return { value: null, error: `超过上限 ${max}（${text}）` };
  return { value: num, error: null };
}

/** 食材名列表解析：顿号/中英文逗号/中英文分号分隔（与后端 INGREDIENT_DELIMITER 同款），去重保序 */
export function parseIngredients(raw) {
  const list = [];
  for (const part of String(raw == null ? '' : raw).split(/[、，,;；]/)) {
    const name = part.trim();
    if (name && !list.includes(name)) list.push(name);
  }
  return list;
}

/** 封面对象键 → 本地封面文件名（取 basename：dining/covers/x.png、image/x.png、x.png 均取 x.png） */
export const coverFileNameOf = (raw) => {
  const text = (raw == null ? '' : String(raw)).trim();
  return text ? text.split(/[\\/]/).pop() : null;
};

/**
 * 单行 CSV → 上传条目归一 + 行级预检（纯前端，不调后端）。
 * @param {Object} row papaparse 表头键值行
 * @param {{rowNo: number, csvName: string, imageIndex: Map<string, File>}} ctx
 * @returns {Object} item（errors 非空即为问题行，调用方据此置 skipReason 跳过上传）
 */
export function buildRecipeRow(row, { rowNo, csvName, imageIndex }) {
  const str = (v) => (v == null ? '' : String(v)).trim();
  const errors = [];
  const item = {
    csvName,
    rowNo,
    categoryName: str(row[CSV_COL.CATEGORY]) || null,
    name: str(row[CSV_COL.NAME]) || null,
    sourceKey: str(row[CSV_COL.SOURCE_KEY]) || null,
    ingredients: [],
    steps: str(row[CSV_COL.STEPS]),
    coverFileName: null,
    coverFile: null,
    caloriesPer100g: null,
    caloriesTotal: null,
    proteinG: null,
    fatG: null,
    carbsG: null,
    sodiumMg: null,
    errors,
    skipReason: '',
    willOverwrite: false,
    existingId: null,
    matchVia: null,
    result: null,
  };

  if (!item.name) errors.push('菜名为空');
  else if (item.name.length > FIELD_LIMITS.NAME_MAX) {
    errors.push(`菜名超过 ${FIELD_LIMITS.NAME_MAX} 字`);
  }
  if (item.categoryName && item.categoryName.length > FIELD_LIMITS.CATEGORY_MAX) {
    errors.push(`分类名超过 ${FIELD_LIMITS.CATEGORY_MAX} 字`);
  }
  if (item.sourceKey && item.sourceKey.length > FIELD_LIMITS.SOURCE_KEY_MAX) {
    errors.push(`唯一标识超过 ${FIELD_LIMITS.SOURCE_KEY_MAX} 字`);
  }
  if (item.steps.length > FIELD_LIMITS.STEPS_MAX) {
    errors.push(`步骤超过 ${FIELD_LIMITS.STEPS_MAX} 字`);
  }

  item.ingredients = parseIngredients(row[CSV_COL.INGREDIENTS]);
  if (item.ingredients.length > FIELD_LIMITS.INGREDIENT_COUNT_MAX) {
    errors.push(`食材超过 ${FIELD_LIMITS.INGREDIENT_COUNT_MAX} 项`);
  }
  const overLong = item.ingredients.find((n) => n.length > FIELD_LIMITS.INGREDIENT_MAX);
  if (overLong) errors.push(`食材名「${overLong}」超过 ${FIELD_LIMITS.INGREDIENT_MAX} 字`);
  // 食材明细列表（CSV_COL.INGREDIENT_DETAILS）暂不消费：add/update 接口仅承载食材名，
  // 数量明细只存在于批量导入两阶段链路（已知限制，见交付说明）

  for (const [col, field, max] of NUTRITION_DEFS) {
    const { value, error } = parseNutritionValue(row[col], max);
    if (error) {
      errors.push(`${col} ${error}`);
    } else {
      item[field] = value;
    }
  }

  item.coverFileName = coverFileNameOf(row[CSV_COL.COVER_KEY]);
  if (item.coverFileName) {
    const lower = item.coverFileName.toLowerCase();
    if (!isCoverImage(item.coverFileName)) {
      errors.push(`封面「${item.coverFileName}」不是 jpg/jpeg/png/webp 图片`);
    } else {
      item.coverFile = imageIndex.get(lower) || null;
      if (!item.coverFile) {
        errors.push(`封面「${item.coverFileName}」在 image/ 目录中未找到`);
      } else if (item.coverFile.size > MAX_COVER_BYTES) {
        errors.push(`封面「${item.coverFileName}」超过 5MB 上限`);
      }
    }
  }
  return item;
}

/**
 * 批内判重（对齐后端 markBatchDuplicateSourceKeys + 任务要求的（菜名,分类）组合去重）：
 * 同 sourceKey 重复、或无标识行（菜名,分类)重复，全部重复行标错——
 * 逐条上传为并发写库，批内重复会互相覆盖/撞 uk_source_key，必须预检拦截。
 * @param {Object[]} items buildRecipeRow 输出（错误追加进各 item.errors）
 */
export function markBatchDuplicates(items) {
  const skCounts = new Map();
  const ncCounts = new Map();
  for (const it of items) {
    if (it.sourceKey) {
      skCounts.set(it.sourceKey, (skCounts.get(it.sourceKey) || 0) + 1);
    } else if (it.name) {
      const key = nameCategoryKey(it.name, it.categoryName);
      ncCounts.set(key, (ncCounts.get(key) || 0) + 1);
    }
  }
  for (const it of items) {
    if (it.sourceKey && skCounts.get(it.sourceKey) > 1) {
      it.errors.push(`唯一标识「${it.sourceKey}」在本批内重复`);
    } else if (
      !it.sourceKey &&
      it.name &&
      ncCounts.get(nameCategoryKey(it.name, it.categoryName)) > 1
    ) {
      it.errors.push(`「${it.name}」（${it.categoryName || '未分类'}）在本批内重复`);
    }
  }
  return items;
}

/**
 * 库内既有菜谱 → 三级判重映射（sourceKey → id；（菜名,分类）→ [{id, sourceKey}]；
 * 菜名 → [{id, sourceKey}] 仅收录已分类菜，供「同名唯一」宽松兜底）。
 * 同键多条时保留先见者（sourceKey 有唯一索引本不会重复；（菜名,分类) 重复为合法业务态）。
 * @param {Array<{id: number, name: string, categoryName: string|null, sourceKey: string|null}>} records
 */
export function buildRecipeMappings(records) {
  const bySourceKey = new Map();
  const byNameCategory = new Map();
  const byName = new Map();
  for (const r of records || []) {
    if (r.sourceKey && !bySourceKey.has(r.sourceKey)) bySourceKey.set(r.sourceKey, r.id);
    const key = nameCategoryKey(r.name, r.categoryName);
    if (!byNameCategory.has(key)) byNameCategory.set(key, []);
    byNameCategory.get(key).push({ id: r.id, sourceKey: r.sourceKey || null });
    // 宽松兜底只认「已分类」菜（对齐后端 tier3 过滤 candidate.categoryId != null：
    // 手工建/历史遗留的未分类同名菜不被 CSV 行静默收编）
    if (r.categoryName != null) {
      if (!byName.has(r.name)) byName.set(r.name, []);
      byName.get(r.name).push({ id: r.id, sourceKey: r.sourceKey || null });
    }
  }
  return { bySourceKey, byNameCategory, byName };
}

/**
 * 行判重命中（与后端 resolveExistingRecipe 同语义，含 147bcb62 三级兜底）：
 * ① sourceKey 直查；②（菜名,分类）兜底；③ 前两级未命中 → 仅菜名且库内唯一（已分类）宽松兜底，
 * 覆盖分类改名场景（CSV 换了分类名，两级键必然不命中）；多道同名存在歧义，保守不命中（保持新增）。
 * ②③ 均排除「双方都挂了不同 key」（不同上游按新增不覆盖）；行无 key 命中已回填 key 的菜谱
 * → 仍算命中（更新时 sourceKey 不传，后端保留既有 key）。
 */
export function matchExistingRecipe(item, mappings) {
  if (item.sourceKey && mappings.bySourceKey.has(item.sourceKey)) {
    return { existingId: mappings.bySourceKey.get(item.sourceKey), matchVia: 'sourceKey' };
  }
  if (!item.name) return { existingId: null, matchVia: null };
  const notConflicting = (c) => !(c.sourceKey && item.sourceKey && c.sourceKey !== item.sourceKey);
  const sameCategory =
    mappings.byNameCategory.get(nameCategoryKey(item.name, item.categoryName)) || [];
  const hit = sameCategory.find(notConflicting);
  if (hit) return { existingId: hit.id, matchVia: 'nameCategory' };
  const sameName = mappings.byName.get(item.name) || [];
  if (sameName.length === 1 && notConflicting(sameName[0])) {
    return { existingId: sameName[0].id, matchVia: 'nameOnly' };
  }
  return { existingId: null, matchVia: null };
}

/**
 * 覆盖预检：逐行命中既有菜谱标「覆盖更新」；多行命中同一菜谱时后者标错
 *（并发写同一行会互相覆盖，结果不可预期）。
 * @param {Object[]} items 有效行（跳过行原样带过）
 * @param {{bySourceKey: Map, byNameCategory: Map, byName: Map}} mappings buildRecipeMappings 输出
 */
export function markOverwrite(items, mappings) {
  const claimed = new Map(); // existingId → 先见行（菜名用于报错文案）
  for (const it of items) {
    if (it.errors.length) continue;
    const { existingId, matchVia } = matchExistingRecipe(it, mappings);
    if (existingId == null) continue;
    if (claimed.has(existingId)) {
      it.errors.push(`与「${claimed.get(existingId)}」命中同一既有菜谱（id=${existingId}）`);
      continue;
    }
    claimed.set(existingId, it.name);
    it.existingId = existingId;
    it.matchVia = matchVia;
    it.willOverwrite = true;
  }
  return items;
}

/**
 * 分页循环拉全量（注入 fetchApi 便于单测）：空页即止，页数上限防御死循环。
 * @param {(params: {page: number, size: number}) => Promise<{records: any[], total: number}>} fetchApi
 * @param {number} [pageSize] 单页条数（默认 RECIPE_PAGE_SIZE，见其注释）
 */
export async function fetchAllPaged(fetchApi, pageSize = RECIPE_PAGE_SIZE) {
  const out = [];
  let page = 1;
  let total = Infinity;
  while ((page - 1) * pageSize < total && page <= 10000) {
    const res = await fetchApi({ page, size: pageSize });
    total = res.total || 0;
    const records = res.records || [];
    if (!records.length) break; // 防御：空页直接结束
    out.push(...records);
    page += 1;
  }
  return out;
}

/**
 * 固定并发 Promise 池：worker 逐个消费 items，最多 limit 个同时运行；
 * shouldStop() 为真后不再取新任务（在途任务跑完），由调用方给未开始行标记中止。
 * worker 自行捕获业务异常（池不吞错，异常会中断整池）。
 * @param {Array} items
 * @param {number} limit 并发上限
 * @param {(item: any) => Promise<void>} worker
 * @param {() => boolean} [shouldStop]
 */
export async function runPool(items, limit, worker, shouldStop) {
  let next = 0;
  const runners = Array.from({ length: Math.max(1, Math.min(limit, items.length)) }, async () => {
    for (;;) {
      if (shouldStop && shouldStop()) return;
      const idx = next++;
      if (idx >= items.length) return;
      await worker(items[idx]);
    }
  });
  await Promise.all(runners);
}

/**
 * 分类名 → 分类 id 解析表构建：先串行补建缺失分类（addDiningCategory 全局唯一、不回传 id，
 * 并发同建无收益），再由调用方重拉分类列表映射回 id。
 * @param {string[]} categoryNames 本次上传涉及的全部非空分类名
 * @param {Array<{name: string}>} knownCategories 预检拉到的既有分类（求差集）
 * @param {(data: {name: string, sort: number}) => Promise<any>} addCategory
 * @returns {Promise<{created: string[], failed: Map<string, Error>}>} failed 的分类相关行上传时报错
 */
export async function createMissingCategories(categoryNames, knownCategories, addCategory) {
  const known = new Set((knownCategories || []).map((c) => c.name));
  let nextSort = (knownCategories || []).reduce((m, c) => Math.max(m, Number(c.sort) || 0), 0) + 1;
  const created = [];
  const failed = new Map();
  for (const name of categoryNames) {
    if (!name || known.has(name)) continue;
    try {
      await addCategory({ name, sort: nextSort });
      created.push(name);
      nextSort += 1;
      known.add(name); // 同名只建一次
    } catch (err) {
      failed.set(name, err);
    }
  }
  return { created, failed };
}

/**
 * 构建 add/update 请求体（与 Add·UpdateDiningRecipeFrom 对齐）：
 * - 封面：仅本次成功上传封面时携带 coverObjectKey（update 侧 null=不修改，不传即保既有封面）；
 * - sourceKey：仅行携带时传（update 不传 = 保留既有标识，对齐后端 applyUpdate 语义）；
 * - 营养列：null = 未录入（add）/ 不修改（update，后端不支持经此接口清空）；
 * - 分类：categoryName 解析出的 id；未分类传 0（update 侧 0=改回未分类，全量快照语义）。
 * @param {Object} item 有效行
 * @param {{coverObjectKey: string|null, categoryId: number, existingId?: number|null}} opts
 */
export function buildRecipePayload(item, { coverObjectKey, categoryId, existingId = null }) {
  const payload = {
    name: item.name,
    categoryId: categoryId ?? 0,
    ingredients: item.ingredients,
    steps: item.steps,
    caloriesPer100g: item.caloriesPer100g,
    caloriesTotal: item.caloriesTotal,
    proteinG: item.proteinG,
    fatG: item.fatG,
    carbsG: item.carbsG,
    sodiumMg: item.sodiumMg,
  };
  if (coverObjectKey) payload.coverObjectKey = coverObjectKey;
  if (item.sourceKey) payload.sourceKey = item.sourceKey;
  if (existingId != null) payload.id = existingId;
  return payload;
}

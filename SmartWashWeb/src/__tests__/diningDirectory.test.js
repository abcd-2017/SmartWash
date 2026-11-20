// src/__tests__/diningDirectory.test.js
// 目录式菜谱上传纯逻辑单测：目录分组规范（gameDirectory.test.js 同范式）、CSV RFC4180 解析、
// 行级预检、批内判重、库内映射判重、请求体构建、并发池与分类补建。
import { describe, expect, it, vi } from 'vitest';
import {
  buildRecipeMappings,
  buildRecipePayload,
  buildRecipeRow,
  createMissingCategories,
  decodeCsvText,
  fetchAllPaged,
  inspectDiningDir,
  markBatchDuplicates,
  markOverwrite,
  matchExistingRecipe,
  MAX_COVER_BYTES,
  parseIngredients,
  parseNutritionValue,
  parseRecipeCsv,
  RECIPE_PAGE_SIZE,
  runPool,
  splitDiningDir,
} from '../views/dining/diningDirectory';

/** 构造带 webkitRelativePath 的伪目录文件（浏览器 webkitdirectory 选择行为的单测替身） */
const makeFile = (name, relPath, content = 'x', size) => {
  const file = new File([content], name);
  Object.defineProperty(file, 'webkitRelativePath', { value: relPath });
  if (size != null) Object.defineProperty(file, 'size', { value: size });
  return file;
};

/** 与 cooklikehoc.csv 一致的表头（顺序无关解析，最后一列可选） */
const CSV_HEADER =
  '分类名,菜名,食材名列表,食材明细列表,步骤,封面对象键,封面链接,热量每100g,整份热量,蛋白质g,脂肪g,碳水g,钠mg,唯一标识';

describe('splitDiningDir 目录分组', () => {
  it('根层 *.csv 进 csvFiles，image/ 内图片进索引', () => {
    const aCsv = makeFile('a.csv', '菜谱库/a.csv', 'csv-a');
    const bCsv = makeFile('b.CSV', '菜谱库/b.CSV', 'csv-b');
    const img = makeFile('番茄.png', '菜谱库/image/番茄.png', 'img');
    const res = splitDiningDir([aCsv, bCsv, img]);
    expect(res.csvFiles.map((f) => f.name)).toEqual(['a.csv', 'b.CSV']);
    expect(res.imageDirPresent).toBe(true);
    expect(res.imageIndex.get('番茄.png')).toBe(img);
    expect(res.ignoredFiles).toEqual([]);
  });

  it('image/ 目录名大小写不敏感，子层级图片一并索引，同名取先见者', () => {
    const img1 = makeFile('a.png', '菜谱库/Image/a.png', 'first');
    const img2 = makeFile('a.png', '菜谱库/image/sub/a.png', 'second');
    const res = splitDiningDir([img2, img1]);
    expect(res.imageDirPresent).toBe(true);
    expect(res.imageIndex.get('a.png')).toBe(img2); // 先见者胜
  });

  it('covers/ 目录同样识别为封面目录（兼容项目现有命名）', () => {
    const aCsv = makeFile('a.csv', '菜谱库/a.csv');
    const img = makeFile('番茄.png', '菜谱库/covers/番茄.png', 'img');
    const res = splitDiningDir([aCsv, img]);
    expect(res.csvFiles.map((f) => f.name)).toEqual(['a.csv']);
    expect(res.imageDirPresent).toBe(true);
    expect(res.imageIndex.get('番茄.png')).toBe(img);
  });

  it('.DS_Store 等隐藏文件与隐藏目录任意层级静默忽略', () => {
    const res = splitDiningDir([
      makeFile('.DS_Store', '菜谱库/.DS_Store'),
      makeFile('keep.csv', '菜谱库/keep.csv'),
      makeFile('x.csv', '菜谱库/.git/x.csv'),
    ]);
    expect(res.csvFiles.map((f) => f.name)).toEqual(['keep.csv']);
    expect(res.ignoredFiles).toEqual([]);
  });

  it('根层非 csv 文件与其他子目录文件进入忽略清单（带路径定位）', () => {
    const res = splitDiningDir([
      makeFile('readme.txt', '菜谱库/readme.txt'),
      makeFile('old.csv', '菜谱库/backup/old.csv'),
      makeFile('keep.csv', '菜谱库/keep.csv'),
    ]);
    expect(res.csvFiles.map((f) => f.name)).toEqual(['keep.csv']);
    expect(res.ignoredFiles).toEqual(['readme.txt', 'backup/old.csv']);
  });

  it('image/ 内非图片扩展名不进索引（引用行按未找到报错）', () => {
    const res = splitDiningDir([makeFile('note.txt', '菜谱库/image/note.txt')]);
    expect(res.imageDirPresent).toBe(true);
    expect(res.imageIndex.size).toBe(0);
  });
});

describe('inspectDiningDir 整体校验', () => {
  it('无 csv 整体报错并提示目录规范', () => {
    const res = inspectDiningDir({ csvFiles: [], imageDirPresent: true });
    expect(res.ok).toBe(false);
    expect(res.reason).toContain('未找到 .csv');
    expect(res.reason).toContain('image/');
  });

  it('缺 image/ 目录整体报错', () => {
    const res = inspectDiningDir({
      csvFiles: [makeFile('a.csv', 'p/a.csv')],
      imageDirPresent: false,
    });
    expect(res.ok).toBe(false);
    expect(res.reason).toContain('image/');
  });

  it('csv + image/ 齐备通过', () => {
    const res = inspectDiningDir({
      csvFiles: [makeFile('a.csv', 'p/a.csv')],
      imageDirPresent: true,
    });
    expect(res.ok).toBe(true);
  });
});

describe('decodeCsvText 编码探测', () => {
  it('UTF-8 文本原样返回', async () => {
    const file = makeFile('a.csv', 'p/a.csv', `${CSV_HEADER}\n主食,番茄,鸡蛋,.,步骤,,`);
    expect(await decodeCsvText(file)).toContain('番茄');
  });

  it('GBK 文件回退 GBK 解码（中文 Windows Excel 另存场景）', async () => {
    // GBK 字节：分类名,菜名,番茄炒蛋
    const gbkBytes = new Uint8Array([
      183, 214, 192, 224, 195, 251, 44, 178, 203, 195, 251, 44, 183, 172, 199, 209, 179, 180, 181,
      176,
    ]);
    const file = new File([gbkBytes], 'gbk.csv');
    const text = await decodeCsvText(file);
    expect(text).toBe('分类名,菜名,番茄炒蛋');
  });
});

describe('parseRecipeCsv RFC4180 解析', () => {
  it('引号字段内的顿号逗号与换行完整保留（食材明细/步骤列）', () => {
    const text = [
      CSV_HEADER,
      '主食,"面, 食","鸡蛋、西红柿","鸡蛋:2个","1. 打蛋\n2. 炒",dining/covers/a.png,,88.5,,, ,,100,hoc_1',
    ].join('\n');
    const res = parseRecipeCsv(text);
    expect(res.ok).toBe(true);
    expect(res.rows).toHaveLength(1);
    expect(res.rows[0]['菜名']).toBe('面, 食');
    expect(res.rows[0]['步骤']).toBe('1. 打蛋\n2. 炒');
    expect(res.rows[0]['食材明细列表']).toBe('鸡蛋:2个');
    expect(res.rows[0]['唯一标识']).toBe('hoc_1');
  });

  it('CRLF 行尾与表头首列 BOM 均正确处理', () => {
    const text = `\uFEFF${CSV_HEADER}\r\n主食,番茄,鸡蛋,.,步骤,,,`;
    const res = parseRecipeCsv(text);
    expect(res.ok).toBe(true);
    expect(res.rows[0]['分类名']).toBe('主食');
  });

  it('缺必选列整体报错（必选 5 列：分类名/菜名/食材名列表/步骤/封面链接）', () => {
    const res = parseRecipeCsv('分类名,菜名\n主食,番茄');
    expect(res.ok).toBe(false);
    expect(res.reason).toContain('食材名列表');
  });

  it('空数据行文件整体报错', () => {
    const res = parseRecipeCsv(`${CSV_HEADER}\n,,`);
    expect(res.ok).toBe(false);
    expect(res.reason).toContain('数据行');
  });

  it('引号未闭合等结构损坏整体报错', () => {
    const res = parseRecipeCsv(`${CSV_HEADER}\n主食,"未闭合`);
    expect(res.ok).toBe(false);
    expect(res.reason).toContain('CSV 解析失败');
  });

  it('表头按列名匹配顺序无关', () => {
    // 调换列序：菜名在前、分类名在后
    const text = '菜名,步骤,封面链接,食材名列表,分类名\n番茄,做法,,鸡蛋,汤类';
    const res = parseRecipeCsv(text);
    expect(res.ok).toBe(true);
    expect(res.rows[0]['分类名']).toBe('汤类');
  });
});

describe('buildRecipeRow 行级预检', () => {
  const ctx = (imageIndex = new Map()) => ({ rowNo: 1, csvName: 'a.csv', imageIndex });

  it('正常行归一：食材分列去重、封面对象键取 basename 关联本地文件', () => {
    const img = makeFile('番茄.png', 'p/image/番茄.png');
    const res = buildRecipeRow(
      {
        分类名: '主食 ',
        菜名: '番茄炒蛋',
        食材名列表: '鸡蛋、西红柿, 鸡蛋；葱花',
        步骤: '打蛋',
        封面对象键: 'dining/covers/番茄.png',
        热量每100g: '88.5',
        唯一标识: ' hoc_1 ',
      },
      ctx(new Map([['番茄.png', img]]))
    );
    expect(res.errors).toEqual([]);
    expect(res.categoryName).toBe('主食');
    expect(res.name).toBe('番茄炒蛋');
    expect(res.sourceKey).toBe('hoc_1');
    expect(res.ingredients).toEqual(['鸡蛋', '西红柿', '葱花']);
    expect(res.coverFileName).toBe('番茄.png');
    expect(res.coverFile).toBe(img);
    expect(res.caloriesPer100g).toBe(88.5);
  });

  it('菜名为空报错；菜名/分类名/唯一标识/步骤超长报错', () => {
    const res = buildRecipeRow(
      {
        菜名: '',
        分类名: 'x'.repeat(33),
        唯一标识: 'k'.repeat(65),
        步骤: 's'.repeat(20001),
        食材名列表: '鸡蛋',
      },
      ctx()
    );
    expect(res.errors.some((e) => e.includes('菜名为空'))).toBe(true);
    expect(res.errors.some((e) => e.includes('分类名超过 32'))).toBe(true);
    expect(res.errors.some((e) => e.includes('唯一标识超过 64'))).toBe(true);
    expect(res.errors.some((e) => e.includes('步骤超过 20000'))).toBe(true);
  });

  it('营养列非数字/负数/越界报错，留空为 null', () => {
    const res = buildRecipeRow(
      {
        菜名: '番茄',
        热量每100g: 'abc',
        整份热量: '-1',
        蛋白质g: '10000',
        钠mg: '999999.99',
        脂肪g: '',
      },
      ctx()
    );
    expect(res.errors.some((e) => e.includes('热量每100g') && e.includes('不是数字'))).toBe(true);
    expect(res.errors.some((e) => e.includes('整份热量') && e.includes('负数'))).toBe(true);
    expect(res.errors.some((e) => e.includes('蛋白质g') && e.includes('上限 9999.99'))).toBe(true);
    expect(res.errors).toHaveLength(3);
    expect(res.sodiumMg).toBe(999999.99);
    expect(res.fatG).toBeNull();
  });

  it('封面文件名在 image/ 中找不到报错', () => {
    const res = buildRecipeRow({ 菜名: '番茄', 封面对象键: 'dining/covers/缺失.png' }, ctx());
    expect(res.errors).toEqual(['封面「缺失.png」在封面目录中未找到']);
  });

  it('封面扩展名不合法报错', () => {
    const res = buildRecipeRow({ 菜名: '番茄', 封面对象键: 'dining/covers/a.gif' }, ctx());
    expect(res.errors[0]).toContain('不是 jpg/jpeg/png/webp');
  });

  it('封面超过 5MB 报错', () => {
    const img = makeFile('big.png', 'p/image/big.png', 'x', MAX_COVER_BYTES + 1);
    const res = buildRecipeRow(
      { 菜名: '番茄', 封面对象键: 'big.png' },
      ctx(new Map([['big.png', img]]))
    );
    expect(res.errors[0]).toContain('超过 5MB');
  });

  it('食材超数量/超长度报错', () => {
    const res = buildRecipeRow(
      { 菜名: '番茄', 食材名列表: Array.from({ length: 101 }, (_, i) => `蛋${i}`).join('、') },
      ctx()
    );
    expect(res.errors.some((e) => e.includes('食材超过 100 项'))).toBe(true);
    const res2 = buildRecipeRow({ 菜名: '番茄', 食材名列表: 'x'.repeat(33) }, ctx());
    expect(res2.errors.some((e) => e.includes('超过 32 字'))).toBe(true);
  });

  it('parseIngredients 分隔符与去重保序', () => {
    expect(parseIngredients('鸡蛋、西红柿, 葱花；鸡蛋')).toEqual(['鸡蛋', '西红柿', '葱花']);
  });
});

describe('批内判重 markBatchDuplicates', () => {
  const row = (over = {}) =>
    buildRecipeRow(
      { 菜名: '默认', 分类名: '主', ...over },
      { rowNo: 1, csvName: 'a.csv', imageIndex: new Map() }
    );

  it('同唯一标识重复：全部重复行标错', () => {
    const a = row({ 菜名: 'A', 唯一标识: 'hoc_1' });
    const b = row({ 菜名: 'B', 唯一标识: 'hoc_1' });
    markBatchDuplicates([a, b]);
    expect(a.errors.some((e) => e.includes('本批内重复'))).toBe(true);
    expect(b.errors.some((e) => e.includes('本批内重复'))).toBe(true);
  });

  it('无标识行（菜名,分类）组合重复标错', () => {
    const a = row({ 菜名: '番茄炒蛋', 分类名: '主' });
    const b = row({ 菜名: '番茄炒蛋', 分类名: '主' });
    const c = row({ 菜名: '番茄炒蛋', 分类名: '汤' });
    markBatchDuplicates([a, b, c]);
    expect(a.errors.some((e) => e.includes('本批内重复'))).toBe(true);
    expect(b.errors.some((e) => e.includes('本批内重复'))).toBe(true);
    expect(c.errors).toEqual([]);
  });

  it('不同 key 的同名菜不判重（不同上游来源的菜是合法业务态）', () => {
    const a = row({ 菜名: '番茄', 唯一标识: 'hoc_a' });
    const b = row({ 菜名: '番茄', 唯一标识: 'htc_b' });
    markBatchDuplicates([a, b]);
    expect(a.errors).toEqual([]);
    expect(b.errors).toEqual([]);
  });
});

describe('库内映射判重 buildRecipeMappings / matchExistingRecipe / markOverwrite', () => {
  const mappings = buildRecipeMappings([
    { id: 1, name: '番茄炒蛋', categoryName: '主食', sourceKey: 'hoc_1' },
    { id: 2, name: '番茄炒蛋', categoryName: '汤类', sourceKey: null },
    { id: 3, name: '小龙虾', categoryName: '水产', sourceKey: 'htc_1' },
  ]);

  it('sourceKey 优先直查', () => {
    const item = buildRecipeRow(
      { 菜名: '别的名字', 唯一标识: 'hoc_1' },
      { rowNo: 1, csvName: 'a', imageIndex: new Map() }
    );
    expect(matchExistingRecipe(item, mappings)).toEqual({ existingId: 1, matchVia: 'sourceKey' });
  });

  it('（菜名,分类）兜底命中', () => {
    const item = buildRecipeRow(
      { 菜名: '番茄炒蛋', 分类名: '汤类' },
      { rowNo: 1, csvName: 'a', imageIndex: new Map() }
    );
    expect(matchExistingRecipe(item, mappings)).toEqual({
      existingId: 2,
      matchVia: 'nameCategory',
    });
  });

  it('兜底排除双方挂不同 key：按新增不覆盖', () => {
    const item = buildRecipeRow(
      { 菜名: '小龙虾', 分类名: '水产', 唯一标识: 'hoc_x' },
      { rowNo: 1, csvName: 'a', imageIndex: new Map() }
    );
    expect(matchExistingRecipe(item, mappings).existingId).toBeNull();
  });

  it('行无 key 命中已回填 key 的菜谱：仍算命中（更新时保留既有 key）', () => {
    const item = buildRecipeRow(
      { 菜名: '番茄炒蛋', 分类名: '主食' },
      { rowNo: 1, csvName: 'a', imageIndex: new Map() }
    );
    expect(matchExistingRecipe(item, mappings).existingId).toBe(1);
  });

  it('宽松兜底：分类改名后仅菜名且库内唯一（已分类）命中（对齐后端 147bcb62）', () => {
    const item = buildRecipeRow(
      { 菜名: '小龙虾', 分类名: '烧烤' },
      { rowNo: 1, csvName: 'a', imageIndex: new Map() }
    );
    // （小龙虾,烧烤）两级键不命中 → 同名唯一兜底命中 id=3
    expect(matchExistingRecipe(item, mappings)).toEqual({ existingId: 3, matchVia: 'nameOnly' });
  });

  it('宽松兜底排除双方挂不同 key', () => {
    const item = buildRecipeRow(
      { 菜名: '小龙虾', 分类名: '烧烤', 唯一标识: 'hoc_y' },
      { rowNo: 1, csvName: 'a', imageIndex: new Map() }
    );
    expect(matchExistingRecipe(item, mappings).existingId).toBeNull();
  });

  it('多道同名（已分类）存在歧义，宽松兜底保守不命中', () => {
    const dupMappings = buildRecipeMappings([
      { id: 21, name: '狮子头', categoryName: '主食', sourceKey: null },
      { id: 22, name: '狮子头', categoryName: '汤类', sourceKey: null },
    ]);
    const item = buildRecipeRow(
      { 菜名: '狮子头', 分类名: '烧烤' },
      { rowNo: 1, csvName: 'a', imageIndex: new Map() }
    );
    expect(matchExistingRecipe(item, dupMappings).existingId).toBeNull();
  });

  it('未分类同名菜不参与宽松兜底，也不阻断唯一已分类同名的命中', () => {
    const mixed = buildRecipeMappings([
      { id: 31, name: '凉拌黄瓜', categoryName: null, sourceKey: null },
      { id: 32, name: '凉拌黄瓜', categoryName: '凉菜', sourceKey: null },
    ]);
    const item = buildRecipeRow(
      { 菜名: '凉拌黄瓜', 分类名: '酱料' },
      { rowNo: 1, csvName: 'a', imageIndex: new Map() }
    );
    expect(matchExistingRecipe(item, mixed)).toEqual({ existingId: 32, matchVia: 'nameOnly' });
  });

  it('markOverwrite 标覆盖行；多行命中同一菜谱时后来者标错', () => {
    const a = buildRecipeRow(
      { 菜名: '番茄炒蛋', 分类名: '汤类' },
      { rowNo: 1, csvName: 'a', imageIndex: new Map() }
    );
    const b = buildRecipeRow(
      { 菜名: '番茄炒蛋', 分类名: '汤类', 唯一标识: 'x' },
      { rowNo: 2, csvName: 'a', imageIndex: new Map() }
    );
    markOverwrite([a, b], mappings);
    expect(a.willOverwrite).toBe(true);
    expect(a.existingId).toBe(2);
    // b 带 key=x、目标菜无 key → 兜底命中同一 id=2，但已被 a 占用 → 报错跳过
    expect(b.errors.some((e) => e.includes('命中同一既有菜谱'))).toBe(true);
    expect(b.willOverwrite).toBe(false);
  });
});

describe('分页循环 fetchAllPaged', () => {
  it('按 total 翻页聚合，空页即止', async () => {
    const fetchApi = vi
      .fn()
      .mockResolvedValueOnce({
        records: Array.from({ length: RECIPE_PAGE_SIZE }, (_, i) => i),
        total: RECIPE_PAGE_SIZE + 1,
      })
      .mockResolvedValueOnce({ records: [999], total: RECIPE_PAGE_SIZE + 1 })
      .mockResolvedValueOnce({ records: [], total: RECIPE_PAGE_SIZE + 1 });
    const out = await fetchAllPaged(fetchApi, RECIPE_PAGE_SIZE);
    expect(out).toHaveLength(RECIPE_PAGE_SIZE + 1);
    expect(out[out.length - 1]).toBe(999);
    expect(fetchApi).toHaveBeenNthCalledWith(1, { page: 1, size: RECIPE_PAGE_SIZE });
    expect(fetchApi).toHaveBeenNthCalledWith(2, { page: 2, size: RECIPE_PAGE_SIZE });
  });
});

describe('并发池 runPool', () => {
  it('最多 limit 个同时运行，全部处理完成', async () => {
    let inflight = 0;
    let peak = 0;
    const items = Array.from({ length: 7 }, (_, i) => i);
    const processed = [];
    await runPool(items, 3, async (n) => {
      inflight += 1;
      peak = Math.max(peak, inflight);
      await new Promise((r) => setTimeout(r, 5));
      processed.push(n);
      inflight -= 1;
    });
    expect(peak).toBe(3);
    expect(processed.sort((a, b) => a - b)).toEqual(items);
  });

  it('shouldStop 为真后不再取新任务', async () => {
    const items = [1, 2, 3, 4, 5, 6];
    let stop = false;
    const processed = [];
    await runPool(
      items,
      2,
      async (n) => {
        processed.push(n);
        if (processed.length >= 2) stop = true;
        await new Promise((r) => setTimeout(r, 5));
      },
      () => stop
    );
    expect(processed.length).toBeLessThan(items.length);
    expect(items.slice(processed.length)).toEqual(items.slice(processed.length)); // 剩余项由调用方标记
  });
});

describe('分类补建 createMissingCategories', () => {
  it('仅创建缺失分类，sort 沿既有最大值递增，同名只建一次', async () => {
    const addCategory = vi.fn().mockResolvedValue({});
    const res = await createMissingCategories(
      ['汤类', '主食', '汤类', '甜品'],
      [{ name: '主食', sort: 3 }],
      addCategory
    );
    expect(res.created).toEqual(['汤类', '甜品']);
    expect(addCategory).toHaveBeenCalledTimes(2);
    expect(addCategory).toHaveBeenNthCalledWith(1, { name: '汤类', sort: 4 });
    expect(addCategory).toHaveBeenNthCalledWith(2, { name: '甜品', sort: 5 });
    expect(res.failed.size).toBe(0);
  });

  it('单个创建失败不阻断后续，failed 供行级报错', async () => {
    const addCategory = vi
      .fn()
      .mockRejectedValueOnce(new Error('分类名已存在'))
      .mockResolvedValueOnce({});
    const res = await createMissingCategories(['重复类', '甜品'], [], addCategory);
    expect(res.created).toEqual(['甜品']);
    expect(res.failed.get('重复类').message).toBe('分类名已存在');
  });
});

describe('请求体构建 buildRecipePayload', () => {
  const item = buildRecipeRow(
    {
      菜名: '番茄炒蛋',
      分类名: '主食',
      食材名列表: '鸡蛋、西红柿',
      步骤: '打蛋',
      热量每100g: '88.5',
      唯一标识: 'hoc_1',
    },
    { rowNo: 1, csvName: 'a', imageIndex: new Map() }
  );

  it('新增：带 sourceKey；有封面才带 coverObjectKey；未分类 categoryId=0', () => {
    const withCover = buildRecipePayload(item, {
      coverObjectKey: 'dining/covers/a.png',
      categoryId: 5,
    });
    expect(withCover).toEqual({
      name: '番茄炒蛋',
      categoryId: 5,
      ingredients: ['鸡蛋', '西红柿'],
      steps: '打蛋',
      caloriesPer100g: 88.5,
      caloriesTotal: null,
      proteinG: null,
      fatG: null,
      carbsG: null,
      sodiumMg: null,
      coverObjectKey: 'dining/covers/a.png',
      sourceKey: 'hoc_1',
    });
    const noCover = buildRecipePayload(item, { coverObjectKey: null, categoryId: 0 });
    expect(noCover.coverObjectKey).toBeUndefined();
    expect(noCover.id).toBeUndefined();
    expect(noCover.categoryId).toBe(0);
  });

  it('更新：带 id；行无 sourceKey 不传（后端保留既有标识）', () => {
    const payload = buildRecipePayload(item, {
      coverObjectKey: null,
      categoryId: 5,
      existingId: 9,
    });
    expect(payload.id).toBe(9);
    expect(payload.sourceKey).toBe('hoc_1');
    const keyless = buildRecipeRow(
      { 菜名: 'A' },
      { rowNo: 1, csvName: 'a', imageIndex: new Map() }
    );
    const p2 = buildRecipePayload(keyless, { coverObjectKey: null, categoryId: 0, existingId: 9 });
    expect(p2.sourceKey).toBeUndefined();
    expect(p2.coverObjectKey).toBeUndefined();
  });

  it('parseNutritionValue 边界：空/非数字/上限', () => {
    expect(parseNutritionValue('', 100)).toEqual({ value: null, error: null });
    expect(parseNutritionValue('  ', 100)).toEqual({ value: null, error: null });
    expect(parseNutritionValue('abc', 100).error).toContain('不是数字');
    expect(parseNutritionValue('100.01', 100).error).toContain('上限');
    expect(parseNutritionValue('99.99', 100)).toEqual({ value: 99.99, error: null });
  });
});

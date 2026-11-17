// src/__tests__/DiningDirectoryImportDialog.test.js
// 目录导入菜谱对话框的组件级端到端测试（mock API 层，范式同 BatchUploadDialog.test.js）：
// 覆盖「选父目录 → CSV/image 预检分组 → 覆盖预检 → 覆盖确认 → 逐条上传（封面先行/分类补建/
// 单行失败不阻断/中止）」完整状态机。本机无后端栈，以组件级验证替代浏览器走查。
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

vi.mock('@/api/dining', () => ({
  getDiningRecipeList: vi.fn(),
  getDiningCategoryList: vi.fn(),
  addDiningCategory: vi.fn(),
  addDiningRecipe: vi.fn(),
  updateDiningRecipe: vi.fn(),
  uploadDiningRecipeCover: vi.fn(),
}));

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: vi.fn(() => Promise.resolve(true)),
}));

// 共享分类选项缓存带模块级 memo，测试内替换为轻量替身，隔离缓存态并避免拉起 http 链
vi.mock('@/utils/optionCache', () => ({
  diningCategoryOptionsCache: { invalidate: vi.fn() },
}));

import DiningDirectoryImportDialog from '@/views/dining/components/DiningDirectoryImportDialog.vue';
import { ElMessage } from 'element-plus';
import {
  getDiningRecipeList,
  getDiningCategoryList,
  addDiningCategory,
  addDiningRecipe,
  updateDiningRecipe,
  uploadDiningRecipeCover,
} from '@/api/dining';

/** 构造带 webkitRelativePath 的伪目录文件（浏览器 webkitdirectory 选择行为的测试替身） */
const makeFile = (name, relPath, content = 'x') => {
  const file = new File([content], name);
  Object.defineProperty(file, 'webkitRelativePath', { value: relPath });
  return file;
};

const CSV_HEADER =
  '分类名,菜名,食材名列表,食材明细列表,步骤,封面对象键,封面链接,热量每100g,整份热量,蛋白质g,脂肪g,碳水g,钠mg,唯一标识';

const makeCsv = (name, relPath, rows) => makeFile(name, relPath, [CSV_HEADER, ...rows].join('\n'));

// cooklikehoc.csv 同构行：14 列（封面链接/营养留空段补齐空字段，唯一标识落位末列）
const row1 = '主食,番茄炒蛋,"鸡蛋、西红柿",鸡蛋:2个,打蛋,dining/covers/番茄.png,,88.5,,,,,,hoc_1';
const row2 = '汤类,紫菜蛋花汤,紫菜,紫菜:10g,煮,,http://example.com/a.png,,,,,,,';
const row1Dup =
  '主食,番茄炒蛋,"鸡蛋、西红柿",鸡蛋:2个,打蛋,dining/covers/番茄.png,,88.5,,,,,,hoc_1';

/** Element Plus 组件 stub：保留结构语义，去掉样式/定位等 DOM 依赖 */
const globalStubs = {
  'el-dialog': {
    props: ['modelValue', 'title', 'width'],
    template: '<div v-if="modelValue" class="dialog"><slot /></div>',
  },
  'el-button': {
    props: ['type', 'disabled', 'loading'],
    template:
      '<button :disabled="disabled || loading" :data-loading="loading" @click="$emit(\'click\')"><slot /></button>',
  },
  'el-table': { props: ['rowClassName', 'data'], template: '<table><slot /></table>' },
  // 只渲染 header 具名插槽：默认插槽是带 { row } 作用域的单元格内容，脱离 el-table 上下文无 props 可绑
  'el-table-column': { template: '<div class="col"><slot name="header" /></div>' },
  'el-tag': {
    props: ['type', 'size', 'effect'],
    template: '<span class="tag" :data-type="type"><slot /></span>',
  },
  'el-tooltip': { template: '<span><slot /></span>' },
  'el-progress': { props: ['percentage', 'strokeWidth'], template: '<div class="progress" />' },
};

const mountDialog = () =>
  mount(DiningDirectoryImportDialog, {
    props: { modelValue: true },
    global: { stubs: globalStubs },
  });

const chooseParentDir = async (wrapper, files) => {
  await wrapper.vm.handleDirChosen({ target: { files, value: '' } });
  await flushPromises();
};

beforeEach(() => {
  vi.clearAllMocks();
  // 库内已有：紫菜蛋花汤（汤类，无 key）→ 行2 走（菜名,分类）兜底命中覆盖更新
  getDiningRecipeList.mockResolvedValue({
    records: [{ id: 10, name: '紫菜蛋花汤', categoryName: '汤类', sourceKey: null }],
    total: 1,
  });
  // 分类预检/重拉：默认只有 主食；「新分类」由 addDiningCategory 后补进 state 模拟重拉可见
  const categoryState = [{ id: 1, name: '主食', sort: 1 }];
  getDiningCategoryList.mockImplementation(() =>
    Promise.resolve({ records: categoryState, total: categoryState.length })
  );
  addDiningCategory.mockImplementation(({ name, sort }) => {
    categoryState.push({ id: 99, name, sort });
    return Promise.resolve({});
  });
  addDiningRecipe.mockResolvedValue({ id: 101 });
  updateDiningRecipe.mockResolvedValue({});
  uploadDiningRecipeCover.mockResolvedValue('dining/covers/番茄.png');
});

describe('目录导入预检（CSV/image 分组与覆盖标记）', () => {
  it('多 CSV 分组正确：覆盖/新增/批内重复/文件级缺列/忽略文件', async () => {
    const wrapper = mountDialog();
    await chooseParentDir(wrapper, [
      makeCsv('cook.csv', '菜谱库/cook.csv', [row1, row2, row1Dup]),
      // 缺必选列（无封面链接）→ 文件级整体报错，伪行进队列
      makeFile('bad.csv', '菜谱库/bad.csv', '分类名,菜名,食材名列表,步骤,蛋白质g'),
      makeFile('番茄.png', '菜谱库/image/番茄.png'),
      makeFile('readme.txt', '菜谱库/readme.txt'),
    ]);

    // 文件级错误 1 伪行 + 正常 3 行（同 hoc_1 两行批内重复 → 全部跳过，对齐后端全标错语义）
    expect(wrapper.vm.queue).toHaveLength(4);
    const fileError = wrapper.vm.queue[0];
    expect(fileError.rowNo).toBeNull();
    expect(fileError.skipReason).toContain('缺少必选列');

    const rows = wrapper.vm.queue.slice(1);
    expect(rows[0].name).toBe('番茄炒蛋');
    expect(rows[0].skipReason).toContain('本批内重复');
    expect(rows[0].coverFile.name).toBe('番茄.png');
    expect(rows[0].willOverwrite).toBe(false); // 错误行不参与覆盖标记

    expect(rows[1].name).toBe('紫菜蛋花汤');
    expect(rows[1].skipReason).toBe('');
    expect(rows[1].willOverwrite).toBe(true);
    expect(rows[1].existingId).toBe(10);
    expect(rows[1].matchVia).toBe('nameCategory');

    expect(rows[2].skipReason).toContain('本批内重复'); // 同 hoc_1 重复行标红跳过

    expect(wrapper.vm.uploadTargets).toHaveLength(1);
    expect(wrapper.vm.skippedCount).toBe(3);
    expect(wrapper.vm.ignoredFiles).toEqual(['readme.txt']);
    expect(wrapper.vm.phase).toBe('preview');
    wrapper.unmount();
  });

  it('image/ 缺失或无 csv：整体报错并停留 choose，不发起任何请求', async () => {
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}));
    const wrapper = mountDialog();

    await chooseParentDir(wrapper, [makeCsv('cook.csv', '菜谱库/cook.csv', [row1])]);
    expect(errorSpy).toHaveBeenCalledTimes(1);
    expect(errorSpy.mock.calls[0][0]).toContain('image/');
    expect(wrapper.vm.phase).toBe('choose');

    errorSpy.mockClear();
    await chooseParentDir(wrapper, [
      makeFile('番茄.png', '菜谱库/image/番茄.png'),
      makeFile('.DS_Store', '菜谱库/.DS_Store'),
    ]);
    expect(errorSpy).toHaveBeenCalledTimes(1);
    expect(errorSpy.mock.calls[0][0]).toContain('.csv');
    expect(getDiningRecipeList).not.toHaveBeenCalled();
    wrapper.unmount();
    errorSpy.mockRestore();
  });

  it('覆盖预检拉取失败：提示错误、复位 checking、停留 choose 不卡死', async () => {
    getDiningRecipeList.mockRejectedValue(new Error('服务开小差了'));
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}));
    const wrapper = mountDialog();

    await chooseParentDir(wrapper, [
      makeCsv('cook.csv', '菜谱库/cook.csv', [row1]),
      makeFile('a.png', '菜谱库/image/a.png'),
    ]);

    expect(errorSpy).toHaveBeenCalledWith('服务开小差了');
    expect(wrapper.vm.checking).toBe(false);
    expect(wrapper.vm.phase).toBe('choose');
    expect(wrapper.vm.queue).toHaveLength(0);
    errorSpy.mockRestore();
    wrapper.unmount();
  });
});

describe('目录导入上传（封面先行 / 判重幂等 / 分类补建）', () => {
  it('有封面行先传封面拿对象键再 add（带 sourceKey）；覆盖行走 update（带 id，无封面不带对象键）', async () => {
    const wrapper = mountDialog();
    await chooseParentDir(wrapper, [
      makeCsv('cook.csv', '菜谱库/cook.csv', [row1, row2]),
      makeFile('番茄.png', '菜谱库/image/番茄.png'),
    ]);
    await wrapper.vm.startUpload();
    await flushPromises();

    expect(wrapper.vm.phase).toBe('done');
    // 行1：封面 → add（sourceKey + 对象键 + 分类 id）
    expect(uploadDiningRecipeCover).toHaveBeenCalledTimes(1);
    const coverForm = uploadDiningRecipeCover.mock.calls[0][0];
    expect(coverForm.get('file').name).toBe('番茄.png');
    // 封面上传先于该行的菜谱写入（并发下各自行内保序）
    expect(uploadDiningRecipeCover.mock.invocationCallOrder[0]).toBeLessThan(
      addDiningRecipe.mock.invocationCallOrder[0]
    );
    expect(addDiningRecipe).toHaveBeenCalledTimes(1);
    const addPayload = addDiningRecipe.mock.calls[0][0];
    expect(addPayload).toMatchObject({
      name: '番茄炒蛋',
      categoryId: 1,
      coverObjectKey: 'dining/covers/番茄.png',
      sourceKey: 'hoc_1',
      caloriesPer100g: 88.5,
    });
    // 行2：兜底命中 → update 带 id；无封面不带 coverObjectKey、无 key 不传 sourceKey
    // 分类「汤类」库内不存在 → 自动补建（sort 沿 主食(1) 递增），更新行挂重拉后的新分类 id
    expect(addDiningCategory).toHaveBeenCalledWith({ name: '汤类', sort: 2 });
    expect(updateDiningRecipe).toHaveBeenCalledTimes(1);
    const updatePayload = updateDiningRecipe.mock.calls[0][0];
    expect(updatePayload).toMatchObject({ id: 10, name: '紫菜蛋花汤', categoryId: 99 });
    expect(updatePayload.coverObjectKey).toBeUndefined();
    expect(updatePayload.sourceKey).toBeUndefined();
    // 行结果与汇总
    const rows = wrapper.vm.uploadTargets;
    expect(rows[0].result).toEqual({ ok: true, kind: 'create' });
    expect(rows[1].result).toEqual({ ok: true, kind: 'update' });
    expect(wrapper.emitted('success')).toHaveLength(1);
    wrapper.unmount();
  });

  it('分类不存在时自动补建并按重拉映射回填 categoryId', async () => {
    const wrapper = mountDialog();
    await chooseParentDir(wrapper, [
      makeCsv('new.csv', '菜谱库/new.csv', ['新分类,麻婆豆腐,豆腐,豆腐:300g,烧,,,99,,,,,,htc_ma']),
      makeFile('a.png', '菜谱库/image/a.png'),
    ]);
    // 预览汇总提示将自动创建的分类
    expect(wrapper.vm.missingCategories).toEqual(['新分类']);
    await wrapper.vm.startUpload();
    await flushPromises();

    expect(addDiningCategory).toHaveBeenCalledWith({ name: '新分类', sort: 2 });
    const addPayload = addDiningRecipe.mock.calls[0][0];
    expect(addPayload.categoryId).toBe(99); // 重拉后的映射 id
    expect(addPayload.sourceKey).toBe('htc_ma');
    expect(wrapper.vm.uploadTargets[0].result.ok).toBe(true);
    wrapper.unmount();
  });

  it('单行失败不阻断：封面失败行报错，其余行照常入库', async () => {
    uploadDiningRecipeCover.mockRejectedValue(new Error('封面上传失败'));
    const wrapper = mountDialog();
    await chooseParentDir(wrapper, [
      makeCsv('cook.csv', '菜谱库/cook.csv', [row1, row2]),
      makeFile('番茄.png', '菜谱库/image/番茄.png'),
    ]);
    await wrapper.vm.startUpload();
    await flushPromises();

    const rows = wrapper.vm.uploadTargets;
    expect(rows[0].result.ok).toBe(false);
    expect(rows[0].result.message).toContain('封面上传失败');
    expect(rows[1].result).toEqual({ ok: true, kind: 'update' }); // 行2 无封面不受影响
    expect(wrapper.vm.phase).toBe('done');
    wrapper.unmount();
  });

  it('覆盖上传前统一确认（useConfirm 收到覆盖清单），取消则不上传', async () => {
    const { useConfirm } = await import('@/composables/useConfirm');
    const wrapper = mountDialog();
    await chooseParentDir(wrapper, [
      makeCsv('cook.csv', '菜谱库/cook.csv', [row1, row2]),
      makeFile('番茄.png', '菜谱库/image/番茄.png'),
    ]);
    await wrapper.vm.startUpload();
    await flushPromises();
    expect(useConfirm).toHaveBeenCalledTimes(1);
    expect(useConfirm.mock.calls[0][0]).toContain('紫菜蛋花汤');

    // 取消覆盖确认：中止流程，无任何写入
    useConfirm.mockResolvedValueOnce(false);
    await chooseParentDir(wrapper, [
      makeCsv('cook.csv', '菜谱库/cook.csv', [row1, row2]),
      makeFile('番茄.png', '菜谱库/image/番茄.png'),
    ]);
    await wrapper.vm.startUpload();
    await flushPromises();
    expect(addDiningRecipe).toHaveBeenCalledTimes(1); // 仍是第一次的调用数
    expect(wrapper.vm.phase).toBe('preview');
    wrapper.unmount();
  });

  it('中止上传：在途行完成、未开始行标记「未上传（已中止）」', async () => {
    // 异步置中止：保证初始 3 个并发行全部先被拉起（同步置位会让 2、3 号 worker 取不到任务）
    addDiningRecipe.mockImplementation(
      () =>
        new Promise((resolve) =>
          setTimeout(() => {
            wrapper.vm.abortFlag = true;
            resolve({});
          }, 5)
        )
    );
    const wrapper = mountDialog();
    const rows4 = [
      '主食,A,蛋,,做法1,,',
      '主食,B,蛋,,做法2,,',
      '主食,C,蛋,,做法3,,',
      '主食,D,蛋,,做法4,,',
    ];
    await chooseParentDir(wrapper, [
      makeCsv('cook.csv', '菜谱库/cook.csv', rows4),
      makeFile('a.png', '菜谱库/image/a.png'),
    ]);
    expect(wrapper.vm.uploadTargets).toHaveLength(4);
    await wrapper.vm.startUpload();
    await flushPromises();

    const results = wrapper.vm.uploadTargets.map((it) => it.result);
    const okCount = results.filter((r) => r && r.ok).length;
    const aborted = results.filter((r) => r && !r.ok && r.message.includes('中止'));
    expect(okCount + aborted.length).toBe(4);
    expect(okCount).toBe(3); // 并发 3 的在途行全部完成
    expect(aborted).toHaveLength(1); // 未开始的行标记中止
    expect(wrapper.vm.phase).toBe('done');
    wrapper.unmount();
  });
});

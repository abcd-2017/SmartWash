// src/__tests__/RecipeImportDialog.test.js
// 菜谱批量导入对话框的单测：mock API 层，覆盖「上传解析 → 预览确认 → 结果报告」状态机
// 与「错误行不进入确认提交、整批回滚展示失败报告」两条关键交互契约。
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

vi.mock('@/api/dining', () => ({
  downloadDiningRecipeImportTemplate: vi.fn(() => Promise.resolve(new Blob(['xlsx']))),
  previewDiningRecipeImport: vi.fn(),
  confirmDiningRecipeImport: vi.fn(),
}));

vi.mock('@/utils/download', () => ({
  saveBlobAs: vi.fn(),
}));

import RecipeImportDialog from '@/views/dining/components/RecipeImportDialog.vue';
import { confirmDiningRecipeImport, previewDiningRecipeImport } from '@/api/dining';

// Element Plus 组件 stub：保留结构语义（按钮可点、弹窗受控），去掉样式/定位等 DOM 依赖
const globalStubs = {
  'el-dialog': {
    props: ['modelValue', 'title', 'width'],
    template: '<div v-if="modelValue"><slot /></div>',
  },
  'el-alert': {
    props: ['type', 'title'],
    template: '<div class="alert">{{ title }}</div>',
  },
  'el-button': {
    props: ['type', 'loading', 'size', 'disabled'],
    emits: ['click'], // 声明后 onClick 不再 fallthrough 到根元素，避免与模板内 emit 双触发
    template: '<button :data-disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
  },
  'el-upload': {
    template: '<div class="upload"><slot /><slot name="tip" /></div>',
    methods: { handleStart: () => {}, handleRemove: () => {}, submit: () => {} },
  },
  'el-icon': { template: '<i />' },
  'el-tag': {
    props: ['type', 'size', 'effect'],
    template: '<span class="tag"><slot /></span>',
  },
  // 表格内容依赖 scoped slot 传 row，stub 无法完整模拟——状态断言走 wrapper.vm，不做表格 DOM 断言
  'el-table': { props: ['data'], template: '<div class="table" />' },
  'el-table-column': { props: ['prop', 'label'], template: '<span />' },
  'el-tooltip': {
    props: ['content'],
    template: '<span class="tooltip"><slot /></span>',
  },
};

function mountDialog(visible = true) {
  return mount(RecipeImportDialog, {
    props: { modelValue: visible },
    global: { stubs: globalStubs },
  });
}

// 直接驱动内部文件状态（el-upload 已 stub，on-change 无法从 DOM 触发）
function selectFile(wrapper, fileName) {
  // on-change 由 el-upload 内部回调持有，这里直接模拟用户选好文件后的组件状态
  wrapper.vm.selectedFile = { name: fileName, size: 1024 };
  wrapper.vm.fileName = fileName;
}

describe('RecipeImportDialog.vue 菜谱批量导入', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('未选择文件时解析按钮禁用，选文件后启用并调用预览接口', async () => {
    previewDiningRecipeImport.mockResolvedValue({
      total: 2,
      validCount: 1,
      errorCount: 1,
      rows: [
        {
          rowNo: 2,
          categoryName: null,
          name: '番茄炒蛋',
          ingredients: ['西红柿'],
          steps: '',
          errors: [],
        },
        {
          rowNo: 3,
          categoryName: '幽灵分类',
          name: '幽灵菜',
          ingredients: [],
          steps: '',
          errors: ['分类「幽灵分类」不存在'],
        },
      ],
    });
    const wrapper = mountDialog();

    const parseButton = wrapper.findAll('button').find((b) => b.text().includes('解析预览'));
    expect(parseButton.attributes('data-disabled')).toBe('true');

    selectFile(wrapper, 'recipes.csv');
    await wrapper.vm.$nextTick();
    expect(parseButton.attributes('data-disabled')).toBe('false');

    await parseButton.trigger('click');
    await flushPromises();

    expect(previewDiningRecipeImport).toHaveBeenCalledTimes(1);
    const formData = previewDiningRecipeImport.mock.calls[0][0];
    expect(formData).toBeInstanceOf(FormData);
    expect(formData.get('file')).toBeTruthy();
    expect(wrapper.vm.stage).toBe('preview');
  });

  it('预览态确认提交只携带通过校验的行（错误行剔除，不传 errors/coverUrl）', async () => {
    previewDiningRecipeImport.mockResolvedValue({
      total: 2,
      validCount: 1,
      errorCount: 1,
      rows: [
        {
          rowNo: 2,
          categoryName: '汤羹',
          name: '番茄蛋汤',
          ingredients: ['西红柿', '鸡蛋'],
          steps: '冲汤',
          coverUrl: 'https://img.example.com/a.png',
          errors: [],
        },
        {
          rowNo: 3,
          categoryName: '幽灵分类',
          name: '幽灵菜',
          ingredients: [],
          steps: '',
          errors: ['分类「幽灵分类」不存在'],
        },
      ],
    });
    confirmDiningRecipeImport.mockResolvedValue({
      total: 1,
      successCount: 1,
      failedCount: 0,
      rolledBack: false,
      rows: [{ rowNo: 2, name: '番茄蛋汤', success: true, error: null, recipeId: 11 }],
    });
    const wrapper = mountDialog();
    selectFile(wrapper, 'recipes.csv');

    await wrapper.vm.handleParse();
    await flushPromises();

    await wrapper.vm.handleSubmit();
    await flushPromises();

    expect(confirmDiningRecipeImport).toHaveBeenCalledTimes(1);
    const payload = confirmDiningRecipeImport.mock.calls[0][0];
    expect(payload.rows).toHaveLength(1, '错误行不得进入确认提交');
    expect(payload.rows[0]).toEqual({
      rowNo: 2,
      categoryName: '汤羹',
      name: '番茄蛋汤',
      ingredients: ['西红柿', '鸡蛋'],
      steps: '冲汤',
      // v2 新列（契约 §3.2）：预览行未携带时归一为 null，不传封面链接（封面不随批量导入落库）
      caloriesPer100g: null,
      caloriesTotal: null,
      proteinG: null,
      fatG: null,
      carbsG: null,
      sodiumMg: null,
    });
    expect(payload.rows[0].coverUrl).toBeUndefined();
    expect(wrapper.vm.stage).toBe('result');
  });

  it('v2 行：warnings 不阻断提交，食材明细/封面对象键/营养 6 列随确认行回传', async () => {
    previewDiningRecipeImport.mockResolvedValue({
      total: 1,
      validCount: 1,
      errorCount: 0,
      rows: [
        {
          rowNo: 2,
          categoryName: '新分类',
          name: '西红柿炒鸡蛋',
          ingredients: ['西红柿', '鸡蛋'],
          ingredientDetails: [
            { name: '鸡蛋', amountValue: 575, amountUnit: 'g' },
            { name: '西红柿', amountValue: 1800, amountUnit: 'g' },
            { name: '盐', amountValue: null, amountUnit: null },
          ],
          steps: '',
          coverObjectKey: 'dining/covers/tomato-egg.png',
          caloriesPer100g: 472,
          caloriesTotal: null,
          proteinG: 12.1,
          fatG: 8.5,
          carbsG: 3.2,
          sodiumMg: 560,
          errors: [],
          warnings: [
            '分类「新分类」不存在，确认导入时自动创建',
            '封面对象键 dining/covers/tomato-egg.png 校验通过',
          ],
        },
      ],
    });
    confirmDiningRecipeImport.mockResolvedValue({
      total: 1,
      successCount: 1,
      failedCount: 0,
      rolledBack: false,
      rows: [{ rowNo: 2, name: '西红柿炒鸡蛋', success: true, error: null, recipeId: 21 }],
    });
    const wrapper = mountDialog();
    selectFile(wrapper, 'recipes-v2.csv');
    await wrapper.vm.handleParse();
    await flushPromises();
    await wrapper.vm.handleSubmit();
    await flushPromises();

    expect(confirmDiningRecipeImport).toHaveBeenCalledTimes(1);
    const payload = confirmDiningRecipeImport.mock.calls[0][0];
    expect(payload.rows).toHaveLength(1, '仅带 warnings 的行不得被误当错误行剔除');
    expect(payload.rows[0]).toEqual({
      rowNo: 2,
      categoryName: '新分类',
      name: '西红柿炒鸡蛋',
      ingredients: ['西红柿', '鸡蛋'],
      ingredientDetails: [
        { name: '鸡蛋', amountValue: 575, amountUnit: 'g' },
        { name: '西红柿', amountValue: 1800, amountUnit: 'g' },
        { name: '盐', amountValue: null, amountUnit: null },
      ],
      steps: '',
      coverObjectKey: 'dining/covers/tomato-egg.png',
      caloriesPer100g: 472,
      caloriesTotal: null,
      proteinG: 12.1,
      fatG: 8.5,
      carbsG: 3.2,
      sodiumMg: 560,
    });
    expect(wrapper.emitted('success')).toHaveLength(1);
  });

  it('v2 预览展示辅助：食材明细拼 `名称:数量单位`、营养 6 列紧凑汇总、全空回落空串', () => {
    const wrapper = mountDialog(false);

    expect(
      wrapper.vm.ingredientDetailText({
        ingredientDetails: [
          { name: '鸡蛋', amountValue: 575, amountUnit: 'g' },
          { name: '西红柿', amountValue: 1800, amountUnit: 'g' },
          { name: '盐', amountValue: null, amountUnit: null },
          { name: '料酒', amountValue: null, amountUnit: '勺' },
        ],
      })
    ).toBe('鸡蛋:575g、西红柿:1800g、盐、料酒:勺');
    expect(wrapper.vm.ingredientDetailText({ ingredientDetails: undefined })).toBe('');
    expect(
      wrapper.vm.nutritionSummaryText({
        caloriesPer100g: 472,
        caloriesTotal: 1051,
        proteinG: 12.1,
        fatG: 8.5,
        carbsG: 3.2,
        sodiumMg: 560,
      })
    ).toBe('热量472kcal/100g · 整份1051kcal · 蛋白12.1g · 脂肪8.5g · 碳水3.2g · 钠560mg');
    expect(wrapper.vm.nutritionSummaryText({ caloriesPer100g: null, sodiumMg: undefined })).toBe(
      ''
    );
  });

  it('整批回滚时结果态展示失败告警与逐行原因，不触发 success 刷新', async () => {
    previewDiningRecipeImport.mockResolvedValue({
      total: 1,
      validCount: 1,
      errorCount: 0,
      rows: [
        {
          rowNo: 2,
          categoryName: '汤羹',
          name: '番茄蛋汤',
          ingredients: ['西红柿'],
          steps: '',
          errors: [],
        },
      ],
    });
    confirmDiningRecipeImport.mockResolvedValue({
      total: 1,
      successCount: 0,
      failedCount: 1,
      rolledBack: true,
      rows: [
        {
          rowNo: 2,
          name: '番茄蛋汤',
          success: false,
          error: '分类「汤羹」不存在（请先在分类管理页创建）',
          recipeId: null,
        },
      ],
    });
    const wrapper = mountDialog();
    selectFile(wrapper, 'recipes.csv');
    await wrapper.vm.handleParse();
    await flushPromises();
    await wrapper.vm.handleSubmit();
    await flushPromises();

    expect(wrapper.vm.result.rolledBack).toBe(true);
    expect(wrapper.vm.result.failedCount).toBe(1);
    expect(wrapper.vm.stage).toBe('result');
    expect(wrapper.emitted('success')).toBeUndefined();
  });

  it('导入成功触发 success 事件供父页面刷新列表', async () => {
    previewDiningRecipeImport.mockResolvedValue({
      total: 1,
      validCount: 1,
      errorCount: 0,
      rows: [
        {
          rowNo: 2,
          categoryName: null,
          name: '凉拌黄瓜',
          ingredients: ['黄瓜'],
          steps: '',
          errors: [],
        },
      ],
    });
    confirmDiningRecipeImport.mockResolvedValue({
      total: 1,
      successCount: 1,
      failedCount: 0,
      rolledBack: false,
      rows: [{ rowNo: 2, name: '凉拌黄瓜', success: true, error: null, recipeId: 12 }],
    });
    const wrapper = mountDialog();
    selectFile(wrapper, 'recipes.csv');
    await wrapper.vm.handleParse();
    await flushPromises();
    await wrapper.vm.handleSubmit();
    await flushPromises();

    expect(wrapper.emitted('success')).toHaveLength(1);
  });
});

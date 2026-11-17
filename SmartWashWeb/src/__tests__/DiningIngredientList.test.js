// src/__tests__/DiningIngredientList.test.js
// 配料库管理页单测：mock API 层与 element-plus（对齐 useTableList.test.js 的轻量 mock 先例），
// 覆盖「分页拉取参数清洗、新增/编辑提交（RESTful PUT）、删除确认、被引用删除透出后端 message」四条契约。
import { describe, it, expect, beforeEach, beforeAll, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

vi.mock('@/api/dining', () => ({
  getDiningIngredientList: vi.fn(() => Promise.resolve({ records: [], total: 0 })),
  addDiningIngredient: vi.fn(() => Promise.resolve('添加成功')),
  updateDiningIngredient: vi.fn(() => Promise.resolve('修改成功')),
  deleteDiningIngredient: vi.fn(() => Promise.resolve(true)),
}));

// element-plus 部分 mock：保留真实组件（vitest 下 unplugin-vue-components 会把模板里的
// el-* 编译成具名导入，全量 mock 会缺 ElInput 等导出），仅替换 ElMessage/ElMessageBox 便于断言
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    // useConfirm 消费 ElMessageBox.confirm：默认 resolve（即用户点了「确认」）
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  };
});

import DiningIngredientList from '@/views/dining/DiningIngredientList.vue';
import {
  getDiningIngredientList,
  addDiningIngredient,
  updateDiningIngredient,
  deleteDiningIngredient,
} from '@/api/dining';
import { ElMessage } from 'element-plus';

// Element Plus 组件 stub：保留结构语义（按钮可点、弹窗受控、表单校验直接通过），去掉样式/DOM 依赖
const globalStubs = {
  'el-form': {
    template: '<form><slot /></form>',
    methods: {
      validate: () => Promise.resolve(true),
      resetFields: () => {},
      clearValidate: () => {},
    },
  },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div />' },
  'el-input-number': { template: '<input />' },
  'el-button': {
    props: ['type', 'loading', 'size', 'disabled'],
    emits: ['click'], // 声明后 onClick 不再 fallthrough 到根元素，避免与模板内 emit 双触发
    template: '<button :data-disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
  },
  'el-dialog': {
    props: ['modelValue', 'title', 'width'],
    template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
  },
  'el-table': { props: ['data'], template: '<div class="table"><slot /></div>' },
  'el-table-column': { props: ['prop', 'label'], template: '<span />' },
  'el-pagination': { template: '<div />' },
  'el-tag': {
    props: ['type', 'size', 'effect'],
    template: '<span class="tag"><slot /></span>',
  },
};

function mountPage() {
  return mount(DiningIngredientList, {
    global: { stubs: globalStubs },
  });
}

describe('DiningIngredientList.vue 配料库管理', () => {
  beforeAll(() => {
    // happy-dom 未实现滚动 API，列表刷新（翻页/删除后）会调用
    Element.prototype.scrollIntoView = () => {};
    window.matchMedia = window.matchMedia || ((q) => ({ matches: false, media: q }));
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('挂载即分页拉取配料列表，空筛选参数清洗为 undefined', async () => {
    getDiningIngredientList.mockResolvedValue({
      records: [{ id: 1, name: '鸡蛋', category: 'MAIN', createdAt: '2026-11-17T10:00:00' }],
      total: 1,
    });
    const wrapper = mountPage();
    await flushPromises();

    expect(getDiningIngredientList).toHaveBeenCalledTimes(1);
    expect(getDiningIngredientList.mock.calls[0][0]).toEqual({ page: 1, size: 10 });
    expect(wrapper.vm.ingredientList).toHaveLength(1);
    expect(wrapper.vm.total).toBe(1);
  });

  it('新增配料：校验通过后调用 addDiningIngredient 并提示成功', async () => {
    const wrapper = mountPage();
    await flushPromises();

    wrapper.vm.handleCreate();
    expect(wrapper.vm.dialogVisible).toBe(true);
    // 弹窗 v-if 挂载后模板 ref 才绑定 formRef，需等一拍再提交
    await wrapper.vm.$nextTick();
    wrapper.vm.tempIngredient.name = '鸡蛋';
    wrapper.vm.tempIngredient.category = 'MAIN';
    await wrapper.vm.submitForm();
    await flushPromises();

    expect(addDiningIngredient).toHaveBeenCalledTimes(1);
    expect(addDiningIngredient).toHaveBeenCalledWith({ name: '鸡蛋', category: 'MAIN' });
    expect(ElMessage.success).toHaveBeenCalledWith('新增成功');
    expect(getDiningIngredientList).toHaveBeenCalledTimes(2);
  });

  it('表单校验未通过：拦截提交，不调接口也不弹「操作失败」', async () => {
    const wrapper = mountPage();
    await flushPromises();

    wrapper.vm.handleCreate();
    await wrapper.vm.$nextTick();
    wrapper.vm.formRef.validate = () => Promise.reject({ name: ['请输入配料名称'] });
    await wrapper.vm.submitForm();
    await flushPromises();

    expect(addDiningIngredient).not.toHaveBeenCalled();
    expect(ElMessage.error).not.toHaveBeenCalled();
  });

  it('编辑配料：RESTful PUT 携带路径 id 与改名/改分类字段', async () => {
    const wrapper = mountPage();
    await flushPromises();

    wrapper.vm.handleEdit({ id: 3, name: '生抽', category: 'SEASONING' });
    expect(wrapper.vm.dialogVisible).toBe(true);
    await wrapper.vm.$nextTick();
    wrapper.vm.tempIngredient.name = '老抽';
    await wrapper.vm.submitForm();
    await flushPromises();

    expect(updateDiningIngredient).toHaveBeenCalledTimes(1);
    expect(updateDiningIngredient.mock.calls[0][0]).toBe(3);
    expect(updateDiningIngredient.mock.calls[0][1]).toEqual({ name: '老抽', category: 'SEASONING' });
    expect(ElMessage.success).toHaveBeenCalledWith('修改成功');
    expect(addDiningIngredient).not.toHaveBeenCalled();
  });

  it('删除配料：确认后调用删除接口并按页码钳制刷新', async () => {
    getDiningIngredientList.mockResolvedValue({ records: [], total: 0 });
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.vm.handleDelete({ id: 3, name: '生抽', category: 'SEASONING' });
    await flushPromises();

    expect(deleteDiningIngredient).toHaveBeenCalledTimes(1);
    expect(deleteDiningIngredient).toHaveBeenCalledWith(3);
    expect(ElMessage.success).toHaveBeenCalledWith('删除成功');
    expect(getDiningIngredientList).toHaveBeenCalledTimes(2);
  });

  it('删除被引用配料：透出后端 409 业务 message，不弹成功', async () => {
    deleteDiningIngredient.mockRejectedValueOnce(
      new Error('配料「鸡蛋」被 3 个菜谱引用，无法删除')
    );
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.vm.handleDelete({ id: 5, name: '鸡蛋', category: 'MAIN' });
    await flushPromises();

    expect(ElMessage.error).toHaveBeenCalledWith('配料「鸡蛋」被 3 个菜谱引用，无法删除');
    expect(ElMessage.success).not.toHaveBeenCalled();
  });
});

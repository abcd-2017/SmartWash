import { describe, it, expect, beforeEach, beforeAll, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// API 层全部 mock，组件测试关注交互与参数组装
vi.mock('@/api/locker', () => ({
  getLockerStatus: vi.fn(() => Promise.resolve({ 0: '空闲', 1: '使用中', 2: '故障' })),
  getLockerList: vi.fn(() => Promise.resolve({ records: [], total: 0 })),
  addLocker: vi.fn(() => Promise.resolve()),
  updateLocker: vi.fn(() => Promise.resolve()),
  deleteLocker: vi.fn(() => Promise.resolve()),
  batchAddLocker: vi.fn(() =>
    Promise.resolve({ addedCount: 100, firstNumber: 101, lastNumber: 200 })
  ),
}));

vi.mock('@/utils/optionCache', () => ({
  schoolOptionsCache: {
    load: vi.fn(() => Promise.resolve([{ schoolId: 1, schoolName: '测试学校' }])),
    invalidate: vi.fn(),
  },
}));

import LockerList from '@/views/campus/LockerList.vue';
import { batchAddLocker } from '@/api/locker';

// Element Plus 组件 stub：保留结构语义（按钮可点、弹窗受控），去掉样式/定位等 DOM 依赖
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
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div />' },
  'el-input': { template: '<input />' },
  'el-input-number': { template: '<input />' },
  'el-button': {
    props: ['type', 'loading', 'size'],
    template: '<button @click="$emit(\'click\')"><slot /></button>',
  },
  'el-dialog': {
    props: ['modelValue', 'title', 'width'],
    template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
  },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-pagination': { template: '<div />' },
  'el-date-picker': { template: '<input />' },
  'el-tag': { template: '<span><slot /></span>' },
};

function mountPage() {
  return mount(LockerList, { global: { stubs: globalStubs } });
}

describe('LockerList.vue 批量新增寄存柜', () => {
  beforeAll(() => {
    // happy-dom 未实现滚动 API，列表刷新会调用
    Element.prototype.scrollIntoView = () => {};
    window.matchMedia = window.matchMedia || ((q) => ({ matches: false, media: q }));
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('渲染「批量新增」按钮', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const texts = wrapper.findAll('button').map((b) => b.text());
    expect(texts.some((t) => t.includes('批量新增'))).toBe(true);
  });

  it('点击「批量新增」打开批量弹窗', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const btn = wrapper.findAll('button').find((b) => b.text().includes('批量新增'));
    await btn.trigger('click');
    expect(wrapper.vm.batchDialogVisible).toBe(true);
  });

  it('提交批量新增：按表单参数调用 batchAddLocker', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('批量新增'))
      .trigger('click');

    wrapper.vm.tempBatchLocker.schoolId = 1;
    wrapper.vm.tempBatchLocker.startNumber = 101;
    wrapper.vm.tempBatchLocker.count = 100;
    await wrapper.vm.submitBatchForm();
    await flushPromises();

    expect(batchAddLocker).toHaveBeenCalledTimes(1);
    expect(batchAddLocker).toHaveBeenCalledWith({
      schoolId: 1,
      startNumber: 101,
      count: 100,
    });
  });
});

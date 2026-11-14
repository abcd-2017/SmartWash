// src/__tests__/BatchUploadDialog.test.js
// 批量导入游戏对话框的组件级端到端测试（mock API 层）：覆盖「选父目录 → 预检分组 → 关卡列预览
//（覆盖/新增分布 + 列头全选）→ 串行上传 → 关卡跟随导入」完整状态机，以及不携带关卡/解析失败/
// 目录不合规等分支。本机无后端栈，以组件级验证替代浏览器走查（范式同 RecipeImportDialog.test.js）。
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

vi.mock('@/api/game', () => ({
  getGameList: vi.fn(),
  uploadGame: vi.fn(),
  uploadGameCover: vi.fn(),
}));

vi.mock('@/api/gameData', () => ({
  getGameLevels: vi.fn(),
  batchImportGameLevels: vi.fn(),
}));

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: vi.fn(() => Promise.resolve(true)),
}));

import BatchUploadDialog from '@/views/platform/game/components/BatchUploadDialog.vue';
import { getGameList, uploadGame, uploadGameCover } from '@/api/game';
import { getGameLevels, batchImportGameLevels } from '@/api/gameData';

/** 构造带 webkitRelativePath 的伪目录文件（浏览器 webkitdirectory 选择行为的测试替身） */
const makeFile = (name, relPath, content = 'x') => {
  const file = new File([content], name);
  Object.defineProperty(file, 'webkitRelativePath', { value: relPath });
  return file;
};

/** Element Plus 组件 stub：保留结构语义，去掉样式/定位等 DOM 依赖 */
const globalStubs = {
  'el-dialog': {
    props: ['modelValue', 'title', 'width'],
    template: '<div v-if="modelValue" class="dialog"><slot /></div>',
  },
  'el-button': {
    props: ['type', 'disabled', 'loading'],
    template:
      '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>',
  },
  'el-table': { template: '<table><slot /></table>' },
  'el-table-column': { template: '<div class="col"><slot name="header" /></div>' },
  'el-tag': {
    props: ['type', 'size', 'effect'],
    template: '<span class="tag" :data-type="type"><slot /></span>',
  },
  'el-checkbox': {
    props: ['modelValue', 'indeterminate', 'disabled', 'ariaLabel'],
    emits: ['update:modelValue', 'change'],
    template: '<input type="checkbox" :checked="modelValue" @change="$emit(\'change\', $event.target.checked)" />',
  },
  'el-tooltip': { template: '<span><slot /></span>' },
  'el-progress': {
    props: ['percentage', 'strokeWidth'],
    template: '<div class="progress" />',
  },
};

/** 组装一次「选择父目录」事件：files 直接走 handleDirChosen 入参 */
const chooseParentDir = async (wrapper, files) => {
  await wrapper.vm.handleDirChosen({ target: { files, value: '' } });
  await flushPromises();
};

beforeEach(() => {
  vi.clearAllMocks();
  // 覆盖预检：库内已有 maze（覆盖场景）；分页循环一页即止
  getGameList.mockResolvedValue({ records: [{ gameKey: 'maze' }], total: 1 });
  uploadGame.mockResolvedValue({ id: 101, url: 'http://x/maze/index.html' });
  uploadGameCover.mockResolvedValue('http://cover/maze.png');
  // maze 库内已有第 1、2 关（信封显式序号撞号 → 覆盖）
  getGameLevels.mockResolvedValue([
    { levelNo: 1, name: 'old1' },
    { levelNo: 2, name: 'old2' },
  ]);
  batchImportGameLevels.mockImplementation((payload) =>
    Promise.resolve({ total: payload.levels.length, created: payload.levels.length, updated: 0 })
  );
});

describe('BatchUploadDialog 预检（关卡列预览）', () => {
  it('四个目录分组正确：覆盖分布 / 全新顺延 / 不合规跳过 / 未携带', async () => {
    const wrapper = mount(BatchUploadDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs },
    });
    await chooseParentDir(wrapper, [
      makeFile('index.html', 'parent/maze/index.html', '<html></html>'),
      makeFile('levels.json', 'parent/maze/levels.json', '[{"levelNo":1,"data":"x"},{"levelNo":2,"data":"y"},"z"]'),
      makeFile('index.html', 'parent/beat-tap/index.html', '<html></html>'),
      makeFile('levels.json', 'parent/beat-tap/levels.json', '["a","b","c"]'),
      makeFile('index.html', 'parent/snake/index.html', '<html></html>'),
      makeFile('index.html', 'parent/bad/index.html', '<html></html>'),
      makeFile('notes.txt', 'parent/bad/notes.txt', 'oops'),
    ]);

    expect(wrapper.vm.queue).toHaveLength(4);
    const byDir = Object.fromEntries(wrapper.vm.queue.map((it) => [it.dirName, it]));

    // maze：库内已存在 → 覆盖更新；信封 1、2 撞号 → 覆盖 2，裸数据顺延第 3 关 → 新增 1
    expect(byDir.maze.skipReason).toBe('');
    expect(byDir.maze.willOverwrite).toBe(true);
    expect(byDir.maze.levels.plan.updated).toBe(2);
    expect(byDir.maze.levels.plan.created).toBe(1);
    expect(byDir.maze.levels.error).toBe('');
    expect(getGameLevels).toHaveBeenCalledWith('maze');

    // beat-tap：全新游戏 → 裸数据 3 关全部新增，无需拉现有关卡
    expect(byDir['beat-tap'].willOverwrite).toBe(false);
    expect(byDir['beat-tap'].levels.plan.created).toBe(3);
    expect(byDir['beat-tap'].levels.plan.updated).toBe(0);
    expect(getGameLevels).not.toHaveBeenCalledWith('beat-tap');

    // snake：未携带 levels.json → 无处置计划，列内展示「未携带」
    expect(byDir.snake.levels.items).toBeNull();
    expect(byDir.snake.levels.plan).toBeNull();

    // bad：白名单外文件 → 整目录跳过（关卡列展示「—」）
    expect(byDir.bad.skipReason).toContain('notes.txt');

    wrapper.unmount();
  });

  it('levels.json 解析失败不跳过目录，仅标记关卡文件异常且不给处置计划', async () => {
    const wrapper = mount(BatchUploadDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs },
    });
    await chooseParentDir(wrapper, [
      makeFile('index.html', 'parent/typo/index.html'),
      makeFile('levels.json', 'parent/typo/levels.json', 'not-json{{'),
    ]);

    const item = wrapper.vm.queue[0];
    expect(item.skipReason).toBe('');
    expect(item.levels.error).toContain('JSON 解析失败');
    expect(item.levels.plan).toBeNull();
    // 解析失败行不参与全选
    expect(wrapper.vm.importableRows).toHaveLength(0);
    wrapper.unmount();
  });

  it('列头全选：默认全选、可整批取消、半选态正确', async () => {
    const wrapper = mount(BatchUploadDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs },
    });
    await chooseParentDir(wrapper, [
      makeFile('index.html', 'parent/maze/index.html'),
      makeFile('levels.json', 'parent/maze/levels.json', '["a"]'),
      makeFile('index.html', 'parent/beat-tap/index.html'),
      makeFile('levels.json', 'parent/beat-tap/levels.json', '["b"]'),
    ]);

    expect(wrapper.vm.importableRows).toHaveLength(2);
    expect(wrapper.vm.allLevelsChecked).toBe(true);
    expect(wrapper.vm.someLevelsChecked).toBe(false);

    // 整批取消 → 全不选；单行勾选 → 半选态
    wrapper.vm.toggleAllLevels(false);
    expect(wrapper.vm.allLevelsChecked).toBe(false);
    expect(wrapper.vm.importableRows.every((it) => !it.levels.checked)).toBe(true);
    wrapper.vm.queue[0].levels.checked = true;
    expect(wrapper.vm.someLevelsChecked).toBe(true);
    wrapper.unmount();
  });
});

describe('BatchUploadDialog 上传（关卡跟随导入）', () => {
  it('勾选行先游戏后关卡、按序号对齐提交；未勾选行只传游戏不导关卡', async () => {
    const wrapper = mount(BatchUploadDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs },
    });
    await chooseParentDir(wrapper, [
      makeFile('index.html', 'parent/maze/index.html'),
      makeFile('levels.json', 'parent/maze/levels.json', '[{"levelNo":1,"data":"x"},{"levelNo":2,"data":"y"},"z"]'),
      makeFile('index.html', 'parent/beat-tap/index.html'),
      makeFile('levels.json', 'parent/beat-tap/levels.json', '["a","b","c"]'),
      makeFile('index.html', 'parent/snake/index.html'),
    ]);

    // beat-tap 取消勾选：只传游戏包，不带关卡
    const byDir = Object.fromEntries(wrapper.vm.queue.map((it) => [it.dirName, it]));
    byDir['beat-tap'].levels.checked = false;

    await wrapper.vm.startUpload();
    await flushPromises();

    // 两个游戏都上传（snake 也在列，共 3 个目标），cover 无则不调
    expect(uploadGame).toHaveBeenCalledTimes(3);
    const formKeys = uploadGame.mock.calls.map(([fd]) => fd.get('gameKey'));
    expect(formKeys).toEqual(['maze', 'beat-tap', 'snake']);
    expect(uploadGameCover).not.toHaveBeenCalled();

    // 关卡导入只发生一次（maze）：按序号对齐 3 关、replaceAll=false（覆盖时保留多余关卡）
    expect(batchImportGameLevels).toHaveBeenCalledTimes(1);
    const payload = batchImportGameLevels.mock.calls[0][0];
    expect(payload.gameKey).toBe('maze');
    expect(payload.replaceAll).toBe(false);
    expect(payload.levels.map((l) => l.levelNo)).toEqual([1, 2, 3]);
    expect(payload.levels.filter((l) => l.dataJson === '"z"')).toHaveLength(1);

    // 行内结果：maze 关卡已导入；beat-tap 未勾选 → 未导入；snake 未携带
    const after = Object.fromEntries(wrapper.vm.queue.map((it) => [it.dirName, it]));
    expect(after.maze.result.ok).toBe(true);
    expect(after.maze.levels.result).toEqual({ ok: true, created: 3, updated: 0 });
    expect(after['beat-tap'].result.ok).toBe(true);
    expect(after['beat-tap'].levels.result).toBeNull();
    expect(after.snake.levels.result).toBeNull();
    expect(wrapper.vm.phase).toBe('done');
    wrapper.unmount();
  });

  it('关卡导入失败不回滚游戏上传，逐行独立标记可重试', async () => {
    batchImportGameLevels.mockRejectedValue(new Error('第 1 关：关卡数据不是合法 JSON'));
    const wrapper = mount(BatchUploadDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs },
    });
    await chooseParentDir(wrapper, [
      makeFile('index.html', 'parent/maze/index.html'),
      makeFile('levels.json', 'parent/maze/levels.json', '["a"]'),
    ]);

    await wrapper.vm.startUpload();
    await flushPromises();

    const item = wrapper.vm.queue[0];
    expect(item.result.ok).toBe(true); // 游戏本体入库成功
    expect(item.levels.result.ok).toBe(false);
    expect(item.levels.result.message).toContain('不是合法 JSON');
    wrapper.unmount();
  });

  it('覆盖上传前统一确认（useConfirm 收到覆盖清单）', async () => {
    const { useConfirm } = await import('@/composables/useConfirm');
    const wrapper = mount(BatchUploadDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs },
    });
    await chooseParentDir(wrapper, [
      makeFile('index.html', 'parent/maze/index.html'),
      makeFile('levels.json', 'parent/maze/levels.json', '["a"]'),
    ]);

    await wrapper.vm.startUpload();
    await flushPromises();

    expect(useConfirm).toHaveBeenCalledTimes(1);
    expect(useConfirm.mock.calls[0][0]).toContain('maze');
    wrapper.unmount();
  });
});

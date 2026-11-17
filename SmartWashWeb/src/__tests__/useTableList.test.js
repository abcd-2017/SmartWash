// src/__tests__/useTableList.test.js
// useTableList 新增 listLoadError 状态的本地验证（按 shared-rules：新增测试文件不入库，仅本地运行）
// 覆盖：失败置文案 + 成功清空 + 竞态守卫下旧失败不覆盖新状态
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
}));

import { ElMessage } from 'element-plus';
import { useTableList } from '@/composables/useTableList';

describe('useTableList listLoadError', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('拉取失败置错误文案并弹提示，随后成功清空', async () => {
    const fetchApi = vi
      .fn()
      .mockRejectedValueOnce(new Error('服务不可用'))
      .mockResolvedValueOnce({ records: [{ id: 1 }], total: 1 });
    const { list, listLoadError, fetchData } = useTableList({ fetchApi });

    await fetchData();
    expect(listLoadError.value).toBe('服务不可用');
    expect(ElMessage.error).toHaveBeenCalled();

    // 重试发起即清错误态：错误占位立刻让位给表格加载反馈
    const retry = fetchData();
    expect(listLoadError.value).toBe('');
    await retry;
    expect(listLoadError.value).toBe('');
    expect(list.value).toEqual([{ id: 1 }]);
  });

  it('竞态守卫：过期请求的失败不覆盖新一轮状态、不弹提示', async () => {
    let rejectOld;
    const stale = new Promise((_, reject) => {
      rejectOld = reject;
    });
    const fetchApi = vi
      .fn()
      .mockReturnValueOnce(stale)
      .mockResolvedValue({ records: [], total: 0 });
    const { listLoadError, fetchData } = useTableList({ fetchApi });

    const first = fetchData();
    await fetchData(); // 第二轮先完成，fetchSeq 前进
    expect(listLoadError.value).toBe('');

    rejectOld(new Error('过期失败'));
    await first;
    expect(listLoadError.value).toBe('');
    expect(ElMessage.error).not.toHaveBeenCalled();
  });
});

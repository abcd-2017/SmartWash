import { describe, it, expect, beforeEach, vi } from 'vitest';

// API 层只负责「组装请求」，http 封装本身已由 http.test.js 覆盖，这里 mock 掉
vi.mock('@/utils/http', () => ({ default: vi.fn() }));

import request from '@/utils/http';
import { getShortCodeList, getShortCodeDetail, deleteShortCode } from '@/api/toolbox';

describe('toolbox.js 短链管理 API', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('getShortCodeList 以 GET 请求 /admin/toolbox/short-codes 并透传分页与筛选参数', async () => {
    const params = {
      page: 2,
      size: 20,
      code: 'Ab3x9K2',
      ownerPhone: '13800000000',
      contentType: 1,
    };
    await getShortCodeList(params);

    expect(request).toHaveBeenCalledTimes(1);
    expect(request).toHaveBeenCalledWith({
      url: '/admin/toolbox/short-codes',
      method: 'get',
      params,
    });
  });

  it('getShortCodeDetail 以 GET 请求 /admin/toolbox/short-codes/{id}', async () => {
    await getShortCodeDetail(42);

    expect(request).toHaveBeenCalledTimes(1);
    expect(request).toHaveBeenCalledWith({
      url: '/admin/toolbox/short-codes/42',
      method: 'get',
    });
  });

  it('deleteShortCode 以 DELETE 请求 /admin/toolbox/short-codes/{id}', async () => {
    await deleteShortCode(42);

    expect(request).toHaveBeenCalledTimes(1);
    expect(request).toHaveBeenCalledWith({
      url: '/admin/toolbox/short-codes/42',
      method: 'delete',
    });
  });
});

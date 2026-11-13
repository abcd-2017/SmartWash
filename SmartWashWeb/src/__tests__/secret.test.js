import { describe, it, expect, beforeEach, vi } from 'vitest';

// API 层只负责「组装请求」；envelopeHttp 封装本身已由 http.test.js 覆盖，这里 mock 掉
// request 给默认兑现值（完整信封），API 层会解包 res.data
vi.mock('@/utils/http', () => ({
  envelopeHttp: { request: vi.fn(async () => ({ data: { code: 200, message: 'ok', data: null } })) },
}));

import { envelopeHttp } from '@/utils/http';
import { SECRET_CODE, getSecretMeta, viewSecret } from '@/api/secret';

describe('secret.js 阅后即焚公开 API', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('getSecretMeta 以 GET 请求 /web/s/{code}/meta 并对码做 URL 编码', async () => {
    await getSecretMeta('sK4/x 9');

    expect(envelopeHttp.request).toHaveBeenCalledTimes(1);
    expect(envelopeHttp.request).toHaveBeenCalledWith({
      url: '/web/s/sK4%2Fx%209/meta',
      method: 'get',
    });
  });

  it('viewSecret 无口令时不携带 body（后端 @RequestBody required=false）', async () => {
    await viewSecret('sK4x9Qm', '');

    expect(envelopeHttp.request).toHaveBeenCalledWith({
      url: '/web/s/sK4x9Qm/view',
      method: 'post',
    });
  });

  it('viewSecret 有口令时以 POST body 携带（揭示即焚毁，错误 403 不焚毁）', async () => {
    await viewSecret('sK4x9Qm', 'op-1234');

    expect(envelopeHttp.request).toHaveBeenCalledWith({
      url: '/web/s/sK4x9Qm/view',
      method: 'post',
      data: { password: 'op-1234' },
    });
  });

  it('业务码常量与后端信封语义对齐（403/404/410）', () => {
    expect(SECRET_CODE.OK).toBe(200);
    expect(SECRET_CODE.WRONG_PASSWORD).toBe(403);
    expect(SECRET_CODE.NOT_FOUND).toBe(404);
    expect(SECRET_CODE.GONE).toBe(410);
  });
});

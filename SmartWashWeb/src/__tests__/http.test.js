import { describe, it, expect, beforeEach, vi } from 'vitest';

describe('http.js', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('有正确的 baseURL（默认 /api，由 vite 代理转发）', async () => {
    const { default: http } = await import('@/utils/http');
    expect(http.defaults.baseURL).toBe('/api');
  });

  it('请求拦截器在 token 存在时添加 Authorization 头', async () => {
    localStorage.setItem('token', 'test-token-123');
    const { default: http } = await import('@/utils/http');

    const config = http.interceptors.request.handlers[0].fulfilled({ headers: {} });
    expect(config.headers.Authorization).toBe('Bearer test-token-123');
  });

  it('请求拦截器在 token 不存在时不添加 Authorization 头', async () => {
    const { default: http } = await import('@/utils/http');

    const config = http.interceptors.request.handlers[0].fulfilled({ headers: {} });
    expect(config.headers.Authorization).toBeUndefined();
  });

  it('响应拦截器对 code !== 200 返回 reject', async () => {
    const { default: http } = await import('@/utils/http');

    const response = { data: { code: 500, message: '服务器错误' } };
    const handler = http.interceptors.response.handlers[0];

    await expect(handler.fulfilled(response)).rejects.toThrow('服务器错误');
  });

  it('响应拦截器对 code === 200 解包返回业务数据 res.data', async () => {
    const { default: http } = await import('@/utils/http');

    const response = { data: { code: 200, data: { id: 1 }, message: 'ok' } };
    const handler = http.interceptors.response.handlers[0];

    const result = handler.fulfilled(response);
    expect(result.id).toBe(1);
  });
});

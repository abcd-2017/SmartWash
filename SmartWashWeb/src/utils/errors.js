// src/utils/errors.js
// 错误判据统一（评审收口）：Element Plus validate() 的 reject 值是 invalidFields
// 字段键对象（无 message 属性），而 http 层业务失败是 Error 实例（message 必有值）。
// 此前页面散落的 `error?.fields` 判据在 EP 2.9.6 下恒为 false，导致每次校验失败
// 都叠加一条「操作失败」假 toast——校验失败请走字段红字，不再弹全局错误。
import axios from 'axios';

/** 判定一个 reject 值是否为表单校验失败（而非业务/网络错误） */
export const isValidationError = (error) => Boolean(error) && !error.message;

/** 是否为 axios 传输层错误（HTTP 状态码非 2xx / 断网 / 超时） */
export const isHttpError = (error) => Boolean(error) && axios.isAxiosError(error);

/**
 * 提取用户可读的错误文案：axios 原始英文（如 "Request failed with status code 500"）
 * 不得直出给运营，统一映射为中文友好文案；业务失败（后端 message）原样透出。
 * 与 http.js 响应拦截器的 HTTP 错误文案保持一致。
 */
export const friendlyErrorMessage = (error, fallback = '操作失败') => {
  if (!error) return fallback;
  if (isHttpError(error)) {
    return error.code === 'ECONNABORTED' || /timeout/i.test(error.message || '')
      ? '请求超时，请稍后重试'
      : '网络异常，请稍后重试';
  }
  return error.message || fallback;
};

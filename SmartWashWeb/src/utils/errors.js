// src/utils/errors.js
// 错误判据统一（评审收口）：Element Plus validate() 的 reject 值是 invalidFields
// 字段键对象（无 message 属性），而 http 层业务失败是 Error 实例（message 必有值）。
// 此前页面散落的 `error?.fields` 判据在 EP 2.9.6 下恒为 false，导致每次校验失败
// 都叠加一条「操作失败」假 toast——校验失败请走字段红字，不再弹全局错误。
/** 判定一个 reject 值是否为表单校验失败（而非业务/网络错误） */
export const isValidationError = (error) => Boolean(error) && !error.message;

import request from '@/utils/http';

/**
 * 查询指定手机号当前有效的短信验证码（演示工具）
 * @param {string} phoneNumber 手机号
 * @returns {Promise<{phone: string, captchas: Array<{purpose: string, purposeDesc: string, code: string, remainSeconds: number}>}>}
 */
export function queryCaptcha(phoneNumber) {
  return request({
    url: `/admin/captcha/${phoneNumber}`,
    method: 'get',
  });
}

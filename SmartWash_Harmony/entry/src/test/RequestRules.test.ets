/**
 * Axios 拦截器判定规则单测（hypium）。
 *
 * 被测对象是 Axios.ets 请求/响应拦截器所使用的纯判定函数（network/RequestRules.ets）：
 * - 公开路径白名单分支（isPublicRequest / shouldInjectToken）
 * - token 注入（resolveToken / buildAuthorizationHeader）
 * - 401 处理判定（isUnauthorizedCode）
 *
 * 拦截器本体含 preferences/UI 副作用（清 token、replace 登录页），无法在单测环境驱动；
 * 其判定逻辑已抽离为纯函数，本文件即覆盖这些分支。
 *
 * 说明：本地无设备/模拟器时无法执行，作为验收工件（同 BaZiAnchor.test.ets 约定）。
 */

import { describe, it, expect } from '@ohos/hypium';
import {
  UNAUTHORIZED_CODE,
  buildAuthorizationHeader,
  isPublicRequest,
  isUnauthorizedCode,
  resolveToken,
  shouldInjectToken
} from '../main/ets/network/RequestRules';

export default function requestRulesTest() {
  describe('RequestRules-公开路径白名单分支', () => {
    it('isPublicRequest-与 PublicPaths 判定一致', 0, () => {
      expect(isPublicRequest('/web/laundryItems/all')).assertTrue()
      expect(isPublicRequest('/auth/user/login')).assertTrue()
      expect(isPublicRequest('/web/auth/user/getUserInfo')).assertFalse()
      expect(isPublicRequest(undefined)).assertFalse()
    })

    it('shouldInjectToken-公开路径不注入', 0, () => {
      expect(shouldInjectToken('/web/laundryItems/all')).assertFalse()
      expect(shouldInjectToken('/web/orders/status')).assertFalse()
      expect(shouldInjectToken('/web/deeplink/routes')).assertFalse()
      expect(shouldInjectToken('/web/config')).assertFalse()
    })

    it('shouldInjectToken-/web 前缀非公开接口注入', 0, () => {
      expect(shouldInjectToken('/web/auth/user/getUserInfo')).assertTrue()
      expect(shouldInjectToken('/web/auth/orders')).assertTrue()
      expect(shouldInjectToken('/web/auth/orders/itemCount')).assertTrue()
    })

    it('shouldInjectToken-非 /web 前缀不注入', 0, () => {
      expect(shouldInjectToken('/auth/user/login')).assertFalse()
      expect(shouldInjectToken('/auth/user/register')).assertFalse()
      expect(shouldInjectToken('/admin/index')).assertFalse()
      expect(shouldInjectToken(undefined)).assertFalse()
      expect(shouldInjectToken('')).assertFalse()
    })
  })

  describe('RequestRules-token 注入', () => {
    it('resolveToken-字符串原样返回', 0, () => {
      expect(resolveToken('jwt-token-value')).assertEqual('jwt-token-value')
    })

    it('resolveToken-非字符串一律视为无 token', 0, () => {
      expect(resolveToken(12345)).assertEqual('')
      expect(resolveToken(true)).assertEqual('')
      expect(resolveToken(null)).assertEqual('')
    })

    it('buildAuthorizationHeader-Bearer 方案', 0, () => {
      expect(buildAuthorizationHeader('abc')).assertEqual('Bearer abc')
      expect(buildAuthorizationHeader('')).assertEqual('Bearer ')
    })
  })

  describe('RequestRules-401 处理判定', () => {
    it('isUnauthorizedCode-401 命中', 0, () => {
      expect(isUnauthorizedCode(UNAUTHORIZED_CODE)).assertTrue()
      expect(isUnauthorizedCode(401)).assertTrue()
    })

    it('isUnauthorizedCode-非 401 不命中', 0, () => {
      expect(isUnauthorizedCode(200)).assertFalse()
      expect(isUnauthorizedCode(201)).assertFalse()
      expect(isUnauthorizedCode(404)).assertFalse()
      expect(isUnauthorizedCode(500)).assertFalse()
    })

    it('isUnauthorizedCode-缺失码不命中（非信封 body 场景）', 0, () => {
      expect(isUnauthorizedCode(null)).assertFalse()
      expect(isUnauthorizedCode(undefined)).assertFalse()
    })
  })
}

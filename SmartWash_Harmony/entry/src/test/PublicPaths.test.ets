/**
 * PublicPaths.isPublic 公开路径白名单单测（hypium）。
 *
 * 事实源：后端 SecurityConfig.permitAll 段（见 PublicPaths.ets 头部注释）。
 * 覆盖：精确命中 / 前缀命中 / 非公开 / 空值 / query 截断 / 前缀边界（防误命中）。
 *
 * 说明：本地无设备/模拟器时无法执行，作为验收工件（同 BaZiAnchor.test.ets 约定）。
 */

import { describe, it, expect } from '@ohos/hypium';
import { PublicPaths } from '../main/ets/network/PublicPaths';

export default function publicPathsTest() {
  describe('PublicPaths.isPublic', () => {
    it('精确路径-公开只读接口全部命中', 0, () => {
      const exact: string[] = [
        '/web/orders/status',
        '/web/laundryItems/all',
        '/web/lockers/status',
        '/web/lockers/all',
        '/web/schools/allName',
        '/web/payments/payType',
        '/web/payments/payStatus',
        '/web/app/version',
        '/web/app/download',
        '/web/deeplink/routes',
        '/web/config',
        '/swagger-ui.html'
      ]
      for (const path of exact) {
        expect(PublicPaths.isPublic(path)).assertTrue()
      }
    })

    it('前缀路径-认证与工具箱短码与文档命中', 0, () => {
      const prefixed: string[] = [
        '/auth/user/login',
        '/auth/user/register',
        '/auth/user/captcha/13800000000',
        '/web/t/abc123',
        '/swagger-ui/index.html',
        '/webjars/swagger-ui/5.0.0/swagger-ui.css',
        '/v3/api-docs/swagger-config'
      ]
      for (const path of prefixed) {
        expect(PublicPaths.isPublic(path)).assertTrue()
      }
    })

    it('非公开路径-/web/auth 与其它业务前缀不命中', 0, () => {
      const nonPublic: string[] = [
        '/web/auth/user/getUserInfo',
        '/web/auth/orders',
        '/web/auth/recharge/list',
        '/web/auth/toolbox/short-codes',
        '/admin/index',
        '/web/orders'
      ]
      for (const path of nonPublic) {
        expect(PublicPaths.isPublic(path)).assertFalse()
      }
    })

    it('空值-undefined 与空串不放宽', 0, () => {
      expect(PublicPaths.isPublic(undefined)).assertFalse()
      expect(PublicPaths.isPublic('')).assertFalse()
    })

    it('query 截断-带查询串仍按路径部分匹配', 0, () => {
      expect(PublicPaths.isPublic('/web/orders/status?foo=bar')).assertTrue()
      expect(PublicPaths.isPublic('/web/laundryItems/all?size=10')).assertTrue()
      expect(PublicPaths.isPublic('/web/auth/user/getUserInfo?token=x')).assertFalse()
    })

    it('前缀边界-/web/t/ 不误命中 /web/tt/', 0, () => {
      // 前缀段以 "/" 结尾，避免 /web/t/ 命中 /web/tt/xxx
      expect(PublicPaths.isPublic('/web/tt/xxx')).assertFalse()
      expect(PublicPaths.isPublic('/web/t/xxx')).assertTrue()
    })
  })
}

/**
 * HttpUtil 信封解码单测（hypium）。
 *
 * 被测对象：HttpUtil.ets 的 decode（业务信封兜底转译）：
 * - code 缺省（body 非信封/网关异常页）→ 统一按业务失败处理，code=500、data 清空；
 * - 业务失败且 message 缺失 → 补默认文案，页面 toast 不弹 "undefined"；
 * - 成功（200）原样透传。
 *
 * 说明：本地无设备/模拟器时无法执行，作为验收工件（同 BaZiAnchor.test.ets 约定）。
 */

import { describe, it, expect } from '@ohos/hypium';
import { BizCode, decode } from '../main/ets/network/HttpUtil';
import { BaseResponse } from '../main/ets/network/BaseResponse';

export default function httpUtilTest() {
  describe('HttpUtil.decode-信封兜底', () => {
    it('成功-200 原样透传', 0, () => {
      const raw: BaseResponse<string> = { code: BizCode.SUCCESS, message: 'ok', data: 'payload' }
      const result: BaseResponse<string> = decode<string>(raw)
      expect(result.code).assertEqual(BizCode.SUCCESS)
      expect(result.message).assertEqual('ok')
      expect(result.data).assertEqual('payload')
    })

    it('code 缺省-按未知错误处理并清空 data', 0, () => {
      const raw: BaseResponse<string> = { code: undefined, message: undefined, data: 'leak' }
      const result: BaseResponse<string> = decode<string>(raw)
      expect(result.code).assertEqual(BizCode.ERROR)
      expect(result.data).assertNull()
      expect(result.message).assertEqual('未知错误')
    })

    it('code 为 null-同缺省处理', 0, () => {
      // 运行时 JSON 可能显式下发 null（非缺省），用 JSON.parse 构造后断言
      const rawNull: BaseResponse<string> =
        JSON.parse('{"code":null,"message":null,"data":null}') as BaseResponse<string>
      const result: BaseResponse<string> = decode<string>(rawNull)
      expect(result.code).assertEqual(BizCode.ERROR)
      expect(result.message).assertEqual('未知错误')
    })

    it('code 缺省但带 message-保留该 message', 0, () => {
      const raw: BaseResponse<string> = { code: undefined, message: '网关异常', data: null }
      const result: BaseResponse<string> = decode<string>(raw)
      expect(result.code).assertEqual(BizCode.ERROR)
      expect(result.message).assertEqual('网关异常')
    })

    it('业务失败且 message 缺失-补默认文案', 0, () => {
      const raw: BaseResponse<number> = { code: BizCode.FAIL, message: undefined, data: 7 }
      const result: BaseResponse<number> = decode<number>(raw)
      expect(result.code).assertEqual(BizCode.FAIL)
      expect(result.data).assertEqual(7)
      expect(result.message).assertEqual('请求失败')
    })

    it('业务失败且 message 为空串-同样补默认文案', 0, () => {
      const raw: BaseResponse<number> = { code: BizCode.NOT_FOUND, message: '', data: null }
      const result: BaseResponse<number> = decode<number>(raw)
      expect(result.code).assertEqual(BizCode.NOT_FOUND)
      expect(result.message).assertEqual('请求失败')
    })

    it('业务失败且带 message-原样保留', 0, () => {
      const raw: BaseResponse<boolean> = { code: BizCode.UNAUTHORIZED, message: '登录失效', data: null }
      const result: BaseResponse<boolean> = decode<boolean>(raw)
      expect(result.code).assertEqual(BizCode.UNAUTHORIZED)
      expect(result.message).assertEqual('登录失效')
    })
  })
}

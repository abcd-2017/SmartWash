/**
 * 吃啥（dining）域纯函数单测（hypium）。
 *
 * 覆盖对齐 Android 端 JVM 单测锚点：DiningJoinUrlBuilder / DiningJoinUrlParser /
 * DiningJoinEntry / maskDiningPhone / 购买清单快照解析。
 * 事实源：DiningJoinUrlBuilder 契约（{origin}/dining/join/{orderId}?t={token}）与
 * 后端 DiningOrderServiceImpl.maskPhone 脱敏规则。
 */

import { describe, it, expect } from '@ohos/hypium';
import {
  DiningJoinEntryDecision,
  DiningJoinUrlKind,
  buildDiningJoinUrl,
  decideDiningJoinEntry,
  maskDiningPhone,
  originOf,
  parseDiningJoinUrl,
  parseDiningShoppingList
} from '../main/ets/feature/dining/DiningLogic';
import { DiningOrderBriefVo } from '../main/ets/network/vo/DiningVo';

export default function diningLogicTest() {
  describe('diningLogicTest', () => {

    // ---------- originOf ----------

    it('originOf-http 带端口去路径', 0, () => {
      expect(originOf('http://192.168.1.61:8080/')).assertEqual('http://192.168.1.61:8080')
    })
    it('originOf-https 无端口去 query', 0, () => {
      expect(originOf('https://api.example.com/base?x=1')).assertEqual('https://api.example.com')
    })
    it('originOf-非 http 族返回 null', 0, () => {
      expect(originOf('ftp://example.com')).assertNull()
      expect(originOf('')).assertNull()
      expect(originOf('not-a-url')).assertNull()
    })

    // ---------- buildDiningJoinUrl ----------

    it('buildJoinUrl-标准构造', 0, () => {
      const url = buildDiningJoinUrl('http://192.168.1.61:8080', 12, 'tok123')
      expect(url).assertEqual('http://192.168.1.61:8080/dining/join/12?t=tok123')
    })
    it('buildJoinUrl-token 空白返回 null', 0, () => {
      expect(buildDiningJoinUrl('http://a.com', 1, '   ')).assertNull()
      expect(buildDiningJoinUrl('http://a.com', 1, '')).assertNull()
    })
    it('buildJoinUrl-baseUrl 畸形返回 null', 0, () => {
      expect(buildDiningJoinUrl('bad-url', 1, 'tok')).assertNull()
    })

    // ---------- parseDiningJoinUrl ----------

    it('parseJoinUrl-合法加入链接', 0, () => {
      const result = parseDiningJoinUrl('http://192.168.1.61:8080/dining/join/88?t=abc')
      expect(result.kind).assertEqual(DiningJoinUrlKind.JOIN)
      expect(result.orderId).assertEqual(88)
      expect(result.token).assertEqual('abc')
    })
    it('parseJoinUrl-https 大写 scheme 与 T 键大小写不敏感', 0, () => {
      const result = parseDiningJoinUrl('HTTPS://Example.com/dining/join/9?T=xyz')
      expect(result.kind).assertEqual(DiningJoinUrlKind.JOIN)
      expect(result.orderId).assertEqual(9)
      expect(result.token).assertEqual('xyz')
    })
    it('parseJoinUrl-非本应用码', 0, () => {
      expect(parseDiningJoinUrl('').kind).assertEqual(DiningJoinUrlKind.NOT_DINING)
      expect(parseDiningJoinUrl(null).kind).assertEqual(DiningJoinUrlKind.NOT_DINING)
      expect(parseDiningJoinUrl('hello world').kind).assertEqual(DiningJoinUrlKind.NOT_DINING)
      expect(parseDiningJoinUrl('https://example.com/other/path?t=1').kind).assertEqual(DiningJoinUrlKind.NOT_DINING)
    })
    it('parseJoinUrl-命中路径但结构非法', 0, () => {
      expect(parseDiningJoinUrl('http://a.com/dining/join/?t=1').kind).assertEqual(DiningJoinUrlKind.MALFORMED)
      expect(parseDiningJoinUrl('http://a.com/dining/join/abc?t=1').kind).assertEqual(DiningJoinUrlKind.MALFORMED)
      expect(parseDiningJoinUrl('http://a.com/dining/join/0?t=1').kind).assertEqual(DiningJoinUrlKind.MALFORMED)
      expect(parseDiningJoinUrl('http://a.com/dining/join/1/2?t=1').kind).assertEqual(DiningJoinUrlKind.MALFORMED)
      expect(parseDiningJoinUrl('http://a.com/dining/join/5').kind).assertEqual(DiningJoinUrlKind.MALFORMED)
    })

    // ---------- maskDiningPhone ----------

    it('maskPhone-11 位 3+4+4', 0, () => {
      expect(maskDiningPhone('13812345678')).assertEqual('138****5678')
    })
    it('maskPhone-短号原样与其余前3后2', 0, () => {
      expect(maskDiningPhone('12345')).assertEqual('12345')
      expect(maskDiningPhone('123456')).assertEqual('123****56')
      expect(maskDiningPhone('')).assertEqual('')
      expect(maskDiningPhone(null)).assertEqual('')
    })

    // ---------- decideDiningJoinEntry ----------

    it('joinEntry-brief 缺失不可用', 0, () => {
      expect(decideDiningJoinEntry(null)).assertEqual(DiningJoinEntryDecision.BRIEF_UNAVAILABLE)
    })
    it('joinEntry-已成员直达', 0, () => {
      const brief: DiningOrderBriefVo = {
        orderId: 1, title: '周五晚火锅', status: 'ONGOING', creatorName: '139****9000',
        recipeCount: 3, joined: true
      }
      expect(decideDiningJoinEntry(brief)).assertEqual(DiningJoinEntryDecision.ENTER_DETAIL)
    })
    it('joinEntry-非成员进行中确认加入', 0, () => {
      const brief: DiningOrderBriefVo = {
        orderId: 1, title: '周五晚火锅', status: 'ONGOING', creatorName: '139****9000',
        recipeCount: 3, joined: false
      }
      expect(decideDiningJoinEntry(brief)).assertEqual(DiningJoinEntryDecision.CONFIRM_JOIN)
    })
    it('joinEntry-已结束与未知态不引导加入', 0, () => {
      const finished: DiningOrderBriefVo = {
        orderId: 1, title: '周五晚火锅', status: 'FINISHED', creatorName: '139****9000',
        recipeCount: 3, joined: false
      }
      const dirty: DiningOrderBriefVo = {
        orderId: 1, title: '周五晚火锅', status: 'WEIRD', creatorName: '139****9000',
        recipeCount: 3, joined: false
      }
      expect(decideDiningJoinEntry(finished)).assertEqual(DiningJoinEntryDecision.ORDER_FINISHED)
      expect(decideDiningJoinEntry(dirty)).assertEqual(DiningJoinEntryDecision.ORDER_FINISHED)
    })

    // ---------- parseDiningShoppingList ----------

    it('shoppingList-标准快照解析', 0, () => {
      const json = '{"ingredientNames":["西红柿","鸡蛋"],"perRecipe":[' +
        '{"recipeName":"番茄炒蛋","selectors":[{"userId":7,"note":"不要辣"}]}]}'
      const snapshot = parseDiningShoppingList(json)
      expect(snapshot.ingredientNames.length).assertEqual(2)
      expect(snapshot.ingredientNames[0]).assertEqual('西红柿')
      expect(snapshot.perRecipe.length).assertEqual(1)
      expect(snapshot.perRecipe[0].recipeName).assertEqual('番茄炒蛋')
      expect(snapshot.perRecipe[0].selectors[0].note).assertEqual('不要辣')
    })
    it('shoppingList-空值与畸形兜底空快照', 0, () => {
      expect(parseDiningShoppingList(null).ingredientNames.length).assertEqual(0)
      expect(parseDiningShoppingList('').perRecipe.length).assertEqual(0)
      expect(parseDiningShoppingList('not-json{{{').ingredientNames.length).assertEqual(0)
    })
  })
}

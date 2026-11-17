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
  DINING_ALLERGEN_CODES,
  DINING_PEOPLE_MAX,
  DINING_PEOPLE_MIN,
  DINING_PORTION_GRAMS,
  DiningJoinEntryDecision,
  DiningJoinUrlKind,
  DiningPortionVerdict,
  DiningRecipeShopping,
  DiningScanKind,
  DiningSelectedDish,
  DiningShoppingResult,
  DiningStepLineKind,
  buildDiningJoinUrl,
  buildShoppingListText,
  coerceDiningPeopleCount,
  decideDiningJoinEntry,
  diningAllergenHits,
  diningCalorieRemaining,
  diningPortionAdvice,
  diningSelectedTotalCalories,
  diningServingCalories,
  formatDiningAmount,
  maskDiningPhone,
  normalizeDiningAllergen,
  normalizeDiningAllergens,
  normalizeDiningScan,
  originOf,
  parseDiningJoinUrl,
  parseDiningShoppingList,
  parseDiningStepLines
} from '../main/ets/feature/dining/DiningLogic';
import {
  DietaryPreferenceVo,
  DiningOrderBriefVo,
  DiningRecipeVo,
  dietaryPreferenceVoFromJSON,
  diningRecipeVoFromJSON
} from '../main/ets/network/vo/DiningVo';

/** 测试用菜谱 VO 工厂（仅填营养相关字段，其余走默认） */
function testRecipe(id: number, caloriesTotal: number | null,
  caloriesPer100g: number | null): DiningRecipeVo {
  return {
    id: id,
    categoryId: 0,
    categoryName: '',
    name: `菜${id}`,
    coverUrl: '',
    ingredients: [],
    steps: '',
    source: 'USER',
    status: 1,
    caloriesPer100g: caloriesPer100g,
    caloriesTotal: caloriesTotal,
    proteinG: null,
    fatG: null,
    carbsG: null,
    sodiumMg: null,
    favorited: null,
    allergens: null
  }
}

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

    // ---------- normalizeDiningScan（壳层扫一扫的域内归一） ----------

    it('normalizeScan-加入链接归一为深链串', 0, () => {
      const result = normalizeDiningScan('http://192.168.1.61:8080/dining/join/88?t=abc')
      expect(result.kind).assertEqual(DiningScanKind.DEEP_LINK)
      expect(result.deepLinkUri).assertEqual('smartwash://dining/order/88?t=abc')
    })
    it('normalizeScan-非本应用码交还通用分发', 0, () => {
      const result = normalizeDiningScan('https://example.com/other')
      expect(result.kind).assertEqual(DiningScanKind.NOT_DINING)
      expect(result.deepLinkUri).assertEqual('')
    })
    it('normalizeScan-命中路径但结构非法', 0, () => {
      const result = normalizeDiningScan('http://a.com/dining/join/abc?t=1')
      expect(result.kind).assertEqual(DiningScanKind.MALFORMED)
      expect(result.deepLinkUri).assertEqual('')
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

    // ---------- buildShoppingListText ----------

    it('shoppingListText-标题勾选态与按菜选菜人', 0, () => {
      const result: DiningShoppingResult = {
        ingredients: ['西红柿', '鸡蛋', '葱'],
        perRecipe: [
          {
            recipeName: '番茄炒蛋',
            selectors: [
              { label: '139****0001', note: '不要辣', isMe: true },
              { label: '138****0002', note: '', isMe: false }
            ]
          },
          {
            recipeName: '凉拌西红柿',
            selectors: [{ label: '139****0001', note: '少糖', isMe: true }]
          }
        ],
        ingredientTotals: null,
        totalCalories: null,
        calorieUnknownDishCount: null
      }
      const text = buildShoppingListText('周五晚火锅', result, ['鸡蛋'])
      const expected = '吃啥 · 购买清单\n订单：周五晚火锅\n\n【按食材买菜】\n[ ] 西红柿\n[x] 鸡蛋\n[ ] 葱\n\n' +
        '【按菜备菜】\n- 番茄炒蛋：139****0001（不要辣）、138****0002\n- 凉拌西红柿：139****0001（少糖）'
      expect(text).assertEqual(expected)
    })
    it('shoppingListText-空标题省略订单行与空清单降级', 0, () => {
      const empty: DiningShoppingResult = {
        ingredients: [], perRecipe: [], ingredientTotals: null, totalCalories: null,
        calorieUnknownDishCount: null
      }
      const text = buildShoppingListText('  ', empty, [])
      expect(text.indexOf('订单：')).assertEqual(-1)
      expect(text.indexOf('【按食材买菜】\n（无）')).assertLarger(0)
      expect(text.indexOf('【按菜备菜】\n（无）')).assertLarger(0)
    })
    it('shoppingListText-无选菜人的菜只出菜名', 0, () => {
      const recipe: DiningRecipeShopping = { recipeName: '白米饭', selectors: [] }
      const result: DiningShoppingResult = {
        ingredients: [], perRecipe: [recipe], ingredientTotals: null, totalCalories: null,
        calorieUnknownDishCount: null
      }
      const text = buildShoppingListText('家宴', result, [])
      expect(text.indexOf('- 白米饭')).assertLarger(0)
      expect(text.indexOf('白米饭：')).assertEqual(-1)
    })

    // ---------- 营养计算（契约 §二 份热量口径，对齐 Android DiningNutritionCalculator） ----------

    it('servingCalories-整份口径优先', 0, () => {
      expect(DINING_PORTION_GRAMS).assertEqual(250)
      expect(diningServingCalories(1830, null)).assertEqual(1830)
      expect(diningServingCalories(1830, 500)).assertEqual(1830)
    })
    it('servingCalories-per100g 按 250g 折算', 0, () => {
      expect(diningServingCalories(null, 500)).assertEqual(1250)
      expect(diningServingCalories(null, 100)).assertEqual(250)
    })
    it('servingCalories-两者皆缺未知', 0, () => {
      expect(diningServingCalories(null, null)).assertNull()
    })
    it('formatAmount-整数去尾零与 1 位小数', 0, () => {
      expect(formatDiningAmount(1830)).assertEqual('1830')
      expect(formatDiningAmount(666.66)).assertEqual('666.7')
      expect(formatDiningAmount(null)).assertNull()
    })

    // ---------- 步骤渲染清洗（TD24 对齐安卓 parseStepLines） ----------

    it('stepLines-剥离 markdown 加粗与原文编号', 0, () => {
      const lines = parseDiningStepLines('**1.** 打散鸡蛋\n2、热锅下油\n3) 翻炒装盘')
      expect(lines.length).assertEqual(3)
      expect(lines[0].kind).assertEqual(DiningStepLineKind.MAIN)
      expect(lines[0].text).assertEqual('打散鸡蛋')
      expect(lines[1].text).assertEqual('热锅下油')
      expect(lines[2].text).assertEqual('翻炒装盘')
    })

    it('stepLines-「-」开头行归子注解', 0, () => {
      const lines = parseDiningStepLines('打散鸡蛋\n- 用土鸡蛋更香\n——也可以加点料酒')
      expect(lines.length).assertEqual(3)
      expect(lines[0].kind).assertEqual(DiningStepLineKind.MAIN)
      expect(lines[1].kind).assertEqual(DiningStepLineKind.SUB)
      expect(lines[1].text).assertEqual('用土鸡蛋更香')
      expect(lines[2].kind).assertEqual(DiningStepLineKind.SUB)
      expect(lines[2].text).assertEqual('也可以加点料酒')
    })

    it('stepLines-空行跳过与空串返回空表', 0, () => {
      expect(parseDiningStepLines('').length).assertEqual(0)
      const lines = parseDiningStepLines('第一步\n\n\n第二步\n')
      expect(lines.length).assertEqual(2)
      expect(lines[0].text).assertEqual('第一步')
      expect(lines[1].text).assertEqual('第二步')
    })

    // ---------- 购买清单快照 v2（契约 §3.3 additive，v1 存在性降级） ----------

    it('shoppingList-v2 快照解析聚合与热量', 0, () => {
      const json = '{"ingredientNames":["鸡蛋"],"perRecipe":[],' +
        '"ingredientTotals":[{"name":"鸡蛋","totalValue":1130,"unit":"g","dishCount":2,' +
        '"perDish":[{"recipeName":"番茄炒蛋","amountValue":575,"amountUnit":"g"}]}],' +
        '"totalCalories":1830,"calorieKnownDishCount":2,"calorieUnknownDishCount":1,' +
        '"perDishCalories":[{"recipeName":"宫保鸡丁","calories":1051}]}'
      const snapshot = parseDiningShoppingList(json)
      expect(snapshot.ingredientTotals === null).assertFalse()
      if (snapshot.ingredientTotals !== null) {
        expect(snapshot.ingredientTotals.length).assertEqual(1)
        expect(snapshot.ingredientTotals[0].totalValue).assertEqual(1130)
        expect(snapshot.ingredientTotals[0].unit).assertEqual('g')
        expect(snapshot.ingredientTotals[0].dishCount).assertEqual(2)
        expect(snapshot.ingredientTotals[0].perDish.length).assertEqual(1)
      }
      expect(snapshot.totalCalories).assertEqual(1830)
      expect(snapshot.calorieKnownDishCount).assertEqual(2)
      expect(snapshot.calorieUnknownDishCount).assertEqual(1)
      expect(snapshot.perDishCalories === null).assertFalse()
    })
    it('shoppingList-v1 老快照 v2 字段缺失降级 null', 0, () => {
      const snapshot = parseDiningShoppingList('{"ingredientNames":["鸡蛋"],"perRecipe":[]}')
      expect(snapshot.ingredientTotals === null).assertTrue()
      expect(snapshot.totalCalories).assertNull()
      expect(snapshot.calorieKnownDishCount).assertNull()
      expect(snapshot.calorieUnknownDishCount).assertNull()
      expect(snapshot.perDishCalories === null).assertTrue()
      expect(snapshot.ingredientNames.length).assertEqual(1)
    })

    // ---------- 忌口警示（契约 §三.1，对齐 Android DiningAllergenGuard） ----------

    it('allergen-normalize 去空白大写与去重', 0, () => {
      expect(normalizeDiningAllergen(' seafood ')).assertEqual('SEAFOOD')
      expect(normalizeDiningAllergen('nut')).assertEqual('NUT')
      expect(normalizeDiningAllergen('')).assertNull()
      expect(normalizeDiningAllergen(null)).assertNull()
      const normalized = normalizeDiningAllergens(['nut', ' NUT ', 'egg', ''])
      expect(normalized.length).assertEqual(2)
      expect(normalized[0]).assertEqual('NUT')
      expect(normalized[1]).assertEqual('EGG')
      expect(normalizeDiningAllergens(null).length).assertEqual(0)
    })

    it('allergen-hits 交集保序且用户空集短路', 0, () => {
      const dishes: Array<DiningSelectedDish> = [
        { recipeId: 1, recipeName: '宫保鸡丁', allergens: ['nut', 'SOY'] },
        { recipeId: 2, recipeName: '清蒸鱼', allergens: ['SEAFOOD'] },
        { recipeId: 3, recipeName: '白米饭', allergens: null }
      ]
      const hits = diningAllergenHits(dishes, ['NUT', 'SEAFOOD'])
      expect(hits.length).assertEqual(2)
      expect(hits[0].recipeId).assertEqual(1)
      expect(hits[0].allergen).assertEqual('NUT')
      expect(hits[1].recipeId).assertEqual(2)
      expect(hits[1].allergen).assertEqual('SEAFOOD')
      expect(diningAllergenHits(dishes, []).length).assertEqual(0)
      expect(diningAllergenHits([], ['NUT']).length).assertEqual(0)
    })

    it('allergen-白名单 7 标签', 0, () => {
      expect(DINING_ALLERGEN_CODES.length).assertEqual(7)
      expect(DINING_ALLERGEN_CODES.indexOf('SPICY') >= 0).assertTrue()
    })

    // ---------- 份量建议（契约 §2.5，对齐 Android DiningPortionAdvisor） ----------

    it('people-coerce 收敛 1-20 与异常值', 0, () => {
      expect(DINING_PEOPLE_MIN).assertEqual(1)
      expect(DINING_PEOPLE_MAX).assertEqual(20)
      expect(coerceDiningPeopleCount(0)).assertEqual(1)
      expect(coerceDiningPeopleCount(25)).assertEqual(20)
      expect(coerceDiningPeopleCount(3)).assertEqual(3)
      expect(coerceDiningPeopleCount(Number.NaN)).assertEqual(1)
    })

    it('selected-total-calories 仅统计已知菜', 0, () => {
      expect(diningSelectedTotalCalories([])).assertNull()
      expect(diningSelectedTotalCalories([testRecipe(1, null, null)])).assertNull()
      // 1830 + 1250（500×250÷100）
      expect(diningSelectedTotalCalories([
        testRecipe(1, 1830, null),
        testRecipe(2, null, 500)
      ])).assertEqual(3080)
    })

    it('portion-advice 够/偏多/偏少阈值', 0, () => {
      // 参考 = 2000÷3 ≈ 666.7；人均 600 = 参考×0.9 → ENOUGH
      const enough = diningPortionAdvice(1200, 2, 2000)
      expect(enough !== null).assertTrue()
      if (enough !== null) {
        expect(enough.verdict).assertEqual(DiningPortionVerdict.ENOUGH)
        expect(enough.peopleCount).assertEqual(2)
      }
      // 人均 800 > 参考×1.1 → MORE
      const more = diningPortionAdvice(1600, 2, 2000)
      expect(more !== null && more.verdict === DiningPortionVerdict.MORE).assertTrue()
      // 人均 450 < 参考×0.9 → LESS
      const less = diningPortionAdvice(900, 2, 2000)
      expect(less !== null && less.verdict === DiningPortionVerdict.LESS).assertTrue()
      // 总热量未知 → null；目标缺省走默认 2000
      expect(diningPortionAdvice(null, 2, 2000)).assertNull()
      const fallback = diningPortionAdvice(1200, 2, 0)
      expect(fallback !== null && fallback.reference > 666 && fallback.reference < 667).assertTrue()
    })

    it('calorie-remaining 目标减本单总热量', 0, () => {
      expect(diningCalorieRemaining(1800, 2000)).assertEqual(200)
      expect(diningCalorieRemaining(2200, 2000)).assertEqual(-200)
      expect(diningCalorieRemaining(null, 2000)).assertNull()
      expect(diningCalorieRemaining(2000, 0)).assertEqual(0)
    })

    // ---------- VO 字段透传（契约 §2.1/§2.2/§2.3） ----------

    it('recipe-vo-favorited-allergens 映射与缺失兜底', 0, () => {
      const raw: Record<string, Object> = {
        'id': 7, 'name': '宫保鸡丁', 'favorited': true, 'allergens': ['NUT', 'SOY'],
        'caloriesTotal': 1051
      }
      const vo = diningRecipeVoFromJSON(raw)
      expect(vo.favorited).assertEqual(true)
      expect(vo.allergens !== null).assertTrue()
      if (vo.allergens !== null) {
        expect(vo.allergens.length).assertEqual(2)
      }
      // 列表接口不带 allergens / favorited 缺失 → null 兜底
      const listRaw: Record<string, Object> = { 'id': 8, 'name': '白米饭' }
      const listVo = diningRecipeVoFromJSON(listRaw)
      expect(listVo.favorited).assertNull()
      expect(listVo.allergens === null || listVo.allergens.length === 0).assertTrue()
    })

    it('dietary-preference-vo 映射与 null 默认语义', 0, () => {
      const prefRaw: Record<string, Object> = {
        'allergens': ['NUT'], 'calorieTarget': 2200, 'sodiumTarget': 1800
      }
      const vo: DietaryPreferenceVo = dietaryPreferenceVoFromJSON(prefRaw)
      expect(vo.calorieTarget).assertEqual(2200)
      expect(vo.sodiumTarget).assertEqual(1800)
      if (vo.allergens !== null) {
        expect(vo.allergens.length).assertEqual(1)
      }
      // 字段缺失 → null（默认值语义）
      const emptyRaw: Record<string, Object> = {}
      const empty = dietaryPreferenceVoFromJSON(emptyRaw)
      expect(empty.allergens === null || empty.allergens.length === 0).assertTrue()
      expect(empty.calorieTarget).assertNull()
      expect(empty.sodiumTarget).assertNull()
    })
  })
}

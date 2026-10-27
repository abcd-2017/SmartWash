/**
 * 六爻纳甲装卦差分锚点（hypium 单测，对拍基准）。
 *
 * 原样移植安卓 feature/divination/src/test/.../core/liuyao/LiuYaoAnchorTest.kt
 * （oracle：docs/divination/references/verify_paipan_anchor.py，najia 实现）。
 * 锚点输入：爻值 [2,2,1,4,2,1]（单拆重交）@ 2026-08-29 12:00，14 个数据点逐项断言。
 *
 * 注意：本机 SDK 版本不匹配无法构建，hypium 用例需设备/模拟器执行，本文件作为验收工件。
 */

import { describe, beforeAll, it, expect } from '@ohos/hypium';
import { LiuYaoChart } from '../main/ets/feature/divination/core/liuyao/LiuYaoChart';
import { LiuYaoFacts } from '../main/ets/feature/divination/core/liuyao/LiuYaoFacts';
import { DivCategory } from '../main/ets/feature/divination/core/DivinationCommon';

export default function liuYaoAnchorTest() {
  describe('LiuYaoAnchor', () => {
    let chart: LiuYaoChart;

    beforeAll(() => {
      chart = LiuYaoChart.compileAt([2, 2, 1, 4, 2, 1], 2026, 8, 29, 12, 0, 0);
    });

    it('锚点1 本卦名与卦宫', 0, () => {
      expect(chart.name).assertEqual('艮为山');
      expect(chart.gong).assertEqual('艮');
    });

    it('锚点2 本卦六冲', 0, () => {
      expect(chart.type).assertEqual('六冲');
    });

    it('锚点3 变卦火山旅离宫六合', 0, () => {
      expect(chart.bianName).assertEqual('火山旅');
      expect(chart.bianGong).assertEqual('离');
      expect(chart.bianType).assertEqual('六合');
    });

    it('锚点4 四柱', 0, () => {
      expect(chart.siZhu.year.label).assertEqual('丙午');
      expect(chart.siZhu.month.label).assertEqual('丙申');
      expect(chart.siZhu.day.label).assertEqual('乙亥');
      expect(chart.siZhu.hour.label).assertEqual('壬午');
    });

    it('锚点5 旬空申酉', 0, () => {
      expect(chart.xunKong).assertEqual('申酉');
    });

    it('锚点6 世上应三', 0, () => {
      expect(chart.shiYao).assertEqual(6);
      expect(chart.yingYao).assertEqual(3);
    });

    it('锚点7 六亲干支逐爻', 0, () => {
      const expected: [string, string][] = [
        ['兄弟', '丙辰土'],
        ['父母', '丙午火'],
        ['子孙', '丙申金'],
        ['兄弟', '丙戌土'],
        ['妻财', '丙子水'],
        ['官鬼', '丙寅木'],
      ];
      for (let index = 0; index < expected.length; index++) {
        expect(chart.rows[index].liuQin).assertEqual(expected[index][0]);
        expect(chart.rows[index].ganZhi + chart.rows[index].zhiWuXing.label)
          .assertEqual(expected[index][1]);
      }
    });

    it('锚点8 世应标记落位', 0, () => {
      expect(chart.rows[0].shiYing).assertNull();
      expect(chart.rows[2].shiYing).assertEqual('应');
      expect(chart.rows[5].shiYing).assertEqual('世');
    });

    it('锚点9 动爻为四爻老阴', 0, () => {
      expect(chart.movingPositions.join(',')).assertEqual('4');
      expect(chart.rows[3].yao.label).assertEqual('老阴');
      expect(chart.rows[3].yao.code).assertEqual(4);
    });

    it('锚点10 六神自下而上', 0, () => {
      const expected: string[] = ['青龙', '朱雀', '勾陈', '螣蛇', '白虎', '玄武'];
      for (let index = 0; index < expected.length; index++) {
        expect(chart.rows[index].liuShen).assertEqual(expected[index]);
      }
    });

    it('锚点11 动爻化出子孙酉金回头生', 0, () => {
      expect(chart.rows[3].bianGanZhi).assertEqual('己酉');
      expect(chart.rows[3].bianLiuQin).assertEqual('子孙');
    });

    it('锚点12 装卦六亲齐全无伏神', 0, () => {
      expect(chart.hidden).assertNull();
    });

    it('锚点13 变卦六亲以本宫论', 0, () => {
      const bianRows = chart.bianRows;
      if (bianRows === null) {
        throw new Error('变卦行为空');
      }
      // 动爻位（四爻）变出 己酉 子孙，其余行保持本卦纳甲
      expect(bianRows[3].ganZhi).assertEqual('己酉');
      expect(bianRows[3].isChanged).assertEqual(true);
      expect(bianRows[0].ganZhi).assertEqual('丙辰');
      expect(bianRows[0].isChanged).assertEqual(false);
    });

    it('锚点14 规则层事实标记', 0, () => {
      const facts: LiuYaoFacts = LiuYaoFacts.of(chart, DivCategory.CAREER);
      // 月建申金：五爻子水得令生 → 相
      expect(facts.strength.get(5)).assertEqual('相');
      // 旬空申酉：三爻申金 → 三爻旬空
      expect(facts.xunKongYao.join(',')).assertEqual('3');
      // 月建申金冲寅：上爻官鬼寅木月破；日支亥冲巳无爻
      expect(facts.yuePo.join(',')).assertEqual('6');
      expect(facts.riChong.length).assertEqual(0);
      // 四爻戌土动化酉金 → 回头生
      expect(facts.huiTou.get(4)).assertEqual('回头生');
    });
  });
}

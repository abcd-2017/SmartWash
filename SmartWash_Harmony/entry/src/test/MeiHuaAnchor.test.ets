/**
 * 梅花易数时间起卦差分锚点（hypium 单测，对拍基准）。
 *
 * 原样移植安卓 feature/divination/src/test/.../core/meihua/MeiHuaAnchorTest.kt
 * （oracle：docs/divination/references/verify_meihua_anchor.py）。
 * 锚点输入：2026-08-29 12:00 → 上艮(31%8=7) 下坎(38%8=6) 二爻动(38%6=2) → 山水蒙。
 *
 * 注意：本机 SDK 版本不匹配无法构建，hypium 用例需设备/模拟器执行，本文件作为验收工件。
 */

import { describe, beforeAll, it, expect } from '@ohos/hypium';
import { MeiHuaChart, TiYongRelation } from '../main/ets/feature/divination/core/meihua/MeiHuaChart';
import { Trigram } from '../main/ets/feature/divination/core/DivinationCommon';

export default function meiHuaAnchorTest() {
  describe('MeiHuaAnchor', () => {
    let chart: MeiHuaChart;

    beforeAll(() => {
      chart = MeiHuaChart.timeChartAt(2026, 8, 29, 12, 0, 0);
    });

    it('锚点1 上艮下坎二爻动', 0, () => {
      expect(chart.upper).assertEqual(Trigram.GEN);
      expect(chart.lower).assertEqual(Trigram.KAN);
      expect(chart.movingYao).assertEqual(2);
    });

    it('锚点2 本卦山水蒙', 0, () => {
      expect(chart.benName).assertEqual('山水蒙');
      expect(chart.benMark).assertEqual('010001');
    });

    it('锚点3 互卦地雷复与变卦山地剥', 0, () => {
      expect(chart.huName).assertEqual('地雷复');
      expect(chart.huMark).assertEqual('100000');
      expect(chart.bianName).assertEqual('山地剥');
      expect(chart.bianMark).assertEqual('000001');
    });

    it('锚点4 体艮土用坎水体克用', 0, () => {
      expect(chart.ti).assertEqual(Trigram.GEN);
      expect(chart.yong).assertEqual(Trigram.KAN);
      expect(chart.tiYongRelation).assertEqual(TiYongRelation.TI_KE_YONG);
    });

    it('数字卦口径一致', 0, () => {
      // 同参数下 数字卦(31,7) 与时间卦同构：31%8=7 艮、38%8=6 坎、38%6=2
      const byNumber = MeiHuaChart.numbersChart(31, 7);
      expect(byNumber.benName).assertEqual('山水蒙');
      expect(byNumber.movingYao).assertEqual(2);
    });
  });
}

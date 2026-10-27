/**
 * 奇门遁甲排盘差分锚点（hypium 单测，对拍基准）。
 *
 * 原样移植安卓 feature/divination/src/test/.../core/qimen/QiMenAnchorTest.kt
 * （oracle：demo/qimen/verify_qimen.js，qfdk/qimen 转盘排法）。
 * 锚点输入：2026-08-29 12:00 → 阴遁七局下元（处暑 147、符头甲戌），旬首己。
 * 全链路落宫逐宫断言（地盘戊落七宫、天盘戊落八宫、值符落三宫、生门落一宫）。
 *
 * 注意：本机 SDK 版本不匹配无法构建，hypium 用例需设备/模拟器执行，本文件作为验收工件。
 */

import { describe, beforeAll, it, expect } from '@ohos/hypium';
import { QiMenChart } from '../main/ets/feature/divination/core/qimen/QiMenChart';

/** 逐宫比对九宫 Map（ArkTS 无 Map 字面量断言，按 1..9 顺序逐项断言） */
function checkMap(map: Map<number, string>, expected: Record<number, string>): void {
  for (let g = 1; g <= 9; g++) {
    expect(map.get(g)).assertEqual(expected[g]);
  }
}

export default function qiMenAnchorTest() {
  describe('QiMenAnchor', () => {
    let chart: QiMenChart;

    beforeAll(() => {
      chart = QiMenChart.compileAt(2026, 8, 29, 12, 0, 0);
    });

    it('锚点1 定局阴遁七局下元旬首己', 0, () => {
      expect(chart.jieQiName).assertEqual('处暑');
      expect(chart.yinYang).assertEqual('阴遁');
      expect(chart.juNumber).assertEqual(7);
      expect(chart.yuan).assertEqual('下元');
      expect(chart.xunShouYi).assertEqual('己');
      expect(chart.siZhu.year.label).assertEqual('丙午');
      expect(chart.siZhu.month.label).assertEqual('丙申');
      expect(chart.siZhu.day.label).assertEqual('乙亥');
      expect(chart.siZhu.hour.label).assertEqual('壬午');
    });

    it('锚点2 地盘三奇六仪逐宫', 0, () => {
      // 阴遁七局：戊从七宫起逆飞
      checkMap(chart.diPan, {
        1: '丁', 2: '癸', 3: '壬', 4: '辛', 5: '庚', 6: '己', 7: '戊', 8: '乙', 9: '丙',
      });
      let wuGong = 0;
      for (const entry of chart.diPan) {
        if (entry[1] === '戊') {
          wuGong = entry[0];
        }
      }
      expect(wuGong).assertEqual(7);
    });

    it('锚点3 天盘转盘逐宫', 0, () => {
      checkMap(chart.tianPan, {
        1: '癸', 2: '壬', 3: '己', 4: '丁', 5: '庚', 6: '丙', 7: '辛', 8: '戊', 9: '乙',
      });
      // 天盘戊落八宫；值符（旬首仪己）随时干壬落三宫
      let wuGong = 0;
      for (const entry of chart.tianPan) {
        if (entry[1] === '戊') {
          wuGong = entry[0];
        }
      }
      expect(wuGong).assertEqual(8);
      expect(chart.zhiFuLuoGong).assertEqual(3);
      expect(chart.zhiFuYuanGong).assertEqual(6);
      expect(chart.zhiFuXing).assertEqual('天心');
    });

    it('锚点4 九星八门八神逐宫', 0, () => {
      checkMap(chart.jiuXing, {
        1: '禽芮', 2: '天冲', 3: '天心', 4: '天蓬', 5: '', 6: '天英', 7: '天辅', 8: '天柱', 9: '天任',
      });
      checkMap(chart.baMen, {
        1: '生门', 2: '惊门', 3: '杜门', 4: '景门', 5: '', 6: '休门', 7: '开门', 8: '伤门', 9: '死门',
      });
      checkMap(chart.baShen, {
        1: '太阴', 2: '玄武', 3: '值符', 4: '九天', 5: '', 6: '六合', 7: '白虎', 8: '腾蛇', 9: '九地',
      });
      // 生门落一宫；值使门开门落七宫
      expect(chart.baMen.get(1)).assertEqual('生门');
      expect(chart.zhiShiMen).assertEqual('开门');
      expect(chart.zhiShiGong).assertEqual(7);
    });

    it('锚点5 暗干逐宫', 0, () => {
      checkMap(chart.anGan, {
        1: '己', 2: '戊', 3: '乙', 4: '丙', 5: '丁', 6: '癸', 7: '壬', 8: '辛', 9: '庚',
      });
    });

    it('锚点6 空亡驿马', 0, () => {
      expect(chart.kongWangZhi.join(',')).assertEqual('申,酉');
      expect(chart.kongWangGong.join(',')).assertEqual('2,7');
      expect(chart.maZhi).assertEqual('申');
      expect(chart.maGong).assertEqual(2);
    });
  });
}

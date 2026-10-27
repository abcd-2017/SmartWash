/**
 * 大六壬起课锚点（hypium 单测，对拍基准）。
 *
 * 原样移植安卓 feature/divination/src/test/.../core/liuren/LiuRenAnchorTest.kt
 * （课例：ZhouYiLab/docs/大六壬起课-步骤.md 辛卯日乙未时寅将实例）。
 * 三传九宗门本期实现贼克/比用主链；涉害/遥克等抛"暂不支持"。
 *
 * 注意：本机 SDK 版本不匹配无法构建，hypium 用例需设备/模拟器执行，本文件作为验收工件。
 */

import { describe, beforeAll, it, expect } from '@ohos/hypium';
import { DiZhi, GanZhi } from '../main/ets/feature/divination/core/GanZhi';
import {
  LiuRenChart, LiuRenCourse, LiuRenUnsupportedException,
} from '../main/ets/feature/divination/core/liuren/LiuRenChart';

/** 由 DiZhi 构造天盘（偏移 offset 位顺布） */
function buildTianPan(offset: number): Map<DiZhi, DiZhi> {
  const map = new Map<DiZhi, DiZhi>();
  for (const di of DiZhi.ALL) {
    map.set(di, DiZhi.ALL[(di.ordinal + offset) % 12]);
  }
  return map;
}

export default function liuRenAnchorTest() {
  describe('LiuRenAnchor', () => {
    // 文档实例：甲辰年 乙亥月 辛卯日 乙未时，月将寅（功曹，小雪后亥月）
    let chart: LiuRenChart;

    beforeAll(() => {
      chart = LiuRenChart.compile(
        GanZhi.parse('辛卯'),
        GanZhi.parse('乙未'),
        DiZhi.YIN,
        '功曹',
      );
    });

    it('锚点1 月将加时天地盘', 0, () => {
      expect(chart.yueJiang).assertEqual('寅');
      // 寅加未上：天盘(未)=寅、天盘(戌)=巳、天盘(巳)=子、天盘(卯)=戌
      expect(chart.tianPan.get('未')).assertEqual('寅');
      expect(chart.tianPan.get('戌')).assertEqual('巳');
      expect(chart.tianPan.get('巳')).assertEqual('子');
      expect(chart.tianPan.get('卯')).assertEqual('戌');
    });

    it('锚点2 四课巳辛子巳戌卯巳戌', 0, () => {
      const expected: [string, string][] = [
        ['巳', '辛'],
        ['子', '巳'],
        ['戌', '卯'],
        ['巳', '戌'],
      ];
      for (let index = 0; index < expected.length; index++) {
        expect(chart.courses[index].upper).assertEqual(expected[index][0]);
        expect(chart.courses[index].lower).assertEqual(expected[index][1]);
      }
      // 一课为干课（辛寄戌）
      expect(chart.courses[0].lowerIsGan).assertTrue();
      // 三课下神为日支卯
      expect(chart.courses[2].lower).assertEqual('卯');
    });

    it('锚点3 三传重审课戌巳子', 0, () => {
      // 下贼上唯一（三课卯木克戌土）→ 重审课；中末传依次天盘传出
      expect(chart.sanChuan === null ? '' : chart.sanChuan.join(',')).assertEqual('戌,巳,子');
      expect(chart.keTi).assertEqual('重审课');
      expect(chart.jiuZongMen).assertEqual('贼克');
      expect(chart.unsupportedNote).assertNull();
    });

    it('锚点4 昼占贵人与十二天将', 0, () => {
      // 未时昼占，辛日昼贵在午；贵人临午（巳午未申酉戌）→ 逆布
      expect(chart.isDay).assertTrue();
      expect(chart.guiRen).assertEqual('午');
      expect(chart.tianJiang.get('午')).assertEqual('贵人');
      expect(chart.tianJiang.get('巳')).assertEqual('螣蛇');
      // 三传所乘：戌乘太常、巳乘螣蛇、子乘天空
      expect(chart.sanChuanJiang === null ? '' : chart.sanChuanJiang.join(','))
        .assertEqual('太常,螣蛇,天空');
    });

    it('比用课甲辰日丑时子将', 0, () => {
      // 甲辰日丑时子将：三处下贼上（丑/甲、子/丑、卯/辰），甲日阳干比用取阳支子 → 初传子
      const byYong = LiuRenChart.compile(GanZhi.parse('甲辰'), GanZhi.parse('乙丑'), DiZhi.ZI, '神后');
      expect(byYong.courses[0].xiaZeShang).assertTrue();
      expect(byYong.sanChuan === null ? '' : byYong.sanChuan.join(',')).assertEqual('子,亥,戌');
      expect(byYong.keTi).assertEqual('比用课');
    });

    it('元首课甲子日寅时午将', 0, () => {
      // 甲子日寅时午将：上克下唯一（辰土克子水）→ 元首课，三传辰申子
      const shouXiang = LiuRenChart.compile(GanZhi.parse('甲子'), GanZhi.parse('丙寅'), DiZhi.WU, '胜光');
      expect(shouXiang.courses[2].upper).assertEqual('辰');
      expect(shouXiang.courses[2].shangKeXia).assertTrue();
      expect(shouXiang.sanChuan === null ? '' : shouXiang.sanChuan.join(',')).assertEqual('辰,申,子');
      expect(shouXiang.keTi).assertEqual('元首课');
    });

    it('比用取与日干俱比之上神', 0, () => {
      // 合成课例：两处上克下（申金克甲木、丑土克子水），甲日阳干 → 比用取阳支申
      const tianPan = buildTianPan(2);
      const courses: LiuRenCourse[] = [
        new LiuRenCourse('一课', '申', '甲', true, true, false),
        new LiuRenCourse('三课', '丑', '子', false, true, false),
      ];
      const result = LiuRenChart.resolveSanChuan(GanZhi.parse('甲子'), tianPan, courses);
      expect(result.keTi).assertEqual('比用课');
      expect(result.chuan.map((d) => d.label).join(',')).assertEqual('申,戌,子');
    });

    it('俱比涉害暂不支持', 0, () => {
      // 两处下贼上且上神俱阳（辰、戌）→ 涉害（本期 TODO）
      const tianPan = buildTianPan(2);
      const courses: LiuRenCourse[] = [
        new LiuRenCourse('一课', '辰', '甲', true, false, true),
        new LiuRenCourse('四课', '戌', '寅', false, false, true),
      ];
      let gate = '';
      try {
        LiuRenChart.resolveSanChuan(GanZhi.parse('甲子'), tianPan, courses);
      } catch (e) {
        if (e instanceof LiuRenUnsupportedException) {
          gate = e.gate;
        }
      }
      expect(gate).assertEqual('涉害');
    });

    it('伏吟返吟暂不支持', 0, () => {
      // 伏吟：月将等于占时（寅将寅时）→ compile 不抛出，以 unsupportedNote 呈现
      const fuYin = LiuRenChart.compile(GanZhi.parse('甲寅'), GanZhi.parse('丙寅'), DiZhi.YIN, '功曹');
      expect(fuYin.sanChuan).assertNull();
      expect(fuYin.unsupportedNote !== null && fuYin.unsupportedNote.includes('伏吟')).assertTrue();

      // 返吟：月将临冲位（子将午时）
      const fanYin = LiuRenChart.compile(GanZhi.parse('甲辰'), GanZhi.parse('庚午'), DiZhi.ZI, '神后');
      expect(fanYin.sanChuan).assertNull();
      expect(fanYin.unsupportedNote !== null && fanYin.unsupportedNote.includes('返吟')).assertTrue();
    });
  });
}

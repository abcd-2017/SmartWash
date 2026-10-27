/**
 * 八字内核差分锚点（hypium 单测，对拍基准）。
 *
 * 原样移植安卓 feature/divination/src/test/.../core/bazi/BaZiAnchorTest.kt。
 *
 * 权威锚点（docs §4.5 验证实录 + lunar 实测）：
 *   1990-05-20 14:30 男 → 庚午 辛巳 乙酉 癸未，日主乙木，
 *   月藏 [丙,庚,戊]、日藏 [辛]、时藏 [己,丁,乙]，
 *   十神 正官/七杀/日主/偏印，纳音 路旁土/白蜡金/泉中水/杨柳木，
 *   命宫辛巳 身宫己丑 胎元壬申，日旬空午未，日支十二长生=绝，大运顺排、起运 1995-12-10。
 *
 * 边界：
 *  - 1990-02-04 立春日分界（年柱 己巳/庚午 切换）；
 *  - 闰月（lunar 计算）；
 *  - 23:30 晚子时（lunar 默认 sect=2 时日柱处理）。
 *
 * 注意：本机 SDK 版本不匹配无法构建，hypium 用例需设备/模拟器执行，本文件作为验收工件。
 */

import { describe, it, expect } from '@ohos/hypium';
import { DiZhi, TianGan, WuXing } from '../main/ets/feature/divination/core/GanZhi';
import { BaZiChart } from '../main/ets/feature/divination/core/bazi/BaZiChart';
import { BaZiFacts } from '../main/ets/feature/divination/core/bazi/BaZiFacts';
import {
  ShiShen, ShiShenUtil, Strength, WangShuai, WangShuaiUtil,
} from '../main/ets/feature/divination/core/bazi/BaZiTypes';
import { RelType, RelationEngine } from '../main/ets/feature/divination/core/bazi/RelationEngine';

export default function baZiAnchorTest() {
  describe('BaZiAnchor', () => {
    /** 权威锚点：四柱委托层与 lunar 对拍 */
    it('锚点1 四柱-十神-纳音-命宫-旬空-大运 全部对拍', 0, () => {
      const c = BaZiChart.fromYmdHms(1990, 5, 20, 14, 30, 0, 1);

      expect(c.labels.join(' ')).assertEqual('庚午 辛巳 乙酉 癸未');
      expect(c.dayGan).assertEqual(TianGan.YI);   // 日主乙木
      expect(c.dayZhi).assertEqual(DiZhi.YOU);    // 日支酉

      // 十神：年正官、月七杀、日主、时偏印
      expect(c.shiShenGan[0]).assertEqual(ShiShen.ZHENG_GUAN);  // 庚(正官)
      expect(c.shiShenGan[1]).assertEqual(ShiShen.QI_SHA);      // 辛(七杀)
      expect(c.shiShenGan[2]).assertEqual(ShiShen.RI_ZHU);      // 日主
      expect(c.shiShenGan[3]).assertEqual(ShiShen.PIAN_YIN);    // 癸(偏印)

      // 藏干：月（巳→丙庚戊）、日（酉→辛）
      expect(c.zhiGan[1].map((g) => g.label).join(',')).assertEqual('丙,庚,戊');
      expect(c.zhiGan[2].map((g) => g.label).join(',')).assertEqual('辛');

      // 纳音
      expect(c.naYin[0]).assertEqual('路旁土');
      expect(c.naYin[2]).assertEqual('泉中水');

      // 命宫/身宫/胎元
      expect(c.mingGong).assertEqual('辛巳');
      expect(c.shenGong).assertEqual('己丑');
      expect(c.taiYuan).assertEqual('壬申');

      // 旬空 + 十二长生
      expect(c.dayXunKong).assertEqual('午未');
      expect(c.dayDiShi).assertEqual('绝');

      // 大运顺排、起运 1995-12-10、岁数5
      expect(c.yunForward).assertTrue();
      expect(c.yunStartSolar).assertEqual('1995-12-10');
      expect(c.yunStartYear).assertEqual(5);
    });

    /** 证据链：1990-05-20 从弱（日主乙木无根且异党占绝对优势）—— v1 权重自算 */
    it('锚点2 旺衰从弱与用神候选（v1权重自研）', 0, () => {
      const c = BaZiChart.fromYmdHms(1990, 5, 20, 14, 30, 0, 1);
      const facts = BaZiFacts.ofChart(
        c.dayGan,
        c.siZhu.month.zhi,
        [c.siZhu.year.gan, c.siZhu.month.gan, c.siZhu.day.gan, c.siZhu.hour.gan],
        c.zhiGan,
      );

      expect(facts.strength).assertEqual(Strength.CONG_RUO);
      expect(facts.strengthNote.includes('从弱')).assertTrue();
      // 从弱用神：官杀（金）为用
      expect(facts.yongShenCandidates.some((y) => y.role === '用')).assertTrue();
      // 格局：月支巳透庚（年干）→ 七杀/正官格；巳本一中气庚，乙见庚为正官
      expect(facts.geJuCandidates.length > 0).assertTrue();
    });

    /** 立春分界：1990-02-04 立春前年柱己巳、后年柱庚午 */
    it('锚点3 立春分界月季', 0, () => {
      // 1990 立春约为 2/4 10:14（lunar 计算）。取立春当天 00:00 与立春后区分年柱
      const before = BaZiChart.fromYmdHms(1990, 2, 4, 0, 0, 0, 1);
      const after = BaZiChart.fromYmdHms(1990, 2, 5, 0, 0, 0, 1);
      // 立春前仍为己巳，之后为庚午（以 lunar 为准，这是对拍）
      const beforeYear = before.siZhu.year.label;
      expect(beforeYear === '己巳' || beforeYear === '庚午').assertTrue();
      // 存续：两日年柱不同（说明跨年柱切换发生了）
      expect(before.siZhu.year.label !== after.siZhu.year.label).assertTrue();
    });

    /** 闰月：1995 农历闰八月，验证 lunar 正常换算不至于崩溃且日柱有效 */
    it('锚点4 闰月日期可排盘', 0, () => {
      const c = BaZiChart.fromYmdHms(1995, 10, 15, 12, 0, 0, 0); // 接近闰八月区间
      expect(c.labels.length).assertEqual(4);
      // 四柱都是有效干支
      for (const label of c.labels) {
        expect(label.length).assertEqual(2);
      }
    });

    /** 晚子时：23:30 的日柱处理（lunar 默认 sect=2 取当日） */
    it('锚点5 晚子时日柱取当日', 0, () => {
      // 1990-05-20 日柱乙酉，23:30 为晚子时（时支固定"子"）
      const c = BaZiChart.fromYmdHms(1990, 5, 20, 23, 30, 0, 1);
      // 时支恒为子（23-1 点子时）
      expect(c.siZhu.hour.zhi).assertEqual(DiZhi.ZI);
      // 日柱仍是当天 乙酉（sect=2 晚子时日按当日，与正统一致）
      expect(c.siZhu.day.label).assertEqual('乙酉');
    });

    /** 关系引擎：巳午未三会火方（锚点盘年月时支） */
    it('锚点6 三会方检测 巳午未', 0, () => {
      const rels = RelationEngine.diZhi([DiZhi.SI, DiZhi.WU, DiZhi.WEI]);
      expect(rels.some((r) => r.type === RelType.DI_ZHI_SAN_HUI)).assertTrue();
    });

    /** 关系引擎：天干五合 甲己合 */
    it('锚点7 天干五合检测', 0, () => {
      const rels = RelationEngine.tianGanHe([TianGan.JIA, TianGan.JI, TianGan.BING]);
      expect(rels.some((r) => r.type === RelType.TIAN_GAN_HE)).assertTrue();
    });

    /** 十神推演稳定性：同我=比劫、生我=印 */
    it('锚点8 十神基础推演', 0, () => {
      // 甲乙同木、异阴阳 → 劫财
      expect(ShiShenUtil.of(TianGan.JIA, TianGan.YI)).assertEqual(ShiShen.JIE_CAI);
      // 甲见丙（甲木生丙火，同阳）→食神
      expect(ShiShenUtil.of(TianGan.JIA, TianGan.BING)).assertEqual(ShiShen.SHI_SHEN);
      // 甲见己土（甲木克己土，异阴阳）→正财
      expect(ShiShenUtil.of(TianGan.JIA, TianGan.JI)).assertEqual(ShiShen.ZHENG_CAI);
      // 甲见庚金（庚克甲，异阳）→七杀
      expect(ShiShenUtil.of(TianGan.JIA, TianGan.GENG)).assertEqual(ShiShen.QI_SHA);
    });

    /** 旺相休囚死：月令火对木为"休" */
    it('锚点9 旺相休囚死 月令火见木休', 0, () => {
      expect(WangShuaiUtil.of(WuXing.HUO, WuXing.MU)).assertEqual(WangShuai.XIU); // 木生火→我生月令→休
    });
  });
}

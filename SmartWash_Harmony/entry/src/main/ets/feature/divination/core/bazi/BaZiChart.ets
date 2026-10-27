/**
 * 四柱排盘结果 —— 确定性数据全部委托 lunar 的 EightChar（可对拍、权威），
 * 本文件只做 ArkTS 封装 + 证据链字段组织。
 *
 * 逐行移植安卓 feature/divination/src/main/java/com/smartwash/feature/divination/core/bazi/BaZiChart.kt。
 *
 * 锚点（docs §4.5 验证实录）：1990-05-20 14:30 男 → 庚午 辛巳 乙酉 癸未
 * 实测 lunar 输出：四柱 庚午/辛巳/乙酉/癸未；日干乙；月藏 [丙,庚,戊]；日藏 [辛]；
 *  十神 正官/七杀/日主/偏印；纳音 路旁土/白蜡金/泉中水/杨柳木；
 *  命宫 辛巳 身宫 己丑 胎元 壬申；旬空 午未；日支十二长生 绝；大运顺排、起运 1995-12-10。
 *
 * lunar 依赖点 7（04-new-features.md §1.3.7）。
 */

import { EightChar, Solar } from '../lunar/lunar';
import { DiZhi, GanZhi, SiZhu, TianGan } from '../GanZhi';
import { LunarAdapter } from '../LunarAdapter';
import { HiddenGan, ShiShen, ShiShenUtil } from './BaZiTypes';

/** 八字盘面（纯数据字段，可 JSON 序列化） */
export class BaZiChart {
  /** 四柱 */
  readonly siZhu: SiZhu;
  /** 1=男 0=女 */
  readonly gender: number;
  /** 日主（日干） */
  readonly dayGan: TianGan;
  /** 日支 */
  readonly dayZhi: DiZhi;
  /** 年/月/日/时 天干十神（日于=日主） */
  readonly shiShenGan: ShiShen[];
  /** 四支藏干 */
  readonly zhiGan: TianGan[][];
  /** 四柱纳音 */
  readonly naYin: string[];
  /** 胎元 */
  readonly taiYuan: string;
  /** 命宫 */
  readonly mingGong: string;
  /** 身宫 */
  readonly shenGong: string;
  /** 日旬空 */
  readonly dayXunKong: string;
  /** 日支十二长生（对日干） */
  readonly dayDiShi: string;
  /** 大运顺/逆 */
  readonly yunForward: boolean;
  /** 起运公历 */
  readonly yunStartSolar: string;
  /** 起运岁数 */
  readonly yunStartYear: number;

  constructor(siZhu: SiZhu, gender: number, dayGan: TianGan, dayZhi: DiZhi,
    shiShenGan: ShiShen[], zhiGan: TianGan[][], naYin: string[], taiYuan: string,
    mingGong: string, shenGong: string, dayXunKong: string, dayDiShi: string,
    yunForward: boolean, yunStartSolar: string, yunStartYear: number) {
    this.siZhu = siZhu;
    this.gender = gender;
    this.dayGan = dayGan;
    this.dayZhi = dayZhi;
    this.shiShenGan = shiShenGan;
    this.zhiGan = zhiGan;
    this.naYin = naYin;
    this.taiYuan = taiYuan;
    this.mingGong = mingGong;
    this.shenGong = shenGong;
    this.dayXunKong = dayXunKong;
    this.dayDiShi = dayDiShi;
    this.yunForward = yunForward;
    this.yunStartSolar = yunStartSolar;
    this.yunStartYear = yunStartYear;
  }

  get labels(): string[] {
    return this.siZhu.labels();
  }

  /** 出生公历 → 八字盘。gender：1=男 0=女。sect：晚子时处理（2=晚子时日按当日） */
  static fromSolar(solar: Solar, gender: number): BaZiChart {
    return BaZiChart.build(LunarAdapter.eightChar(solar), gender);
  }

  static fromYmdHms(year: number, month: number, day: number,
    hour: number, minute: number, second: number, gender: number): BaZiChart {
    return BaZiChart.fromSolar(Solar.fromYmdHms(year, month, day, hour, minute, second), gender);
  }

  private static build(e: EightChar, gender: number): BaZiChart {
    const siZhu = new SiZhu(
      GanZhi.parse(e.getYear()),
      GanZhi.parse(e.getMonth()),
      GanZhi.parse(e.getDay()),
      GanZhi.parse(e.getTime()),
    );
    const dayGan = siZhu.day.gan;
    const yearGan = siZhu.year.gan;
    const monthGan = siZhu.month.gan;
    const hourGan = siZhu.hour.gan;
    const shiShen: ShiShen[] = [
      yearGan === dayGan ? ShiShen.RI_ZHU : ShiShenUtil.of(dayGan, yearGan),
      monthGan === dayGan ? ShiShen.RI_ZHU : ShiShenUtil.of(dayGan, monthGan),
      ShiShen.RI_ZHU,
      hourGan === dayGan ? ShiShen.RI_ZHU : ShiShenUtil.of(dayGan, hourGan),
    ];
    const zhiGan: TianGan[][] = [siZhu.year.zhi, siZhu.month.zhi, siZhu.day.zhi, siZhu.hour.zhi]
      .map((zhi) => HiddenGan.of(zhi).all);

    const yun = e.getYun(gender === 1 ? 1 : 0);
    const startSolar = yun.getStartSolar();
    return new BaZiChart(
      siZhu,
      gender,
      dayGan,
      siZhu.day.zhi,
      shiShen,
      zhiGan,
      [e.getYearNaYin(), e.getMonthNaYin(), e.getDayNaYin(), e.getTimeNaYin()],
      e.getTaiYuan(),
      e.getMingGong(),
      e.getShenGong(),
      e.getDayXunKong(),
      e.getDayDiShi(),
      yun.isForward(),
      `${startSolar.getYear()}-${p2(startSolar.getMonth())}-${p2(startSolar.getDay())}`,
      yun.getStartYear(),
    );
  }
}

function p2(i: number): string {
  return i < 10 ? `0${i}` : `${i}`;
}

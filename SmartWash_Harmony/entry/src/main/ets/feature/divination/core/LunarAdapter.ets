/**
 * 历法底座薄适配器 —— 封装观象台术数内核对 vendor lunar-javascript 的全部调用。
 *
 * 目的：把 `Solar` / `Lunar` 的 3 个调用点（getBaZi 四柱 / dayXunKong 日旬空 / toString 农历文本）
 * 集中在一处，后续梅花、奇门、六壬、八字与首页天象头复用；若 ArkTS 编译器拒绝 vendor JS + .d.ts，
 * 只需替换本文件的实现（退化为机械移植 .ets），调用方零改动。
 *
 * 蓝本：安卓 feature/divination/src/main/java/com/smartwash/feature/divination/core/liuyao/LiuYaoChart.kt
 * 依赖点 1/2/3（04-new-features.md §1.3.7）。
 */

import { Solar, Lunar, EightChar } from './lunar/lunar';
import { GanZhi, SiZhu } from './GanZhi';

/**
 * 农历要素快照（梅花时间卦用）：年支 / 农历月 / 农历日 / 时支。
 * 对应 lunar 依赖点 4（04-new-features.md §1.3.7）。
 */
export class LunarElements {
  readonly yearZhi: string;
  readonly month: number;
  readonly day: number;
  readonly timeZhi: string;

  constructor(yearZhi: string, month: number, day: number, timeZhi: string) {
    this.yearZhi = yearZhi;
    this.month = month;
    this.day = day;
    this.timeZhi = timeZhi;
  }
}

/** 历法要素快照：四柱 + 日旬空 + 农历文本 */
export class LunarInfo {
  readonly siZhu: SiZhu;
  readonly xunKong: string;
  readonly lunarText: string;

  constructor(siZhu: SiZhu, xunKong: string, lunarText: string) {
    this.siZhu = siZhu;
    this.xunKong = xunKong;
    this.lunarText = lunarText;
  }
}

export class LunarAdapter {
  /** 由公历年月日时分秒取历法要素 */
  static fromYmdHms(year: number, month: number, day: number,
    hour: number, minute: number, second: number): LunarInfo {
    return LunarAdapter.of(Solar.fromYmdHms(year, month, day, hour, minute, second));
  }

  /** 由 Solar 取历法要素 */
  static of(solar: Solar): LunarInfo {
    const lunar: Lunar = solar.getLunar();
    return LunarAdapter.ofLunar(lunar);
  }

  /** 由 Lunar 取历法要素 */
  static ofLunar(lunar: Lunar): LunarInfo {
    const bazi = lunar.getBaZi();
    const siZhu = new SiZhu(
      GanZhi.parse(bazi[0]),
      GanZhi.parse(bazi[1]),
      GanZhi.parse(bazi[2]),
      GanZhi.parse(bazi[3]),
    );
    return new LunarInfo(siZhu, lunar.getDayXunKong(), lunar.toString());
  }

  /** 由 Solar 取农历要素（梅花时间卦：年支/农历月/农历日/时支），依赖点 4 */
  static elements(solar: Solar): LunarElements {
    const lunar: Lunar = solar.getLunar();
    return new LunarElements(lunar.getYearZhi(), lunar.getMonth(), lunar.getDay(), lunar.getTimeZhi());
  }

  /** 最近节气名（奇门定局用），依赖点 5 */
  static prevJieQiName(solar: Solar): string {
    return solar.getLunar().getPrevJieQi(true).getName();
  }

  /** 全年节气表（六壬月将用），依赖点 6 */
  static jieQiTable(solar: Solar): Record<string, Solar> {
    return solar.getLunar().getJieQiTable();
  }

  /** 八字命盘（四柱/纳音/命宫/胎元/身宫/旬空/十二长生/大运），依赖点 7 */
  static eightChar(solar: Solar): EightChar {
    return solar.getLunar().getEightChar();
  }
}

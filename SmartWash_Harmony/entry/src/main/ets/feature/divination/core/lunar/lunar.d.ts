/**
 * lunar-javascript 1.7.7 类型声明（6tail 官方同源库，与安卓 cn.6tail:lunar:1.7.7 同版本）。
 *
 * 用途：观象台术数内核的历法底座。安卓端直接依赖 lunar-java，鸿蒙端 vendor 本 JS 库源码，
 * 两端同版本可对拍，避免自研历法带来的立春分界 / 闰月 / 晚子时等边界错误。
 *
 * 方法名与签名经运行时枚举核对（非凭文档猜测）。只声明观象台实际调用的 API 面，
 * 未覆盖的成员按需在此补充——业务代码禁止用 any 绕过类型。
 */

export class Solar {
  static fromDate(date: Date): Solar;
  static fromYmd(year: number, month: number, day: number): Solar;
  static fromYmdHms(year: number, month: number, day: number, hour: number, minute: number, second: number): Solar;

  getYear(): number;
  getMonth(): number;
  getDay(): number;
  getHour(): number;
  getMinute(): number;
  getSecond(): number;
  getJulianDay(): number;
  getLunar(): Lunar;
  toString(): string;
}

export class JieQi {
  getName(): string;
  getSolar(): Solar;
  isJie(): boolean;
  isQi(): boolean;
}

export class Lunar {
  getYear(): number;
  getMonth(): number;
  getDay(): number;

  /** 四柱干支数组 [年, 月, 日, 时]（如 ['庚午','辛巳','乙酉','癸未']） */
  getBaZi(): Array<string>;
  getYearInGanZhi(): string;
  getMonthInGanZhi(): string;
  getDayInGanZhi(): string;
  getTimeInGanZhi(): string;
  getYearGan(): string;
  getYearZhi(): string;
  getMonthZhi(): string;
  getDayZhi(): string;
  getTimeZhi(): string;

  getYearInChinese(): string;
  getMonthInChinese(): string;
  getDayInChinese(): string;
  getYearShengXiao(): string;

  getYearXunKong(): string;
  getMonthXunKong(): string;
  getDayXunKong(): string;
  getTimeXunKong(): string;

  getJieQiTable(): Record<string, Solar>;
  getPrevJieQi(day?: boolean): JieQi;
  getPrevJieQiWholeDay(day?: boolean): JieQi;
  getNextJieQi(day?: boolean): JieQi;

  getEightChar(): EightChar;
  getYearNaYin(): string;
  getMonthNaYin(): string;
  getDayNaYin(): string;
  toString(): string;
}

export class EightChar {
  setSect(sect: number): void;
  getSect(): number;

  getYear(): string;
  getMonth(): string;
  getDay(): string;
  getTime(): string;
  getYearGan(): string;
  getYearZhi(): string;
  getYearHideGan(): Array<string>;
  getYearNaYin(): string;
  getMonthNaYin(): string;
  getDayNaYin(): string;
  getTimeNaYin(): string;
  getYearDiShi(): string;
  getMonthDiShi(): string;
  getDayDiShi(): string;
  getTimeDiShi(): string;
  getYearShiShenGan(): Array<string>;
  getMonthShiShenGan(): Array<string>;
  getDayShiShenGan(): Array<string>;
  getTimeShiShenGan(): Array<string>;
  getYearShiShenZhi(): Array<string>;
  getMonthShiShenZhi(): Array<string>;
  getDayShiShenZhi(): Array<string>;
  getTimeShiShenZhi(): Array<string>;
  getYearXunKong(): string;
  getMonthXunKong(): string;
  getDayXunKong(): string;
  getTimeXunKong(): string;
  getTaiYuan(): string;
  getMingGong(): string;
  getShenGong(): string;
  getYun(gender: number, sect?: number): Yun;
  getLunar(): Lunar;
  toString(): string;
}

export class Yun {
  getGender(): number;
  isForward(): boolean;
  /** 起运年/月/日/时——注意是「岁数」口径（如 5 岁起运），非公历年月日 */
  getStartYear(): number;
  getStartMonth(): number;
  getStartDay(): number;
  getStartHour(): number;
  /** 起运的公历时刻（安卓八字锚点：1990-05-20 14:30 男 → 1995-12-10），起运日期取此值 */
  getStartSolar(): Solar;
  getLunar(): Lunar;
  /** 返回从第 n 步起的大运数组（长度 10；索引 0 为起运前的那一步，无干支） */
  getDaYun(n: number): Array<DaYun>;
}

export class DaYun {
  getStartIndex(): number;
  getStartYear(): number;
  getEndYear(): number;
  getStartAge(): number;
  getEndAge(): number;
  getIndex(): number;
  getLunar(): Lunar;
  /** 起运前的那一步（索引 0）无干支，返回空串 */
  getGanZhi(): string;
  getXun(): string;
  getXunKong(): string;
  /** 按索引取流年 */
  getLiuNian(n: number): LiuNian;
  /** 按索引取小运 */
  getXiaoYun(n: number): XiaoYun;
}

export class LiuNian {
  getYear(): number;
  getAge(): number;
  getIndex(): number;
  getGanZhi(): string;
  getLunar(): Lunar;
}

export class XiaoYun {
  getYear(): number;
  getAge(): number;
  getIndex(): number;
  getGanZhi(): string;
  getLunar(): Lunar;
}

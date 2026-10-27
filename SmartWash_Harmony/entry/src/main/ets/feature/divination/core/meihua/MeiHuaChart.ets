/**
 * 梅花易数：时间/数字起卦 + 本互变 + 体用（方案文档 4.1，口诀级实现）。
 *
 * 逐行移植安卓 feature/divination/src/main/java/com/smartwash/feature/divination/core/meihua/MeiHuaChart.kt。
 *
 * 时间卦：(年支数+月数+日数) % 8 = 上卦，+时支数 % 8 = 下卦，总数 % 6 = 动爻；
 * 年支数按子 1 丑 2…亥 12，月/日用农历数，余 0 作 8 / 作 6。
 * 先天卦数：乾一兑二离三震四巽五坎六艮七坤八。
 * 体用：动爻所在之卦为"用"，静卦为"体"；互、变皆论"用"侧。
 */

import { DiZhi, WuXing, ShengKeRelation } from '../GanZhi';
import { Trigram } from '../DivinationCommon';
import { Solar } from '../lunar/lunar';
import { LunarAdapter } from '../LunarAdapter';
import { guaNameOf } from '../liuyao/LiuYaoTables';

/**
 * 64 卦名与 liuyao/LiuYaoTables.ets 的 GUA64 同值（安卓 MeiHuaChart 自带一份重复表）。
 * 为避免双表漂移，此处直接复用 liuyao 的 `guaNameOf`（两表同源同值，已对拍）。
 */

/** 体用生克判定（规则层产出，断语口径见《梅花易数》） */
export class TiYongRelation {
  readonly label: string;
  readonly phrase: string;

  private constructor(label: string, phrase: string) {
    this.label = label;
    this.phrase = phrase;
  }

  static readonly YONG_SHENG_TI: TiYongRelation = new TiYongRelation('用生体', '用生体，主进益之喜');
  static readonly BI_HE: TiYongRelation = new TiYongRelation('比和', '体用比和，诸事顺遂');
  static readonly TI_KE_YONG: TiYongRelation = new TiYongRelation('体克用', '体克用，可成但费力');
  static readonly TI_SHENG_YONG: TiYongRelation = new TiYongRelation('体生用', '体生用，耗泄之力，恐有损耗');
  static readonly YONG_KE_TI: TiYongRelation = new TiYongRelation('用克体', '用克体，所谋受制，宜缓图');

  static readonly ALL: TiYongRelation[] = [
    TiYongRelation.YONG_SHENG_TI, TiYongRelation.BI_HE, TiYongRelation.TI_KE_YONG,
    TiYongRelation.TI_SHENG_YONG, TiYongRelation.YONG_KE_TI,
  ];
}

/** 梅花易数盘面（全部为纯数据字段，可 JSON 序列化） */
export class MeiHuaChart {
  /** 六爻值（自初爻，1=少阳 2=少阴 3=老阳(动) 4=老阴(动)） */
  readonly lines: number[];
  /** 本卦二进制卦码（初爻在前） */
  readonly benMark: string;
  readonly benName: string;
  readonly huMark: string;
  readonly huName: string;
  readonly bianMark: string;
  readonly bianName: string;
  /** 动爻 1..6 */
  readonly movingYao: number;
  /** 上卦 */
  readonly upper: Trigram;
  /** 下卦 */
  readonly lower: Trigram;
  /** 体卦（静卦） */
  readonly ti: Trigram;
  /** 用卦（动爻所在） */
  readonly yong: Trigram;
  readonly tiYongRelation: TiYongRelation;
  /** 互卦五行（用侧） */
  readonly huWuXing: WuXing;
  /** 变卦五行（用侧） */
  readonly bianWuXing: WuXing;
  /** 起卦推演说明（时间卦记录算式，数字卦记录报数） */
  readonly derivation: string | null;

  constructor(lines: number[], benMark: string, benName: string, huMark: string, huName: string,
    bianMark: string, bianName: string, movingYao: number, upper: Trigram, lower: Trigram,
    ti: Trigram, yong: Trigram, tiYongRelation: TiYongRelation, huWuXing: WuXing,
    bianWuXing: WuXing, derivation: string | null) {
    this.lines = lines;
    this.benMark = benMark;
    this.benName = benName;
    this.huMark = huMark;
    this.huName = huName;
    this.bianMark = bianMark;
    this.bianName = bianName;
    this.movingYao = movingYao;
    this.upper = upper;
    this.lower = lower;
    this.ti = ti;
    this.yong = yong;
    this.tiYongRelation = tiYongRelation;
    this.huWuXing = huWuXing;
    this.bianWuXing = bianWuXing;
    this.derivation = derivation;
  }

  get benUpperName(): string {
    return this.upper.label;
  }

  get benLowerName(): string {
    return this.lower.label;
  }

  /** 时间卦：起卦时刻（公历年月日时分秒） */
  static timeChartAt(year: number, month: number, day: number,
    hour: number, minute: number, second: number): MeiHuaChart {
    return MeiHuaChart.timeChart(Solar.fromYmdHms(year, month, day, hour, minute, second));
  }

  /** 时间卦：起卦时刻（公历）。lunar 依赖点 4 */
  static timeChart(solar: Solar): MeiHuaChart {
    const lunar = LunarAdapter.elements(solar);
    const zhiNian = DiZhi.fromChar(lunar.yearZhi.charAt(0)).ordinal + 1;
    const yue = lunar.month;
    const ri = lunar.day;
    const zhiShi = DiZhi.fromChar(lunar.timeZhi.charAt(0)).ordinal + 1;

    const upperTotal = zhiNian + yue + ri;
    const total = upperTotal + zhiShi;
    const upperNumber = MeiHuaChart.mod(upperTotal, 8);
    const lowerNumber = MeiHuaChart.mod(total, 8);
    const moving = MeiHuaChart.mod(total, 6);

    const derivation = `年支数${zhiNian} + 月${yue} + 日${ri} = ${upperTotal} → 上卦取${upperNumber}；` +
      `加时支数${zhiShi} = ${total} → 下卦取${lowerNumber}、动爻取${moving}`;

    return MeiHuaChart.build(upperNumber, lowerNumber, moving, derivation);
  }

  /** 数字卦：随报两数，上卦取首数，下卦/动爻取两数之和（同上取模） */
  static numbersChart(a: number, b: number): MeiHuaChart {
    const upperNumber = MeiHuaChart.mod(a, 8);
    const lowerNumber = MeiHuaChart.mod(a + b, 8);
    const moving = MeiHuaChart.mod(a + b, 6);
    return MeiHuaChart.build(
      upperNumber, lowerNumber, moving,
      `报数${a}、${b} → 上卦取${a}，下卦取${a + b}，动爻取${a + b}`,
    );
  }

  /** 由上/下卦先天数与动爻组装本互变与体用 */
  static build(upperNumber: number, lowerNumber: number, moving: number,
    derivation: string | null): MeiHuaChart {
    const upper = Trigram.ALL.find((t) => t.number === upperNumber);
    const lower = Trigram.ALL.find((t) => t.number === lowerNumber);
    if (upper === undefined || lower === undefined) {
      throw new Error(`未知先天卦数: upper=${upperNumber} lower=${lowerNumber}`);
    }
    const benMark = lower.binary + upper.binary;

    // 统一爻值编码：阳=少阳(1) 阴=少阴(2)，动爻改记 老阳(3)/老阴(4)
    const lines: number[] = [];
    for (let index = 0; index < benMark.length; index++) {
      const yang = benMark.charAt(index) === '1';
      if (yang && index === moving - 1) {
        lines.push(3);
      } else if (!yang && index === moving - 1) {
        lines.push(4);
      } else if (yang) {
        lines.push(1);
      } else {
        lines.push(2);
      }
    }

    // 互卦：取本卦 234 爻为下互、345 爻为上互（爻序自初爻 0 起）
    const benLines = benMark.split('');
    const huLower = benLines[1] + benLines[2] + benLines[3];
    const huUpper = benLines[2] + benLines[3] + benLines[4];
    const huMark = huLower + huUpper;

    // 变卦：动爻翻转
    let bianMark = '';
    for (let index = 0; index < benMark.length; index++) {
      if (index === moving - 1) {
        bianMark += benMark.charAt(index) === '1' ? '0' : '1';
      } else {
        bianMark += benMark.charAt(index);
      }
    }

    // 体用：动爻在下卦(1..3) → 下卦为用、上卦为体；反之亦然
    const movingInLower = moving <= 3;
    const ti = movingInLower ? upper : lower;
    const yong = movingInLower ? lower : upper;

    let relation: TiYongRelation;
    switch (ti.wuXing.relationTo(yong.wuXing)) {
      case ShengKeRelation.SHENG_WO:
        relation = TiYongRelation.YONG_SHENG_TI;
        break;
      case ShengKeRelation.BI_HE:
        relation = TiYongRelation.BI_HE;
        break;
      case ShengKeRelation.WO_KE:
        relation = TiYongRelation.TI_KE_YONG;
        break;
      case ShengKeRelation.WO_SHENG:
        relation = TiYongRelation.TI_SHENG_YONG;
        break;
      default:
        relation = TiYongRelation.YONG_KE_TI;
        break;
    }

    return new MeiHuaChart(
      lines, benMark, guaNameOf(benMark), huMark, guaNameOf(huMark),
      bianMark, guaNameOf(bianMark), moving, upper, lower, ti, yong, relation,
      Trigram.fromBinary(huMark.substring(3, 6)).wuXing,
      Trigram.fromBinary(bianMark.substring(3, 6)).wuXing,
      derivation,
    );
  }

  /** 取模，余 0 作 base */
  private static mod(value: number, base: number): number {
    const r = value % base;
    return r === 0 ? base : r;
  }
}

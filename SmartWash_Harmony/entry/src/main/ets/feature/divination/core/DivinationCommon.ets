/**
 * 四术公共枚举：方法标识、所问领域、经卦。
 *
 * 蓝本：安卓 feature/divination/src/main/java/com/smartwash/feature/divination/core/DivinationCommon.kt
 */

import { WuXing } from './GanZhi';

/** 起卦方法 */
export class DivMethod {
  readonly id: string;

  private constructor(id: string) {
    this.id = id;
  }

  static readonly LIU_YAO: DivMethod = new DivMethod('liuyao');
  static readonly MEI_HUA: DivMethod = new DivMethod('meihua');
  static readonly QI_MEN: DivMethod = new DivMethod('qimen');
  static readonly LIU_REN: DivMethod = new DivMethod('liuren');

  static readonly ALL: DivMethod[] = [
    DivMethod.LIU_YAO, DivMethod.MEI_HUA, DivMethod.QI_MEN, DivMethod.LIU_REN,
  ];

  static fromId(id: string): DivMethod {
    const found = DivMethod.ALL.find((m) => m.id === id);
    if (found === undefined) {
      throw new Error(`未知起卦方法: ${id}`);
    }
    return found;
  }
}

/** 所问领域（问事页 chips 自动预选 + 用神映射） */
export class DivCategory {
  readonly id: string;

  private constructor(id: string) {
    this.id = id;
  }

  static readonly CAREER: DivCategory = new DivCategory('career');
  static readonly WEALTH: DivCategory = new DivCategory('wealth');
  static readonly LOVE: DivCategory = new DivCategory('love');
  static readonly STUDY: DivCategory = new DivCategory('study');
  static readonly TRAVEL: DivCategory = new DivCategory('travel');
  static readonly OTHER: DivCategory = new DivCategory('other');

  static readonly ALL: DivCategory[] = [
    DivCategory.CAREER, DivCategory.WEALTH, DivCategory.LOVE,
    DivCategory.STUDY, DivCategory.TRAVEL, DivCategory.OTHER,
  ];

  static fromId(id: string): DivCategory {
    const found = DivCategory.ALL.find((c) => c.id === id);
    return found === undefined ? DivCategory.OTHER : found;
  }
}

/** 经卦（三爻卦）：先天卦数 乾一兑二离三震四巽五坎六艮七坤八 */
export class Trigram {
  readonly label: string;
  /** 三爻二进制串（自初爻起，阳 1 阴 0） */
  readonly binary: string;
  readonly wuXing: WuXing;
  /** 先天卦数 1..8 */
  readonly number: number;

  private constructor(label: string, binary: string, wuXing: WuXing, number: number) {
    this.label = label;
    this.binary = binary;
    this.wuXing = wuXing;
    this.number = number;
  }

  static readonly QIAN: Trigram = new Trigram('乾', '111', WuXing.JIN, 1);
  static readonly DUI: Trigram = new Trigram('兑', '110', WuXing.JIN, 2);
  static readonly LI: Trigram = new Trigram('离', '101', WuXing.HUO, 3);
  static readonly ZHEN: Trigram = new Trigram('震', '100', WuXing.MU, 4);
  static readonly XUN: Trigram = new Trigram('巽', '011', WuXing.MU, 5);
  static readonly KAN: Trigram = new Trigram('坎', '010', WuXing.SHUI, 6);
  static readonly GEN: Trigram = new Trigram('艮', '001', WuXing.TU, 7);
  static readonly KUN: Trigram = new Trigram('坤', '000', WuXing.TU, 8);

  static readonly ALL: Trigram[] = [
    Trigram.QIAN, Trigram.DUI, Trigram.LI, Trigram.ZHEN,
    Trigram.XUN, Trigram.KAN, Trigram.GEN, Trigram.KUN,
  ];

  /** 由三爻二进制串（自初爻起）取卦 */
  static fromBinary(mark: string): Trigram {
    const found = Trigram.ALL.find((t) => t.binary === mark);
    if (found === undefined) {
      throw new Error(`未知经卦二进制: ${mark}`);
    }
    return found;
  }
}

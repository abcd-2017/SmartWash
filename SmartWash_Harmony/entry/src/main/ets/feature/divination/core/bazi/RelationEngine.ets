/**
 * 干支关系引擎 —— 天干五合/相冲、地支六合/三合/三会/六冲/三刑/六害。
 * 自研（纯表/规则，零 lunar 依赖），供八字证据链与后续可复用（六爻等）。
 *
 * 逐行移植安卓 feature/divination/src/main/java/com/smartwash/feature/divination/core/bazi/RelationEngine.kt。
 * 依据：docs §4.5「冲合刑会：天干五合、地支六合/三合局/三会方/六冲/刑/害」。
 */

import { DiZhi, SiZhu, TianGan } from '../GanZhi';

/** 天干五合：甲己合土、乙庚合金、丙辛合水、丁壬合木、戊癸合火 */
const TIAN_GAN_HE: Record<string, TianGan> = {};
TIAN_GAN_HE['甲己'] = TianGan.WU;
TIAN_GAN_HE['己甲'] = TianGan.WU;
TIAN_GAN_HE['乙庚'] = TianGan.XIN;
TIAN_GAN_HE['庚乙'] = TianGan.XIN;
TIAN_GAN_HE['丙辛'] = TianGan.GUI;
TIAN_GAN_HE['辛丙'] = TianGan.GUI;
TIAN_GAN_HE['丁壬'] = TianGan.JIA;
TIAN_GAN_HE['壬丁'] = TianGan.JIA;
TIAN_GAN_HE['戊癸'] = TianGan.BING;
TIAN_GAN_HE['癸戊'] = TianGan.BING;

/** 地支六合：子丑土、寅亥木、卯戌火、辰酉金、巳申水、午未土 */
const DI_ZHI_LIU_HE: Record<string, DiZhi> = {
  '子': DiZhi.CHOU, '丑': DiZhi.ZI,
  '寅': DiZhi.HAI, '亥': DiZhi.YIN,
  '卯': DiZhi.XU, '戌': DiZhi.MAO,
  '辰': DiZhi.YOU, '酉': DiZhi.CHEN,
  '巳': DiZhi.SHEN, '申': DiZhi.SI,
  '午': DiZhi.WEI, '未': DiZhi.WU,
};

/** 地支三合局：申子辰水、寅午戌火、巳酉丑金、亥卯未木 */
const DI_ZHI_SAN_HE: DiZhi[][] = [
  [DiZhi.SHEN, DiZhi.ZI, DiZhi.CHEN],
  [DiZhi.YIN, DiZhi.WU, DiZhi.XU],
  [DiZhi.SI, DiZhi.YOU, DiZhi.CHOU],
  [DiZhi.HAI, DiZhi.MAO, DiZhi.WEI],
];

/** 地支三会方：寅卯辰会木、巳午未会火、申酉戌会金、亥子丑会水 */
const DI_ZHI_SAN_HUI: DiZhi[][] = [
  [DiZhi.YIN, DiZhi.MAO, DiZhi.CHEN],
  [DiZhi.SI, DiZhi.WU, DiZhi.WEI],
  [DiZhi.SHEN, DiZhi.YOU, DiZhi.XU],
  [DiZhi.HAI, DiZhi.ZI, DiZhi.CHOU],
];

/** 地支六冲：子午、丑未、寅申、卯酉、辰戌、巳亥 */
const DI_ZHI_CHONG: Record<string, DiZhi> = {};
for (const di of DiZhi.ALL) {
  DI_ZHI_CHONG[di.label] = di.chong();
}

/** 地支三刑：寅巳申、丑戌未 */
const DI_ZHI_XING: DiZhi[][] = [
  [DiZhi.YIN, DiZhi.SI, DiZhi.SHEN],
  [DiZhi.CHOU, DiZhi.XU, DiZhi.WEI],
];

/** 自刑：辰辰、午午、酉酉、亥亥 */
const DI_ZHI_ZI_XING: DiZhi[] = [DiZhi.CHEN, DiZhi.WU, DiZhi.YOU, DiZhi.HAI];

/**
 * 地支六害：子未、丑午、寅巳、卯辰、申亥、酉戌。
 * 注意：与安卓源码一致——本表已定义但当前 `diZhi()` 未消费（六害暂未纳入关系输出）。
 */
const DI_ZHI_HAI: Record<string, DiZhi> = {
  '子': DiZhi.WEI, '未': DiZhi.ZI,
  '丑': DiZhi.WU, '午': DiZhi.CHOU,
  '寅': DiZhi.SI, '巳': DiZhi.YIN,
  '卯': DiZhi.CHEN, '辰': DiZhi.MAO,
  '申': DiZhi.HAI, '亥': DiZhi.SHEN,
  '酉': DiZhi.XU, '戌': DiZhi.YOU,
};

/** 关系类型 */
export class RelType {
  readonly label: string;

  private constructor(label: string) {
    this.label = label;
  }

  static readonly TIAN_GAN_HE: RelType = new RelType('天干五合');
  static readonly DI_ZHI_LIU_HE: RelType = new RelType('六合');
  static readonly DI_ZHI_SAN_HE: RelType = new RelType('三合局');
  static readonly DI_ZHI_SAN_HUI: RelType = new RelType('三会方');
  static readonly CHONG: RelType = new RelType('六冲');
  static readonly XING: RelType = new RelType('三刑');
  static readonly ZI_XING: RelType = new RelType('自刑');
  static readonly HAI: RelType = new RelType('六害');
}

/** 一条支（或干）间的关系 */
export class Relation {
  readonly type: RelType;
  /** 参与的天干（天干五合） */
  readonly gans: TianGan[];
  /** 参与的地支（其余类型） */
  readonly zhis: DiZhi[];

  constructor(type: RelType, gans: TianGan[] = [], zhis: DiZhi[] = []) {
    this.type = type;
    this.gans = gans;
    this.zhis = zhis;
  }
}

/** 干支关系引擎 */
export class RelationEngine {
  /** 天干五合：给定三四个天干，找相合对 */
  static tianGanHe(gans: TianGan[]): Relation[] {
    const result: Relation[] = [];
    for (let i = 0; i < gans.length; i++) {
      for (let j = i + 1; j < gans.length; j++) {
        const product = TIAN_GAN_HE[gans[i].label + gans[j].label];
        if (product !== undefined) {
          result.push(new Relation(RelType.TIAN_GAN_HE, [gans[i], gans[j]], []));
        }
      }
    }
    return result;
  }

  /** 地支关系：查四支中出现的六合/三合/三会/六冲/三刑/六害 */
  static diZhi(zhis: DiZhi[]): Relation[] {
    const result: Relation[] = [];
    const seen = new Set<string>();

    const add = (r: Relation): void => {
      const key = r.type.label + ':' + r.zhis.map((z) => z.label).sort().join(',');
      if (!seen.has(key)) {
        seen.add(key);
        result.push(r);
      }
    };

    const contains = (list: DiZhi[], z: DiZhi): boolean => list.indexOf(z) >= 0;

    // 六合
    for (const key of Object.keys(DI_ZHI_LIU_HE)) {
      const a = DiZhi.fromChar(key.charAt(0));
      const b = DI_ZHI_LIU_HE[key];
      if (contains(zhis, a) && contains(zhis, b)) {
        add(new Relation(RelType.DI_ZHI_LIU_HE, [], [a, b]));
      }
    }
    // 三合局
    for (const set of DI_ZHI_SAN_HE) {
      const contained = zhis.filter((z) => contains(set, z));
      if (contained.length >= 2) {
        add(new Relation(RelType.DI_ZHI_SAN_HE, [], RelationEngine.sortByOrdinal(contained)));
      }
    }
    // 三会方
    for (const set of DI_ZHI_SAN_HUI) {
      const contained = zhis.filter((z) => contains(set, z));
      if (contained.length >= 2) {
        add(new Relation(RelType.DI_ZHI_SAN_HUI, [], RelationEngine.sortByOrdinal(contained)));
      }
    }
    // 六冲
    for (const key of Object.keys(DI_ZHI_CHONG)) {
      const a = DiZhi.fromChar(key.charAt(0));
      const b = DI_ZHI_CHONG[key];
      if (contains(zhis, a) && contains(zhis, b)) {
        add(new Relation(RelType.CHONG, [], [a, b]));
      }
    }
    // 三刑
    for (const set of DI_ZHI_XING) {
      const contained = zhis.filter((z) => contains(set, z));
      if (contained.length >= 2) {
        add(new Relation(RelType.XING, [], RelationEngine.sortByOrdinal(contained)));
      }
    }
    // 自刑
    for (const z of DI_ZHI_ZI_XING) {
      let count = 0;
      for (const item of zhis) {
        if (item === z) {
          count++;
        }
      }
      if (count >= 2) {
        add(new Relation(RelType.ZI_XING, [], [z, z]));
      }
    }
    return result;
  }

  /** 对整盘（四柱）跑关系 */
  static ofChart(siZhu: SiZhu): Relation[] {
    const gans: TianGan[] = [siZhu.year.gan, siZhu.month.gan, siZhu.day.gan, siZhu.hour.gan];
    const zhis: DiZhi[] = [siZhu.year.zhi, siZhu.month.zhi, siZhu.day.zhi, siZhu.hour.zhi];
    return RelationEngine.tianGanHe(gans).concat(RelationEngine.diZhi(zhis));
  }

  private static sortByOrdinal(zhis: DiZhi[]): DiZhi[] {
    const copy = zhis.slice();
    copy.sort((a, b) => a.ordinal - b.ordinal);
    return copy;
  }
}

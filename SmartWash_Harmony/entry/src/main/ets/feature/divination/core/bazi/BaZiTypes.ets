/**
 * 八字基础类型 —— 观象台第 5 术"四柱"内核（纯 ArkTS，零 ArkUI 依赖）。
 *
 * 逐行移植安卓 feature/divination/src/main/java/com/smartwash/feature/divination/core/bazi/BaZiTypes.kt。
 *
 * 分工：
 *  - 四柱/纳音/命宫/胎元/身宫/旬空/大运 等确定性数据 → 委托 lunar 的 EightChar 对拍（见 BaZiChart）；
 *  - 本文件只放"自研证据链"所需的基础查表与枚举：
 *    十神（子平）、地支藏干（通根/强度打分用）、旺相休囚、格局候选。
 *  依据：docs/divination/references/术数排盘解卦完整方案.md §4.5 + 附录 B。
 */

import { DiZhi, TianGan, WuXing } from '../GanZhi';

/** 十神（以日干为"我"） */
export class ShiShen {
  readonly label: string;

  private constructor(label: string) {
    this.label = label;
  }

  static readonly BI_JIAN: ShiShen = new ShiShen('比肩');
  static readonly JIE_CAI: ShiShen = new ShiShen('劫财');
  static readonly SHI_SHEN: ShiShen = new ShiShen('食神');
  static readonly SHANG_GUAN: ShiShen = new ShiShen('伤官');
  static readonly ZHENG_CAI: ShiShen = new ShiShen('正财');
  static readonly PIAN_CAI: ShiShen = new ShiShen('偏财');
  static readonly ZHENG_GUAN: ShiShen = new ShiShen('正官');
  static readonly QI_SHA: ShiShen = new ShiShen('七杀');
  static readonly ZHENG_YIN: ShiShen = new ShiShen('正印');
  static readonly PIAN_YIN: ShiShen = new ShiShen('偏印');
  static readonly RI_ZHU: ShiShen = new ShiShen('日主');
}

/**
 * 十神推演。以日干为"我"，按五行生克 + 阴阳定正偏：
 *  同我 → 比劫（同阴阳=比肩，异=劫财）
 *  我生 → 食伤（同阴阳=食神，异=伤官）
 *  我克 → 财  （同阴阳=偏财，异=正财）
 *  克我 → 官杀（同阴阳=七杀，异=正官）
 *  生我 → 印  （同阴阳=偏印，异=正印）
 */
export class ShiShenUtil {
  static of(dayGan: TianGan, otherGan: TianGan): ShiShen {
    if (dayGan === otherGan) {
      return ShiShen.RI_ZHU;
    }
    const dayYang = dayGan.ordinal % 2 === 0;   // 甲丙戊庚壬阳
    const otherYang = otherGan.ordinal % 2 === 0;
    const sameYang = dayYang === otherYang;
    const dayWx = dayGan.wuXing;
    const otherWx = otherGan.wuXing;

    if (dayWx === otherWx) {
      // 同我 → 比劫
      return sameYang ? ShiShen.BI_JIAN : ShiShen.JIE_CAI;
    }
    if (dayWx.generates() === otherWx) {
      // 我生 → 食伤
      return sameYang ? ShiShen.SHI_SHEN : ShiShen.SHANG_GUAN;
    }
    if (dayWx.overcomes() === otherWx) {
      // 我克 → 财
      return sameYang ? ShiShen.PIAN_CAI : ShiShen.ZHENG_CAI;
    }
    if (otherWx.overcomes() === dayWx) {
      // 克我 → 官杀
      return sameYang ? ShiShen.QI_SHA : ShiShen.ZHENG_GUAN;
    }
    // 生我 → 印
    return sameYang ? ShiShen.PIAN_YIN : ShiShen.ZHENG_YIN;
  }
}

/** 地支藏干（本气/中气/余气），用于"通根强度"打分。依据主流子平藏干表。 */
export class HiddenGan {
  readonly benQi: TianGan;
  readonly zhongQi: TianGan | null;
  readonly yuQi: TianGan | null;

  constructor(benQi: TianGan, zhongQi: TianGan | null = null, yuQi: TianGan | null = null) {
    this.benQi = benQi;
    this.zhongQi = zhongQi;
    this.yuQi = yuQi;
  }

  get all(): TianGan[] {
    const result: TianGan[] = [this.benQi];
    if (this.zhongQi !== null) {
      result.push(this.zhongQi);
    }
    if (this.yuQi !== null) {
      result.push(this.yuQi);
    }
    return result;
  }

  /**
   * 子午卯酉 藏 1 气；寅申巳亥 / 辰戌丑未 藏本中余三气（部分二气）。
   * key 为地支名（ArkTS 无 enum 作 Map key，故以 label 索引）。
   */
  static readonly TABLE: Record<string, HiddenGan> = {
    '子': new HiddenGan(TianGan.GUI),                              // 子 癸
    '丑': new HiddenGan(TianGan.JI, TianGan.GUI, TianGan.XIN),    // 丑 己癸辛
    '寅': new HiddenGan(TianGan.JIA, TianGan.BING, TianGan.WU),   // 寅 甲丙戊
    '卯': new HiddenGan(TianGan.YI),                              // 卯 乙
    '辰': new HiddenGan(TianGan.WU, TianGan.YI, TianGan.GUI),     // 辰 戊乙癸
    '巳': new HiddenGan(TianGan.BING, TianGan.GENG, TianGan.WU),  // 巳 丙庚戊
    '午': new HiddenGan(TianGan.DING, TianGan.JI),                // 午 丁己
    '未': new HiddenGan(TianGan.JI, TianGan.DING, TianGan.YI),    // 未 己丁乙
    '申': new HiddenGan(TianGan.GENG, TianGan.REN, TianGan.WU),   // 申 庚壬戊
    '酉': new HiddenGan(TianGan.XIN),                             // 酉 辛
    '戌': new HiddenGan(TianGan.WU, TianGan.XIN, TianGan.DING),   // 戌 戊辛丁
    '亥': new HiddenGan(TianGan.REN, TianGan.JIA),                // 亥 壬甲
  };

  static of(zhi: DiZhi): HiddenGan {
    const found = HiddenGan.TABLE[zhi.label];
    if (found === undefined) {
      throw new Error(`未知地支藏干: ${zhi.label}`);
    }
    return found;
  }
}

/** 旺相休囚死：月支五行对日主五行的旺相休囚死（当令者旺、令生者相、我生令者休、令克者囚、令生我者死——以月令为"令"）。 */
export class WangShuai {
  readonly label: string;

  private constructor(label: string) {
    this.label = label;
  }

  static readonly WANG: WangShuai = new WangShuai('旺');
  static readonly XIANG: WangShuai = new WangShuai('相');
  static readonly XIU: WangShuai = new WangShuai('休');
  static readonly QIU: WangShuai = new WangShuai('囚');
  static readonly SI: WangShuai = new WangShuai('死');
}

export class WangShuaiUtil {
  /**
   * @param monthWx 月令五行（当令者）
   * @param wx      被考察五行
   * 规则（以月令为令）：
   *   wx == 月令        → 旺（当令）
   *   月令生 wx         → 相（令生）
   *   wx 生月令         → 休（我生令）
   *   月令克 wx         → 囚（令克）
   *   wx 克月令         → 死（我克令）
   */
  static of(monthWx: WuXing, wx: WuXing): WangShuai {
    if (wx === monthWx) {
      return WangShuai.WANG;
    }
    if (monthWx.generates() === wx) {
      return WangShuai.XIANG;   // 月令生我
    }
    if (monthWx.generatedBy() === wx) {
      return WangShuai.XIU;     // 我生月令
    }
    if (monthWx.overcomes() === wx) {
      return WangShuai.QIU;     // 月令克我
    }
    return WangShuai.SI;        // 我克月令
  }
}

/** 格局候选（子平八格 + 从格，仅"候选 + 依据"，不下单一结论） */
export class GeJu {
  readonly label: string;

  private constructor(label: string) {
    this.label = label;
  }

  // 普通八格：月令藏干透出定格
  static readonly ZHENG_GUAN: GeJu = new GeJu('正官格');
  static readonly QI_SHA: GeJu = new GeJu('七杀格');
  static readonly ZHENG_CAI: GeJu = new GeJu('正财格');
  static readonly PIAN_CAI: GeJu = new GeJu('偏财格');
  static readonly ZHENG_YIN: GeJu = new GeJu('正印格');
  static readonly PIAN_YIN: GeJu = new GeJu('偏印格');
  static readonly SHI_SHEN: GeJu = new GeJu('食神格');
  static readonly SHANG_GUAN: GeJu = new GeJu('伤官格');
  static readonly BI_JIAN: GeJu = new GeJu('建禄格');
  static readonly JIE_CAI: GeJu = new GeJu('羊刃格');
  // 从格：身极弱而满盘异党 → 从
  static readonly CONG_CAI: GeJu = new GeJu('从财格');
  static readonly CONG_SHA: GeJu = new GeJu('从杀格');
  static readonly CONG_ER: GeJu = new GeJu('从儿格');
  static readonly CONG_RUO: GeJu = new GeJu('从弱格');
  static readonly CONG_WANG: GeJu = new GeJu('从旺格');
  static readonly CONG_QING: GeJu = new GeJu('从强格');
  // 特殊
  static readonly QU_ZHI: GeJu = new GeJu('曲直格');
  static readonly YAN_SHANG: GeJu = new GeJu('炎上格');
  static readonly JIA_SE: GeJu = new GeJu('稼穑格');
  static readonly CONG_GE: GeJu = new GeJu('从革格');
  static readonly RUN_XIA: GeJu = new GeJu('润下格');
}

/**
 * 日主强弱档位（自研证据链用，明确标注"v1 示例权重"，非权威）。
 * 完全仿逆向报告的 9 档（身旺/偏旺/平和/偏弱/身弱/从强/从弱/假从强/假从弱），
 * 但打分公式由我们自研（逆向往弱打分有 bug，不能照抄）。
 */
export class Strength {
  readonly label: string;

  private constructor(label: string) {
    this.label = label;
  }

  static readonly SHEN_WANG: Strength = new Strength('身旺');
  static readonly PIAN_WANG: Strength = new Strength('偏旺');
  static readonly PING_HE: Strength = new Strength('平和');
  static readonly PIAN_RUO: Strength = new Strength('偏弱');
  static readonly SHEN_RUO: Strength = new Strength('身弱');
  static readonly CONG_QIANG: Strength = new Strength('从强');
  static readonly CONG_RUO: Strength = new Strength('从弱');
}

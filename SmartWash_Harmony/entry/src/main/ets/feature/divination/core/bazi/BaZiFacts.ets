/**
 * 八字"证据链"规则层 —— 只产确定性事实标记，不下吉凶结论（仿六爻 LiuYaoFacts 定位）。
 *
 * 逐行移植安卓 feature/divination/src/main/java/com/smartwash/feature/divination/core/bazi/BaZiFacts.kt。
 *
 * 产出三件事：
 *  1. 五行力量打分（旺衰证据链）：天干透出 + 地支藏干通根，按月令旺相休囚加权；
 *  2. 日主强弱档位（身旺/偏旺/平和/偏弱/身弱/从强/从弱）；
 *  3. 格局候选 + 用神/忌神五行候选（只给"候选 + 理由"，不下单一定论）。
 *
 * 权重口径为「v1 示例」，严格自研。数量级参考逆向报告的藏干权重方向，
 * 但**不照抄其有 bug 的累加逻辑**，也**不宣称与问真一致**。UI 需展示"v1 示例权重"。
 *
 * 评分细则（v1）：
 *  天干透出（年/月/时干，日干除外）贡献 1.0；
 *  地支藏干通根：本气 1.0、中气 0.7、余气 0.5；
 *  月令旺相休囚死加权：旺 1.5 / 相 1.2 / 休 1.0 / 囚 0.8 / 死 0.6。
 */

import { DiZhi, TianGan, WuXing } from '../GanZhi';
import { GeJu, ShiShen, ShiShenUtil, Strength, WangShuai, WangShuaiUtil } from './BaZiTypes';

/** 五行全集（安卓 WuXing.entries 等价） */
const WU_XING_ALL: WuXing[] = [WuXing.MU, WuXing.HUO, WuXing.TU, WuXing.JIN, WuXing.SHUI];

/** 某五行的力量构成 */
export class WuXingScore {
  readonly wuXing: WuXing;
  /** 天干透出贡献 */
  readonly tianGanScore: number;
  /** 地支藏干通根贡献 */
  readonly zhiScore: number;
  /** 月令加权后总分 */
  readonly weighted: number;

  constructor(wuXing: WuXing, tianGanScore: number, zhiScore: number, weighted: number) {
    this.wuXing = wuXing;
    this.tianGanScore = tianGanScore;
    this.zhiScore = zhiScore;
    this.weighted = weighted;
  }
}

/** 用神/忌神候选 */
export class YongShenCandidate {
  readonly wuXing: WuXing;
  /** 用 / 喜 / 忌 */
  readonly role: string;
  readonly reason: string;

  constructor(wuXing: WuXing, role: string, reason: string) {
    this.wuXing = wuXing;
    this.role = role;
    this.reason = reason;
  }
}

/** 八字证据链事实标记（不下吉凶结论） */
export class BaZiFacts {
  readonly dayGan: TianGan;
  readonly dayWx: WuXing;
  readonly scores: Map<WuXing, WuXingScore>;
  readonly strength: Strength;
  readonly strengthNote: string;
  readonly geJuCandidates: GeJu[];
  readonly yongShenCandidates: YongShenCandidate[];

  constructor(dayGan: TianGan, dayWx: WuXing, scores: Map<WuXing, WuXingScore>, strength: Strength,
    strengthNote: string, geJuCandidates: GeJu[], yongShenCandidates: YongShenCandidate[]) {
    this.dayGan = dayGan;
    this.dayWx = dayWx;
    this.scores = scores;
    this.strength = strength;
    this.strengthNote = strengthNote;
    this.geJuCandidates = geJuCandidates;
    this.yongShenCandidates = yongShenCandidates;
  }

  /**
   * @param dayGan      日主（日干）
   * @param monthZhi    月支（当令）
   * @param ganList     年/月/日/时 四天干
   * @param zhiHidden   年/月/日/时 四支藏干（本/中/余），zhiHidden[1] 为月支
   */
  static ofChart(dayGan: TianGan, monthZhi: DiZhi, ganList: TianGan[],
    zhiHidden: TianGan[][]): BaZiFacts {
    const dayWx = dayGan.wuXing;
    const monthWx = monthZhi.wuXing;

    // ---- 1. 天干透出分 ----
    const tianScore = new Map<WuXing, number>();
    for (const wx of WU_XING_ALL) {
      tianScore.set(wx, 0);
    }
    for (const g of ganList) {
      if (g === dayGan) {
        continue;   // 日干本身是"我"，不给所在五行计党力
      }
      tianScore.set(g.wuXing, BaZiFacts.valueOf(tianScore, g.wuXing) + 1.0);
    }

    // ---- 2. 地支藏干通根分 ----
    const zhiScore = new Map<WuXing, number>();
    for (const wx of WU_XING_ALL) {
      zhiScore.set(wx, 0);
    }
    for (const hidden of zhiHidden) {
      for (let i = 0; i < hidden.length; i++) {
        const w = i === 0 ? 1.0 : (i === 1 ? 0.7 : 0.5);
        const g = hidden[i];
        zhiScore.set(g.wuXing, BaZiFacts.valueOf(zhiScore, g.wuXing) + w);
      }
    }

    // ---- 3. 月令加权 ----
    const factor = new Map<WuXing, number>();
    for (const wx of WU_XING_ALL) {
      const ws = WangShuaiUtil.of(monthWx, wx);
      let f = 0;
      if (ws === WangShuai.WANG) {
        f = 1.5;
      } else if (ws === WangShuai.XIANG) {
        f = 1.2;
      } else if (ws === WangShuai.XIU) {
        f = 1.0;
      } else if (ws === WangShuai.QIU) {
        f = 0.8;
      } else {
        f = 0.6;
      }
      factor.set(wx, f);
    }
    const weighted = new Map<WuXing, number>();
    const scores = new Map<WuXing, WuXingScore>();
    for (const wx of WU_XING_ALL) {
      const t = BaZiFacts.valueOf(tianScore, wx);
      const z = BaZiFacts.valueOf(zhiScore, wx);
      const w = (t + z) * BaZiFacts.valueOf(factor, wx);
      weighted.set(wx, w);
      scores.set(wx, new WuXingScore(wx, t, z, w));
    }

    // ---- 4. 日主强弱：同党（印 + 比劫）vs 异党（官杀 + 财 + 食伤） ----
    let total = 0;
    for (const v of weighted.values()) {
      total += v;
    }
    const help = BaZiFacts.valueOf(weighted, dayWx) + BaZiFacts.valueOf(weighted, dayWx.generatedBy());
    const drain = total - help;

    let strength: Strength;
    let note: string;
    if (help >= drain * 2.2) {
      strength = Strength.SHEN_WANG;
      note = `同党(${help.toFixed(1)}) ≥ 异党2.2倍(${(drain * 2.2).toFixed(1)})，身旺`;
    } else if (help >= drain * 1.4) {
      strength = Strength.PIAN_WANG;
      note = `同党(${help.toFixed(1)}) 明显大于异党(${drain.toFixed(1)})，偏旺`;
    } else if (help <= drain * 0.45) {
      strength = Strength.SHEN_RUO;
      note = `同党(${help.toFixed(1)}) 远小于异党(${drain.toFixed(1)})，身弱`;
    } else if (help <= drain * 0.72) {
      strength = Strength.PIAN_RUO;
      note = `同党(${help.toFixed(1)}) 小于异党(${drain.toFixed(1)})，偏弱`;
    } else {
      strength = Strength.PING_HE;
      note = `同党(${help.toFixed(1)}) 与异党(${drain.toFixed(1)}) 接近，中和`;
    }

    // 从格修正：日主无根（日支/月支本气无日主五行）且同党占比极小 → 从弱；极大 → 从强
    const dayRoot = BaZiFacts.valueOf(zhiScore, dayWx) > 0.5;
    const dayRatio = total > 0 ? BaZiFacts.valueOf(weighted, dayWx) / total : 0.0;
    if (strength === Strength.SHEN_RUO && !dayRoot && dayRatio < 0.15) {
      strength = Strength.CONG_RUO;
      note = `日主无根且同党占比 ${(dayRatio * 100).toFixed(1)}% < 15% → 从弱`;
    } else if (strength === Strength.SHEN_WANG && dayRatio > 0.85) {
      strength = Strength.CONG_QIANG;
      note = `同党占比 ${(dayRatio * 100).toFixed(1)}% > 85% → 从强`;
    }

    return new BaZiFacts(
      dayGan,
      dayGan.wuXing,
      scores,
      strength,
      note,
      BaZiFacts.geJuOf(dayGan, ganList, zhiHidden),
      BaZiFacts.yongShen(dayGan.wuXing, strength, scores),
    );
  }

  // ---- 格局候选：月令藏干透干定格（子平八格），未透则月令本位 ----
  private static geJuOf(dayGan: TianGan, ganList: TianGan[],
    zhiHidden: TianGan[][]): GeJu[] {
    const monthHidden = zhiHidden.length > 1 ? zhiHidden[1] : [];
    if (monthHidden.length === 0) {
      return [];
    }

    const candidates: GeJu[] = [];
    // 月令每个藏干，若透出年/月/时干（不含日干）→ 按其十神定格
    for (const zhiGan of monthHidden) {
      const tou = ganList.some((g) => g === zhiGan && g !== dayGan);
      if (tou) {
        const ss = ShiShenUtil.of(dayGan, zhiGan);
        const geJu = BaZiFacts.gejuByShiShen(ss);
        if (geJu !== null && candidates.indexOf(geJu) < 0) {
          candidates.push(geJu);
        }
      }
    }
    // 未透出 → 月令本位（建禄 / 羊刃）
    if (candidates.length === 0) {
      const benQi = monthHidden[0];
      const ss = ShiShenUtil.of(dayGan, benQi);
      if (ss === ShiShen.BI_JIAN) {
        candidates.push(GeJu.BI_JIAN);
      } else if (ss === ShiShen.JIE_CAI) {
        candidates.push(GeJu.JIE_CAI);
      } else {
        const geJu = BaZiFacts.gejuByShiShen(ss);
        candidates.push(geJu === null ? GeJu.ZHENG_YIN : geJu);
      }
    }
    return candidates;
  }

  /** 十神 → 格局（比劫不入普通格，返回 null 由调用方兜底建禄/羊刃） */
  private static gejuByShiShen(ss: ShiShen): GeJu | null {
    if (ss === ShiShen.ZHENG_GUAN) {
      return GeJu.ZHENG_GUAN;
    }
    if (ss === ShiShen.QI_SHA) {
      return GeJu.QI_SHA;
    }
    if (ss === ShiShen.ZHENG_CAI) {
      return GeJu.ZHENG_CAI;
    }
    if (ss === ShiShen.PIAN_CAI) {
      return GeJu.PIAN_CAI;
    }
    if (ss === ShiShen.ZHENG_YIN) {
      return GeJu.ZHENG_YIN;
    }
    if (ss === ShiShen.PIAN_YIN) {
      return GeJu.PIAN_YIN;
    }
    if (ss === ShiShen.SHI_SHEN) {
      return GeJu.SHI_SHEN;
    }
    if (ss === ShiShen.SHANG_GUAN) {
      return GeJu.SHANG_GUAN;
    }
    return null;
  }

  // ---- 用神/忌神候选 ----
  private static yongShen(dayWx: WuXing, strength: Strength,
    scores: Map<WuXing, WuXingScore>): YongShenCandidate[] {
    const result: YongShenCandidate[] = [];
    const yin = dayWx.generatedBy();          // 生我→印
    const biJie = dayWx;                       // 同我→比劫
    const guanSha = dayWx.overcomeBy();        // 克我→官杀
    const shiShang = dayWx.generates();        // 我生→食伤
    const cai = dayWx.overcomes();             // 我克→财
    if (strength === Strength.SHEN_RUO || strength === Strength.PIAN_RUO) {
      result.push(new YongShenCandidate(yin, '喜', '身弱喜印（其生我）以扶身'));
      result.push(new YongShenCandidate(biJie, '喜', '身弱喜比劫（同五行）以帮身'));
      result.push(new YongShenCandidate(guanSha, '忌', '身弱忌官杀（其克我）更泄身'));
    } else if (strength === Strength.SHEN_WANG || strength === Strength.PIAN_WANG
      || strength === Strength.CONG_QIANG) {
      result.push(new YongShenCandidate(guanSha, '喜', '身旺喜官杀（其克我）制身'));
      result.push(new YongShenCandidate(shiShang, '喜', '身旺喜食伤（我生）泄秀'));
      result.push(new YongShenCandidate(cai, '喜', '身旺喜财（我克）耗身'));
      result.push(new YongShenCandidate(yin, '忌', '身旺忌印（其生我）助旺'));
    } else if (strength === Strength.PING_HE) {
      let weakest = dayWx;
      let minWeighted = Number.MAX_VALUE;
      for (const entry of scores) {
        if (entry[1].weighted < minWeighted) {
          minWeighted = entry[1].weighted;
          weakest = entry[1].wuXing;
        }
      }
      result.push(new YongShenCandidate(weakest, '喜', '中和取最弱五行以平衡'));
    } else if (strength === Strength.CONG_RUO) {
      result.push(new YongShenCandidate(guanSha, '用', '从弱喜官杀（异党主帅）'));
      result.push(new YongShenCandidate(cai, '喜', '从弱喜财（流通）'));
    }
    return result;
  }

  private static valueOf(map: Map<WuXing, number>, key: WuXing): number {
    const v = map.get(key);
    if (v === undefined) {
      throw new Error(`五行缺值: ${key.label}`);
    }
    return v;
  }
}

/**
 * 大六壬起课（方案文档 4.3 + ZhouYiLab/docs/大六壬起课-步骤.md；顺逆与贵人乘神口径
 * 对拍 ZhouYiLab C++ 引擎 src/da_liu_ren）。
 *
 * 逐行移植安卓 feature/divination/src/main/java/com/smartwash/feature/divination/core/liuren/LiuRenChart.kt。
 *
 * 流程：定月将（气中换将）→ 月将加时成天地盘 → 四课（日干寄宫起一课，逐上传出）→
 * 三传（九宗门：本期实现贼克/比用主链，伏吟/返吟/遥克/昴星/别责/八专/涉害暂不支持）→ 十二天将。
 */

import { DiZhi, GanZhi, TianGan, WuXing } from '../GanZhi';
import { Solar } from '../lunar/lunar';
import { LunarAdapter } from '../LunarAdapter';

/** 九宗门未实现时抛出，调用方按"暂不支持"呈现（TODO Phase2 补齐九宗门全量） */
export class LiuRenUnsupportedException extends Error {
  readonly gate: string;

  constructor(gate: string) {
    super(`三传九宗门「${gate}」暂不支持`);
    this.gate = gate;
  }
}

/** 一课（上神/下神） */
export class LiuRenCourse {
  /** 一课..四课 */
  readonly label: string;
  /** 上神（天盘支） */
  readonly upper: string;
  /** 下神（日干或地支） */
  readonly lower: string;
  readonly lowerIsGan: boolean;
  /** 上神克下神（上克下） */
  readonly shangKeXia: boolean;
  /** 下神克上神（下贼上） */
  readonly xiaZeShang: boolean;

  constructor(label: string, upper: string, lower: string, lowerIsGan: boolean,
    shangKeXia: boolean, xiaZeShang: boolean) {
    this.label = label;
    this.upper = upper;
    this.lower = lower;
    this.lowerIsGan = lowerIsGan;
    this.shangKeXia = shangKeXia;
    this.xiaZeShang = xiaZeShang;
  }
}

/** 大六壬盘面（纯数据字段，可 JSON 序列化） */
export class LiuRenChart {
  /** 日干支 */
  readonly dayGanZhi: string;
  /** 时干支 */
  readonly hourGanZhi: string;
  /** 月将地支 */
  readonly yueJiang: string;
  /** 月将名（登明/河魁…） */
  readonly yueJiangName: string;
  /** 地盘支 → 天盘支 */
  readonly tianPan: Map<string, string>;
  readonly courses: LiuRenCourse[];
  /** 初/中/末传（无克且宗门未实现时为 null） */
  readonly sanChuan: string[] | null;
  /** 三传所乘天将 */
  readonly sanChuanJiang: string[] | null;
  /** 课体：元首课/重审课/比用课 */
  readonly keTi: string | null;
  /** 九宗门：贼克/比用（未实现宗门为 null） */
  readonly jiuZongMen: string | null;
  /** 暂不支持说明 */
  readonly unsupportedNote: string | null;
  /** 贵人地支 */
  readonly guiRen: string;
  /** 昼占/夜占 */
  readonly isDay: boolean;
  /** 天盘支 → 天将 */
  readonly tianJiang: Map<string, string>;

  constructor(dayGanZhi: string, hourGanZhi: string, yueJiang: string, yueJiangName: string,
    tianPan: Map<string, string>, courses: LiuRenCourse[], sanChuan: string[] | null,
    sanChuanJiang: string[] | null, keTi: string | null, jiuZongMen: string | null,
    unsupportedNote: string | null, guiRen: string, isDay: boolean, tianJiang: Map<string, string>) {
    this.dayGanZhi = dayGanZhi;
    this.hourGanZhi = hourGanZhi;
    this.yueJiang = yueJiang;
    this.yueJiangName = yueJiangName;
    this.tianPan = tianPan;
    this.courses = courses;
    this.sanChuan = sanChuan;
    this.sanChuanJiang = sanChuanJiang;
    this.keTi = keTi;
    this.jiuZongMen = jiuZongMen;
    this.unsupportedNote = unsupportedNote;
    this.guiRen = guiRen;
    this.isDay = isDay;
    this.tianJiang = tianJiang;
  }

  /** 日干寄宫：甲寄寅、乙寄辰、丙戊寄巳、丁己寄未、庚寄申、辛寄戌、壬寄亥、癸寄丑 */
  static jiGong(gan: TianGan): DiZhi {
    if (gan === TianGan.JIA) {
      return DiZhi.YIN;
    }
    if (gan === TianGan.YI) {
      return DiZhi.CHEN;
    }
    if (gan === TianGan.BING || gan === TianGan.WU) {
      return DiZhi.SI;
    }
    if (gan === TianGan.DING || gan === TianGan.JI) {
      return DiZhi.WEI;
    }
    if (gan === TianGan.GENG) {
      return DiZhi.SHEN;
    }
    if (gan === TianGan.XIN) {
      return DiZhi.XU;
    }
    if (gan === TianGan.REN) {
      return DiZhi.HAI;
    }
    return DiZhi.CHOU;
  }

  /** 月将中气换将表：雨水亥将登明……大寒子将神后（气中换将） */
  static readonly YUE_JIANG_TABLE: Record<string, [DiZhi, string]> = {
    '雨水': [DiZhi.HAI, '登明'],
    '春分': [DiZhi.XU, '河魁'],
    '谷雨': [DiZhi.YOU, '从魁'],
    '小满': [DiZhi.SHEN, '传送'],
    '夏至': [DiZhi.WEI, '小吉'],
    '大暑': [DiZhi.WU, '胜光'],
    '处暑': [DiZhi.SI, '太乙'],
    '秋分': [DiZhi.CHEN, '天罡'],
    '霜降': [DiZhi.MAO, '太冲'],
    '小雪': [DiZhi.YIN, '功曹'],
    '冬至': [DiZhi.CHOU, '大吉'],
    '大寒': [DiZhi.ZI, '神后'],
  };

  private static readonly ZHONG_QI: Set<string> = new Set<string>(Object.keys(LiuRenChart.YUE_JIANG_TABLE));

  /** 由公历时刻定月将（取不晚于占时的最近中气）。lunar 依赖点 6 */
  static yueJiang(solar: Solar): [DiZhi, string] {
    const table = LunarAdapter.jieQiTable(solar);
    const target = LiuRenChart.stamp(solar.getYear(), solar.getMonth(), solar.getDay(),
      solar.getHour(), solar.getMinute());
    let bestStamp = -1;
    let bestName = '';
    for (const key of Object.keys(table)) {
      const name = LiuRenChart.convertQiName(key);
      if (!LiuRenChart.ZHONG_QI.has(name)) {
        continue;
      }
      const s = table[key];
      const stamp = LiuRenChart.stamp(s.getYear(), s.getMonth(), s.getDay(), s.getHour(), s.getMinute());
      if (stamp <= target && stamp > bestStamp) {
        bestStamp = stamp;
        bestName = name;
      }
    }
    if (bestName.length === 0) {
      throw new Error('无法定位月将中气');
    }
    const entry = LiuRenChart.YUE_JIANG_TABLE[bestName];
    return [entry[0], entry[1]];
  }

  private static stamp(year: number, month: number, day: number, hour: number, minute: number): number {
    return year * 100000000 + month * 1000000 + day * 10000 + hour * 100 + minute;
  }

  private static convertQiName(key: string): string {
    if (key === 'DONG_ZHI') {
      return '冬至';
    }
    if (key === 'XIAO_HAN') {
      return '小寒';
    }
    if (key === 'DA_HAN') {
      return '大寒';
    }
    if (key === 'LI_CHUN') {
      return '立春';
    }
    if (key === 'YU_SHUI') {
      return '雨水';
    }
    if (key === 'JING_ZHE') {
      return '惊蛰';
    }
    if (key === 'DA_XUE') {
      return '大雪';
    }
    return key;
  }

  private static readonly TIAN_JIANG: string[] = [
    '贵人', '螣蛇', '朱雀', '六合', '勾陈', '青龙',
    '天空', '白虎', '太常', '玄武', '太阴', '天后',
  ];

  /** 贵人歌诀：甲戊庚牛羊，乙己鼠猴乡，丙丁猪鸡位，壬癸蛇兔藏，六辛逢马虎 */
  private static readonly GUI_REN_TABLE: Record<string, [DiZhi, DiZhi]> = {
    '甲': [DiZhi.CHOU, DiZhi.WEI],
    '戊': [DiZhi.CHOU, DiZhi.WEI],
    '庚': [DiZhi.CHOU, DiZhi.WEI],
    '乙': [DiZhi.ZI, DiZhi.SHEN],
    '己': [DiZhi.ZI, DiZhi.SHEN],
    '丙': [DiZhi.HAI, DiZhi.YOU],
    '丁': [DiZhi.HAI, DiZhi.YOU],
    '壬': [DiZhi.SI, DiZhi.MAO],
    '癸': [DiZhi.SI, DiZhi.MAO],
    '辛': [DiZhi.WU, DiZhi.YIN],
  };

  /** 起课。dayGanZhi/hourGanZhi 为日时干支，yueJiang/yueJiangName 由 yueJiang(solar) 取得 */
  static compile(dayGanZhi: GanZhi, hourGanZhi: GanZhi, yueJiang: DiZhi,
    yueJiangName: string): LiuRenChart {
    // 天地盘：月将加时（月将落时支之上，顺布十二支）
    const offset = (yueJiang.ordinal - hourGanZhi.zhi.ordinal + 12) % 12;
    const tianPan = new Map<DiZhi, DiZhi>();
    for (const di of DiZhi.ALL) {
      tianPan.set(di, DiZhi.ALL[(di.ordinal + offset) % 12]);
    }

    // 四课：一课=日干寄宫之上神，二课=一课上神之上神，三课=日支之上神，四课=三课上神之上神
    const ganGong = LiuRenChart.jiGong(dayGanZhi.gan);
    const upper1 = LiuRenChart.tianOf(tianPan, ganGong);
    const upper2 = LiuRenChart.tianOf(tianPan, upper1);
    const upper3 = LiuRenChart.tianOf(tianPan, dayGanZhi.zhi);
    const upper4 = LiuRenChart.tianOf(tianPan, upper3);

    const course = (label: string, upper: DiZhi, lower: string, lowerIsGan: boolean,
      lowerWx: WuXing): LiuRenCourse => {
      const upperWx = upper.wuXing;
      return new LiuRenCourse(
        label,
        upper.label,
        lower,
        lowerIsGan,
        upperWx === lowerWx.overcomeBy(),
        lowerWx === upperWx.overcomeBy(),
      );
    };

    const courses: LiuRenCourse[] = [
      course('一课', upper1, dayGanZhi.gan.label, true, dayGanZhi.gan.wuXing),
      course('二课', upper2, upper1.label, false, upper1.wuXing),
      course('三课', upper3, dayGanZhi.zhi.label, false, dayGanZhi.zhi.wuXing),
      course('四课', upper4, upper3.label, false, upper3.wuXing),
    ];

    // 十二天将：昼夜定贵人（卯~申昼占），贵人乘贵人支，临亥子丑寅卯辰顺布、余逆布（ZhouYiLab 口径）
    const isDay = hourGanZhi.zhi.ordinal >= 3 && hourGanZhi.zhi.ordinal <= 8;
    const guiEntry = LiuRenChart.GUI_REN_TABLE[dayGanZhi.gan.label];
    const guiRen = isDay ? guiEntry[0] : guiEntry[1];
    const clockwise = guiRen === DiZhi.HAI || guiRen.ordinal <= DiZhi.CHEN.ordinal;
    const step = clockwise ? 1 : -1;
    const tianJiang = new Map<DiZhi, string>();
    for (let i = 0; i < 12; i++) {
      const pos = (guiRen.ordinal + i * step + 24) % 12;
      tianJiang.set(DiZhi.ALL[pos], LiuRenChart.TIAN_JIANG[i]);
    }

    // 三传（九宗门）
    let sanChuan: DiZhi[] | null = null;
    let keTi: string | null = null;
    let jiuZongMen: string | null = null;
    let unsupported: string | null = null;
    try {
      const result = LiuRenChart.resolveSanChuan(dayGanZhi, tianPan, courses);
      sanChuan = result.chuan;
      keTi = result.keTi;
      jiuZongMen = result.gate;
    } catch (e) {
      if (e instanceof LiuRenUnsupportedException) {
        unsupported = e.gate;
      } else {
        throw e;
      }
    }

    // 盘面字段：天盘/天将统一以支名文本呈现（可 JSON 序列化）
    const tianPanText = new Map<string, string>();
    for (const entry of tianPan) {
      tianPanText.set(entry[0].label, entry[1].label);
    }
    const tianJiangText = new Map<string, string>();
    for (const entry of tianJiang) {
      tianJiangText.set(entry[0].label, entry[1]);
    }

    const chuanLabels = sanChuan === null ? null : sanChuan.map((d) => d.label);
    const chuanJiang = sanChuan === null
      ? null
      : sanChuan.map((d) => LiuRenChart.jiangOf(tianJiang, d));

    return new LiuRenChart(
      dayGanZhi.label,
      hourGanZhi.label,
      yueJiang.label,
      yueJiangName,
      tianPanText,
      courses,
      chuanLabels,
      chuanJiang,
      keTi,
      jiuZongMen,
      unsupported === null ? null : `三传九宗门「${unsupported}」暂不支持，敬请期待`,
      guiRen.label,
      isDay,
      tianJiangText,
    );
  }

  /** 三传结果 */
  static resolveSanChuan(dayGanZhi: GanZhi, tianPan: Map<DiZhi, DiZhi>,
    courses: LiuRenCourse[]): ChuanResult {
    // 伏吟/返吟盘先判（ZhouYiLab 口径）
    let fuYin = true;
    let fanYin = true;
    for (const entry of tianPan) {
      if (entry[0] !== entry[1]) {
        fuYin = false;
      }
      if (entry[0] !== entry[1].chong()) {
        fanYin = false;
      }
    }
    if (fuYin) {
      throw new LiuRenUnsupportedException('伏吟');
    }
    if (fanYin) {
      throw new LiuRenUnsupportedException('返吟');
    }

    const dayYang = dayGanZhi.gan.isYang;

    // 下贼上优先，其次上克下；同去重（ZhouYiLab remove_duplicate_lessons）
    const zei = LiuRenChart.dedupe(courses.filter((c) => c.xiaZeShang));
    const ke = LiuRenChart.dedupe(courses.filter((c) => c.shangKeXia && !c.xiaZeShang));

    const chuanOf = (first: DiZhi): DiZhi[] => {
      const mid = LiuRenChart.tianOf(tianPan, first);
      return [first, mid, LiuRenChart.tianOf(tianPan, mid)];
    };

    if (zei.length > 0) {
      if (zei.length === 1) {
        return new ChuanResult(chuanOf(LiuRenChart.zhiOf(zei[0].upper)), '重审课', '贼克');
      }
      return LiuRenChart.biYong(zei, dayYang, chuanOf);
    }
    if (ke.length > 0) {
      if (ke.length === 1) {
        return new ChuanResult(chuanOf(LiuRenChart.zhiOf(ke[0].upper)), '元首课', '贼克');
      }
      return LiuRenChart.biYong(ke, dayYang, chuanOf);
    }
    // 无克 → 遥克/昴星/别责/八专 TODO
    throw new LiuRenUnsupportedException('遥克');
  }

  /** 比用：取与日干阴阳俱比的上神；俱不比/多比 → 涉害 TODO */
  static biYong(candidates: LiuRenCourse[], dayYang: boolean,
    chuanOf: (first: DiZhi) => DiZhi[]): ChuanResult {
    const matched = candidates.filter((c) => LiuRenChart.zhiOf(c.upper).isYang === dayYang);
    if (matched.length === 1) {
      return new ChuanResult(chuanOf(LiuRenChart.zhiOf(matched[0].upper)), '比用课', '比用');
    }
    throw new LiuRenUnsupportedException('涉害');
  }

  private static zhiOf(label: string): DiZhi {
    return DiZhi.fromChar(label.charAt(0));
  }

  /** 课去重：干课按天干、支课按地支（ZhouYiLab remove_duplicate_lessons 口径） */
  private static dedupe(courses: LiuRenCourse[]): LiuRenCourse[] {
    const ganSeen = new Set<string>();
    const zhiSeen = new Set<string>();
    const result: LiuRenCourse[] = [];
    for (const c of courses) {
      const seen = c.lowerIsGan ? ganSeen : zhiSeen;
      if (!seen.has(c.lower)) {
        seen.add(c.lower);
        result.push(c);
      }
    }
    return result;
  }

  private static tianOf(map: Map<DiZhi, DiZhi>, key: DiZhi): DiZhi {
    const v = map.get(key);
    if (v === undefined) {
      throw new Error(`天地盘缺值: ${key.label}`);
    }
    return v;
  }

  private static jiangOf(map: Map<DiZhi, string>, key: DiZhi): string {
    const v = map.get(key);
    if (v === undefined) {
      throw new Error(`天将缺值: ${key.label}`);
    }
    return v;
  }
}

/** 三传结果（内部用） */
export class ChuanResult {
  readonly chuan: DiZhi[];
  readonly keTi: string;
  readonly gate: string;

  constructor(chuan: DiZhi[], keTi: string, gate: string) {
    this.chuan = chuan;
    this.keTi = keTi;
    this.gate = gate;
  }
}

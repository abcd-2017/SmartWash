/**
 * 干支与五行基础类型 —— 观象台内核共享底座（纯 ArkTS，无 ArkUI 依赖）。
 *
 * 蓝本：安卓 feature/divination/src/main/java/com/smartwash/feature/divination/core/GanZhi.kt
 * 口诀依据：docs/divination/references/术数排盘解卦完整方案.md 附录 B。
 *
 * 说明：安卓用 enum 承载「带字段 + 方法」的枚举，ArkTS 枚举不支持成员方法，
 * 故此处用「私有构造 + 静态单例」等价实现，调用面（wx.generates() / zhi.chong()）与安卓一致。
 */

/** 五行（木火土金水），含生克关系推演 */
export class WuXing {
  /** 五行名（木/火/土/金/水） */
  readonly label: string;

  private constructor(label: string) {
    this.label = label;
  }

  static readonly MU: WuXing = new WuXing('木');
  static readonly HUO: WuXing = new WuXing('火');
  static readonly TU: WuXing = new WuXing('土');
  static readonly JIN: WuXing = new WuXing('金');
  static readonly SHUI: WuXing = new WuXing('水');

  /** 我生者：木生火、火生土、土生金、金生水、水生木 */
  generates(): WuXing {
    if (this === WuXing.MU) {
      return WuXing.HUO;
    }
    if (this === WuXing.HUO) {
      return WuXing.TU;
    }
    if (this === WuXing.TU) {
      return WuXing.JIN;
    }
    if (this === WuXing.JIN) {
      return WuXing.SHUI;
    }
    return WuXing.MU;
  }

  /** 生我者 */
  generatedBy(): WuXing {
    if (this === WuXing.HUO) {
      return WuXing.MU;
    }
    if (this === WuXing.TU) {
      return WuXing.HUO;
    }
    if (this === WuXing.JIN) {
      return WuXing.TU;
    }
    if (this === WuXing.SHUI) {
      return WuXing.JIN;
    }
    return WuXing.SHUI;
  }

  /** 我克者：木克土、土克水、水克火、火克金、金克木 */
  overcomes(): WuXing {
    if (this === WuXing.MU) {
      return WuXing.TU;
    }
    if (this === WuXing.HUO) {
      return WuXing.JIN;
    }
    if (this === WuXing.TU) {
      return WuXing.SHUI;
    }
    if (this === WuXing.JIN) {
      return WuXing.MU;
    }
    return WuXing.HUO;
  }

  /** 克我者 */
  overcomeBy(): WuXing {
    if (this === WuXing.TU) {
      return WuXing.MU;
    }
    if (this === WuXing.JIN) {
      return WuXing.HUO;
    }
    if (this === WuXing.SHUI) {
      return WuXing.TU;
    }
    if (this === WuXing.MU) {
      return WuXing.JIN;
    }
    return WuXing.SHUI;
  }

  /** 相对关系（以 this 为「我」） */
  relationTo(other: WuXing): ShengKeRelation {
    if (other === this) {
      return ShengKeRelation.BI_HE;
    }
    if (other === this.generates()) {
      return ShengKeRelation.WO_SHENG;
    }
    if (other === this.generatedBy()) {
      return ShengKeRelation.SHENG_WO;
    }
    if (other === this.overcomes()) {
      return ShengKeRelation.WO_KE;
    }
    return ShengKeRelation.KE_WO;
  }
}

/** 生克关系（以主体为「我」） */
export class ShengKeRelation {
  readonly label: string;

  private constructor(label: string) {
    this.label = label;
  }

  static readonly BI_HE: ShengKeRelation = new ShengKeRelation('比和');
  static readonly WO_SHENG: ShengKeRelation = new ShengKeRelation('我生');
  static readonly SHENG_WO: ShengKeRelation = new ShengKeRelation('生我');
  static readonly WO_KE: ShengKeRelation = new ShengKeRelation('我克');
  static readonly KE_WO: ShengKeRelation = new ShengKeRelation('克我');
}

/** 天干（甲~癸，index 与六十甲子序一致） */
export class TianGan {
  readonly label: string;
  readonly wuXing: WuXing;
  /** 天干序 0..9（甲=0 … 癸=9） */
  readonly ordinal: number;

  private constructor(label: string, wuXing: WuXing, ordinal: number) {
    this.label = label;
    this.wuXing = wuXing;
    this.ordinal = ordinal;
  }

  static readonly JIA: TianGan = new TianGan('甲', WuXing.MU, 0);
  static readonly YI: TianGan = new TianGan('乙', WuXing.MU, 1);
  static readonly BING: TianGan = new TianGan('丙', WuXing.HUO, 2);
  static readonly DING: TianGan = new TianGan('丁', WuXing.HUO, 3);
  static readonly WU: TianGan = new TianGan('戊', WuXing.TU, 4);
  static readonly JI: TianGan = new TianGan('己', WuXing.TU, 5);
  static readonly GENG: TianGan = new TianGan('庚', WuXing.JIN, 6);
  static readonly XIN: TianGan = new TianGan('辛', WuXing.JIN, 7);
  static readonly REN: TianGan = new TianGan('壬', WuXing.SHUI, 8);
  static readonly GUI: TianGan = new TianGan('癸', WuXing.SHUI, 9);

  /** 十天干有序表（下标即位次） */
  static readonly ALL: TianGan[] = [
    TianGan.JIA, TianGan.YI, TianGan.BING, TianGan.DING, TianGan.WU,
    TianGan.JI, TianGan.GENG, TianGan.XIN, TianGan.REN, TianGan.GUI,
  ];

  /** 阳干 / 阴干 */
  get isYang(): boolean {
    return this.ordinal % 2 === 0;
  }

  static fromChar(c: string): TianGan {
    const found = TianGan.ALL.find((t) => t.label === c);
    if (found === undefined) {
      throw new Error(`未知天干: ${c}`);
    }
    return found;
  }
}

/** 地支（子~亥，index 与六十甲子序一致） */
export class DiZhi {
  readonly label: string;
  readonly wuXing: WuXing;
  /** 地支序 0..11（子=0 … 亥=11） */
  readonly ordinal: number;

  private constructor(label: string, wuXing: WuXing, ordinal: number) {
    this.label = label;
    this.wuXing = wuXing;
    this.ordinal = ordinal;
  }

  static readonly ZI: DiZhi = new DiZhi('子', WuXing.SHUI, 0);
  static readonly CHOU: DiZhi = new DiZhi('丑', WuXing.TU, 1);
  static readonly YIN: DiZhi = new DiZhi('寅', WuXing.MU, 2);
  static readonly MAO: DiZhi = new DiZhi('卯', WuXing.MU, 3);
  static readonly CHEN: DiZhi = new DiZhi('辰', WuXing.TU, 4);
  static readonly SI: DiZhi = new DiZhi('巳', WuXing.HUO, 5);
  static readonly WU: DiZhi = new DiZhi('午', WuXing.HUO, 6);
  static readonly WEI: DiZhi = new DiZhi('未', WuXing.TU, 7);
  static readonly SHEN: DiZhi = new DiZhi('申', WuXing.JIN, 8);
  static readonly YOU: DiZhi = new DiZhi('酉', WuXing.JIN, 9);
  static readonly XU: DiZhi = new DiZhi('戌', WuXing.TU, 10);
  static readonly HAI: DiZhi = new DiZhi('亥', WuXing.SHUI, 11);

  /** 十二地支有序表（下标即位次） */
  static readonly ALL: DiZhi[] = [
    DiZhi.ZI, DiZhi.CHOU, DiZhi.YIN, DiZhi.MAO, DiZhi.CHEN, DiZhi.SI,
    DiZhi.WU, DiZhi.WEI, DiZhi.SHEN, DiZhi.YOU, DiZhi.XU, DiZhi.HAI,
  ];

  /** 阳支：子寅辰午申戌 */
  get isYang(): boolean {
    return this.ordinal % 2 === 0;
  }

  /** 六冲：相隔六位 */
  chong(): DiZhi {
    return DiZhi.ALL[(this.ordinal + 6) % 12];
  }

  /** 六合：子丑、寅亥、卯戌、辰酉、巳申、午未 */
  he(): DiZhi {
    if (this === DiZhi.ZI) {
      return DiZhi.CHOU;
    }
    if (this === DiZhi.CHOU) {
      return DiZhi.ZI;
    }
    if (this === DiZhi.YIN) {
      return DiZhi.HAI;
    }
    if (this === DiZhi.HAI) {
      return DiZhi.YIN;
    }
    if (this === DiZhi.MAO) {
      return DiZhi.XU;
    }
    if (this === DiZhi.XU) {
      return DiZhi.MAO;
    }
    if (this === DiZhi.CHEN) {
      return DiZhi.YOU;
    }
    if (this === DiZhi.YOU) {
      return DiZhi.CHEN;
    }
    if (this === DiZhi.SI) {
      return DiZhi.SHEN;
    }
    if (this === DiZhi.SHEN) {
      return DiZhi.SI;
    }
    if (this === DiZhi.WU) {
      return DiZhi.WEI;
    }
    return DiZhi.WU;
  }

  static fromChar(c: string): DiZhi {
    const found = DiZhi.ALL.find((z) => z.label === c);
    if (found === undefined) {
      throw new Error(`未知地支: ${c}`);
    }
    return found;
  }
}

/** 一组干支（如 乙亥），index 为六十甲子序（甲子=0） */
export class GanZhi {
  readonly gan: TianGan;
  readonly zhi: DiZhi;

  constructor(gan: TianGan, zhi: DiZhi) {
    this.gan = gan;
    this.zhi = zhi;
  }

  get label(): string {
    return this.gan.label + this.zhi.label;
  }

  /** 六十甲子序号 0..59 */
  get jiaZiIndex(): number {
    for (let i = 0; i < 60; i++) {
      if (i % 10 === this.gan.ordinal && i % 12 === this.zhi.ordinal) {
        return i;
      }
    }
    throw new Error(`非法干支组合: ${this.label}`);
  }

  /** 所在旬首（甲子/甲戌/甲申/甲午/甲辰/甲寅） */
  get xun(): GanZhi {
    return new GanZhi(TianGan.JIA, DiZhi.ALL[(this.zhi.ordinal - this.gan.ordinal + 12 * 2) % 12]);
  }

  /**
   * 旬空两支：旬首地支前两位。
   * 甲子旬空戌亥、甲戌旬空申酉、甲申旬空午未、甲午旬空辰巳、甲辰旬空寅卯、甲寅旬空子丑
   */
  get xunKong(): [DiZhi, DiZhi] {
    const startZhi = this.xun.zhi.ordinal;
    return [DiZhi.ALL[(startZhi + 10) % 12], DiZhi.ALL[(startZhi + 11) % 12]];
  }

  /** 按六十甲子序构造 */
  static fromJiaZiIndex(index: number): GanZhi {
    const n = ((index % 60) + 60) % 60;
    return new GanZhi(TianGan.ALL[n % 10], DiZhi.ALL[n % 12]);
  }

  static parse(text: string): GanZhi {
    return new GanZhi(TianGan.fromChar(text.charAt(0)), DiZhi.fromChar(text.charAt(1)));
  }
}

/** 四柱（年月日时干支） */
export class SiZhu {
  readonly year: GanZhi;
  readonly month: GanZhi;
  readonly day: GanZhi;
  readonly hour: GanZhi;

  constructor(year: GanZhi, month: GanZhi, day: GanZhi, hour: GanZhi) {
    this.year = year;
    this.month = month;
    this.day = day;
    this.hour = hour;
  }

  labels(): string[] {
    return [this.year.label, this.month.label, this.day.label, this.hour.label];
  }
}

/**
 * 奇门遁甲排盘（时家奇门 · 拆补定元 · 转盘排法）。
 * 蓝本：qfdk/qimen（lib/dipan、jiuxing、bamen、bashen、qimen.js），差分 oracle
 * 为 demo/qimen/verify_qimen.js，口径锁定"拆补 + 转盘"并在盘面声明，不混派。
 *
 * 逐行移植安卓 feature/divination/src/main/java/com/smartwash/feature/divination/core/qimen/QiMenChart.kt。
 *
 * 三奇六仪顺序：戊己庚辛壬癸丁丙乙；旬首遁干：甲子戊 甲戌己 甲申庚 甲午辛 甲辰壬 甲寅癸。
 * 洛书飞宫顺序（不含中五宫）：1 8 3 4 9 2 7 6。
 */

import { DiZhi, GanZhi, SiZhu } from '../GanZhi';
import { Solar } from '../lunar/lunar';
import { LunarAdapter } from '../LunarAdapter';

/** 三奇六仪 */
const SAN_QI_LIU_YI: string[] = ['戊', '己', '庚', '辛', '壬', '癸', '丁', '丙', '乙'];

/** 洛书飞宫顺序（不含中宫5） */
const LUO_SHU_ORDER: number[] = [1, 8, 3, 4, 9, 2, 7, 6];

/** 节气 → (阴阳遁, 上中下元局数三位串)。冬至后阳遁、夏至后阴遁。 */
const JIE_QI_JU: Record<string, [string, string]> = {
  '冬至': ['yang', '174'], '惊蛰': ['yang', '174'],
  '小寒': ['yang', '285'], '大寒': ['yang', '396'],
  '春分': ['yang', '396'], '雨水': ['yang', '963'],
  '清明': ['yang', '417'], '立夏': ['yang', '417'],
  '立春': ['yang', '852'], '谷雨': ['yang', '528'],
  '小满': ['yang', '528'], '芒种': ['yang', '639'],
  '夏至': ['yin', '936'], '白露': ['yin', '936'],
  '小暑': ['yin', '825'], '大暑': ['yin', '714'],
  '秋分': ['yin', '714'], '立秋': ['yin', '258'],
  '寒露': ['yin', '693'], '立冬': ['yin', '693'],
  '处暑': ['yin', '147'], '霜降': ['yin', '582'],
  '小雪': ['yin', '582'], '大雪': ['yin', '471'],
};

/** 旬首 → 六仪 */
const XUN_SHOU_YI: Record<string, string> = {
  '甲子': '戊', '甲戌': '己', '甲申': '庚', '甲午': '辛', '甲辰': '壬', '甲寅': '癸',
};

/** 九星原位（含中宫天禽） */
const BASIC_XING: Record<number, string> = {
  1: '天蓬', 8: '天任', 3: '天冲', 4: '天辅',
  9: '天英', 2: '天芮', 7: '天柱', 6: '天心', 5: '天禽',
};

/** 八门原位（中宫无门） */
const BASIC_MEN: Record<number, string> = {
  1: '休门', 8: '生门', 3: '伤门', 4: '杜门',
  9: '景门', 2: '死门', 7: '惊门', 6: '开门',
};

/** 八神顺序：值符 → 螣蛇 → 太阴 → 六合 → 白虎 → 玄武 → 九地 → 九天 */
const SHEN_ORDER: string[] = ['值符', '腾蛇', '太阴', '六合', '白虎', '玄武', '九地', '九天'];

/** 地支 → 九宫（奇门口径） */
const ZHI_TO_GONG: Record<string, number> = {
  '子': 1, '丑': 8, '寅': 8, '卯': 3, '辰': 4, '巳': 4,
  '午': 9, '未': 2, '申': 2, '酉': 7, '戌': 6, '亥': 6,
};

const YUAN_NAMES: string[] = ['上元', '中元', '下元'];

/** 奇门遁甲盘面（九宫 Map 为纯数据字段，可 JSON 序列化） */
export class QiMenChart {
  readonly siZhu: SiZhu;
  /** 最近节气（定局依据） */
  readonly jieQiName: string;
  /** "阴遁" / "阳遁" */
  readonly yinYang: string;
  /** 局数 1..9 */
  readonly juNumber: number;
  /** 上元 / 中元 / 下元 */
  readonly yuan: string;
  /** 旬首六仪（时柱旬） */
  readonly xunShouYi: string;
  /** 地盘干：宫 → 干 */
  readonly diPan: Map<number, string>;
  /** 天盘干：宫 → 干 */
  readonly tianPan: Map<number, string>;
  /** 九星（坤二宫寄宫记"禽芮"，中宫空） */
  readonly jiuXing: Map<number, string>;
  /** 八门（中宫无门） */
  readonly baMen: Map<number, string>;
  /** 八神（中宫无神） */
  readonly baShen: Map<number, string>;
  /** 暗干 */
  readonly anGan: Map<number, string>;
  /** 值符星 */
  readonly zhiFuXing: string;
  /** 值符落宫（转动后，即时干落宫） */
  readonly zhiFuLuoGong: number;
  /** 值符原宫（旬首六仪地盘宫） */
  readonly zhiFuYuanGong: number;
  /** 值使门 */
  readonly zhiShiMen: string;
  /** 值使门落宫（显示宫，中五寄坤二） */
  readonly zhiShiGong: number;
  /** 值使门真实落宫（含中五，供暗干起局） */
  readonly zhiShiGongRaw: number;
  /** 时柱旬空地支 */
  readonly kongWangZhi: string[];
  /** 空亡宫 */
  readonly kongWangGong: number[];
  /** 驿马地支 */
  readonly maZhi: string;
  /** 驿马宫 */
  readonly maGong: number;

  constructor(siZhu: SiZhu, jieQiName: string, yinYang: string, juNumber: number, yuan: string,
    xunShouYi: string, diPan: Map<number, string>, tianPan: Map<number, string>,
    jiuXing: Map<number, string>, baMen: Map<number, string>, baShen: Map<number, string>,
    anGan: Map<number, string>, zhiFuXing: string, zhiFuLuoGong: number, zhiFuYuanGong: number,
    zhiShiMen: string, zhiShiGong: number, zhiShiGongRaw: number, kongWangZhi: string[],
    kongWangGong: number[], maZhi: string, maGong: number) {
    this.siZhu = siZhu;
    this.jieQiName = jieQiName;
    this.yinYang = yinYang;
    this.juNumber = juNumber;
    this.yuan = yuan;
    this.xunShouYi = xunShouYi;
    this.diPan = diPan;
    this.tianPan = tianPan;
    this.jiuXing = jiuXing;
    this.baMen = baMen;
    this.baShen = baShen;
    this.anGan = anGan;
    this.zhiFuXing = zhiFuXing;
    this.zhiFuLuoGong = zhiFuLuoGong;
    this.zhiFuYuanGong = zhiFuYuanGong;
    this.zhiShiMen = zhiShiMen;
    this.zhiShiGong = zhiShiGong;
    this.zhiShiGongRaw = zhiShiGongRaw;
    this.kongWangZhi = kongWangZhi;
    this.kongWangGong = kongWangGong;
    this.maZhi = maZhi;
    this.maGong = maGong;
  }

  /** 口径声明行，如 "阴遁七局 · 下元 · 拆补转盘" */
  juDeclaration(cnNum: (n: number) => string): string {
    return `${this.yinYang}${cnNum(this.juNumber)}局 · ${this.yuan} · 拆补转盘`;
  }

  /** 排盘入口（公历年月日时分秒） */
  static compileAt(year: number, month: number, day: number,
    hour: number, minute: number, second: number): QiMenChart {
    return QiMenChart.compile(Solar.fromYmdHms(year, month, day, hour, minute, second));
  }

  /** 排盘入口。solar 为当下时刻。lunar 依赖点 5 */
  static compile(solar: Solar): QiMenChart {
    const lunar = LunarAdapter.of(solar);
    const siZhu = lunar.siZhu;

    // 1. 定局：最近节气 + 日柱符头（甲/己日，五日一元）定上中下元
    const jieQiName = LunarAdapter.prevJieQiName(solar);
    const juEntry = JIE_QI_JU[jieQiName];
    if (juEntry === undefined) {
      throw new Error(`未知节气局: ${jieQiName}`);
    }
    const type = juEntry[0];
    const numbers = juEntry[1];
    const fuTou = QiMenChart.fuTouZhi(siZhu.day);
    let yuanIndex: number;
    if (fuTou === DiZhi.ZI || fuTou === DiZhi.WU || fuTou === DiZhi.MAO || fuTou === DiZhi.YOU) {
      yuanIndex = 0;   // 上元
    } else if (fuTou === DiZhi.YIN || fuTou === DiZhi.SHEN || fuTou === DiZhi.SI || fuTou === DiZhi.HAI) {
      yuanIndex = 1;   // 中元
    } else {
      yuanIndex = 2;   // 下元
    }
    const juNumber = Number(numbers.charAt(yuanIndex));
    const isYang = type === 'yang';

    // 2. 旬首六仪（时柱旬）
    const xunShouYi = XUN_SHOU_YI[siZhu.hour.xun.label];

    // 3. 地盘：戊从局数宫起，阳遁顺飞、阴遁逆飞
    const diPan = new Map<number, string>();
    let gong = juNumber;
    for (let i = 0; i < 9; i++) {
      diPan.set(gong, SAN_QI_LIU_YI[i]);
      gong = isYang ? (gong % 9) + 1 : (gong + 7) % 9 + 1;
    }

    // 4. 天盘（转盘）：值符（旬首六仪所在宫）随时干落宫整体转动；中宫寄坤二
    const shiGan = siZhu.hour.gan.label;
    const zhiFuYuanGong = QiMenChart.findGanGong(diPan, xunShouYi) ?? 2;
    const shiGanGong = QiMenChart.findGanGong(diPan, shiGan) ?? zhiFuYuanGong;

    const zhiFuIdx = LUO_SHU_ORDER.indexOf(zhiFuYuanGong);
    const shiGanIdx = LUO_SHU_ORDER.indexOf(shiGanGong);
    // 阳遁：值符宫 → 时干落宫顺时针走 n 步，天盘整体顺转；阴遁逆转（等价于反向步数）
    const tianPanStep = isYang ? QiMenChart.steps(zhiFuIdx, shiGanIdx) : QiMenChart.steps(shiGanIdx, zhiFuIdx);
    const tianPan = new Map<number, string>();
    for (let i = 0; i < 8; i++) {
      const diGong = LUO_SHU_ORDER[i];
      const newIdx = isYang ? (i + tianPanStep) % 8 : (i - tianPanStep + 8) % 8;
      tianPan.set(LUO_SHU_ORDER[newIdx], QiMenChart.valueOf(diPan, diGong));
    }
    tianPan.set(5, QiMenChart.valueOf(diPan, 5));
    const zhiFuLuoGong = shiGanGong;

    // 5. 九星（转盘）：值符星随时干落宫，天禽寄坤二（禽芮合称）
    const zhiFuXing = BASIC_XING[zhiFuYuanGong];
    const jiuXing = new Map<number, string>();
    const xingStep = QiMenChart.steps(zhiFuIdx, shiGanIdx);
    for (let i = 0; i < 8; i++) {
      const originGong = LUO_SHU_ORDER[i];
      const newGong = LUO_SHU_ORDER[(i + xingStep) % 8];
      jiuXing.set(newGong, originGong === 2 ? '禽芮' : BASIC_XING[originGong]);
    }
    jiuXing.set(5, '');

    // 6. 八门（转盘）：值使门加时——从值符宫按宫数顺序阳顺阴逆走时柱旬内序数步
    const zhiShiMen = BASIC_MEN[zhiFuYuanGong];
    const step = siZhu.hour.jiaZiIndex % 10;
    let g = zhiFuYuanGong;
    for (let i = 0; i < step; i++) {
      g = isYang ? (g % 9) + 1 : (g + 7) % 9 + 1;
    }
    const zhiShiGongRaw = g;
    const zhiShiGong = g === 5 ? 2 : g;
    const fromIdx = LUO_SHU_ORDER.indexOf(zhiFuYuanGong);
    const toIdx = LUO_SHU_ORDER.indexOf(zhiShiGong);
    const menStep = QiMenChart.steps(fromIdx, toIdx);
    const baMen = new Map<number, string>();
    baMen.set(5, '');
    for (let i = 0; i < 8; i++) {
      baMen.set(LUO_SHU_ORDER[(i + menStep) % 8], BASIC_MEN[LUO_SHU_ORDER[i]]);
    }

    // 7. 八神：值符落宫起，阳遁顺时针、阴遁逆时针
    const baShen = new Map<number, string>();
    const gongOrder: number[] = isYang ? LUO_SHU_ORDER : [1, 6, 7, 2, 9, 4, 3, 8];
    const zhiFuGongIdx = gongOrder.indexOf(zhiFuLuoGong);
    baShen.set(5, '');
    for (let i = 0; i < gongOrder.length; i++) {
      baShen.set(gongOrder[(zhiFuGongIdx + i) % 8], SHEN_ORDER[i]);
    }

    // 8. 暗干：时干加值使落宫起，按三奇六仪顺序阳顺阴逆排满九宫；甲隐于旬首仪、寄中五
    const anGan = new Map<number, string>();
    const isJia = shiGan === '甲';
    const shiYi = isJia ? xunShouYi : shiGan;
    const anchorGong = isJia ? 5 : zhiShiGongRaw;
    const startIdx = SAN_QI_LIU_YI.indexOf(shiYi);
    let ag = anchorGong;
    for (let i = 0; i < 9; i++) {
      anGan.set(ag, SAN_QI_LIU_YI[(startIdx + i) % 9]);
      ag = isYang ? (ag % 9) + 1 : (ag + 7) % 9 + 1;
    }

    // 9. 空亡与驿马（时柱口径）
    const kong = siZhu.hour.xunKong;
    const kongWangZhi = [kong[0].label, kong[1].label];
    const kongWangGong: number[] = [];
    const kongSeen = new Set<number>();
    for (const z of kongWangZhi) {
      const kg = ZHI_TO_GONG[z];
      if (kg !== undefined && !kongSeen.has(kg)) {
        kongSeen.add(kg);
        kongWangGong.push(kg);
      }
    }
    const hourZhi = siZhu.hour.zhi;
    let maZhi: DiZhi;
    if (hourZhi === DiZhi.YIN || hourZhi === DiZhi.WU || hourZhi === DiZhi.XU) {
      maZhi = DiZhi.SHEN;
    } else if (hourZhi === DiZhi.SHEN || hourZhi === DiZhi.ZI || hourZhi === DiZhi.CHEN) {
      maZhi = DiZhi.YIN;
    } else if (hourZhi === DiZhi.SI || hourZhi === DiZhi.YOU || hourZhi === DiZhi.CHOU) {
      maZhi = DiZhi.HAI;
    } else {
      maZhi = DiZhi.SI;
    }

    return new QiMenChart(
      siZhu, jieQiName, isYang ? '阳遁' : '阴遁', juNumber, YUAN_NAMES[yuanIndex], xunShouYi,
      diPan, tianPan, jiuXing, baMen, baShen, anGan, zhiFuXing, zhiFuLuoGong, zhiFuYuanGong,
      zhiShiMen, zhiShiGong, zhiShiGongRaw, kongWangZhi, kongWangGong, maZhi.label,
      ZHI_TO_GONG[maZhi.label],
    );
  }

  /** 天干在地盘所在宫（甲不现盘、中宫寄坤二；找不到返回 null 由调用方兜底） */
  private static findGanGong(diPan: Map<number, string>, gan: string): number | null {
    if (gan === '甲') {
      return null;
    }
    for (const entry of diPan) {
      if (entry[1] === gan && entry[0] !== 5) {
        return entry[0];
      }
    }
    return diPan.get(5) === gan ? 2 : null;
  }

  /** 顺时针序上的步数（from → to） */
  private static steps(fromIdx: number, toIdx: number): number {
    return (toIdx - fromIdx + 8) % 8;
  }

  /** 符头地支：日柱往前取最近的甲/己日 */
  private static fuTouZhi(day: GanZhi): DiZhi {
    const fuTou = day.jiaZiIndex - (day.jiaZiIndex % 5);
    return DiZhi.ALL[fuTou % 12];
  }

  private static valueOf(map: Map<number, string>, key: number): string {
    const v = map.get(key);
    if (v === undefined) {
      throw new Error(`九宫缺值: ${key}`);
    }
    return v;
  }
}

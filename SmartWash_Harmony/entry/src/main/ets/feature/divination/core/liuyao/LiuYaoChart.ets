/**
 * 六爻纳甲装卦（确定性，零 AI）。
 *
 * 逐行移植安卓 feature/divination/src/main/java/com/smartwash/feature/divination/core/liuyao/LiuYaoChart.kt。
 * 流程（方案文档第三章）：本卦 → 寻世诀安世应 → 认宫诀定宫 → 纳甲干支 → 六亲 →
 * 按日干起六神 → 缺六亲取伏神 → 六冲/六合/游魂/归魂 → 动爻取反得变卦。
 *
 * 爻值编码：1=少阳 2=少阴 3=老阳○(动) 4=老阴×(动)；自初爻到上爻。
 */

import { DiZhi, SiZhu, WuXing, ShengKeRelation } from '../GanZhi';
import { Trigram } from '../DivinationCommon';
import { YaoValue } from '../Yao';
import { Solar } from '../lunar/lunar';
import { LunarAdapter } from '../LunarAdapter';
import {
  NAJIA, SHEN6, SHEN6_ROTATION, QING6, LIUHE_FRAGMENTS, guaNameOf,
} from './LiuYaoTables';

/** 本卦一行（自初爻到上爻） */
export class LiuYaoLine {
  /** 1..6 自下而上 */
  readonly position: number;
  readonly yao: YaoValue;
  /** 纳甲干支，如 丙辰 */
  readonly ganZhi: string;
  /** 爻支五行 */
  readonly zhiWuXing: WuXing;
  /** 六亲（以本宫五行为我） */
  readonly liuQin: string;
  /** 六神（按日干起） */
  readonly liuShen: string;
  /** "世" / "应" / null */
  readonly shiYing: string | null;
  /** 动爻变出干支 */
  readonly bianGanZhi: string | null;
  /** 变爻六亲（以本卦宫五行论） */
  readonly bianLiuQin: string | null;

  constructor(position: number, yao: YaoValue, ganZhi: string, zhiWuXing: WuXing,
    liuQin: string, liuShen: string, shiYing: string | null,
    bianGanZhi: string | null, bianLiuQin: string | null) {
    this.position = position;
    this.yao = yao;
    this.ganZhi = ganZhi;
    this.zhiWuXing = zhiWuXing;
    this.liuQin = liuQin;
    this.liuShen = liuShen;
    this.shiYing = shiYing;
    this.bianGanZhi = bianGanZhi;
    this.bianLiuQin = bianLiuQin;
  }
}

/** 变卦行（六亲以本宫论，静态行与动变行区分展示由 UI 决定） */
export class LiuYaoBianLine {
  readonly position: number;
  readonly ganZhi: string;
  readonly liuQin: string;
  readonly isChanged: boolean;

  constructor(position: number, ganZhi: string, liuQin: string, isChanged: boolean) {
    this.position = position;
    this.ganZhi = ganZhi;
    this.liuQin = liuQin;
    this.isChanged = isChanged;
  }
}

/** 伏神：本宫纳甲中缺失六亲的藏伏之爻 */
export class LiuYaoHidden {
  /** 本宫卦名（如 艮为山） */
  readonly gongName: string;
  /** 爻位(1..6) → (六亲, 干支) */
  readonly seats: Map<number, [string, string]>;

  constructor(gongName: string, seats: Map<number, [string, string]>) {
    this.gongName = gongName;
    this.seats = seats;
  }
}

/** 六爻盘面（全部为纯数据字段，可 JSON 序列化） */
export class LiuYaoChart {
  /** 原始爻值 1..4 */
  readonly lines: number[];
  /** 二进制卦码（初爻在前） */
  readonly mark: string;
  /** 本卦名 */
  readonly name: string;
  /** 卦宫名（艮） */
  readonly gong: string;
  /** 宫五行 */
  readonly gongWuXing: WuXing;
  /** 六冲/六合/游魂/归魂/""（najia 口径，单标签） */
  readonly type: string;
  /** 世爻位 1..6 */
  readonly shiYao: number;
  /** 应爻位 */
  readonly yingYao: number;
  readonly rows: LiuYaoLine[];
  /** 动爻位（1..6） */
  readonly movingPositions: number[];
  /** 变卦名 */
  readonly bianName: string | null;
  readonly bianMark: string | null;
  readonly bianGong: string | null;
  readonly bianType: string | null;
  readonly bianRows: LiuYaoBianLine[] | null;
  readonly hidden: LiuYaoHidden | null;
  readonly siZhu: SiZhu;
  /** 日旬空，如 申酉 */
  readonly xunKong: string;
  /** 农历日期文本 */
  readonly lunarText: string;

  constructor(lines: number[], mark: string, name: string, gong: string, gongWuXing: WuXing,
    type: string, shiYao: number, yingYao: number, rows: LiuYaoLine[],
    movingPositions: number[], bianName: string | null, bianMark: string | null,
    bianGong: string | null, bianType: string | null, bianRows: LiuYaoBianLine[] | null,
    hidden: LiuYaoHidden | null, siZhu: SiZhu, xunKong: string, lunarText: string) {
    this.lines = lines;
    this.mark = mark;
    this.name = name;
    this.gong = gong;
    this.gongWuXing = gongWuXing;
    this.type = type;
    this.shiYao = shiYao;
    this.yingYao = yingYao;
    this.rows = rows;
    this.movingPositions = movingPositions;
    this.bianName = bianName;
    this.bianMark = bianMark;
    this.bianGong = bianGong;
    this.bianType = bianType;
    this.bianRows = bianRows;
    this.hidden = hidden;
    this.siZhu = siZhu;
    this.xunKong = xunKong;
    this.lunarText = lunarText;
  }

  /** 头部声明行：如 "艮宫属土 · 六冲卦" */
  gongDeclaration(): string {
    let out = this.gong + '宫属' + this.gongWuXing.label;
    if (this.type.length > 0) {
      out += ' · ' + this.type + '卦';
    }
    return out;
  }

  /** 六爻摇卦排盘入口（公历年月日时分秒） */
  static compileAt(lines: number[], year: number, month: number, day: number,
    hour: number, minute: number, second: number): LiuYaoChart {
    return LiuYaoChart.compile(lines, Solar.fromYmdHms(year, month, day, hour, minute, second));
  }

  /** 六爻摇卦排盘入口。solar 为起卦时刻（公历） */
  static compile(lines: number[], solar: Solar): LiuYaoChart {
    if (lines.length !== 6) {
      throw new Error('六爻需要 6 个爻值');
    }
    if (lines.some((v) => v < 1 || v > 4)) {
      throw new Error('爻值只能取 1..4');
    }

    const lunar = LunarAdapter.of(solar);
    const siZhu = lunar.siZhu;

    // 卦码：阳爻记 1 阴爻记 0（少阳/老阳为 1，少阴/老阴为 0）
    let mark = '';
    for (const v of lines) {
      mark += (v === 1 || v === 3) ? '1' : '0';
    }

    const shiYing = LiuYaoChart.findShiYing(mark);
    const shi = shiYing[0];
    const ying = shiYing[1];
    const gongTrigram = LiuYaoChart.findPalace(mark, shi);
    const name = guaNameOf(mark);

    // 纳甲（自初爻到上爻）+ 六亲（以本宫五行为我）
    const najia = LiuYaoChart.najiaOf(mark);

    // 六神（按日干起，自初爻顺轮）
    const shenRotation = SHEN6_ROTATION[siZhu.day.gan.ordinal];
    const god6: string[] = [];
    for (let i = 0; i < 6; i++) {
      god6.push(SHEN6[(shenRotation + i) % 6]);
    }

    const movingPositions: number[] = [];
    for (let i = 0; i < 6; i++) {
      if (lines[i] > 2) {
        movingPositions.push(i + 1);
      }
    }

    // 变卦：动爻取反；变爻六亲仍以本卦宫五行为我
    const hasBian = movingPositions.length > 0;
    let bianMark: string | null = null;
    let bianNajia: string[] | null = null;
    if (hasBian) {
      let bm = '';
      for (const v of lines) {
        bm += (v === 2 || v === 3) ? '0' : '1';
      }
      bianMark = bm;
      bianNajia = LiuYaoChart.najiaOf(bm);
    }

    const rows: LiuYaoLine[] = [];
    for (let index = 0; index < najia.length; index++) {
      const gz = najia[index];
      const zhi = DiZhi.fromChar(gz.charAt(1));
      const position = index + 1;
      const bianGz = bianNajia === null ? null : bianNajia[index];
      let bianLiuQin: string | null = null;
      if (bianGz !== null) {
        bianLiuQin = LiuYaoChart.liuQinOf(gongTrigram.wuXing, DiZhi.fromChar(bianGz.charAt(1)).wuXing);
      }
      let shiYingMark: string | null = null;
      if (position === shi) {
        shiYingMark = '世';
      } else if (position === ying) {
        shiYingMark = '应';
      }
      rows.push(new LiuYaoLine(
        position,
        YaoValue.fromCode(lines[index]),
        gz,
        zhi.wuXing,
        LiuYaoChart.liuQinOf(gongTrigram.wuXing, zhi.wuXing),
        god6[index],
        shiYingMark,
        bianGz,
        bianLiuQin,
      ));
    }

    // 变卦行（全部六行，UI 只显动变位）
    let bianRows: LiuYaoBianLine[] | null = null;
    let bianGong: string | null = null;
    let bianType: string | null = null;
    let bianName: string | null = null;
    if (bianMark !== null) {
      const bm = bianMark;
      const bgong = LiuYaoChart.findPalace(bm, LiuYaoChart.findShiYing(bm)[0]);
      bianGong = bgong.label;
      bianType = LiuYaoChart.guaType(bm);
      bianName = guaNameOf(bm);
      const bNajia = LiuYaoChart.najiaOf(bm);
      bianRows = [];
      for (let index = 0; index < bNajia.length; index++) {
        const gz = bNajia[index];
        const zhi = DiZhi.fromChar(gz.charAt(1));
        bianRows.push(new LiuYaoBianLine(
          index + 1,
          gz,
          LiuYaoChart.liuQinOf(bgong.wuXing, zhi.wuXing),
          movingPositions.indexOf(index + 1) >= 0,
        ));
      }
    }

    const qin6 = rows.map((r) => r.liuQin);
    const hidden = buildHidden(gongTrigram, qin6, gongTrigram.wuXing);

    return new LiuYaoChart(
      lines, mark, name, gongTrigram.label, gongTrigram.wuXing,
      LiuYaoChart.guaType(mark), shi, ying, rows, movingPositions,
      bianName, bianMark, bianGong, bianType, bianRows, hidden,
      siZhu, lunar.xunKong, lunar.lunarText,
    );
  }

  // ---------- 寻世诀：天同二世天变五，地同四世地变初；本宫六世三世异，人同游魂人变归 ----------
  // （蓝本 najia/utils.py set_shi_yao，逐分支对拍）
  static findShiYing(mark: string): [number, number] {
    const nei = mark.substring(0, 3); // 内卦（初二三爻）
    const wai = mark.substring(3, 6); // 外卦（四五六爻）
    const same = (i: number): boolean => wai.charAt(i) === nei.charAt(i);

    const shiYao = (shi: number): [number, number] =>
      shi > 3 ? [shi, shi - 3] : [shi, shi + 3];

    // 天同二世天变五
    if (same(2)) {
      if (!same(1) && !same(0)) {
        return shiYao(2);
      }
    } else {
      if (same(1) && same(0)) {
        return shiYao(5);
      }
    }
    // 人同游魂人变归
    if (same(1)) {
      if (!same(0) && !same(2)) {
        return shiYao(4);
      }
    } else {
      if (same(0) && same(2)) {
        return shiYao(3);
      }
    }
    // 地同四世地变初
    if (same(0)) {
      if (!same(1) && !same(2)) {
        return shiYao(4);
      }
    } else {
      if (same(1) && same(2)) {
        return shiYao(1);
      }
    }
    // 本宫六世
    if (wai === nei) {
      return shiYao(6);
    }
    // 三世异
    return shiYao(3);
  }

  // ---------- 认宫诀：一二三六外卦宫，四五游魂内变更；若问归魂何所取，归魂内卦是本宫 ----------
  static findPalace(mark: string, shiYao: number): Trigram {
    const nei = mark.substring(0, 3);
    const wai = mark.substring(3, 6);
    const hun = LiuYaoChart.soulOf(mark);

    // 归魂内卦是本宫
    if (hun === '归魂') {
      return Trigram.fromBinary(nei);
    }
    // 一二三六外卦宫
    if (shiYao === 1 || shiYao === 2 || shiYao === 3 || shiYao === 6) {
      return Trigram.fromBinary(wai);
    }
    // 四五游魂内变更（内卦取反）
    if (shiYao === 4 || shiYao === 5 || hun === '游魂') {
      return Trigram.fromBinary(flipBinary(nei));
    }
    throw new Error(`认宫失败: mark=${mark} shi=${shiYao}`);
  }

  /** 游魂/归魂（人同位异为游魂，位同人异为归魂），无则 null */
  static soulOf(mark: string): string | null {
    const nei = mark.substring(0, 3);
    const wai = mark.substring(3, 6);
    if (wai.charAt(1) === nei.charAt(1)) {
      if (wai.charAt(0) !== nei.charAt(0) && wai.charAt(2) !== nei.charAt(2)) {
        return '游魂';
      }
      return null;
    }
    if (wai.charAt(0) === nei.charAt(0) && wai.charAt(2) === nei.charAt(2)) {
      return '归魂';
    }
    return null;
  }

  /** 六冲卦：内外卦相同，或天雷无妄/雷天大壮 */
  static isChong(mark: string): boolean {
    const nei = mark.substring(0, 3);
    const wai = mark.substring(3, 6);
    if (wai === nei) {
      return true;
    }
    return (nei === '100' && wai === '111') || (nei === '111' && wai === '100');
  }

  /** 六合卦：卦名含固定片段 */
  static isHe(mark: string): boolean {
    const name = guaNameOf(mark);
    return LIUHE_FRAGMENTS.some((f) => name.includes(f));
  }

  /** 卦类型标签（口径与 najia 一致：游魂/归魂 > 六冲 > 六合） */
  static guaType(mark: string): string {
    const soul = LiuYaoChart.soulOf(mark);
    if (soul !== null) {
      return soul;
    }
    if (LiuYaoChart.isChong(mark)) {
      return '六冲';
    }
    if (LiuYaoChart.isHe(mark)) {
      return '六合';
    }
    return '';
  }

  /** 纳甲干支自初爻到上爻 */
  static najiaOf(mark: string): string[] {
    const nei = Trigram.fromBinary(mark.substring(0, 3));
    const wai = Trigram.fromBinary(mark.substring(3, 6));
    const neiTable = NAJIA[nei.label][0];
    const waiTable = NAJIA[wai.label][1];
    // 表首字为天干，后三字为三爻地支（自初爻/四爻起）
    const result: string[] = [];
    for (let i = 1; i <= 3; i++) {
      result.push(neiTable.charAt(0) + neiTable.charAt(i));
    }
    for (let i = 1; i <= 3; i++) {
      result.push(waiTable.charAt(0) + waiTable.charAt(i));
    }
    return result;
  }

  /** 六亲：同我兄弟，我生子孙，我克妻财，克我官鬼，生我父母 */
  static liuQinOf(gongWuXing: WuXing, zhiWuXing: WuXing): string {
    const relation = gongWuXing.relationTo(zhiWuXing);
    if (relation === ShengKeRelation.BI_HE) {
      return '兄弟';
    }
    if (relation === ShengKeRelation.WO_SHENG) {
      return '子孙';
    }
    if (relation === ShengKeRelation.WO_KE) {
      return '妻财';
    }
    if (relation === ShengKeRelation.KE_WO) {
      return '官鬼';
    }
    return '父母';
  }
}

/** 三爻二进制串逐位取反 */
function flipBinary(mark: string): string {
  let out = '';
  for (let i = 0; i < mark.length; i++) {
    out += mark.charAt(i) === '1' ? '0' : '1';
  }
  return out;
}

/** 伏神：装卦六亲不足五种时，从本宫卦纳甲中取藏伏之爻（najia _hidden 口径，首位命中） */
function buildHidden(gongTrigram: Trigram, qin6: string[],
  gongWuXing: WuXing): LiuYaoHidden | null {
  if (new Set(qin6).size >= 5) {
    return null;
  }
  const gongMark = gongTrigram.binary + gongTrigram.binary;
  const gongName = guaNameOf(gongMark);
  const seats = new Map<number, [string, string]>();
  const added = new Set<string>();
  const missing = QING6.filter((q) => qin6.indexOf(q) < 0);
  const gongNajia = LiuYaoChart.najiaOf(gongMark);
  const gongQin = gongNajia.map(
    (gz) => LiuYaoChart.liuQinOf(gongWuXing, DiZhi.fromChar(gz.charAt(1)).wuXing));
  for (const qin of missing) {
    const seat = gongQin.indexOf(qin);
    if (seat >= 0 && !added.has(qin)) {
      seats.set(seat + 1, [qin, gongNajia[seat]]);
      added.add(qin);
    }
  }
  if (seats.size === 0) {
    return null;
  }
  return new LiuYaoHidden(gongName, seats);
}

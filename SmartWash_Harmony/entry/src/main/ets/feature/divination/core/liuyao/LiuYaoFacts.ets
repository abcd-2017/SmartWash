/**
 * 六爻断法规则层 —— 只产确定性事实标记，不下吉凶结论（方案文档第三章第 4 节）。
 * 旺相休囚死 / 月破 / 日冲 / 旬空 / 动变回头生克 / 用神候选。
 *
 * 逐行移植安卓 feature/divination/src/main/java/com/smartwash/feature/divination/core/liuyao/LiuYaoFacts.kt。
 */

import { DiZhi, WuXing, ShengKeRelation } from '../GanZhi';
import { DivCategory } from '../DivinationCommon';
import { LiuYaoChart, LiuYaoLine } from './LiuYaoChart';

/** 六爻断法事实标记 */
export class LiuYaoFacts {
  /** 用神爻位（1..6）与描述，如 "妻财丙子水 · 五爻" */
  readonly yongShenPosition: number | null;
  readonly yongShenLabel: string | null;
  /** 爻位 → 旺相休囚死（按月令） */
  readonly strength: Map<number, string>;
  /** 月破爻位（爻支冲月支） */
  readonly yuePo: number[];
  /** 日冲爻位（爻支冲日支） */
  readonly riChong: number[];
  /** 旬空爻位（爻支落日旬空） */
  readonly xunKongYao: number[];
  /** 动爻位 → 变爻回头生 / 回头克（变爻五行对动爻五行） */
  readonly huiTou: Map<number, string>;
  /** 用神选取说明（含两现裁决），供解读层引用 */
  readonly yongShenNote: string | null;

  constructor(yongShenPosition: number | null, yongShenLabel: string | null,
    strength: Map<number, string>, yuePo: number[], riChong: number[],
    xunKongYao: number[], huiTou: Map<number, string>, yongShenNote: string | null) {
    this.yongShenPosition = yongShenPosition;
    this.yongShenLabel = yongShenLabel;
    this.strength = strength;
    this.yuePo = yuePo;
    this.riChong = riChong;
    this.xunKongYao = xunKongYao;
    this.huiTou = huiTou;
    this.yongShenNote = yongShenNote;
  }

  /**
   * 用神六亲映射（首选项）：事业/工作取官鬼，财运取妻财，感情取妻财（官鬼次之），
   * 学业取父母，出行取父母，其余以世爻为用。
   */
  private static preferredQin(category: DivCategory): string[] {
    if (category === DivCategory.CAREER) {
      return ['官鬼', '父母'];
    }
    if (category === DivCategory.WEALTH) {
      return ['妻财'];
    }
    if (category === DivCategory.LOVE) {
      return ['妻财', '官鬼'];
    }
    if (category === DivCategory.STUDY) {
      return ['父母', '官鬼'];
    }
    if (category === DivCategory.TRAVEL) {
      return ['父母'];
    }
    return [];
  }

  static of(chart: LiuYaoChart, category: DivCategory): LiuYaoFacts {
    // 旺相休囚死：当令旺、令生相、生令休、克令囚、令克死（月支五行为令）
    const monthWx: WuXing = chart.siZhu.month.zhi.wuXing;
    const strength = new Map<number, string>();
    for (const row of chart.rows) {
      let label: string;
      if (row.zhiWuXing === monthWx) {
        label = '旺';
      } else if (row.zhiWuXing === monthWx.generates()) {
        label = '相';
      } else if (row.zhiWuXing === monthWx.generatedBy()) {
        label = '休';
      } else if (row.zhiWuXing === monthWx.overcomeBy()) {
        label = '囚';
      } else {
        label = '死';
      }
      strength.set(row.position, label);
    }

    const monthZhi = chart.siZhu.month.zhi;
    const dayZhi = chart.siZhu.day.zhi;
    const kongZhi = chart.siZhu.day.xunKong;

    const yuePo: number[] = [];
    const riChong: number[] = [];
    const xunKongYao: number[] = [];
    for (const row of chart.rows) {
      const zhi = DiZhi.fromChar(row.ganZhi.charAt(1));
      if (zhi === monthZhi.chong()) {
        yuePo.push(row.position);
      }
      if (zhi === dayZhi.chong()) {
        riChong.push(row.position);
      }
      if (zhi === kongZhi[0] || zhi === kongZhi[1]) {
        xunKongYao.push(row.position);
      }
    }

    // 回头生克：变爻五行对动爻五行
    const huiTou = new Map<number, string>();
    for (const row of chart.rows) {
      if (!row.yao.isMoving || row.bianGanZhi === null) {
        continue;
      }
      const bianWx = DiZhi.fromChar(row.bianGanZhi.charAt(1)).wuXing;
      const relation = bianWx.relationTo(row.zhiWuXing);
      if (relation === ShengKeRelation.SHENG_WO) {
        huiTou.set(row.position, '回头生');
      } else if (relation === ShengKeRelation.KE_WO) {
        huiTou.set(row.position, '回头克');
      }
    }

    // 用神：按领域取六亲候选；两现时优先动爻、次取不落旬空；缺六亲取伏神
    let yongPosition: number | null = null;
    let yongLabel: string | null = null;
    let yongNote: string | null = null;
    const preferred = LiuYaoFacts.preferredQin(category);
    if (preferred.length === 0) {
      const shiRow = LiuYaoFacts.findShiRow(chart);
      yongPosition = shiRow.position;
      yongLabel = `${shiRow.liuQin}${shiRow.ganZhi}${shiRow.zhiWuXing.label} · 世爻`;
      yongNote = '无专属六亲，以世爻为用';
    } else {
      let done = false;
      for (const qin of preferred) {
        if (done) {
          break;
        }
        const candidates = chart.rows.filter((r) => r.liuQin === qin);
        let picked: LiuYaoLine | undefined = candidates.find((r) => r.yao.isMoving);
        if (picked === undefined) {
          picked = candidates.find((r) => xunKongYao.indexOf(r.position) < 0);
        }
        if (picked === undefined && candidates.length > 0) {
          picked = candidates[0];
        }
        if (picked !== undefined) {
          yongPosition = picked.position;
          yongLabel = `${picked.liuQin}${picked.ganZhi}${picked.zhiWuXing.label} · ${cnPosition(picked.position)}爻`;
          const notes: string[] = [];
          if (candidates.length > 1) {
            notes.push(`两现，取${picked.yao.isMoving ? '动爻' : '静爻'}`);
          }
          if (xunKongYao.indexOf(picked.position) >= 0) {
            notes.push('用神旬空');
          }
          if (yuePo.indexOf(picked.position) >= 0) {
            notes.push('月破');
          }
          yongNote = notes.length > 0 ? notes.join(' · ') : null;
          done = true;
          break;
        }
        // 装卦缺此六亲 → 取伏神
        if (chart.hidden !== null) {
          let hiddenKey: number | null = null;
          let hiddenPair: [string, string] | null = null;
          chart.hidden.seats.forEach((value, key) => {
            if (hiddenKey === null && value[0] === qin) {
              hiddenKey = key;
              hiddenPair = value;
            }
          });
          if (hiddenKey !== null && hiddenPair !== null) {
            yongPosition = hiddenKey;
            yongLabel = `伏神 ${hiddenPair[0]}${hiddenPair[1]}（伏于${cnPosition(hiddenKey)}爻下）`;
            yongNote = `卦中缺${qin}，取${chart.hidden.gongName}本宫伏神`;
            done = true;
            break;
          }
        }
      }
      if (yongPosition === null) {
        const shiRow = LiuYaoFacts.findShiRow(chart);
        yongPosition = shiRow.position;
        yongLabel = `${shiRow.liuQin}${shiRow.ganZhi}${shiRow.zhiWuXing.label} · 世爻`;
      }
    }

    return new LiuYaoFacts(
      yongPosition, yongLabel, strength, yuePo, riChong, xunKongYao, huiTou, yongNote);
  }

  private static findShiRow(chart: LiuYaoChart): LiuYaoLine {
    const found = chart.rows.find((r) => r.position === chart.shiYao);
    if (found === undefined) {
      throw new Error(`世爻不存在: ${chart.shiYao}`);
    }
    return found;
  }
}

function cnPosition(p: number): string {
  const names = ['初', '二', '三', '四', '五', '上'];
  return names[p - 1];
}

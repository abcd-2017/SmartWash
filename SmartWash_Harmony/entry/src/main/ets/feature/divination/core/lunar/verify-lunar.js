/**
 * 历法底座对拍脚本（Node 运行，不参与应用编译）。
 *
 * 用途：证明 vendor 的 lunar-javascript@1.7.7 与安卓 cn.6tail:lunar:1.7.7 行为一致，
 * 作为观象台术数内核的验收基准。锚点取自安卓 AnchorTest（见 04-new-features.md §1.3.6/§1.3.7）。
 *
 * 运行：node verify-lunar.js
 */
const { Solar } = require('./lunar.js');

let pass = 0, fail = 0;
function chk(name, got, exp) {
  const ok = got === exp;
  if (ok) pass++; else fail++;
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}: ${got}${ok ? '' : ` (期望 ${exp})`}`);
}

// ① 四柱（含晚子时边界：23:30 属次日子时口径）
function bazi(y, m, d, h, mi) {
  const e = Solar.fromYmdHms(y, m, d, h, mi, 0).getLunar().getEightChar();
  return [e.getYear(), e.getMonth(), e.getDay(), e.getTime()];
}
chk('四柱 1990-05-20 14:30（安卓八字锚点）', bazi(1990, 5, 20, 14, 30).join(' '), '庚午 辛巳 乙酉 癸未');
chk('四柱 2005-12-23 08:37（官方用例）', bazi(2005, 12, 23, 8, 37).join(' '), '乙酉 戊子 辛巳 壬辰');
chk('四柱 1988-02-15 23:30（晚子时）', bazi(1988, 2, 15, 23, 30).join(' '), '戊辰 甲寅 庚子 戊子');

// ② 八字锚点的其余字段（纳音 / 命宫身宫胎元 / 旬空 / 十二长生 / 起运）
const e = Solar.fromYmdHms(1990, 5, 20, 14, 30, 0).getLunar().getEightChar();
chk('纳音', [e.getYearNaYin(), e.getMonthNaYin(), e.getDayNaYin(), e.getTimeNaYin()].join('/'), '路旁土/白蜡金/泉中水/杨柳木');
chk('命宫/身宫/胎元', `${e.getMingGong()}/${e.getShenGong()}/${e.getTaiYuan()}`, '辛巳/己丑/壬申');
chk('日旬空', e.getDayXunKong(), '午未');
chk('日十二长生', e.getDayDiShi(), '绝');
chk('起运时刻（男）', e.getYun(1).getStartSolar().toString(), '1995-12-10');

// ③ 六爻/梅花/奇门/六壬所需字段
const l = Solar.fromYmdHms(2026, 8, 29, 12, 0, 0).getLunar();
chk('2026-08-29 四柱', l.getBaZi().join(' '), '丙午 丙申 乙亥 壬午');
chk('2026-08-29 日旬空', l.getDayXunKong(), '申酉');
chk('2026-08-29 年支/农历月日/时支', `${l.getYearZhi()}/${l.getMonth()}/${l.getDay()}/${l.getTimeZhi()}`, '午/7/17/午');

// ④ 节气（奇门定局 / 六壬月将）
const jq = l.getPrevJieQi(true);
chk('2026-08-29 之前最近节气', jq.getName(), '处暑');

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail === 0 ? 0 : 1);

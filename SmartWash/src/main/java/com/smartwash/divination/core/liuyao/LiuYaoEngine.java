package com.smartwash.divination.core.liuyao;

import com.smartwash.divination.core.GanZhi;
import com.smartwash.divination.core.LunarCalendar;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * 六爻纳甲排盘引擎。
 *
 * 流程：
 * 1. 起卦：三枚铜钱，字3背2，六掷从初爻到上爻；和为6/7/8/9=老阴×(动)/少阳/少阴/老阳○(动)
 *    爻值编码：6=老阴(动)、7=少阳、8=少阴、9=老阳(动)
 * 2. 装卦：卦码（阳1阴0，自初爻到上爻）→64卦名→寻世诀安世应→认宫诀定卦宫→
 *    纳甲干支（天干+地支，按经卦表起）→六亲（以卦宫五行为我）→按日干起六神→
 *    动爻取反得变卦与变出干支（变爻六亲仍以本卦宫论）
 * 3. 四柱+旬空
 *
 * 算法依据：与 Android 端 LiuYaoChart.kt 同源移植（寻世诀 8 句口诀/认宫诀/纳甲表/
 * 六神起例/六亲生克），经 14 点 oracle 锚点对拍验证。
 */
public class LiuYaoEngine {

    /**
     * 纳甲表：每经卦给出（内卦、外卦）四字串，首字为天干、后三字为三爻地支（自初爻/四爻起）。
     * 口诀：乾金甲子外壬午，兑金丁巳外丁亥……（壬甲从乾数，乙癸向坤求）
     */
    private static final Map<String, String[]> NAJIA = buildNajia();

    private static Map<String, String[]> buildNajia() {
        Map<String, String[]> najia = new LinkedHashMap<>();
        najia.put("乾", new String[]{"甲子寅辰", "壬午申戌"});
        najia.put("兑", new String[]{"丁巳卯丑", "丁亥酉未"});
        najia.put("离", new String[]{"己卯丑亥", "己酉未巳"});
        najia.put("震", new String[]{"庚子寅辰", "庚午申戌"});
        najia.put("巽", new String[]{"辛丑亥酉", "辛未巳卯"});
        najia.put("坎", new String[]{"戊寅辰午", "戊申戌子"});
        najia.put("艮", new String[]{"丙辰午申", "丙戌子寅"});
        najia.put("坤", new String[]{"乙未巳卯", "癸丑亥酉"});
        return najia;
    }

    /** 六神起例轮转起点（按日干序：甲乙0 丙丁1 戊2 己3 庚辛4 壬癸5） */
    private static final int[] SHEN6_ROTATION = {0, 0, 1, 1, 2, 3, 4, 4, 5, 5};

    /** 六合卦名片段（卦名含之即六合） */
    private static final String[] LIUHE_FRAGMENTS = {"否", "困", "旅", "豫", "节", "贲", "复", "泰"};

    /**
     * 六爻排盘入口。
     *
     * @param castAt     起卦时刻（epoch ms）
     * @param tzOffset   时区偏移分钟（服务端排盘统一传 480=北京时间）
     * @param lines      爻值数组（6个元素，每个 6/7/8/9）；为空则按时间起卦
     * @param castMethod auto/manual/time
     * @return 盘面 Map（可序列化为 JSON）
     */
    public Map<String, Object> cast(long castAt, int tzOffset, List<Integer> lines, String castMethod) {
        Map<String, Object> chart = new LinkedHashMap<>();

        // 1. 起卦：获取六个爻值
        List<Integer> yaoLines = (lines != null && lines.size() == 6) ? lines : timeBasedLines(castAt);
        chart.put("method", "liuyao");
        chart.put("lines", yaoLines);

        // 2. 四柱
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(castAt), ZoneOffset.ofTotalSeconds(tzOffset * 60));
        Map<String, String> pillars = LunarCalendar.fourPillars(dateTime);
        chart.put("fourPillars", pillars);

        // 3. 旬空（由日柱推算）
        chart.put("xunkong", calcXunkong(pillars.get("day")));

        // 4. 装卦：卦码（阳1阴0，自初爻到上爻）→ 本卦名/上下卦
        String mark = toMark(yaoLines);
        chart.put("benMark", mark);
        chart.put("lowerGua", GanZhi.baguaFromBinary(mark.substring(0, 3)));
        chart.put("upperGua", GanZhi.baguaFromBinary(mark.substring(3, 6)));
        chart.put("benGua", GanZhi.GUA64_NAME.get(mark));

        // 5. 世应（寻世诀 8 句口诀）与卦宫（认宫诀）
        int[] shiYingPos = findShiYing(mark);
        Map<String, Object> shiYing = new LinkedHashMap<>();
        shiYing.put("shi", shiYingPos[0]);
        shiYing.put("ying", shiYingPos[1]);
        chart.put("shiYing", shiYing);
        String gong = findPalace(mark, shiYingPos[0]);
        String gongWuxing = GanZhi.BAGUA_WUXING[GanZhi.indexOf(GanZhi.BAGUA_NAME, gong)];
        chart.put("gong", gong);
        chart.put("gongWuxing", gongWuxing);
        chart.put("guaType", guaType(mark));

        // 6. 纳甲干支（六爻从初到上，天干+地支）
        List<Map<String, String>> naJia = calcNaJia(mark, yaoLines);
        chart.put("naJia", naJia);

        // 7. 六亲（以卦宫五行为我）与六神（按日干起）
        List<String> liuqin = new ArrayList<>();
        for (Map<String, String> yao : naJia) {
            liuqin.add(yao.get("liuqin"));
        }
        chart.put("liuqin", liuqin);
        List<String> liushen = calcLiushen(pillars.get("dayGan"));
        chart.put("liushen", liushen);

        // 8. 变卦（动爻取反：老阴6→阳、老阳9→阴）与变出干支（变爻六亲以本宫论）
        List<Integer> bianLines = new ArrayList<>(yaoLines);
        List<Map<String, Object>> movingYao = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            if (yaoLines.get(i) == 6 || yaoLines.get(i) == 9) {
                bianLines.set(i, yaoLines.get(i) == 6 ? 7 : 8); // 老阴变少阳，老阳变少阴
                Map<String, Object> moving = new LinkedHashMap<>();
                moving.put("position", i + 1);
                moving.put("from", yaoLines.get(i));
                moving.put("to", bianLines.get(i));
                movingYao.add(moving);
            }
        }
        if (!movingYao.isEmpty()) {
            String bianMark = toMark(bianLines);
            List<String> bianNajiaList = najiaOf(bianMark);
            for (Map<String, Object> moving : movingYao) {
                int position = (int) moving.get("position");
                String bianGanZhi = bianNajiaList.get(position - 1);
                String bianZhi = bianGanZhi.substring(1);
                String bianZhiWuxing = GanZhi.DIZHI_WUXING[GanZhi.dizhiIndex(bianZhi)];
                moving.put("bianGanZhi", bianGanZhi);
                moving.put("bianLiuqin", GanZhi.getLiuqin(gongWuxing, bianZhiWuxing));
            }
            chart.put("bianMark", bianMark);
            chart.put("bianGua", GanZhi.GUA64_NAME.get(bianMark));
            chart.put("bianGong", findPalace(bianMark, findShiYing(bianMark)[0]));
            chart.put("bianType", guaType(bianMark));
        } else {
            chart.put("bianMark", mark);
            chart.put("bianGua", GanZhi.GUA64_NAME.get(mark));
            chart.put("bianGong", gong);
            chart.put("bianType", guaType(mark));
        }
        chart.put("movingYao", movingYao);
        chart.put("bianLines", bianLines);

        return chart;
    }

    /** 时间起卦：用时刻的随机确定性哈希生成爻值 */
    private List<Integer> timeBasedLines(long castAt) {
        List<Integer> lines = new ArrayList<>();
        Random rng = new Random(castAt);
        for (int i = 0; i < 6; i++) {
            // 三枚铜钱：每枚字=3背=2，三枚和为6/7/8/9
            int sum = (rng.nextInt(2) + 2) + (rng.nextInt(2) + 2) + (rng.nextInt(2) + 2);
            lines.add(sum);
        }
        return lines;
    }

    /** 爻值→卦码：阳爻（7少阳/9老阳）记1、阴爻（6老阴/8少阴）记0，自初爻到上爻 */
    private String toMark(List<Integer> lines) {
        StringBuilder mark = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            mark.append(lines.get(i) % 2 == 1 ? '1' : '0');
        }
        return mark.toString();
    }

    /**
     * 寻世诀安世应（完整 8 句口诀，逐分支与蓝本对拍）：
     * 天同二世天变五，地同四世地变初；本宫六世三世异，人同游魂人变归。
     *
     * @return [世爻位, 应爻位]，1..6 自初爻起
     */
    static int[] findShiYing(String mark) {
        String nei = mark.substring(0, 3); // 内卦（初二三爻）
        String wai = mark.substring(3, 6); // 外卦（四五六爻）
        // same[0]=地位（初对四）、same[1]=人位（二对五）、same[2]=天位（三对上）
        boolean[] same = {
                wai.charAt(0) == nei.charAt(0),
                wai.charAt(1) == nei.charAt(1),
                wai.charAt(2) == nei.charAt(2)
        };

        int shi = 0;
        // 天同二世，天变五
        if (same[2]) {
            if (!same[1] && !same[0]) shi = 2;
        } else {
            if (same[1] && same[0]) shi = 5;
        }
        // 人同游魂（四世），人变归魂（三世）
        if (shi == 0) {
            if (same[1]) {
                if (!same[0] && !same[2]) shi = 4;
            } else {
                if (same[0] && same[2]) shi = 3;
            }
        }
        // 地同四世，地变初
        if (shi == 0) {
            if (same[0]) {
                if (!same[1] && !same[2]) shi = 4;
            } else {
                if (same[1] && same[2]) shi = 1;
            }
        }
        // 本宫六世（内外卦相同）
        if (shi == 0 && wai.equals(nei)) shi = 6;
        // 兜底三世
        if (shi == 0) shi = 3;

        int ying = shi > 3 ? shi - 3 : shi + 3;
        return new int[]{shi, ying};
    }

    /**
     * 认宫诀定卦宫：一二三六外卦宫，四五游魂内变更；若问归魂何所取，归魂内卦是本宫。
     *
     * @return 卦宫名（如 艮）
     */
    static String findPalace(String mark, int shiYao) {
        String nei = mark.substring(0, 3);
        String wai = mark.substring(3, 6);
        String soul = soulOf(mark);

        if ("归魂".equals(soul)) return GanZhi.baguaFromBinary(nei);
        if (shiYao == 1 || shiYao == 2 || shiYao == 3 || shiYao == 6) {
            return GanZhi.baguaFromBinary(wai);
        }
        // 四五世游魂：内卦取反（内变更）
        StringBuilder flipped = new StringBuilder(3);
        for (int i = 0; i < 3; i++) {
            flipped.append(nei.charAt(i) == '1' ? '0' : '1');
        }
        return GanZhi.baguaFromBinary(flipped.toString());
    }

    /** 游魂/归魂（人同位异为游魂，位同人异为归魂），无则 null */
    static String soulOf(String mark) {
        String nei = mark.substring(0, 3);
        String wai = mark.substring(3, 6);
        if (wai.charAt(1) == nei.charAt(1)) {
            if (wai.charAt(0) != nei.charAt(0) && wai.charAt(2) != nei.charAt(2)) return "游魂";
            return null;
        }
        if (wai.charAt(0) == nei.charAt(0) && wai.charAt(2) == nei.charAt(2)) return "归魂";
        return null;
    }

    /** 六冲卦：内外卦相同，或天雷无妄/雷天大壮 */
    private static boolean isChong(String mark) {
        String nei = mark.substring(0, 3);
        String wai = mark.substring(3, 6);
        if (wai.equals(nei)) return true;
        Set<String> pair = new HashSet<>(Arrays.asList(nei, wai));
        return pair.equals(new HashSet<>(Arrays.asList("100", "111")));
    }

    /** 六合卦：卦名含固定片段 */
    private static boolean isHe(String mark) {
        String name = GanZhi.GUA64_NAME.get(mark);
        for (String fragment : LIUHE_FRAGMENTS) {
            if (name.contains(fragment)) return true;
        }
        return false;
    }

    /** 卦类型标签（口径与纳甲一致：游魂/归魂 > 六冲 > 六合 > 空串） */
    static String guaType(String mark) {
        String soul = soulOf(mark);
        if (soul != null) return soul;
        if (isChong(mark)) return "六冲";
        if (isHe(mark)) return "六合";
        return "";
    }

    /** 纳甲干支自初爻到上爻（内卦取经卦内卦表、外卦取经卦外卦表） */
    static List<String> najiaOf(String mark) {
        String neiTable = NAJIA.get(GanZhi.baguaFromBinary(mark.substring(0, 3)))[0];
        String waiTable = NAJIA.get(GanZhi.baguaFromBinary(mark.substring(3, 6)))[1];
        List<String> result = new ArrayList<>(6);
        // 表首字为天干，后三字为三爻地支（自初爻/四爻起）
        for (int i = 0; i < 3; i++) {
            result.add(neiTable.charAt(0) + String.valueOf(neiTable.charAt(i + 1)));
        }
        for (int i = 0; i < 3; i++) {
            result.add(waiTable.charAt(0) + String.valueOf(waiTable.charAt(i + 1)));
        }
        return result;
    }

    /** 纳甲+六亲逐爻（六亲以卦宫五行为我：同我兄弟/我生子孙/我克妻财/克我官鬼/生我父母） */
    private List<Map<String, String>> calcNaJia(String mark, List<Integer> lines) {
        String gong = findPalace(mark, findShiYing(mark)[0]);
        String gongWuxing = GanZhi.BAGUA_WUXING[GanZhi.indexOf(GanZhi.BAGUA_NAME, gong)];
        List<String> najia = najiaOf(mark);

        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String ganZhi = najia.get(i);
            String tiangan = ganZhi.substring(0, 1);
            String dizhi = ganZhi.substring(1);
            String zhiWuxing = GanZhi.DIZHI_WUXING[GanZhi.dizhiIndex(dizhi)];

            Map<String, String> yao = new LinkedHashMap<>();
            yao.put("position", String.valueOf(i + 1));
            yao.put("value", String.valueOf(lines.get(i)));
            yao.put("tiangan", tiangan);
            yao.put("dizhi", dizhi);
            yao.put("ganZhi", ganZhi);
            yao.put("liuqin", GanZhi.getLiuqin(gongWuxing, zhiWuxing));
            result.add(yao);
        }
        return result;
    }

    /** 六神（按日干起，自初爻起顺轮：甲乙起青龙…壬癸起玄武） */
    private List<String> calcLiushen(String dayGan) {
        int start = GanZhi.liushenStart(dayGan);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            result.add(GanZhi.LIUSHEN[(start + i) % 6]);
        }
        return result;
    }

    /** 旬空（由日柱推算：旬首地支之前的两个地支为空亡） */
    private List<String> calcXunkong(String dayPillar) {
        if (dayPillar == null || dayPillar.length() < 2) return Collections.emptyList();
        String dayZhi = dayPillar.substring(1);
        int zhiIdx = GanZhi.dizhiIndex(dayZhi);
        if (zhiIdx < 0) return Collections.emptyList();
        // 旬空地支 = 日支 - (日干序号%10) ... 简化：直接返回对应旬空
        int xunkongStart = (zhiIdx - (GanZhi.tianganIndex(dayPillar.substring(0, 1))) % 10 + 12) % 12;
        List<String> result = new ArrayList<>();
        result.add(GanZhi.DIZHI[(xunkongStart + 10) % 12]);
        result.add(GanZhi.DIZHI[(xunkongStart + 11) % 12]);
        return result;
    }
}

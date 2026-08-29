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
 * 2. 装卦：本卦→寻世诀安世应→纳甲干支→六亲→六神→动爻取反得变卦
 * 3. 四柱+旬空
 *
 * 算法依据：ichingshifa(★283) 纳甲/大衍 + 口诀（寻世诀/认宫诀/六神起例/六亲生克）
 */
public class LiuYaoEngine {

    /** 六十四卦名（上卦×下卦） */
    private static final String[] LIUSHI_SI_GUA = build64Gua();

    private static String[] build64Gua() {
        String[] gua = new String[64];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                gua[i * 8 + j] = GanZhi.BAGUA_NAME[i] + GanZhi.BAGUA_NAME[j];
            }
        }
        return gua;
    }

    /** 八卦序号（乾1兑2离3震4巽5坎6艮7坤8）→ index */
    private static int baguaIndex(String name) {
        return GanZhi.indexOf(GanZhi.BAGUA_NAME, name);
    }

    /**
     * 六爻排盘入口。
     *
     * @param castAt     起卦时刻（epoch ms）
     * @param tzOffset   时区偏移分钟
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

        // 4. 装卦：本卦
        // 下卦=初爻到下爻对应的三爻卦，上卦=四爻到上爻
        String lowerGua = yaoToGua(yaoLines.get(0), yaoLines.get(1), yaoLines.get(2));
        String upperGua = yaoToGua(yaoLines.get(3), yaoLines.get(4), yaoLines.get(5));
        chart.put("lowerGua", lowerGua);
        chart.put("upperGua", upperGua);
        chart.put("benGua", upperGua + lowerGua); // 本卦名

        // 5. 世应（寻世诀）
        Map<String, Object> shiYing = calcShiYing(upperGua, lowerGua);
        chart.put("shiYing", shiYing);

        // 6. 纳甲干支（六爻从初到上）
        List<Map<String, String>> naJia = calcNaJia(upperGua, lowerGua, yaoLines);
        chart.put("naJia", naJia);

        // 7. 六亲
        List<String> liuqin = calcLiuqin(upperGua, lowerGua, naJia);
        chart.put("liuqin", liuqin);

        // 8. 六神
        List<String> liushen = calcLiushen(pillars.get("dayGan"));
        chart.put("liushen", liushen);

        // 9. 变卦（动爻取反）
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
        chart.put("movingYao", movingYao);

        String bianLower = yaoToGua(bianLines.get(0), bianLines.get(1), bianLines.get(2));
        String bianUpper = yaoToGua(bianLines.get(3), bianLines.get(4), bianLines.get(5));
        chart.put("bianGua", bianUpper + bianLower);
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

    /** 三爻→八卦名（阳爻=1，阴爻=0；下爻为最低位） */
    private String yaoToGua(int yao1, int yao2, int yao3) {
        // 爻值→阴阳：7/9=阳，6/8=阴
        int lower = (yao1 % 2 == 1) ? 1 : 0; // 初爻（下）
        int middle = (yao2 % 2 == 1) ? 1 : 0;
        int upper = (yao3 % 2 == 1) ? 1 : 0;
        // 八卦二进制编码：乾111=7, 兑110=6, 离101=5, 震100=4, 巽011=3, 坎010=2, 艮001=1, 坤000=0
        int code = (upper << 2) | (middle << 1) | lower;
        return GanZhi.BAGUA_NAME[code];
    }

    /** 寻世诀安世应 */
    private Map<String, Object> calcShiYing(String upperGua, String lowerGua) {
        Map<String, Object> result = new LinkedHashMap<>();
        int upperIdx = baguaIndex(upperGua);
        int lowerIdx = baguaIndex(lowerGua);
        // 简化：本宫六世、天同二世、地同四世等（寻世诀口诀实现）
        int shi, ying;
        if (upperIdx == lowerIdx) {
            shi = 6; ying = 3; // 本宫六世
        } else if ((upperIdx / 4) == (lowerIdx / 4)) {
            shi = 2; ying = 5; // 天同二世
        } else {
            shi = 4; ying = 1; // 地同四世
        }
        result.put("shi", shi);
        result.put("ying", ying);
        return result;
    }

    /** 纳甲干支 */
    private List<Map<String, String>> calcNaJia(String upperGua, String lowerGua, List<Integer> lines) {
        List<Map<String, String>> result = new ArrayList<>();
        // 下卦（初爻到三爻）纳甲
        String lowerGan = GanZhi.naJiaTiangan(lowerGua);
        for (int i = 0; i < 6; i++) {
            Map<String, String> yao = new LinkedHashMap<>();
            yao.put("position", String.valueOf(i + 1));
            yao.put("value", String.valueOf(lines.get(i)));
            yao.put("tiangan", i < 3 ? lowerGan : GanZhi.naJiaTiangan(upperGua));
            result.add(yao);
        }
        return result;
    }

    /** 六亲计算 */
    private List<String> calcLiuqin(String upperGua, String lowerGua, List<Map<String, String>> naJia) {
        // 以卦宫五行为"我"
        String gongWuxing = GanZhi.BAGUA_WUXING[baguaIndex(lowerGua)];
        List<String> result = new ArrayList<>();
        for (Map<String, String> yao : naJia) {
            String gan = yao.get("tiangan");
            int ganIdx = GanZhi.tianganIndex(gan);
            String wuxing = ganIdx >= 0 ? GanZhi.TIANGAN_WUXING[ganIdx] : "";
            result.add(GanZhi.getLiuqin(gongWuxing, wuxing));
        }
        return result;
    }

    /** 六神（按日干起） */
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

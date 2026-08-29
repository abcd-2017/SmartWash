package com.smartwash.divination.core.meihua;

import com.smartwash.divination.core.GanZhi;
import com.smartwash.divination.core.LunarCalendar;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * 梅花易数排盘引擎。
 *
 * 流程：
 * 1. 起卦：时间卦 (年支数+月数+日数)%8=上卦，+时支数%8=下卦，总数%6=动爻
 *    先天卦数：乾一兑二离三震四巽五坎六艮七坤八
 * 2. 排法：本卦→互卦（234爻为下互、345爻为上互）→变卦（动爻翻转）
 * 3. 断法规则层：体用——动爻所在卦为"用"，静卦为"体"
 *
 * 算法依据：术数方案 4.1（口诀级，已验证）
 */
public class MeiHuaEngine {

    /** 先天八卦数 → index（乾1→0, 兑2→1, ... 坤8→7） */
    private static int xiantianToIndex(int num) {
        // num 为 1~8，对应乾兑离震巽坎艮坤
        int n = ((num - 1) % 8 + 8) % 8;
        return n;
    }

    /**
     * 梅花易数排盘入口。
     *
     * @param castAt     起卦时刻（epoch ms）
     * @param tzOffset   时区偏移分钟
     * @param lines      可选：[上卦数, 下卦数, 动爻]；为空则按时间起卦
     * @param castMethod auto/time/number
     * @return 盘面 Map
     */
    public Map<String, Object> cast(long castAt, int tzOffset, List<Integer> lines, String castMethod) {
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("method", "meihua");

        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(castAt), ZoneOffset.ofTotalSeconds(tzOffset * 60));
        Map<String, String> pillars = LunarCalendar.fourPillars(dateTime);
        chart.put("fourPillars", pillars);

        int upperNum, lowerNum, moving;
        if (lines != null && lines.size() >= 3) {
            // 数字起卦
            upperNum = lines.get(0);
            lowerNum = lines.get(1);
            moving = lines.get(2);
        } else {
            // 时间起卦
            int yearZhiIdx = GanZhi.dizhiIndex(pillars.get("yearZhi"));
            int month = dateTime.getMonthValue();
            int day = dateTime.getDayOfMonth();
            int hourZhiIdx = GanZhi.dizhiIndex(pillars.get("hourZhi"));

            // 年支数：子1丑2...亥12
            int yearNum = yearZhiIdx + 1;
            int hourNum = hourZhiIdx + 1;

            int total = yearNum + month + day;
            upperNum = total % 8;
            if (upperNum == 0) upperNum = 8;
            lowerNum = (total + hourNum) % 8;
            if (lowerNum == 0) lowerNum = 8;
            moving = (total + hourNum) % 6;
            if (moving == 0) moving = 6;
        }

        String upperGua = GanZhi.BAGUA_NAME[xiantianToIndex(upperNum)];
        String lowerGua = GanZhi.BAGUA_NAME[xiantianToIndex(lowerNum)];

        chart.put("upperGua", upperGua);
        chart.put("lowerGua", lowerGua);
        chart.put("benGua", upperGua + lowerGua);
        chart.put("moving", moving);

        // 本卦六爻（从上到下：上爻→初爻）
        List<Integer> benLines = guaToLines(upperGua, lowerGua);
        chart.put("benLines", benLines);

        // 互卦：取本卦 234 爻为下互、345 爻为上互
        String huLower = yaoToGua(benLines.get(3), benLines.get(2), benLines.get(1)); // 234爻
        String huUpper = yaoToGua(benLines.get(4), benLines.get(3), benLines.get(2)); // 345爻
        chart.put("huGua", huUpper + huLower);

        // 变卦：动爻翻转
        List<Integer> bianLines = new ArrayList<>(benLines);
        int bianIdx = 6 - moving; // 动爻位置转 index（上爻=0）
        bianLines.set(bianIdx, bianLines.get(bianIdx) == 1 ? 0 : 1);
        String bianUpper = yaoToGua(bianLines.get(0), bianLines.get(1), bianLines.get(2));
        String bianLower = yaoToGua(bianLines.get(3), bianLines.get(4), bianLines.get(5));
        chart.put("bianGua", bianUpper + bianLower);
        chart.put("bianLines", bianLines);

        // 体用：动爻所在卦为"用"，静卦为"体"
        String ti, yong;
        if (moving >= 4) {
            // 动爻在上卦 → 上卦为用，下卦为体
            yong = upperGua;
            ti = lowerGua;
        } else {
            yong = lowerGua;
            ti = upperGua;
        }
        chart.put("ti", ti);
        chart.put("yong", yong);

        // 体用五行生克
        String tiWuxing = GanZhi.BAGUA_WUXING[baguaIndex(ti)];
        String yongWuxing = GanZhi.BAGUA_WUXING[baguaIndex(yong)];
        chart.put("tiYongRelation", GanZhi.getLiuqin(tiWuxing, yongWuxing));

        return chart;
    }

    /** 上下卦→六爻（上爻到初爻，阳=1阴=0） */
    private List<Integer> guaToLines(String upperGua, String lowerGua) {
        int upperIdx = baguaIndex(upperGua);
        int lowerIdx = baguaIndex(lowerGua);
        List<Integer> lines = new ArrayList<>();
        // 上卦三爻（上爻、五爻、四爻）
        lines.add((upperIdx >> 2) & 1);
        lines.add((upperIdx >> 1) & 1);
        lines.add(upperIdx & 1);
        // 下卦三爻（三爻、二爻、初爻）
        lines.add((lowerIdx >> 2) & 1);
        lines.add((lowerIdx >> 1) & 1);
        lines.add(lowerIdx & 1);
        return lines;
    }

    /** 三爻→八卦名 */
    private String yaoToGua(int upper, int middle, int lower) {
        int code = (upper << 2) | (middle << 1) | lower;
        return GanZhi.BAGUA_NAME[code];
    }

    private int baguaIndex(String name) {
        return GanZhi.indexOf(GanZhi.BAGUA_NAME, name);
    }
}

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
 * 1. 起卦：时间卦 (年支数+农历月数+农历日数)%8=上卦，+时支数%8=下卦，总数%6=动爻
 *    先天卦数：乾一兑二离三震四巽五坎六艮七坤八
 * 2. 排法：本卦→互卦（234爻为下互、345爻为上互）→变卦（动爻翻转）
 * 3. 断法规则层：体用——动爻所在卦为"用"，静卦为"体"
 *
 * 算法依据：术数方案 4.1（口诀级）+ Android 端 MeiHuaChart.kt 同源移植
 * （时间卦以农历月日累加、互卦取爻与变卦翻转均用真实三爻爻值，经锚点对拍验证）
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
     * @param tzOffset   时区偏移分钟（服务端排盘统一传 480=北京时间）
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
            // 时间起卦（传统梅花按农历：年支数+农历月数+农历日数，Android 端同口径）
            Map<String, Integer> lunar = LunarCalendar.lunarDivinationNumbers(dateTime);
            int yearZhiIdx = lunar.get("yearZhiIdx");
            int month = lunar.get("month");
            int day = lunar.get("day");
            int hourZhiIdx = lunar.get("hourZhiIdx");

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
        chart.put("moving", moving);

        // 本卦卦码（自初爻到上爻）：下卦三爻+上卦三爻
        String benMark = GanZhi.baguaBinary(lowerGua) + GanZhi.baguaBinary(upperGua);
        chart.put("benMark", benMark);
        chart.put("benGua", GanZhi.GUA64_NAME.get(benMark));

        // 本卦六爻（从上到下：上爻→初爻，阳=1阴=0）
        List<Integer> benLines = markToLines(benMark);
        chart.put("benLines", benLines);

        // 互卦：取本卦 234 爻为下互、345 爻为上互（卦码自初爻起，benMark[i] 即第 i+1 爻）
        char[] ben = benMark.toCharArray();
        String huMark = "" + ben[1] + ben[2] + ben[3] + ben[2] + ben[3] + ben[4];
        chart.put("huMark", huMark);
        chart.put("huGua", GanZhi.GUA64_NAME.get(huMark));

        // 变卦：动爻在卦码上的真实爻值翻转
        char[] bian = benMark.toCharArray();
        bian[moving - 1] = bian[moving - 1] == '1' ? '0' : '1';
        String bianMark = new String(bian);
        chart.put("bianMark", bianMark);
        chart.put("bianGua", GanZhi.GUA64_NAME.get(bianMark));
        chart.put("bianLines", markToLines(bianMark));

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

    /** 卦码（自初爻到上爻，阳1阴0）→ 六爻（从上到下：上爻→初爻） */
    private List<Integer> markToLines(String mark) {
        List<Integer> lines = new ArrayList<>(6);
        for (int i = 5; i >= 0; i--) {
            lines.add(mark.charAt(i) == '1' ? 1 : 0);
        }
        return lines;
    }

    private int baguaIndex(String name) {
        return GanZhi.indexOf(GanZhi.BAGUA_NAME, name);
    }
}

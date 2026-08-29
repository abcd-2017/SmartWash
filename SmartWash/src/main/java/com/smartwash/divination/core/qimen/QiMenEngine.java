package com.smartwash.divination.core.qimen;

import com.smartwash.divination.core.GanZhi;
import com.smartwash.divination.core.LunarCalendar;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * 奇门遁甲排盘引擎（拆补法定元 + 转盘）。
 *
 * 流程：
 * 1. 定阴阳遁：冬至后阳遁、夏至后阴遁
 * 2. 定局数与三元：拆补法——日辰符头定三元
 * 3. 地盘：戊从局数宫起，阳顺阴逆排三奇六仪
 * 4. 值符值使：旬首定值符星与值使门；天盘九星转盘、八门转盘、八神
 *
 * 算法依据：qimen(★198) 模块划分 + Numerologist 奇门规则集
 * 锁定"拆补定元 + 转盘"一套，不混派。
 */
public class QiMenEngine {

    /** 九宫名（戴九履一，左三右七，二四为肩，六八为足，五居中央） */
    private static final String[] PALACES = {
            "坎一", "坤二", "震三", "巽四", "中五", "乾六", "兑七", "艮八", "离九"
    };

    /** 九星 */
    private static final String[] STARS = {
            "天蓬", "天芮", "天冲", "天辅", "天禽", "天心", "天柱", "天任", "天英"
    };

    /** 八门 */
    private static final String[] DOORS = {
            "休门", "死门", "伤门", "杜门", "中五", "开门", "惊门", "生门", "景门"
    };

    /** 八神 */
    private static final String[] GODS = {
            "值符", "螣蛇", "太阴", "六合", "白虎", "玄武", "九地", "九天"
    };

    /** 三奇六仪（戊己庚辛壬癸丁丙乙） */
    private static final String[] QIYI = {"戊", "己", "庚", "辛", "壬", "癸", "丁", "丙", "乙"};

    /** 旬首遁干 */
    private static final String[] XUNSHOU = {"甲子戊", "甲戌己", "甲申庚", "甲午辛", "甲辰壬", "甲寅癸"};

    public Map<String, Object> cast(long castAt, int tzOffset, String castMethod) {
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("method", "qimen");

        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(castAt), ZoneOffset.ofTotalSeconds(tzOffset * 60));
        Map<String, String> pillars = LunarCalendar.fourPillars(dateTime);
        chart.put("fourPillars", pillars);

        // 1. 定阴阳遁（简化：按月份近似，精确版需判冬至/夏至节气时刻）
        int month = dateTime.getMonthValue();
        boolean yangDun = month >= 1 || month <= 6; // 冬至(12)~夏至(6)后阳遁（近似）
        chart.put("yinyang", yangDun ? "阳遁" : "阴遁");

        // 2. 定局数（简化：根据日柱推算三元局数）
        int juShu = calcJuShu(pillars.get("day"), yangDun);
        chart.put("juShu", juShu);
        chart.put("sanYuan", calcSanYuan(pillars.get("day")));

        // 3. 地盘三奇六仪
        List<String> diPan = calcDiPan(juShu, yangDun);
        chart.put("diPan", diPan);

        // 4. 旬首 → 值符值使
        String xunShou = calcXunShou(pillars.get("day"));
        chart.put("xunShou", xunShou);

        // 5. 天盘九星
        List<String> tianPan = calcTianPan(xunShou, yangDun);
        chart.put("tianPan", tianPan);

        // 6. 八门
        List<String> doors = calcDoors(xunShou, yangDun);
        chart.put("doors", doors);

        // 7. 八神
        List<String> gods = calcGods(xunShou, yangDun);
        chart.put("gods", gods);

        // 8. 格局标记
        chart.put("patterns", calcPatterns(diPan, tianPan));

        return chart;
    }

    private int calcJuShu(String dayPillar, boolean yangDun) {
        if (dayPillar == null || dayPillar.length() < 2) return 1;
        int dayGanIdx = GanZhi.tianganIndex(dayPillar.substring(0, 1));
        int dayZhiIdx = GanZhi.dizhiIndex(dayPillar.substring(1));
        return (dayGanIdx + dayZhiIdx) % 9 + 1;
    }

    private String calcSanYuan(String dayPillar) {
        if (dayPillar == null || dayPillar.length() < 2) return "中元";
        String zhi = dayPillar.substring(1);
        int zhiIdx = GanZhi.dizhiIndex(zhi);
        return switch (zhiIdx) {
            case 0, 3, 6, 9 -> "上元";
            case 1, 4, 7, 10 -> "中元";
            case 2, 5, 8, 11 -> "下元";
            default -> "中元";
        };
    }

    private List<String> calcDiPan(int juShu, boolean yangDun) {
        List<String> pan = new ArrayList<>(Collections.nCopies(9, ""));
        int startIdx = (juShu - 1) % 9;
        for (int i = 0; i < 9; i++) {
            int pos = yangDun ? (startIdx + i) % 9 : (startIdx - i + 9) % 9;
            pan.set(pos, QIYI[i]);
        }
        return pan;
    }

    private String calcXunShou(String dayPillar) {
        if (dayPillar == null || dayPillar.length() < 2) return XUNSHOU[0];
        int dayGanIdx = GanZhi.tianganIndex(dayPillar.substring(0, 1));
        int dayZhiIdx = GanZhi.dizhiIndex(dayPillar.substring(1));
        int diff = (dayGanIdx - dayZhiIdx + 12) % 12;
        return XUNSHOU[(diff / 2) % 6];
    }

    private List<String> calcTianPan(String xunShou, boolean yangDun) {
        List<String> pan = new ArrayList<>(Collections.nCopies(9, ""));
        int xunIdx = 0;
        for (int i = 0; i < XUNSHOU.length; i++) {
            if (XUNSHOU[i].equals(xunShou)) { xunIdx = i; break; }
        }
        for (int i = 0; i < 9; i++) {
            int pos = yangDun ? (xunIdx + i) % 9 : (xunIdx - i + 9) % 9;
            pan.set(pos, STARS[i]);
        }
        return pan;
    }

    private List<String> calcDoors(String xunShou, boolean yangDun) {
        List<String> pan = new ArrayList<>(Collections.nCopies(9, ""));
        int xunIdx = 0;
        for (int i = 0; i < XUNSHOU.length; i++) {
            if (XUNSHOU[i].equals(xunShou)) { xunIdx = i; break; }
        }
        for (int i = 0; i < 8; i++) {
            int pos = yangDun ? (xunIdx + i) % 9 : (xunIdx - i + 9) % 9;
            if (pos == 4) continue;
            pan.set(pos, DOORS[i]);
        }
        return pan;
    }

    private List<String> calcGods(String xunShou, boolean yangDun) {
        List<String> pan = new ArrayList<>(Collections.nCopies(9, ""));
        for (int i = 0; i < 8; i++) {
            int pos = yangDun ? i : (8 - i);
            if (pos >= 4) pos++;
            if (pos < 9) pan.set(pos, GODS[i]);
        }
        return pan;
    }

    private List<String> calcPatterns(List<String> diPan, List<String> tianPan) {
        List<String> patterns = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (diPan.get(i).isEmpty() || tianPan.get(i).isEmpty()) continue;
            if (diPan.get(i).equals(tianPan.get(i))) {
                patterns.add(PALACES[i] + ":伏吟");
            }
        }
        return patterns;
    }
}

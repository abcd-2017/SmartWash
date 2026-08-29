package com.smartwash.divination.core;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * 简化历法工具：四柱（年/月/日/时干支）计算。
 *
 * 说明：完整农历历法依赖 lunar-java 等库（节气交节/中气/闰月/真太阳时）。
 * 此处提供基于公历近似的四柱计算底座，用于服务端重算校验的结构完整性；
 * 精确节气交节时刻、中气换将等边界 case 在后续迭代中接入 6tail lunar 库。
 *
 * 四柱以节气分界（年柱以立春、月柱以节分界），日柱以子时换日。
 */
public final class LunarCalendar {

    private LunarCalendar() {}

    /** 六十甲子表（用于干支循环） */
    private static final String[] JIAZI_TABLE = buildJiaziTable();

    private static String[] buildJiaziTable() {
        String[] table = new String[60];
        for (int i = 0; i < 60; i++) {
            table[i] = GanZhi.TIANGAN[i % 10] + GanZhi.DIZHI[i % 12];
        }
        return table;
    }

    /**
     * 计算四柱（基于公历日期的近似六十甲子循环）。
     * 日柱以已知的参考锚点（1900-01-01 为甲戌日 #10）推算。
     */
    public static Map<String, String> fourPillars(LocalDateTime dateTime) {
        Map<String, String> pillars = new HashMap<>();

        int year = dateTime.getYear();
        int month = dateTime.getMonthValue();
        int day = dateTime.getDayOfMonth();

        // 年柱：以立春分界，此处近似用公历年份（精确版需判立春时刻）
        // 1984年为甲子年，(年份-1984)%60 → 干支序号
        int yearOffset = (year - 1984) % 60;
        if (yearOffset < 0) yearOffset += 60;
        // 月柱：月干由年干推算（甲己之年丙作首...），月支固定寅月起
        String yearGan = JIAZI_TABLE[yearOffset].substring(0, 1);
        int yearGanIdx = GanZhi.tianganIndex(yearGan);
        // 月干口诀：甲己之年丙作首，乙庚之岁戊为头，丙辛之年庚开始，丁壬壬寅顺水流，戊癸之年甲寅始
        int monthGanStart = switch (yearGanIdx) {
            case 0, 5 -> 2;  // 甲己→丙(2)
            case 1, 6 -> 4;  // 乙庚→戊(4)
            case 2, 7 -> 6;  // 丙辛→庚(6)
            case 3, 8 -> 8;  // 丁壬→壬(8)
            case 4, 9 -> 0;  // 戊癸→甲(0)
            default -> 0;
        };
        // 月支：寅月(2)起，对应农历正月（近似以公历月+1）
        int monthZhiIdx = (month + 1) % 12; // 寅=2 → 公历2月≈寅月
        int monthGanIdx = (monthGanStart + (monthZhiIdx - 2) + 10) % 10;
        String monthGan = GanZhi.TIANGAN[monthGanIdx];
        String monthZhi = GanZhi.DIZHI[monthZhiIdx];

        // 日柱：以锚点推算（1900-01-01 = 甲戌 #10）
        LocalDateTime anchor = LocalDateTime.of(1900, 1, 1, 0, 0);
        long days = java.time.Duration.between(anchor, dateTime).toDays();
        int dayOffset = (int) ((10 + days) % 60);
        if (dayOffset < 0) dayOffset += 60;

        // 时柱：时干由日干推算（甲己还加甲...），时支固定（子时=0）
        String dayGan = JIAZI_TABLE[dayOffset].substring(0, 1);
        int dayGanIdx = GanZhi.tianganIndex(dayGan);
        int hour = dateTime.getHour();
        int hourZhiIdx = ((hour + 1) / 2) % 12; // 子时=23~1→0
        int hourGanStart = switch (dayGanIdx) {
            case 0, 5 -> 0;  // 甲己→甲(0)
            case 1, 6 -> 2;  // 乙庚→丙(2)
            case 2, 7 -> 4;  // 丙辛→戊(4)
            case 3, 8 -> 6;  // 丁壬→庚(6)
            case 4, 9 -> 8;  // 戊癸→壬(8)
            default -> 0;
        };
        int hourGanIdx = (hourGanStart + hourZhiIdx) % 10;

        pillars.put("year", JIAZI_TABLE[yearOffset]);
        pillars.put("month", monthGan + monthZhi);
        pillars.put("day", JIAZI_TABLE[dayOffset]);
        pillars.put("hour", GanZhi.TIANGAN[hourGanIdx] + GanZhi.DIZHI[hourZhiIdx]);

        // 单独取出天干地支便于后续使用
        pillars.put("yearGan", JIAZI_TABLE[yearOffset].substring(0, 1));
        pillars.put("yearZhi", JIAZI_TABLE[yearOffset].substring(1));
        pillars.put("monthGan", monthGan);
        pillars.put("monthZhi", monthZhi);
        pillars.put("dayGan", dayGan);
        pillars.put("dayZhi", JIAZI_TABLE[dayOffset].substring(1));
        pillars.put("hourGan", GanZhi.TIANGAN[hourGanIdx]);
        pillars.put("hourZhi", GanZhi.DIZHI[hourZhiIdx]);

        return pillars;
    }
}

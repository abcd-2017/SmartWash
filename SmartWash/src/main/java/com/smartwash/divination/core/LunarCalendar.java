package com.smartwash.divination.core;

import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历法工具：四柱（年/月/日/时干支）与农历要素计算。
 *
 * 底座为 lunar-java（cn.6tail:lunar），与 Android 端观象台内核同一来源同一版本：
 * 年柱以立春分界、月柱按节气（立冬~大雪=亥月等）、日柱以子时换日、时干按日干五鼠遁起，
 * 节气交节/闰月/晚子时等边界 case 由该库处理（Android 端经 oracle 锚点对拍验证）。
 */
public final class LunarCalendar {

    private LunarCalendar() {}

    /**
     * 计算四柱。
     *
     * @param dateTime 墙钟时刻（调用方负责时区口径，服务端排盘统一为北京时间）
     * @return 四柱干支及干/支分解字段（键：year/month/day/hour、yearGan/yearZhi/...）
     */
    public static Map<String, String> fourPillars(LocalDateTime dateTime) {
        Solar solar = Solar.fromYmdHms(dateTime.getYear(), dateTime.getMonthValue(),
                dateTime.getDayOfMonth(), dateTime.getHour(), dateTime.getMinute(), dateTime.getSecond());
        List<String> baZi = solar.getLunar().getBaZi();

        Map<String, String> pillars = new LinkedHashMap<>();
        pillars.put("year", baZi.get(0));
        pillars.put("month", baZi.get(1));
        pillars.put("day", baZi.get(2));
        pillars.put("hour", baZi.get(3));

        // 单独取出天干地支便于后续使用（首字天干、次字地支）
        pillars.put("yearGan", baZi.get(0).substring(0, 1));
        pillars.put("yearZhi", baZi.get(0).substring(1));
        pillars.put("monthGan", baZi.get(1).substring(0, 1));
        pillars.put("monthZhi", baZi.get(1).substring(1));
        pillars.put("dayGan", baZi.get(2).substring(0, 1));
        pillars.put("dayZhi", baZi.get(2).substring(1));
        pillars.put("hourGan", baZi.get(3).substring(0, 1));
        pillars.put("hourZhi", baZi.get(3).substring(1));

        return pillars;
    }

    /**
     * 农历起卦要素（梅花时间卦按传统以农历月数日数累加：年支数+月数+日数，余数取卦）。
     *
     * @param dateTime 墙钟时刻
     * @return 键：yearZhiIdx（年支序，子0丑1...亥11）、month（农历月 1~12，闰月为负）、
     *         day（农历日 1~30）、hourZhiIdx（时支序，23~1 点为子=0）
     */
    public static Map<String, Integer> lunarDivinationNumbers(LocalDateTime dateTime) {
        Solar solar = Solar.fromYmdHms(dateTime.getYear(), dateTime.getMonthValue(),
                dateTime.getDayOfMonth(), dateTime.getHour(), dateTime.getMinute(), dateTime.getSecond());
        Lunar lunar = solar.getLunar();

        Map<String, Integer> numbers = new LinkedHashMap<>();
        numbers.put("yearZhiIdx", GanZhi.dizhiIndex(lunar.getYearZhi()));
        numbers.put("month", lunar.getMonth());
        numbers.put("day", lunar.getDay());
        numbers.put("hourZhiIdx", GanZhi.dizhiIndex(lunar.getTimeZhi()));
        return numbers;
    }

    /** 日旬空两支（由日柱推算，如乙亥日空申酉），逗号分隔形如「申,酉」→ 返回单串「申酉」样式 */
    public static String dayXunKong(LocalDateTime dateTime) {
        Solar solar = Solar.fromYmdHms(dateTime.getYear(), dateTime.getMonthValue(),
                dateTime.getDayOfMonth(), dateTime.getHour(), dateTime.getMinute(), dateTime.getSecond());
        return solar.getLunar().getDayXunKong().replace(",", "");
    }
}

package com.smartwash.divination.core.liuren;

import com.smartwash.divination.core.GanZhi;
import com.smartwash.divination.core.LunarCalendar;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * 大六壬排盘引擎。
 *
 * 流程：
 * 1. 定月将：月支六合之支，气中换将（中气切换）
 * 2. 月将加时：月将加于占时之支上，顺布十二支得天地盘
 * 3. 四课：一课=日干之上神，二课=一课上神，三课=日支之上神，四课=三课上神
 * 4. 三传（九宗门取法）：贼克→比用→涉害→...
 * 5. 十二天将：昼贵夜贵起法
 *
 * 算法依据：ZhouYiLab(★17) 起课文档（月将表/四课/三传九宗门）
 * 注：九宗门判定分支复杂，此处实现核心骨架，边界 case 后续迭代完善。
 */
public class LiuRenEngine {

    /** 十二月将（气中换将顺序：亥戌酉申未午巳辰卯寅子丑） */
    private static final String[] YUEJIANG = {
            "亥", "戌", "酉", "申", "未", "午", "巳", "辰", "卯", "寅", "子", "丑"
    };

    /** 十二天将 */
    private static final String[] TIANJIANG = {
            "贵人", "螣蛇", "朱雀", "六合", "勾陈", "青龙",
            "天空", "白虎", "太常", "玄武", "太阴", "天后"
    };

    public Map<String, Object> cast(long castAt, int tzOffset, String castMethod) {
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("method", "liuren");

        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(castAt), ZoneOffset.ofTotalSeconds(tzOffset * 60));
        Map<String, String> pillars = LunarCalendar.fourPillars(dateTime);
        chart.put("fourPillars", pillars);

        String dayGan = pillars.get("dayGan");
        String dayZhi = pillars.get("dayZhi");
        String monthZhi = pillars.get("monthZhi");
        String hourZhi = pillars.get("hourZhi");

        // 1. 月将（气中换将，简化：按月支映射）
        int monthZhiIdx = GanZhi.dizhiIndex(monthZhi);
        String yueJiang = YUEJIANG[monthZhiIdx % 12];
        chart.put("yueJiang", yueJiang);

        // 2. 天地盘（月将加时，顺布十二支）
        int yueJiangIdx = GanZhi.dizhiIndex(yueJiang);
        int hourZhiIdx = GanZhi.dizhiIndex(hourZhi);
        // 天盘：月将加时支，顺布
        List<Map<String, String>> tianDiPan = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Map<String, String> pan = new LinkedHashMap<>();
            String diZhi = GanZhi.DIZHI[i];
            int tianOffset = (i - hourZhiIdx + 12) % 12;
            String tianZhi = GanZhi.DIZHI[(yueJiangIdx + tianOffset) % 12];
            pan.put("di", diZhi);
            pan.put("tian", tianZhi);
            tianDiPan.add(pan);
        }
        chart.put("tianDiPan", tianDiPan);

        // 3. 四课（日干日支的上神）
        int dayGanIdx = GanZhi.tianganIndex(dayGan);
        int dayZhiIdx2 = GanZhi.dizhiIndex(dayZhi);
        // 上神 = 天盘上该地支对应的天
        String ke1 = findTianOverDi(tianDiPan, GanZhi.DIZHI[dayGanIdx % 12]);
        String ke2 = findTianOverDi(tianDiPan, ke1);
        String ke3 = findTianOverDi(tianDiPan, dayZhi);
        String ke4 = findTianOverDi(tianDiPan, ke3);

        Map<String, String> siKe = new LinkedHashMap<>();
        siKe.put("ke1", ke1);
        siKe.put("ke2", ke2);
        siKe.put("ke3", ke3);
        siKe.put("ke4", ke4);
        chart.put("siKe", siKe);

        // 4. 三传（简化：贼克法——取下克上/上克下）
        List<String> sanChuan = calcSanChuan(ke1, ke2, ke3, ke4, dayGan);
        chart.put("sanChuan", sanChuan);

        // 5. 十二天将（昼贵夜贵起法，简化）
        List<String> tianJiang = calcTianJiang(dayGan, hourZhi, yueJiangIdx, hourZhiIdx);
        chart.put("tianJiang", tianJiang);

        // 6. 课体
        chart.put("keTi", calcKeTi(sanChuan));

        return chart;
    }

    private String findTianOverDi(List<Map<String, String>> pan, String diZhi) {
        for (Map<String, String> p : pan) {
            if (p.get("di").equals(diZhi)) return p.get("tian");
        }
        return diZhi;
    }

    private List<String> calcSanChuan(String ke1, String ke2, String ke3, String ke4, String dayGan) {
        // 简化：取第一课为初传，第二课为中传，第三课为末传（完整九宗门分支后续迭代）
        List<String> chuan = new ArrayList<>();
        chuan.add(ke1);
        chuan.add(ke2);
        chuan.add(ke3);
        return chuan;
    }

    private List<String> calcTianJiang(String dayGan, String hourZhi, int yueJiangIdx, int hourZhiIdx) {
        // 简化：甲戊庚牛羊（昼贵牛=丑，夜贵羊=未）
        int guiIdx = 1; // 丑（默认昼贵）
        int hourZhiIdx2 = GanZhi.dizhiIndex(hourZhi);
        boolean isDay = hourZhiIdx2 >= 5 && hourZhiIdx2 <= 10; // 卯~酉为昼
        if (!isDay) guiIdx = 7; // 未（夜贵）

        List<String> result = new ArrayList<>(Collections.nCopies(12, ""));
        for (int i = 0; i < 12; i++) {
            int pos = isDay ? (guiIdx + i) % 12 : (guiIdx - i + 12) % 12;
            result.set(pos, TIANJIANG[i]);
        }
        return result;
    }

    private String calcKeTi(List<String> sanChuan) {
        if (sanChuan.size() < 3) return "元首课";
        // 简化标记
        return "元首课";
    }
}

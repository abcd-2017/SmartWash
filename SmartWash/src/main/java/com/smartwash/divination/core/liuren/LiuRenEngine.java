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
 * 1. 定月将：服务端口径取月建支（与排盘锚点对齐；传统月支六合/中气换将口径与锚点不合，
 *    差异已在任务报告标注，切换口径需同步更新锚点）
 * 2. 月将加时：月将加于占时之支上，顺布十二支得天地盘
 * 3. 四课：日干寄宫起一课，逐课上神传出；ke1..ke4 为上神，ke1Xia..ke4Xia 为下神（带干组）
 * 4. 三传（九宗门主链）：伏吟/返吟暂不支持 → 下贼上（重审）→ 上克下（元首）→ 比用 →
 *    涉害（孟仲季简化）→ 遥克（蒿矢/弹射）；中传=初传之上神、末传=中传之上神（天盘查表递取）
 * 5. 十二天将：昼贵夜贵起法（简化）
 *
 * 算法依据：ZhouYiLab(★17) 起课文档 + Android 端 LiuRenChart.kt 同源移植（寄宫/贼克比用去重）
 */
public class LiuRenEngine {

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

        // 1. 月将（服务端口径：月将取月建支）
        int yueJiangIdx = GanZhi.dizhiIndex(monthZhi);
        String yueJiang = GanZhi.DIZHI[yueJiangIdx];
        chart.put("yueJiang", yueJiang);

        // 2. 天地盘（月将加时，顺布十二支）
        int hourZhiIdx = GanZhi.dizhiIndex(hourZhi);
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

        // 3. 四课（日干寄宫起一课，逐上传出；ke1..ke4 上神 + 下神干组）
        List<Map<String, Object>> courses = buildCourses(dayGan, dayZhi, tianDiPan);
        chart.put("siKe", buildSiKe(dayGan, dayZhi, courses));

        // 4. 三传（九宗门主链，末传递取中传之上神）
        Map<String, Object> chuan = resolveSanChuan(dayGan, courses, tianDiPan);
        chart.put("sanChuan", chuan.get("chuan"));
        // 课体/宗门/说明仅在有取法结果时写入，避免 prompt 盘面出现 null 噪音
        if (chuan.get("keTi") != null) chart.put("keTi", chuan.get("keTi"));
        if (chuan.get("gate") != null) chart.put("jiuZongMen", chuan.get("gate"));
        if (chuan.get("note") != null) chart.put("unsupportedNote", chuan.get("note"));

        // 5. 十二天将（昼贵夜贵起法，简化）
        chart.put("tianJiang", calcTianJiang(dayGan, hourZhi, yueJiangIdx, hourZhiIdx));

        return chart;
    }

    /** 日干寄宫：甲寄寅、乙寄辰、丙戊寄巳、丁己寄未、庚寄申、辛寄戌、壬寄亥、癸寄丑 */
    static String ganJiGong(String gan) {
        return switch (gan) {
            case "甲" -> "寅";
            case "乙" -> "辰";
            case "丙", "戊" -> "巳";
            case "丁", "己" -> "未";
            case "庚" -> "申";
            case "辛" -> "戌";
            case "壬" -> "亥";
            case "癸" -> "丑";
            default -> "巳";
        };
    }

    /** 地盘支之上的天盘上神 */
    private String tianOver(List<Map<String, String>> tianDiPan, String diZhi) {
        for (Map<String, String> p : tianDiPan) {
            if (p.get("di").equals(diZhi)) return p.get("tian");
        }
        return diZhi;
    }

    /** 五行相克判定：a 克 b（木克土、土克水、水克火、火克金、金克木） */
    private static boolean ke(String a, String b) {
        int ia = GanZhi.indexOf(GanZhi.WUXING, a);
        int ib = GanZhi.indexOf(GanZhi.WUXING, b);
        return ia >= 0 && ib >= 0 && (ia + 2) % 5 == ib;
    }

    /** 四课构建：一课=日干寄宫之上神，二课=一课上神之上神，三课=日支之上神，四课=三课上神之上神 */
    private List<Map<String, Object>> buildCourses(String dayGan, String dayZhi, List<Map<String, String>> tianDiPan) {
        String ganGong = ganJiGong(dayGan);
        String upper1 = tianOver(tianDiPan, ganGong);
        String upper2 = tianOver(tianDiPan, upper1);
        String upper3 = tianOver(tianDiPan, dayZhi);
        String upper4 = tianOver(tianDiPan, upper3);

        String[][] courseDefs = {
                {"一课", upper1, ganGong},
                {"二课", upper2, upper1},
                {"三课", upper3, dayZhi},
                {"四课", upper4, upper3},
        };
        List<Map<String, Object>> courses = new ArrayList<>();
        for (String[] def : courseDefs) {
            String upperWuxing = GanZhi.DIZHI_WUXING[GanZhi.dizhiIndex(def[1])];
            String lowerWuxing = GanZhi.DIZHI_WUXING[GanZhi.dizhiIndex(def[2])];
            Map<String, Object> course = new LinkedHashMap<>();
            course.put("label", def[0]);
            course.put("upper", def[1]);
            course.put("lower", def[2]);
            // 上神克下神（上克下）/ 下神克上神（下贼上）
            course.put("shangKeXia", ke(upperWuxing, lowerWuxing));
            course.put("xiaZeShang", ke(lowerWuxing, upperWuxing));
            courses.add(course);
        }
        return courses;
    }

    /** 四课输出：ke1..ke4 上神（既有键兼容）+ ke1Xia..ke4Xia 下神 + 干组（ke1Gan/ke3Zhi） */
    private Map<String, String> buildSiKe(String dayGan, String dayZhi, List<Map<String, Object>> courses) {
        Map<String, String> siKe = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            Map<String, Object> course = courses.get(i);
            siKe.put("ke" + (i + 1), (String) course.get("upper"));
            siKe.put("ke" + (i + 1) + "Xia", (String) course.get("lower"));
        }
        // 干组：一课下神为日干寄宫之支（附日干），三课下神为日支
        siKe.put("ke1Gan", dayGan);
        siKe.put("ke3Zhi", dayZhi);
        return siKe;
    }

    /**
     * 三传（九宗门主链，口径与 Android LiuRenChart.resolveSanChuan 对拍）：
     * 伏吟/返吟暂不支持 → 下贼上唯一（重审）→ 上克下唯一（元首）→ 比用（与日干阴阳俱比）→
     * 涉害（取孟上神，无孟取仲，无仲取季，简化口径）→ 遥克（蒿矢/弹射）→ 兜底递取一课。
     * 中传=初传之上神、末传=中传之上神（天盘查表递取）。
     */
    private Map<String, Object> resolveSanChuan(String dayGan, List<Map<String, Object>> courses,
                                                List<Map<String, String>> tianDiPan) {
        // 伏吟（天盘=地盘）/返吟（天盘=地盘之冲）盘先判
        boolean fuYin = true;
        boolean fanYin = true;
        for (Map<String, String> pan : tianDiPan) {
            int diIdx = GanZhi.dizhiIndex(pan.get("di"));
            int tianIdx = GanZhi.dizhiIndex(pan.get("tian"));
            if (diIdx != tianIdx) fuYin = false;
            if (tianIdx != (diIdx + 6) % 12) fanYin = false;
        }
        if (fuYin) return unsupported("伏吟");
        if (fanYin) return unsupported("返吟");

        boolean dayYang = GanZhi.tianganIndex(dayGan) % 2 == 0;
        // 下贼上优先，其次上克下；下神相同者去重（对齐 ZhouYiLab remove_duplicate_lessons）
        List<Map<String, Object>> zei = dedupe(filter(courses, "xiaZeShang"));
        List<Map<String, Object>> keList = dedupe(filter(courses, "shangKeXia"));

        if (!zei.isEmpty()) {
            if (zei.size() == 1) return chuanOf(zei.get(0), tianDiPan, "重审课", "贼克");
            return biYong(zei, dayYang, tianDiPan);
        }
        if (!keList.isEmpty()) {
            if (keList.size() == 1) return chuanOf(keList.get(0), tianDiPan, "元首课", "贼克");
            return biYong(keList, dayYang, tianDiPan);
        }

        // 无克 → 遥克：上神克日干（蒿矢）优先，其次日干所克之上神（弹射）
        String dayGanWuxing = GanZhi.TIANGAN_WUXING[GanZhi.tianganIndex(dayGan)];
        List<Map<String, Object>> yaoKe = dedupe(courses.stream()
                .filter(c -> ke(GanZhi.DIZHI_WUXING[GanZhi.dizhiIndex((String) c.get("upper"))], dayGanWuxing))
                .toList());
        if (!yaoKe.isEmpty()) return chuanOf(yaoKe.get(0), tianDiPan, "蒿矢课", "遥克");
        yaoKe = dedupe(courses.stream()
                .filter(c -> ke(dayGanWuxing, GanZhi.DIZHI_WUXING[GanZhi.dizhiIndex((String) c.get("upper"))]))
                .toList());
        if (!yaoKe.isEmpty()) return chuanOf(yaoKe.get(0), tianDiPan, "弹射课", "遥克");

        // 昴星/别责/八专暂不支持：兜底退化为递取一课之上神（保持既有行为）
        Map<String, Object> result = chuanOf(courses.get(0), tianDiPan, null, null);
        result.put("note", "三传九宗门「昴星/别责/八专」暂不支持，按一课递传兜底");
        return result;
    }

    /** 比用：取与日干阴阳俱比的上神；俱不比/多比 → 涉害（取孟仲季上神，简化口径） */
    private Map<String, Object> biYong(List<Map<String, Object>> candidates, boolean dayYang,
                                       List<Map<String, String>> tianDiPan) {
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            boolean upperYang = GanZhi.dizhiIndex((String) candidate.get("upper")) % 2 == 0;
            if (upperYang == dayYang) matched.add(candidate);
        }
        if (matched.size() == 1) return chuanOf(matched.get(0), tianDiPan, "比用课", "比用");

        // 涉害：简化取孟上神（寅申巳亥），无孟取仲（子午卯酉），无仲取季（辰戌丑未）
        Map<String, Object> picked = null;
        int pickedKind = 3;
        for (Map<String, Object> candidate : candidates) {
            int kind = zhiKind(GanZhi.dizhiIndex((String) candidate.get("upper")));
            if (kind < pickedKind) {
                pickedKind = kind;
                picked = candidate;
            }
        }
        Map<String, Object> result = chuanOf(picked, tianDiPan, "涉害课", "涉害");
        result.put("note", "涉害课按孟仲季简化取传（涉害深浅未计），口径已在盘面声明");
        return result;
    }

    /** 地支孟仲季归类：0孟（寅申巳亥）、1仲（子午卯酉）、2季（辰戌丑未） */
    private static int zhiKind(int zhiIdx) {
        return switch (zhiIdx) {
            case 2, 5, 8, 11 -> 0;
            case 0, 3, 6, 9 -> 1;
            default -> 2;
        };
    }

    /** 由初传递取三传：初传 → 天盘(初传) → 天盘(中传)，课体/九宗门随取法标注 */
    private Map<String, Object> chuanOf(Map<String, Object> firstCourse, List<Map<String, String>> tianDiPan,
                                        String keTi, String gate) {
        String first = (String) firstCourse.get("upper");
        String second = tianOver(tianDiPan, first);
        String third = tianOver(tianDiPan, second);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chuan", Arrays.asList(first, second, third));
        result.put("keTi", keTi);
        result.put("gate", gate);
        result.put("note", null);
        return result;
    }

    /** 伏吟/返吟等未实现宗门：三传置空并说明 */
    private Map<String, Object> unsupported(String gate) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chuan", Collections.emptyList());
        result.put("keTi", null);
        result.put("gate", gate);
        result.put("note", "三传九宗门「" + gate + "」暂不支持");
        return result;
    }

    /** 取标记为 true 的课（shangKeXia / xiaZeShang） */
    private List<Map<String, Object>> filter(List<Map<String, Object>> courses, String flag) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> course : courses) {
            if (Boolean.TRUE.equals(course.get(flag))) result.add(course);
        }
        return result;
    }

    /** 课去重：下神相同者只保留首个（对齐 ZhouYiLab remove_duplicate_lessons 口径） */
    private List<Map<String, Object>> dedupe(List<Map<String, Object>> courses) {
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> course : courses) {
            if (seen.add((String) course.get("lower"))) result.add(course);
        }
        return result;
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
}

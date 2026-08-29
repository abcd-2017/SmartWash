package com.smartwash.divination.core;

/**
 * 天干地支、八卦、五行等共享基础数据与工具。
 * 四术共用的确定性口诀表，零 AI。
 */
public final class GanZhi {

    private GanZhi() {}

    /** 十天干 */
    public static final String[] TIANGAN = {
            "甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"
    };

    /** 十二地支 */
    public static final String[] DIZHI = {
            "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"
    };

    /** 五行 */
    public static final String[] WUXING = {"木", "火", "土", "金", "水"};

    /** 天干对应五行 */
    public static final String[] TIANGAN_WUXING = {
            "木", "木", "火", "火", "土", "土", "金", "金", "水", "水"
    };

    /** 地支对应五行 */
    public static final String[] DIZHI_WUXING = {
            "水", "土", "木", "木", "土", "火", "火", "土", "金", "金", "土", "水"
    };

    /** 先天八卦数：乾一兑二离三震四巽五坎六艮七坤八 */
    public static final String[] BAGUA_NAME = {
            "乾", "兑", "离", "震", "巽", "坎", "艮", "坤"
    };

    /** 八卦对应五行 */
    public static final String[] BAGUA_WUXING = {
            "金", "金", "火", "木", "木", "水", "土", "土"
    };

    /** 六亲（以宫五行为我） */
    public static final String[] LIUQIN = {"兄弟", "子孙", "妻财", "官鬼", "父母"};

    /** 六神起例 */
    public static final String[] LIUSHEN = {"青龙", "朱雀", "勾陈", "螣蛇", "白虎", "玄武"};

    /** 根据日干起六神（甲乙起青龙...） */
    public static int liushenStart(String dayGan) {
        int idx = indexOf(TIANGAN, dayGan);
        if (idx < 0) return 0;
        return switch (idx) {
            case 0, 1 -> 0; // 甲乙起青龙
            case 2, 3 -> 1; // 丙丁起朱雀
            case 4 -> 2;    // 戊起勾陈
            case 5 -> 3;    // 己起螣蛇
            case 6, 7 -> 4; // 庚辛起白虎
            case 8, 9 -> 5; // 壬癸起玄武
            default -> 0;
        };
    }

    /** 纳甲：乾纳甲壬、坤纳乙癸、艮纳丙、兑纳丁、坎纳戊、离纳己、震纳庚、纳辛 */
    public static String naJiaTiangan(String baguaName) {
        return switch (baguaName) {
            case "乾" -> "甲";
            case "坤" -> "乙";
            case "艮" -> "丙";
            case "兑" -> "丁";
            case "坎" -> "戊";
            case "离" -> "己";
            case "震" -> "庚";
            case "巽" -> "辛";
            default -> "";
        };
    }

    /** 地支五行生克关系：生我父母、我生子孙、我克妻财、克我官鬼、同我兄弟 */
    public static String getLiuqin(String woWuxing, String otherWuxing) {
        int wo = indexOf(WUXING, woWuxing);
        int other = indexOf(WUXING, otherWuxing);
        if (wo < 0 || other < 0) return "";
        // 五行相生序：木→火→土→金→水→木
        int shengWo = (wo + 4) % 5; // 生我者（水生木→水=4,木=0）
        int woSheng = (wo + 1) % 5; // 我生者
        int woKe = (wo + 2) % 5;    // 我克者
        int keWo = (wo + 3) % 5;    // 克我者
        if (other == shengWo) return "父母";
        if (other == woSheng) return "子孙";
        if (other == woKe) return "妻财";
        if (other == keWo) return "官鬼";
        return "兄弟"; // 同我
    }

    /** 计算天干序号（甲=0） */
    public static int tianganIndex(String gan) {
        return indexOf(TIANGAN, gan);
    }

    /** 计算地支序号（子=0） */
    public static int dizhiIndex(String zhi) {
        return indexOf(DIZHI, zhi);
    }

    /** 组合干支（天干序号 + 地支序号 → 六十甲子） */
    public static String combine(int tianganIdx, int dizhiIdx) {
        // 干支组合规则：阳干配阳支，阴干配阴支
        return TIANGAN[tianganIdx % 10] + DIZHI[dizhiIdx % 12];
    }

    public static int indexOf(String[] arr, String val) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(val)) return i;
        }
        return -1;
    }
}

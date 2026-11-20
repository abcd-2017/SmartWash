package com.smartwash.divination.core;

/**
 * 天干地支、八卦、五行等共享基础数据与工具。
 * 四术共用的确定性口诀表，零 AI。
 */
public final class GanZhi {

    private GanZhi() {}

    public static final String[] TIANGAN = {
            "甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"
    };

    public static final String[] DIZHI = {
            "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"
    };

    public static final String[] WUXING = {"木", "火", "土", "金", "水"};

    public static final String[] TIANGAN_WUXING = {
            "木", "木", "火", "火", "土", "土", "金", "金", "水", "水"
    };

    public static final String[] DIZHI_WUXING = {
            "水", "土", "木", "木", "土", "火", "火", "土", "金", "金", "土", "水"
    };

    /** 先天八卦数：乾一兑二离三震四巽五坎六艮七坤八 */
    public static final String[] BAGUA_NAME = {
            "乾", "兑", "离", "震", "巽", "坎", "艮", "坤"
    };

    /** 八卦三爻二进制码（自初爻起，阳1阴0）→ 卦名：乾111 兑110 离101 震100 巽011 坎010 艮001 坤000 */
    public static final String[] BAGUA_BINARY_NAME = {
            "坤", "艮", "坎", "巽", "震", "离", "兑", "乾"
    };

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

    /** 三爻二进制码（自初爻起，阳1阴0）→ 卦名，如 111→乾、101→离 */
    public static String baguaFromBinary(String mark) {
        int code = Integer.parseInt(mark, 2);
        return BAGUA_BINARY_NAME[code];
    }

    /** 卦名 → 三爻二进制码（自初爻起，阳1阴0），如 乾→111、坎→010 */
    public static String baguaBinary(String name) {
        return switch (name) {
            case "乾" -> "111";
            case "兑" -> "110";
            case "离" -> "101";
            case "震" -> "100";
            case "巽" -> "011";
            case "坎" -> "010";
            case "艮" -> "001";
            case "坤" -> "000";
            default -> "";
        };
    }

    /**
     * 六十四卦名：key 为二进制卦码（自初爻到上爻，阳1阴0），如 111111=乾为天、101101=离为火。
     */
    public static final java.util.Map<String, String> GUA64_NAME = buildGua64();

    private static java.util.Map<String, String> buildGua64() {
        java.util.Map<String, String> gua = new java.util.LinkedHashMap<>();
        gua.put("111111", "乾为天"); gua.put("011111", "天风姤"); gua.put("001111", "天山遁"); gua.put("000111", "天地否");
        gua.put("000011", "风地观"); gua.put("000001", "山地剥"); gua.put("000101", "火地晋"); gua.put("111101", "火天大有");
        gua.put("110110", "兑为泽"); gua.put("010110", "泽水困"); gua.put("000110", "泽地萃"); gua.put("001110", "泽山咸");
        gua.put("001010", "水山蹇"); gua.put("001000", "地山谦"); gua.put("001100", "雷山小过"); gua.put("110100", "雷泽归妹");
        gua.put("101101", "离为火"); gua.put("001101", "火山旅"); gua.put("011101", "火风鼎"); gua.put("010101", "火水未济");
        gua.put("010001", "山水蒙"); gua.put("010011", "风水涣"); gua.put("010111", "天水讼"); gua.put("101111", "天火同人");
        gua.put("100100", "震为雷"); gua.put("000100", "雷地豫"); gua.put("010100", "雷水解"); gua.put("011100", "雷风恒");
        gua.put("011000", "地风升"); gua.put("011010", "水风井"); gua.put("011110", "泽风大过"); gua.put("100110", "泽雷随");
        gua.put("011011", "巽为风"); gua.put("111011", "风天小畜"); gua.put("101011", "风火家人"); gua.put("100011", "风雷益");
        gua.put("100111", "天雷无妄"); gua.put("100101", "火雷噬嗑"); gua.put("100001", "山雷颐"); gua.put("011001", "山风蛊");
        gua.put("010010", "坎为水"); gua.put("110010", "水泽节"); gua.put("100010", "水雷屯"); gua.put("101010", "水火既济");
        gua.put("101110", "泽火革"); gua.put("101100", "雷火丰"); gua.put("101000", "地火明夷"); gua.put("010000", "地水师");
        gua.put("001001", "艮为山"); gua.put("101001", "山火贲"); gua.put("111001", "山天大畜"); gua.put("110001", "山泽损");
        gua.put("110101", "火泽睽"); gua.put("110111", "天泽履"); gua.put("110011", "风泽中孚"); gua.put("001011", "风山渐");
        gua.put("000000", "坤为地"); gua.put("100000", "地雷复"); gua.put("110000", "地泽临"); gua.put("111000", "地天泰");
        gua.put("111100", "雷天大壮"); gua.put("111110", "泽天夬"); gua.put("111010", "水天需"); gua.put("000010", "水地比");
        return gua;
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

package com.smartwash.feature.divination.core

/**
 * 四术公共枚举：方法标识与所问领域。
 */
enum class DivMethod(val id: String) {
    LIU_YAO("liuyao"),
    MEI_HUA("meihua"),
    QI_MEN("qimen"),
    LIU_REN("liuren"),
    ;

    companion object {
        /** 未知 id 兜底为六爻（与 [DivCategory.fromId] 的 `?: OTHER` 同口径）：本地库当前
         *  只写四枚举封闭值，但未来新增术数/数据迁移出现未知值时，UI 组合期直呼本函数的
         *  when 名称/印章映射不得抛 NoSuchElementException 整页崩溃 */
        fun fromId(id: String): DivMethod = entries.firstOrNull { it.id == id } ?: LIU_YAO
    }
}

/** 所问领域（问事页 chips 自动预选 + 用神映射） */
enum class DivCategory(val id: String) {
    CAREER("career"),
    WEALTH("wealth"),
    LOVE("love"),
    STUDY("study"),
    TRAVEL("travel"),
    OTHER("other"),
    ;

    companion object {
        fun fromId(id: String): DivCategory = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/** 经卦（三爻卦）：先天卦数 乾一兑二离三震四巽五坎六艮七坤八 */
enum class Trigram(val label: String, val binary: String, val wuXing: WuXing, val number: Int) {
    QIAN("乾", "111", WuXing.JIN, 1),
    DUI("兑", "110", WuXing.JIN, 2),
    LI("离", "101", WuXing.HUO, 3),
    ZHEN("震", "100", WuXing.MU, 4),
    XUN("巽", "011", WuXing.MU, 5),
    KAN("坎", "010", WuXing.SHUI, 6),
    GEN("艮", "001", WuXing.TU, 7),
    KUN("坤", "000", WuXing.TU, 8),
    ;

    companion object {
        /** 由三爻二进制串（自初爻起）取卦 */
        fun fromBinary(mark: String): Trigram = entries.first { it.binary == mark }
    }
}

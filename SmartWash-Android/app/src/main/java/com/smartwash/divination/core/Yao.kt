package com.smartwash.divination.core

import java.security.SecureRandom

/**
 * 爻值统一编码（全模块约定）：
 * 1=少阳 2=少阴 3=老阳○(动) 4=老阴×(动)
 */
enum class YaoValue(val code: Int, val label: String, val isMoving: Boolean, val isYang: Boolean) {
    SHAO_YANG(1, "少阳", false, true),
    SHAO_YIN(2, "少阴", false, false),
    LAO_YANG(3, "老阳", true, true),
    LAO_YIN(4, "老阴", true, false),
    ;

    /** 二进制爻位：阳 1 / 阴 0 */
    val binary: Int get() = if (isYang) 1 else 0

    /** 动而变：老阳变阴，老阴变阳，静爻不变 */
    fun transformedBinary(): Int = when (this) {
        LAO_YANG -> 0
        LAO_YIN -> 1
        else -> binary
    }

    companion object {
        fun fromCode(code: Int): YaoValue = entries.first { it.code == code }
    }
}

/**
 * 摇卦随机源 —— 三枚铜钱，字记 3、背记 2：
 * 和 6=老阴(三字) 7=少阳(一背) 8=少阴(二背) 9=老阳(三背)。
 * 随机源 SecureRandom，禁用可预测随机。
 */
class CoinTosser(private val random: SecureRandom = SecureRandom()) {

    /** 单枚铜钱：true=背(3) false=字(2) */
    fun tossCoin(): Boolean = random.nextBoolean()

    /** 一掷三币得爻值 */
    fun tossLine(): YaoValue {
        val backs = (1..3).count { tossCoin() }
        return when (backs) {
            0 -> YaoValue.LAO_YIN   // 6
            1 -> YaoValue.SHAO_YANG // 7
            2 -> YaoValue.SHAO_YIN  // 8
            else -> YaoValue.LAO_YANG // 9
        }
    }
}

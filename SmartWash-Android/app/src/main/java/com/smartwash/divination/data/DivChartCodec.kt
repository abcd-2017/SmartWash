package com.smartwash.divination.data

import com.google.gson.Gson
import com.smartwash.divination.core.liuren.LiuRenChart
import com.smartwash.divination.core.liuyao.LiuYaoChart
import com.smartwash.divination.core.meihua.MeiHuaChart
import com.smartwash.divination.core.qimen.QiMenChart

/**
 * 排盘结果 JSON 信封（Room chartJson 字段编解码）。
 * 四术共用一个 bundle，按 method 取对应排盘；core 数据类全部为可 Gson 反射的纯数据。
 */
data class DivChartBundle(
    val method: String,
    val liuyao: LiuYaoChart? = null,
    val meihua: MeiHuaChart? = null,
    val qimen: QiMenChart? = null,
    val liuren: LiuRenChart? = null,
)

object DivChartCodec {

    private val gson = Gson()

    fun encode(bundle: DivChartBundle): String = gson.toJson(bundle)

    fun decode(json: String): DivChartBundle? = runCatching {
        gson.fromJson(json, DivChartBundle::class.java)
    }.getOrNull()
}

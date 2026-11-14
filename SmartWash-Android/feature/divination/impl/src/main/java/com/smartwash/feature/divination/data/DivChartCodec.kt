package com.smartwash.feature.divination.data

import com.google.gson.Gson
import com.smartwash.feature.divination.core.liuren.LiuRenChart
import com.smartwash.feature.divination.core.liuyao.LiuYaoChart
import com.smartwash.feature.divination.core.meihua.MeiHuaChart
import com.smartwash.feature.divination.core.qimen.QiMenChart

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
    }.getOrNull()?.takeIf { it.isUsable() }

    /**
     * 可用性校验：混淆键名失配 / 旧版本存量 chartJson 会让 Gson 留下空字段
     * （如 rows 为 null），渲染期直接 NPE——此处按无效丢弃，由调用方走空态兜底。
     * 字段声明为非空，缺失时读取即 NPE，故整体包进 runCatching。
     */
    private fun DivChartBundle.isUsable(): Boolean = runCatching {
        when {
            liuyao != null -> liuyao.rows.isNotEmpty()
            meihua != null -> meihua.lines.isNotEmpty()
            qimen != null -> qimen.diPan.isNotEmpty()
            liuren != null -> liuren.courses.isNotEmpty()
            else -> false
        }
    }.getOrDefault(false)
}

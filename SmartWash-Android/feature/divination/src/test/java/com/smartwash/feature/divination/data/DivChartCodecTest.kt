package com.smartwash.feature.divination.data

import com.nlf.calendar.Solar
import com.smartwash.feature.divination.core.DivCategory
import com.smartwash.feature.divination.core.liuyao.LiuYaoChart
import com.smartwash.feature.divination.core.liuyao.LiuYaoFacts
import com.smartwash.feature.divination.core.liuyao.LiuYaoLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DivChartCodec 编码→解码回归锚点。
 *
 * 2026-11-12 缺陷：卦盘页点击「解读」闪退，栈顶
 * `ClassCastException: LinkedTreeMap cannot be cast to LiuYaoLine`，
 * 说明 Room chartJson 解码后 `LiuYaoChart.rows` 元素不是 `LiuYaoLine`。
 * 本测试锁死「encode→decode 后 rows 元素类型与规则层可用」这一真实缺陷面。
 */
class DivChartCodecTest {

    private val chart: LiuYaoChart by lazy {
        LiuYaoChart.compile(listOf(2, 2, 1, 4, 2, 1), Solar.fromYmdHms(2026, 8, 29, 12, 0, 0))
    }

    @Test
    fun `编码后解码应还原 LiuYaoLine 列表`() {
        val bundle = DivChartBundle(method = "liuyao", liuyao = chart)
        val json = DivChartCodec.encode(bundle)
        val decoded = DivChartCodec.decode(json)

        assertTrue("解码结果不应为 null", decoded != null)
        assertTrue("liuyao 应为 LiuYaoChart", decoded!!.liuyao is LiuYaoChart)
        val rows = decoded.liuyao!!.rows
        assertEquals(6, rows.size)
        rows.forEachIndexed { index, row ->
            assertTrue("第${index + 1}爻应为 LiuYaoLine，实际 ${row!!::class.java.name}", row is LiuYaoLine)
        }
    }

    @Test
    fun `解码后规则层不应抛 ClassCastException`() {
        val bundle = DivChartBundle(method = "liuyao", liuyao = chart)
        val decoded = DivChartCodec.decode(DivChartCodec.encode(bundle))
        val facts = LiuYaoFacts.of(decoded!!.liuyao!!, DivCategory.CAREER)
        assertEquals(listOf(3), facts.xunKongYao)
    }

    @Test
    fun `键名失配的存量 chartJson 应按无效丢弃不崩渲染`() {
        // 旧版本混淆键名（rows -> a 等）：Gson 留下空字段，解码结果不可用
        val bad = """{"method":"liuyao","liuyao":{"a":[2,2,1,4,2,1],"b":"101010"}}"""
        assertEquals(null, DivChartCodec.decode(bad))
    }
}

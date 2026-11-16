package com.smartwash.common.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.PageData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PageData 形状容错回归（2026-11-17 E1 遍历 P0：家庭点单 data:[] 崩溃）：
 * 远程部署漂移返回裸数组时反序列化不抛异常、降级为空页；对象形状泛型 T 无损。
 */
class PageDataShapeTolerantFactoryTest {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapterFactory(PageDataShapeTolerantFactory())
        .create()

    /** ApiResult<PageData<String>> 声明类型 */
    private val pageType: java.lang.reflect.Type =
        TypeToken.getParameterized(
            ApiResult::class.java,
            TypeToken.getParameterized(PageData::class.java, String::class.java).type,
        ).type

    @Test
    fun `裸数组形状降级为空页不崩溃`() {
        val json = """{"code":200,"message":"成功","data":[]}"""
        val result = gson.fromJson<ApiResult<PageData<String>>>(json, pageType)
        val page = requireNotNull(result.data)
        assertTrue(page.records.isEmpty())
        assertEquals(0L, page.total)
    }

    @Test
    fun `对象形状泛型 T 无损解析`() {
        val json = """{"code":200,"message":"成功",
            "data":{"records":["a","b"],"total":2,"size":10,"current":1}}"""
        val result = gson.fromJson<ApiResult<PageData<String>>>(json, pageType)
        val page = requireNotNull(result.data)
        assertEquals(listOf("a", "b"), page.records)
        assertEquals(2L, page.total)
    }

    @Test
    fun `data 为 null 时保持 null`() {
        val json = """{"code":200,"message":"成功","data":null}"""
        val result = gson.fromJson<ApiResult<PageData<String>>>(json, pageType)
        assertTrue(result.data == null)
    }
}

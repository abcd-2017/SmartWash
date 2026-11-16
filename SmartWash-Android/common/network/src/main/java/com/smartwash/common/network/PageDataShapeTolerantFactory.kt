package com.smartwash.common.network

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.smartwash.common.utils.model.PageData

/**
 * PageData JSON 形状容错工厂（2026-11-17 E1 遍历 P0）：
 *
 * 远程服务器部署漂移时，分页接口可能返回裸数组形状（`data: []` 而非
 * `data: {records:[],total,size,current}`）——Gson 按声明类型 `PageData` 反序列化
 * 数组会抛 JsonSyntaxException，业务层未捕获即主线程崩溃（家庭点单点击即崩且
 * 重启复崩，实测见 docs/scan-2026-11-16/e1-workbench-tour.md）。
 *
 * 容错策略：对象形状走默认反射解析（泛型 T 无损）；数组/null/其他形状一律包装为
 * 空页对象再解析——App 不崩、页面展示空态，数据正确性由服务端部署对齐兜底，
 * 端侧不猜测元素类型（裸数组的元素映射需要跨端契约，属服务端修复范畴）。
 *
 * 仅注册于 Retrofit Gson（[NetworkModule]）；ResponseInterceptor 的信封探测
 * 用 raw ApiResult（data 为 Object）不受影响。
 */
class PageDataShapeTolerantFactory : TypeAdapterFactory {

    override fun <T : Any> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (!PageData::class.java.isAssignableFrom(type.rawType)) return null
        val delegate = gson.getDelegateAdapter(this, type)
        return object : TypeAdapter<T>() {
            override fun read(reader: JsonReader): T? {
                val element: JsonElement = JsonParser.parseReader(reader)
                return when {
                    element.isJsonObject -> delegate.fromJsonTree(element)
                    // data:null 保持 null（业务层既有空态语义依赖它）
                    element.isJsonNull -> null
                    // 裸数组等漂移形状：包一层空页对象（records=[]，页面展示空态而非崩溃）
                    else -> delegate.fromJsonTree(emptyPageJson())
                }
            }

            override fun write(out: JsonWriter, value: T) {
                delegate.write(out, value)
            }
        }
    }

    private fun emptyPageJson(): JsonObject = JsonObject().apply {
        add("records", JsonArray())
        addProperty("total", 0)
        addProperty("size", 0)
        addProperty("current", 1)
    }
}

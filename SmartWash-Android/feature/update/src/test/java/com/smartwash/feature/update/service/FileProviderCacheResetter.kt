package com.smartwash.feature.update.service

import androidx.core.content.FileProvider

/**
 * FileProvider 的路径策略按 authority 静态缓存，且绑定首次解析时的 cacheDir；
 * Robolectric 每个测试方法使用不同的临时目录，跨方法残留会让后续测试抛
 * 「Failed to find configured root」。测试间统一清空。
 */
internal object FileProviderCacheResetter {
    fun reset() {
        val field = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        // androidx.core 各版本字段类型在 SparseArray / ArrayMap 间变动，统一经 clear() 清空
        val cache = field.get(null)
        cache.javaClass.getMethod("clear").invoke(cache)
    }
}

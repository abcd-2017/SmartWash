package com.smartwash.feature.readlater

import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 稍后读模块内常量（非用户可见；用户可见文案一律进 res/values/strings.xml）。
 */
object ReadLaterConstant {

    /** 列表分页大小（与后端 list 接口对齐） */
    const val PAGE_SIZE = 10

    /** 收藏状态：未读（后端 status 字段取值，Tab 一/二对应） */
    const val STATUS_UNREAD = 0

    /** 收藏状态：已读 */
    const val STATUS_READ = 1

    /** 收藏 URL 的 https 前缀（契约仅收 https，端上先行简单校验兜底） */
    const val HTTPS_PREFIX = "https://"

    /** 单条收藏标签数上限（端上软约束，超出后端仍兜底校验） */
    const val TAG_MAX_COUNT = 5

    /** 单个标签长度上限 */
    const val TAG_MAX_LENGTH = 12
}

/**
 * 展示格式化工具（纯 JVM）：https 简单校验、域名提取、收藏时间展示。
 */
object ReadLaterFormat {

    /** 空格分隔的时间格式（Java 后端 Jackson 默认序列化的兜底形态之一） */
    private val SPACE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 列表卡片上的时间展示格式 */
    private val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /**
     * https 简单校验：https:// 前缀 + host 非空且不含空白。
     * 只做端上快捷拦截（省一次注定失败的网络请求），完整合法性以后端校验为准。
     */
    fun isValidHttpsUrl(url: String): Boolean {
        if (!url.startsWith(ReadLaterConstant.HTTPS_PREFIX)) return false
        val host = url.removePrefix(ReadLaterConstant.HTTPS_PREFIX).substringBefore('/').trim()
        return host.isNotEmpty() && !host.contains(' ')
    }

    /** 展示用域名（小写、去 www. 前缀；解析失败回退原文） */
    fun hostOf(url: String): String {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase()
            ?: url.removePrefix(ReadLaterConstant.HTTPS_PREFIX).removePrefix("http://").substringBefore('/')
        return host.removePrefix("www.")
    }

    /** 收藏时间展示：解析成功格式化为 yyyy-MM-dd HH:mm，失败原样返回 */
    fun formatCreatedAt(raw: String?): String {
        val parsed = parseOrNull(raw) ?: return raw.orEmpty()
        return parsed.format(DISPLAY_FORMAT)
    }

    private fun parseOrNull(raw: String?): LocalDateTime? {
        val text = raw?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { LocalDateTime.parse(text) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text, SPACE_FORMAT) }.getOrNull()
    }
}

package com.smartwash.game.support;

import java.util.Map;

/**
 * 游戏静态资源扩展名 → Content-Type 映射。
 * 覆盖 H5 游戏常见资源类型；文本类统一追加 charset=utf-8，防浏览器把资源当文件下载或乱码渲染。
 * 未知扩展名回退 application/octet-stream。
 */
public final class GameContentTypes {

    /** HTML 入口与页面 */
    public static final String TEXT_HTML = "text/html";

    private static final Map<String, String> MIME_MAP = Map.ofEntries(
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"),
            Map.entry("js", "application/javascript"),
            Map.entry("mjs", "application/javascript"),
            Map.entry("css", "text/css"),
            Map.entry("json", "application/json"),
            Map.entry("txt", "text/plain"),
            Map.entry("xml", "application/xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("avif", "image/avif"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("ogg", "audio/ogg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("m4a", "audio/mp4"),
            Map.entry("aac", "audio/aac"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("wasm", "application/wasm"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("otf", "font/otf"),
            Map.entry("eot", "application/vnd.ms-fontobject")
    );

    /** 需要追加 charset=utf-8 的文本类 MIME */
    private static final Map<String, Boolean> TEXT_MIME = Map.of(
            "text/html", true,
            "text/css", true,
            "text/plain", true,
            "application/javascript", true,
            "application/json", true,
            "application/xml", true
    );

    private static final String DEFAULT_MIME = "application/octet-stream";

    private GameContentTypes() {
    }

    /**
     * 按文件名（或相对路径）扩展名取 Content-Type；文本类追加 charset=utf-8。
     */
    public static String forFilename(String filename) {
        String mime = rawForFilename(filename);
        return Boolean.TRUE.equals(TEXT_MIME.get(mime)) ? mime + "; charset=utf-8" : mime;
    }

    /**
     * 按扩展名取裸 MIME（不含 charset），供上传对象写入时使用。
     */
    public static String rawForFilename(String filename) {
        if (filename == null) {
            return DEFAULT_MIME;
        }
        String name = filename.toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return DEFAULT_MIME;
        }
        return MIME_MAP.getOrDefault(name.substring(dot + 1), DEFAULT_MIME);
    }
}

package com.smartwash.config

/**
 * 服务端公开配置（**运行期**拉取，替代编译期注入 BuildConfig.MINIO_ENDPOINT）。
 *
 * 历史做法把 MinIO endpoint 经 Gradle 编译进 APK，缺点是域名变更必须重新发版，
 * 且端上无法感知后端实际下发的游戏直链 / 头像地址域名。现改为启动时经
 * [com.smartwash.init.ServerConfigInitTask] 拉 `GET /web/config` 写入本对象。
 *
 * 读取方（当前仅 WebView 深色主题桥白名单）需在**组合期**读取——配置未就绪时（启动极早期）
 * 白名单只含 API host，属可接受降级（游戏页由用户操作打开，此时配置早已就绪）。
 */
object ServerConfig {

    /** MinIO 对外可访问端点（externalEndpoint 优先，缺失回退 endpoint） */
    @Volatile
    var minioEndpoint: String? = null

    /** 短链对外跳转域名（后端拼 shortUrl 用；端上仅展示，不拼接） */
    @Volatile
    var shortLinkBaseUrl: String? = null
}

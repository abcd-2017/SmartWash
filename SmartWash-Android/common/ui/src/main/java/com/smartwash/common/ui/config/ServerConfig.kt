package com.smartwash.common.ui.config

/**
 * 服务端公开配置（**运行期**拉取，替代编译期注入 BuildConfig.MINIO_ENDPOINT）。
 *
 * 历史做法把 MinIO endpoint 经 Gradle 编译进 APK，缺点是域名变更必须重新发版，
 * 且端上无法感知后端实际下发的游戏直链 / 头像地址域名。现改为启动时经
 * app 壳 [com.smartwash.init.ServerConfigInitTask] 拉 `GET /web/config` 写入本对象。
 *
 * 下沉于 common:ui（2026-11-14）：游戏域自建 H5 承载页（feature:game）需要同一份
 * 端点数据组桥 host 白名单，而公共容器（app 壳 WebViewPage）保持纯净——
 * 配置属公共能力，下沉后 app 壳与各 feature 同源读取。
 *
 * 读取方需在**组合期**读取——配置未就绪时（启动极早期）白名单只含 API host，
 * 属可接受降级（游戏页由用户操作打开，此时配置早已就绪）。
 */
object ServerConfig {

    /** 主网关 BASE_URL（app 壳启动时经 ServerConfigInitTask 写入编译期注入值） */
    @Volatile
    var apiBaseUrl: String? = null

    /** MinIO 对外可访问端点（externalEndpoint 优先，缺失回退 endpoint） */
    @Volatile
    var minioEndpoint: String? = null

    /** 短链对外跳转域名（后端拼 shortUrl 用；端上仅展示，不拼接） */
    @Volatile
    var shortLinkBaseUrl: String? = null
}

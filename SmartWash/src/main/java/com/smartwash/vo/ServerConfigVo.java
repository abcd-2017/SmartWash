package com.smartwash.vo;

import lombok.Data;

/**
 * 服务端公开配置（App 启动拉取）。
 *
 * <p>替代编译期注入（BuildConfig.MINIO_ENDPOINT）：MinIO 域名 / 短链域名变更无需重新发版，
 * 端上经 {@code GET /web/config} 运行期获取。</p>
 */
@Data
public class ServerConfigVo {

    /**
     * MinIO 对外可访问端点（externalEndpoint 优先，缺失回退 endpoint）。
     * 端上用途：WebView 深色主题桥 host 白名单（游戏 H5 直拉该域名）。
     */
    private String minioEndpoint;

    /**
     * 短链对外跳转域名（后端拼 shortUrl 用；端上仅展示，不拼接——后端返回的 shortUrl 即完整地址）。
     */
    private String shortLinkBaseUrl;
}

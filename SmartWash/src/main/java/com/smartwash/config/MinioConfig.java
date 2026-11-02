package com.smartwash.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * MinIO 对象存储配置类
 * 从 application.yaml 中读取 minio 前缀的配置项
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {
    /** MinIO 服务端点（内部通信，如 Docker 服务名） */
    private String endpoint;
    /** MinIO 外部访问端点（用于生成预签名 URL，可选） */
    private String externalEndpoint;
    /** 访问密钥 */
    private String accessKey;
    /** 秘密密钥 */
    private String secretKey;
    /** 存储桶名称（上传用） */
    private String bucketName;
    /** APK 下载专用的 MinIO 桶名称（与上传用的 bucket 分开） */
    private String apkBucketName;

    /**
     * 对外可访问端点：优先 external-endpoint（公网/预签名用），未配置时回退 endpoint。
     * 所有拼给端上直链的 URL（游戏可玩地址、头像）必须用本方法，禁止直接用 getEndpoint()
     * ——endpoint 是内部通信地址（如 Docker 服务名），端上无法解析。
     */
    public String publicEndpoint() {
        if (externalEndpoint != null && !externalEndpoint.isBlank()) {
            return externalEndpoint;
        }
        return endpoint;
    }

    /**
     * 由对象键拼对外直链（游戏可玩地址、头像共用同一构造口径）。
     * 对象键与部署地址解耦：换服务器/换域名只需改配置，不必刷库。
     * 路径段做 URL 编码兜底（游戏包 zip 条目名可能含空格/中文）。
     */
    public String urlOf(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return "";
        }
        String base = publicEndpoint();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String key = objectName.startsWith("/") ? objectName.substring(1) : objectName;
        return base + "/" + bucketName + "/" + UriUtils.encodePath(key, StandardCharsets.UTF_8);
    }

    /**
     * 把存量值还原为对象键。
     *
     * <p>历史实现把对外端点拼进了 DB（{@code users.avatar} / {@code game_config.url}），
     * 换域名后这批数据全部失效。端点可能已变更，故按 {@code /{bucket}/} 定位而非按已知端点前缀匹配；
     * 已是对象键的值原样返回，绝对 URL 则解出并 URL 解码为真实对象键（与 {@link #urlOf} 互为逆运算）。</p>
     */
    public String toObjectKey(String stored) {
        if (stored == null) {
            return null;
        }
        String trimmed = stored.trim();
        if (trimmed.isEmpty() || (!trimmed.startsWith("http://") && !trimmed.startsWith("https://"))) {
            return trimmed;
        }
        String marker = "/" + bucketName + "/";
        int idx = trimmed.indexOf(marker);
        if (idx < 0) {
            return trimmed;
        }
        return URLDecoder.decode(trimmed.substring(idx + marker.length()), StandardCharsets.UTF_8);
    }

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}

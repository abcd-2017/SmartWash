package com.smartwash.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}

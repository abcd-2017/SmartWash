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
    /** MinIO 服务端点 */
    private String endpoint;
    /** 访问密钥 */
    private String accessKey;
    /** 秘密密钥 */
    private String secretKey;
    /** 存储桶名称 */
    private String bucketName;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}

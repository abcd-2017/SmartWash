package com.smartwash.workbench.storage;

import com.smartwash.config.MinioConfig;
import com.smartwash.exception.CustomExceptions;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * MinIO 工作台配图存储。
 * 对象名用日期分区 + uuid（内容不可变），直链可长缓存（max-age=31536000, immutable）；
 * 前缀 workbench/covers/ 与游戏包 game/ 互相独立，删除入口不回收对象（孤儿对象量级小，可接受）。
 * 写入元数据经 MinIO GET 随响应头返回（对齐 MinioGameObjectStorage 的 Cache-Control 键名约定）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkbenchImageStorage {

    /** 配图对象前缀（bucket 内独立命名空间，不与 game/apk 前缀交叉） */
    private static final String OBJECT_PREFIX = "workbench/covers/";

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * 写入一张配图，返回公网直链（管理端回填 imageUrl 字段用）。
     *
     * @param content     图片字节（调用方已完成大小与类型校验）
     * @param contentType image/png / image/jpeg / image/webp
     */
    public String putImage(byte[] content, String contentType) {
        String ext = switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            default -> "webp";
        };
        String objectKey = OBJECT_PREFIX
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try (InputStream in = new ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .stream(in, content.length, -1)
                    .contentType(contentType)
                    .userMetadata(Map.of("Cache-Control", "max-age=31536000, immutable"))
                    .build());
        } catch (Exception e) {
            log.error("工作台配图写入失败, objectKey: {}", objectKey, e);
            throw new CustomExceptions("图片存储服务暂不可用，请稍后重试");
        }
        return minioConfig.urlOf(objectKey);
    }
}

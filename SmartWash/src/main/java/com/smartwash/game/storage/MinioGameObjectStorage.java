package com.smartwash.game.storage;

import com.smartwash.config.MinioConfig;
import com.smartwash.exception.GameServiceException;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * MinIO 游戏对象存储实现。
 * 只在读写入口处包一次业务异常（GameServiceException），对象不存在按契约返回 null 不算错误。
 * 删除走「列举前缀 → 逐个删除」，删除失败抛出异常由调用方决定是否阻断（fail-closed，防存储泄漏）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioGameObjectStorage implements GameObjectStorage {

    private static final String HEADER_CONTENT_LENGTH = "Content-Length";
    private static final String HEADER_ETAG = "ETag";

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @Override
    public void putObject(String objectKey, byte[] content, String contentType) {
        try (InputStream in = new ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .stream(in, content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("游戏对象写入失败, objectKey: {}", objectKey, e);
            throw new GameServiceException(GameServiceException.INTERNAL_ERROR, "游戏存储服务暂不可用，请稍后重试");
        }
    }

    @Override
    public void removeObjectsByPrefix(String prefix) {
        try {
            List<String> objectKeys = new LinkedList<>();
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .prefix(prefix)
                    .build());
            for (Result<Item> result : results) {
                objectKeys.add(result.get().objectName());
            }
            for (String objectKey : objectKeys) {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(objectKey)
                        .build());
            }
            if (!objectKeys.isEmpty()) {
                log.info("游戏前缀清理完成, prefix: {}, 对象数: {}", prefix, objectKeys.size());
            }
        } catch (Exception e) {
            log.error("游戏前缀清理失败, prefix: {}", prefix, e);
            throw new GameServiceException(GameServiceException.INTERNAL_ERROR, "游戏存储服务暂不可用，请稍后重试");
        }
    }

    @Override
    public GameObject getObject(String objectKey) {
        try {
            GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .build());
            return new GameObject(response, parseLong(response.headers(), HEADER_CONTENT_LENGTH),
                    trimQuoted(response.headers(), HEADER_ETAG));
        } catch (ErrorResponseException e) {
            // S3 语义 NoSuchKey = 对象不存在，按契约返回 null（转发层给 404）
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                log.debug("游戏对象读取未命中, objectKey: {}", objectKey);
                return null;
            }
            log.error("游戏对象读取失败, objectKey: {}, code: {}", objectKey, e.errorResponse().code(), e);
            throw new GameServiceException(GameServiceException.INTERNAL_ERROR, "游戏存储服务暂不可用，请稍后重试");
        } catch (Exception e) {
            log.error("游戏对象读取失败, objectKey: {}", objectKey, e);
            throw new GameServiceException(GameServiceException.INTERNAL_ERROR, "游戏存储服务暂不可用，请稍后重试");
        }
    }

    private long parseLong(Headers headers, String name) {
        String value = headers == null ? null : headers.get(name);
        if (value == null) {
            return -1;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String trimQuoted(Headers headers, String name) {
        String value = headers == null ? null : headers.get(name);
        if (value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}

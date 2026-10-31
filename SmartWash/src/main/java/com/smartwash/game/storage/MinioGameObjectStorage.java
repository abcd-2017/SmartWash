package com.smartwash.game.storage;

import com.smartwash.config.MinioConfig;
import com.smartwash.exception.GameServiceException;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * MinIO 游戏对象存储实现。
 * 写入：Content-Type + Cache-Control 均为对象元数据，MinIO GET 时随响应头返回（直链下发的缓存与渲染依赖）。
 * MinIO SDK 约定：userMetadata 中属标准 HTTP 头的键（如 Cache-Control）按标准头存储，
 * 其余以 x-amz-meta- 前缀存储，故此处直接用 "Cache-Control" 键名。
 * 删除走「列举前缀 → 逐个删除」，失败抛业务异常由调用方决定是否阻断（fail-closed，防存储泄漏）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioGameObjectStorage implements GameObjectStorage {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @Override
    public void putObject(String objectKey, byte[] content, String contentType, String cacheControl) {
        try (InputStream in = new ByteArrayInputStream(content)) {
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .stream(in, content.length, -1)
                    .contentType(contentType);
            if (cacheControl != null) {
                builder.userMetadata(Map.of("Cache-Control", cacheControl));
            }
            minioClient.putObject(builder.build());
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
        } catch (ErrorResponseException e) {
            // 前缀无对象等不存在类错误按幂等静默，其余存储故障转业务异常
            if ("NoSuchKey".equals(e.errorResponse().code()) || "NoSuchBucket".equals(e.errorResponse().code())) {
                log.debug("游戏前缀清理未命中对象, prefix: {}", prefix);
                return;
            }
            log.error("游戏前缀清理失败, prefix: {}, code: {}", prefix, e.errorResponse().code(), e);
            throw new GameServiceException(GameServiceException.INTERNAL_ERROR, "游戏存储服务暂不可用，请稍后重试");
        } catch (Exception e) {
            log.error("游戏前缀清理失败, prefix: {}", prefix, e);
            throw new GameServiceException(GameServiceException.INTERNAL_ERROR, "游戏存储服务暂不可用，请稍后重试");
        }
    }
}

package com.smartwash.toolbox.delivery.toolkit;

import com.smartwash.config.MinioConfig;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * 设备投递 MinIO 对象存取（key 语义：delivery/{sessionCode}/{uuid}.{ext}）。
 * 与 MinioStorageServiceImpl 的 URL 语义不同：投递全程持有 object_key，不拼不暴露对象 URL
 * （下载走后端流式转发，保留应用层感知「传输完成」的能力，见调研 §4.4）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryObjectStorage {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /** 上传条目文件，返回相对 object_key（delivery/{code}/{uuid}.{ext}） */
    public String put(MultipartFile file, String sessionCode) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String objectKey = "delivery/" + sessionCode + "/" + UUID.randomUUID() + extension;
        minioClient.putObject(
                io.minio.PutObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(objectKey)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );
        return objectKey;
    }

    /** 打开对象输入流（调用方负责关闭与生命周期回调） */
    public GetObjectResponse openStream(String objectKey) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(objectKey)
                        .build()
        );
    }

    /** 精确删除单个对象（焚毁路径）。失败抛出，由调用方决定 fail-closed 策略 */
    public void remove(String objectKey) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(objectKey)
                        .build()
        );
    }

    /** 按前缀批量删除（会话回收路径）：删除失败仅记录，残余对象由下一次清理兜底 */
    public void removeByPrefix(String prefix) {
        try {
            List<DeleteObject> targets = new LinkedList<>();
            for (Result<Item> r : minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(minioConfig.getBucketName()).prefix(prefix).build())) {
                targets.add(new DeleteObject(decode(r.get().objectName())));
            }
            if (targets.isEmpty()) {
                return;
            }
            for (Result<DeleteError> r : minioClient.removeObjects(
                    RemoveObjectsArgs.builder().bucket(minioConfig.getBucketName()).objects(targets).build())) {
                DeleteError e = r.get();
                if (e != null) {
                    log.warn("投递对象批量删除失败, key: {}, message: {}", e.objectName(), e.message());
                }
            }
            log.debug("投递对象前缀清理完成, prefix: {}, 共 {} 个", prefix, targets.size());
        } catch (Exception e) {
            log.error("投递对象前缀清理异常, prefix: {}", prefix, e);
        }
    }

    /** listObjects 返回的 objectName 可能带 URL 编码，与 MinioStorageServiceImpl.deleteFile 同样处理 */
    private String decode(String objectName) {
        try {
            return URLDecoder.decode(objectName, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return objectName;
        }
    }
}

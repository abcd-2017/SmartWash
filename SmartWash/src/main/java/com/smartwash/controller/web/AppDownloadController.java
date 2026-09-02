package com.smartwash.controller.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.smartwash.common.Result;
import com.smartwash.config.MinioConfig;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 应用预签名下载接口
 * <p>
 * App 调用 {@code GET /web/app/download} 获取 1 小时有效期的 MinIO 预签名下载地址，
 * 避免将 MinIO 桶设为公共读，提升文件访问安全性。
 * </p>
 */
@Tag(name = "用户端-应用版本", description = "App 预签名下载接口")
@Slf4j
@RestController
@RequestMapping("/web")
@RequiredArgsConstructor
public class AppDownloadController {

    /** 版本文件路径，可通过配置文件覆盖；默认 /app/config/releases/latest/version.json */
    @Value("${app.version.file-path:/app/config/releases/latest/version.json}")
    private String versionFilePath;

    /** APK 在 MinIO 桶下的对象前缀路径，与 deploy.sh 的 MINIO_APK_PATH 保持一致 */
    @Value("${app.apk.object-prefix:android/}")
    private String apkObjectPrefix;

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * 获取最新版本的预签名下载 URL
     *
     * <p>从 version.json 中读取 {@code fileName}，生成 1 小时有效期的 MinIO 预签名 GET 地址。</p>
     *
     * @return 预签名下载 URL，文件不存在、解析失败或 MinIO 异常时返回失败提示
     */
    @Operation(summary = "获取预签名下载 URL", description = "读取 version.json 中的 fileName，返回 1 小时有效期的 MinIO 预签名下载地址")
    @GetMapping("/app/download")
    public Result<String> getPresignedDownloadUrl() {
        Path path = Paths.get(versionFilePath);

        // 文件不存在时返回提示
        if (!Files.exists(path)) {
            log.warn("版本文件不存在：{}", versionFilePath);
            return Result.failMsg("暂无版本信息");
        }

        try {
            // 读取并解析 version.json，取 fileName 字段
            String content = Files.readString(path);
            JSONObject jsonObject = JSON.parseObject(content);
            String fileName = jsonObject.getString("fileName");
            if (fileName == null || fileName.isBlank()) {
                log.error("version.json 中缺少 fileName 字段：{}", versionFilePath);
                return Result.failMsg("版本信息缺少文件名");
            }

            // 拼接完整的 MinIO 对象路径（前缀 + 文件名），生成 1 小时有效期的预签名下载 URL
            String objectName = apkObjectPrefix + fileName;
            String downloadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .method(io.minio.http.Method.GET)
                            .expiry(3600)
                            .build()
            );

            log.debug("生成预签名下载 URL 成功：fileName={}", fileName);
            return Result.ok(downloadUrl);
        } catch (MinioException e) {
            log.error("生成预签名下载 URL 失败：fileName 异常, path={}", versionFilePath, e);
            return Result.failMsg("下载地址生成失败");
        } catch (Exception e) {
            log.error("读取或解析版本文件失败：{}", versionFilePath, e);
            return Result.failMsg("版本信息读取失败");
        }
    }
}

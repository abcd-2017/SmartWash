package com.smartwash.controller.web;

import com.smartwash.common.AppChannel;
import com.smartwash.common.Result;
import com.smartwash.config.MinioConfig;
import com.smartwash.service.AppVersionService;
import com.smartwash.vo.ChannelVersion;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用预签名下载接口
 * <p>
 * App 调用 {@code GET /web/app/download?channel=full|lite} 获取 1 小时有效期的 MinIO 预签名下载地址，
 * 避免将 MinIO 桶设为公共读，提升文件访问安全性。
 * </p>
 * <p>
 * 对象键 = 渠道对象前缀 + 该渠道的 fileName：
 * full 用 {@code app.apk.object-prefix}（默认 {@code android/}），
 * lite 用 {@code app.apk.object-prefix-lite}（默认 {@code android-lite/}）。
 * </p>
 */
@Tag(name = "用户端-应用版本", description = "App 预签名下载接口")
@Slf4j
@RestController
@RequestMapping("/web")
@RequiredArgsConstructor
public class AppDownloadController {

    /** full 渠道 APK 在 MinIO 桶下的对象前缀路径，与 deploy.sh 的 MINIO_APK_PATH 保持一致 */
    @Value("${app.apk.object-prefix:android/}")
    private String apkObjectPrefix;

    /** lite 渠道（吃啥）APK 在 MinIO 桶下的对象前缀路径 */
    @Value("${app.apk.object-prefix-lite:android-lite/}")
    private String apkObjectPrefixLite;

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final AppVersionService appVersionService;

    /**
     * 获取指定渠道最新版本的预签名下载 URL
     *
     * <p>从 version.json 中读取该渠道的 {@code fileName}，按渠道选对象前缀拼接完整对象键，
     * 生成 1 小时有效期的 MinIO 预签名 GET 地址。</p>
     *
     * @param channel 渠道标识（full / lite），缺省 full
     * @return 预签名下载 URL，渠道不存在、文件缺失或 MinIO 异常时返回失败提示
     */
    @Operation(summary = "获取预签名下载 URL", description = "按 channel 选对象前缀（full=android/，lite=android-lite/），返回 1 小时有效期的 MinIO 预签名下载地址")
    @GetMapping("/app/download")
    public Result<String> getPresignedDownloadUrl(
            @Parameter(description = "渠道：full（全量版，缺省）/ lite（吃啥精简版）")
            @RequestParam(value = "channel", required = false) String channel) {
        try {
            // 解析渠道（缺省 full，非法取值由 resolveArtifact 统一报错），再按渠道取产物
            AppChannel resolved = AppChannel.resolveOrDefault(channel);
            ChannelVersion artifact = appVersionService.resolveArtifact(channel);
            String fileName = artifact.getFileName();

            // 拼接完整的 MinIO 对象路径（渠道前缀 + 文件名）
            String objectName = objectPrefix(resolved) + fileName;

            // 配置了 external-endpoint 则用外部地址创建 MinioClient 生成预签名 URL（签名 Host 一致），否则兜底用内部 MinioClient
            String externalEndpoint = minioConfig.getExternalEndpoint();
            MinioClient presignClient = minioClient;
            if (externalEndpoint != null && !externalEndpoint.isBlank()) {
                MinioClient.Builder builder = MinioClient.builder()
                        .endpoint(externalEndpoint)
                        .credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey());
                // 与内部客户端同口径显式 region，避免 SDK 回退发起 region 查询请求
                if (minioConfig.getRegion() != null && !minioConfig.getRegion().isBlank()) {
                    builder.region(minioConfig.getRegion());
                }
                presignClient = builder.build();
            }

            String downloadUrl = presignClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(minioConfig.getApkBucketName())
                            .object(objectName)
                            .method(io.minio.http.Method.GET)
                            .expiry(3600)
                            .build()
            );

            log.debug("生成预签名下载 URL 成功：channel={}, objectName={}", resolved.getCode(), objectName);
            return Result.ok(downloadUrl);
        } catch (MinioException e) {
            log.error("生成预签名下载 URL 失败：channel={}", channel, e);
            return Result.failMsg("下载地址生成失败");
        } catch (Exception e) {
            // 渠道不存在 / 版本清单缺失等业务失败：透传业务提示，端上按失败分支处理
            log.warn("生成预签名下载 URL 失败：channel={}, message={}", channel, e.getMessage());
            return Result.failMsg(e.getMessage());
        }
    }

    /** 按渠道选对象前缀：lite 走独立前缀，其余（含 full）走默认前缀 */
    private String objectPrefix(AppChannel channel) {
        return channel == AppChannel.LITE ? apkObjectPrefixLite : apkObjectPrefix;
    }
}

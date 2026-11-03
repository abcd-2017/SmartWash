package com.smartwash.toolbox.delivery.toolkit;

import com.smartwash.toolbox.delivery.service.ToolboxDeliveryService;
import com.smartwash.toolbox.delivery.service.ToolboxDeliveryService.DeliveryFileDownload;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 投递文件流式转发器（owner / 对端两个控制器共用）。
 * 生命周期（PsiTransfer claim 全套语义）：
 * - 正常完成：删除 MinIO 对象（焚毁）；删除失败由 service 保 taken=1（fail-closed）；
 * - 中断/开流失败：释放认领（taken 回 0）允许重取。
 * 对象 URL 全程不暴露（调研 §4.4：保留应用层感知「传输完成」的能力）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryFileStreaming {

    private final MinioClient minioClient;

    public ResponseEntity<StreamingResponseBody> toResponse(ToolboxDeliveryService service,
                                                            DeliveryFileDownload download) {
        StreamingResponseBody body = out -> {
            try (GetObjectResponse in = minioClient.getObject(
                    GetObjectArgs.builder().object(download.getObjectKey()).build())) {
                in.transferTo(out);
                out.flush();
                // 流正常完成才焚毁对象
                service.onFileDownloadComplete(download);
            } catch (Exception e) {
                // 中断释放认领，允许重取
                service.onFileDownloadInterrupted(download);
                log.debug("投递文件转发中断, itemId: {}, objectKey: {}", download.getItemId(), download.getObjectKey());
                throw new IllegalStateException("文件转发中断", e);
            }
        };
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Content-Disposition", "inline; filename*=UTF-8''"
                        + URLEncoder.encode(download.getFileName() == null ? "file" : download.getFileName(),
                        StandardCharsets.UTF_8).replace("+", "%20"));
        if (download.getMimeType() != null && !download.getMimeType().isBlank()) {
            builder.contentType(MediaType.parseMediaType(download.getMimeType()));
        } else {
            builder.contentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        if (download.getObjectSize() != null) {
            builder.contentLength(download.getObjectSize());
        }
        return builder.body(body);
    }
}

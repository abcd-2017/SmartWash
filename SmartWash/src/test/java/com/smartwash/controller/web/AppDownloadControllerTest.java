package com.smartwash.controller.web;

import com.smartwash.common.Result;
import com.smartwash.config.MinioConfig;
import com.smartwash.service.AppVersionService;
import com.smartwash.vo.ChannelVersion;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预签名下载接口单测（mock MinioClient + 真实 AppVersionService 读临时清单）。
 *
 * <p>核心断言：对象键 = 渠道前缀 + 该渠道 fileName，full 与 lite 分目录不串包；
 * 预签名参数保持 1 小时有效期与 APK 专用桶。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AppDownloadController 按渠道拼对象键")
class AppDownloadControllerTest {

    private static final String FULL_PREFIX = "android/";
    private static final String LITE_PREFIX = "android-lite/";
    private static final String APK_BUCKET = "smartwash-apk";

    @Mock
    private MinioClient minioClient;
    @Mock
    private AppVersionService appVersionService;

    private AppDownloadController controller;
    private MinioConfig minioConfig;

    @BeforeEach
    void setUp() {
        minioConfig = new MinioConfig();
        minioConfig.setAccessKey("ak");
        minioConfig.setSecretKey("sk");
        minioConfig.setApkBucketName(APK_BUCKET);

        controller = new AppDownloadController(minioClient, minioConfig, appVersionService);
        ReflectionTestUtils.setField(controller, "apkObjectPrefix", FULL_PREFIX);
        ReflectionTestUtils.setField(controller, "apkObjectPrefixLite", LITE_PREFIX);
    }

    private void mockArtifact(String channel, String fileName) {
        ChannelVersion artifact = new ChannelVersion();
        artifact.setFileName(fileName);
        artifact.setFileSize(1L);
        artifact.setSha256("sha-" + channel);
        when(appVersionService.resolveArtifact(channel)).thenReturn(artifact);
    }

    @Test
    @DisplayName("channel 缺省：走 full 前缀 android/")
    void download_defaultChannel_fullPrefix() throws Exception {
        mockArtifact(null, "app-1.0.0.apk");
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://cdn.example.com/signed");

        Result<String> result = controller.getPresignedDownloadUrl(null);

        assertEquals(200, result.getCode());
        assertEquals("https://cdn.example.com/signed", result.getData());
        ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertEquals("android/app-1.0.0.apk", captor.getValue().object());
        assertEquals(APK_BUCKET, captor.getValue().bucket());
        assertEquals(3600, captor.getValue().expiry());
    }

    @Test
    @DisplayName("channel=lite：走 lite 前缀 android-lite/，与 full 分目录")
    void download_liteChannel_litePrefix() throws Exception {
        mockArtifact("lite", "app-lite-1.0.0.apk");
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://cdn.example.com/signed-lite");

        Result<String> result = controller.getPresignedDownloadUrl("lite");

        assertEquals(200, result.getCode());
        ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertEquals("android-lite/app-lite-1.0.0.apk", captor.getValue().object());
    }

    @Test
    @DisplayName("渠道不存在：透传业务失败提示，不生成预签名 URL")
    void download_unknownChannel_fails() {
        when(appVersionService.resolveArtifact("beta"))
                .thenThrow(new com.smartwash.exception.CustomExceptions("不支持的更新渠道：beta"));

        Result<String> result = controller.getPresignedDownloadUrl("beta");

        assertEquals(201, result.getCode());
        assertEquals("不支持的更新渠道：beta", result.getMessage());
        assertTrue(result.getData() == null);
    }

    @Test
    @DisplayName("清单缺少渠道产物：透传失败提示，不生成预签名 URL")
    void download_missingArtifact_fails() {
        when(appVersionService.resolveArtifact("lite"))
                .thenThrow(new com.smartwash.exception.CustomExceptions("暂无 吃啥精简版 的版本信息"));

        Result<String> result = controller.getPresignedDownloadUrl("lite");

        assertEquals(201, result.getCode());
        assertEquals("暂无 吃啥精简版 的版本信息", result.getMessage());
    }

    @Test
    @DisplayName("MinIO 异常：返回通用失败提示，不泄露内部细节")
    void download_minioException_fails() throws Exception {
        mockArtifact(null, "app-1.0.0.apk");
        // getPresignedObjectUrl 声明的受检异常均继承自 MinioException，
        // 用 InsufficientDataException 模拟签名失败，验证落入 MinioException 分支返回通用文案
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new io.minio.errors.InsufficientDataException("sign failed"));

        Result<String> result = controller.getPresignedDownloadUrl(null);

        assertEquals(201, result.getCode());
        assertEquals("下载地址生成失败", result.getMessage());
    }

    @Test
    @DisplayName("配置了 external-endpoint：预签名 URL 的 Host 为外部端点（端上直连可达）")
    void download_externalEndpoint_usesExternalClient() throws Exception {
        MinioConfig externalConfig = new MinioConfig();
        externalConfig.setAccessKey("ak");
        externalConfig.setSecretKey("sk");
        externalConfig.setApkBucketName(APK_BUCKET);
        // 显式 region：minio 8.5.7 未指定 region 时预签名会同步发起 region 查询请求，
        // 单测环境不可联网，故此处固定 region 让签名纯离线完成
        externalConfig.setRegion("us-east-1");
        externalConfig.setExternalEndpoint("https://cdn.example.com");

        mockArtifact(null, "app-1.0.0.apk");

        AppDownloadController externalController =
                new AppDownloadController(minioClient, externalConfig, appVersionService);
        ReflectionTestUtils.setField(externalController, "apkObjectPrefix", FULL_PREFIX);
        ReflectionTestUtils.setField(externalController, "apkObjectPrefixLite", LITE_PREFIX);

        Result<String> result = externalController.getPresignedDownloadUrl(null);

        assertEquals(200, result.getCode());
        assertTrue(result.getData().startsWith("https://cdn.example.com/"),
                "external-endpoint 必须作为预签名 URL 的 Host，保证端上直连可达");
        assertTrue(result.getData().contains("smartwash-apk/android/app-1.0.0.apk"),
                "预签名 URL 必须携带 APK 桶与 full 前缀拼出的对象键");
    }
}

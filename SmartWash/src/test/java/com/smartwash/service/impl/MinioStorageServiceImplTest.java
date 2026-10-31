package com.smartwash.service.impl;

import com.smartwash.config.MinioConfig;
import io.minio.BucketExistsArgs;
import io.minio.GetBucketPolicyArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MinIO 存储服务初始化测试（mock MinioClient，不连存储）。
 * 覆盖：启动收窄链路——桶不存在建桶后写入前缀白名单策略（历史全桶公共读被覆盖）；
 * 白名单已符合时幂等跳过；头像默认对象直链依赖的 default/ 前缀在策略内（收窄后不回归）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MinioStorageServiceImpl 启动策略收窄测试")
class MinioStorageServiceImplTest {

    private static final String BUCKET = "smartwash";

    @Mock
    private MinioClient minioClient;

    private MinioStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        MinioConfig minioConfig = new MinioConfig();
        minioConfig.setEndpoint("http://minio.local:9000");
        minioConfig.setBucketName(BUCKET);
        service = new MinioStorageServiceImpl(minioClient, minioConfig);
    }

    @Test
    @DisplayName("启动：历史全桶公共读被覆盖为前缀白名单，仅 game/avatar/default 可匿名 GetObject")
    void init_legacyPolicy_overwrittenWithScopedPolicy() throws Exception {
        String legacy = """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},
                 "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::smartwash/*"]}]}
                """;
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.getBucketPolicy(any(GetBucketPolicyArgs.class))).thenReturn(legacy);
        // 默认头像已存在（statObject 正常返回即跳过上传分支，避免无关交互）
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(null);

        service.init();

        ArgumentCaptor<SetBucketPolicyArgs> captor = ArgumentCaptor.forClass(SetBucketPolicyArgs.class);
        verify(minioClient).setBucketPolicy(captor.capture());
        String applied = captor.getValue().config();
        assertEquals(MinioPublicReadPolicy.buildScopedPolicy(BUCKET), applied,
                "生效策略必须等于白名单文档（game/avatar/default 三前缀，仅 GetObject）");
        assertTrue(applied.contains("arn:aws:s3:::" + BUCKET + "/avatar/*"),
                "收窄后 avatar/* 仍必须匿名可读（头像链路回归锚点）");
        assertTrue(applied.contains("arn:aws:s3:::" + BUCKET + "/default/*"),
                "收窄后 default/* 仍必须匿名可读（默认头像直链锚点）");
        assertTrue(applied.contains("arn:aws:s3:::" + BUCKET + "/game/*"),
                "game/* 在白名单内（游戏直链天然可读）");
    }

    @Test
    @DisplayName("启动：白名单已符合则幂等跳过，不重复写策略")
    void init_policyAlreadyScoped_skipsWrite() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.getBucketPolicy(any(GetBucketPolicyArgs.class)))
                .thenReturn(MinioPublicReadPolicy.buildScopedPolicy(BUCKET));
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(null);

        service.init();

        verify(minioClient, org.mockito.Mockito.never()).setBucketPolicy(any(SetBucketPolicyArgs.class));
    }

    @Test
    @DisplayName("启动：读取策略异常（无策略/MinIO 拒绝）按无策略处理，仍完成收窄写入")
    void init_policyReadFails_stillWritesScopedPolicy() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.getBucketPolicy(any(GetBucketPolicyArgs.class)))
                .thenThrow(new RuntimeException("NoSuchBucketPolicy"));
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(null);

        service.init();

        ArgumentCaptor<SetBucketPolicyArgs> captor = ArgumentCaptor.forClass(SetBucketPolicyArgs.class);
        verify(minioClient).setBucketPolicy(captor.capture());
        assertEquals(MinioPublicReadPolicy.buildScopedPolicy(BUCKET), captor.getValue().config());
    }
}

package com.smartwash.service.impl;

import com.smartwash.config.MinioConfig;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.service.FileStorageService;
import io.minio.*;
import io.minio.errors.MinioException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 基于 MinIO 的文件存储服务实现
 *
 * <p><b>访问控制（评审报告后端 #23 已部分收敛）</b>：存储桶匿名访问已收窄为
 * {@link MinioPublicReadPolicy#PUBLIC_READ_PREFIXES} 前缀白名单
 * （game/ 游戏直链、avatar/ 用户头像、default/ 默认头像），仅授予 s3:GetObject，
 * 其余前缀（投递等私密内容）不再匿名可读；桶级公共读泄露面已消除。
 * <b>残留待办</b>：白名单内对象仍为永久直链（无时效），后续如需进一步收敛，
 * 对象访问应改为后端签发的预签名 URL（presigned GET，短时效）——与 APK 下载桶的做法一致。
 * <b>影响面</b>：三端（Android / 鸿蒙 / Web 管理后台）当前均直接使用后端返回的完整 URL 渲染头像，
 * 改造需同步调整头像字段的语义（返回签名 URL）或新增签名换取接口，并连带处理三端图片加载逻辑、
 * default/avatar.png 等内置资源的访问方式以及 URL 缓存/过期刷新策略，涉及接口响应结构变更。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageServiceImpl implements FileStorageService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * 初始化：确保存储桶存在，并检查/上传默认头像
     */
    @PostConstruct
    public void init() {
        try {
            ensureBucketExists();
            setScopedPublicReadPolicy();
            initDefaultAvatar();
        } catch (Exception e) {
            log.error("MinIO 初始化失败", e);
        }
    }

    @Override
    public String uploadFile(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new CustomExceptions("上传文件不能为空");
        }

        try {
            ensureBucketExists();

            // 生成唯一的文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String objectName = directory + "/" + UUID.randomUUID() + extension;

            // 上传文件到 MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // 拼接并返回访问 URL
            String url = minioConfig.getEndpoint() + "/" + minioConfig.getBucketName() + "/" + objectName;
            log.info("文件上传成功: {}", url);
            return url;
        } catch (CustomExceptions e) {
            throw e;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new CustomExceptions("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }

        try {
            // 从 URL 中提取 objectName
            String prefix = minioConfig.getEndpoint() + "/" + minioConfig.getBucketName() + "/";
            if (!fileUrl.startsWith(prefix)) {
                log.warn("文件 URL 格式不正确，无法提取 objectName: {}", fileUrl);
                return;
            }
            String objectName = URLDecoder.decode(
                    fileUrl.substring(prefix.length()), StandardCharsets.UTF_8
            );

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .build()
            );
            log.info("文件删除成功: {}", objectName);
        } catch (Exception e) {
            log.error("文件删除失败: {}", fileUrl, e);
            throw new CustomExceptions("文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 确保存储桶存在，不存在则创建
     */
    private void ensureBucketExists() throws Exception {
        String bucketName = minioConfig.getBucketName();
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
        );
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build()
            );
            log.info("MinIO 存储桶创建成功: {}", bucketName);
        }
    }

    /**
     * 匿名只读策略收窄为前缀白名单（评审 #23 收敛，幂等）：
     * 仅 game/avatar/default 三前缀授予 s3:GetObject（不授 ListBucket/写入），
     * 其余前缀（投递等私密内容）不再匿名可读。已符合则跳过写入；
     * 不符合（含历史全桶公共读）则覆盖为目标白名单——桶策略由本类统一管理，
     * 覆盖会移除其它既有语句（当前无第三方语句，如部署侧手工配置过需自行合并）。
     */
    private void setScopedPublicReadPolicy() {
        String bucketName = minioConfig.getBucketName();
        String existing = null;
        try {
            existing = minioClient.getBucketPolicy(
                    GetBucketPolicyArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            // 无策略时 MinIO 抛 NoSuchBucketPolicy 类错误，按无策略处理继续收窄
            log.debug("读取桶策略未命中（视为无策略）: {}", e.getMessage());
        }
        if (MinioPublicReadPolicy.isScopedSatisfied(existing, bucketName)) {
            log.info("桶匿名只读白名单策略已符合，跳过设置，当前策略: {}", existing);
            return;
        }
        String scoped = MinioPublicReadPolicy.buildScopedPolicy(bucketName);
        try {
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .config(scoped)
                            .build()
            );
            log.info("桶匿名只读策略已收窄为前缀白名单 {}，生效策略: {}",
                    MinioPublicReadPolicy.PUBLIC_READ_PREFIXES, scoped);
            if (existing != null && !existing.isBlank()) {
                log.info("原桶策略已被收窄覆盖: {}", existing);
            }
        } catch (Exception e) {
            log.warn("设置 MinIO 桶白名单策略失败（游戏/头像直链将无法匿名读取，请检查 MinIO 权限）", e);
        }
    }

    /**
     * 初始化默认头像：检查 default/avatar.png 是否存在，不存在则上传
     */
    private void initDefaultAvatar() {
String objectName = "default/avatar.png";
        try {
            // 检查默认头像是否已存在
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .build()
            );
            log.info("默认头像已存在，跳过上传");
        } catch (Exception e) {
            // 对象不存在，需要上传
            log.info("默认头像不存在，准备上传...");
            try {
                byte[] avatarBytes = loadDefaultAvatarBytes();
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(minioConfig.getBucketName())
                                .object(objectName)
                                .stream(new ByteArrayInputStream(avatarBytes), avatarBytes.length, -1)
                                .contentType("image/png")
                                .build()
                );
                log.info("默认头像上传成功");
            } catch (Exception ex) {
                log.error("默认头像上传失败", ex);
            }
        }
    }

    /**
     * 加载默认头像字节数组：优先从 classpath 读取，不存在则用代码生成
     */
    private byte[] loadDefaultAvatarBytes() throws Exception {
        // 优先从 classpath 读取
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("static/default-avatar.png")) {
            if (is != null) {
                log.info("从 classpath 加载默认头像");
                return is.readAllBytes();
            }
        }

        // classpath 中没有，用代码生成一个简单的灰色圆形头像
        log.info("classpath 中未找到默认头像，使用代码生成");
        return generateDefaultAvatar();
    }

    /**
     * 生成简单的灰色圆形默认头像 PNG
     */
    private byte[] generateDefaultAvatar() throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                200, 200, java.awt.image.BufferedImage.TYPE_INT_ARGB
        );
        java.awt.Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(
                java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON
        );
        // 灰色背景圆
        g2d.setColor(new java.awt.Color(200, 200, 200));
        g2d.fillOval(0, 0, 200, 200);
        // 深灰色人物轮廓
        g2d.setColor(new java.awt.Color(160, 160, 160));
        g2d.fillOval(70, 40, 60, 60);       // 头部
        g2d.fillRoundRect(50, 110, 100, 80, 40, 40); // 身体
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}

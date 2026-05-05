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

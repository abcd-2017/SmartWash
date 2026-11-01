package com.smartwash.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务接口
 * 提供文件上传和删除的统一抽象
 */
public interface FileStorageService {

    /**
     * 上传文件到指定目录
     *
     * @param file      待上传的文件
     * @param directory 目标目录（如 "avatar"、"order" 等）
     * @return 对象键（如 {@code avatar/uuid.png}）。刻意不返回绝对 URL：
     *         端点变更不应迫使刷库，对外直链由 {@link com.smartwash.config.MinioConfig#urlOf} 拼装
     */
    String uploadFile(MultipartFile file, String directory);

    /**
     * 根据对象键删除文件
     *
     * @param objectName 对象键；兼容传入存量的绝对 URL（内部经
     *                   {@link com.smartwash.config.MinioConfig#toObjectKey} 还原）
     */
    void deleteFile(String objectName);
}

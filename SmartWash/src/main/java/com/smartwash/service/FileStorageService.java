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
     * @return 文件的访问 URL
     */
    String uploadFile(MultipartFile file, String directory);

    /**
     * 根据文件 URL 删除文件
     *
     * @param fileUrl 文件的完整访问 URL
     */
    void deleteFile(String fileUrl);
}

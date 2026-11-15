package com.smartwash.vo;

import lombok.Data;

/**
 * 单个渠道（full / lite）的 APK 产物信息，对应 version.json 的 {@code channels.<channel>} 节点。
 *
 * <p>不同渠道是独立 App，产物文件名/大小/校验值各不相同，必须按渠道分别描述。</p>
 */
@Data
public class ChannelVersion {

    /** MinIO 对象名（如 app-1.0.0.apk / app-lite-1.0.0.apk），不含对象前缀 */
    private String fileName;

    /** APK 文件大小（字节） */
    private Long fileSize;

    /** APK 文件的 SHA-256 校验值 */
    private String sha256;
}

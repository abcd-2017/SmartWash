package com.smartwash.vo;

import lombok.Data;

/**
 * 按渠道扁平化后的版本信息 VO（{@code GET /web/app/version?channel=xxx} 的 data）。
 *
 * <p>扁平结构：顶层版本字段（versionCode/versionName/forceUpdate/changelog 等）+
 * 该渠道的产物字段（fileName/fileSize/sha256），端上沿用既有字段名，无需改解析。</p>
 */
@Data
public class ChannelVersionVo {

    /** 本次下发渠道（回显，便于端上自查） */
    private String channel;

    /** 版本号（整数，用于比较新旧） */
    private Integer versionCode;

    /** 版本名称（展示用） */
    private String versionName;

    /** APK 下载地址（保留字段，与旧清单一致，通常为空） */
    private String apkUrl;

    /** 该渠道的 MinIO 对象名（不含对象前缀） */
    private String fileName;

    /** 该渠道的 APK 文件大小（字节） */
    private Long fileSize;

    /** 该渠道的 APK SHA-256 校验值 */
    private String sha256;

    /** 是否强制更新 */
    private Boolean forceUpdate;

    /** 强制更新的最低版本号 */
    private Integer minVersionCode;

    /** 更新日志 */
    private String changelog;

    /** 发布日期（格式 yyyy-MM-dd） */
    private String releaseDate;
}

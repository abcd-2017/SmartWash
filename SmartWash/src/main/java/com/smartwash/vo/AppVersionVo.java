package com.smartwash.vo;

import lombok.Data;

/**
 * 应用版本信息 VO，与服务器磁盘上的 version.json 一一对应
 */
@Data
public class AppVersionVo {

    /** 版本号（整数，用于比较新旧，如 10002） */
    private Integer versionCode;

    /** 版本名称（展示用，如 "1.0.2"） */
    private String versionName;

    /** APK 下载地址 */
    private String apkUrl;

    /** APK 文件大小（字节） */
    private Long fileSize;

    /** APK 文件的 SHA-256 校验值 */
    private String sha256;

    /** 是否强制更新 */
    private Boolean forceUpdate;

    /** 强制更新的最低版本号，低于此值必须升级 */
    private Integer minVersionCode;

    /** 更新日志 */
    private String changelog;

    /** 发布日期（格式 yyyy-MM-dd） */
    private String releaseDate;
}

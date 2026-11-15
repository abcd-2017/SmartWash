package com.smartwash.vo;

import lombok.Data;

import java.util.Map;

/**
 * 应用版本信息 VO，与服务器磁盘上的 version.json 一一对应。
 *
 * <p><b>渠道分组</b>：{@link #channels} 按 channel（full / lite）描述各自的 APK 产物；
 * 顶层 {@code fileName/fileSize/sha256} 为兼容旧清单/旧端保留的字段，
 * 发版时把 full 渠道的产物同步写到顶层即可（见 version.json 说明）。</p>
 */
@Data
public class AppVersionVo {

    /** 版本号（整数，用于比较新旧，如 10002） */
    private Integer versionCode;

    /** 版本名称（展示用，如 "1.0.2"） */
    private String versionName;

    /** APK 下载地址 */
    private String apkUrl;

    /** MinIO 对象名（如 app-1.0.2.apk），用于生成预签名下载 URL；兼容字段，等价于 full 渠道产物 */
    private String fileName;

    /** APK 文件大小（字节）；兼容字段，等价于 full 渠道产物 */
    private Long fileSize;

    /** APK 文件的 SHA-256 校验值；兼容字段，等价于 full 渠道产物 */
    private String sha256;

    /** 是否强制更新 */
    private Boolean forceUpdate;

    /** 强制更新的最低版本号，低于此值必须升级 */
    private Integer minVersionCode;

    /** 更新日志 */
    private String changelog;

    /** 发布日期（格式 yyyy-MM-dd） */
    private String releaseDate;

    /** 按渠道分组的 APK 产物（键为 full / lite），渠道不存在时该键缺失 */
    private Map<String, ChannelVersion> channels;
}

package com.smartwash.toolbox.delivery.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 投递条目视图对象（列表轮询用）。
 * 已焚毁条目保留占位（hasContent=false / taken=true），不回传内容本身。
 */
@Data
@Accessors(chain = true)
public class DeliveryItemVo {

    private Long id;

    /** 1手机→电脑 2电脑→手机 */
    private Integer direction;

    /** 1文本 2图片 3文件 */
    private Integer kind;

    /** 文件名或文本摘要 */
    private String title;

    /** 0未取走 1已取走（焚毁占位） */
    private Integer taken;

    private LocalDateTime takenAt;

    /** 文件字节数（kind=2/3） */
    private Long objectSize;

    private String mimeType;

    /** 完整性校验（kind=2/3，sha256 hex） */
    private String sha256;

    /** 是否仍有内容可读（焚毁后 false；文本已置 NULL / 文件对象已删） */
    private Boolean hasContent;

    private LocalDateTime createdAt;
}

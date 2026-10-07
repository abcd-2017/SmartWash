package com.smartwash.toolbox.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 短码视图对象（用户端）。
 */
@Data
@Accessors(chain = true)
public class ShortCodeVo {

    private Long id;

    private String code;

    /** 1短链 2活码 3交付箱 4图床 5时间胶囊 */
    private Integer contentType;

    private String target;

    /** 1公开 2私有 */
    private Integer isPublic;

    private LocalDateTime unlockAt;

    private LocalDateTime expireAt;

    private Long maxVisits;

    private Long clickCount;

    /** 完整跳转 URL：{toolbox.public-base-url}/web/t/{code}，二维码由客户端生成 */
    private String shortUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

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

    /** 1短链 2活码 6设备投递 7私密文本 8物品标签 */
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

    /** 私密文本状态（仅 contentType=7）：ACTIVE/BURNED/EXPIRED，取值见 SecretState */
    private String secretState;

    /** 焚毁时刻（仅 contentType=7，未焚毁为 null） */
    private LocalDateTime burnedAt;

    /** 私密文本分享链接：{toolbox.web-base-url}/s/{code}（未配置 web-base-url 时为空串，端上降级展示短码） */
    private String secretUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

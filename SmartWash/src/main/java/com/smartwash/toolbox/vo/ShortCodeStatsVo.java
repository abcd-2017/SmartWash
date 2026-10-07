package com.smartwash.toolbox.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 短码访问明细视图对象（IP 仅存摘要，不暴露明文）。
 */
@Data
@Accessors(chain = true)
public class ShortCodeStatsVo {

    private Long id;

    private String code;

    /** IP 摘要前 8 位 */
    private String ipHash;

    private String userAgent;

    private String referer;

    private LocalDateTime createdAt;
}

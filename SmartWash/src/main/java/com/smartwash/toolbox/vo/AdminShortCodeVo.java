package com.smartwash.toolbox.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 短码管理端视图对象：管理员视角不受 is_public 限制，含 owner 手机号（脱敏）与全部生命周期字段。
 */
@Data
@Accessors(chain = true)
public class AdminShortCodeVo {

    private Long id;

    private String code;

    private Integer contentType;

    private String target;

    private Long ownerUserId;

    /** owner 手机号（脱敏，形如 138****0000） */
    private String ownerPhone;

    private Integer isPublic;

    private LocalDateTime unlockAt;

    private LocalDateTime expireAt;

    private Long maxVisits;

    private Long clickCount;

    /** 私密文本状态（仅 contentType=7）：ACTIVE/BURNED/EXPIRED；管理端永不携带内容（含密文） */
    private String secretState;

    /** 焚毁时刻（仅 contentType=7，未焚毁为 null） */
    private LocalDateTime burnedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 详情接口附带：最近访问明细摘要 */
    private List<ShortCodeStatsVo> recentVisits;
}

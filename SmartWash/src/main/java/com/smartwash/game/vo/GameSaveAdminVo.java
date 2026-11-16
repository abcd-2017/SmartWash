package com.smartwash.game.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端用户存档列表项。
 * stateJson 仅作详情查看（管理端不做字段级解析）。
 */
@Data
public class GameSaveAdminVo {

    private Long id;

    private Long userId;

    private String maskedPhone;

    private String gameKey;

    private String gameTitle;

    private Integer schemaVer;

    private String stateJson;

    private LocalDateTime updatedAt;
}

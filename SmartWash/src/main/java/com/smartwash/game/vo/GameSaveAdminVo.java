package com.smartwash.game.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端用户存档列表项。
 * stateJson 仅作详情查看（管理端不做字段级解析）。
 */
@Data
public class GameSaveAdminVo {

    /** 存档 ID */
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 脱敏手机号 */
    private String maskedPhone;

    /** 游戏标识 */
    private String gameKey;

    /** 游戏名 */
    private String gameTitle;

    /** 存档结构版本 */
    private Integer schemaVer;

    /** 存档内容 JSON */
    private String stateJson;

    /** 最近更新时间 */
    private LocalDateTime updatedAt;
}

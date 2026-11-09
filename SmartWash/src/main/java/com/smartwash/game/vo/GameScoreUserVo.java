package com.smartwash.game.vo;

import lombok.Data;

/**
 * 游戏榜内用户信息（脱敏）。
 * 由 GameScoreEntryMapper#selectScoreUsers 批量查出，供 Service 拼装榜单。
 */
@Data
public class GameScoreUserVo {

    /** 用户 ID */
    private Long userId;

    /** 脱敏手机号（138****1234） */
    private String maskedPhone;

    /** 头像 URL（可空） */
    private String avatar;
}

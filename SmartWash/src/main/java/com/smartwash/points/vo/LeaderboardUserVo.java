package com.smartwash.points.vo;

import lombok.Data;

/**
 * 月榜展示用户信息（mapper 查询专用载体，仅含脱敏所需列）。
 */
@Data
public class LeaderboardUserVo {

    private Long userId;

    private String phoneNumber;

    private String avatar;
}

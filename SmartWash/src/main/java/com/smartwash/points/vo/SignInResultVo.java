package com.smartwash.points.vo;

import lombok.Data;

/**
 * 签到结果（POST /web/auth/points/sign-in）。
 * 幂等命中（当日已签）仍返回本 VO：signed=false + alreadySigned=true，code=200，
 * 不重复加分，双端按 alreadySigned 渲染"今日已签到"。
 */
@Data
public class SignInResultVo {

    /** 本次是否真正签上（幂等命中=false） */
    private Boolean signed;

    /** 是否幂等命中（当日已签，含并发冲突方） */
    private Boolean alreadySigned;

    /** 签到时点的连签天数（幂等命中=既有记录的 streak） */
    private Integer streak;

    /** 本次实得积分（幂等命中=0） */
    private Integer pointsAwarded;

    /** 签到后余额（幂等命中=当前余额） */
    private Integer balanceAfter;
}

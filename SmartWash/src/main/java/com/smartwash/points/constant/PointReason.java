package com.smartwash.points.constant;

/**
 * 积分流水业务原因枚举（集中定义，禁止魔法字符串散落）。
 * 枚举名即入库的 reason 字符串（point_ledger.reason），新增场景在此扩展，
 * 双端展示映射跟随本枚举。
 */
public enum PointReason {

    /** 每日签到基础分 */
    SIGN_IN("每日签到"),

    /** 连签奖励（每满配置周期天数额外发放一次） */
    STREAK_BONUS("连签奖励");

    // —— 后置扩展（v1 不做，占位说明防误用）——
    // POINTS_MALL_REDEEM("积分商城兑换")：需积分商城与出账流水配套，避免碰 user_coupon 资金区
    // ADMIN_ADJUST("管理端调分")：需管理端审计入口，后置

    private final String desc;

    PointReason(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 入库值 = 枚举名（append-only 流水的 reason 字符串）
     */
    public String code() {
        return name();
    }
}

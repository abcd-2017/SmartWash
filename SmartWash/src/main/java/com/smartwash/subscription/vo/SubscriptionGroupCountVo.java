package com.smartwash.subscription.vo;

import lombok.Data;

/**
 * 按订阅分组的计数值（group by subscription_id 的通用映射载体）：
 * 使用打卡近 90 天计数 / 未读提醒计数共用。
 */
@Data
public class SubscriptionGroupCountVo {

    private Long subscriptionId;

    /** 计数值 */
    private Long cnt;
}

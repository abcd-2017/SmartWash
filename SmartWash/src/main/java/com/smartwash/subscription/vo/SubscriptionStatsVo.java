package com.smartwash.subscription.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订阅统计视图对象（月度应收 + 各活跃订阅近 90 天使用决策数据）。
 * 口径：月度应收 = Σ 各 active 订阅按周期折算的月度金额（month: amount/interval；year: amount/(interval*12)，HALF_UP 保留2位）；
 * 单次折算成本 = 月度折算金额 × 3 ÷ 近90天使用次数（等价于 月折算金额 ÷ 月均使用次数），无使用记录为 null。
 */
@Data
@Accessors(chain = true)
public class SubscriptionStatsVo {

    /** 月度应收总额（active 订阅折算月度金额之和，2位小数） */
    private BigDecimal monthlyPayable;

    /** 活跃订阅数 */
    private Integer activeCount;

    /** 各活跃订阅明细 */
    private List<Item> items;

    @Data
    @Accessors(chain = true)
    public static class Item {

        private Long subscriptionId;

        private String name;

        /** 计费周期 month/year */
        private String cycleType;

        /** 间隔期数 */
        private Integer cycleInterval;

        /** 每期金额 */
        private BigDecimal amount;

        /** 折算月度金额（2位小数） */
        private BigDecimal monthlyNormalized;

        /** 近 90 天使用次数 */
        private Integer usageCount90d;

        /** 单次折算成本（2位小数，近90天无使用记录为 null） */
        private BigDecimal perUseCost;
    }
}

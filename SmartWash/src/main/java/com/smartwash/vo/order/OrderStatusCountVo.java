package com.smartwash.vo.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单状态聚合计数载体：仅作为 Mapper 聚合查询（GROUP BY status）的结果行映射，
 * 不直接对外返回——对外契约仍为 OrderItemCountVo / Map&lt;String, OrderGroupVo&gt;
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusCountVo {

    /** 订单状态码（见 OrderStatus 枚举） */
    private String status;

    /** 该状态下的订单数量 */
    private Long count;
}

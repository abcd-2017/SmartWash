package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 结束点单响应 VO。
 * 首次结束返回新写入的购买清单快照；重复结束（幂等）返回库内原快照，
 * 端上据此进入购买清单结果页（不重复触发聚合）。
 */
@Data
@Accessors(chain = true)
public class DiningOrderFinishVo {

    /** 订单 ID */
    private Long id;

    /** 订单标题 */
    private String title;

    /** 订单状态（结束成功或幂等命中均为 FINISHED） */
    private String status;

    /** 结束时间（幂等命中为首次结束时间） */
    private LocalDateTime finishedAt;

    /** 购买清单快照 JSON 文本（食材名并集去重 + 按菜视图） */
    private String shoppingList;
}

package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 提交点单响应 VO。
 * 首次提交返回新写入的购买清单快照；重复提交（幂等）返回库内原快照，
 * 端上据此进入购买清单结果页（不重复触发聚合）。
 */
@Data
@Accessors(chain = true)
public class DiningOrderFinishVo {

    private Long id;

    private String title;

    /** 订单状态（提交成功或幂等命中均为 FINISHED） */
    private String status;

    /** 提交时间（幂等命中为首次提交时间） */
    private LocalDateTime finishedAt;

    /** 购买清单快照 JSON 文本（食材名并集去重 + 按菜视图） */
    private String shoppingList;
}

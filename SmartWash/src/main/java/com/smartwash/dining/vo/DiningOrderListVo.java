package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 我的订单列表条目 VO（我发起的 + 我参与的，按创建时间倒序）。
 */
@Data
@Accessors(chain = true)
public class DiningOrderListVo {

    private Long id;

    /** 订单标题 */
    private String title;

    /** 订单状态：ONGOING / FINISHED */
    private String status;

    /** 是否我发起的 */
    private Boolean isCreator;

    /** 发起者用户 ID */
    private Long creatorUserId;

    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;
}

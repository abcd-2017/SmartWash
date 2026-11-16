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

    private String title;

    /** 订单状态：ONGOING / FINISHED */
    private String status;

    private Boolean isCreator;

    private Long creatorUserId;

    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;
}

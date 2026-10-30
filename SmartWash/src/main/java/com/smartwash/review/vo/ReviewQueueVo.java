package com.smartwash.review.vo;

import lombok.Data;

import java.util.List;

/**
 * 今日复习队列视图：到期卡在前（state 升序、due 升序），新卡注入队尾。
 */
@Data
public class ReviewQueueVo {

    /** 到期卡张数 */
    private int dueCount;

    /** 注入的新卡张数 */
    private int newCount;

    /** 合并后的队列（到期卡 + 新卡） */
    private List<ReviewCardVo> cards;
}

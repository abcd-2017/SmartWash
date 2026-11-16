package com.smartwash.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_reviews")
public class OrderReviews {

    @TableId(value = "review_id", type = IdType.AUTO)
    private Long reviewId;

    private Long orderId;

    private Long userId;

    /** 评分 1-5 */
    private Integer rating;

    private String content;

    private LocalDateTime createdAt;
}

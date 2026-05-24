package com.smartwash.vo.review;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewVo {

    private Long reviewId;

    private Long orderId;

    private Long userId;

    private String userPhone; // 脱敏手机号

    private Integer rating;

    private String content;

    private LocalDateTime createdAt;
}

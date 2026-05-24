package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.OrderReviews;
import com.smartwash.from.review.AddReviewFrom;
import com.smartwash.vo.review.ReviewVo;

public interface IOrderReviewsService extends IService<OrderReviews> {

    Boolean addReview(AddReviewFrom reviewFrom, Long userId);

    Page<ReviewVo> getReviewsByOrderId(Long orderId, int page, int size);
}

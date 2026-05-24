package com.smartwash.service.impl;

import cn.hutool.core.util.DesensitizedUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.entity.OrderReviews;
import com.smartwash.entity.Orders;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.from.review.AddReviewFrom;
import com.smartwash.mapper.OrderReviewsMapper;
import com.smartwash.mapper.OrdersMapper;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.service.IOrderReviewsService;
import com.smartwash.vo.review.ReviewVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReviewsServiceImpl extends ServiceImpl<OrderReviewsMapper, OrderReviews> implements IOrderReviewsService {

    private final OrdersMapper ordersMapper;
    private final UsersMapper usersMapper;

    @Override
    public Boolean addReview(AddReviewFrom reviewFrom, Long userId) {
        Orders order = ordersMapper.selectById(reviewFrom.getOrderId());
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new CustomExceptions("订单不存在");
        }

        // 检查是否已评价
        LambdaQueryWrapper<OrderReviews> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderReviews::getOrderId, reviewFrom.getOrderId());
        if (count(wrapper) > 0) {
            throw new CustomExceptions("该订单已评价");
        }

        OrderReviews review = new OrderReviews();
        review.setOrderId(reviewFrom.getOrderId());
        review.setUserId(userId);
        review.setRating(reviewFrom.getRating());
        review.setContent(reviewFrom.getContent());
        review.setCreatedAt(LocalDateTime.now());

        log.info("用户评价订单, orderId: {}, userId: {}, rating: {}", reviewFrom.getOrderId(), userId, reviewFrom.getRating());
        return save(review);
    }

    @Override
    public Page<ReviewVo> getReviewsByOrderId(Long orderId, int page, int size) {
        Page<OrderReviews> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<OrderReviews> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderReviews::getOrderId, orderId)
                .orderByDesc(OrderReviews::getCreatedAt);

        Page<OrderReviews> reviewPage = page(pageParam, wrapper);
        Page<ReviewVo> voPage = new Page<>();
        voPage.setRecords(reviewPage.getRecords().stream().map(r -> {
            ReviewVo vo = new ReviewVo();
            vo.setReviewId(r.getReviewId());
            vo.setOrderId(r.getOrderId());
            vo.setUserId(r.getUserId());
            vo.setRating(r.getRating());
            vo.setContent(r.getContent());
            vo.setCreatedAt(r.getCreatedAt());
            var user = usersMapper.selectById(r.getUserId());
            if (user != null) {
                vo.setUserPhone(DesensitizedUtil.mobilePhone(user.getPhoneNumber()));
            }
            return vo;
        }).toList());
        voPage.setTotal(reviewPage.getTotal());
        return voPage;
    }
}

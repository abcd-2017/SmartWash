package com.smartwash.controller.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.from.review.AddReviewFrom;
import com.smartwash.service.IOrderReviewsService;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import com.smartwash.vo.review.ReviewVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户端-评价", description = "用户端订单评价接口")
@Slf4j
@RestController
@RequestMapping("/web")
@RequiredArgsConstructor
public class WebReviewController {

    private final IOrderReviewsService orderReviewsService;

    @Operation(summary = "提交评价", description = "对已完成的订单提交评价")
    @PostMapping("/auth/reviews/add")
    public Result<Boolean> addReview(@RequestBody @Valid AddReviewFrom reviewFrom) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(orderReviewsService.addReview(reviewFrom, user.getUserId()));
    }

    @Operation(summary = "获取订单评价", description = "获取指定订单的评价列表")
    @GetMapping("/auth/reviews/{orderId}")
    public Result<Page<ReviewVo>> getReviews(
            @PathVariable("orderId") @Parameter(description = "订单ID") Long orderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(orderReviewsService.getReviewsByOrderId(orderId, page, size));
    }
}

package com.smartwash.subscription.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartwash.common.Result;
import com.smartwash.subscription.from.AdminSearchSubscriptionFrom;
import com.smartwash.subscription.service.UserSubscriptionService;
import com.smartwash.subscription.vo.AdminSubscriptionVo;
import com.smartwash.subscription.vo.SubscriptionSummaryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订阅逃生舱管理端控制器。
 * 路径前缀 /admin/subscription，需 ROLE_ADMIN（SecurityConfig 的 /admin/** 通配统一拦截）。
 * 全部为只读查询，无写操作；下发手机号一律脱敏，不下发用户私有文本（取消网址/步骤/备注）。
 * 四端联动说明：本组为新增接口，Web 管理后台为消费方，Android/鸿蒙不消费。
 */
@Tag(name = "订阅逃生舱-管理端", description = "全平台订阅记录汇总与分页查询（ROLE_ADMIN，只读）")
@Slf4j
@RestController
@RequestMapping("/admin/subscription")
@RequiredArgsConstructor
public class SubscriptionAdminController {

    private final UserSubscriptionService userSubscriptionService;

    @Operation(summary = "订阅汇总",
            description = "activeCount 生效中；expiredCount 已过期（active 但下次续费日已过，逾期未推进）；" +
                    "expiringIn7Days 7日内到期（active 且下次续费日 ∈ [今日, 今日+7]）")
    @GetMapping("/summary")
    public Result<SubscriptionSummaryVo> summary() {
        return Result.ok(userSubscriptionService.adminSummary());
    }

    @Operation(summary = "订阅记录分页",
            description = "status 可选（active/paused/canceled/finished）；dateFrom/dateTo（yyyy-MM-dd，可空）" +
                    "按下次续费日（到期时间）筛选；按到期日升序；userPhone 已脱敏")
    @GetMapping("/list")
    public Result<IPage<AdminSubscriptionVo>> list(@Valid AdminSearchSubscriptionFrom from) {
        return Result.ok(userSubscriptionService.adminPage(from));
    }
}

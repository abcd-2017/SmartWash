package com.smartwash.subscription.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartwash.common.Result;
import com.smartwash.from.BaseSearchFrom;
import com.smartwash.subscription.from.AddSubscriptionFrom;
import com.smartwash.subscription.from.AddUsageFrom;
import com.smartwash.subscription.from.SearchSubscriptionFrom;
import com.smartwash.subscription.from.UpdateSubscriptionFrom;
import com.smartwash.subscription.service.SubscriptionReminderLogService;
import com.smartwash.subscription.service.UserSubscriptionService;
import com.smartwash.subscription.service.UserSubscriptionUsageLogService;
import com.smartwash.subscription.vo.SubscriptionReminderVo;
import com.smartwash.subscription.vo.SubscriptionStatsVo;
import com.smartwash.subscription.vo.SubscriptionUsageVo;
import com.smartwash.subscription.vo.UserSubscriptionVo;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订阅逃生舱用户端控制器。
 * 路径前缀 /web/auth/subscription（SecurityConfig 的 /web/auth/** 规则天然要求 ROLE_USER），
 * 全部接口以当前登录用户为主体，归属不符按「不存在」处理。
 */
@Tag(name = "订阅逃生舱", description = "订阅档案 CRUD、状态流转、使用打卡、提醒与统计接口")
@Slf4j
@RestController
@RequestMapping("/web/auth/subscription")
@RequiredArgsConstructor
public class UserSubscriptionController {

    private final UserSubscriptionService userSubscriptionService;
    private final UserSubscriptionUsageLogService userSubscriptionUsageLogService;
    private final SubscriptionReminderLogService subscriptionReminderLogService;

    // ==================== CRUD + 状态机 ====================

    @Operation(summary = "创建订阅", description = "初始状态 active；金额/周期(month|year)/下次续费日必填")
    @PostMapping
    public Result<UserSubscriptionVo> create(@RequestBody @Valid AddSubscriptionFrom from) {
        return Result.ok(userSubscriptionService.createSubscription(from, currentUserId()));
    }

    @Operation(summary = "编辑订阅", description = "全字段可选，null=不改；canceled/finished 终态拒绝编辑")
    @PutMapping("/{id}")
    public Result<UserSubscriptionVo> update(@PathVariable("id") Long id, @RequestBody @Valid UpdateSubscriptionFrom from) {
        return Result.ok(userSubscriptionService.updateSubscription(id, from, currentUserId()));
    }

    @Operation(summary = "删除订阅", description = "物理删除并级联清理打卡流水与提醒记录")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        userSubscriptionService.deleteSubscription(id, currentUserId());
        return Result.ok(null);
    }

    @Operation(summary = "我的订阅分页", description = "按下次续费日升序（最临近续费在前），含未读提醒数与距续费天数")
    @GetMapping
    public Result<IPage<UserSubscriptionVo>> page(SearchSubscriptionFrom from) {
        return Result.ok(userSubscriptionService.pageMine(from, currentUserId()));
    }

    @Operation(summary = "订阅详情", description = "含未读提醒数、距续费/试用截止天数")
    @GetMapping("/{id}")
    public Result<UserSubscriptionVo> detail(@PathVariable("id") Long id) {
        return Result.ok(userSubscriptionService.getDetail(id, currentUserId()));
    }

    @Operation(summary = "暂停订阅", description = "状态机 active → paused，非法流转/并发冲突拒绝")
    @PostMapping("/{id}/pause")
    public Result<Void> pause(@PathVariable("id") Long id) {
        userSubscriptionService.pause(id, currentUserId());
        return Result.ok(null);
    }

    @Operation(summary = "恢复订阅", description = "状态机 paused → active，非法流转/并发冲突拒绝")
    @PostMapping("/{id}/resume")
    public Result<Void> resume(@PathVariable("id") Long id) {
        userSubscriptionService.resume(id, currentUserId());
        return Result.ok(null);
    }

    @Operation(summary = "取消订阅", description = "状态机 active/paused → canceled 终态（保留数据，取消网址/步骤仍可查）")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable("id") Long id) {
        userSubscriptionService.cancel(id, currentUserId());
        return Result.ok(null);
    }

    @Operation(summary = "结束订阅", description = "状态机 active/paused → finished 终态（保留数据，语义为试用结束不再续用）")
    @PostMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable("id") Long id) {
        userSubscriptionService.finish(id, currentUserId());
        return Result.ok(null);
    }

    // ==================== 使用打卡 ====================

    @Operation(summary = "使用打卡", description = "不传日期默认今天，不允许未来日期；每订阅每天一条，同日重复拒绝（可撤销后重打）")
    @PostMapping("/{id}/usages")
    public Result<SubscriptionUsageVo> checkIn(@PathVariable("id") Long id, @RequestBody(required = false) @Valid AddUsageFrom from) {
        return Result.ok(userSubscriptionUsageLogService.checkIn(id, currentUserId(),
                from == null ? new AddUsageFrom() : from));
    }

    @Operation(summary = "打卡记录分页", description = "指定订阅的打卡流水，按使用日期倒序")
    @GetMapping("/{id}/usages")
    public Result<IPage<SubscriptionUsageVo>> usages(@PathVariable("id") Long id, @Valid BaseSearchFrom from) {
        return Result.ok(userSubscriptionUsageLogService.pageBySubscription(id, currentUserId(), from.getPage(), from.getSize()));
    }

    @Operation(summary = "撤销打卡", description = "物理删除指定打卡记录，须归属当前订阅")
    @DeleteMapping("/{id}/usages/{usageId}")
    public Result<Void> deleteUsage(@PathVariable("id") Long id, @PathVariable("usageId") Long usageId) {
        userSubscriptionUsageLogService.deleteUsage(id, usageId, currentUserId());
        return Result.ok(null);
    }

    // ==================== 提醒 ====================

    @Operation(summary = "我的提醒列表", description = "unread=true 只看未读；按应提醒日倒序，上限200条；含订阅名称")
    @GetMapping("/reminders")
    public Result<List<SubscriptionReminderVo>> reminders(@RequestParam(value = "unread", required = false) Boolean unread) {
        return Result.ok(subscriptionReminderLogService.listMine(currentUserId(), unread));
    }

    @Operation(summary = "提醒标记已读", description = "幂等：重复标记静默成功")
    @PostMapping("/reminders/{id}/read")
    public Result<Void> readReminder(@PathVariable("id") Long id) {
        subscriptionReminderLogService.markRead(id, currentUserId());
        return Result.ok(null);
    }

    // ==================== 统计 ====================

    @Operation(summary = "订阅统计", description = "月度应收总额（按周期折算）+ 各活跃订阅近90天使用次数与单次折算成本")
    @GetMapping("/stats")
    public Result<SubscriptionStatsVo> stats() {
        return Result.ok(userSubscriptionService.getMyStats(currentUserId()));
    }

    private Long currentUserId() {
        LoginUser user = UserContextHolder.getUser();
        return user.getUserId();
    }
}

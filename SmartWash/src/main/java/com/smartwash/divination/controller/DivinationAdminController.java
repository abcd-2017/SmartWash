package com.smartwash.divination.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.divination.entity.DivInterpretation;
import com.smartwash.divination.entity.DivUsageDaily;
import com.smartwash.divination.mapper.DivInterpretationMapper;
import com.smartwash.divination.mapper.DivUsageDailyMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 观象台管理端控制器。
 * 路径前缀 /admin/div/**，需 ROLE_ADMIN。
 * 提供用量看板、审计失败复审等管理端接口。
 */
@Tag(name = "观象台-管理端", description = "占卜模块管理端接口（用量/审计/拦截日志）")
@Slf4j
@RestController
@RequestMapping("/admin/div")
@RequiredArgsConstructor
public class DivinationAdminController {

    private final DivUsageDailyMapper usageDailyMapper;
    private final DivInterpretationMapper interpretationMapper;

    @Operation(summary = "用量看板", description = "按日期范围查询每日用量统计")
    @GetMapping("/usage")
    public Result<List<DivUsageDaily>> getUsage(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        LambdaQueryWrapper<DivUsageDaily> wrapper = new LambdaQueryWrapper<>();
        if (from != null) wrapper.ge(DivUsageDaily::getStatDate, LocalDate.parse(from));
        if (to != null) wrapper.le(DivUsageDaily::getStatDate, LocalDate.parse(to));
        wrapper.orderByDesc(DivUsageDaily::getStatDate);
        return Result.ok(usageDailyMapper.selectList(wrapper));
    }

    @Operation(summary = "审计失败复审", description = "查询审计不一致的解读记录")
    @GetMapping("/audits")
    public Result<Page<DivInterpretation>> getAuditFailures(
            @RequestParam(value = "status", defaultValue = "2") Integer status,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        Page<DivInterpretation> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<DivInterpretation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivInterpretation::getAuditStatus, status);
        wrapper.orderByDesc(DivInterpretation::getCreatedAt);
        return Result.ok(interpretationMapper.selectPage(pageObj, wrapper));
    }
}

package com.smartwash.mood.controller;

import com.smartwash.common.Result;
import com.smartwash.mood.from.AdminMoodSummaryFrom;
import com.smartwash.mood.service.MoodRecordService;
import com.smartwash.mood.vo.MoodSummaryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 情绪气象站管理端控制器。
 * 路径前缀 /admin/mood，需 ROLE_ADMIN（SecurityConfig 的 /admin/** 通配统一拦截）。
 * 全部为只读聚合查询，无写操作；口径与用户端 stats 一致（情绪维度 = mood_level 1..5）。
 * 四端联动说明：本组为新增接口，Web 管理后台为消费方，Android/鸿蒙不消费。
 */
@Tag(name = "情绪气象站-管理端", description = "全平台情绪记录聚合报表（ROLE_ADMIN，只读）")
@Slf4j
@RestController
@RequestMapping("/admin/mood")
@RequiredArgsConstructor
public class MoodAdminController {

    private final MoodRecordService moodRecordService;

    @Operation(summary = "情绪记录聚合报表",
            description = "dateFrom/dateTo 可空（默认近30天含今日），yyyy-MM-dd；" +
                    "recordCount 区间记录总数，userCount 去重用户数；" +
                    "byEmotion 按情绪等级聚合（1暴雨 2阴 3多云 4晴 5大晴，仅含实际出现的等级）；" +
                    "byDate 按日计数（逐日连续补零，无记录日为 0）")
    @GetMapping("/summary")
    public Result<MoodSummaryVo> summary(@Valid AdminMoodSummaryFrom from) {
        return Result.ok(moodRecordService.adminSummary(from.getDateFrom(), from.getDateTo()));
    }
}

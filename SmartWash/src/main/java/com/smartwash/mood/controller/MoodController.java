package com.smartwash.mood.controller;

import com.smartwash.common.Result;
import com.smartwash.mood.from.SaveMoodFrom;
import com.smartwash.mood.service.MoodRecordService;
import com.smartwash.mood.vo.MoodCalendarDayVo;
import com.smartwash.mood.vo.MoodStatsVo;
import com.smartwash.mood.vo.MoodRecordVo;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 情绪气象站用户端控制器。
 * 路径前缀 /web/auth/mood，需 ROLE_USER；所有读写均以当前登录用户为主体。
 */
@Tag(name = "情绪气象站", description = "每日情绪记录/月历热图/月度趋势接口")
@Slf4j
@RestController
@RequestMapping("/web/auth/mood")
@RequiredArgsConstructor
public class MoodController {

    private final MoodRecordService moodRecordService;

    @Operation(summary = "记录/覆盖今日情绪", description = "level 必填且须为1..5，color 必填；同日重复提交即覆盖（upsert）")
    @PutMapping("/today")
    public Result<MoodRecordVo> saveToday(@RequestBody @Valid SaveMoodFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(moodRecordService.saveToday(from, user.getUserId()));
    }

    @Operation(summary = "月历热图数据", description = "ym 格式 yyyy-MM；仅返回当月有记录的日期（无记录日期端上缺省灰格），跨月/空月返回空数组")
    @GetMapping("/calendar")
    public Result<List<MoodCalendarDayVo>> calendar(@RequestParam("ym") String ym) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(moodRecordService.monthCalendar(ym, user.getUserId()));
    }

    @Operation(summary = "月度趋势统计", description = "ym 格式 yyyy-MM；返回各等级计数分布、记录天数与月均值（等级均值保留2位，无记录时为 null）")
    @GetMapping("/stats")
    public Result<MoodStatsVo> stats(@RequestParam("ym") String ym) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(moodRecordService.monthStats(ym, user.getUserId()));
    }
}

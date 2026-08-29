package com.smartwash.divination.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.divination.from.CreateRecordFrom;
import com.smartwash.divination.from.SearchRecordFrom;
import com.smartwash.divination.service.IDivInterpretationService;
import com.smartwash.divination.service.IDivRecordService;
import com.smartwash.divination.vo.RecordDetailVo;
import com.smartwash.divination.vo.RecordVo;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 观象台用户端控制器。
 * 路径前缀 /web/auth/div/**，需 ROLE_USER。
 * 接口契约必须与 Android 端 DivinationApi.kt 匹配。
 */
@Tag(name = "观象台-用户端", description = "占卜卦例管理与解读接口")
@Slf4j
@RestController
@RequestMapping("/web/auth/div")
@RequiredArgsConstructor
public class DivinationWebController {

    private final IDivRecordService recordService;
    private final IDivInterpretationService interpretationService;

    @Operation(summary = "创建卦例", description = "服务端 core 重算权威盘面并与客户端盘面比对校验")
    @PostMapping("/records")
    public Result<Long> createRecord(@RequestBody @Valid CreateRecordFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(recordService.createRecord(from, user.getUserId()));
    }

    @Operation(summary = "卦历分页", description = "分页查询当前用户的卦例历史")
    @GetMapping("/records")
    public Result<Page<RecordVo>> searchRecords(SearchRecordFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(recordService.searchRecords(from, user.getUserId()));
    }

    @Operation(summary = "卦例详情", description = "查询单个卦例详情（含最近一次解读）")
    @GetMapping("/records/{id}")
    public Result<RecordDetailVo> getRecordDetail(@PathVariable("id") Long id) {
        LoginUser user = UserContextHolder.getUser();
        RecordDetailVo detail = recordService.getRecordDetail(id, user.getUserId());
        if (detail == null) {
            return Result.failMsg("卦例不存在");
        }
        return Result.ok(detail);
    }

    @Operation(summary = "今日一签", description = "服务端按当日时间卦生成/复用卦例")
    @GetMapping("/today")
    public Result<RecordDetailVo> getTodayRecord() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(recordService.getTodayRecord(user.getUserId()));
    }

    @Operation(summary = "SSE 流式解读", description = "对卦例进行 LLM 流式解读（event: delta/done/error）")
    @PostMapping("/records/{id}/interpret")
    public SseEmitter interpret(@PathVariable("id") Long id, @RequestBody(required = false) String question) {
        LoginUser user = UserContextHolder.getUser();
        return interpretationService.interpret(id, question, user.getUserId(), false);
    }

    @Operation(summary = "同卦追问", description = "对同一卦例进行追问（SSE 流式）")
    @PostMapping("/records/{id}/followup")
    public SseEmitter followup(@PathVariable("id") Long id, @RequestBody String question) {
        LoginUser user = UserContextHolder.getUser();
        return interpretationService.interpret(id, question, user.getUserId(), true);
    }
}

package com.smartwash.toolbox.controller;

import com.smartwash.common.Result;
import com.smartwash.common.audit.Audited;
import com.smartwash.toolbox.service.ToolboxLockerTagService;
import com.smartwash.toolbox.vo.LockerTagBatchItemVo;
import com.smartwash.toolbox.vo.ShortCodeVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 储物柜资产码管理端控制器（一物一码 M3）。
 * 路径前缀 /admin/toolbox/locker-tags，需 ROLE_ADMIN。
 * 生成即写短码主表（活码），幂等键为别名 L{lockerId}；管理端写操作走 @Audited 审计链。
 */
@Tag(name = "工作台-柜码管理端", description = "储物柜资产码生成（单柜/按校批量，ROLE_ADMIN）")
@Slf4j
@RestController
@RequestMapping("/admin/toolbox/locker-tags")
@RequiredArgsConstructor
public class ToolboxLockerTagAdminController {

    private final ToolboxLockerTagService toolboxLockerTagService;

    @Operation(summary = "为单个柜子生成柜码",
            description = "活码别名 L{lockerId} 幂等：已存在直接复用；target 指向 Web 公开落地页，需先配置 toolbox.web-base-url")
    @Audited(action = "LOCKER_TAG_GENERATE", targetType = "locker", targetIdSpEL = "#lockerId")
    @PostMapping("/locker/{lockerId}")
    public Result<ShortCodeVo> generateForLocker(@PathVariable("lockerId") Long lockerId) {
        return Result.ok(toolboxLockerTagService.generateForLocker(lockerId));
    }

    @Operation(summary = "按校批量生成柜码",
            description = "逐柜幂等生成并返回全量结果（含既有复用项），供打印排版一次出 A4 标签")
    @Audited(action = "LOCKER_TAG_BATCH_GENERATE", targetType = "school", targetIdSpEL = "#schoolId")
    @PostMapping("/school/{schoolId}")
    public Result<List<LockerTagBatchItemVo>> generateForSchool(@PathVariable("schoolId") Long schoolId) {
        return Result.ok(toolboxLockerTagService.generateForSchool(schoolId));
    }
}

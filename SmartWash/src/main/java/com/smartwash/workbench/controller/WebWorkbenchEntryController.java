package com.smartwash.workbench.controller;

import com.smartwash.common.Result;
import com.smartwash.workbench.service.WorkbenchEntryService;
import com.smartwash.workbench.vo.WorkbenchEntryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作台入口用户端控制器。
 * 路径前缀 /web/auth/workbench，需 ROLE_USER（SecurityConfig 统一拦截）。
 * 四端联动说明：本接口为新增接口，不改动任何既有接口；Android 端后续接入，
 * 鸿蒙端本次不接入（端上保留兜底静态列表，按 entryKey 与下发数据对齐）。
 */
@Tag(name = "工作台-入口配置", description = "移动端工作台入口动态下发")
@Slf4j
@RestController
@RequestMapping("/web/auth/workbench")
@RequiredArgsConstructor
public class WebWorkbenchEntryController {

    private final WorkbenchEntryService workbenchEntryService;

    @Operation(summary = "工作台入口列表", description = "仅启用中的入口，按 sort 升序、id 升序，不分页；icon/color 为端上白名单枚举 key，未知值显示默认样式")
    @GetMapping("/entries")
    public Result<List<WorkbenchEntryVo>> entries() {
        return Result.ok(workbenchEntryService.listEnabledEntries());
    }
}

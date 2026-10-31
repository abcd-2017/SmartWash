package com.smartwash.workbench.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.workbench.from.AddWorkbenchEntryFrom;
import com.smartwash.workbench.from.SearchWorkbenchEntryFrom;
import com.smartwash.workbench.from.UpdateWorkbenchEntryFrom;
import com.smartwash.workbench.service.WorkbenchEntryService;
import com.smartwash.workbench.vo.AdminWorkbenchEntryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 工作台入口配置管理端控制器。
 * 路径前缀 /admin/workbench/entries，需 ROLE_ADMIN（SecurityConfig 统一拦截）。
 * 写操作统一失效用户端下发缓存（workbenchEntries），保证端上拉到的列表即时更新。
 * Web 管理页属前端消费方，本次仅交付后端接口。
 */
@Tag(name = "工作台-入口配置管理端", description = "工作台入口卡片配置（ROLE_ADMIN）")
@Slf4j
@RestController
@RequestMapping("/admin/workbench/entries")
@RequiredArgsConstructor
public class WorkbenchEntryAdminController {

    private final WorkbenchEntryService workbenchEntryService;

    @Operation(summary = "分页查询入口列表", description = "page/size + title 模糊 + enabled 筛选，按 sort/id 升序（与端上渲染顺序一致）")
    @GetMapping("/all")
    public Result<Page<AdminWorkbenchEntryVo>> getAll(SearchWorkbenchEntryFrom from) {
        return Result.ok(workbenchEntryService.adminPage(from));
    }

    @Operation(summary = "新增入口", description = "entry_key 全局唯一（重复拒绝且创建后不可修改）；icon/color 走端上白名单校验")
    @PostMapping("/add")
    public Result<String> add(@RequestBody @Valid AddWorkbenchEntryFrom from) {
        workbenchEntryService.addEntry(from);
        return Result.ok("添加成功");
    }

    @Operation(summary = "更新入口", description = "entry_key 不可修改；badge/description/remark 传空字符串表示清空")
    @PostMapping("/update")
    public Result<String> update(@RequestBody @Valid UpdateWorkbenchEntryFrom from) {
        workbenchEntryService.updateEntry(from);
        return Result.ok("修改成功");
    }

    @Operation(summary = "批量删除入口", description = "根据ID批量物理删除，多个ID用逗号分隔")
    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> delete(@PathVariable("ids") @Parameter(description = "入口ID列表，多个ID用逗号分隔", required = true, example = "1,2,3") String ids) {
        workbenchEntryService.deleteEntries(ids);
        return Result.ok(true);
    }
}

package com.smartwash.toolbox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartwash.common.Result;
import com.smartwash.toolbox.from.SearchShortCodeFrom;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.vo.AdminShortCodeVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 工作台工具箱管理端控制器。
 * 路径前缀 /admin/toolbox/short-codes，需 ROLE_ADMIN（SecurityConfig 统一拦截）。
 * 管理员视角不受 is_public 限制（治理场景需看到全部，含私有码）。
 * Web 管理页属前端消费方，随「前端暂缓」延后，本控制器为后端先行交付物。
 */
@Tag(name = "工作台-短链管理端", description = "短码全局查询与治理接口（ROLE_ADMIN）")
@Slf4j
@RestController
@RequestMapping("/admin/toolbox/short-codes")
@RequiredArgsConstructor
public class ToolboxAdminController {

    private final ToolboxShortCodeService toolboxShortCodeService;

    @Operation(summary = "短码全局分页", description = "page/size + code/ownerPhone/contentType 筛选，返回含脱敏 owner 手机号")
    @GetMapping
    public Result<IPage<AdminShortCodeVo>> page(SearchShortCodeFrom from) {
        return Result.ok(toolboxShortCodeService.adminPage(from));
    }

    @Operation(summary = "短码详情", description = "含最近访问明细摘要")
    @GetMapping("/{id}")
    public Result<AdminShortCodeVo> detail(@PathVariable("id") Long id) {
        return Result.ok(toolboxShortCodeService.adminDetail(id));
    }

    @Operation(summary = "删除短码", description = "治理用途：物理删除并清对应缓存")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        toolboxShortCodeService.adminDelete(id);
        return Result.ok(null);
    }
}

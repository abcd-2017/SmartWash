package com.smartwash.toolbox.assettag.controller;

import com.smartwash.common.Result;
import com.smartwash.common.audit.Audited;
import com.smartwash.toolbox.assettag.service.ToolboxAssetTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 物品标签管理端控制器（一物一码 M2）。
 * 路径前缀 /admin/toolbox/asset-tags，需 ROLE_ADMIN；治理删除走 @Audited 审计链。
 * 管理端不提供物品信息/联系方式读取通道（隐私红线）——仅保留治理删除。
 */
@Tag(name = "工作台-物品标签管理端", description = "物品标签治理删除（ROLE_ADMIN；无内容读取通道）")
@Slf4j
@RestController
@RequestMapping("/admin/toolbox/asset-tags")
@RequiredArgsConstructor
public class ToolboxAssetTagAdminController {

    private final ToolboxAssetTagService toolboxAssetTagService;

    @Operation(summary = "治理删除标签", description = "明细 + 访问明细 + 主表级联删除；物品信息对管理端不可见")
    @Audited(action = "TOOLBOX_ASSET_TAG_DELETE", targetType = "toolbox_asset_tag", targetIdSpEL = "#id")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        toolboxAssetTagService.adminDelete(id);
        return Result.ok(null);
    }
}

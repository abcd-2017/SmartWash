package com.smartwash.toolbox.assettag.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartwash.common.Result;
import com.smartwash.common.ratelimit.RateLimit;
import com.smartwash.toolbox.assettag.from.CreateAssetTagFrom;
import com.smartwash.toolbox.assettag.from.UpdateAssetTagFrom;
import com.smartwash.toolbox.assettag.service.ToolboxAssetTagService;
import com.smartwash.toolbox.assettag.vo.AssetTagVo;
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

/**
 * 物品标签（一物一码 M2）用户端控制器。
 * 路径前缀 /web/auth/toolbox/asset-tags，需 ROLE_USER。
 * 明细 contact 永不回显（连掩码不给）；编辑时留空联系方式 = 不修改。
 */
@Tag(name = "工作台-物品标签", description = "一物一码：标签创建/管理/寻物状态流转（ROLE_USER）")
@Slf4j
@RestController
@RequestMapping("/web/auth/toolbox/asset-tags")
@RequiredArgsConstructor
public class ToolboxAssetTagController {

    private final ToolboxAssetTagService toolboxAssetTagService;

    @Operation(summary = "创建标签",
            description = "主表 type=8 短码 + 明细同事务；联系方式可选（加密落库）；标签默认永久")
    @PostMapping
    @RateLimit(name = "asset-tag-create", window = 60, limit = 20, message = "创建过于频繁，请稍后再试")
    public Result<AssetTagVo> create(@RequestBody @Valid CreateAssetTagFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(toolboxAssetTagService.createAssetTag(from, user.getUserId()));
    }

    @Operation(summary = "我的标签分页", description = "按创建时间倒序，含扫码次数与最近扫码时间（失主线索）")
    @GetMapping
    public Result<IPage<AssetTagVo>> page(@RequestParam("page") long page,
                                          @RequestParam("size") long size) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(toolboxAssetTagService.pageMyAssetTags(page, size, user.getUserId()));
    }

    @Operation(summary = "更新标签",
            description = "信息/联系方式/寻物状态；状态裁定在后端；空串 contact = 清空联系方式")
    @PutMapping("/{id}")
    public Result<AssetTagVo> update(@PathVariable("id") Long id,
                                     @RequestBody @Valid UpdateAssetTagFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(toolboxAssetTagService.updateAssetTag(id, from, user.getUserId()));
    }

    @Operation(summary = "删除标签", description = "明细 + 访问明细 + 主表同事务级联删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        LoginUser user = UserContextHolder.getUser();
        toolboxAssetTagService.deleteAssetTag(id, user.getUserId());
        return Result.ok(null);
    }
}

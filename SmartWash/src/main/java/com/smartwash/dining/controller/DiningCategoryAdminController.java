package com.smartwash.dining.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.common.audit.Audited;
import com.smartwash.dining.from.AddDiningCategoryFrom;
import com.smartwash.dining.from.SearchDiningCategoryFrom;
import com.smartwash.dining.from.UpdateDiningCategoryFrom;
import com.smartwash.dining.service.DiningCategoryService;
import com.smartwash.dining.vo.DiningCategoryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 家庭点单-菜谱分类管理端控制器。
 * 路径前缀 /admin/dining/categories，需 ROLE_ADMIN（SecurityConfig /admin/** 通配统一拦截，无需改权限配置）。
 * 写操作挂 @Audited（HMAC 审计链）——管理端写接口横切红线；分类为低频运营操作，无需 @RateLimit。
 */
@Tag(name = "家庭点单-分类管理端", description = "家庭点单菜谱库分类目录管理（ROLE_ADMIN）")
@Slf4j
@Validated
@RestController
@RequestMapping("/admin/dining/categories")
@RequiredArgsConstructor
public class DiningCategoryAdminController {

    private final DiningCategoryService diningCategoryService;

    @Operation(summary = "分页查询分类列表", description = "page/size + name 模糊筛选，按 sort/id 升序（与用户端展示顺序一致）")
    @GetMapping("/all")
    public Result<Page<DiningCategoryVo>> getAll(SearchDiningCategoryFrom from) {
        return Result.ok(diningCategoryService.adminPage(from));
    }

    @Operation(summary = "新增分类", description = "分类名全局唯一（重复拒绝，uk_name 唯一索引兜底并发）")
    @Audited(action = "DINING_CATEGORY_ADD", targetType = "dining_category")
    @PostMapping("/add")
    public Result<String> add(@RequestBody @Valid AddDiningCategoryFrom from) {
        diningCategoryService.addCategory(from);
        return Result.ok("添加成功");
    }

    @Operation(summary = "更新分类", description = "id 必传；name/sort 不传即不修改，改名与其他分类重名时拒绝")
    @Audited(action = "DINING_CATEGORY_UPDATE", targetType = "dining_category", targetIdSpEL = "#from.id")
    @PostMapping("/update")
    public Result<String> update(@RequestBody @Valid UpdateDiningCategoryFrom from) {
        diningCategoryService.updateCategory(from);
        return Result.ok("修改成功");
    }

    @Operation(summary = "批量删除分类", description = "根据 ID 批量物理删除，多个 ID 用逗号分隔；任一分类被菜谱引用时整体拒绝删除并提示引用数量")
    @Audited(action = "DINING_CATEGORY_DELETE", targetType = "dining_category", targetIdSpEL = "#ids")
    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> delete(@PathVariable("ids") @Pattern(regexp = "^\\d+(,\\d+)*$", message = "ids 必须为逗号分隔的数字") @Parameter(description = "分类ID列表，多个ID用逗号分隔", required = true, example = "1,2,3") String ids) {
        diningCategoryService.deleteCategories(ids);
        return Result.ok(true);
    }
}

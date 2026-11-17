package com.smartwash.dining.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.common.audit.Audited;
import com.smartwash.dining.from.AddDiningIngredientFrom;
import com.smartwash.dining.from.SearchDiningIngredientFrom;
import com.smartwash.dining.from.UpdateDiningIngredientFrom;
import com.smartwash.dining.service.DiningIngredientService;
import com.smartwash.dining.vo.DiningIngredientVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 吃啥-配料库管理端控制器。
 * 路径前缀 /admin/dining/ingredient（契约 §3.4），需 ROLE_ADMIN（SecurityConfig /admin/** 通配统一拦截）。
 * 写操作挂 @Audited（HMAC 审计链）——管理端写接口横切红线；配料为低频运营操作，无需 @RateLimit。
 * 重名/被引用删除均走 409 业务码（信封语义：HTTP 200 + code=409，前端按 code 分支提示）。
 */
@Tag(name = "吃啥-配料库管理端", description = "吃啥配料库（dining_ingredient）管理（ROLE_ADMIN）")
@Slf4j
@Validated
@RestController
@RequestMapping("/admin/dining/ingredient")
@RequiredArgsConstructor
public class DiningIngredientAdminController {

    private final DiningIngredientService diningIngredientService;

    @Operation(summary = "分页查询配料列表", description = "page/size + keyword（配料名模糊）+ category（MAIN/AUX/SEASONING/OTHER 精确，空=全部），按 id 升序")
    @GetMapping("/page")
    public Result<Page<DiningIngredientVo>> page(SearchDiningIngredientFrom from) {
        return Result.ok(diningIngredientService.adminPage(from));
    }

    @Operation(summary = "新增配料", description = "配料名全局唯一（重名 409 业务码，uk_name 唯一索引兜底并发）；category 缺省 OTHER")
    @Audited(action = "DINING_INGREDIENT_ADD", targetType = "dining_ingredient")
    @PostMapping
    public Result<String> add(@RequestBody @Valid AddDiningIngredientFrom from) {
        diningIngredientService.addIngredient(from);
        return Result.ok("添加成功");
    }

    @Operation(summary = "更新配料", description = "id 走路径变量；name/category 不传即不修改，改名与其他配料重名时 409")
    @Audited(action = "DINING_INGREDIENT_UPDATE", targetType = "dining_ingredient", targetIdSpEL = "#id")
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable("id") @Parameter(description = "配料ID", required = true) Long id,
                                 @RequestBody @Valid UpdateDiningIngredientFrom from) {
        diningIngredientService.updateIngredient(id, from);
        return Result.ok("修改成功");
    }

    @Operation(summary = "删除配料", description = "物理删除；被菜谱配料关联（dining_recipe_ingredient）引用时 409 拒绝并提示引用数量")
    @Audited(action = "DINING_INGREDIENT_DELETE", targetType = "dining_ingredient", targetIdSpEL = "#id")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable("id") @Parameter(description = "配料ID", required = true) Long id) {
        diningIngredientService.deleteIngredient(id);
        return Result.ok(true);
    }
}

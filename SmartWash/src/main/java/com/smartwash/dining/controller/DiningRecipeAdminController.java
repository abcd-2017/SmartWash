package com.smartwash.dining.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.common.audit.Audited;
import com.smartwash.dining.from.AddDiningRecipeFrom;
import com.smartwash.dining.from.DiningRecipeImportConfirmFrom;
import com.smartwash.dining.from.SearchDiningRecipeFrom;
import com.smartwash.dining.from.UpdateDiningRecipeFrom;
import com.smartwash.dining.service.DiningRecipeImportService;
import com.smartwash.dining.service.DiningRecipeService;
import com.smartwash.dining.vo.DiningRecipeImportPreviewVo;
import com.smartwash.dining.vo.DiningRecipeImportResultVo;
import com.smartwash.dining.vo.DiningRecipeVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 吃啥-菜谱管理端控制器。
 * 路径前缀 /admin/dining/recipes，需 ROLE_ADMIN（SecurityConfig /admin/** 通配统一拦截，无需改权限配置）。
 * 写操作挂 @Audited（HMAC 审计链）——管理端写接口横切红线；菜谱为低频运营操作，无需 @RateLimit。
 * 批量导入为两阶段：预览（解析校验不落库）→ 确认（整批事务写入，@Audited 留痕）。
 */
@Tag(name = "吃啥-菜谱管理端", description = "吃啥公共菜谱库管理（ROLE_ADMIN）")
@Slf4j
@Validated
@RestController
@RequestMapping("/admin/dining/recipes")
@RequiredArgsConstructor
public class DiningRecipeAdminController {

    private final DiningRecipeService diningRecipeService;

    private final DiningRecipeImportService diningRecipeImportService;

    @Operation(summary = "分页查询菜谱列表", description = "page/size + categoryId（精确）/status（1上架 0下架）/keyword（菜名模糊）筛选，按 id 降序（新菜谱在前）")
    @GetMapping("/all")
    public Result<Page<DiningRecipeVo>> getAll(SearchDiningRecipeFrom from) {
        return Result.ok(diningRecipeService.adminPage(from));
    }

    @Operation(summary = "新增菜谱", description = "默认上架；categoryId 可空=未分类，传值时校验分类存在；封面走独立上传接口拿对象键后随编辑/新增回填")
    @Audited(action = "DINING_RECIPE_ADD", targetType = "dining_recipe")
    @PostMapping("/add")
    public Result<String> add(@RequestBody @Valid AddDiningRecipeFrom from) {
        diningRecipeService.addRecipe(from);
        return Result.ok("添加成功");
    }

    @Operation(summary = "更新菜谱", description = "id 必传；其余字段不传即不修改；categoryId 传值时校验分类存在；上下架走专用状态接口")
    @Audited(action = "DINING_RECIPE_UPDATE", targetType = "dining_recipe", targetIdSpEL = "#from.id")
    @PostMapping("/update")
    public Result<String> update(@RequestBody @Valid UpdateDiningRecipeFrom from) {
        diningRecipeService.updateRecipe(from);
        return Result.ok("修改成功");
    }

    @Operation(summary = "批量删除菜谱", description = "根据 ID 批量物理删除，多个 ID 用逗号分隔；封面 MinIO 对象不随删回收（孤儿对象量级小，可接受）")
    @Audited(action = "DINING_RECIPE_DELETE", targetType = "dining_recipe", targetIdSpEL = "#ids")
    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> delete(@PathVariable("ids") @Pattern(regexp = "^\\d+(,\\d+)*$", message = "ids 必须为逗号分隔的数字") @Parameter(description = "菜谱ID列表，多个ID用逗号分隔", required = true, example = "1,2,3") String ids) {
        diningRecipeService.deleteRecipes(ids);
        return Result.ok(true);
    }

    @Operation(summary = "菜谱上下架", description = "status 仅接受 1=上架 0=下架；下架后不再出现在客户端上架列表，历史点单订单不受影响")
    @Audited(action = "DINING_RECIPE_STATUS", targetType = "dining_recipe", targetIdSpEL = "#id")
    @PostMapping("/{id}/status/{status}")
    public Result<String> changeStatus(@PathVariable("id") @Parameter(description = "菜谱ID", required = true) Long id,
                                       @PathVariable("status") @Parameter(description = "1=上架 0=下架", required = true) Integer status) {
        diningRecipeService.changeStatus(id, status);
        return Result.ok(status == 1 ? "已上架" : "已下架");
    }

    @Operation(summary = "上传菜谱封面", description = "multipart 图片（JPG/PNG/WebP，校验魔数与扩展名一致性，≤5MB），存 MinIO dining/covers/ 前缀并返回对象键；对象键回填到新增/修改接口的 coverObjectKey 字段，读取时按对象键拼直链")
    @Audited(action = "DINING_RECIPE_COVER_UPLOAD", targetType = "dining_recipe", targetIdSpEL = "#result.data")
    @PostMapping("/cover")
    public Result<String> uploadCover(@RequestPart("file") @Parameter(description = "封面图片文件", required = true) MultipartFile file) {
        return Result.ok(diningRecipeService.uploadCover(file));
    }

    // ==================== 批量导入（03 号票，两阶段：预览不落库 → 确认整批事务） ====================

    @Operation(summary = "下载菜谱导入模板", description = "动态生成 xlsx 模板（首个 sheet 为表头+示例行，第二个 sheet 为填写说明：食材分隔符/分类需先建/封面不导入/500 行上限）。列：分类名、菜名、食材名列表、步骤、封面链接")
    @GetMapping("/import/template")
    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
        // 响应头语义与充值记录导出一致：附件下载，固定文件名（xlsx MIME）
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=dining_recipes_import_template.xlsx");
        response.getOutputStream().write(diningRecipeImportService.buildTemplate());
    }

    @Operation(summary = "菜谱批量导入-解析预览", description = "上传 .xlsx/.csv（≤5MB、≤500 数据行），解析并逐行校验（缺列整体拒绝；菜名/分类/食材/步骤/封面链接给行号级错误），返回归一后行数据供预览确认；本接口不落库")
    @PostMapping("/import")
    public Result<DiningRecipeImportPreviewVo> previewImport(@RequestPart("file") @Parameter(description = "导入文件（.xlsx/.csv）", required = true) MultipartFile file) {
        return Result.ok(diningRecipeImportService.preview(file));
    }

    @Operation(summary = "菜谱批量导入-确认写入", description = "两阶段导入第二步：提交预览通过校验的行（归一后结构化数据），整批事务——再次逐行校验，任一行失败全批不落库并返回逐行失败报告（rolledBack=true）；全部通过才逐行写入并返回逐行菜谱 ID。封面不支持批量导入，导入后单独上传")
    @Audited(action = "DINING_RECIPE_IMPORT", targetType = "dining_recipe")
    @PostMapping("/import/confirm")
    public Result<DiningRecipeImportResultVo> confirmImport(@RequestBody @Valid DiningRecipeImportConfirmFrom from) {
        return Result.ok(diningRecipeImportService.confirm(from));
    }
}

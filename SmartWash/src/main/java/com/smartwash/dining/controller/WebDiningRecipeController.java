package com.smartwash.dining.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.dining.from.AddDiningRecipeFrom;
import com.smartwash.dining.from.SearchWebDiningRecipeFrom;
import com.smartwash.dining.service.DiningRecipeService;
import com.smartwash.dining.vo.DiningRecipeVo;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 吃啥-菜谱用户端控制器。
 * 路径前缀 /web/auth/dining/recipes，需 ROLE_USER（SecurityConfig /web/auth/** 通配统一拦截，无需改权限配置）。
 * 全部接口以当前登录用户为主体（UserContextHolder 注入 userId）。
 * 上架可见性由服务层固定过滤（status=1），端上无法通过任何参数翻出下架菜谱。
 * 四端联动说明：本组为新增接口，不改动任何既有接口；Android 端随本票 :feature:dining 接入，
 * 鸿蒙端暂不消费（接口契约以本类为源，已同步 GLOSSARY 10.10）。
 */
@Tag(name = "吃啥-菜谱用户端", description = "上架列表/详情/新增（直上架）/封面上传（ROLE_USER）")
@Slf4j
@RestController
@RequestMapping("/web/auth/dining/recipes")
@RequiredArgsConstructor
public class WebDiningRecipeController {

    private final DiningRecipeService diningRecipeService;

    @Operation(summary = "上架菜谱分页", description = "page/size + categoryId（精确，null=全部含未分类）/keyword（菜名模糊）筛选；"
            + "仅返回上架菜谱，按 id 降序（新菜谱在前）")
    @GetMapping("/all")
    public Result<Page<DiningRecipeVo>> getAll(SearchWebDiningRecipeFrom from) {
        return Result.ok(diningRecipeService.userPage(from));
    }

    @Operation(summary = "菜谱详情", description = "食材名数组与步骤全文；仅上架可见，不存在或已下架返回同一业务失败提示")
    @GetMapping("/{id}")
    public Result<DiningRecipeVo> detail(
            @PathVariable("id") @Parameter(description = "菜谱ID", required = true) Long id) {
        return Result.ok(diningRecipeService.userDetail(id));
    }

    @Operation(summary = "新增菜谱", description = "source=USER 直上架（无审核流，保存即可被所有人看到）；"
            + "categoryId 可空或 0=未分类，传值时校验分类存在；封面先走上传接口拿对象键再随本接口回填")
    @PostMapping("/add")
    public Result<String> add(@RequestBody @Valid AddDiningRecipeFrom from) {
        diningRecipeService.addUserRecipe(from, currentUserId());
        return Result.ok("添加成功");
    }

    @Operation(summary = "上传菜谱封面", description = "multipart 图片（JPG/PNG/WebP，校验魔数与扩展名一致性，≤5MB），"
            + "存 MinIO dining/covers/ 前缀并返回对象键；对象键回填到新增接口的 coverObjectKey 字段，"
            + "读取时按对象键拼直链（校验与管理端封面上传同链路）")
    @PostMapping("/cover")
    public Result<String> uploadCover(@RequestPart("file") @Parameter(description = "封面图片文件", required = true) MultipartFile file) {
        return Result.ok(diningRecipeService.uploadCover(file));
    }

    private Long currentUserId() {
        LoginUser user = UserContextHolder.getUser();
        return user.getUserId();
    }
}

package com.smartwash.dining.controller;

import com.smartwash.common.Result;
import com.smartwash.dining.service.DiningCategoryService;
import com.smartwash.dining.vo.DiningCategoryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 家庭点单-菜谱分类用户端控制器。
 * 路径前缀 /web/auth/dining/categories，需 ROLE_USER（SecurityConfig /web/auth/** 通配统一拦截）。
 * 用户端菜谱库分类筛选条的数据源：全量分类（量级小不分页），sort 升序与管理端列表一致。
 * 管理端写接口（增删改）不向用户端开放——用户只读分类目录。
 */
@Tag(name = "家庭点单-菜谱分类用户端", description = "全量分类目录（只读，ROLE_USER）")
@Slf4j
@RestController
@RequestMapping("/web/auth/dining/categories")
@RequiredArgsConstructor
public class WebDiningCategoryController {

    private final DiningCategoryService diningCategoryService;

    @Operation(summary = "全部分类", description = "sort 升序、id 升序（与管理端列表顺序一致），供菜谱库分类筛选")
    @GetMapping("/all")
    public Result<List<DiningCategoryVo>> getAll() {
        return Result.ok(diningCategoryService.listAll());
    }
}

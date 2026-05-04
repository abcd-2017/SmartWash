package com.smartwash.controller.web;


import com.smartwash.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.service.ILaundryItemsService;
import com.smartwash.vo.laudry.LaundryPackageVo;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 衣物套餐
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Tag(name = "用户端-洗衣项目", description = "用户端洗衣项目查询接口")
@Slf4j
@RestController
@RequestMapping("/web/laundryItems")
public class WebLaundryItemsController {
    @Autowired
    private ILaundryItemsService laundryItemsService;

    @Operation(summary = "获取洗衣项目列表", description = "获取所有可用的洗衣套餐列表")
    @GetMapping("/all")
    public Result<List<LaundryPackageVo>> getAll() {
        return Result.ok(laundryItemsService.getAllItem());
    }
}

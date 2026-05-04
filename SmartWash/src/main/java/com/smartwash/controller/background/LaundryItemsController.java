package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.entity.LaundryItems;
import com.smartwash.from.laundry_item.AddLaundryItemsFrom;
import com.smartwash.from.laundry_item.SearchLaundryItemsFrom;
import com.smartwash.from.laundry_item.UpdateLaundryItemsFrom;
import com.smartwash.service.ILaundryItemsService;
import com.smartwash.vo.laudry.LaundryPackageVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * <p>
 * 衣物套餐
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Tag(name = "洗衣项目管理", description = "洗衣套餐/项目的增删改查")
@Slf4j
@RestController
@RequestMapping("/admin/laundryItems")
public class LaundryItemsController {
    @Autowired
    private ILaundryItemsService laundryItemsService;

    @Operation(summary = "分页查询洗衣项目列表", description = "根据条件分页查询洗衣套餐列表")
    @GetMapping("/all")
    public Result<Page<LaundryPackageVo>> getAll(SearchLaundryItemsFrom laundryPackageFrom) {
        return Result.ok(laundryItemsService.getAllLaundryPackage(laundryPackageFrom));
    }

    @Operation(summary = "新增洗衣项目", description = "新增洗衣套餐，项目名称不可重复")
    @PostMapping("/add")
    public Result<String> addSchool(@RequestBody @Valid AddLaundryItemsFrom addLaundryPackageFrom) {
        if (laundryItemsService.getSearchByName(addLaundryPackageFrom.getItemName()) != null) {
            return Result.failMsg("该套餐已经存在了");
        }
        laundryItemsService.addLaundryPackage(addLaundryPackageFrom);
        return Result.ok("添加成功");
    }

    @Operation(summary = "更新洗衣项目", description = "修改洗衣套餐信息")
    @PostMapping("/update")
    public Result<String> updateSchool(@RequestBody @Valid UpdateLaundryItemsFrom laundryPackageFrom) {
        LaundryItems laundryItems = laundryItemsService.getById(laundryPackageFrom.getItemId());
        if (!Objects.equals(laundryItems.getItemName(), laundryItems.getItemName()) && laundryItemsService.getSearchByName(laundryPackageFrom.getItemName()) != null) {
            return Result.failMsg("该套餐已经存在了");
        }
        laundryItemsService.updateLaundryPackage(laundryPackageFrom);
        return Result.ok("修改成功");
    }

    @Operation(summary = "批量删除洗衣项目", description = "根据ID批量删除洗衣套餐，多个ID用逗号分隔")
    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteLaundryPackage(@PathVariable("ids") @Parameter(description = "洗衣项目ID列表，多个ID用逗号分隔", required = true, example = "1,2,3") String ids) {
        return Result.ok(laundryItemsService.deleteLaundryPackage(ids));
    }
}

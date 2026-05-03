package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
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
@Slf4j
@RestController
@RequestMapping("/admin/laundryItems")
public class LaundryItemsController {
    @Autowired
    private ILaundryItemsService laundryItemsService;

    //获取所有套餐
    @GetMapping("/all")
    public Result<Page<LaundryPackageVo>> getAll(SearchLaundryItemsFrom laundryPackageFrom) {
        return Result.ok(laundryItemsService.getAllLaundryPackage(laundryPackageFrom));
    }

    //添加套餐
    @PostMapping("/add")
    public Result<String> addSchool(@RequestBody @Valid AddLaundryItemsFrom addLaundryPackageFrom) {
        if (laundryItemsService.getSearchByName(addLaundryPackageFrom.getItemName()) != null) {
            return Result.failMsg("该套餐已经存在了");
        }
        laundryItemsService.addLaundryPackage(addLaundryPackageFrom);
        return Result.ok("添加成功");
    }

    //修改套餐
    @PostMapping("/update")
    public Result<String> updateSchool(@RequestBody @Valid UpdateLaundryItemsFrom laundryPackageFrom) {
        LaundryItems laundryItems = laundryItemsService.getById(laundryPackageFrom.getItemId());
        if (!Objects.equals(laundryItems.getItemName(), laundryItems.getItemName()) && laundryItemsService.getSearchByName(laundryPackageFrom.getItemName()) != null) {
            return Result.failMsg("该套餐已经存在了");
        }
        laundryItemsService.updateLaundryPackage(laundryPackageFrom);
        return Result.ok("修改成功");
    }

    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteLaundryPackage(@PathVariable("ids") String ids) {
        return Result.ok(laundryItemsService.deleteLaundryPackage(ids));
    }
}

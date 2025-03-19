package com.smartwash.controller.web;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.from.laundry_item.SearchLaundryItemsFrom;
import com.smartwash.service.ILaundryItemsService;
import com.smartwash.vo.laudry.LaundryPackageVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 衣物套餐
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@RestController
@RequestMapping("/web/laundryItems")
public class WebLaundryItemsController {
    @Autowired
    private ILaundryItemsService laundryItemsService;

    //获取所有套餐
    @GetMapping("/all")
    public Result<Page<LaundryPackageVo>> getAll(SearchLaundryItemsFrom laundryPackageFrom) {
        return Result.ok(laundryItemsService.getAllLaundryPackage(laundryPackageFrom));
    }
}

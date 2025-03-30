package com.smartwash.controller.web;


import com.smartwash.common.Result;
import com.smartwash.service.ILaundryItemsService;
import com.smartwash.vo.laudry.LaundryPackageVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
@RestController
@RequestMapping("/web/laundryItems")
public class WebLaundryItemsController {
    @Autowired
    private ILaundryItemsService laundryItemsService;

    //获取所有套餐
    @GetMapping("/all")
    public Result<List<LaundryPackageVo>> getAll() {
        return Result.ok(laundryItemsService.getAllItem());
    }
}

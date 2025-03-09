package com.smartwash.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.from.recharge_records_from.SearchRechargeRecordsFrom;
import com.smartwash.service.IRechargeRecordsService;
import com.smartwash.vo.recharge_records_vo.RechargeRecordsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@RestController
@RequestMapping("/rechargeRecords")
public class RechargeRecordsController {
    @Autowired
    private IRechargeRecordsService rechargeRecordsService;

    //获取所有充值记录
    @GetMapping("/all")
    public Result<Page<RechargeRecordsVo>> getAll(SearchRechargeRecordsFrom rechargeRecordsFrom) {
        return Result.ok(rechargeRecordsService.getAllRecords(rechargeRecordsFrom));
    }
}

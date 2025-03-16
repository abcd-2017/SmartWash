package com.smartwash.controller.web;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.from.recharge_records.SearchRechargeRecordsFrom;
import com.smartwash.service.IRechargeRecordsService;
import com.smartwash.vo.recharge_records.RechargeRecordsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
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
@RequestMapping("/web/rechargeRecords")
public class WebRechargeRecordsController {
    @Autowired
    private IRechargeRecordsService rechargeRecordsService;

    //获取所有充值记录
    @GetMapping("/all")
    public Result<Page<RechargeRecordsVo>> getAll(SearchRechargeRecordsFrom rechargeRecordsFrom) {
        String phoneNumber = rechargeRecordsFrom.getPhoneNumber();
        if (StringUtils.hasText(phoneNumber)) {
            if (phoneNumber.length() > 11) return Result.failMsg("手机号长度错误");
            if (!phoneNumber.matches("^(\\+86)?1[3-9]\\d{9}$")) return Result.failMsg("手机号格式错误");
        }
        return Result.ok(rechargeRecordsService.getAllRecords(rechargeRecordsFrom));
    }
}
